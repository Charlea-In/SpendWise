package com.example

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BankMessageProcessOutcome
import com.example.data.BudgetPreferencesRepository
import com.example.data.SpendWiseDatabase
import com.example.data.SpendWiseRepository
import com.example.model.BankMessageParser
import com.example.model.PaymentMethod
import com.example.notifications.BankNotificationAccessHelper
import com.example.notifications.BankNotificationFilter
import com.example.notifications.BankNotificationFilterDecision
import com.example.notifications.BankNotificationListenerService
import com.example.notifications.BudgetNotificationHelper
import com.example.notifications.NotificationTextExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BankNotificationPipelineRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: SpendWiseDatabase
    private lateinit var prefsRepo: BudgetPreferencesRepository
    private lateinit var repo: SpendWiseRepository

    private val bancoCuscatlanSms =
        "Alerta de consumo con Tarjeta de Debito con Cuenta B.CUSCATLAN 7708 por USD10.00 en SUPER7 PRESIDENCIAL el 2026-11-17 21:39. Mas inf. 22122000"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = BudgetPreferencesRepository(context)
        prefsRepo.updateBankDetectionSettings(
            detectionEnabled = true,
            autoLogEnabled = false,
            defaultAccountName = ""
        )
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            ""
        )
        repo = SpendWiseRepository(
            expenseDao = db.expenseDao(),
            categoryBudgetDao = db.categoryBudgetDao(),
            accountDao = db.accountDao(),
            accountTransferDao = db.accountTransferDao(),
            pointsAccountDao = db.pointsAccountDao(),
            commitmentDao = db.commitmentDao(),
            detectedTransactionDao = db.detectedTransactionDao(),
            preferencesRepository = prefsRepo,
            notificationHelper = BudgetNotificationHelper(context)
        )
    }

    @After
    fun tearDown() = runBlocking {
        prefsRepo.updateBankDetectionSettings(
            detectionEnabled = true,
            autoLogEnabled = false,
            defaultAccountName = ""
        )
        db.close()
    }

    @Test
    fun scenario1_exactBancoCuscatlanMessageParsesAndEntersReviewQueue() = runBlocking {
        // Direct parser check
        val parsed = BankMessageParser.parse(
            title = "22122",
            body = bancoCuscatlanSms,
            sourceAppLabel = "Messages"
        )
        assertNotNull(parsed)
        assertEquals(10_00L, parsed!!.amountCents)
        assertEquals("SUPER7 PRESIDENCIAL", parsed.merchant)
        assertEquals(PaymentMethod.CARD, parsed.paymentMethod)
        assertEquals("7708", parsed.cardLastFour)

        // Repository pipeline check (default bankAutoLogEnabled = false -> QueuedForReview)
        val outcome = repo.processIncomingBankMessage(
            sourcePackage = "com.google.android.apps.messaging",
            sourceAppLabel = "Messages",
            rawTitle = "22122",
            rawBody = bancoCuscatlanSms,
            respectToggle = true
        )
        assertTrue(outcome is BankMessageProcessOutcome.QueuedForReview)
        val queued = (outcome as BankMessageProcessOutcome.QueuedForReview).entity
        assertEquals(10_00L, queued.amountCents)
        assertEquals("SUPER7 PRESIDENCIAL", queued.merchant)
        assertEquals(PaymentMethod.CARD.name, queued.paymentMethod)
        assertEquals("7708", queued.cardLastFour)

        val pending = repo.pendingDetectedTransactions.first()
        assertEquals(1, pending.size)
    }

    @Test
    fun scenario2_googleMessagesStyleExtraTextExtractsAndLogsToQueue() = runBlocking {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "B.CUSCATLAN")
            putCharSequence(Notification.EXTRA_TEXT, bancoCuscatlanSms)
        }

        val extraction = NotificationTextExtractor.extract(extras)
        assertEquals("B.CUSCATLAN", extraction.title)
        assertEquals(1, extraction.candidates.size)
        assertEquals(bancoCuscatlanSms, extraction.candidates.first().body)

        val outcome = repo.processIncomingBankMessage(
            sourcePackage = "com.google.android.apps.messaging",
            sourceAppLabel = "Messages",
            rawTitle = extraction.candidates.first().title,
            rawBody = extraction.candidates.first().body,
            respectToggle = true
        )
        assertTrue(outcome is BankMessageProcessOutcome.QueuedForReview)
        val queued = (outcome as BankMessageProcessOutcome.QueuedForReview).entity
        assertEquals(10_00L, queued.amountCents)
        assertEquals("SUPER7 PRESIDENCIAL", queued.merchant)
    }

    @Test
    fun scenario3_extraBigTextPreferredOverTruncatedExtraTextWithoutDuplication() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "22122")
            putCharSequence(
                Notification.EXTRA_TEXT,
                "Alerta de consumo con Tarjeta de Debito..."
            )
            putCharSequence(Notification.EXTRA_BIG_TEXT, bancoCuscatlanSms)
        }

        val extraction = NotificationTextExtractor.extract(extras)
        assertTrue(extraction.metadata.hasBigText)
        assertEquals(1, extraction.candidates.size)
        assertEquals(bancoCuscatlanSms, extraction.candidates.first().body)

        val parsed = BankMessageParser.parse(
            title = extraction.candidates.first().title,
            body = extraction.candidates.first().body,
            sourceAppLabel = "Messages"
        )
        assertNotNull(parsed)
        assertEquals(10_00L, parsed!!.amountCents)
        assertEquals("SUPER7 PRESIDENCIAL", parsed.merchant)
    }

    @Test
    fun scenario4_extraMessagesMessagingStyleWithOlderOtpAndDuplicateExtraText() = runBlocking {
        val olderOtpMsg = Bundle().apply {
            putCharSequence("text", "Su codigo de verificacion OTP es 482910. No la compartas.")
            putCharSequence("sender", "B.CUSCATLAN")
            putLong("time", 1_700_000_000_000L)
        }
        val newerChargeMsg = Bundle().apply {
            putCharSequence("text", bancoCuscatlanSms)
            putCharSequence("sender", "B.CUSCATLAN")
            putLong("time", 1_700_000_060_000L)
        }

        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "B.CUSCATLAN")
            putCharSequence(Notification.EXTRA_TEXT, "2 new messages")
            putParcelableArray(
                Notification.EXTRA_MESSAGES,
                arrayOf<Parcelable>(olderOtpMsg, newerChargeMsg)
            )
        }

        val extraction = NotificationTextExtractor.extract(extras)
        assertEquals(2, extraction.metadata.extraMessagesCount)
        // "2 new messages" summary is excluded; newest message comes first
        assertEquals(2, extraction.candidates.size)
        assertEquals(bancoCuscatlanSms, extraction.candidates[0].body)

        // Also verify duplicate EXTRA_TEXT mirroring EXTRA_MESSAGES does not yield duplicate candidate
        val dedupExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "B.CUSCATLAN")
            putCharSequence(Notification.EXTRA_TEXT, bancoCuscatlanSms)
            putParcelableArray(
                Notification.EXTRA_MESSAGES,
                arrayOf<Parcelable>(newerChargeMsg)
            )
        }
        val dedupExtraction = NotificationTextExtractor.extract(dedupExtras)
        assertEquals(1, dedupExtraction.candidates.size)

        // Process the extracted candidates through repository
        val firstOutcome = repo.processIncomingBankMessage(
            sourcePackage = "com.google.android.apps.messaging",
            sourceAppLabel = "Messages",
            rawTitle = extraction.candidates[0].title,
            rawBody = extraction.candidates[0].body,
            respectToggle = true
        )
        val secondOutcome = repo.processIncomingBankMessage(
            sourcePackage = "com.google.android.apps.messaging",
            sourceAppLabel = "Messages",
            rawTitle = extraction.candidates[1].title,
            rawBody = extraction.candidates[1].body,
            respectToggle = true
        )
        assertTrue(firstOutcome is BankMessageProcessOutcome.QueuedForReview)
        assertEquals(BankMessageProcessOutcome.Ignored, secondOutcome)
    }

    @Test
    fun scenario5_missingNullAndEmptyExtrasHandledSafely() {
        val nullExtraction = NotificationTextExtractor.extract(null)
        assertEquals("", nullExtraction.title)
        assertTrue(nullExtraction.candidates.isEmpty())

        val preDecision = BankNotificationFilter.evaluatePreExtraction(
            packageName = "com.google.android.apps.messaging",
            ownPackageName = context.packageName,
            hasNotification = true,
            flags = 0,
            hasExtras = false
        )
        assertTrue(preDecision is BankNotificationFilterDecision.Ignore)
        assertEquals("NULL_EXTRAS", (preDecision as BankNotificationFilterDecision.Ignore).reason)

        val emptyExtraction = NotificationTextExtractor.extract(Bundle())
        assertEquals("", emptyExtraction.title)
        assertTrue(emptyExtraction.candidates.isEmpty())
        val postDecision = BankNotificationFilter.evaluatePostExtraction(emptyExtraction)
        assertTrue(postDecision is BankNotificationFilterDecision.Ignore)
        assertEquals("EMPTY_CONTENT", (postDecision as BankNotificationFilterDecision.Ignore).reason)

        // Malformed EXTRA_MESSAGES entries do not throw
        val malformedExtras = Bundle().apply {
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(Bundle()))
        }
        val malformedExtraction = NotificationTextExtractor.extract(malformedExtras)
        assertTrue(malformedExtraction.candidates.isEmpty())
    }

    @Test
    fun scenario6_groupSummaryNotificationsAreFilteredOut() {
        val decision = BankNotificationFilter.evaluatePreExtraction(
            packageName = "com.google.android.apps.messaging",
            ownPackageName = context.packageName,
            hasNotification = true,
            flags = Notification.FLAG_GROUP_SUMMARY,
            hasExtras = true
        )
        assertTrue(decision is BankNotificationFilterDecision.Ignore)
        assertEquals("GROUP_SUMMARY", (decision as BankNotificationFilterDecision.Ignore).reason)
    }

    @Test
    fun scenario7_ongoingAndSystemAndOwnNotificationsAreFilteredOut() {
        val ongoing = BankNotificationFilter.evaluatePreExtraction(
            packageName = "com.google.android.apps.messaging",
            ownPackageName = context.packageName,
            hasNotification = true,
            flags = Notification.FLAG_ONGOING_EVENT,
            hasExtras = true
        )
        assertEquals("ONGOING_EVENT", (ongoing as BankNotificationFilterDecision.Ignore).reason)

        val ownApp = BankNotificationFilter.evaluatePreExtraction(
            packageName = context.packageName,
            ownPackageName = context.packageName,
            hasNotification = true,
            flags = 0,
            hasExtras = true
        )
        assertEquals("OWN_APP", (ownApp as BankNotificationFilterDecision.Ignore).reason)

        val systemUi = BankNotificationFilter.evaluatePreExtraction(
            packageName = "com.android.systemui",
            ownPackageName = context.packageName,
            hasNotification = true,
            flags = 0,
            hasExtras = true
        )
        assertEquals("SYSTEM_PACKAGE", (systemUi as BankNotificationFilterDecision.Ignore).reason)

        val validSms = BankNotificationFilter.evaluatePreExtraction(
            packageName = "com.google.android.apps.messaging",
            ownPackageName = context.packageName,
            hasNotification = true,
            flags = Notification.FLAG_AUTO_CANCEL,
            hasExtras = true
        )
        assertEquals(BankNotificationFilterDecision.Allow, validSms)
    }

    @Test
    fun scenario8_duplicateBankNotificationsAreDeduplicatedWithinWindow() = runBlocking {
        val first = repo.processIncomingBankMessage(
            sourcePackage = "com.google.android.apps.messaging",
            sourceAppLabel = "Messages",
            rawTitle = "22122",
            rawBody = bancoCuscatlanSms,
            respectToggle = true
        )
        val second = repo.processIncomingBankMessage(
            sourcePackage = "com.google.android.apps.messaging",
            sourceAppLabel = "Messages",
            rawTitle = "22122",
            rawBody = bancoCuscatlanSms,
            respectToggle = true
        )

        assertTrue(first is BankMessageProcessOutcome.QueuedForReview)
        assertEquals(BankMessageProcessOutcome.Duplicate, second)
        assertEquals(1, repo.pendingDetectedTransactions.first().size)
    }

    @Test
    fun scenario9_otpAndVerificationMessagesAreIgnored() = runBlocking {
        val otpSamples = listOf(
            "Your verification code is 482910 for $25.00 login attempt. Do not share.",
            "Tu codigo de verificacion es 918273 para autorizar USD10.00. No compartas esta clave dinamica.",
            "Banco Cuscatlan: Su OTP temporal es 554433."
        )

        otpSamples.forEach { sms ->
            assertNull(BankMessageParser.parse("22122", sms, "Messages"))
            val outcome = repo.processIncomingBankMessage(
                sourcePackage = "com.google.android.apps.messaging",
                sourceAppLabel = "Messages",
                rawTitle = "22122",
                rawBody = sms,
                respectToggle = true
            )
            assertEquals(BankMessageProcessOutcome.Ignored, outcome)
        }
    }

    @Test
    fun scenario10_declinedRefundPromoAndBalanceOnlyAlertsAreIgnored() = runBlocking {
        val nonChargeSamples = listOf(
            "Declined charge of $45.00 at Target on card ending 4821",
            "Compra rechazada por USD10.00 en SUPER7 PRESIDENCIAL por fondos insuficientes",
            "Refund of $18.40 credited back to your card ending 4821",
            "Promo: Get 20% off your next purchase of $50.00!",
            "Your available balance is $1,240.00 on checking account 7708",
            "Saldo disponible: USD 850.00 en Cuenta B.CUSCATLAN 7708"
        )

        nonChargeSamples.forEach { sms ->
            assertNull(BankMessageParser.parse("Bank Alert", sms, "Messages"))
            val outcome = repo.processIncomingBankMessage(
                sourcePackage = "com.google.android.apps.messaging",
                sourceAppLabel = "Messages",
                rawTitle = "Bank Alert",
                rawBody = sms,
                respectToggle = true
            )
            assertEquals(BankMessageProcessOutcome.Ignored, outcome)
        }
    }

    @Test
    fun preferencesAndNotificationAccessStateVerifyCorrectly() = runBlocking {
        // Default bankDetectionEnabled is true, bankAutoLogEnabled is false
        val initial = prefsRepo.getCurrentPreferences()
        assertTrue(initial.bankDetectionEnabled)
        assertFalse(initial.bankAutoLogEnabled)

        // Verify toggling bankDetectionEnabled off blocks automatic processing when respectToggle = true
        prefsRepo.updateBankDetectionSettings(
            detectionEnabled = false,
            autoLogEnabled = false,
            defaultAccountName = "Checking"
        )
        val disabledOutcome = repo.processIncomingBankMessage(
            sourcePackage = "com.google.android.apps.messaging",
            sourceAppLabel = "Messages",
            rawTitle = "22122",
            rawBody = bancoCuscatlanSms,
            respectToggle = true
        )
        assertEquals(BankMessageProcessOutcome.Ignored, disabledOutcome)

        // Manual test (respectToggle = false) still parses identically
        val manualOutcome = repo.processIncomingBankMessage(
            sourcePackage = "manual_paste",
            sourceAppLabel = "Messages",
            rawTitle = "22122",
            rawBody = bancoCuscatlanSms,
            respectToggle = false
        )
        assertTrue(manualOutcome is BankMessageProcessOutcome.QueuedForReview)

        // Verify NotificationListener access check reflects Android enabled_notification_listeners setting
        assertFalse(BankNotificationAccessHelper.isNotificationAccessEnabled(context))
        val component = "${context.packageName}/${BankNotificationListenerService::class.java.name}"
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            component
        )
        assertTrue(BankNotificationAccessHelper.isNotificationAccessEnabled(context))
    }
}
