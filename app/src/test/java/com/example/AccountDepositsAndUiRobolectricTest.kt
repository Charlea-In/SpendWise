package com.example

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountDepositEntity
import com.example.data.AccountEntity
import com.example.data.BudgetPreferencesRepository
import com.example.data.SpendWiseDatabase
import com.example.data.SpendWiseRepository
import com.example.notifications.BudgetNotificationHelper
import com.example.ui.components.GlobalLoadingIndicator
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.SpendWiseTheme
import com.example.util.FinancialInsightsEngine
import com.example.viewmodel.SpendWiseUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountDepositsAndUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SpendWiseDatabase
    private lateinit var repo: SpendWiseRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SpendWiseRepository(
            expenseDao = db.expenseDao(),
            categoryBudgetDao = db.categoryBudgetDao(),
            accountDao = db.accountDao(),
            accountTransferDao = db.accountTransferDao(),
            pointsAccountDao = db.pointsAccountDao(),
            commitmentDao = db.commitmentDao(),
            detectedTransactionDao = db.detectedTransactionDao(),
            preferencesRepository = BudgetPreferencesRepository(context),
            notificationHelper = BudgetNotificationHelper(context),
            accountDepositDao = db.accountDepositDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun repositoryPersistsEditsRenamesDeletesAndRestoresCustomDeposits() = runBlocking {
        // Validation rejects empty source or non-positive amount
        assertTrue(
            repo.saveAccountDeposit(
                accountName = "Main",
                source = "",
                amountCents = 5_00L,
                timestampMillis = 1_700_000_000_000L,
                note = ""
            ).isFailure
        )
        assertTrue(
            repo.saveAccountDeposit(
                accountName = "Main",
                source = "Uber",
                amountCents = 0L,
                timestampMillis = 1_700_000_000_000L,
                note = ""
            ).isFailure
        )

        // Saving a deposit for a brand new custom account creates the account automatically
        val now = System.currentTimeMillis()
        val createdRes = repo.saveAccountDeposit(
            accountName = "Personal",
            source = "Friend transfer",
            amountCents = 5_00L,
            timestampMillis = now,
            note = "Paid for lunch earlier"
        )
        assertTrue(createdRes.isSuccess)
        val created = createdRes.getOrThrow()

        val accounts = repo.allAccounts.first()
        assertEquals(1, accounts.size)
        assertEquals("Personal", accounts.first().name)
        assertEquals(1, repo.allDeposits.first().size)

        // Updating deposit keeps same row id and reflects customized source & amount
        val updatedRes = repo.saveAccountDeposit(
            existingId = created.id,
            accountName = "Personal",
            source = "Uber payout",
            amountCents = 15_00L,
            timestampMillis = now,
            note = "Saturday trip"
        )
        assertTrue(updatedRes.isSuccess)
        val depositsAfterUpdate = repo.allDeposits.first()
        assertEquals(1, depositsAfterUpdate.size)
        assertEquals("Uber payout", depositsAfterUpdate.first().source)
        assertEquals(15_00L, depositsAfterUpdate.first().amountCents)

        // Renaming account propagates to existing deposits
        val renameRes = repo.saveAccount(
            existingId = accounts.first().id,
            name = "Daily Cash",
            initialBalanceCents = 55_00L
        )
        assertTrue(renameRes.isSuccess)
        assertEquals("Daily Cash", repo.allDeposits.first().first().accountName)

        // Delete and restore deposit
        val toDelete = repo.allDeposits.first().first()
        repo.deleteAccountDeposit(toDelete)
        assertTrue(repo.allDeposits.first().isEmpty())

        repo.restoreAccountDeposit(toDelete)
        assertEquals(1, repo.allDeposits.first().size)
        assertEquals("Uber payout", repo.allDeposits.first().first().source)
    }

    @Test
    fun todayScreenHides7DayChartAndExposesAddFundsEntries() {
        val now = System.currentTimeMillis()
        val account = AccountEntity(id = 1, name = "Checking", initialBalanceCents = 55_00L)
        val deposit = AccountDepositEntity(
            id = 10,
            accountName = "Checking",
            source = "Friend transfer back",
            amountCents = 5_00L,
            timestampMillis = now,
            note = "Dinner share"
        )
        val summary = FinancialInsightsEngine.compute(
            expenses = emptyList(),
            categoryBudgets = emptyList(),
            prefs = com.example.data.BudgetPreferences(),
            nowMillis = now,
            accounts = listOf(account),
            deposits = listOf(deposit)
        )
        val uiState = SpendWiseUiState(
            isLoading = false,
            accounts = listOf(account),
            deposits = listOf(deposit),
            todayDeposits = listOf(deposit),
            summary = summary
        )

        var addFundsClickedAccount: AccountEntity? = AccountEntity(id = -1, name = "UNSET")

        composeRule.setContent {
            SpendWiseTheme {
                TodayScreen(
                    uiState = uiState,
                    onOpenNewExpense = {},
                    onEditExpense = {},
                    onDeleteExpense = {},
                    onQuickPreset = {},
                    onDismissAlertBanner = {},
                    onOpenBudgetTab = {},
                    onOpenPlansTab = {},
                    onCreateCommitment = {},
                    onRecordCommitmentPayment = { _, _ -> },
                    onOpenTodaySourceDialog = {},
                    onOpenSheetSyncDialog = {},
                    onToggleDemoMode = {},
                    onSelectTodayAccount = {},
                    onCreateAccount = {},
                    onEditAccount = {},
                    onAddFundsClick = { acct -> addFundsClickedAccount = acct },
                    onTransferClick = {},
                    onEditTransfer = {},
                    onDeleteTransfer = {},
                    onEditDeposit = {},
                    onDeleteDeposit = {},
                    onCreatePointsAccount = {},
                    onEditPointsAccount = {},
                    onGeminiPromptChanged = {},
                    onSubmitGeminiExpense = {},
                    onStartVoiceRecording = {},
                    onStopVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onVoicePermissionDenied = {},
                    onClearGeminiFeedback = {},
                    onApproveDetectedCharge = {},
                    onEditDetectedCharge = {},
                    onDismissDetectedCharge = {},
                    onOpenBankMessageDialog = {},
                    onPermissionResult = {}
                )
            }
        }

        composeRule.waitForIdle()

        // 7-day chart MUST NOT exist on the Today tab
        composeRule.onNodeWithTag("today_weekly_trend_card").assertDoesNotExist()
        composeRule.onNodeWithText("Last 7 Days Spending").assertDoesNotExist()

        // Add funds chip is visible and triggers callback
        composeRule.onNodeWithTag("today_add_funds_button").assertIsDisplayed()
        composeRule.onNodeWithTag("today_add_funds_button").performClick()
        composeRule.waitForIdle()
        assertEquals(null, addFundsClickedAccount)
    }

    @Test
    fun globalLoadingIndicatorDisplaysMessageWhileProcessingAndHidesWhenCleared() {
        val loadingMsg = mutableStateOf<String?>("Syncing with Google Sheet...")

        composeRule.setContent {
            SpendWiseTheme {
                GlobalLoadingIndicator(message = loadingMsg.value)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global_loading_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("global_loading_spinner").assertIsDisplayed()
        composeRule.onNodeWithText("Syncing with Google Sheet...").assertIsDisplayed()

        loadingMsg.value = null
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global_loading_indicator").assertDoesNotExist()
    }
}
