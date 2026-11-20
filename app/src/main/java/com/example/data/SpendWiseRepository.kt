package com.example.data

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

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
    private val syncClient: AppsScriptSyncClient = AppsScriptSyncClient(),
    private val accountDepositDao: AccountDepositDao? = null
) {
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.observeAllExpenses()
    val categoryBudgets: Flow<List<CategoryBudgetEntity>> = categoryBudgetDao.observeAllCategoryBudgets()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.observeAllAccounts()
    val allTransfers: Flow<List<AccountTransferEntity>> = accountTransferDao.observeAllTransfers()
    val allDeposits: Flow<List<AccountDepositEntity>> =
        accountDepositDao?.observeAllDeposits() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val allPointsAccounts: Flow<List<PointsAccountEntity>> = pointsAccountDao.observeAllPointsAccounts()
    val allCommitments: Flow<List<CommitmentEntity>> = commitmentDao.observeAllCommitments()
    val pendingDetectedTransactions: Flow<List<DetectedTransactionEntity>> = detectedTransactionDao.observePendingDetected()
    val preferences: Flow<BudgetPreferences> = preferencesRepository.preferencesFlow

    private val requestIdCounter = AtomicLong(0L)
    private val activeRequestQueue = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val activeRequestMessage: Flow<String?> = activeRequestQueue.map { it.lastOrNull()?.second }

    private suspend fun <T> withActiveRequest(message: String, block: suspend () -> T): T {
        val id = requestIdCounter.incrementAndGet()
        activeRequestQueue.update { it + (id to message) }
        return try {
            block()
        } finally {
            activeRequestQueue.update { list -> list.filterNot { it.first == id } }
        }
    }

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
                notificationHelper = BudgetNotificationHelper(appCtx),
                accountDepositDao = db.accountDepositDao()
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

    private suspend fun triggerAutoSyncIfEnabled() {
        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.isSheetConnected && prefs.autoSyncEnabled && !prefs.isDemoMode) {
            syncWithGoogleSheet()
        }
    }

    private suspend fun deleteRemoteRecordIfAutoSync(
        recordType: String,
        remoteId: String,
        isDemo: Boolean = false
    ) {
        if (isDemo || remoteId.isBlank()) return
        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.isSheetConnected && prefs.autoSyncEnabled && !prefs.isDemoMode) {
            withActiveRequest("Updating Google Sheet...") {
                syncClient.deleteRemoteRecord(
                    scriptUrl = prefs.appsScriptUrl,
                    token = prefs.appsScriptToken,
                    recordType = recordType,
                    remoteId = remoteId
                )
            }
        }
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
        val now = System.currentTimeMillis()
        val trimmedAccount = accountName.trim()
        val matchedAccount = if (trimmedAccount.isNotBlank()) {
            ensureAccountExists(trimmedAccount)
        } else ""

        val resolvedRemoteId = existingRemoteId?.takeIf { it.isNotBlank() }
            ?: existingId?.takeIf { it > 0L }?.let { id ->
                expenseDao.getNonDemoExpenses().firstOrNull { it.id == id }?.remoteId
            }
            ?: UUID.randomUUID().toString()

        val entity = ExpenseEntity(
            id = existingId ?: 0L,
            remoteId = resolvedRemoteId,
            description = description.trim().ifBlank { category.displayName },
            amountCents = amountCents,
            category = category.name,
            paymentMethod = paymentMethod.name,
            accountName = matchedAccount,
            timestampMillis = timestampMillis,
            note = note.trim(),
            wasAutoCategorized = wasAutoCategorized,
            isDemo = false,
            updatedAtMillis = now
        )
        if (existingId != null && existingId > 0L) {
            expenseDao.updateExpense(entity)
        } else {
            expenseDao.insertExpense(entity)
        }
        val alerts = evaluateAndNotifyBudgets(category)
        triggerAutoSyncIfEnabled()
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
        val now = System.currentTimeMillis()
        val duplicate = accountDao.getAccountByNameIgnoreCase(cleanName)
        if (existingId != null && existingId > 0L) {
            val current = accountDao.getAccountById(existingId)
                ?: return Result.failure(IllegalStateException("Account not found."))
            if (duplicate != null && duplicate.id != existingId) {
                return Result.failure(IllegalArgumentException("An account named \"$cleanName\" already exists."))
            }
            if (!current.name.equals(cleanName, ignoreCase = false)) {
                expenseDao.renameAccountExpenses(current.name, cleanName, now)
                commitmentDao.renameAccountCommitments(current.name, cleanName, now)
                accountTransferDao.renameFromAccount(current.name, cleanName, now)
                accountTransferDao.renameToAccount(current.name, cleanName, now)
                accountDepositDao?.renameAccountDeposits(current.name, cleanName, now)
            }
            val updated = current.copy(
                remoteId = current.remoteId.ifBlank { UUID.randomUUID().toString() },
                name = cleanName,
                initialBalanceCents = initialBalanceCents.coerceAtLeast(0L),
                updatedAtMillis = now
            )
            accountDao.updateAccount(updated)
            triggerAutoSyncIfEnabled()
            return Result.success(updated)
        } else {
            if (duplicate != null) {
                return Result.failure(IllegalArgumentException("An account named \"${duplicate.name}\" already exists."))
            }
            val created = AccountEntity(
                remoteId = UUID.randomUUID().toString(),
                name = cleanName,
                initialBalanceCents = initialBalanceCents.coerceAtLeast(0L),
                createdAtMillis = now,
                updatedAtMillis = now,
                isDemo = false
            )
            val newId = accountDao.insertAccount(created)
            triggerAutoSyncIfEnabled()
            return Result.success(created.copy(id = newId))
        }
    }

    suspend fun deleteAccount(account: AccountEntity) {
        val now = System.currentTimeMillis()
        accountDao.deleteAccountById(account.id)
        expenseDao.clearAccountFromExpenses(account.name, now)
        commitmentDao.clearAccountFromCommitments(account.name, now)
        accountDepositDao?.clearAccountFromDeposits(account.name, now)
        deleteRemoteRecordIfAutoSync("account", account.remoteId, account.isDemo)
        triggerAutoSyncIfEnabled()
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

        val now = System.currentTimeMillis()
        val resolvedFrom = ensureAccountExists(fromClean)
        val resolvedTo = ensureAccountExists(toClean)
        val existingRemoteId = existingId?.takeIf { it > 0L }?.let { id ->
            accountTransferDao.getNonDemoTransfers().firstOrNull { it.id == id }?.remoteId
        }

        val entity = AccountTransferEntity(
            id = existingId ?: 0L,
            remoteId = existingRemoteId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            fromAccountName = resolvedFrom,
            toAccountName = resolvedTo,
            amountCents = amountCents,
            timestampMillis = timestampMillis,
            note = note.trim(),
            isDemo = false,
            updatedAtMillis = now
        )

        val saved = if (existingId != null && existingId > 0L) {
            accountTransferDao.updateTransfer(entity)
            entity
        } else {
            val id = accountTransferDao.insertTransfer(entity)
            entity.copy(id = id)
        }
        triggerAutoSyncIfEnabled()
        return Result.success(saved)
    }

    suspend fun deleteAccountTransfer(transfer: AccountTransferEntity) {
        accountTransferDao.deleteTransferById(transfer.id)
        deleteRemoteRecordIfAutoSync("transfer", transfer.remoteId, transfer.isDemo)
    }

    suspend fun restoreAccountTransfer(transfer: AccountTransferEntity) {
        accountTransferDao.insertTransfer(
            transfer.copy(
                id = 0L,
                remoteId = transfer.remoteId.ifBlank { UUID.randomUUID().toString() },
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        if (!transfer.isDemo) triggerAutoSyncIfEnabled()
    }

    suspend fun saveAccountDeposit(
        existingId: Long? = null,
        accountName: String,
        source: String,
        amountCents: Long,
        timestampMillis: Long,
        note: String
    ): Result<AccountDepositEntity> {
        val dao = accountDepositDao
            ?: return Result.failure(IllegalStateException("Account deposits storage is unavailable."))
        val cleanAccount = accountName.trim()
        val cleanSource = source.trim()
        if (cleanAccount.isBlank()) {
            return Result.failure(IllegalArgumentException("Please choose or enter a destination account."))
        }
        if (cleanSource.isBlank()) {
            return Result.failure(IllegalArgumentException("Please describe where this money is coming from."))
        }
        if (amountCents <= 0L) {
            return Result.failure(IllegalArgumentException("Please enter an amount greater than 0."))
        }

        val now = System.currentTimeMillis()
        val resolvedAccount = ensureAccountExists(cleanAccount)
        val existingRemoteId = existingId?.takeIf { it > 0L }?.let { id ->
            dao.getNonDemoDeposits().firstOrNull { it.id == id }?.remoteId
        }
        val entity = AccountDepositEntity(
            id = existingId ?: 0L,
            remoteId = existingRemoteId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            accountName = resolvedAccount,
            source = cleanSource,
            amountCents = amountCents,
            timestampMillis = timestampMillis,
            note = note.trim(),
            isDemo = false,
            updatedAtMillis = now
        )

        val saved = if (existingId != null && existingId > 0L) {
            dao.updateDeposit(entity)
            entity
        } else {
            val id = dao.insertDeposit(entity)
            entity.copy(id = id)
        }
        triggerAutoSyncIfEnabled()
        return Result.success(saved)
    }

    suspend fun deleteAccountDeposit(deposit: AccountDepositEntity) {
        accountDepositDao?.deleteDepositById(deposit.id)
        deleteRemoteRecordIfAutoSync("deposit", deposit.remoteId, deposit.isDemo)
    }

    suspend fun restoreAccountDeposit(deposit: AccountDepositEntity) {
        accountDepositDao?.insertDeposit(
            deposit.copy(
                id = 0L,
                remoteId = deposit.remoteId.ifBlank { UUID.randomUUID().toString() },
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        if (!deposit.isDemo) triggerAutoSyncIfEnabled()
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
                remoteId = current.remoteId.ifBlank { UUID.randomUUID().toString() },
                name = cleanName,
                pointsBalance = safePoints,
                unitLabel = cleanUnit,
                centsPerPointTimes100 = safeRate,
                note = note.trim(),
                updatedAtMillis = now
            )
            pointsAccountDao.updatePointsAccount(updated)
            triggerAutoSyncIfEnabled()
            return Result.success(updated)
        } else {
            if (duplicate != null) {
                return Result.failure(IllegalArgumentException("A points tracker named \"${duplicate.name}\" already exists."))
            }
            val created = PointsAccountEntity(
                remoteId = UUID.randomUUID().toString(),
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
            triggerAutoSyncIfEnabled()
            return Result.success(created.copy(id = id))
        }
    }

    suspend fun adjustPointsBalance(
        account: PointsAccountEntity,
        deltaPoints: Long
    ): PointsAccountEntity {
        val nextPoints = (account.pointsBalance + deltaPoints).coerceAtLeast(0L)
        val updated = account.copy(
            remoteId = account.remoteId.ifBlank { UUID.randomUUID().toString() },
            pointsBalance = nextPoints,
            updatedAtMillis = System.currentTimeMillis()
        )
        pointsAccountDao.updatePointsAccount(updated)
        triggerAutoSyncIfEnabled()
        return updated
    }

    suspend fun deletePointsAccount(account: PointsAccountEntity) {
        pointsAccountDao.deletePointsAccountById(account.id)
        deleteRemoteRecordIfAutoSync("points", account.remoteId, account.isDemo)
    }

    suspend fun restorePointsAccount(account: PointsAccountEntity) {
        pointsAccountDao.insertPointsAccount(
            account.copy(
                id = 0L,
                remoteId = account.remoteId.ifBlank { UUID.randomUUID().toString() },
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        if (!account.isDemo) triggerAutoSyncIfEnabled()
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

        val now = System.currentTimeMillis()
        val trimmedAccount = accountName.trim()
        val resolvedAccount = if (trimmedAccount.isNotBlank()) {
            ensureAccountExists(trimmedAccount)
        } else ""

        val existing = existingId?.takeIf { it > 0L }?.let { commitmentDao.getCommitmentById(it) }
        val entity = CommitmentEntity(
            id = existing?.id ?: 0L,
            remoteId = existing?.remoteId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
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
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now
        )

        val saved = if (existing != null) {
            commitmentDao.updateCommitment(entity)
            entity
        } else {
            val id = commitmentDao.insertCommitment(entity)
            entity.copy(id = id)
        }
        triggerAutoSyncIfEnabled()
        return Result.success(saved)
    }

    suspend fun deleteCommitment(commitment: CommitmentEntity) {
        commitmentDao.deleteCommitmentById(commitment.id)
        deleteRemoteRecordIfAutoSync("commitment", commitment.remoteId)
    }

    suspend fun toggleCommitmentActive(commitment: CommitmentEntity) {
        commitmentDao.updateCommitment(
            commitment.copy(
                remoteId = commitment.remoteId.ifBlank { UUID.randomUUID().toString() },
                isActive = !commitment.isActive,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        triggerAutoSyncIfEnabled()
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
                remoteId = commitment.remoteId.ifBlank { UUID.randomUUID().toString() },
                progressCents = nextProgress,
                completedInstallments = nextInstallments,
                lastCompletedCycleKey = status.cycleKey,
                updatedAtMillis = now
            )
        )

        if (!logAsExpense) {
            triggerAutoSyncIfEnabled()
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
            val now = System.currentTimeMillis()
            accountDao.insertAccount(
                AccountEntity(
                    remoteId = UUID.randomUUID().toString(),
                    name = clean,
                    initialBalanceCents = 0L,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    isDemo = isDemo
                )
            )
            clean
        }
    }

    suspend fun restoreExpense(expense: ExpenseEntity) {
        expenseDao.insertExpense(
            expense.copy(
                id = 0L,
                remoteId = expense.remoteId.ifBlank { UUID.randomUUID().toString() },
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        if (!expense.isDemo) triggerAutoSyncIfEnabled()
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpenseById(expense.id)
        deleteRemoteRecordIfAutoSync("expense", expense.remoteId, expense.isDemo)
    }

    suspend fun setCategoryBudget(category: ExpenseCategory, monthlyLimitCents: Long) {
        if (monthlyLimitCents <= 0L) {
            categoryBudgetDao.deleteBudget(category.name)
            deleteRemoteRecordIfAutoSync("budget", category.name)
        } else {
            categoryBudgetDao.upsertBudget(
                CategoryBudgetEntity(
                    category = category.name,
                    monthlyLimitCents = monthlyLimitCents,
                    alert80SentMonth = "",
                    alert100SentMonth = "",
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            triggerAutoSyncIfEnabled()
        }
    }

    suspend fun applySuggestedCategoryBudgets() {
        val now = System.currentTimeMillis()
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
                monthlyLimitCents = cat.suggestedMonthlyCents,
                updatedAtMillis = now
            )
        }
        categoryBudgetDao.upsertBudgets(budgets)
        triggerAutoSyncIfEnabled()
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
            accountDepositDao?.deleteDemoDeposits()
            pointsAccountDao.deleteDemoPointsAccounts()
            preferencesRepository.setDemoMode(false)
        }
    }

    suspend fun testAppsScriptConnection(url: String, token: String): Result<String> {
        if (!syncClient.isValidAppsScriptUrl(url)) {
            return Result.failure(IllegalArgumentException("Please enter a valid HTTPS Apps Script Web App URL."))
        }
        return withActiveRequest("Testing Google Sheet connection...") {
            syncClient.ping(url, token)
        }
    }

    suspend fun saveAppsScriptConfig(url: String, token: String, autoSync: Boolean) {
        preferencesRepository.updateAppsScriptConfig(url, token, autoSync)
    }

    private suspend fun ensureRemoteIdsPopulated(): SyncLocalSnapshot {
        val expenses = expenseDao.getNonDemoExpenses().map { exp ->
            if (exp.remoteId.isBlank()) {
                val fixed = exp.copy(remoteId = UUID.randomUUID().toString())
                expenseDao.updateExpense(fixed)
                fixed
            } else exp
        }
        val accounts = accountDao.getNonDemoAccounts().map { acct ->
            if (acct.remoteId.isBlank()) {
                val fixed = acct.copy(remoteId = UUID.randomUUID().toString())
                accountDao.updateAccount(fixed)
                fixed
            } else acct
        }
        val deposits = accountDepositDao?.getNonDemoDeposits()?.map { dep ->
            if (dep.remoteId.isBlank()) {
                val fixed = dep.copy(remoteId = UUID.randomUUID().toString())
                accountDepositDao.updateDeposit(fixed)
                fixed
            } else dep
        }.orEmpty()
        val transfers = accountTransferDao.getNonDemoTransfers().map { tr ->
            if (tr.remoteId.isBlank()) {
                val fixed = tr.copy(remoteId = UUID.randomUUID().toString())
                accountTransferDao.updateTransfer(fixed)
                fixed
            } else tr
        }
        val commitments = commitmentDao.getAllCommitmentsOnce().map { c ->
            if (c.remoteId.isBlank()) {
                val fixed = c.copy(remoteId = UUID.randomUUID().toString())
                commitmentDao.updateCommitment(fixed)
                fixed
            } else c
        }
        val pointsAccounts = pointsAccountDao.getNonDemoPointsAccounts().map { p ->
            if (p.remoteId.isBlank()) {
                val fixed = p.copy(remoteId = UUID.randomUUID().toString())
                pointsAccountDao.updatePointsAccount(fixed)
                fixed
            } else p
        }
        val budgets = categoryBudgetDao.getAllCategoryBudgetsOnce()
        return SyncLocalSnapshot(
            expenses = expenses,
            budgets = budgets,
            accounts = accounts,
            deposits = deposits,
            transfers = transfers,
            commitments = commitments,
            pointsAccounts = pointsAccounts
        )
    }

    private data class SyncLocalSnapshot(
        val expenses: List<ExpenseEntity>,
        val budgets: List<CategoryBudgetEntity>,
        val accounts: List<AccountEntity>,
        val deposits: List<AccountDepositEntity>,
        val transfers: List<AccountTransferEntity>,
        val commitments: List<CommitmentEntity>,
        val pointsAccounts: List<PointsAccountEntity>
    )

    suspend fun syncWithGoogleSheet(): Result<String> {
        val prefs = preferencesRepository.getCurrentPreferences()
        if (prefs.appsScriptUrl.isBlank()) {
            return Result.failure(IllegalStateException("No Google Apps Script URL configured."))
        }

        return withActiveRequest("Syncing with Google Sheet...") {
            val snapshot = ensureRemoteIdsPopulated()

            val result = syncClient.syncAll(
                scriptUrl = prefs.appsScriptUrl,
                token = prefs.appsScriptToken,
                localExpenses = snapshot.expenses,
                localBudgets = snapshot.budgets,
                localAccounts = snapshot.accounts,
                localDeposits = snapshot.deposits,
                localTransfers = snapshot.transfers,
                localCommitments = snapshot.commitments,
                localPointsAccounts = snapshot.pointsAccounts,
                localSettings = prefs
            )

            result.fold(
            onSuccess = { syncResult ->
                // 1. Merge Custom Accounts first so expenses/deposits/transfers/plans link cleanly
                for (remote in syncResult.remoteAccounts) {
                    val byRemote = accountDao.getAccountByRemoteId(remote.remoteId)
                    val byName = if (byRemote == null) accountDao.getAccountByNameIgnoreCase(remote.name) else null
                    val existing = byRemote ?: byName
                    if (existing == null) {
                        accountDao.insertAccount(
                            AccountEntity(
                                remoteId = remote.remoteId,
                                name = remote.name,
                                initialBalanceCents = remote.initialBalanceCents.coerceAtLeast(0L),
                                createdAtMillis = remote.createdAtMillis,
                                updatedAtMillis = remote.updatedAtMillis,
                                isDemo = false
                            )
                        )
                    } else if (existing.remoteId != remote.remoteId ||
                        (remote.updatedAtMillis >= existing.updatedAtMillis &&
                            (existing.name != remote.name ||
                                existing.initialBalanceCents != remote.initialBalanceCents))
                    ) {
                        val shouldTakeRemoteValues = remote.updatedAtMillis >= existing.updatedAtMillis
                        val targetName = if (shouldTakeRemoteValues) remote.name else existing.name
                        if (shouldTakeRemoteValues && !existing.name.equals(remote.name, ignoreCase = false)) {
                            expenseDao.renameAccountExpenses(existing.name, remote.name, remote.updatedAtMillis)
                            commitmentDao.renameAccountCommitments(existing.name, remote.name, remote.updatedAtMillis)
                            accountTransferDao.renameFromAccount(existing.name, remote.name, remote.updatedAtMillis)
                            accountTransferDao.renameToAccount(existing.name, remote.name, remote.updatedAtMillis)
                            accountDepositDao?.renameAccountDeposits(existing.name, remote.name, remote.updatedAtMillis)
                        }
                        accountDao.updateAccount(
                            existing.copy(
                                remoteId = remote.remoteId,
                                name = targetName,
                                initialBalanceCents = if (shouldTakeRemoteValues) {
                                    remote.initialBalanceCents.coerceAtLeast(0L)
                                } else existing.initialBalanceCents,
                                updatedAtMillis = maxOf(existing.updatedAtMillis, remote.updatedAtMillis)
                            )
                        )
                    }
                }

                // 2. Upsert remote expenses
                for (remote in syncResult.remoteExpenses) {
                    val syncedAccount = if (remote.accountName.isNotBlank()) {
                        ensureAccountExists(remote.accountName)
                    } else ""
                    val existing = expenseDao.getByRemoteId(remote.remoteId)
                    val normalizedCategory = ExpenseCategory.fromKey(remote.category).name
                    val normalizedMethod = PaymentMethod.fromKey(remote.paymentMethod).name
                    if (existing == null) {
                        expenseDao.insertExpense(
                            ExpenseEntity(
                                remoteId = remote.remoteId,
                                description = remote.description,
                                amountCents = remote.amountCents,
                                category = normalizedCategory,
                                paymentMethod = normalizedMethod,
                                accountName = syncedAccount,
                                timestampMillis = remote.timestampMillis,
                                note = remote.note,
                                wasAutoCategorized = remote.wasAutoCategorized,
                                isDemo = false,
                                updatedAtMillis = remote.updatedAtMillis
                            )
                        )
                    } else if (remote.updatedAtMillis >= existing.updatedAtMillis &&
                        (existing.description != remote.description ||
                            existing.amountCents != remote.amountCents ||
                            existing.category != normalizedCategory ||
                            existing.paymentMethod != normalizedMethod ||
                            existing.accountName != syncedAccount ||
                            existing.timestampMillis != remote.timestampMillis ||
                            existing.note != remote.note)
                    ) {
                        expenseDao.updateExpense(
                            existing.copy(
                                description = remote.description,
                                amountCents = remote.amountCents,
                                category = normalizedCategory,
                                paymentMethod = normalizedMethod,
                                accountName = syncedAccount,
                                timestampMillis = remote.timestampMillis,
                                note = remote.note,
                                updatedAtMillis = remote.updatedAtMillis
                            )
                        )
                    }
                }

                // 3. Merge remote category budgets
                val localBudgetMap = snapshot.budgets.associateBy { it.category }
                val toUpsertBudgets = syncResult.remoteBudgets.mapNotNull { rb ->
                    val normalizedKey = ExpenseCategory.fromKey(rb.category).name
                    val existing = localBudgetMap[normalizedKey]
                    if (existing == null) {
                        CategoryBudgetEntity(
                            category = normalizedKey,
                            monthlyLimitCents = rb.monthlyLimitCents,
                            updatedAtMillis = rb.updatedAtMillis
                        )
                    } else if (rb.updatedAtMillis >= existing.updatedAtMillis &&
                        existing.monthlyLimitCents != rb.monthlyLimitCents
                    ) {
                        existing.copy(
                            monthlyLimitCents = rb.monthlyLimitCents,
                            updatedAtMillis = rb.updatedAtMillis
                        )
                    } else null
                }
                if (toUpsertBudgets.isNotEmpty()) {
                    categoryBudgetDao.upsertBudgets(toUpsertBudgets)
                }

                // 4. Merge Added Funds / Deposits
                if (accountDepositDao != null) {
                    for (remote in syncResult.remoteDeposits) {
                        val syncedAccount = ensureAccountExists(remote.accountName)
                        val existing = accountDepositDao.getByRemoteId(remote.remoteId)
                        if (existing == null) {
                            accountDepositDao.insertDeposit(
                                AccountDepositEntity(
                                    remoteId = remote.remoteId,
                                    accountName = syncedAccount,
                                    source = remote.source,
                                    amountCents = remote.amountCents,
                                    timestampMillis = remote.timestampMillis,
                                    note = remote.note,
                                    isDemo = false,
                                    updatedAtMillis = remote.updatedAtMillis
                                )
                            )
                        } else if (remote.updatedAtMillis >= existing.updatedAtMillis &&
                            (existing.accountName != syncedAccount ||
                                existing.source != remote.source ||
                                existing.amountCents != remote.amountCents ||
                                existing.timestampMillis != remote.timestampMillis ||
                                existing.note != remote.note)
                        ) {
                            accountDepositDao.updateDeposit(
                                existing.copy(
                                    accountName = syncedAccount,
                                    source = remote.source,
                                    amountCents = remote.amountCents,
                                    timestampMillis = remote.timestampMillis,
                                    note = remote.note,
                                    updatedAtMillis = remote.updatedAtMillis
                                )
                            )
                        }
                    }
                }

                // 5. Merge Internal Account Transfers
                for (remote in syncResult.remoteTransfers) {
                    val syncedFrom = ensureAccountExists(remote.fromAccountName)
                    val syncedTo = ensureAccountExists(remote.toAccountName)
                    val existing = accountTransferDao.getByRemoteId(remote.remoteId)
                    if (existing == null) {
                        accountTransferDao.insertTransfer(
                            AccountTransferEntity(
                                remoteId = remote.remoteId,
                                fromAccountName = syncedFrom,
                                toAccountName = syncedTo,
                                amountCents = remote.amountCents,
                                timestampMillis = remote.timestampMillis,
                                note = remote.note,
                                isDemo = false,
                                updatedAtMillis = remote.updatedAtMillis
                            )
                        )
                    } else if (remote.updatedAtMillis >= existing.updatedAtMillis &&
                        (existing.fromAccountName != syncedFrom ||
                            existing.toAccountName != syncedTo ||
                            existing.amountCents != remote.amountCents ||
                            existing.timestampMillis != remote.timestampMillis ||
                            existing.note != remote.note)
                    ) {
                        accountTransferDao.updateTransfer(
                            existing.copy(
                                fromAccountName = syncedFrom,
                                toAccountName = syncedTo,
                                amountCents = remote.amountCents,
                                timestampMillis = remote.timestampMillis,
                                note = remote.note,
                                updatedAtMillis = remote.updatedAtMillis
                            )
                        )
                    }
                }

                // 6. Merge Plans / Financial Commitments
                for (remote in syncResult.remoteCommitments) {
                    val syncedAccount = if (remote.accountName.isNotBlank()) {
                        ensureAccountExists(remote.accountName)
                    } else ""
                    val normalizedType = CommitmentType.fromKey(remote.type).name
                    val normalizedCycle = RecurrenceCycle.fromKey(remote.cycle).name
                    val normalizedCat = ExpenseCategory.fromKey(remote.category).name
                    val existing = commitmentDao.getByRemoteId(remote.remoteId)
                    if (existing == null) {
                        commitmentDao.insertCommitment(
                            CommitmentEntity(
                                remoteId = remote.remoteId,
                                title = remote.title,
                                type = normalizedType,
                                amountCents = remote.amountCents,
                                targetOrTotalCents = remote.targetOrTotalCents,
                                progressCents = remote.progressCents,
                                cycle = normalizedCycle,
                                dueDayOfMonth = remote.dueDayOfMonth,
                                statementDayOfMonth = remote.statementDayOfMonth,
                                scheduledDateMillis = remote.scheduledDateMillis,
                                totalInstallments = remote.totalInstallments,
                                completedInstallments = remote.completedInstallments,
                                accountName = syncedAccount,
                                category = normalizedCat,
                                note = remote.note,
                                reminderEnabled = remote.reminderEnabled,
                                isActive = remote.isActive,
                                lastCompletedCycleKey = remote.lastCompletedCycleKey,
                                createdAtMillis = remote.createdAtMillis,
                                updatedAtMillis = remote.updatedAtMillis
                            )
                        )
                    } else if (remote.updatedAtMillis >= existing.updatedAtMillis &&
                        (existing.title != remote.title ||
                            existing.type != normalizedType ||
                            existing.amountCents != remote.amountCents ||
                            existing.targetOrTotalCents != remote.targetOrTotalCents ||
                            existing.progressCents != remote.progressCents ||
                            existing.cycle != normalizedCycle ||
                            existing.dueDayOfMonth != remote.dueDayOfMonth ||
                            existing.statementDayOfMonth != remote.statementDayOfMonth ||
                            existing.scheduledDateMillis != remote.scheduledDateMillis ||
                            existing.totalInstallments != remote.totalInstallments ||
                            existing.completedInstallments != remote.completedInstallments ||
                            existing.accountName != syncedAccount ||
                            existing.category != normalizedCat ||
                            existing.note != remote.note ||
                            existing.reminderEnabled != remote.reminderEnabled ||
                            existing.isActive != remote.isActive ||
                            existing.lastCompletedCycleKey != remote.lastCompletedCycleKey)
                    ) {
                        commitmentDao.updateCommitment(
                            existing.copy(
                                title = remote.title,
                                type = normalizedType,
                                amountCents = remote.amountCents,
                                targetOrTotalCents = remote.targetOrTotalCents,
                                progressCents = remote.progressCents,
                                cycle = normalizedCycle,
                                dueDayOfMonth = remote.dueDayOfMonth,
                                statementDayOfMonth = remote.statementDayOfMonth,
                                scheduledDateMillis = remote.scheduledDateMillis,
                                totalInstallments = remote.totalInstallments,
                                completedInstallments = remote.completedInstallments,
                                accountName = syncedAccount,
                                category = normalizedCat,
                                note = remote.note,
                                reminderEnabled = remote.reminderEnabled,
                                isActive = remote.isActive,
                                lastCompletedCycleKey = remote.lastCompletedCycleKey,
                                updatedAtMillis = remote.updatedAtMillis
                            )
                        )
                    }
                }

                // 7. Merge Points & Rewards Trackers
                for (remote in syncResult.remotePointsAccounts) {
                    val byRemote = pointsAccountDao.getByRemoteId(remote.remoteId)
                    val byName = if (byRemote == null) {
                        pointsAccountDao.getPointsAccountByNameIgnoreCase(remote.name)
                    } else null
                    val existing = byRemote ?: byName
                    if (existing == null) {
                        pointsAccountDao.insertPointsAccount(
                            PointsAccountEntity(
                                remoteId = remote.remoteId,
                                name = remote.name,
                                pointsBalance = remote.pointsBalance,
                                unitLabel = remote.unitLabel,
                                centsPerPointTimes100 = remote.centsPerPointTimes100,
                                note = remote.note,
                                updatedAtMillis = remote.updatedAtMillis,
                                createdAtMillis = remote.createdAtMillis,
                                isDemo = false
                            )
                        )
                    } else if (existing.remoteId != remote.remoteId ||
                        (remote.updatedAtMillis >= existing.updatedAtMillis &&
                            (existing.name != remote.name ||
                                existing.pointsBalance != remote.pointsBalance ||
                                existing.unitLabel != remote.unitLabel ||
                                existing.centsPerPointTimes100 != remote.centsPerPointTimes100 ||
                                existing.note != remote.note))
                    ) {
                        val takeRemote = remote.updatedAtMillis >= existing.updatedAtMillis
                        pointsAccountDao.updatePointsAccount(
                            existing.copy(
                                remoteId = remote.remoteId,
                                name = if (takeRemote) remote.name else existing.name,
                                pointsBalance = if (takeRemote) remote.pointsBalance else existing.pointsBalance,
                                unitLabel = if (takeRemote) remote.unitLabel else existing.unitLabel,
                                centsPerPointTimes100 = if (takeRemote) remote.centsPerPointTimes100 else existing.centsPerPointTimes100,
                                note = if (takeRemote) remote.note else existing.note,
                                updatedAtMillis = maxOf(existing.updatedAtMillis, remote.updatedAtMillis)
                            )
                        )
                    }
                }

                // 8. Merge Monthly Budget Plan, Currency & Today Source Settings
                val remoteSettings = syncResult.remoteSettings
                if (remoteSettings != null && remoteSettings.updatedAtMillis > prefs.settingsUpdatedAtMillis) {
                    preferencesRepository.applyRemoteSettings(
                        monthlyBudgetCents = remoteSettings.monthlyBudgetCents,
                        monthlySavingsGoalCents = remoteSettings.monthlySavingsGoalCents,
                        dailyMode = DailyBudgetMode.fromKey(remoteSettings.dailyMode),
                        fixedDailyLimitCents = remoteSettings.fixedDailyLimitCents,
                        currencySymbol = remoteSettings.currencySymbol,
                        todayUseAccountsBalance = remoteSettings.todayUseAccountsBalance,
                        updatedAtMillis = remoteSettings.updatedAtMillis
                    )
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
        triggerAutoSyncIfEnabled()
    }

    suspend fun updateCurrencySymbol(symbol: String) {
        preferencesRepository.updateCurrencySymbol(symbol)
        triggerAutoSyncIfEnabled()
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

    suspend fun updateTodayUseAccountsBalance(useAccounts: Boolean) {
        preferencesRepository.updateTodayUseAccountsBalance(useAccounts)
        triggerAutoSyncIfEnabled()
    }

    suspend fun processIncomingBankMessage(
        sourcePackage: String,
        sourceAppLabel: String,
        rawTitle: String,
        rawBody: String,
        respectToggle: Boolean = true
    ): BankMessageProcessOutcome {
        val logTag = "SpendWiseBankListener"
        val prefs = preferencesRepository.getCurrentPreferences()
        if (respectToggle && !prefs.bankDetectionEnabled) {
            Log.i(logTag, "Repo outcome=Ignored reason=DETECTION_DISABLED pkg=$sourcePackage")
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
        )
        if (parsed == null) {
            Log.d(
                logTag,
                "Parser result: parsed=false pkg=$sourcePackage titleLen=${rawTitle.trim().length} bodyLen=${rawBody.trim().length}"
            )
            Log.i(logTag, "Repo outcome=Ignored reason=PARSER_FILTERED pkg=$sourcePackage")
            return BankMessageProcessOutcome.Ignored
        }

        Log.d(
            logTag,
            "Parser result: parsed=true pkg=$sourcePackage method=${parsed.paymentMethod.name} " +
                "confidence=${parsed.confidence.name} hasLast4=${parsed.cardLastFour.isNotBlank()}"
        )

        // Deduplicate identical charge messages within a 5-minute window
        val existingCount = detectedTransactionDao.countByFingerprintSince(
            fingerprint = parsed.fingerprint,
            sinceMillis = now - 300_000L
        )
        if (existingCount > 0) {
            Log.i(logTag, "Repo outcome=Duplicate pkg=$sourcePackage")
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
            Log.i(logTag, "Repo outcome=AutoLogged pkg=$sourcePackage matchedAccount=${matchedAccount.isNotBlank()}")
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
            Log.i(logTag, "Repo outcome=QueuedForReview pkg=$sourcePackage matchedAccount=${matchedAccount.isNotBlank()}")
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
