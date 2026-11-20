package com.example.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.BankMessageProcessOutcome
import com.example.data.SpendWiseRepository
import com.example.widget.SpendWiseBalanceWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "SpendWiseBankListener"

object BankNotificationAccessHelper {
    fun isNotificationAccessEnabled(context: Context): Boolean {
        val appCtx = context.applicationContext
        return try {
            val compatEnabled = NotificationManagerCompat
                .getEnabledListenerPackages(appCtx)
                .contains(appCtx.packageName)
            if (compatEnabled) return true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val nm = appCtx.getSystemService(NotificationManager::class.java)
                nm?.isNotificationListenerAccessGranted(
                    ComponentName(appCtx, BankNotificationListenerService::class.java)
                ) == true
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed checking notification listener access", t)
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

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.e(TAG, "Uncaught coroutine error in BankNotificationListenerService", throwable)
        }
    }

    @Volatile
    private var serviceJob: CompletableJob = SupervisorJob()

    @Volatile
    private var serviceScope: CoroutineScope = CoroutineScope(serviceJob + Dispatchers.IO + exceptionHandler)

    override fun onCreate() {
        super.onCreate()
        ensureActiveScope()
        Log.i(TAG, "onCreate: BankNotificationListenerService initialized")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ensureActiveScope()
        Log.i(TAG, "onListenerConnected: listener bound to Android notification system")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "onListenerDisconnected: listener unbound from Android notification system")
        try {
            if (BankNotificationAccessHelper.isNotificationAccessEnabled(applicationContext)) {
                requestRebind(ComponentName(this, BankNotificationListenerService::class.java))
                Log.i(TAG, "onListenerDisconnected: requested service rebind")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onListenerDisconnected: requestRebind failed", t)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName
        val notification = sbn?.notification
        val flags = notification?.flags ?: 0
        val extras = notification?.extras

        Log.d(
            TAG,
            "onNotificationPosted: pkg=${pkg ?: "<null>"} hasNotification=${notification != null} flags=0x${Integer.toHexString(flags)} hasExtras=${extras != null}"
        )

        val preDecision = BankNotificationFilter.evaluatePreExtraction(
            packageName = pkg,
            ownPackageName = packageName,
            hasNotification = notification != null,
            flags = flags,
            hasExtras = extras != null
        )
        if (preDecision is BankNotificationFilterDecision.Ignore) {
            Log.d(TAG, "PreFilter=IGNORED reason=${preDecision.reason} pkg=${pkg ?: "<null>"}")
            return
        }

        val resolvedPkg = pkg ?: return
        val extraction = NotificationTextExtractor.extract(extras)
        val meta = extraction.metadata

        Log.d(
            TAG,
            "Extraction: pkg=$resolvedPkg hasTitle=${meta.hasTitle}(len=${meta.titleLength}) " +
                "hasConvTitle=${meta.hasConversationTitle}(len=${meta.conversationTitleLength}) " +
                "hasText=${meta.hasText}(len=${meta.textLength}) " +
                "hasBigText=${meta.hasBigText}(len=${meta.bigTextLength}) " +
                "hasSubText=${meta.hasSubText}(len=${meta.subTextLength}) " +
                "extraMessages=${meta.extraMessagesCount} textLines=${meta.textLinesCount} " +
                "candidates=${meta.candidateCount}"
        )

        val postDecision = BankNotificationFilter.evaluatePostExtraction(extraction)
        if (postDecision is BankNotificationFilterDecision.Ignore) {
            Log.d(TAG, "PostFilter=IGNORED reason=${postDecision.reason} pkg=$resolvedPkg")
            return
        }

        Log.d(TAG, "Filter=PASS pkg=$resolvedPkg candidates=${extraction.candidates.size}")

        val appLabel = resolveAppLabel(resolvedPkg)
        val entriesToProcess = if (extraction.candidates.isNotEmpty()) {
            extraction.candidates
        } else {
            listOf(ExtractedNotificationMessage(title = extraction.title, body = ""))
        }

        val scope = ensureActiveScope()
        scope.launch {
            try {
                val repo = SpendWiseRepository.create(applicationContext)
                var anyAutoLogged = false

                for ((index, entry) in entriesToProcess.withIndex()) {
                    try {
                        val outcome = repo.processIncomingBankMessage(
                            sourcePackage = resolvedPkg,
                            sourceAppLabel = appLabel,
                            rawTitle = entry.title,
                            rawBody = entry.body,
                            respectToggle = true
                        )
                        val outcomeName = when (outcome) {
                            BankMessageProcessOutcome.Ignored -> "Ignored"
                            BankMessageProcessOutcome.Duplicate -> "Duplicate"
                            is BankMessageProcessOutcome.QueuedForReview -> "QueuedForReview"
                            is BankMessageProcessOutcome.AutoLogged -> {
                                anyAutoLogged = true
                                "AutoLogged"
                            }
                        }
                        Log.i(
                            TAG,
                            "Processed candidate[$index/${entriesToProcess.size}] pkg=$resolvedPkg outcome=$outcomeName"
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        Log.e(
                            TAG,
                            "Failed processing candidate[$index/${entriesToProcess.size}] pkg=$resolvedPkg",
                            t
                        )
                    }
                }

                if (anyAutoLogged) {
                    try {
                        SpendWiseBalanceWidgetProvider.refreshAll(applicationContext)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Widget refresh failed after auto-logged bank notification", t)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed handling notification batch for pkg=$resolvedPkg", t)
            }
        }
    }

    @Synchronized
    private fun ensureActiveScope(): CoroutineScope {
        if (!serviceJob.isActive) {
            serviceJob = SupervisorJob()
            serviceScope = CoroutineScope(serviceJob + Dispatchers.IO + exceptionHandler)
        }
        return serviceScope
    }

    private fun resolveAppLabel(targetPackage: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(targetPackage, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(targetPackage, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            targetPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: shutting down BankNotificationListenerService scope")
        serviceScope.cancel()
    }
}
