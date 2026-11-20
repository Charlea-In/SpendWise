package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DailyBudgetMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.spendWiseDataStore: DataStore<Preferences> by preferencesDataStore(name = "spendwise_prefs")

data class BudgetPreferences(
    val monthlyBudgetCents: Long = 1800_00L,
    val monthlySavingsGoalCents: Long = 300_00L,
    val dailyMode: DailyBudgetMode = DailyBudgetMode.AUTO_PACED,
    val fixedDailyLimitCents: Long = 50_00L,
    val currencySymbol: String = "$",
    val notificationsEnabled: Boolean = true,
    val overallAlert80Month: String = "",
    val overallAlert100Month: String = "",
    val isDemoMode: Boolean = false,
    val appsScriptUrl: String = "",
    val appsScriptToken: String = "",
    val autoSyncEnabled: Boolean = true,
    val lastSyncTimestampMillis: Long = 0L,
    val lastSyncMessage: String = "",
    val geminiAssistEnabled: Boolean = true,
    val bankDetectionEnabled: Boolean = true,
    val bankAutoLogEnabled: Boolean = false,
    val bankDefaultAccountName: String = "",
    val todayUseAccountsBalance: Boolean = true,
    val settingsUpdatedAtMillis: Long = 0L
) {
    val isSheetConnected: Boolean
        get() = appsScriptUrl.isNotBlank()
}

class BudgetPreferencesRepository(private val context: Context) {

    private object Keys {
        val MONTHLY_BUDGET_CENTS = longPreferencesKey("monthly_budget_cents")
        val MONTHLY_SAVINGS_GOAL_CENTS = longPreferencesKey("monthly_savings_goal_cents")
        val DAILY_MODE = stringPreferencesKey("daily_mode")
        val FIXED_DAILY_LIMIT_CENTS = longPreferencesKey("fixed_daily_limit_cents")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val OVERALL_ALERT_80_MONTH = stringPreferencesKey("overall_alert_80_month")
        val OVERALL_ALERT_100_MONTH = stringPreferencesKey("overall_alert_100_month")
        val IS_DEMO_MODE = booleanPreferencesKey("is_demo_mode")
        val APPS_SCRIPT_URL = stringPreferencesKey("apps_script_url")
        val APPS_SCRIPT_TOKEN = stringPreferencesKey("apps_script_token")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val GEMINI_ASSIST_ENABLED = booleanPreferencesKey("gemini_assist_enabled")
        val BANK_DETECTION_ENABLED = booleanPreferencesKey("bank_detection_enabled")
        val BANK_AUTO_LOG_ENABLED = booleanPreferencesKey("bank_auto_log_enabled")
        val BANK_DEFAULT_ACCOUNT = stringPreferencesKey("bank_default_account")
        val TODAY_USE_ACCOUNTS_BALANCE = booleanPreferencesKey("today_use_accounts_balance")
        val SETTINGS_UPDATED_AT_MILLIS = longPreferencesKey("settings_updated_at_millis")
    }

