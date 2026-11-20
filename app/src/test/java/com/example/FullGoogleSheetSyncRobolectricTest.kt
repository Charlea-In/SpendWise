package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountDepositEntity
import com.example.data.AccountEntity
import com.example.data.AccountTransferEntity
import com.example.data.AppsScriptSyncClient
import com.example.data.BudgetPreferences
import com.example.data.BudgetPreferencesRepository
import com.example.data.CategoryBudgetEntity
import com.example.data.CommitmentEntity
import com.example.data.ExpenseEntity
import com.example.data.PointsAccountEntity
import com.example.data.SpendWiseDatabase
import com.example.data.SpendWiseRepository
import com.example.model.CommitmentType
import com.example.model.DailyBudgetMode
import com.example.model.ExpenseCategory
import com.example.model.PaymentMethod
import com.example.model.RecurrenceCycle
import com.example.notifications.BudgetNotificationHelper
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FullGoogleSheetSyncRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: SpendWiseDatabase
    private lateinit var prefsRepo: BudgetPreferencesRepository
    private var server: HttpServer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = BudgetPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        server?.stop(0)
        db.close()
    }

    @Test
    fun appsScriptSyncClientSerializesAllEntitiesExcludesDemoAndHandlesV1AndV2Responses() = runBlocking {
        val capturedPayloads = CopyOnWriteArrayList<JSONObject>()
        var returnLegacyV1 = false

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { s ->
            s.createContext("/exec") { exchange ->
                val reqText = exchange.requestBody.bufferedReader().readText()
                val reqJson = if (reqText.isNotBlank()) JSONObject(reqText) else JSONObject()
                capturedPayloads.add(reqJson)

                val action = reqJson.optString("action")
                val respJson = when {
                    action == "deleteExpense" || action == "deleteRecord" -> {
                        JSONObject().put("status", "ok").put("schemaVersion", 2)
                    }
                    returnLegacyV1 -> {
                        JSONObject()
                            .put("status", "ok")
                            .put("expenses", reqJson.optJSONArray("expenses") ?: JSONArray())
                            .put("budgets", reqJson.optJSONArray("budgets") ?: JSONArray())
                    }
                    else -> {
                        JSONObject()
                            .put("status", "ok")
                            .put("schemaVersion", 2)
                            .put("expenses", reqJson.optJSONArray("expenses") ?: JSONArray())
                            .put("budgets", reqJson.optJSONArray("budgets") ?: JSONArray())
                            .put("accounts", reqJson.optJSONArray("accounts") ?: JSONArray())
                            .put("deposits", reqJson.optJSONArray("deposits") ?: JSONArray())
                            .put("transfers", reqJson.optJSONArray("transfers") ?: JSONArray())
                            .put("commitments", reqJson.optJSONArray("commitments") ?: JSONArray())
                            .put("pointsAccounts", reqJson.optJSONArray("pointsAccounts") ?: JSONArray())
                            .put("settings", reqJson.optJSONObject("settings") ?: JSONObject())
                    }
                }
                val bytes = respJson.toString().toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            s.start()
        }
        server = httpServer
        val url = "http://127.0.0.1:${httpServer.address.port}/exec"
        val client = AppsScriptSyncClient()

        // Include both real and demo entries to verify demo rows are excluded from sync
        val realExp = ExpenseEntity(
            remoteId = "exp-1",
            description = "Lunch",
            amountCents = 12_50L,
            category = ExpenseCategory.FOOD_DINING.name,
            paymentMethod = PaymentMethod.CARD.name,
            accountName = "Checking",
            timestampMillis = 1_700_000_000_000L,
            note = "Tacos",
            wasAutoCategorized = true,
            isDemo = false,
            updatedAtMillis = 1000L
        )
        val demoExp = realExp.copy(remoteId = "exp-demo", isDemo = true)

        val realAcct = AccountEntity(
            remoteId = "acct-1",
            name = "Checking",
            initialBalanceCents = 500_00L,
            createdAtMillis = 900L,
            updatedAtMillis = 1000L,
            isDemo = false
        )
        val demoAcct = realAcct.copy(remoteId = "acct-demo", name = "Demo", isDemo = true)

        val realDep = AccountDepositEntity(
            remoteId = "dep-1",
            accountName = "Checking",
            source = "Uber",
            amountCents = 15_00L,
            timestampMillis = 1_700_000_050_000L,
            note = "Payout",
            isDemo = false,
            updatedAtMillis = 1000L
        )
        val realTr = AccountTransferEntity(
            remoteId = "tr-1",
            fromAccountName = "Checking",
            toAccountName = "Cash",
            amountCents = 25_00L,
            timestampMillis = 1_700_000_060_000L,
            note = "ATM",
            isDemo = false,
            updatedAtMillis = 1000L
        )
        val realPlan = CommitmentEntity(
            remoteId = "plan-1",
            title = "Car Loan",
            type = CommitmentType.DEBT.name,
            amountCents = 250_00L,
            targetOrTotalCents = 2000_00L,
            progressCents = 500_00L,
            cycle = RecurrenceCycle.MONTHLY.name,
            dueDayOfMonth = 15,
            accountName = "Checking",
            category = ExpenseCategory.TRANSPORTATION.name,
            updatedAtMillis = 1000L
        )
        val realPts = PointsAccountEntity(
            remoteId = "pts-1",
            name = "Miles",
            pointsBalance = 12_000L,
            unitLabel = "pts",
            centsPerPointTimes100 = 150L,
            updatedAtMillis = 1000L
        )
        val realBudget = CategoryBudgetEntity(
            category = ExpenseCategory.FOOD_DINING.name,
            monthlyLimitCents = 300_00L,
            updatedAtMillis = 1000L
        )
        val prefs = BudgetPreferences(
            monthlyBudgetCents = 2500_00L,
            monthlySavingsGoalCents = 500_00L,
            dailyMode = DailyBudgetMode.FIXED_DAILY,
            fixedDailyLimitCents = 65_00L,
            currencySymbol = "€",
            todayUseAccountsBalance = false,
            settingsUpdatedAtMillis = 1000L
        )

        val v2Result = client.syncAll(
            scriptUrl = url,
            token = "my-token",
            localExpenses = listOf(realExp, demoExp),
            localBudgets = listOf(realBudget),
            localAccounts = listOf(realAcct, demoAcct),
            localDeposits = listOf(realDep),
            localTransfers = listOf(realTr),
            localCommitments = listOf(realPlan),
            localPointsAccounts = listOf(realPts),
            localSettings = prefs
        ).getOrThrow()

        assertTrue(v2Result.supportsFullSync)
        assertEquals(1, v2Result.remoteExpenses.size)
        assertEquals(1, v2Result.remoteAccounts.size)
        assertEquals(1, v2Result.remoteDeposits.size)
        assertEquals(1, v2Result.remoteTransfers.size)
        assertEquals(1, v2Result.remoteCommitments.size)
        assertEquals(1, v2Result.remotePointsAccounts.size)
        assertEquals(1, v2Result.remoteBudgets.size)
        assertNotNull(v2Result.remoteSettings)
        assertEquals("€", v2Result.remoteSettings!!.currencySymbol)
        assertEquals(2500_00L, v2Result.remoteSettings!!.monthlyBudgetCents)
        assertFalse(v2Result.remoteSettings!!.todayUseAccountsBalance)

        // Verify demo rows were filtered out before sending
        val sent = capturedPayloads.first()
        assertEquals("my-token", sent.getString("token"))
        assertEquals(1, sent.getJSONArray("expenses").length())
        assertEquals(1, sent.getJSONArray("accounts").length())

        // Verify deleteRemoteRecord uses legacy deleteExpense for expenses and deleteRecord for other types
        client.deleteRemoteRecord(url, "my-token", "expense", "exp-1").getOrThrow()
        client.deleteRemoteRecord(url, "my-token", "deposit", "dep-1").getOrThrow()
        assertEquals("deleteExpense", capturedPayloads[1].getString("action"))
        assertEquals("deleteRecord", capturedPayloads[2].getString("action"))
        assertEquals("deposit", capturedPayloads[2].getString("recordType"))
        assertEquals("dep-1", capturedPayloads[2].getString("remoteId"))

        // Verify legacy Code.gs v1 response degrades gracefully with upgrade hint
        returnLegacyV1 = true
        val legacyResult = client.syncAll(
            scriptUrl = url,
            token = "my-token",
            localExpenses = listOf(realExp),
            localBudgets = listOf(realBudget)
        ).getOrThrow()
        assertFalse(legacyResult.supportsFullSync)
        assertEquals(1, legacyResult.remoteExpenses.size)
        assertTrue(legacyResult.message.contains("Update Code.gs", ignoreCase = true))
    }

    @Test
    fun secondEmptyDevicePullsAllDataTypesAndSettingsFromGoogleSheet() = runBlocking {
        val deletedRemoteCalls = CopyOnWriteArrayList<Pair<String, String>>()

        // Prepare a remote Sheet response representing Device 1's full dataset
        val remotePayload = JSONObject()
            .put("status", "ok")
            .put("schemaVersion", 2)
            .put(
                "accounts",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("remoteId", "acct-checking-uuid")
                            .put("name", "Checking")
                            .put("initialBalanceCents", 55_00L)
                            .put("createdAtMillis", 1000L)
                            .put("updatedAtMillis", 2000L)
                    )
                    .put(
                        JSONObject()
                            .put("remoteId", "acct-wallet-uuid")
                            .put("name", "Wallet")
                            .put("initialBalanceCents", 15_00L)
                            .put("createdAtMillis", 1000L)
                            .put("updatedAtMillis", 2000L)
                    )
            )
            .put(
                "expenses",
                JSONArray().put(
                    JSONObject()
                        .put("remoteId", "exp-coffee-uuid")
                        .put("description", "Blue Bottle Coffee")
                        .put("amountCents", 6_50L)
                        .put("category", "FOOD_DINING")
                        .put("paymentMethod", "CARD")
                        .put("accountName", "Checking")
                        .put("timestampMillis", 1_700_000_000_000L)
                        .put("note", "Morning coffee")
                        .put("wasAutoCategorized", true)
                        .put("updatedAtMillis", 2000L)
                )
            )
            .put(
                "budgets",
                JSONArray().put(
                    JSONObject()
                        .put("category", "FOOD_DINING")
                        .put("monthlyLimitCents", 450_00L)
                        .put("updatedAtMillis", 2000L)
                )
            )
            .put(
                "deposits",
                JSONArray().put(
                    JSONObject()
                        .put("remoteId", "dep-uber-uuid")
                        .put("accountName", "Wallet")
                        .put("source", "Uber payout")
                        .put("amountCents", 15_00L)
                        .put("timestampMillis", 1_700_000_100_000L)
                        .put("note", "Airport ride")
                        .put("updatedAtMillis", 2000L)
                )
            )
            .put(
                "transfers",
                JSONArray().put(
                    JSONObject()
                        .put("remoteId", "tr-atm-uuid")
                        .put("fromAccountName", "Checking")
                        .put("toAccountName", "Wallet")
                        .put("amountCents", 10_00L)
                        .put("timestampMillis", 1_700_000_200_000L)
                        .put("note", "ATM withdrawal")
                        .put("updatedAtMillis", 2000L)
                )
            )
            .put(
                "commitments",
                JSONArray().put(
                    JSONObject()
                        .put("remoteId", "plan-rent-uuid")
                        .put("title", "Apartment Rent")
                        .put("type", "BILL")
                        .put("amountCents", 950_00L)
                        .put("targetOrTotalCents", 0L)
                        .put("progressCents", 0L)
                        .put("cycle", "MONTHLY")
                        .put("dueDayOfMonth", 1)
                        .put("statementDayOfMonth", 0)
                        .put("scheduledDateMillis", 0L)
                        .put("totalInstallments", 0)
                        .put("completedInstallments", 0)
                        .put("accountName", "Checking")
                        .put("category", "HOUSING")
                        .put("note", "Due 1st")
                        .put("reminderEnabled", true)
                        .put("isActive", true)
                        .put("lastCompletedCycleKey", "")
                        .put("createdAtMillis", 1000L)
                        .put("updatedAtMillis", 2000L)
                )
            )
            .put(
                "pointsAccounts",
                JSONArray().put(
                    JSONObject()
                        .put("remoteId", "pts-sapphire-uuid")
                        .put("name", "Sapphire Rewards")
                        .put("pointsBalance", 32_500L)
                        .put("unitLabel", "pts")
                        .put("centsPerPointTimes100", 125L)
                        .put("note", "Travel portal")
                        .put("updatedAtMillis", 2000L)
                        .put("createdAtMillis", 1000L)
                )
            )
            .put(
                "settings",
                JSONObject()
                    .put("monthlyBudgetCents", 3200_00L)
                    .put("monthlySavingsGoalCents", 600_00L)
                    .put("dailyMode", "FIXED_DAILY")
                    .put("fixedDailyLimitCents", 85_00L)
                    .put("currencySymbol", "€")
                    .put("todayUseAccountsBalance", false)
                    .put("updatedAtMillis", 2000L)
            )

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { s ->
            s.createContext("/exec") { exchange ->
                val body = exchange.requestBody.bufferedReader().readText()
                val reqJson = JSONObject(body)
                val action = reqJson.optString("action")
                val resp = if (action == "deleteRecord" || action == "deleteExpense") {
                    deletedRemoteCalls.add(
                        reqJson.optString("recordType", "expense") to reqJson.optString("remoteId")
                    )
                    JSONObject().put("status", "ok").put("schemaVersion", 2)
                } else {
                    remotePayload
                }
                val bytes = resp.toString().toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            s.start()
        }
        server = httpServer
        val url = "http://127.0.0.1:${httpServer.address.port}/exec"

        // Reset Device 2 preferences with settingsUpdatedAtMillis = 0L and configure Sheet URL
        prefsRepo.applyRemoteSettings(
            monthlyBudgetCents = 1800_00L,
            monthlySavingsGoalCents = 300_00L,
            dailyMode = DailyBudgetMode.AUTO_PACED,
            fixedDailyLimitCents = 50_00L,
            currencySymbol = "$",
            todayUseAccountsBalance = true,
            updatedAtMillis = 0L
        )
        prefsRepo.updateAppsScriptConfig(url = url, token = "tok", autoSync = true)

        val repo = SpendWiseRepository(
            expenseDao = db.expenseDao(),
            categoryBudgetDao = db.categoryBudgetDao(),
            accountDao = db.accountDao(),
            accountTransferDao = db.accountTransferDao(),
            pointsAccountDao = db.pointsAccountDao(),
            commitmentDao = db.commitmentDao(),
            detectedTransactionDao = db.detectedTransactionDao(),
            preferencesRepository = prefsRepo,
            notificationHelper = BudgetNotificationHelper(context),
            accountDepositDao = db.accountDepositDao()
        )

        // Device 2 also pre-created "Checking" locally with 0 balance to verify name deduplication
        db.accountDao().insertAccount(
            AccountEntity(
                remoteId = "local-temp-uuid",
                name = "Checking",
                initialBalanceCents = 0L,
                updatedAtMillis = 0L
            )
        )

        val syncRes = repo.syncWithGoogleSheet()
        assertTrue(syncRes.isSuccess)

        // Verify ALL 8 data categories synced onto Device 2 cleanly
        val accounts = repo.allAccounts.first()
        assertEquals(2, accounts.size) // Checking adopted canonical remoteId without duplicating!
        val checking = accounts.first { it.name == "Checking" }
        assertEquals("acct-checking-uuid", checking.remoteId)
        assertEquals(55_00L, checking.initialBalanceCents)

        val expenses = repo.allExpenses.first()
        assertEquals(1, expenses.size)
        assertEquals("Blue Bottle Coffee", expenses.first().description)
        assertEquals(6_50L, expenses.first().amountCents)

        val budgets = repo.categoryBudgets.first()
        assertEquals(1, budgets.size)
        assertEquals(450_00L, budgets.first().monthlyLimitCents)

        val deposits = repo.allDeposits.first()
        assertEquals(1, deposits.size)
        assertEquals("Uber payout", deposits.first().source)
        assertEquals(15_00L, deposits.first().amountCents)

        val transfers = repo.allTransfers.first()
        assertEquals(1, transfers.size)
        assertEquals(10_00L, transfers.first().amountCents)
        assertEquals("Wallet", transfers.first().toAccountName)

        val plans = repo.allCommitments.first()
        assertEquals(1, plans.size)
        assertEquals("Apartment Rent", plans.first().title)
        assertEquals(950_00L, plans.first().amountCents)

        val points = repo.allPointsAccounts.first()
        assertEquals(1, points.size)
        assertEquals("Sapphire Rewards", points.first().name)
        assertEquals(32_500L, points.first().pointsBalance)

        val syncedPrefs = prefsRepo.getCurrentPreferences()
        assertEquals(3200_00L, syncedPrefs.monthlyBudgetCents)
        assertEquals(600_00L, syncedPrefs.monthlySavingsGoalCents)
        assertEquals(DailyBudgetMode.FIXED_DAILY, syncedPrefs.dailyMode)
        assertEquals(85_00L, syncedPrefs.fixedDailyLimitCents)
        assertEquals("€", syncedPrefs.currencySymbol)
        assertFalse(syncedPrefs.todayUseAccountsBalance)
        assertEquals(2000L, syncedPrefs.settingsUpdatedAtMillis)

        // Verify deleting a deposit/commitment/points sends deleteRecord remotely
        repo.deleteAccountDeposit(deposits.first())
        repo.deleteCommitment(plans.first())
        repo.deletePointsAccount(points.first())
        assertTrue(deletedRemoteCalls.contains("deposit" to "dep-uber-uuid"))
        assertTrue(deletedRemoteCalls.contains("commitment" to "plan-rent-uuid"))
        assertTrue(deletedRemoteCalls.contains("points" to "pts-sapphire-uuid"))
    }

    @Test
    fun roomMigration9To10PreservesRowsAndBackfillsRemoteIds() {
        val dbName = "migration_9_10_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `remoteId` TEXT NOT NULL, `description` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `category` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, `accountName` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, `wasAutoCategorized` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `category_budgets` (`category` TEXT NOT NULL PRIMARY KEY, `monthlyLimitCents` INTEGER NOT NULL, `alert80SentMonth` TEXT NOT NULL, `alert100SentMonth` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `initialBalanceCents` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `financial_commitments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `targetOrTotalCents` INTEGER NOT NULL, `progressCents` INTEGER NOT NULL, `cycle` TEXT NOT NULL, `dueDayOfMonth` INTEGER NOT NULL, `statementDayOfMonth` INTEGER NOT NULL, `scheduledDateMillis` INTEGER NOT NULL, `totalInstallments` INTEGER NOT NULL, `completedInstallments` INTEGER NOT NULL, `accountName` TEXT NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `reminderEnabled` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `lastCompletedCycleKey` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `detected_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fingerprint` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `sourceAppLabel` TEXT NOT NULL, `rawTitle` TEXT NOT NULL, `rawMessage` TEXT NOT NULL, `merchant` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `suggestedCategory` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, `cardLastFour` TEXT NOT NULL, `matchedAccountName` TEXT NOT NULL, `status` TEXT NOT NULL, `receivedAtMillis` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `account_transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromAccountName` TEXT NOT NULL, `toAccountName` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, `isDemo` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `points_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `pointsBalance` INTEGER NOT NULL, `unitLabel` TEXT NOT NULL, `centsPerPointTimes100` INTEGER NOT NULL, `note` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `account_deposits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountName` TEXT NOT NULL, `source` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, `isDemo` INTEGER NOT NULL)")

                        db.execSQL("INSERT INTO `accounts` (`name`, `initialBalanceCents`, `createdAtMillis`, `isDemo`) VALUES ('Checking', 5500, 1000, 0)")
                        db.execSQL("INSERT INTO `account_deposits` (`accountName`, `source`, `amountCents`, `timestampMillis`, `note`, `isDemo`) VALUES ('Checking', 'Friend', 500, 1000, '', 0)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(SpendWiseDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val accts = migratedDb.accountDao().getNonDemoAccounts()
            assertEquals(1, accts.size)
            assertEquals("Checking", accts.first().name)
            assertEquals(5500L, accts.first().initialBalanceCents)
            assertTrue(accts.first().remoteId.isNotBlank())

            val deps = migratedDb.accountDepositDao().getNonDemoDeposits()
            assertEquals(1, deps.size)
            assertEquals("Friend", deps.first().source)
            assertTrue(deps.first().remoteId.isNotBlank())
        }
        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun repositoryEmitsActiveRequestMessageWhileSyncIsInFlightAndClearsAfter() = runBlocking {
        val requestStarted = CountDownLatch(1)
        val allowResponse = CountDownLatch(1)

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { s ->
            s.createContext("/exec") { exchange ->
                requestStarted.countDown()
                allowResponse.await(5, TimeUnit.SECONDS)
                val bytes = JSONObject()
                    .put("status", "ok")
                    .put("schemaVersion", 2)
                    .put("expenses", JSONArray())
                    .put("budgets", JSONArray())
                    .put("accounts", JSONArray())
                    .put("deposits", JSONArray())
                    .put("transfers", JSONArray())
                    .put("commitments", JSONArray())
                    .put("pointsAccounts", JSONArray())
                    .toString()
                    .toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            s.start()
        }
        server = httpServer
        val url = "http://127.0.0.1:${httpServer.address.port}/exec"
        prefsRepo.updateAppsScriptConfig(url = url, token = "", autoSync = true)

        val repo = SpendWiseRepository(
            expenseDao = db.expenseDao(),
            categoryBudgetDao = db.categoryBudgetDao(),
            accountDao = db.accountDao(),
            accountTransferDao = db.accountTransferDao(),
            pointsAccountDao = db.pointsAccountDao(),
            commitmentDao = db.commitmentDao(),
            detectedTransactionDao = db.detectedTransactionDao(),
            preferencesRepository = prefsRepo,
            notificationHelper = BudgetNotificationHelper(context),
            accountDepositDao = db.accountDepositDao()
        )

        assertNull(repo.activeRequestMessage.first())

        val syncDeferred = async(Dispatchers.IO) { repo.syncWithGoogleSheet() }
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
        assertEquals("Syncing with Google Sheet...", repo.activeRequestMessage.first())

        allowResponse.countDown()
        assertTrue(syncDeferred.await().isSuccess)
        assertNull(repo.activeRequestMessage.first())
    }
}
