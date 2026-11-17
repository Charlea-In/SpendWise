package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.BudgetPreferencesRepository
import com.example.data.PointsFormatter
import com.example.data.SpendWiseDatabase
import com.example.util.FinancialInsightsEngine
import com.example.util.MoneyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class SpendWiseBalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val views = buildRemoteViews(appContext)
                appWidgetIds.forEach { widgetId ->
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_LOG_EXPENSE = "extra_open_log_expense"

        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, SpendWiseBalanceWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val views = buildRemoteViews(appContext)
                ids.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }

        fun canRequestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(context)
            return manager.isRequestPinAppWidgetSupported
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, SpendWiseBalanceWidgetProvider::class.java)
            return manager.requestPinAppWidget(provider, null, null)
        }

        private suspend fun buildRemoteViews(context: Context): RemoteViews {
            val db = SpendWiseDatabase.getInstance(context)
            val prefsRepo = BudgetPreferencesRepository(context)

            val expenses = db.expenseDao().observeAllExpenses().first()
            val budgets = db.categoryBudgetDao().observeAllCategoryBudgets().first()
            val pointsAccounts = db.pointsAccountDao().getAllPointsAccountsOnce()
            val prefs = prefsRepo.getCurrentPreferences()
            val summary = FinancialInsightsEngine.compute(expenses, budgets, prefs)
            val currency = prefs.currencySymbol

            val views = RemoteViews(context.packageName, R.layout.widget_balance)

            views.setTextViewText(
                R.id.widget_mode_badge,
                when {
                    prefs.isDemoMode -> "DEMO"
                    prefs.isSheetConnected -> "SYNCED"
                    else -> "LIVE"
                }
            )

            // Daily balance
            val dailyOver = summary.todayRemainingCents < 0L
            views.setTextViewText(
                R.id.widget_daily_label,
                if (dailyOver) "DAILY OVER" else "DAILY BALANCE"
            )
            views.setTextViewText(
                R.id.widget_daily_amount,
                MoneyFormatter.format(abs(summary.todayRemainingCents), currency)
            )
            views.setTextColor(
                R.id.widget_daily_amount,
                ContextCompat.getColor(
                    context,
                    if (dailyOver) R.color.widget_danger else R.color.widget_accent
                )
            )
            views.setTextViewText(
                R.id.widget_daily_sub,
                "Spent ${MoneyFormatter.format(summary.todaySpentCents, currency, compactWhole = true)} / ${MoneyFormatter.format(summary.dailyAllowanceCents, currency, compactWhole = true)}"
            )

            // Weekly balance (7-day window)
            val weeklyOver = summary.weeklyRemainingCents < 0L
            views.setTextViewText(
                R.id.widget_weekly_label,
                if (weeklyOver) "WEEKLY OVER" else "WEEKLY BALANCE"
            )
            views.setTextViewText(
                R.id.widget_weekly_amount,
                MoneyFormatter.format(abs(summary.weeklyRemainingCents), currency)
            )
            views.setTextColor(
                R.id.widget_weekly_amount,
                ContextCompat.getColor(
                    context,
                    if (weeklyOver) R.color.widget_danger else R.color.widget_accent
                )
            )
            views.setTextViewText(
                R.id.widget_weekly_sub,
                "7d ${MoneyFormatter.format(summary.weekSpentCents, currency, compactWhole = true)} / ${MoneyFormatter.format(summary.weeklyAllowanceCents, currency, compactWhole = true)}"
            )

            // Points quick-view summary (does not affect balances)
            if (pointsAccounts.isEmpty()) {
                views.setViewVisibility(R.id.widget_points_summary, View.GONE)
            } else {
                val previewText = if (pointsAccounts.size == 1) {
                    val only = pointsAccounts.first()
                    "Points: ${only.name} ${PointsFormatter.formatWithUnit(only.pointsBalance, only.unitLabel)}"
                } else {
                    val total = pointsAccounts.sumOf { it.pointsBalance }
                    val topItems = pointsAccounts.take(2).joinToString(" • ") { acct ->
                        "${acct.name} ${PointsFormatter.formatCount(acct.pointsBalance)}"
                    }
                    "Points (${PointsFormatter.formatCount(total)}): $topItems"
                }
                views.setViewVisibility(R.id.widget_points_summary, View.VISIBLE)
                views.setTextViewText(R.id.widget_points_summary, previewText)
            }

            // Tap root -> open app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                101,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            // Tap "+ Log" -> open app straight into Log Expense sheet
            val logIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_LOG_EXPENSE, true)
            }
            val logPending = PendingIntent.getActivity(
                context,
                102,
                logIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_button, logPending)

            return views
        }
    }
}
