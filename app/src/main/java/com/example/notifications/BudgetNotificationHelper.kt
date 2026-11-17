package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ExpenseCategory
import com.example.util.MoneyFormatter

data class BudgetAlertEvent(
    val title: String,
    val message: String,
    val isExceeded: Boolean
)

class BudgetNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "spendwise_budget_alerts"
        private const val OVERALL_NOTIFICATION_ID = 9001
    }

    init {
        createNotificationChannel()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val desc = context.getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = desc
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun sendCategoryBudgetNotification(
        category: ExpenseCategory,
        spentCents: Long,
        limitCents: Long,
        currencySymbol: String,
        isExceeded: Boolean
    ): BudgetAlertEvent {
        val pct = ((spentCents.toDouble() / limitCents.toDouble()) * 100.0).toInt()
        val title = if (isExceeded) {
            "${category.displayName} budget exceeded"
        } else {
            "Approaching ${category.displayName} budget ($pct%)"
        }
        val message = if (isExceeded) {
            "You've spent ${MoneyFormatter.format(spentCents, currencySymbol)} of your ${MoneyFormatter.format(limitCents, currencySymbol)} monthly ${category.displayName} limit."
        } else {
            val remaining = (limitCents - spentCents).coerceAtLeast(0L)
            "${MoneyFormatter.format(remaining, currencySymbol)} left in your ${category.displayName} budget (${MoneyFormatter.format(spentCents, currencySymbol)} / ${MoneyFormatter.format(limitCents, currencySymbol)})."
        }

        dispatchSystemNotification(
            notificationId = 1000 + category.ordinal,
            title = title,
            body = message
        )
        return BudgetAlertEvent(title = title, message = message, isExceeded = isExceeded)
    }

    fun sendOverallBudgetNotification(
        spentCents: Long,
        budgetCents: Long,
        currencySymbol: String,
        isExceeded: Boolean
    ): BudgetAlertEvent {
        val pct = ((spentCents.toDouble() / budgetCents.toDouble()) * 100.0).toInt()
        val title = if (isExceeded) {
            "Monthly spending limit exceeded"
        } else {
            "Monthly budget at $pct%"
        }
        val message = if (isExceeded) {
            "Monthly spending reached ${MoneyFormatter.format(spentCents, currencySymbol)}, over your ${MoneyFormatter.format(budgetCents, currencySymbol)} budget."
        } else {
            "You've used ${MoneyFormatter.format(spentCents, currencySymbol)} of your ${MoneyFormatter.format(budgetCents, currencySymbol)} spendable monthly budget."
        }

        dispatchSystemNotification(
            notificationId = OVERALL_NOTIFICATION_ID,
            title = title,
            body = message
        )
        return BudgetAlertEvent(title = title, message = message, isExceeded = isExceeded)
    }

    fun sendTestNotification(currencySymbol: String) {
        dispatchSystemNotification(
            notificationId = 9999,
            title = "SpendWise Budget Alerts Active",
            body = "You'll be notified automatically when any category reaches 80% or 100% of its monthly limit (e.g. ${currencySymbol}240 / ${currencySymbol}300)."
        )
    }

    fun sendBankChargeDetectedNotification(
        merchant: String,
        amountCents: Long,
        currencySymbol: String,
        category: ExpenseCategory,
        autoLogged: Boolean,
        accountName: String
    ) {
        val formatted = MoneyFormatter.format(amountCents, currencySymbol)
        val accountPart = if (accountName.isNotBlank()) " • $accountName" else ""
        val title = if (autoLogged) {
            "Logged $formatted at $merchant"
        } else {
            "Bank charge detected: $formatted"
        }
        val body = if (autoLogged) {
            "Auto-logged to ${category.displayName}$accountPart from your bank message."
        } else {
            "$merchant (${category.displayName}$accountPart) — open SpendWise to approve."
        }
        dispatchSystemNotification(
            notificationId = 9200 + ((merchant.hashCode() and 0x7FFF) % 500),
            title = title,
            body = body
        )
    }

    private fun dispatchSystemNotification(notificationId: Int, title: String, body: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_wallet)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-flight; in-app banner still displays cleanly
        }
    }
}
