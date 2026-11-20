package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteExpenseDto(
    val remoteId: String,
    val description: String,
    val amountCents: Long,
    val category: String,
    val paymentMethod: String,
    val accountName: String = "",
    val timestampMillis: Long,
    val note: String,
    val wasAutoCategorized: Boolean,
    val updatedAtMillis: Long = 0L
)

data class RemoteBudgetDto(
    val category: String,
    val monthlyLimitCents: Long,
    val updatedAtMillis: Long = 0L
)

data class RemoteAccountDto(
    val remoteId: String,
    val name: String,
    val initialBalanceCents: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = 0L
)

data class RemoteDepositDto(
    val remoteId: String,
    val accountName: String,
    val source: String,
    val amountCents: Long,
    val timestampMillis: Long,
    val note: String,
    val updatedAtMillis: Long = 0L
)

data class RemoteTransferDto(
    val remoteId: String,
    val fromAccountName: String,
    val toAccountName: String,
    val amountCents: Long,
    val timestampMillis: Long,
    val note: String,
    val updatedAtMillis: Long = 0L
)

data class RemoteCommitmentDto(
    val remoteId: String,
    val title: String,
    val type: String,
    val amountCents: Long,
    val targetOrTotalCents: Long,
    val progressCents: Long,
    val cycle: String,
    val dueDayOfMonth: Int,
    val statementDayOfMonth: Int,
    val scheduledDateMillis: Long,
    val totalInstallments: Int,
    val completedInstallments: Int,
    val accountName: String,
    val category: String,
    val note: String,
    val reminderEnabled: Boolean,
    val isActive: Boolean,
    val lastCompletedCycleKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = 0L
)

data class RemotePointsAccountDto(
    val remoteId: String,
    val name: String,
    val pointsBalance: Long,
    val unitLabel: String,
    val centsPerPointTimes100: Long,
    val note: String,
    val updatedAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L
)

data class RemoteSettingsDto(
    val monthlyBudgetCents: Long,
    val monthlySavingsGoalCents: Long,
    val dailyMode: String,
    val fixedDailyLimitCents: Long,
    val currencySymbol: String,
    val todayUseAccountsBalance: Boolean,
    val updatedAtMillis: Long = 0L
)

data class SheetSyncResult(
    val message: String,
    val supportsFullSync: Boolean = false,
    val remoteExpenses: List<RemoteExpenseDto>,
    val remoteBudgets: List<RemoteBudgetDto>,
    val remoteAccounts: List<RemoteAccountDto> = emptyList(),
    val remoteDeposits: List<RemoteDepositDto> = emptyList(),
    val remoteTransfers: List<RemoteTransferDto> = emptyList(),
    val remoteCommitments: List<RemoteCommitmentDto> = emptyList(),
    val remotePointsAccounts: List<RemotePointsAccountDto> = emptyList(),
    val remoteSettings: RemoteSettingsDto? = null
)

