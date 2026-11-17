package com.example.data

import android.content.Context
import com.example.model.BankMessageParser
import com.example.model.CategorizationResult
import com.example.model.CommitmentScheduleHelper
import com.example.model.CommitmentType
import com.example.model.DailyBudgetMode
import com.example.model.ExpenseCategorizer
import com.example.model.ExpenseCategory
import com.example.model.ParsedBankMessage
import com.example.model.PaymentMethod
import com.example.model.RecurrenceCycle
import com.example.notifications.BudgetAlertEvent
import com.example.notifications.BudgetNotificationHelper
import com.example.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed class BankMessageProcessOutcome {
    data object Ignored : BankMessageProcessOutcome()
    data object Duplicate : BankMessageProcessOutcome()
    data class QueuedForReview(val entity: DetectedTransactionEntity) : BankMessageProcessOutcome()
    data class AutoLogged(val entity: DetectedTransactionEntity, val alerts: List<BudgetAlertEvent>) : BankMessageProcessOutcome()
}

class SpendWiseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryBudgetDao: CategoryBudgetDao,
    private val accountDao: AccountDao,
    private val accountTransferDao: AccountTransferDao,
    private val pointsAccountDao: PointsAccountDao,
    private val commitmentDao: CommitmentDao,
    private val detectedTransactionDao: DetectedTransactionDao,
    private val preferencesRepository: BudgetPreferencesRepository,
    private val notificationHelper: BudgetNotificationHelper,
    private val syncClient: AppsScriptSyncClient = AppsScriptSyncClient()
) {
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.observeAllExpenses()
    val categoryBudgets: Flow<List<CategoryBudgetEntity>> = categoryBudgetDao.observeAllCategoryBudgets()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.observeAllAccounts()
    val allTransfers: Flow<List<AccountTransferEntity>> = accountTransferDao.observeAllTransfers()
    val allPointsAccounts: Flow<List<PointsAccountEntity>> = pointsAccountDao.observeAllPointsAccounts()
    val allCommitments: Flow<List<CommitmentEntity>> = commitmentDao.observeAllCommitments()
    val pendingDetectedTransactions: Flow<List<DetectedTransactionEntity>> = detectedTransactionDao.observePendingDetected()
    val preferences: Flow<BudgetPreferences> = preferencesRepository.preferencesFlow

    companion object {
        fun create(context: Context): SpendWiseRepository {
            val appCtx = context.applicationContext
            val db = SpendWiseDatabase.getInstance(appCtx)
            return SpendWiseRepository(
                expenseDao = db.expenseDao(),
                categoryBudgetDao = db.categoryBudgetDao(),
                accountDao = db.accountDao(),
                accountTransferDao = db.accountTransferDao(),
                pointsAccountDao = db.pointsAccountDao(),
                commitmentDao = db.commitmentDao(),
                detectedTransactionDao = db.detectedTransactionDao(),
                preferencesRepository = BudgetPreferencesRepository(appCtx),
                notificationHelper = BudgetNotificationHelper(appCtx)
            )
        }
    }

    suspend fun analyzeExpense(description: String, amountCents: Long): CategorizationResult {
        val recent = expenseDao.getRecentExpensesForHistory()
        val learnedHistory = recent
            .filter { it.description.isNotBlank() }
            .associate { it.description.trim().lowercase() to ExpenseCategory.fromKey(it.category) }
        return ExpenseCategorizer.analyze(
            description = description,
            amountCents = amountCents,
            learnedHistory = learnedHistory
        )
    }

    suspend fun saveExpense(
        existingId: Long? = null,
        existingRemoteId: String? = null,
        description: String,
        amountCents: Long,
        category: ExpenseCategory,
        paymentMethod: PaymentMethod,
        accountName: String = "",
        timestampMillis: Long,
        note: String,
        wasAutoCategorized: Boolean
    ): List<BudgetAlertEvent> {
        val trimmedAccount = accountName.trim()
        val matchedAccount = if (trimmedAccount.isNotBlank()) {
            ensureAccountExists(trimmedAccount)
        } else ""

        val entity = ExpenseEntity(
            id = existingId ?: 0L,
            remoteId = existingRemoteId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            description = description.trim().ifBlank { category.displayName },
            amountCents = amountCents,
            category = category.name,
            paymentMethod = paymentMethod.name,
            accountName = matchedAccount,
            timestampMillis = timestampMillis,
            note = note.trim(),
            wasAutoCategorized = wasAutoCategorized,
            isDemo = false
        )
        if (existingId != null && existingId > 0L) {
            expenseDao.updateExpense(entity)
        } else {
            expenseDao.insertExpense(entity)
        }
        val alerts = evaluateAndNotifyBudgets(category)

        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.isSheetConnected && prefs.autoSyncEnabled && !prefs.isDemoMode) {
            syncWithGoogleSheet()
        }
        return alerts
    }

    suspend fun saveAccount(
        existingId: Long? = null,
        name: String,
        initialBalanceCents: Long
    ): Result<AccountEntity> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter an account name."))
        }

        val duplicate = accountDao.getAccountByNameIgnoreCase(cleanName)
        if (existingId != null && existingId > 0L) {
            val current = accountDao.getAccountById(existingId)
                ?: return Result.failure(IllegalStateException("Account not found."))
            if (duplicate != null && duplicate.id != existingId) {
                return Result.failure(IllegalArgumentException("An account named \"$cleanName\" already exists."))
            }
            if (!current.name.equals(cleanName, ignoreCase = false)) {
                expenseDao.renameAccountExpenses(current.name, cleanName)
                commitmentDao.renameAccountCommitments(current.name, cleanName)
                accountTransferDao.renameFromAccount(current.name, cleanName)
                accountTransferDao.renameToAccount(current.name, cleanName)
            }
            val updated = current.copy(
                name = cleanName,
                initialBalanceCents = initialBalanceCents.coerceAtLeast(0L)
            )
            accountDao.updateAccount(updated)
            return Result.success(updated)
        } else {
            if (duplicate != null) {
                return Result.failure(IllegalArgumentException("An account named \"${duplicate.name}\" already exists."))
            }
            val created = AccountEntity(
                name = cleanName,
                initialBalanceCents = initialBalanceCents.coerceAtLeast(0L),
                isDemo = false
            )
            val newId = accountDao.insertAccount(created)
            return Result.success(created.copy(id = newId))
        }
    }

    suspend fun deleteAccount(account: AccountEntity) {
        accountDao.deleteAccountById(account.id)
        expenseDao.clearAccountFromExpenses(account.name)
        commitmentDao.clearAccountFromCommitments(account.name)
    }

    suspend fun saveAccountTransfer(
        existingId: Long? = null,
        fromAccountName: String,
        toAccountName: String,
        amountCents: Long,
        timestampMillis: Long,
        note: String
    ): Result<AccountTransferEntity> {
        val fromClean = fromAccountName.trim()
        val toClean = toAccountName.trim()
        if (fromClean.isBlank()) {
            return Result.failure(IllegalArgumentException("Please choose or enter a source account."))
        }
        if (toClean.isBlank()) {
            return Result.failure(IllegalArgumentException("Please choose or enter a destination account."))
        }
        if (fromClean.equals(toClean, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Source and destination accounts must be different."))
        }
        if (amountCents <= 0L) {
            return Result.failure(IllegalArgumentException("Please enter a transfer amount greater than 0."))
        }

        val resolvedFrom = ensureAccountExists(fromClean)
        val resolvedTo = ensureAccountExists(toClean)

        val entity = AccountTransferEntity(
            id = existingId ?: 0L,
            fromAccountName = resolvedFrom,
            toAccountName = resolvedTo,
            amountCents = amountCents,
            timestampMillis = timestampMillis,
            note = note.trim(),
            isDemo = false
        )

        return if (existingId != null && existingId > 0L) {
            accountTransferDao.updateTransfer(entity)
            Result.success(entity)
        } else {
            val id = accountTransferDao.insertTransfer(entity)
            Result.success(entity.copy(id = id))
        }
    }

    suspend fun deleteAccountTransfer(transfer: AccountTransferEntity) {
        accountTransferDao.deleteTransferById(transfer.id)
    }

    suspend fun restoreAccountTransfer(transfer: AccountTransferEntity) {
        accountTransferDao.insertTransfer(transfer.copy(id = 0L))
    }

    suspend fun savePointsAccount(
        existingId: Long? = null,
        name: String,
        pointsBalance: Long,
        unitLabel: String,
        centsPerPointTimes100: Long,
        note: String
    ): Result<PointsAccountEntity> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a points tracker name."))
        }
        val cleanUnit = unitLabel.trim().ifBlank { "pts" }
        val safePoints = pointsBalance.coerceAtLeast(0L)
        val safeRate = centsPerPointTimes100.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        val duplicate = pointsAccountDao.getPointsAccountByNameIgnoreCase(cleanName)

        if (existingId != null && existingId > 0L) {
            val current = pointsAccountDao.getPointsAccountById(existingId)
                ?: return Result.failure(IllegalStateException("Points tracker not found."))
            if (duplicate != null && duplicate.id != existingId) {
                return Result.failure(IllegalArgumentException("A points tracker named \"$cleanName\" already exists."))
            }
            val updated = current.copy(
                name = cleanName,
                pointsBalance = safePoints,
                unitLabel = cleanUnit,
                centsPerPointTimes100 = safeRate,
                note = note.trim(),
                updatedAtMillis = now
            )
            pointsAccountDao.updatePointsAccount(updated)
            return Result.success(updated)
        } else {
            if (duplicate != null) {
                return Result.failure(IllegalArgumentException("A points tracker named \"${duplicate.name}\" already exists."))
            }
            val created = PointsAccountEntity(
                name = cleanName,
                pointsBalance = safePoints,
                unitLabel = cleanUnit,
                centsPerPointTimes100 = safeRate,
                note = note.trim(),
                updatedAtMillis = now,
                createdAtMillis = now,
                isDemo = false
            )
            val id = pointsAccountDao.insertPointsAccount(created)
            return Result.success(created.copy(id = id))
        }
    }

    suspend fun adjustPointsBalance(
        account: PointsAccountEntity,
        deltaPoints: Long
    ): PointsAccountEntity {
        val nextPoints = (account.pointsBalance + deltaPoints).coerceAtLeast(0L)
        val updated = account.copy(
            pointsBalance = nextPoints,
            updatedAtMillis = System.currentTimeMillis()
        )
        pointsAccountDao.updatePointsAccount(updated)
        return updated
    }

    suspend fun deletePointsAccount(account: PointsAccountEntity) {
        pointsAccountDao.deletePointsAccountById(account.id)
    }

    suspend fun restorePointsAccount(account: PointsAccountEntity) {
        pointsAccountDao.insertPointsAccount(account.copy(id = 0L))
    }

    suspend fun saveCommitment(
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
    ): Result<CommitmentEntity> {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a name."))
        }
        if (amountCents <= 0L) {
            return Result.failure(IllegalArgumentException("Please enter an amount greater than 0."))
        }

        val trimmedAccount = accountName.trim()
        val resolvedAccount = if (trimmedAccount.isNotBlank()) {
            ensureAccountExists(trimmedAccount)
        } else ""

        val existing = existingId?.takeIf { it > 0L }?.let { commitmentDao.getCommitmentById(it) }
        val entity = CommitmentEntity(
            id = existing?.id ?: 0L,
            title = cleanTitle,
            type = type.name,
            amountCents = amountCents,
            targetOrTotalCents = targetOrTotalCents.coerceAtLeast(0L),
            progressCents = progressCents.coerceAtLeast(0L),
            cycle = cycle.name,
            dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31),
            statementDayOfMonth = if (type == CommitmentType.CREDIT_CARD) {
                statementDayOfMonth.coerceIn(0, 31)
            } else 0,
            scheduledDateMillis = scheduledDateMillis,
            totalInstallments = totalInstallments.coerceAtLeast(0),
            completedInstallments = completedInstallments.coerceAtLeast(0),
            accountName = resolvedAccount,
            category = category.name,
            note = note.trim(),
            reminderEnabled = reminderEnabled,
            isActive = existing?.isActive ?: true,
            lastCompletedCycleKey = existing?.lastCompletedCycleKey.orEmpty(),
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
        )

        return if (existing != null) {
            commitmentDao.updateCommitment(entity)
            Result.success(entity)
        } else {
            val id = commitmentDao.insertCommitment(entity)
            Result.success(entity.copy(id = id))
        }
    }

    suspend fun deleteCommitment(commitment: CommitmentEntity) {
        commitmentDao.deleteCommitmentById(commitment.id)
    }

    suspend fun toggleCommitmentActive(commitment: CommitmentEntity) {
        commitmentDao.updateCommitment(commitment.copy(isActive = !commitment.isActive))
    }

    suspend fun recordCommitmentPayment(
        commitment: CommitmentEntity,
        logAsExpense: Boolean
    ): List<BudgetAlertEvent> {
        val now = System.currentTimeMillis()
        val type = CommitmentType.fromKey(commitment.type)
        val status = CommitmentScheduleHelper.evaluate(commitment, now)

        val nextProgress = (commitment.progressCents + commitment.amountCents)
            .let { if (commitment.targetOrTotalCents > 0L) it.coerceAtMost(commitment.targetOrTotalCents) else it }
        val nextInstallments = if (type == CommitmentType.INSTALLMENT) {
            val next = commitment.completedInstallments + 1
            if (commitment.totalInstallments > 0) next.coerceAtMost(commitment.totalInstallments) else next
        } else {
            commitment.completedInstallments
        }

        commitmentDao.updateCommitment(
            commitment.copy(
                progressCents = nextProgress,
                completedInstallments = nextInstallments,
                lastCompletedCycleKey = status.cycleKey
            )
        )

        if (!logAsExpense) {
            return emptyList()
        }

        val cat = ExpenseCategory.fromKey(commitment.category)
        val paymentMethod = when (type) {
            CommitmentType.CREDIT_CARD -> PaymentMethod.CARD
            CommitmentType.SUBSCRIPTION -> PaymentMethod.CARD
            CommitmentType.DEBT, CommitmentType.BILL, CommitmentType.INSTALLMENT -> PaymentMethod.BANK
            CommitmentType.GOAL, CommitmentType.INCOME -> PaymentMethod.BANK
        }
        return saveExpense(
            description = commitment.title,
            amountCents = commitment.amountCents,
            category = cat,
            paymentMethod = paymentMethod,
            accountName = commitment.accountName,
            timestampMillis = now,
            note = "${type.singularTitle}${if (commitment.note.isNotBlank()) " • ${commitment.note}" else ""}",
            wasAutoCategorized = false
        )
    }

    private suspend fun ensureAccountExists(rawName: String, isDemo: Boolean = false): String {
        val clean = rawName.trim()
        if (clean.isBlank()) return ""
        val existing = accountDao.getAccountByNameIgnoreCase(clean)
        return if (existing != null) {
            existing.name
        } else {
            accountDao.insertAccount(
                AccountEntity(
                    name = clean,
                    initialBalanceCents = 0L,
                    isDemo = isDemo
                )
            )
            clean
        }
    }

    suspend fun restoreExpense(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense.copy(id = 0L))
        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.isSheetConnected && prefs.autoSyncEnabled && !expense.isDemo && !prefs.isDemoMode) {
            syncWithGoogleSheet()
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpenseById(expense.id)
        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.isSheetConnected && prefs.autoSyncEnabled && !expense.isDemo && expense.remoteId.isNotBlank()) {
            syncClient.deleteRemoteExpense(prefs.appsScriptUrl, prefs.appsScriptToken, expense.remoteId)
        }
    }

    suspend fun setCategoryBudget(category: ExpenseCategory, monthlyLimitCents: Long) {
        if (monthlyLimitCents <= 0L) {
            categoryBudgetDao.deleteBudget(category.name)
        } else {
            categoryBudgetDao.upsertBudget(
                CategoryBudgetEntity(
                    category = category.name,
                    monthlyLimitCents = monthlyLimitCents,
                    alert80SentMonth = "",
                    alert100SentMonth = ""
                )
            )
        }
        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.isSheetConnected && prefs.autoSyncEnabled && !prefs.isDemoMode) {
            syncWithGoogleSheet()
        }
    }

    suspend fun applySuggestedCategoryBudgets() {
        val budgets = listOf(
            ExpenseCategory.GROCERIES,
            ExpenseCategory.FOOD_DINING,
            ExpenseCategory.HOUSING,
            ExpenseCategory.UTILITIES,
            ExpenseCategory.TRANSPORTATION,
            ExpenseCategory.ENTERTAINMENT
        ).map { cat ->
            CategoryBudgetEntity(
                category = cat.name,
                monthlyLimitCents = cat.suggestedMonthlyCents
            )
        }
        categoryBudgetDao.upsertBudgets(budgets)
    }

    suspend fun setDemoModeEnabled(enabled: Boolean) {
        if (enabled) {
            if (expenseDao.countDemoExpenses() == 0) {
                expenseDao.insertExpenses(DemoDataSeeder.buildDemoExpenses())
            }
            if (categoryBudgetDao.getAllCategoryBudgetsOnce().isEmpty()) {
                categoryBudgetDao.upsertBudgets(DemoDataSeeder.buildDemoBudgets())
            }
            preferencesRepository.setDemoMode(true)
        } else {
            expenseDao.deleteDemoExpenses()
            accountDao.deleteDemoAccounts()
            accountTransferDao.deleteDemoTransfers()
            pointsAccountDao.deleteDemoPointsAccounts()
            preferencesRepository.setDemoMode(false)
        }
    }

    suspend fun testAppsScriptConnection(url: String, token: String): Result<String> {
        if (!syncClient.isValidAppsScriptUrl(url)) {
            return Result.failure(IllegalArgumentException("Please enter a valid HTTPS Apps Script Web App URL."))
        }
        return syncClient.ping(url, token)
    }

    suspend fun saveAppsScriptConfig(url: String, token: String, autoSync: Boolean) {
        preferencesRepository.updateAppsScriptConfig(url, token, autoSync)
    }

    suspend fun syncWithGoogleSheet(): Result<String> {
        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.appsScriptUrl.isBlank()) {
            return Result.failure(IllegalStateException("No Google Apps Script URL configured."))
        }

        val localExpenses = expenseDao.getNonDemoExpenses()
        val localBudgets = categoryBudgetDao.getAllCategoryBudgetsOnce()

        val result = syncClient.syncAll(
            scriptUrl = prefs.appsScriptUrl,
            token = prefs.appsScriptToken,
            localExpenses = localExpenses,
            localBudgets = localBudgets
        )

        return result.fold(
            onSuccess = { syncResult ->
                // Upsert remote expenses that don't yet exist locally
                for (remote in syncResult.remoteExpenses) {
                    val syncedAccount = if (remote.accountName.isNotBlank()) {
                        ensureAccountExists(remote.accountName)
                    } else ""
                    val existing = expenseDao.getByRemoteId(remote.remoteId)
                    if (existing == null) {
                        expenseDao.insertExpense(
                            ExpenseEntity(
                                remoteId = remote.remoteId,
                                description = remote.description,
                                amountCents = remote.amountCents,
                                category = ExpenseCategory.fromKey(remote.category).name,
                                paymentMethod = PaymentMethod.fromKey(remote.paymentMethod).name,
                                accountName = syncedAccount,
                                timestampMillis = remote.timestampMillis,
                                note = remote.note,
                                wasAutoCategorized = remote.wasAutoCategorized,
                                isDemo = false
                            )
                        )
                    } else if (existing.description != remote.description ||
                        existing.amountCents != remote.amountCents ||
                        existing.category != remote.category ||
                        existing.accountName != syncedAccount ||
                        existing.note != remote.note
                    ) {
                        expenseDao.updateExpense(
                            existing.copy(
                                description = remote.description,
                                amountCents = remote.amountCents,
                                category = ExpenseCategory.fromKey(remote.category).name,
                                paymentMethod = PaymentMethod.fromKey(remote.paymentMethod).name,
                                accountName = syncedAccount,
                                timestampMillis = remote.timestampMillis,
                                note = remote.note
                            )
                        )
                    }
                }

                // Merge remote category budgets if not set locally
                val localBudgetMap = localBudgets.associateBy { it.category }
                val toUpsert = syncResult.remoteBudgets.mapNotNull { rb ->
                    val normalizedKey = ExpenseCategory.fromKey(rb.category).name
                    val existing = localBudgetMap[normalizedKey]
                    if (existing == null) {
                        CategoryBudgetEntity(normalizedKey, rb.monthlyLimitCents)
                    } else if (existing.monthlyLimitCents != rb.monthlyLimitCents) {
                        existing.copy(monthlyLimitCents = rb.monthlyLimitCents)
                    } else null
                }
                if (toUpsert.isNotEmpty()) {
                    categoryBudgetDao.upsertBudgets(toUpsert)
                }

                preferencesRepository.updateSyncStatus(
                    timestampMillis = System.currentTimeMillis(),
                    message = syncResult.message
                )
                Result.success(syncResult.message)
            },
            onFailure = { error ->
                val msg = error.message ?: "Sync failed"
                preferencesRepository.updateSyncStatus(
                    timestampMillis = prefs.lastSyncTimestampMillis,
                    message = "Error: $msg"
                )
                Result.failure(error)
            }
        )
    }

    suspend fun updateBudgetSettings(
        monthlyBudgetCents: Long,
        monthlySavingsGoalCents: Long,
        dailyMode: DailyBudgetMode,
        fixedDailyLimitCents: Long
    ) {
        preferencesRepository.updateBudgetSettings(
            monthlyBudgetCents = monthlyBudgetCents,
            monthlySavingsGoalCents = monthlySavingsGoalCents,
            dailyMode = dailyMode,
            fixedDailyLimitCents = fixedDailyLimitCents
        )
    }

    suspend fun updateCurrencySymbol(symbol: String) {
        preferencesRepository.updateCurrencySymbol(symbol)
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        preferencesRepository.updateNotificationsEnabled(enabled)
    }

    suspend fun updateGeminiAssistEnabled(enabled: Boolean) {
        preferencesRepository.updateGeminiAssistEnabled(enabled)
    }

    suspend fun updateBankDetectionSettings(
        detectionEnabled: Boolean,
        autoLogEnabled: Boolean,
        defaultAccountName: String
    ) {
        preferencesRepository.updateBankDetectionSettings(
            detectionEnabled = detectionEnabled,
            autoLogEnabled = autoLogEnabled,
            defaultAccountName = defaultAccountName
        )
    }

    suspend fun processIncomingBankMessage(
        sourcePackage: String,
        sourceAppLabel: String,
        rawTitle: String,
        rawBody: String,
        respectToggle: Boolean = true
    ): BankMessageProcessOutcome {
        val prefs = preferencesRepository.getCurrentPreferences()
        if (respectToggle && !prefs.bankDetectionEnabled) {
            return BankMessageProcessOutcome.Ignored
        }

        val recent = expenseDao.getRecentExpensesForHistory()
        val learnedHistory = recent
            .filter { it.description.isNotBlank() }
            .associate { it.description.trim().lowercase() to ExpenseCategory.fromKey(it.category) }

        val now = System.currentTimeMillis()
        val parsed = BankMessageParser.parse(
            title = rawTitle,
            body = rawBody,
            sourceAppLabel = sourceAppLabel,
            learnedHistory = learnedHistory,
            nowMillis = now
        ) ?: return BankMessageProcessOutcome.Ignored

        // Deduplicate identical charge messages within a 5-minute window
        val existingCount = detectedTransactionDao.countByFingerprintSince(
            fingerprint = parsed.fingerprint,
            sinceMillis = now - 300_000L
        )
        if (existingCount > 0) {
            return BankMessageProcessOutcome.Duplicate
        }

        val matchedAccount = resolveAccountForBankCharge(parsed, rawTitle, rawBody, prefs)
        val shouldAutoLog = prefs.bankAutoLogEnabled

        val initialEntity = DetectedTransactionEntity(
            fingerprint = parsed.fingerprint,
            sourcePackage = sourcePackage,
            sourceAppLabel = parsed.sourceLabel,
            rawTitle = rawTitle.trim(),
            rawMessage = rawBody.trim(),
            merchant = parsed.merchant,
            amountCents = parsed.amountCents,
            suggestedCategory = parsed.suggestedCategory.name,
            paymentMethod = parsed.paymentMethod.name,
            cardLastFour = parsed.cardLastFour,
            matchedAccountName = matchedAccount,
            status = if (shouldAutoLog) DetectedTransactionStatus.APPROVED.name else DetectedTransactionStatus.PENDING.name,
            receivedAtMillis = now
        )
        val insertedId = detectedTransactionDao.insertDetected(initialEntity)
        val savedDetected = initialEntity.copy(id = insertedId)

        if (shouldAutoLog) {
            val cardNote = buildDetectedNote(parsed)
            val alerts = saveExpense(
                description = parsed.merchant,
                amountCents = parsed.amountCents,
                category = parsed.suggestedCategory,
                paymentMethod = parsed.paymentMethod,
                accountName = matchedAccount,
                timestampMillis = now,
                note = cardNote,
                wasAutoCategorized = true
            )
            if (prefs.notificationsEnabled && respectToggle) {
                notificationHelper.sendBankChargeDetectedNotification(
                    merchant = parsed.merchant,
                    amountCents = parsed.amountCents,
                    currencySymbol = prefs.currencySymbol,
                    category = parsed.suggestedCategory,
                    autoLogged = true,
                    accountName = matchedAccount
                )
            }
            return BankMessageProcessOutcome.AutoLogged(savedDetected, alerts)
        } else {
            if (prefs.notificationsEnabled && respectToggle) {
                notificationHelper.sendBankChargeDetectedNotification(
                    merchant = parsed.merchant,
                    amountCents = parsed.amountCents,
                    currencySymbol = prefs.currencySymbol,
                    category = parsed.suggestedCategory,
                    autoLogged = false,
                    accountName = matchedAccount
                )
            }
            return BankMessageProcessOutcome.QueuedForReview(savedDetected)
        }
    }

    suspend fun approveDetectedTransaction(
        item: DetectedTransactionEntity,
        categoryOverride: ExpenseCategory? = null,
        accountOverride: String? = null,
        paymentMethodOverride: PaymentMethod? = null
    ): List<BudgetAlertEvent> {
        val category = categoryOverride ?: ExpenseCategory.fromKey(item.suggestedCategory)
        val method = paymentMethodOverride ?: PaymentMethod.fromKey(item.paymentMethod)
        val account = accountOverride?.trim() ?: item.matchedAccountName.trim()
        val note = buildString {
            append("Detected via ${item.sourceAppLabel}")
            if (item.cardLastFour.isNotBlank()) {
                append(" (•••• ${item.cardLastFour})")
            }
        }
        val alerts = saveExpense(
            description = item.merchant,
            amountCents = item.amountCents,
            category = category,
            paymentMethod = method,
            accountName = account,
            timestampMillis = item.receivedAtMillis,
            note = note,
            wasAutoCategorized = categoryOverride == null
        )
        detectedTransactionDao.updateStatus(item.id, DetectedTransactionStatus.APPROVED.name)
        return alerts
    }

    suspend fun markDetectedTransactionApproved(id: Long) {
        if (id > 0L) {
            detectedTransactionDao.updateStatus(id, DetectedTransactionStatus.APPROVED.name)
        }
    }

    suspend fun dismissDetectedTransaction(id: Long) {
        detectedTransactionDao.updateStatus(id, DetectedTransactionStatus.DISMISSED.name)
    }

    private fun buildDetectedNote(parsed: ParsedBankMessage): String {
        return buildString {
            append("Auto-detected via ${parsed.sourceLabel}")
            if (parsed.cardLastFour.isNotBlank()) {
                append(" (•••• ${parsed.cardLastFour})")
            }
        }
    }

    private suspend fun resolveAccountForBankCharge(
        parsed: ParsedBankMessage,
        rawTitle: String,
        rawBody: String,
        prefs: BudgetPreferences
    ): String {
        val customAccounts = accountDao.getAllAccountsOnce()
        if (customAccounts.isEmpty()) return ""

        // 1. Match by card last-4 digits if present in any user-created account name (e.g. "Visa 4821")
        if (parsed.cardLastFour.isNotBlank()) {
            val byLast4 = customAccounts.firstOrNull { it.name.contains(parsed.cardLastFour) }
            if (byLast4 != null) return byLast4.name
        }

        // 2. Match by explicit custom account name mentioned in notification title/body
        val combinedLower = "$rawTitle $rawBody".lowercase()
        val byName = customAccounts
            .sortedByDescending { it.name.length }
            .firstOrNull { acct ->
                acct.name.length >= 3 && combinedLower.contains(acct.name.trim().lowercase())
            }
        if (byName != null) return byName.name

        // 3. Fallback to user-configured default custom account if it still exists
        if (prefs.bankDefaultAccountName.isNotBlank()) {
            val defaultAcct = customAccounts.firstOrNull {
                it.name.equals(prefs.bankDefaultAccountName, ignoreCase = true)
            }
            if (defaultAcct != null) return defaultAcct.name
        }
        return ""
    }

    fun hasNotificationPermission(): Boolean = notificationHelper.hasNotificationPermission()

    fun sendTestNotification(currencySymbol: String) {
        notificationHelper.sendTestNotification(currencySymbol)
    }

    private suspend fun evaluateAndNotifyBudgets(category: ExpenseCategory): List<BudgetAlertEvent> {
        val prefs = preferencesRepository.getCurrentPreferences()
        val now = System.currentTimeMillis()
        val monthKey = DateUtils.currentMonthKey(now)
        val monthStart = DateUtils.startOfMonthMillis(now)
        val todayEnd = DateUtils.endOfDayMillis(now)
        val monthExpenses = expenseDao.getExpensesInRange(monthStart, todayEnd)
        val alerts = mutableListOf<BudgetAlertEvent>()

        val catBudget = categoryBudgetDao.getBudgetForCategory(category.name)
        if (catBudget != null && catBudget.monthlyLimitCents > 0L) {
            val catSpent = monthExpenses
                .filter { ExpenseCategory.fromKey(it.category) == category }
                .sumOf { it.amountCents }
            val ratio = catSpent.toDouble() / catBudget.monthlyLimitCents.toDouble()

            if (ratio >= 1.0 && catBudget.alert100SentMonth != monthKey) {
                if (prefs.notificationsEnabled) {
                    val event = notificationHelper.sendCategoryBudgetNotification(
                        category = category,
                        spentCents = catSpent,
                        limitCents = catBudget.monthlyLimitCents,
                        currencySymbol = prefs.currencySymbol,
                        isExceeded = true
                    )
                    alerts.add(event)
                }
                categoryBudgetDao.markAlert100Sent(category.name, monthKey)
            } else if (ratio >= 0.8 && ratio < 1.0 && catBudget.alert80SentMonth != monthKey) {
                if (prefs.notificationsEnabled) {
                    val event = notificationHelper.sendCategoryBudgetNotification(
                        category = category,
                        spentCents = catSpent,
                        limitCents = catBudget.monthlyLimitCents,
                        currencySymbol = prefs.currencySymbol,
                        isExceeded = false
                    )
                    alerts.add(event)
                }
                categoryBudgetDao.markAlert80Sent(category.name, monthKey)
            }
        }

        val spendableBudget = (prefs.monthlyBudgetCents - prefs.monthlySavingsGoalCents).coerceAtLeast(0L)
        if (spendableBudget > 0L) {
            val totalMonthSpent = monthExpenses.sumOf { it.amountCents }
            val ratio = totalMonthSpent.toDouble() / spendableBudget.toDouble()
            if (ratio >= 1.0 && prefs.overallAlert100Month != monthKey) {
                if (prefs.notificationsEnabled) {
                    alerts.add(
                        notificationHelper.sendOverallBudgetNotification(
                            spentCents = totalMonthSpent,
                            budgetCents = spendableBudget,
                            currencySymbol = prefs.currencySymbol,
                            isExceeded = true
                        )
                    )
                }
                preferencesRepository.markOverallAlertSent(monthKey, isExceeded = true)
            } else if (ratio >= 0.8 && ratio < 1.0 && prefs.overallAlert80Month != monthKey) {
                if (prefs.notificationsEnabled) {
                    alerts.add(
                        notificationHelper.sendOverallBudgetNotification(
                            spentCents = totalMonthSpent,
                            budgetCents = spendableBudget,
                            currencySymbol = prefs.currencySymbol,
                            isExceeded = false
                        )
                    )
                }
                preferencesRepository.markOverallAlertSent(monthKey, isExceeded = false)
            }
        }

        return alerts
    }
}
