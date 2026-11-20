package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiExpenseParser
import com.example.ai.ParsedNaturalExpense
import com.example.audio.VoiceExpenseRecorder
import com.example.data.AccountDepositEntity
import com.example.data.AccountEntity
import com.example.data.AccountTransferEntity
import com.example.data.BankMessageProcessOutcome
import com.example.data.BudgetPreferences
import com.example.data.BudgetPreferencesRepository
import com.example.data.CategoryBudgetEntity
import com.example.data.CommitmentEntity
import com.example.data.DetectedTransactionEntity
import com.example.data.ExpenseEntity
import com.example.data.PointsAccountEntity
import com.example.data.PointsFormatter
import com.example.data.SpendWiseDatabase
import com.example.data.SpendWiseRepository
import com.example.model.CategorizationConfidence
import com.example.model.CategorizationResult
import com.example.model.CommitmentScheduleHelper
import com.example.model.CommitmentType
import com.example.model.CommitmentWithStatus
import com.example.model.DailyBudgetMode
import com.example.model.ExpenseCategory
import com.example.model.MonthlyCommitmentsSummary
import com.example.model.PaymentMethod
import com.example.model.QuickPreset
import com.example.model.RecurrenceCycle
import com.example.notifications.BankNotificationAccessHelper
import com.example.notifications.BudgetAlertEvent
import com.example.notifications.BudgetNotificationHelper
import com.example.util.DailySummarySnapshot
import com.example.util.DateUtils
import com.example.util.FinancialInsightsEngine
import com.example.util.MoneyFormatter
import com.example.widget.SpendWiseBalanceWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ActivityTimeframe(val label: String) {
    TODAY("Today"),
    WEEK("7 Days"),
    MONTH("This Month"),
    ALL("All Time")
}

data class ExpenseEditorState(
    val isOpen: Boolean = false,
    val editingId: Long? = null,
    val editingRemoteId: String? = null,
    val editingDetectedId: Long? = null,
    val amountText: String = "",
    val description: String = "",
    val selectedCategory: ExpenseCategory? = null,
    val isCategoryManuallyChosen: Boolean = false,
    val categorizationResult: CategorizationResult? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
    val accountName: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val validationError: String? = null
)

data class SyncUiState(
    val isSyncing: Boolean = false,
    val isTestingConnection: Boolean = false,
    val testFeedback: String? = null,
    val testSucceeded: Boolean = false
)

data class GeminiQuickLogState(
    val promptText: String = "",
    val isParsing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val audioAmplitudeNormalized: Float = 0f,
    val parsingStatusText: String = "Processing with Gemini...",
    val errorMessage: String? = null,
    val lastLoggedSummary: String? = null
)

data class SpendWiseUiState(
    val isLoading: Boolean = true,
    val expenses: List<ExpenseEntity> = emptyList(),
    val todayExpenses: List<ExpenseEntity> = emptyList(),
    val filteredExpenses: List<ExpenseEntity> = emptyList(),
    val transfers: List<AccountTransferEntity> = emptyList(),
    val todayTransfers: List<AccountTransferEntity> = emptyList(),
    val filteredTransfers: List<AccountTransferEntity> = emptyList(),
    val deposits: List<AccountDepositEntity> = emptyList(),
    val todayDeposits: List<AccountDepositEntity> = emptyList(),
    val filteredDeposits: List<AccountDepositEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val pointsAccounts: List<PointsAccountEntity> = emptyList(),
    val selectedTodayAccount: String? = null,
    val selectedActivityAccountFilter: String? = null,
    val categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
    val preferences: BudgetPreferences = BudgetPreferences(),
    val summary: DailySummarySnapshot = FinancialInsightsEngine.compute(
        emptyList(),
        emptyList(),
        BudgetPreferences()
    ),
    val searchQuery: String = "",
    val selectedCategoryFilter: ExpenseCategory? = null,
    val timeframeFilter: ActivityTimeframe = ActivityTimeframe.MONTH,
    val commitments: List<CommitmentWithStatus> = emptyList(),
    val filteredCommitments: List<CommitmentWithStatus> = emptyList(),
    val upcomingCommitments: List<CommitmentWithStatus> = emptyList(),
    val selectedCommitmentFilter: CommitmentType? = null,
    val commitmentsSummary: MonthlyCommitmentsSummary = CommitmentScheduleHelper.summarize(emptyList()),
    val pendingDetectedCharges: List<DetectedTransactionEntity> = emptyList(),
    val hasNotificationListenerAccess: Boolean = false,
    val latestAlertBanner: BudgetAlertEvent? = null,
    val hasNotificationPermission: Boolean = true,
    val syncState: SyncUiState = SyncUiState(),
    val geminiState: GeminiQuickLogState = GeminiQuickLogState(),
    val activeRequestMessage: String? = null
)

enum class UndoActionType {
    NONE,
    EXPENSE,
    TRANSFER,
    DEPOSIT,
    POINTS
}

sealed class UiEffect {
    data class ShowSnackbar(
        val message: String,
        val undoType: UndoActionType = UndoActionType.NONE
    ) : UiEffect() {
        val showUndo: Boolean get() = undoType != UndoActionType.NONE
    }
}

class SpendWiseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SpendWiseDatabase.getInstance(application)
    private val preferencesRepository = BudgetPreferencesRepository(application)
    private val notificationHelper = BudgetNotificationHelper(application)
    private val repository = SpendWiseRepository(
        expenseDao = database.expenseDao(),
        categoryBudgetDao = database.categoryBudgetDao(),
        accountDao = database.accountDao(),
        accountTransferDao = database.accountTransferDao(),
        pointsAccountDao = database.pointsAccountDao(),
        commitmentDao = database.commitmentDao(),
        detectedTransactionDao = database.detectedTransactionDao(),
        preferencesRepository = preferencesRepository,
        notificationHelper = notificationHelper,
        accountDepositDao = database.accountDepositDao()
    )

    private val searchQuery = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<ExpenseCategory?>(null)
    private val timeframeFilter = MutableStateFlow(ActivityTimeframe.MONTH)
    private val selectedTodayAccount = MutableStateFlow<String?>(null)
    private val activityAccountFilter = MutableStateFlow<String?>(null)
    private val commitmentTypeFilter = MutableStateFlow<CommitmentType?>(null)
    private val alertBanner = MutableStateFlow<BudgetAlertEvent?>(null)
    private val permissionGrantedState = MutableStateFlow(repository.hasNotificationPermission())
    private val listenerAccessState = MutableStateFlow(
        BankNotificationAccessHelper.isNotificationAccessEnabled(application)
    )
    private val syncUiState = MutableStateFlow(SyncUiState())
    private val geminiUiState = MutableStateFlow(GeminiQuickLogState())
    private val geminiParser by lazy { GeminiExpenseParser() }
    private val voiceRecorder by lazy { VoiceExpenseRecorder(application) }

    private val _editorState = MutableStateFlow(ExpenseEditorState())
    val editorState: StateFlow<ExpenseEditorState> = _editorState.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>()
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    private var lastDeletedExpense: ExpenseEntity? = null
    private var lastDeletedTransfer: AccountTransferEntity? = null
    private var lastDeletedDeposit: AccountDepositEntity? = null
    private var lastDeletedPointsAccount: PointsAccountEntity? = null
    private var categorizeJob: Job? = null
    private var recordingTickerJob: Job? = null

    val uiState: StateFlow<SpendWiseUiState> = combine(
        combine(
            repository.allExpenses,
            repository.categoryBudgets,
            repository.allAccounts,
            repository.allCommitments,
            repository.preferences
        ) { expenses, budgets, accounts, commitments, prefs ->
            DataSourcesTuple(expenses, budgets, accounts, commitments, prefs)
        },
        combine(
            searchQuery,
            categoryFilter,
            timeframeFilter,
            activityAccountFilter,
            selectedTodayAccount
        ) { q, cat, tf, actAcct, todayAcct ->
            FilterTuple(q, cat, tf, actAcct, todayAcct)
        },
        combine(
            combine(
                alertBanner,
                permissionGrantedState,
                syncUiState
            ) { banner, perm, sync -> Triple(banner, perm, sync) },
            combine(
                geminiUiState,
                commitmentTypeFilter,
                repository.activeRequestMessage
            ) { gemini, commitFilter, activeReq -> Triple(gemini, commitFilter, activeReq) }
        ) { (banner, perm, sync), (gemini, commitFilter, activeReq) ->
            StatusTuple(banner, perm, sync, gemini, commitFilter, activeReq)
        },
        combine(
            repository.pendingDetectedTransactions,
            listenerAccessState,
            repository.allTransfers,
            repository.allPointsAccounts,
            repository.allDeposits
        ) { pendingDetected, listenerAccess, transfers, pointsAccounts, deposits ->
            BankDetectionTuple(pendingDetected, listenerAccess, transfers, pointsAccounts, deposits)
        }
    ) { data, filters, status, bankDetection ->
        val now = System.currentTimeMillis()
        val todayStart = DateUtils.startOfDayMillis(now)
        val todayEnd = DateUtils.endOfDayMillis(now)

        val validTodayAccount = filters.todayAccount?.takeIf { sel ->
            data.accounts.any { it.name.equals(sel, ignoreCase = true) }
        }
        val validActivityAccount = filters.activityAccount?.takeIf { sel ->
            data.accounts.any { it.name.equals(sel, ignoreCase = true) }
        }

        val todayExpenses = data.expenses.filter { item ->
            item.timestampMillis in todayStart..todayEnd &&
                (validTodayAccount == null || item.accountName.equals(validTodayAccount, ignoreCase = true))
        }
        val todayTransfers = bankDetection.transfers.filter { transfer ->
            transfer.timestampMillis in todayStart..todayEnd &&
                (validTodayAccount == null ||
                    transfer.fromAccountName.equals(validTodayAccount, ignoreCase = true) ||
                    transfer.toAccountName.equals(validTodayAccount, ignoreCase = true))
        }
        val todayDeposits = bankDetection.deposits.filter { deposit ->
            deposit.timestampMillis in todayStart..todayEnd &&
                (validTodayAccount == null ||
                    deposit.accountName.equals(validTodayAccount, ignoreCase = true))
        }

        val summary = FinancialInsightsEngine.compute(
            expenses = data.expenses,
            categoryBudgets = data.budgets,
            prefs = data.prefs,
            nowMillis = now,
            accounts = data.accounts,
            transfers = bankDetection.transfers,
            deposits = bankDetection.deposits
        )

        val filtered = filterExpenses(
            expenses = data.expenses,
            query = filters.query,
            category = filters.category,
            accountName = validActivityAccount,
            timeframe = filters.timeframe,
            nowMillis = now
        )
        val filteredTransfers = filterTransfers(
            transfers = bankDetection.transfers,
            query = filters.query,
            category = filters.category,
            accountName = validActivityAccount,
            timeframe = filters.timeframe,
            nowMillis = now
        )
        val filteredDeposits = filterDeposits(
            deposits = bankDetection.deposits,
            query = filters.query,
            category = filters.category,
            accountName = validActivityAccount,
            timeframe = filters.timeframe,
            nowMillis = now
        )

        val evaluatedCommitments = data.commitments.map { entity ->
            CommitmentWithStatus(
                commitment = entity,
                commitmentType = CommitmentType.fromKey(entity.type),
                recurrenceCycle = RecurrenceCycle.fromKey(entity.cycle),
                status = CommitmentScheduleHelper.evaluate(entity, now)
            )
        }.sortedWith(
            compareBy<CommitmentWithStatus> { !it.commitment.isActive || it.status.isFullyPaidOff }
                .thenBy { it.status.isCompletedThisCycle }
                .thenBy { it.status.nextDueMillis }
        )

        val filteredCommitments = evaluatedCommitments.filter { item ->
            status.commitmentFilter == null || item.commitmentType == status.commitmentFilter
        }
        val upcomingCommitments = evaluatedCommitments.filter { item ->
            item.commitment.isActive &&
                !item.status.isFullyPaidOff &&
                !item.status.isCompletedThisCycle &&
                (item.status.isOverdue || item.status.daysUntilDue <= 7)
        }
        val commitmentsSummary = CommitmentScheduleHelper.summarize(evaluatedCommitments)

        val resolvedActiveRequest = when {
            status.gemini.isParsing -> status.gemini.parsingStatusText.ifBlank { "Processing with Gemini..." }
            status.sync.isTestingConnection -> "Testing Google Sheet connection..."
            status.sync.isSyncing -> "Syncing with Google Sheet..."
            !status.activeRepoRequest.isNullOrBlank() -> status.activeRepoRequest
            else -> null
        }

        SpendWiseUiState(
            isLoading = false,
            expenses = data.expenses,
            todayExpenses = todayExpenses,
            filteredExpenses = filtered,
            transfers = bankDetection.transfers,
            todayTransfers = todayTransfers,
            filteredTransfers = filteredTransfers,
            deposits = bankDetection.deposits,
            todayDeposits = todayDeposits,
            filteredDeposits = filteredDeposits,
            accounts = data.accounts,
            pointsAccounts = bankDetection.pointsAccounts,
            selectedTodayAccount = validTodayAccount,
            selectedActivityAccountFilter = validActivityAccount,
            categoryBudgets = data.budgets,
            preferences = data.prefs,
            summary = summary,
            searchQuery = filters.query,
            selectedCategoryFilter = filters.category,
            timeframeFilter = filters.timeframe,
            commitments = evaluatedCommitments,
            filteredCommitments = filteredCommitments,
            upcomingCommitments = upcomingCommitments,
            selectedCommitmentFilter = status.commitmentFilter,
            commitmentsSummary = commitmentsSummary,
            pendingDetectedCharges = bankDetection.pendingDetected,
            hasNotificationListenerAccess = bankDetection.hasListenerAccess,
            latestAlertBanner = status.banner,
            hasNotificationPermission = status.hasPermission,
            syncState = status.sync,
            geminiState = status.gemini,
            activeRequestMessage = resolvedActiveRequest
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SpendWiseUiState()
    )

    init {
        viewModelScope.launch {
            combine(
                combine(
                    repository.allExpenses,
                    repository.categoryBudgets,
                    repository.preferences
                ) { _, _, _ -> Unit },
                combine(
                    repository.allAccounts,
                    repository.allTransfers,
                    repository.allDeposits,
                    repository.allPointsAccounts
                ) { _, _, _, _ -> Unit }
            ) { _, _ -> Unit }
                .drop(1)
                .collect {
                    SpendWiseBalanceWidgetProvider.refreshAll(getApplication())
                }
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _effects.emit(UiEffect.ShowSnackbar(message))
        }
    }

    fun refreshNotificationPermission() {
        permissionGrantedState.value = repository.hasNotificationPermission()
        listenerAccessState.value = BankNotificationAccessHelper.isNotificationAccessEnabled(getApplication())
    }

    fun refreshNotificationListenerAccess() {
        listenerAccessState.value = BankNotificationAccessHelper.isNotificationAccessEnabled(getApplication())
    }

    fun updateBankDetectionSettings(
        detectionEnabled: Boolean,
        autoLogEnabled: Boolean,
        defaultAccountName: String
    ) {
        viewModelScope.launch {
            repository.updateBankDetectionSettings(
                detectionEnabled = detectionEnabled,
                autoLogEnabled = autoLogEnabled,
                defaultAccountName = defaultAccountName
            )
        }
    }

    fun approveDetectedCharge(
        item: DetectedTransactionEntity,
        accountOverride: String? = null
    ) {
        viewModelScope.launch {
            val alerts = repository.approveDetectedTransaction(
                item = item,
                accountOverride = accountOverride
            )
            if (alerts.isNotEmpty()) {
                alertBanner.value = alerts.first()
            }
            val symbol = uiState.value.preferences.currencySymbol
            val formatted = MoneyFormatter.format(item.amountCents, symbol)
            _effects.emit(UiEffect.ShowSnackbar("Logged $formatted at ${item.merchant}"))
        }
    }

    fun openDetectedChargeInEditor(item: DetectedTransactionEntity) {
        val cat = ExpenseCategory.fromKey(item.suggestedCategory)
        val note = buildString {
            append("Detected via ${item.sourceAppLabel}")
            if (item.cardLastFour.isNotBlank()) {
                append(" (•••• ${item.cardLastFour})")
            }
        }
        _editorState.value = ExpenseEditorState(
            isOpen = true,
            editingDetectedId = item.id,
            amountText = MoneyFormatter.centsToEditText(item.amountCents),
            description = item.merchant,
            selectedCategory = cat,
            isCategoryManuallyChosen = false,
            paymentMethod = PaymentMethod.fromKey(item.paymentMethod),
            accountName = item.matchedAccountName,
            timestampMillis = item.receivedAtMillis,
            note = note
        )
        triggerAutoCategorization(item.merchant, item.amountCents)
    }

    fun dismissDetectedCharge(item: DetectedTransactionEntity) {
        viewModelScope.launch {
            repository.dismissDetectedTransaction(item.id)
            _effects.emit(UiEffect.ShowSnackbar("Dismissed ${item.merchant}"))
        }
    }

    fun submitManualBankMessage(rawTitle: String, rawBody: String, onOutcomeMessage: (String, Boolean) -> Unit) {
        val trimmedBody = rawBody.trim()
        if (trimmedBody.isBlank()) {
            onOutcomeMessage("Paste a bank SMS or alert message to parse.", false)
            return
        }
        viewModelScope.launch {
            val hasAccess = BankNotificationAccessHelper.isNotificationAccessEnabled(getApplication())
            listenerAccessState.value = hasAccess
            val prefs = uiState.value.preferences

            val outcome = repository.processIncomingBankMessage(
                sourcePackage = "manual_paste",
                sourceAppLabel = rawTitle.trim().ifBlank { "Bank Message" },
                rawTitle = rawTitle.trim(),
                rawBody = trimmedBody,
                respectToggle = false
            )

            val configWarning = buildString {
                if (!hasAccess) {
                    append(" Warning: Android Notification Access is OFF for SpendWise, so automatic background alerts will not arrive until granted.")
                }
                if (!prefs.bankDetectionEnabled) {
                    append(" Warning: \"Listen for Bank Charge Alerts\" is currently OFF in Budget settings.")
                }
            }

            val symbol = prefs.currencySymbol
            when (outcome) {
                is BankMessageProcessOutcome.AutoLogged -> {
                    if (outcome.alerts.isNotEmpty()) {
                        alertBanner.value = outcome.alerts.first()
                    }
                    val amt = MoneyFormatter.format(outcome.entity.amountCents, symbol)
                    onOutcomeMessage("Auto-logged $amt at ${outcome.entity.merchant}.$configWarning", true)
                }
                is BankMessageProcessOutcome.QueuedForReview -> {
                    val amt = MoneyFormatter.format(outcome.entity.amountCents, symbol)
                    onOutcomeMessage("Detected $amt at ${outcome.entity.merchant} — added to Today's review queue.$configWarning", true)
                }
                BankMessageProcessOutcome.Duplicate -> {
                    onOutcomeMessage("This exact charge was already detected in the last few minutes.$configWarning", false)
                }
                BankMessageProcessOutcome.Ignored -> {
                    onOutcomeMessage(
                        "No card or bank charge was recognized (OTPs, verification codes, declines & promos are filtered out).$configWarning",
                        false
                    )
                }
            }
        }
    }

    fun dismissAlertBanner() {
        alertBanner.value = null
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onCategoryFilterChange(category: ExpenseCategory?) {
        categoryFilter.value = if (categoryFilter.value == category) null else category
    }

    fun onTimeframeChange(timeframe: ActivityTimeframe) {
        timeframeFilter.value = timeframe
    }

    fun onTodayAccountSelected(accountName: String?) {
        selectedTodayAccount.value = if (selectedTodayAccount.value.equals(accountName, ignoreCase = true)) {
            null
        } else {
            accountName
        }
    }

    fun onActivityAccountFilterChange(accountName: String?) {
        activityAccountFilter.value = if (activityAccountFilter.value.equals(accountName, ignoreCase = true)) {
            null
        } else {
            accountName
        }
    }

    fun saveCustomAccount(existingId: Long? = null, name: String, initialBalanceCents: Long) {
        viewModelScope.launch {
            val result = repository.saveAccount(existingId, name, initialBalanceCents)
            result.fold(
                onSuccess = { saved ->
                    if (existingId == null && _editorState.value.isOpen) {
                        _editorState.update { it.copy(accountName = saved.name) }
                    }
                    val verb = if (existingId != null) "Updated" else "Created"
                    _effects.emit(UiEffect.ShowSnackbar("$verb account \"${saved.name}\""))
                },
                onFailure = { err ->
                    _effects.emit(UiEffect.ShowSnackbar(err.localizedMessage ?: "Couldn't save account"))
                }
            )
        }
    }

    fun deleteCustomAccount(account: AccountEntity) {
        viewModelScope.launch {
            if (selectedTodayAccount.value.equals(account.name, ignoreCase = true)) {
                selectedTodayAccount.value = null
            }
            if (activityAccountFilter.value.equals(account.name, ignoreCase = true)) {
                activityAccountFilter.value = null
            }
            repository.deleteAccount(account)
            _effects.emit(UiEffect.ShowSnackbar("Deleted account \"${account.name}\""))
        }
    }

    fun saveAccountTransfer(
        existingId: Long? = null,
        fromAccountName: String,
        toAccountName: String,
        amountCents: Long,
        timestampMillis: Long,
        note: String
    ) {
        viewModelScope.launch {
            val result = repository.saveAccountTransfer(
                existingId = existingId,
                fromAccountName = fromAccountName,
                toAccountName = toAccountName,
                amountCents = amountCents,
                timestampMillis = timestampMillis,
                note = note
            )
            result.fold(
                onSuccess = { saved ->
                    val symbol = uiState.value.preferences.currencySymbol
                    val amt = MoneyFormatter.format(saved.amountCents, symbol)
                    val verb = if (existingId != null && existingId > 0L) "Updated transfer:" else "Transferred"
                    _effects.emit(
                        UiEffect.ShowSnackbar(
                            "$verb $amt from ${saved.fromAccountName} to ${saved.toAccountName}"
                        )
                    )
                },
                onFailure = { err ->
                    _effects.emit(UiEffect.ShowSnackbar(err.localizedMessage ?: "Couldn't save transfer"))
                }
            )
        }
    }

    fun deleteAccountTransfer(transfer: AccountTransferEntity) {
        viewModelScope.launch {
            lastDeletedTransfer = transfer
            repository.deleteAccountTransfer(transfer)
            _effects.emit(
                UiEffect.ShowSnackbar(
                    message = "Removed transfer ${transfer.fromAccountName} -> ${transfer.toAccountName}",
                    undoType = UndoActionType.TRANSFER
                )
            )
        }
    }

    fun undoDeleteTransfer() {
        val toRestore = lastDeletedTransfer ?: return
        lastDeletedTransfer = null
        viewModelScope.launch {
            repository.restoreAccountTransfer(toRestore)
        }
    }

    fun undoAction(undoType: UndoActionType) {
        when (undoType) {
            UndoActionType.EXPENSE -> undoDeleteExpense()
            UndoActionType.TRANSFER -> undoDeleteTransfer()
            UndoActionType.DEPOSIT -> undoDeleteDeposit()
            UndoActionType.POINTS -> undoDeletePointsAccount()
            UndoActionType.NONE -> Unit
        }
    }

    fun saveAccountDeposit(
        existingId: Long? = null,
        accountName: String,
        source: String,
        amountCents: Long,
        timestampMillis: Long,
        note: String
    ) {
        viewModelScope.launch {
            val result = repository.saveAccountDeposit(
                existingId = existingId,
                accountName = accountName,
                source = source,
                amountCents = amountCents,
                timestampMillis = timestampMillis,
                note = note
            )
            result.fold(
                onSuccess = { saved ->
                    val symbol = uiState.value.preferences.currencySymbol
                    val amt = MoneyFormatter.format(saved.amountCents, symbol)
                    val verb = if (existingId != null && existingId > 0L) "Updated funds:" else "Added"
                    _effects.emit(
                        UiEffect.ShowSnackbar(
                            "$verb $amt to ${saved.accountName} (${saved.source})"
                        )
                    )
                },
                onFailure = { err ->
                    _effects.emit(UiEffect.ShowSnackbar(err.localizedMessage ?: "Couldn't add funds"))
                }
            )
        }
    }

    fun deleteAccountDeposit(deposit: AccountDepositEntity) {
        viewModelScope.launch {
            lastDeletedDeposit = deposit
            repository.deleteAccountDeposit(deposit)
            _effects.emit(
                UiEffect.ShowSnackbar(
                    message = "Removed ${deposit.source} from ${deposit.accountName}",
                    undoType = UndoActionType.DEPOSIT
                )
            )
        }
    }

    private fun undoDeleteDeposit() {
        val toRestore = lastDeletedDeposit ?: return
        lastDeletedDeposit = null
        viewModelScope.launch {
            repository.restoreAccountDeposit(toRestore)
        }
    }

    fun savePointsAccount(
        existingId: Long? = null,
        name: String,
        pointsBalance: Long,
        unitLabel: String,
        centsPerPointTimes100: Long,
        note: String
    ) {
        viewModelScope.launch {
            val result = repository.savePointsAccount(
                existingId = existingId,
                name = name,
                pointsBalance = pointsBalance,
                unitLabel = unitLabel,
                centsPerPointTimes100 = centsPerPointTimes100,
                note = note
            )
            result.fold(
                onSuccess = { saved ->
                    val formatted = PointsFormatter.formatWithUnit(saved.pointsBalance, saved.unitLabel)
                    val verb = if (existingId != null && existingId > 0L) "Updated" else "Added"
                    _effects.emit(UiEffect.ShowSnackbar("$verb ${saved.name}: $formatted"))
                },
                onFailure = { err ->
                    _effects.emit(UiEffect.ShowSnackbar(err.localizedMessage ?: "Couldn't save points tracker"))
                }
            )
        }
    }

    fun adjustPointsAccount(account: PointsAccountEntity, deltaPoints: Long) {
        viewModelScope.launch {
            val updated = repository.adjustPointsBalance(account, deltaPoints)
            val formatted = PointsFormatter.formatWithUnit(updated.pointsBalance, updated.unitLabel)
            _effects.emit(UiEffect.ShowSnackbar("${updated.name}: $formatted"))
        }
    }

    fun deletePointsAccount(account: PointsAccountEntity) {
        viewModelScope.launch {
            lastDeletedPointsAccount = account
            repository.deletePointsAccount(account)
            _effects.emit(
                UiEffect.ShowSnackbar(
                    message = "Removed points tracker \"${account.name}\"",
                    undoType = UndoActionType.POINTS
                )
            )
        }
    }

    private fun undoDeletePointsAccount() {
        val toRestore = lastDeletedPointsAccount ?: return
        lastDeletedPointsAccount = null
        viewModelScope.launch {
            repository.restorePointsAccount(toRestore)
        }
    }

    fun onCommitmentFilterChange(type: CommitmentType?) {
        commitmentTypeFilter.value = if (commitmentTypeFilter.value == type) null else type
    }

    fun saveCommitment(
        existingId: Long? = null,
        title: String,
        type: CommitmentType,
        amountCents: Long,
        targetOrTotalCents: Long,
        progressCents: Long,
        cycle: RecurrenceCycle,
        dueDayOfMonth: Int,
        statementDayOfMonth: Int,
        scheduledDateMillis: Long,
        totalInstallments: Int,
        completedInstallments: Int,
        accountName: String,
        category: ExpenseCategory,
        note: String,
        reminderEnabled: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.saveCommitment(
                existingId = existingId,
                title = title,
                type = type,
                amountCents = amountCents,
                targetOrTotalCents = targetOrTotalCents,
                progressCents = progressCents,
                cycle = cycle,
                dueDayOfMonth = dueDayOfMonth,
                statementDayOfMonth = statementDayOfMonth,
                scheduledDateMillis = scheduledDateMillis,
                totalInstallments = totalInstallments,
                completedInstallments = completedInstallments,
                accountName = accountName,
                category = category,
                note = note,
                reminderEnabled = reminderEnabled
            )
            result.fold(
                onSuccess = { saved ->
                    val verb = if (existingId != null) "Updated" else "Saved"
                    _effects.emit(UiEffect.ShowSnackbar("$verb ${type.singularTitle.lowercase()} \"${saved.title}\""))
                },
                onFailure = { err ->
                    _effects.emit(UiEffect.ShowSnackbar(err.localizedMessage ?: "Couldn't save item"))
                }
            )
        }
    }

    fun deleteCommitment(commitment: CommitmentEntity) {
        viewModelScope.launch {
            repository.deleteCommitment(commitment)
            _effects.emit(UiEffect.ShowSnackbar("Deleted \"${commitment.title}\""))
        }
    }

    fun toggleCommitmentActive(commitment: CommitmentEntity) {
        viewModelScope.launch {
            repository.toggleCommitmentActive(commitment)
            val msg = if (commitment.isActive) "Paused \"${commitment.title}\"" else "Resumed \"${commitment.title}\""
            _effects.emit(UiEffect.ShowSnackbar(msg))
        }
    }

    fun recordCommitmentPayment(commitment: CommitmentEntity, logAsExpense: Boolean) {
        viewModelScope.launch {
            val alerts = repository.recordCommitmentPayment(commitment, logAsExpense)
            if (alerts.isNotEmpty()) {
                alertBanner.value = alerts.first()
            }
            val symbol = uiState.value.preferences.currencySymbol
            val formatted = MoneyFormatter.format(commitment.amountCents, symbol)
            val type = CommitmentType.fromKey(commitment.type)
            val msg = when {
                type == CommitmentType.INCOME -> "Marked payday \"$formatted\" from ${commitment.title}"
                logAsExpense -> "Paid & logged $formatted for ${commitment.title}"
                else -> "Recorded $formatted progress on ${commitment.title}"
            }
            _effects.emit(UiEffect.ShowSnackbar(msg))
        }
    }

    fun openNewExpenseSheet(preselectedCategory: ExpenseCategory? = null) {
        _editorState.value = ExpenseEditorState(
            isOpen = true,
            selectedCategory = preselectedCategory,
            isCategoryManuallyChosen = preselectedCategory != null,
            accountName = selectedTodayAccount.value.orEmpty(),
            timestampMillis = System.currentTimeMillis()
        )
        if (preselectedCategory == null) {
            triggerAutoCategorization("", 0L)
        }
    }

    fun openEditExpenseSheet(expense: ExpenseEntity) {
        val cat = ExpenseCategory.fromKey(expense.category)
        _editorState.value = ExpenseEditorState(
            isOpen = true,
            editingId = expense.id,
            editingRemoteId = expense.remoteId,
            amountText = MoneyFormatter.centsToEditText(expense.amountCents),
            description = expense.description,
            selectedCategory = cat,
            isCategoryManuallyChosen = true,
            paymentMethod = PaymentMethod.fromKey(expense.paymentMethod),
            accountName = expense.accountName.trim(),
            timestampMillis = expense.timestampMillis,
            note = expense.note
        )
    }

    fun closeExpenseSheet() {
        _editorState.update { it.copy(isOpen = false, validationError = null) }
    }

    fun onEditorAmountChanged(text: String) {
        val cleaned = text.filter { it.isDigit() || it == '.' }
        val dotCount = cleaned.count { it == '.' }
        if (dotCount > 1) return
        val parts = cleaned.split('.')
        if (parts.size == 2 && parts[1].length > 2) return

        _editorState.update { it.copy(amountText = cleaned, validationError = null) }
        val current = _editorState.value
        if (!current.isCategoryManuallyChosen) {
            val cents = MoneyFormatter.parseCents(cleaned) ?: 0L
            triggerAutoCategorization(current.description, cents)
        }
    }

    fun addQuickAmountDelta(deltaDollars: Int) {
        val currentCents = MoneyFormatter.parseCents(_editorState.value.amountText) ?: 0L
        val nextCents = currentCents + (deltaDollars * 100L)
        onEditorAmountChanged(MoneyFormatter.centsToEditText(nextCents))
    }

    fun onEditorDescriptionChanged(desc: String) {
        _editorState.update { it.copy(description = desc, validationError = null) }
        val current = _editorState.value
        if (!current.isCategoryManuallyChosen) {
            val cents = MoneyFormatter.parseCents(current.amountText) ?: 0L
            triggerAutoCategorization(desc, cents)
        }
    }

    fun onEditorCategorySelected(category: ExpenseCategory) {
        _editorState.update {
            it.copy(
                selectedCategory = category,
                isCategoryManuallyChosen = true,
                validationError = null
            )
        }
    }

    fun onEditorPaymentMethodSelected(method: PaymentMethod) {
        _editorState.update { it.copy(paymentMethod = method) }
    }

    fun onEditorAccountNameChanged(account: String) {
        _editorState.update { it.copy(accountName = account) }
    }

    fun onEditorDateSelected(timestampMillis: Long) {
        _editorState.update { it.copy(timestampMillis = timestampMillis) }
    }

    fun onEditorNoteChanged(note: String) {
        _editorState.update { it.copy(note = note) }
    }

    private fun triggerAutoCategorization(description: String, amountCents: Long) {
        categorizeJob?.cancel()
        categorizeJob = viewModelScope.launch {
            val result = repository.analyzeExpense(description, amountCents)
            _editorState.update { state ->
                if (state.isCategoryManuallyChosen) return@update state
                val autoPicked = if (result.confidence != CategorizationConfidence.UNSURE) {
                    result.suggestedCategory
                } else {
                    null
                }
                state.copy(
                    categorizationResult = result,
                    selectedCategory = autoPicked
                )
            }
        }
    }

    fun saveEditorExpense() {
        val state = _editorState.value
        val cents = MoneyFormatter.parseCents(state.amountText)
        if (cents == null || cents <= 0L) {
            _editorState.update { it.copy(validationError = "Please enter an amount greater than 0.") }
            return
        }
        val category = state.selectedCategory
        if (category == null) {
            _editorState.update {
                it.copy(validationError = "Categorizer is unsure — please select a category above.")
            }
            return
        }

        val wasAuto = !state.isCategoryManuallyChosen &&
            state.categorizationResult?.suggestedCategory == category

        _editorState.update { it.copy(isOpen = false, editingDetectedId = null, validationError = null) }

        viewModelScope.launch {
            val alerts = repository.saveExpense(
                existingId = state.editingId,
                existingRemoteId = state.editingRemoteId,
                description = state.description,
                amountCents = cents,
                category = category,
                paymentMethod = state.paymentMethod,
                accountName = state.accountName,
                timestampMillis = state.timestampMillis,
                note = state.note,
                wasAutoCategorized = wasAuto
            )
            state.editingDetectedId?.let { detectedId ->
                repository.markDetectedTransactionApproved(detectedId)
            }
            if (alerts.isNotEmpty()) {
                alertBanner.value = alerts.first()
            }
            val verb = if (state.editingId != null) "Updated" else "Logged"
            val symbol = uiState.value.preferences.currencySymbol
            _effects.emit(
                UiEffect.ShowSnackbar(
                    "$verb ${MoneyFormatter.format(cents, symbol)} in ${category.displayName}"
                )
            )
        }
    }

    fun logQuickPreset(preset: QuickPreset) {
        val activeAccount = selectedTodayAccount.value.orEmpty()
        viewModelScope.launch {
            val alerts = repository.saveExpense(
                description = preset.description,
                amountCents = preset.amountCents,
                category = preset.category,
                paymentMethod = PaymentMethod.CARD,
                accountName = activeAccount,
                timestampMillis = System.currentTimeMillis(),
                note = "",
                wasAutoCategorized = true
            )
            if (alerts.isNotEmpty()) {
                alertBanner.value = alerts.first()
            }
            val symbol = uiState.value.preferences.currencySymbol
            val acctSuffix = if (activeAccount.isNotBlank()) " on $activeAccount" else ""
            _effects.emit(
                UiEffect.ShowSnackbar(
                    "Logged ${preset.label} (${MoneyFormatter.format(preset.amountCents, symbol)})$acctSuffix"
                )
            )
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            lastDeletedExpense = expense
            repository.deleteExpense(expense)
            if (_editorState.value.editingId == expense.id) {
                _editorState.update { it.copy(isOpen = false) }
            }
            _effects.emit(
                UiEffect.ShowSnackbar(
                    message = "Removed \"${expense.description}\"",
                    undoType = UndoActionType.EXPENSE
                )
            )
        }
    }

    fun undoDeleteExpense() {
        val toRestore = lastDeletedExpense ?: return
        lastDeletedExpense = null
        viewModelScope.launch {
            repository.restoreExpense(toRestore)
        }
    }

    fun setDemoModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDemoModeEnabled(enabled)
            val msg = if (enabled) {
                "Demo Mode active — exploring sample expenses & budgets"
            } else {
                "Exited Demo Mode — sample data cleared"
            }
            _effects.emit(UiEffect.ShowSnackbar(msg))
        }
    }

    fun clearSyncTestFeedback() {
        syncUiState.update { it.copy(testFeedback = null, testSucceeded = false) }
    }

    fun testAppsScriptConnection(url: String, token: String) {
        viewModelScope.launch {
            syncUiState.update { it.copy(isTestingConnection = true, testFeedback = null) }
            val result = repository.testAppsScriptConnection(url, token)
            result.fold(
                onSuccess = { msg ->
                    syncUiState.update {
                        it.copy(isTestingConnection = false, testFeedback = msg, testSucceeded = true)
                    }
                },
                onFailure = { error ->
                    syncUiState.update {
                        it.copy(
                            isTestingConnection = false,
                            testFeedback = error.message ?: "Connection failed",
                            testSucceeded = false
                        )
                    }
                }
            )
        }
    }

    fun saveAndSyncAppsScript(url: String, token: String, autoSync: Boolean) {
        viewModelScope.launch {
            repository.saveAppsScriptConfig(url, token, autoSync)
            if (url.isBlank()) {
                _effects.emit(UiEffect.ShowSnackbar("Disconnected Google Sheet sync"))
                return@launch
            }
            syncNowInternal()
        }
    }

    fun syncWithGoogleSheetNow() {
        viewModelScope.launch {
            syncNowInternal()
        }
    }

    private suspend fun syncNowInternal() {
        syncUiState.update { it.copy(isSyncing = true) }
        val result = repository.syncWithGoogleSheet()
        syncUiState.update { it.copy(isSyncing = false) }
        result.fold(
            onSuccess = { msg -> _effects.emit(UiEffect.ShowSnackbar(msg)) },
            onFailure = { err -> _effects.emit(UiEffect.ShowSnackbar("Sync failed: ${err.message}")) }
        )
    }

    fun setCategoryBudget(category: ExpenseCategory, limitCents: Long) {
        viewModelScope.launch {
            repository.setCategoryBudget(category, limitCents)
            val symbol = uiState.value.preferences.currencySymbol
            val msg = if (limitCents <= 0L) {
                "Removed limit for ${category.displayName}"
            } else {
                "Set ${category.displayName} budget to ${MoneyFormatter.format(limitCents, symbol, compactWhole = true)}/mo"
            }
            _effects.emit(UiEffect.ShowSnackbar(msg))
        }
    }

    fun applySuggestedBudgets() {
        viewModelScope.launch {
            repository.applySuggestedCategoryBudgets()
            _effects.emit(UiEffect.ShowSnackbar("Applied starter monthly category limits"))
        }
    }

    fun updateBudgetSettings(
        monthlyBudgetCents: Long,
        monthlySavingsGoalCents: Long,
        dailyMode: DailyBudgetMode,
        fixedDailyLimitCents: Long
    ) {
        viewModelScope.launch {
            repository.updateBudgetSettings(
                monthlyBudgetCents = monthlyBudgetCents,
                monthlySavingsGoalCents = monthlySavingsGoalCents,
                dailyMode = dailyMode,
                fixedDailyLimitCents = fixedDailyLimitCents
            )
            _effects.emit(UiEffect.ShowSnackbar("Updated monthly budget plan"))
        }
    }

    fun updateTodayUseAccountsBalance(useAccounts: Boolean) {
        viewModelScope.launch {
            repository.updateTodayUseAccountsBalance(useAccounts)
            val msg = if (useAccounts) {
                "Today screen now uses money in your accounts"
            } else {
                "Today screen switched to monthly budget plan"
            }
            _effects.emit(UiEffect.ShowSnackbar(msg))
        }
    }

    fun updateCurrencySymbol(symbol: String) {
        viewModelScope.launch {
            repository.updateCurrencySymbol(symbol)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotificationsEnabled(enabled)
        }
    }

    fun updateGeminiAssistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateGeminiAssistEnabled(enabled)
            if (!enabled) {
                cancelVoiceRecording()
                geminiUiState.update {
                    it.copy(
                        isParsing = false,
                        isRecording = false,
                        recordingSeconds = 0,
                        audioAmplitudeNormalized = 0f,
                        errorMessage = null,
                        lastLoggedSummary = null
                    )
                }
            }
        }
    }

    fun onGeminiPromptChanged(text: String) {
        geminiUiState.update { it.copy(promptText = text, errorMessage = null) }
    }

    fun clearGeminiFeedback() {
        geminiUiState.update { it.copy(errorMessage = null, lastLoggedSummary = null) }
    }

    fun onVoicePermissionDenied() {
        geminiUiState.update {
            it.copy(
                isRecording = false,
                errorMessage = "Microphone permission is required to record expenses by voice. You can still type your expense above."
            )
        }
    }

    fun startVoiceRecording() {
        val current = geminiUiState.value
        if (current.isRecording || current.isParsing) return

        val startResult = voiceRecorder.startRecording()
        startResult.fold(
            onSuccess = {
                geminiUiState.update {
                    it.copy(
                        isRecording = true,
                        recordingSeconds = 0,
                        audioAmplitudeNormalized = 0f,
                        errorMessage = null,
                        lastLoggedSummary = null
                    )
                }
                recordingTickerJob?.cancel()
                recordingTickerJob = viewModelScope.launch {
                    val startMs = System.currentTimeMillis()
                    while (isActive && geminiUiState.value.isRecording) {
                        val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                        val amp = voiceRecorder.readNormalizedAmplitude()
                        geminiUiState.update {
                            it.copy(
                                recordingSeconds = elapsedSec,
                                audioAmplitudeNormalized = amp
                            )
                        }
                        if (elapsedSec >= 60) {
                            stopAndProcessVoiceRecording()
                            break
                        }
                        delay(120L)
                    }
                }
            },
            onFailure = { err ->
                geminiUiState.update {
                    it.copy(
                        isRecording = false,
                        errorMessage = err.localizedMessage ?: "Couldn't access the microphone."
                    )
                }
            }
        )
    }

    fun cancelVoiceRecording() {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        voiceRecorder.cancelRecording()
        geminiUiState.update {
            it.copy(
                isRecording = false,
                recordingSeconds = 0,
                audioAmplitudeNormalized = 0f
            )
        }
    }

    fun stopAndProcessVoiceRecording() {
        if (!geminiUiState.value.isRecording) return
        recordingTickerJob?.cancel()
        recordingTickerJob = null

        val stopResult = voiceRecorder.stopRecording()
        geminiUiState.update {
            it.copy(
                isRecording = false,
                recordingSeconds = 0,
                audioAmplitudeNormalized = 0f
            )
        }

        stopResult.fold(
            onSuccess = { recorded ->
                viewModelScope.launch {
                    geminiUiState.update {
                        it.copy(
                            isParsing = true,
                            parsingStatusText = "Listening & logging with Gemini...",
                            errorMessage = null,
                            lastLoggedSummary = null
                        )
                    }
                    val existingAccountNames = uiState.value.accounts.map { it.name }
                    val result = geminiParser.parseAudioExpense(
                        audioBytes = recorded.bytes,
                        mimeType = recorded.mimeType,
                        existingAccounts = existingAccountNames,
                        onStatusUpdate = { status ->
                            geminiUiState.update { it.copy(parsingStatusText = status) }
                        }
                    )
                    handleParsedGeminiResult(result, fromVoice = true)
                }
            },
            onFailure = { err ->
                geminiUiState.update {
                    it.copy(
                        isParsing = false,
                        errorMessage = err.localizedMessage ?: "Voice recording failed. Please try again."
                    )
                }
            }
        )
    }

    fun submitNaturalLanguageExpense(promptOverride: String? = null) {
        val rawPrompt = (promptOverride ?: geminiUiState.value.promptText).trim()
        if (rawPrompt.isBlank()) {
            geminiUiState.update {
                it.copy(errorMessage = "Type what you bought, how much it cost, or tap the mic to speak.")
            }
            return
        }
        if (geminiUiState.value.isParsing || geminiUiState.value.isRecording) return

        viewModelScope.launch {
            geminiUiState.update {
                it.copy(
                    promptText = rawPrompt,
                    isParsing = true,
                    parsingStatusText = "Parsing expense with Gemini...",
                    errorMessage = null,
                    lastLoggedSummary = null
                )
            }

            val existingAccountNames = uiState.value.accounts.map { it.name }
            val result = geminiParser.parseNaturalLanguageExpense(
                userPrompt = rawPrompt,
                existingAccounts = existingAccountNames,
                onStatusUpdate = { status ->
                    geminiUiState.update { it.copy(parsingStatusText = status) }
                }
            )
            handleParsedGeminiResult(result, fromVoice = false)
        }
    }

    private suspend fun handleParsedGeminiResult(
        result: Result<ParsedNaturalExpense>,
        fromVoice: Boolean
    ) {
        result.fold(
            onSuccess = { parsed ->
                if (parsed.isHighOrMediumConfidence && parsed.amountCents > 0L) {
                    val effectiveAccount = parsed.accountName.ifBlank {
                        selectedTodayAccount.value.orEmpty()
                    }
                    val alerts = repository.saveExpense(
                        description = parsed.description,
                        amountCents = parsed.amountCents,
                        category = parsed.category,
                        paymentMethod = parsed.paymentMethod,
                        accountName = effectiveAccount,
                        timestampMillis = System.currentTimeMillis(),
                        note = parsed.note,
                        wasAutoCategorized = true
                    )
                    if (alerts.isNotEmpty()) {
                        alertBanner.value = alerts.first()
                    }
                    val symbol = uiState.value.preferences.currencySymbol
                    val formattedAmt = MoneyFormatter.format(parsed.amountCents, symbol)
                    val heardPrefix = if (fromVoice && !parsed.spokenTranscript.isNullOrBlank()) {
                        "Heard: \"${parsed.spokenTranscript}\"\n"
                    } else ""
                    val accountSuffix = if (effectiveAccount.isNotBlank()) {
                        " • Account: $effectiveAccount"
                    } else ""
                    val summaryLine = "${heardPrefix}Logged $formattedAmt • ${parsed.description} • ${parsed.category.displayName}$accountSuffix"
                    geminiUiState.update {
                        it.copy(
                            promptText = if (fromVoice) it.promptText else "",
                            isParsing = false,
                            errorMessage = null,
                            lastLoggedSummary = summaryLine
                        )
                    }
                    val snackText = if (effectiveAccount.isNotBlank()) {
                        "Logged $formattedAmt to $effectiveAccount"
                    } else {
                        "Logged $formattedAmt (${parsed.description})"
                    }
                    _effects.emit(UiEffect.ShowSnackbar(snackText))
                } else {
                    geminiUiState.update { it.copy(isParsing = false, errorMessage = null) }
                    _editorState.value = ExpenseEditorState(
                        isOpen = true,
                        amountText = if (parsed.amountCents > 0L) {
                            MoneyFormatter.centsToEditText(parsed.amountCents)
                        } else "",
                        description = parsed.description,
                        selectedCategory = parsed.category,
                        isCategoryManuallyChosen = false,
                        paymentMethod = parsed.paymentMethod,
                        accountName = parsed.accountName,
                        timestampMillis = System.currentTimeMillis(),
                        note = parsed.note.ifBlank {
                            if (fromVoice && !parsed.spokenTranscript.isNullOrBlank()) {
                                "Voice: ${parsed.spokenTranscript}"
                            } else ""
                        },
                        validationError = parsed.clarificationHint
                            ?: "Please confirm the amount or category before saving."
                    )
                }
            },
            onFailure = { error ->
                geminiUiState.update {
                    it.copy(
                        isParsing = false,
                        errorMessage = error.localizedMessage
                            ?: "Gemini couldn't process this request. Check your connection and try again."
                    )
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        recordingTickerJob?.cancel()
        voiceRecorder.cancelRecording()
    }

    fun sendTestNotification() {
        repository.sendTestNotification(uiState.value.preferences.currencySymbol)
        viewModelScope.launch {
            if (repository.hasNotificationPermission()) {
                _effects.emit(UiEffect.ShowSnackbar("Test budget alert notification sent"))
            } else {
                _effects.emit(UiEffect.ShowSnackbar("Enable notification permission to see system alerts"))
            }
        }
    }

    private fun filterExpenses(
        expenses: List<ExpenseEntity>,
        query: String,
        category: ExpenseCategory?,
        accountName: String?,
        timeframe: ActivityTimeframe,
        nowMillis: Long
    ): List<ExpenseEntity> {
        val todayStart = DateUtils.startOfDayMillis(nowMillis)
        val weekStart = DateUtils.lastNDaysStarts(7, nowMillis).first()
        val monthStart = DateUtils.startOfMonthMillis(nowMillis)
        val todayEnd = DateUtils.endOfDayMillis(nowMillis)
        val cleanQuery = query.trim().lowercase()

        return expenses.filter { item ->
            val matchesTime = when (timeframe) {
                ActivityTimeframe.TODAY -> item.timestampMillis in todayStart..todayEnd
                ActivityTimeframe.WEEK -> item.timestampMillis in weekStart..todayEnd
                ActivityTimeframe.MONTH -> item.timestampMillis in monthStart..todayEnd
                ActivityTimeframe.ALL -> true
            }
            val matchesCategory = category == null || ExpenseCategory.fromKey(item.category) == category
            val matchesAccount = accountName == null || item.accountName.equals(accountName, ignoreCase = true)
            val matchesQuery = cleanQuery.isBlank() ||
                item.description.lowercase().contains(cleanQuery) ||
                item.note.lowercase().contains(cleanQuery) ||
                item.accountName.lowercase().contains(cleanQuery) ||
                ExpenseCategory.fromKey(item.category).displayName.lowercase().contains(cleanQuery)

            matchesTime && matchesCategory && matchesAccount && matchesQuery
        }
    }

    private fun filterTransfers(
        transfers: List<AccountTransferEntity>,
        query: String,
        category: ExpenseCategory?,
        accountName: String?,
        timeframe: ActivityTimeframe,
        nowMillis: Long
    ): List<AccountTransferEntity> {
        if (category != null) return emptyList()
        val todayStart = DateUtils.startOfDayMillis(nowMillis)
        val weekStart = DateUtils.lastNDaysStarts(7, nowMillis).first()
        val monthStart = DateUtils.startOfMonthMillis(nowMillis)
        val todayEnd = DateUtils.endOfDayMillis(nowMillis)
        val cleanQuery = query.trim().lowercase()

        return transfers.filter { item ->
            val matchesTime = when (timeframe) {
                ActivityTimeframe.TODAY -> item.timestampMillis in todayStart..todayEnd
                ActivityTimeframe.WEEK -> item.timestampMillis in weekStart..todayEnd
                ActivityTimeframe.MONTH -> item.timestampMillis in monthStart..todayEnd
                ActivityTimeframe.ALL -> true
            }
            val matchesAccount = accountName == null ||
                item.fromAccountName.equals(accountName, ignoreCase = true) ||
                item.toAccountName.equals(accountName, ignoreCase = true)
            val matchesQuery = cleanQuery.isBlank() ||
                item.fromAccountName.lowercase().contains(cleanQuery) ||
                item.toAccountName.lowercase().contains(cleanQuery) ||
                item.note.lowercase().contains(cleanQuery) ||
                "transfer".contains(cleanQuery)

            matchesTime && matchesAccount && matchesQuery
        }
    }

    private fun filterDeposits(
        deposits: List<AccountDepositEntity>,
        query: String,
        category: ExpenseCategory?,
        accountName: String?,
        timeframe: ActivityTimeframe,
        nowMillis: Long
    ): List<AccountDepositEntity> {
        if (category != null) return emptyList()
        val todayStart = DateUtils.startOfDayMillis(nowMillis)
        val weekStart = DateUtils.lastNDaysStarts(7, nowMillis).first()
        val monthStart = DateUtils.startOfMonthMillis(nowMillis)
        val todayEnd = DateUtils.endOfDayMillis(nowMillis)
        val cleanQuery = query.trim().lowercase()

        return deposits.filter { item ->
            val matchesTime = when (timeframe) {
                ActivityTimeframe.TODAY -> item.timestampMillis in todayStart..todayEnd
                ActivityTimeframe.WEEK -> item.timestampMillis in weekStart..todayEnd
                ActivityTimeframe.MONTH -> item.timestampMillis in monthStart..todayEnd
                ActivityTimeframe.ALL -> true
            }
            val matchesAccount = accountName == null ||
                item.accountName.equals(accountName, ignoreCase = true)
            val matchesQuery = cleanQuery.isBlank() ||
                item.accountName.lowercase().contains(cleanQuery) ||
                item.source.lowercase().contains(cleanQuery) ||
                item.note.lowercase().contains(cleanQuery) ||
                "funds".contains(cleanQuery) ||
                "deposit".contains(cleanQuery)

            matchesTime && matchesAccount && matchesQuery
        }
    }

    private data class DataSourcesTuple(
        val expenses: List<ExpenseEntity>,
        val budgets: List<CategoryBudgetEntity>,
        val accounts: List<AccountEntity>,
        val commitments: List<CommitmentEntity>,
        val prefs: BudgetPreferences
    )

    private data class FilterTuple(
        val query: String,
        val category: ExpenseCategory?,
        val timeframe: ActivityTimeframe,
        val activityAccount: String?,
        val todayAccount: String?
    )

    private data class StatusTuple(
        val banner: BudgetAlertEvent?,
        val hasPermission: Boolean,
        val sync: SyncUiState,
        val gemini: GeminiQuickLogState,
        val commitmentFilter: CommitmentType?,
        val activeRepoRequest: String?
    )

    private data class BankDetectionTuple(
        val pendingDetected: List<DetectedTransactionEntity>,
        val hasListenerAccess: Boolean,
        val transfers: List<AccountTransferEntity>,
        val pointsAccounts: List<PointsAccountEntity>,
        val deposits: List<AccountDepositEntity>
    )
}
