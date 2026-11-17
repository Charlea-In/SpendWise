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
    val wasAutoCategorized: Boolean
)

data class RemoteBudgetDto(
    val category: String,
    val monthlyLimitCents: Long
)

data class SheetSyncResult(
    val message: String,
    val remoteExpenses: List<RemoteExpenseDto>,
    val remoteBudgets: List<RemoteBudgetDto>
)

class AppsScriptSyncClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

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
                Result.success("Connected to \"$sheetName\"")
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Connection failed"))
        }
    }

    suspend fun syncAll(
        scriptUrl: String,
        token: String,
        localExpenses: List<ExpenseEntity>,
        localBudgets: List<CategoryBudgetEntity>
    ): Result<SheetSyncResult> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = scriptUrl.trim().toHttpUrlOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid Apps Script URL"))

            val expensesJson = JSONArray()
            localExpenses.filter { !it.isDemo }.forEach { item ->
                expensesJson.put(
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
                    }
                )
            }

            val budgetsJson = JSONArray()
            localBudgets.forEach { budget ->
                budgetsJson.put(
                    JSONObject().apply {
                        put("category", budget.category)
                        put("monthlyLimitCents", budget.monthlyLimitCents)
                    }
                )
            }

            val payload = JSONObject().apply {
                put("action", "syncAll")
                if (token.isNotBlank()) put("token", token.trim())
                put("expenses", expensesJson)
                put("budgets", budgetsJson)
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

                val remoteExpenses = parseExpensesArray(json.optJSONArray("expenses"))
                val remoteBudgets = parseBudgetsArray(json.optJSONArray("budgets"))
                val syncedMsg = "Synced ${remoteExpenses.size} expenses & ${remoteBudgets.size} budgets"
                Result.success(
                    SheetSyncResult(
                        message = syncedMsg,
                        remoteExpenses = remoteExpenses,
                        remoteBudgets = remoteBudgets
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = scriptUrl.trim().toHttpUrlOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid Apps Script URL"))

            val payload = JSONObject().apply {
                put("action", "deleteExpense")
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
            val remoteId = obj.optString("remoteId")
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
                    wasAutoCategorized = obj.optBoolean("wasAutoCategorized", false)
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
            val cat = obj.optString("category")
            val limit = obj.optLong("monthlyLimitCents", 0L)
            if (cat.isNotBlank() && limit > 0L) {
                list.add(RemoteBudgetDto(category = cat, monthlyLimitCents = limit))
            }
        }
        return list
    }
}