    val preferencesFlow: Flow<BudgetPreferences> = context.spendWiseDataStore.data.map { prefs ->
        BudgetPreferences(
            monthlyBudgetCents = prefs[Keys.MONTHLY_BUDGET_CENTS] ?: 1800_00L,
            monthlySavingsGoalCents = prefs[Keys.MONTHLY_SAVINGS_GOAL_CENTS] ?: 300_00L,
            dailyMode = DailyBudgetMode.fromKey(prefs[Keys.DAILY_MODE]),
            fixedDailyLimitCents = prefs[Keys.FIXED_DAILY_LIMIT_CENTS] ?: 50_00L,
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL] ?: "$",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            overallAlert80Month = prefs[Keys.OVERALL_ALERT_80_MONTH] ?: "",
            overallAlert100Month = prefs[Keys.OVERALL_ALERT_100_MONTH] ?: "",
            isDemoMode = prefs[Keys.IS_DEMO_MODE] ?: false,
            appsScriptUrl = prefs[Keys.APPS_SCRIPT_URL] ?: "",
            appsScriptToken = prefs[Keys.APPS_SCRIPT_TOKEN] ?: "",
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: true,
            lastSyncTimestampMillis = prefs[Keys.LAST_SYNC_TIMESTAMP] ?: 0L,
            lastSyncMessage = prefs[Keys.LAST_SYNC_MESSAGE] ?: "",
            geminiAssistEnabled = prefs[Keys.GEMINI_ASSIST_ENABLED] ?: true,
            bankDetectionEnabled = prefs[Keys.BANK_DETECTION_ENABLED] ?: true,
            bankAutoLogEnabled = prefs[Keys.BANK_AUTO_LOG_ENABLED] ?: false,
            bankDefaultAccountName = prefs[Keys.BANK_DEFAULT_ACCOUNT] ?: "",
            todayUseAccountsBalance = prefs[Keys.TODAY_USE_ACCOUNTS_BALANCE] ?: true,
            settingsUpdatedAtMillis = prefs[Keys.SETTINGS_UPDATED_AT_MILLIS] ?: 0L
        )
    }

    suspend fun getCurrentPreferences(): BudgetPreferences = preferencesFlow.first()

    suspend fun updateBudgetSettings(
        monthlyBudgetCents: Long,
        monthlySavingsGoalCents: Long,
        dailyMode: DailyBudgetMode,
        fixedDailyLimitCents: Long
    ) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.MONTHLY_BUDGET_CENTS] = monthlyBudgetCents.coerceAtLeast(0L)
            prefs[Keys.MONTHLY_SAVINGS_GOAL_CENTS] = monthlySavingsGoalCents.coerceAtLeast(0L)
            prefs[Keys.DAILY_MODE] = dailyMode.name
            prefs[Keys.FIXED_DAILY_LIMIT_CENTS] = fixedDailyLimitCents.coerceAtLeast(0L)
            prefs[Keys.OVERALL_ALERT_80_MONTH] = ""
            prefs[Keys.OVERALL_ALERT_100_MONTH] = ""
            prefs[Keys.SETTINGS_UPDATED_AT_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun updateCurrencySymbol(symbol: String) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.CURRENCY_SYMBOL] = symbol
            prefs[Keys.SETTINGS_UPDATED_AT_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun markOverallAlertSent(monthKey: String, isExceeded: Boolean) {
        context.spendWiseDataStore.edit { prefs ->
            if (isExceeded) {
                prefs[Keys.OVERALL_ALERT_100_MONTH] = monthKey
            } else {
                prefs[Keys.OVERALL_ALERT_80_MONTH] = monthKey
            }
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.IS_DEMO_MODE] = enabled
        }
    }

    suspend fun updateAppsScriptConfig(url: String, token: String, autoSync: Boolean) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.APPS_SCRIPT_URL] = url.trim()
            prefs[Keys.APPS_SCRIPT_TOKEN] = token.trim()
            prefs[Keys.AUTO_SYNC_ENABLED] = autoSync
            if (url.isBlank()) {
                prefs[Keys.LAST_SYNC_MESSAGE] = ""
                prefs[Keys.LAST_SYNC_TIMESTAMP] = 0L
            }
        }
    }

    suspend fun updateSyncStatus(timestampMillis: Long, message: String) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_TIMESTAMP] = timestampMillis
            prefs[Keys.LAST_SYNC_MESSAGE] = message
        }
    }

    suspend fun updateGeminiAssistEnabled(enabled: Boolean) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.GEMINI_ASSIST_ENABLED] = enabled
        }
    }

    suspend fun updateBankDetectionSettings(
        detectionEnabled: Boolean,
        autoLogEnabled: Boolean,
        defaultAccountName: String
    ) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.BANK_DETECTION_ENABLED] = detectionEnabled
            prefs[Keys.BANK_AUTO_LOG_ENABLED] = autoLogEnabled
            prefs[Keys.BANK_DEFAULT_ACCOUNT] = defaultAccountName.trim()
        }
    }

    suspend fun updateTodayUseAccountsBalance(useAccounts: Boolean) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.TODAY_USE_ACCOUNTS_BALANCE] = useAccounts
            prefs[Keys.SETTINGS_UPDATED_AT_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun applyRemoteSettings(
        monthlyBudgetCents: Long,
        monthlySavingsGoalCents: Long,
        dailyMode: DailyBudgetMode,
        fixedDailyLimitCents: Long,
        currencySymbol: String,
        todayUseAccountsBalance: Boolean,
        updatedAtMillis: Long
    ) {
        context.spendWiseDataStore.edit { prefs ->
            prefs[Keys.MONTHLY_BUDGET_CENTS] = monthlyBudgetCents.coerceAtLeast(0L)
            prefs[Keys.MONTHLY_SAVINGS_GOAL_CENTS] = monthlySavingsGoalCents.coerceAtLeast(0L)
            prefs[Keys.DAILY_MODE] = dailyMode.name
            prefs[Keys.FIXED_DAILY_LIMIT_CENTS] = fixedDailyLimitCents.coerceAtLeast(0L)
            if (currencySymbol.isNotBlank()) {
                prefs[Keys.CURRENCY_SYMBOL] = currencySymbol
            }
            prefs[Keys.TODAY_USE_ACCOUNTS_BALANCE] = todayUseAccountsBalance
            prefs[Keys.SETTINGS_UPDATED_AT_MILLIS] = updatedAtMillis
        }
    }
}