class AppsScriptSyncClient(
    private val client: OkHttpClient = defaultOkHttpClient()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun isValidAppsScriptUrl(url: String): Boolean {
        val trimmed = url.trim()
        val parsed = trimmed.toHttpUrlOrNull() ?: return false
        return parsed.scheme.equals("https", ignoreCase = true)
    }

    suspend fun ping(scriptUrl: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = scriptUrl.trim().toHttpUrlOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid HTTPS URL format."))

            val requestUrl = baseUrl.newBuilder()
                .addQueryParameter("action", "ping")
                .apply {
                    if (token.isNotBlank()) addQueryParameter("token", token.trim())
                }
                .build()

            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}: Check deployment access ('Anyone')."))
                }
                val json = JSONObject(body)
                if (json.optString("status") != "ok") {
                    return@withContext Result.failure(IllegalStateException(json.optString("error", "Unexpected Apps Script response")))
                }
                val sheetName = json.optString("sheetName", "Google Sheet")
                val schemaVersion = json.optInt("schemaVersion", 1)
                val suffix = if (schemaVersion >= 2) " (Full multi-device sync ready)" else ""
                Result.success("Connected to \"$sheetName\"$suffix")
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Connection failed"))
        }
    }

    suspend fun syncAll(
        scriptUrl: String,
        token: String,
        localExpenses: List<ExpenseEntity>,
        localBudgets: List<CategoryBudgetEntity>,
        localAccounts: List<AccountEntity> = emptyList(),
        localDeposits: List<AccountDepositEntity> = emptyList(),
        localTransfers: List<AccountTransferEntity> = emptyList(),
        localCommitments: List<CommitmentEntity> = emptyList(),
        localPointsAccounts: List<PointsAccountEntity> = emptyList(),
        localSettings: BudgetPreferences? = null
    ): Result<SheetSyncResult> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = scriptUrl.trim().toHttpUrlOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid Apps Script URL"))

            val expensesJson = JSONArray().apply {
                localExpenses.filter { !it.isDemo }.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("remoteId", item.remoteId)
                            put("description", item.description)
                            put("amountCents", item.amountCents)
                            put("category", item.category)
                            put("paymentMethod", item.paymentMethod)
                            put("accountName", item.accountName)
                            put("timestampMillis", item.timestampMillis)
                            put("note", item.note)
                            put("wasAutoCategorized", item.wasAutoCategorized)
                            put("updatedAtMillis", item.updatedAtMillis)
                        }
                    )
                }
            }

            val budgetsJson = JSONArray().apply {
                localBudgets.forEach { budget ->
                    put(
                        JSONObject().apply {
                            put("category", budget.category)
                            put("monthlyLimitCents", budget.monthlyLimitCents)
                            put("updatedAtMillis", budget.updatedAtMillis)
                        }
                    )
                }
            }

            val accountsJson = JSONArray().apply {
                localAccounts.filter { !it.isDemo }.forEach { acct ->
                    put(
                        JSONObject().apply {
                            put("remoteId", acct.remoteId)
                            put("name", acct.name)
                            put("initialBalanceCents", acct.initialBalanceCents)
                            put("createdAtMillis", acct.createdAtMillis)
                            put("updatedAtMillis", acct.updatedAtMillis)
                        }
                    )
                }
            }

            val depositsJson = JSONArray().apply {
                localDeposits.filter { !it.isDemo }.forEach { dep ->
                    put(
                        JSONObject().apply {
                            put("remoteId", dep.remoteId)
                            put("accountName", dep.accountName)
                            put("source", dep.source)
                            put("amountCents", dep.amountCents)
                            put("timestampMillis", dep.timestampMillis)
                            put("note", dep.note)
                            put("updatedAtMillis", dep.updatedAtMillis)
                        }
                    )
                }
            }

            val transfersJson = JSONArray().apply {
                localTransfers.filter { !it.isDemo }.forEach { tr ->
                    put(
                        JSONObject().apply {
                            put("remoteId", tr.remoteId)
                            put("fromAccountName", tr.fromAccountName)
                            put("toAccountName", tr.toAccountName)
                            put("amountCents", tr.amountCents)
                            put("timestampMillis", tr.timestampMillis)
                            put("note", tr.note)
                            put("updatedAtMillis", tr.updatedAtMillis)
                        }
                    )
                }
            }

            val commitmentsJson = JSONArray().apply {
                localCommitments.forEach { c ->
                    put(
                        JSONObject().apply {
                            put("remoteId", c.remoteId)
                            put("title", c.title)
                            put("type", c.type)
                            put("amountCents", c.amountCents)
                            put("targetOrTotalCents", c.targetOrTotalCents)
                            put("progressCents", c.progressCents)
                            put("cycle", c.cycle)
                            put("dueDayOfMonth", c.dueDayOfMonth)
                            put("statementDayOfMonth", c.statementDayOfMonth)
                            put("scheduledDateMillis", c.scheduledDateMillis)
                            put("totalInstallments", c.totalInstallments)
                            put("completedInstallments", c.completedInstallments)
                            put("accountName", c.accountName)
                            put("category", c.category)
                            put("note", c.note)
                            put("reminderEnabled", c.reminderEnabled)
                            put("isActive", c.isActive)
                            put("lastCompletedCycleKey", c.lastCompletedCycleKey)
                            put("createdAtMillis", c.createdAtMillis)
                            put("updatedAtMillis", c.updatedAtMillis)
                        }
                    )
                }
            }

            val pointsJson = JSONArray().apply {
                localPointsAccounts.filter { !it.isDemo }.forEach { p ->
                    put(
                        JSONObject().apply {
                            put("remoteId", p.remoteId)
                            put("name", p.name)
                            put("pointsBalance", p.pointsBalance)
                            put("unitLabel", p.unitLabel)
                            put("centsPerPointTimes100", p.centsPerPointTimes100)
                            put("note", p.note)
                            put("updatedAtMillis", p.updatedAtMillis)
                            put("createdAtMillis", p.createdAtMillis)
                        }
                    )
                }
            }

            val payload = JSONObject().apply {
                put("action", "syncAll")
                put("clientSchemaVersion", 2)
                if (token.isNotBlank()) put("token", token.trim())
                put("expenses", expensesJson)
                put("budgets", budgetsJson)
                put("accounts", accountsJson)
                put("deposits", depositsJson)
                put("transfers", transfersJson)
                put("commitments", commitmentsJson)
                put("pointsAccounts", pointsJson)
                if (localSettings != null) {
                    put(
                        "settings",
                        JSONObject().apply {
                            put("monthlyBudgetCents", localSettings.monthlyBudgetCents)
                            put("monthlySavingsGoalCents", localSettings.monthlySavingsGoalCents)
                            put("dailyMode", localSettings.dailyMode.name)
                            put("fixedDailyLimitCents", localSettings.fixedDailyLimitCents)
                            put("currencySymbol", localSettings.currencySymbol)
                            put("todayUseAccountsBalance", localSettings.todayUseAccountsBalance)
                            put("updatedAtMillis", localSettings.settingsUpdatedAtMillis)
                        }
                    )
                }
            }

            val request = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code} while syncing"))
                }
                val json = JSONObject(body)
                if (json.optString("status") != "ok") {
                    return@withContext Result.failure(IllegalStateException(json.optString("error", "Sync rejected by script")))
                }

                val schemaVersion = json.optInt("schemaVersion", 1)
                val supportsFullSync = schemaVersion >= 2 ||
                    json.has("accounts") ||
                    json.has("settings")

                val remoteExpenses = parseExpensesArray(json.optJSONArray("expenses"))
                val remoteBudgets = parseBudgetsArray(json.optJSONArray("budgets"))
                val remoteAccounts = parseAccountsArray(json.optJSONArray("accounts"))
                val remoteDeposits = parseDepositsArray(json.optJSONArray("deposits"))
                val remoteTransfers = parseTransfersArray(json.optJSONArray("transfers"))
                val remoteCommitments = parseCommitmentsArray(json.optJSONArray("commitments"))
                val remotePoints = parsePointsArray(json.optJSONArray("pointsAccounts"))
                val remoteSettings = parseSettingsObject(json.optJSONObject("settings"))

                val syncedMsg = if (supportsFullSync) {
                    buildString {
                        append("Synced ${remoteExpenses.size} expenses, ${remoteAccounts.size} accounts")
                        if (remoteDeposits.isNotEmpty()) append(", ${remoteDeposits.size} deposits")
                        if (remoteTransfers.isNotEmpty()) append(", ${remoteTransfers.size} transfers")
                        if (remoteCommitments.isNotEmpty()) append(", ${remoteCommitments.size} plans")
                        if (remotePoints.isNotEmpty()) append(", ${remotePoints.size} points")
                        append(", ${remoteBudgets.size} budgets & settings")
                    }
                } else {
                    "Synced ${remoteExpenses.size} expenses & ${remoteBudgets.size} budgets (Update Code.gs deployment to sync accounts, plans, funds & settings)"
                }

                Result.success(
                    SheetSyncResult(
                        message = syncedMsg,
                        supportsFullSync = supportsFullSync,
                        remoteExpenses = remoteExpenses,
                        remoteBudgets = remoteBudgets,
                        remoteAccounts = remoteAccounts,
                        remoteDeposits = remoteDeposits,
                        remoteTransfers = remoteTransfers,
                        remoteCommitments = remoteCommitments,
                        remotePointsAccounts = remotePoints,
                        remoteSettings = remoteSettings
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Sync error"))
        }
    }

    suspend fun deleteRemoteExpense(
        scriptUrl: String,
        token: String,
        remoteId: String
    ): Result<Unit> = deleteRemoteRecord(
        scriptUrl = scriptUrl,
        token = token,
        recordType = "expense",
        remoteId = remoteId
    )

    suspend fun deleteRemoteRecord(
        scriptUrl: String,
        token: String,
        recordType: String,
        remoteId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (remoteId.isBlank()) return@withContext Result.success(Unit)
            val baseUrl = scriptUrl.trim().toHttpUrlOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid Apps Script URL"))

            val payload = JSONObject().apply {
                // Keep legacy action name for expenses so both v1 and v2 Code.gs handle it
                put("action", if (recordType == "expense") "deleteExpense" else "deleteRecord")
                put("recordType", recordType)
                if (token.isNotBlank()) put("token", token.trim())
                put("remoteId", remoteId)
            }

            val request = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseExpensesArray(array: JSONArray?): List<RemoteExpenseDto> {
        if (array == null) return emptyList()
        val list = mutableListOf<RemoteExpenseDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val remoteId = obj.optString("remoteId").trim()
            if (remoteId.isBlank()) continue
            list.add(
                RemoteExpenseDto(
                    remoteId = remoteId,
                    description = obj.optString("description", "Expense"),
                    amountCents = obj.optLong("amountCents", 0L),
                    category = obj.optString("category", "OTHER"),
                    paymentMethod = obj.optString("paymentMethod", "CARD"),
                    accountName = obj.optString("accountName", "").trim(),
                    timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                    note = obj.optString("note", ""),
                    wasAutoCategorized = obj.optBoolean("wasAutoCategorized", false),
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                )
            )
        }
        return list
    }

    private fun parseBudgetsArray(array: JSONArray?): List<RemoteBudgetDto> {
        if (array == null) return emptyList()
        val list = mutableListOf<RemoteBudgetDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val cat = obj.optString("category").trim()
            val limit = obj.optLong("monthlyLimitCents", 0L)
            if (cat.isNotBlank() && limit > 0L) {
                list.add(
                    RemoteBudgetDto(
                        category = cat,
                        monthlyLimitCents = limit,
                        updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                    )
                )
            }
        }
        return list
    }

    private fun parseAccountsArray(array: JSONArray?): List<RemoteAccountDto> {
        if (array == null) return emptyList()
        val list = mutableListOf<RemoteAccountDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val remoteId = obj.optString("remoteId").trim()
            val name = obj.optString("name").trim()
            if (remoteId.isBlank() || name.isBlank()) continue
            list.add(
                RemoteAccountDto(
                    remoteId = remoteId,
                    name = name,
                    initialBalanceCents = obj.optLong("initialBalanceCents", 0L).coerceAtLeast(0L),
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                )
            )
        }
        return list
    }

    private fun parseDepositsArray(array: JSONArray?): List<RemoteDepositDto> {
        if (array == null) return emptyList()
        val list = mutableListOf<RemoteDepositDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val remoteId = obj.optString("remoteId").trim()
            val accountName = obj.optString("accountName").trim()
            val source = obj.optString("source").trim()
            val amountCents = obj.optLong("amountCents", 0L)
            if (remoteId.isBlank() || accountName.isBlank() || source.isBlank() || amountCents <= 0L) continue
            list.add(
                RemoteDepositDto(
                    remoteId = remoteId,
                    accountName = accountName,
                    source = source,
                    amountCents = amountCents,
                    timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                    note = obj.optString("note", ""),
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                )
            )
        }
        return list
    }

    private fun parseTransfersArray(array: JSONArray?): List<RemoteTransferDto> {
        if (array == null) return emptyList()
        val list = mutableListOf<RemoteTransferDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val remoteId = obj.optString("remoteId").trim()
            val fromName = obj.optString("fromAccountName").trim()
            val toName = obj.optString("toAccountName").trim()
            val amountCents = obj.optLong("amountCents", 0L)
            if (remoteId.isBlank() || fromName.isBlank() || toName.isBlank() || amountCents <= 0L) continue
            list.add(
                RemoteTransferDto(
                    remoteId = remoteId,
                    fromAccountName = fromName,
                    toAccountName = toName,
                    amountCents = amountCents,
                    timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                    note = obj.optString("note", ""),
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                )
            )
        }
        return list
    }

    private fun parseCommitmentsArray(array: JSONArray?): List<RemoteCommitmentDto> {
        if (array == null) return emptyList()
        val list = mutableListOf<RemoteCommitmentDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val remoteId = obj.optString("remoteId").trim()
            val title = obj.optString("title").trim()
            val amountCents = obj.optLong("amountCents", 0L)
            if (remoteId.isBlank() || title.isBlank() || amountCents <= 0L) continue
            list.add(
                RemoteCommitmentDto(
                    remoteId = remoteId,
                    title = title,
                    type = obj.optString("type", "DEBT"),
                    amountCents = amountCents,
                    targetOrTotalCents = obj.optLong("targetOrTotalCents", 0L).coerceAtLeast(0L),
                    progressCents = obj.optLong("progressCents", 0L).coerceAtLeast(0L),
                    cycle = obj.optString("cycle", "MONTHLY"),
                    dueDayOfMonth = obj.optInt("dueDayOfMonth", 1).coerceIn(1, 31),
                    statementDayOfMonth = obj.optInt("statementDayOfMonth", 0).coerceIn(0, 31),
                    scheduledDateMillis = obj.optLong("scheduledDateMillis", 0L),
                    totalInstallments = obj.optInt("totalInstallments", 0).coerceAtLeast(0),
                    completedInstallments = obj.optInt("completedInstallments", 0).coerceAtLeast(0),
                    accountName = obj.optString("accountName", "").trim(),
                    category = obj.optString("category", "OTHER"),
                    note = obj.optString("note", ""),
                    reminderEnabled = obj.optBoolean("reminderEnabled", true),
                    isActive = obj.optBoolean("isActive", true),
                    lastCompletedCycleKey = obj.optString("lastCompletedCycleKey", ""),
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                )
            )
        }
        return list
    }

    private fun parsePointsArray(array: JSONArray?): List<RemotePointsAccountDto> {
        if (array == null) return emptyList()
        val list = mutableListOf<RemotePointsAccountDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val remoteId = obj.optString("remoteId").trim()
            val name = obj.optString("name").trim()
            if (remoteId.isBlank() || name.isBlank()) continue
            list.add(
                RemotePointsAccountDto(
                    remoteId = remoteId,
                    name = name,
                    pointsBalance = obj.optLong("pointsBalance", 0L).coerceAtLeast(0L),
                    unitLabel = obj.optString("unitLabel", "pts").ifBlank { "pts" },
                    centsPerPointTimes100 = obj.optLong("centsPerPointTimes100", 0L).coerceAtLeast(0L),
                    note = obj.optString("note", ""),
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L),
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    private fun parseSettingsObject(obj: JSONObject?): RemoteSettingsDto? {
        if (obj == null || obj.length() == 0) return null
        if (!obj.has("monthlyBudgetCents") && !obj.has("currencySymbol")) return null
        return RemoteSettingsDto(
            monthlyBudgetCents = obj.optLong("monthlyBudgetCents", 1800_00L).coerceAtLeast(0L),
            monthlySavingsGoalCents = obj.optLong("monthlySavingsGoalCents", 300_00L).coerceAtLeast(0L),
            dailyMode = obj.optString("dailyMode", "AUTO_PACED"),
            fixedDailyLimitCents = obj.optLong("fixedDailyLimitCents", 50_00L).coerceAtLeast(0L),
            currencySymbol = obj.optString("currencySymbol", "$").ifBlank { "$" },
            todayUseAccountsBalance = obj.optBoolean("todayUseAccountsBalance", true),
            updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
        )
    }
}
