package com.example

import com.example.data.AccountEntity
import com.example.data.AccountTransferEntity
import com.example.data.BudgetPreferences
import com.example.data.CategoryBudgetEntity
import com.example.data.CommitmentEntity
import com.example.data.ExpenseEntity
import com.example.data.PointsAccountEntity
import com.example.data.PointsFormatter
import com.example.model.BankMessageParser
import com.example.model.CategorizationConfidence
import com.example.model.CommitmentScheduleHelper
import com.example.model.CommitmentType
import com.example.model.CommitmentWithStatus
import com.example.model.DailyBudgetMode
import com.example.model.ExpenseCategorizer
import com.example.model.ExpenseCategory
import com.example.model.PaymentMethod
import com.example.model.RecurrenceCycle
import com.example.util.BudgetStatusLevel
import com.example.util.FinancialInsightsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SpendWiseCoreUnitTest {

    @Test
    fun categorizerMatchesKnownMerchantsWithHighConfidence() {
        val groceries = ExpenseCategorizer.analyze("Trader Joe's", 54_20L)
        assertEquals(ExpenseCategory.GROCERIES, groceries.suggestedCategory)
        assertEquals(CategorizationConfidence.HIGH, groceries.confidence)

        val utility = ExpenseCategorizer.analyze("PG&E Electric Bill", 112_00L)
        assertEquals(ExpenseCategory.UTILITIES, utility.suggestedCategory)
        assertEquals(CategorizationConfidence.HIGH, utility.confidence)

        val transit = ExpenseCategorizer.analyze("Uber ride downtown", 18_50L)
        assertEquals(ExpenseCategory.TRANSPORTATION, transit.suggestedCategory)
        assertEquals(CategorizationConfidence.HIGH, transit.confidence)
    }

    @Test
    fun categorizerPromptsUserWhenAmbiguousOrUnknown() {
        val targetResult = ExpenseCategorizer.analyze("Target", 42_00L)
        assertEquals(CategorizationConfidence.UNSURE, targetResult.confidence)
        assertNull(targetResult.suggestedCategory)
        assertTrue(targetResult.candidates.contains(ExpenseCategory.SHOPPING))
        assertTrue(targetResult.candidates.contains(ExpenseCategory.GROCERIES))

        val unknownResult = ExpenseCategorizer.analyze("Alex Reimbursement", 650_00L)
        assertEquals(CategorizationConfidence.UNSURE, unknownResult.confidence)
        assertNull(unknownResult.suggestedCategory)
        assertTrue(unknownResult.candidates.isNotEmpty())
    }

    @Test
    fun categorizerUsesLearnedUserHistory() {
        val history = mapOf("blue bottle" to ExpenseCategory.FOOD_DINING)
        val result = ExpenseCategorizer.analyze("Blue Bottle", 6_50L, history)
        assertEquals(ExpenseCategory.FOOD_DINING, result.suggestedCategory)
        assertEquals(CategorizationConfidence.HIGH, result.confidence)
    }

    @Test
    fun insightsEngineComputesBudgetThresholdsAccurately() {
        val now = System.currentTimeMillis()
        val expenses = listOf(
            ExpenseEntity(
                id = 1,
                description = "Trader Joe's",
                amountCents = 250_00L,
                category = ExpenseCategory.GROCERIES.name,
                paymentMethod = "CARD",
                timestampMillis = now
            ),
            ExpenseEntity(
                id = 2,
                description = "AMC Cinema",
                amountCents = 130_00L,
                category = ExpenseCategory.ENTERTAINMENT.name,
                paymentMethod = "CARD",
                timestampMillis = now
            )
        )
        val budgets = listOf(
            CategoryBudgetEntity(category = ExpenseCategory.GROCERIES.name, monthlyLimitCents = 300_00L),
            CategoryBudgetEntity(category = ExpenseCategory.ENTERTAINMENT.name, monthlyLimitCents = 120_00L)
        )
        val prefs = BudgetPreferences(
            monthlyBudgetCents = 2000_00L,
            monthlySavingsGoalCents = 200_00L,
            dailyMode = DailyBudgetMode.FIXED_DAILY,
            fixedDailyLimitCents = 60_00L
        )

        val summary = FinancialInsightsEngine.compute(expenses, budgets, prefs, now)
        val groceriesProgress = summary.categoryProgress.first { it.category == ExpenseCategory.GROCERIES }
        val entProgress = summary.categoryProgress.first { it.category == ExpenseCategory.ENTERTAINMENT }

        assertEquals(BudgetStatusLevel.APPROACHING, groceriesProgress.status)
        assertEquals(BudgetStatusLevel.EXCEEDED, entProgress.status)
        assertEquals(2, summary.activeBudgetAlerts.size)
        assertEquals(BudgetStatusLevel.EXCEEDED, summary.paceStatus)
    }

    @Test
    fun insightsEngineComputesCustomSeparateAccountSummaries() {
        val now = System.currentTimeMillis()
        val accounts = listOf(
            AccountEntity(id = 1, name = "Chequera", initialBalanceCents = 100_00L),
            AccountEntity(id = 2, name = "Efectivo Casa", initialBalanceCents = 0L)
        )
        val expenses = listOf(
            ExpenseEntity(
                id = 10,
                description = "Churro",
                amountCents = 50L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = "CASH",
                accountName = "chequera",
                timestampMillis = now
            ),
            ExpenseEntity(
                id = 11,
                description = "Pan dulce",
                amountCents = 4_50L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = "CASH",
                accountName = "Chequera",
                timestampMillis = now
            ),
            ExpenseEntity(
                id = 12,
                description = "Agua",
                amountCents = 2_00L,
                category = ExpenseCategory.GROCERIES.name,
                paymentMethod = "CASH",
                accountName = "Efectivo Casa",
                timestampMillis = now - 86_400_000L * 40L
            )
        )

        val summary = FinancialInsightsEngine.compute(
            expenses = expenses,
            categoryBudgets = emptyList(),
            prefs = BudgetPreferences(),
            nowMillis = now,
            accounts = accounts
        )

        assertEquals(2, summary.accountSummaries.size)
        val chequera = summary.accountSummaries[0]
        assertEquals("Chequera", chequera.account.name)
        assertEquals(5_00L, chequera.totalSpentCents)
        assertEquals(5_00L, chequera.todaySpentCents)
        assertEquals(2, chequera.transactionCount)
        assertEquals(true, chequera.hasStartingBalance)
        assertEquals(95_00L, chequera.remainingBalanceCents)

        val cash = summary.accountSummaries[1]
        assertEquals("Efectivo Casa", cash.account.name)
        assertEquals(2_00L, cash.totalSpentCents)
        assertEquals(0L, cash.todaySpentCents)
        assertEquals(1, cash.transactionCount)
        assertEquals(false, cash.hasStartingBalance)
    }

    @Test
    fun commitmentScheduleHelperEvaluatesDebtsCardsSubscriptionsAndGoals() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.timeInMillis

        val debtDueToday = CommitmentEntity(
            id = 1,
            title = "Car Loan",
            type = CommitmentType.DEBT.name,
            amountCents = 250_00L,
            targetOrTotalCents = 2000_00L,
            progressCents = 500_00L,
            cycle = RecurrenceCycle.MONTHLY.name,
            dueDayOfMonth = 17
        )
        val debtStatus = CommitmentScheduleHelper.evaluate(debtDueToday, now)
        assertTrue(debtStatus.isDueToday)
        assertTrue(debtStatus.isDueSoon)
        assertFalse(debtStatus.isOverdue)
        assertEquals("Due today", debtStatus.statusLabel)

        // Marking it paid this cycle advances next due to December 17
        val paidDebt = debtDueToday.copy(lastCompletedCycleKey = debtStatus.cycleKey)
        val paidDebtStatus = CommitmentScheduleHelper.evaluate(paidDebt, now)
        assertTrue(paidDebtStatus.isCompletedThisCycle)
        assertFalse(paidDebtStatus.isDueToday)
        assertTrue(paidDebtStatus.daysUntilDue >= 28)

        // Day 31 clamps cleanly to Nov 30
        val card31 = CommitmentEntity(
            id = 2,
            title = "Travel Visa",
            type = CommitmentType.CREDIT_CARD.name,
            amountCents = 420_00L,
            cycle = RecurrenceCycle.MONTHLY.name,
            dueDayOfMonth = 31,
            statementDayOfMonth = 10
        )
        val cardStatus = CommitmentScheduleHelper.evaluate(card31, now)
        assertEquals(13, cardStatus.daysUntilDue)

        // Weekly subscription starting 3 days from now
        val sub = CommitmentEntity(
            id = 3,
            title = "Cloud Storage",
            type = CommitmentType.SUBSCRIPTION.name,
            amountCents = 10_00L,
            cycle = RecurrenceCycle.WEEKLY.name,
            scheduledDateMillis = now + 3L * 86_400_000L
        )
        val subStatus = CommitmentScheduleHelper.evaluate(sub, now)
        assertEquals(3, subStatus.daysUntilDue)
        assertTrue(subStatus.isDueSoon)

        // Goal scheduled in 5 days
        val goal = CommitmentEntity(
            id = 4,
            title = "Holiday Trip",
            type = CommitmentType.GOAL.name,
            amountCents = 150_00L,
            targetOrTotalCents = 600_00L,
            progressCents = 150_00L,
            cycle = RecurrenceCycle.ONE_TIME.name,
            scheduledDateMillis = now + 5L * 86_400_000L
        )
        val goalStatus = CommitmentScheduleHelper.evaluate(goal, now)
        assertEquals(5, goalStatus.daysUntilDue)
        assertTrue(goalStatus.isDueSoon)

        // Income payday
        val payday = CommitmentEntity(
            id = 5,
            title = "Salary",
            type = CommitmentType.INCOME.name,
            amountCents = 3000_00L,
            cycle = RecurrenceCycle.MONTHLY.name,
            dueDayOfMonth = 30
        )

        val evaluated = listOf(debtDueToday, card31, sub, goal, payday).map { entity ->
            CommitmentWithStatus(
                commitment = entity,
                commitmentType = CommitmentType.fromKey(entity.type),
                recurrenceCycle = RecurrenceCycle.fromKey(entity.cycle),
                status = CommitmentScheduleHelper.evaluate(entity, now)
            )
        }
        val summary = CommitmentScheduleHelper.summarize(evaluated)
        // Monthly outflow = Debt (250) + Card (420) + Weekly Sub (10 * 4 = 40) = 710 (Goals & Income excluded from outflows)
        assertEquals(710_00L, summary.monthlyScheduledOutflowCents)
        assertEquals(3000_00L, summary.monthlyExpectedIncomeCents)
        assertEquals(1500_00L, summary.totalDebtRemainingCents)
        assertEquals(150_00L, summary.totalGoalSavedCents)
        assertEquals(3, summary.dueWithin7DaysCount) // debtDueToday (0d), sub (3d), goal (5d)
    }

    @Test
    fun bankMessageParserExtractsChargesAndFiltersOtpOrDeclines() {
        val fixedNow = 1_700_000_000_000L

        // 1. US card purchase notification
        val usCharge = BankMessageParser.parse(
            title = "Chase Alert",
            body = "Your card ending in 4821 was charged $18.75 at Trader Joe's on Nov 17.",
            sourceAppLabel = "Messages",
            nowMillis = fixedNow
        )
        assertNotNull(usCharge)
        assertEquals(18_75L, usCharge!!.amountCents)
        assertEquals("Trader Joe's", usCharge.merchant)
        assertEquals("4821", usCharge.cardLastFour)
        assertEquals(PaymentMethod.CARD, usCharge.paymentMethod)
        assertEquals(ExpenseCategory.GROCERIES, usCharge.suggestedCategory)

        // 2. Spanish card purchase notification (MXN)
        val mxCharge = BankMessageParser.parse(
            title = "BBVA",
            body = "Compra aprobada por MXN 245.00 en UBER EATS con tarjeta terminacion 9012.",
            sourceAppLabel = "BBVA",
            nowMillis = fixedNow
        )
        assertNotNull(mxCharge)
        assertEquals(245_00L, mxCharge!!.amountCents)
        assertEquals("UBER EATS", mxCharge.merchant)
        assertEquals("9012", mxCharge.cardLastFour)
        assertEquals(PaymentMethod.CARD, mxCharge.paymentMethod)
        assertEquals(ExpenseCategory.FOOD_DINING, mxCharge.suggestedCategory)

        // 3. Bank transfer / checking payment with thousands comma
        val transfer = BankMessageParser.parse(
            title = "Bank Alert",
            body = "Wire transfer to City Rent of $1,250.50 from checking account completed.",
            sourceAppLabel = "Bank",
            nowMillis = fixedNow
        )
        assertNotNull(transfer)
        assertEquals(1250_50L, transfer!!.amountCents)
        assertEquals("City Rent", transfer.merchant)
        assertEquals(PaymentMethod.BANK, transfer.paymentMethod)

        // 4. Deduplication fingerprint matches within same 5-min window
        val duplicateCharge = BankMessageParser.parse(
            title = "Messages",
            body = "Purchase of $18.75 at Trader Joe's on card ****4821",
            sourceAppLabel = "Messages",
            nowMillis = fixedNow + 10_000L
        )
        assertNotNull(duplicateCharge)
        assertEquals(usCharge.fingerprint, duplicateCharge!!.fingerprint)

        // 5. OTP / 2FA verification codes MUST be ignored
        assertNull(
            BankMessageParser.parse(
                title = "Bank Security",
                body = "Your verification code is 492018. Do not share this code.",
                sourceAppLabel = "Messages",
                nowMillis = fixedNow
            )
        )

        // 6. Declined charges and balance-only alerts MUST be ignored
        assertNull(
            BankMessageParser.parse(
                title = "Card Alert",
                body = "Your purchase of $55.00 at Amazon was declined due to insufficient funds.",
                sourceAppLabel = "Chase",
                nowMillis = fixedNow
            )
        )
        assertNull(
            BankMessageParser.parse(
                title = "Bank Update",
                body = "Your available balance is $2,410.00.",
                sourceAppLabel = "Bank",
                nowMillis = fixedNow
            )
        )
    }

    @Test
    fun thirtyDayTrendComputesDailySeriesAndSpendingPatterns() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val dayMs = 86_400_000L

        val expenses = listOf(
            ExpenseEntity(
                id = 1,
                description = "Today Coffee",
                amountCents = 6_00L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = "CARD",
                timestampMillis = now
            ),
            ExpenseEntity(
                id = 2,
                description = "Mid-month Groceries",
                amountCents = 120_00L,
                category = ExpenseCategory.GROCERIES.name,
                paymentMethod = "CARD",
                timestampMillis = now - 10L * dayMs
            ),
            ExpenseEntity(
                id = 3,
                description = "Start of window Dinner",
                amountCents = 54_00L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = "CARD",
                timestampMillis = now - 29L * dayMs
            ),
            // Outside 30-day window -> must be excluded
            ExpenseEntity(
                id = 4,
                description = "Older Expense",
                amountCents = 500_00L,
                category = ExpenseCategory.SHOPPING.name,
                paymentMethod = "CARD",
                timestampMillis = now - 35L * dayMs
            )
        )

        val trend = FinancialInsightsEngine.computeThirtyDayTrend(expenses, nowMillis = now)
        assertEquals(30, trend.days.size)
        assertEquals(180_00L, trend.totalCents)
        assertEquals(6_00L, trend.dailyAverageCents)
        assertEquals(27, trend.noSpendDaysCount)
        assertNotNull(trend.peakDay)
        assertEquals(120_00L, trend.peakDay!!.spentCents)
        assertEquals(54_00L, trend.days.first().spentCents)
        assertEquals(6_00L, trend.days.last().spentCents)
    }

    @Test
    fun internalTransfersAdjustAccountBalancesWithoutCountingAsExpenses() {
        val now = System.currentTimeMillis()
        val accounts = listOf(
            AccountEntity(id = 1, name = "Chequera", initialBalanceCents = 1_000_00L),
            AccountEntity(id = 2, name = "Personal", initialBalanceCents = 0L)
        )
        val transfers = listOf(
            AccountTransferEntity(
                id = 1,
                fromAccountName = "chequera",
                toAccountName = "Personal",
                amountCents = 350_00L,
                timestampMillis = now,
                note = "Paycheck move"
            )
        )

        val summary = FinancialInsightsEngine.compute(
            expenses = emptyList(),
            categoryBudgets = emptyList(),
            prefs = BudgetPreferences(),
            nowMillis = now,
            accounts = accounts,
            transfers = transfers
        )

        // Internal transfers must NOT count toward daily, weekly, or monthly expenses
        assertEquals(0L, summary.todaySpentCents)
        assertEquals(0L, summary.weekSpentCents)
        assertEquals(0L, summary.monthSpentCents)

        val chequera = summary.accountSummaries.first { it.account.name == "Chequera" }
        assertEquals(0L, chequera.transferredInCents)
        assertEquals(350_00L, chequera.transferredOutCents)
        assertEquals(650_00L, chequera.remainingBalanceCents)
        assertTrue(chequera.hasBalanceOrTransferActivity)

        val personal = summary.accountSummaries.first { it.account.name == "Personal" }
        assertEquals(350_00L, personal.transferredInCents)
        assertEquals(0L, personal.transferredOutCents)
        assertEquals(350_00L, personal.remainingBalanceCents)
        assertTrue(personal.hasBalanceOrTransferActivity)
    }

    @Test
    fun pointsTrackerFormatsAndEstimatesValueWithoutAffectingCashBalances() {
        val ptsAccount = PointsAccountEntity(
            id = 1,
            name = "Rewards Card",
            pointsBalance = 24_500L,
            unitLabel = "pts",
            centsPerPointTimes100 = 125L // 1.25 cents per point
        )

        assertEquals("24,500", PointsFormatter.formatCount(ptsAccount.pointsBalance))
        assertEquals("24,500 pts", PointsFormatter.formatWithUnit(ptsAccount.pointsBalance, ptsAccount.unitLabel))
        // 24,500 * 1.25 cents = 30,625 cents ($306.25)
        assertEquals(30_625L, ptsAccount.estimatedValueCents)
        assertEquals("1.25", PointsFormatter.centsPerPointToEditText(125L))
        assertEquals(125L, PointsFormatter.parseCentsPerPointTimes100("1.25"))

        // Verify cash engine balances remain completely unaffected
        val summary = FinancialInsightsEngine.compute(
            expenses = emptyList(),
            categoryBudgets = emptyList(),
            prefs = BudgetPreferences(monthlyBudgetCents = 1_500_00L),
            accounts = listOf(AccountEntity(id = 1, name = "Checking", initialBalanceCents = 500_00L))
        )
        assertEquals(0L, summary.todaySpentCents)
        assertEquals(500_00L, summary.accountSummaries.first().remainingBalanceCents)
    }
}
