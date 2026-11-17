package com.example.notifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.data.BankMessageProcessOutcome
import com.example.data.SpendWiseRepository
import com.example.widget.SpendWiseBalanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object BankNotificationAccessHelper {
    fun isNotificationAccessEnabled(context: Context): Boolean {
        return try {
            NotificationManagerCompat
                .getEnabledListenerPackages(context.applicationContext)
                .contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun createSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

class BankNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val pkg = statusBarNotification.packageName ?: return

        // Ignore SpendWise's own budget/bank alert notifications and system noise
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return

        val notification = statusBarNotification.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val body = listOf(bigText.ifBlank { normalText }, subText)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" • ")

        if (title.isBlank() && body.isBlank()) return

        val appLabel = resolveAppLabel(pkg)

        serviceScope.launch {
            try {
                val repo = SpendWiseRepository.create(applicationContext)
                val outcome = repo.processIncomingBankMessage(
                    sourcePackage = pkg,
                    sourceAppLabel = appLabel,
                    rawTitle = title,
                    rawBody = body,
                    respectToggle = true
                )
                if (outcome is BankMessageProcessOutcome.AutoLogged) {
                    SpendWiseBalanceWidgetProvider.refreshAll(applicationContext)
                }
            } catch (_: Exception) {
                // Fail safely so notification listener never crashes system binding
            }
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            try {
                @Suppress("DEPRECATION")
                val appInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                applicationContext.packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Throwable) {
                packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
