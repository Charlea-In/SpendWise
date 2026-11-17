package com.example.util

import com.example.data.AccountEntity
import com.example.data.AccountTransferEntity
import com.example.data.BudgetPreferences
import com.example.data.CategoryBudgetEntity
import com.example.data.ExpenseEntity
import com.example.model.DailyBudgetMode
import com.example.model.ExpenseCategory
import java.util.Calendar
import kotlin.math.roundToLong

enum class BudgetStatusLevel {
    ON_TRACK,
    APPROACHING, // >= 80%
    EXCEEDED     // >= 100%
}

data class DailyDayBar(
    val dayStartMillis: Long,
    val label: String,
    val fullDateLabel: String,
    val spentCents: Long
)

data class CategoryProgressItem(
    val category: ExpenseCategory,
    val spentCents: Long,
    val limitCents: Long, // 0 if unset
    val percentOfSpend: Float,
    val budgetRatio: Float, // 0f if unset
    val status: BudgetStatusLevel
)

data class FinancialInsightTip(
    val title: String,
    val message: String,
    val isWarning: Boolean = false
)

data class AccountSummaryItem(
    val account: AccountEntity,
    val totalSpentCents: Long,
    val todaySpentCents: Long,
    val monthSpentCents: Long,
    val transactionCount: Int,
    val transferredInCents: Long = 0L,
    val transferredOutCents: Long = 0L
) {
    val hasStartingBalance: Boolean
        get() = account.initialBalanceCents > 0L

    val hasBalanceOrTransferActivity: Boolean
        get() = account.initialBalanceCents > 0L || transferredInCents > 0L || transferredOutCents > 0L

    val remainingBalanceCents: Long
        get() = account.initialBalanceCents + transferredInCents - transferredOutCents - totalSpentCents
}

data class ThirtyDayTrendSnapshot(
    val days: List<DailyDayBar>,
    val totalCents: Long,
    val dailyAverageCents: Long,
    val peakDay: DailyDayBar?,
    val noSpendDaysCount: Int
)

data class DailySummarySnapshot(
    val todaySpentCents: Long,
    val dailyAllowanceCents: Long,
    val todayRemainingCents: Long,
    val weekSpentCents: Long,
    val weeklyAllowanceCents: Long,
    val weeklyRemainingCents: Long,
    val monthSpentCents: Long,
    val spendableMonthBudgetCents: Long,
    val monthRemainingCents: Long,
    val daysRemainingInMonth: Int,
    val projectedMonthSpendCents: Long,
    val paceStatus: BudgetStatusLevel,
    val last7Days: List<DailyDayBar>,
    val last30DaysTrend: ThirtyDayTrendSnapshot = ThirtyDayTrendSnapshot(
        days = emptyList(),
        totalCents = 0L,
        dailyAverageCents = 0L,
        peakDay = null,
        noSpendDaysCount = 0
    ),
    val categoryProgress: List<CategoryProgressItem>,
    val activeBudgetAlerts: List<CategoryProgressItem>,
    val tips: List<FinancialInsightTip>,
    val underLimitStreakDays: Int,
    val accountSummaries: List<AccountSummaryItem> = emptyList()
)

object FinancialInsightsEngine {

    fun compute(
        expenses: List<ExpenseEntity>,
        categoryBudgets: List<CategoryBudgetEntity>,
        prefs: BudgetPreferences,
        nowMillis: Long = System.currentTimeMillis(),
        accounts: List<AccountEntity> = emptyList(),
        transfers: List<AccountTransferEntity> = emptyList()
    ): DailySummarySnapshot {
        val todayStart = DateUtils.startOfDayMillis(nowMillis)
        val todayEnd = DateUtils.endOfDayMillis(nowMillis)
        val monthStart = DateUtils.startOfMonthMillis(nowMillis)
        val daysInMonth = DateUtils.daysInCurrentMonth(nowMillis)
        val dayOfMonth = DateUtils.currentDayOfMonth(nowMillis).coerceAtLeast(1)
        val daysRemaining = DateUtils.remainingDaysInMonthIncludingToday(nowMillis)

        val monthExpenses = expenses.filter { it.timestampMillis in monthStart..todayEnd }
        val todayExpenses = monthExpenses.filter { it.timestampMillis in todayStart..todayEnd }

        val todaySpentCents = todayExpenses.sumOf { it.amountCents }
        val monthSpentCents = monthExpenses.sumOf { it.amountCents }
        val spentBeforeTodayCents = (monthSpentCents - todaySpentCents).coerceAtLeast(0L)

        val spendableMonthBudget = (prefs.monthlyBudgetCents - prefs.monthlySavingsGoalCents).coerceAtLeast(0L)
        val dailyAllowanceCents = when (prefs.dailyMode) {
            DailyBudgetMode.AUTO_PACED -> {
                val remainingBeforeToday = (spendableMonthBudget - spentBeforeTodayCents).coerceAtLeast(0L)
                remainingBeforeToday / daysRemaining
            }
            DailyBudgetMode.FIXED_DAILY -> prefs.fixedDailyLimitCents.coerceAtLeast(0L)
        }

        val weeklyAllowanceCents = when (prefs.dailyMode) {
            DailyBudgetMode.AUTO_PACED ->
                ((spendableMonthBudget.toDouble() * 7.0) / daysInMonth.toDouble()).roundToLong().coerceAtLeast(0L)
            DailyBudgetMode.FIXED_DAILY ->
                (prefs.fixedDailyLimitCents * 7L).coerceAtLeast(0L)
        }

        val todayRemainingCents = dailyAllowanceCents - todaySpentCents
        val monthRemainingCents = spendableMonthBudget - monthSpentCents

        val dailyRatio = if (dailyAllowanceCents > 0L) {
            todaySpentCents.toFloat() / dailyAllowanceCents.toFloat()
        } else {
            if (todaySpentCents > 0L) 1.5f else 0f
        }
        val paceStatus = when {
            dailyRatio >= 1.0f -> BudgetStatusLevel.EXCEEDED
            dailyRatio >= 0.8f -> BudgetStatusLevel.APPROACHING
            else -> BudgetStatusLevel.ON_TRACK
        }

        // Last 7 days bar data & Last 30 days line trend data
        val dayStarts = DateUtils.lastNDaysStarts(7, nowMillis)
        val byDayKey = expenses.groupBy { DateUtils.dayKey(it.timestampMillis) }
        val last7Days = dayStarts.map { start ->
            val key = DateUtils.dayKey(start)
            val total = byDayKey[key]?.sumOf { it.amountCents } ?: 0L
            DailyDayBar(
                dayStartMillis = start,
                label = DateUtils.formatShortWeekday(start),
                fullDateLabel = DateUtils.formatHeaderDate(start),
                spentCents = total
            )
        }

        val last30DaysTrend = computeThirtyDayTrend(
            expenses = expenses,
            nowMillis = nowMillis,
            preGroupedByDayKey = byDayKey
        )

        val weekSpentCents = last7Days.sumOf { it.spentCents }
        val weeklyRemainingCents = weeklyAllowanceCents - weekSpentCents

        // Streak of days <= dailyAllowance (among last 7 days with activity or up to yesterday/today)
        var streak = 0
        for (bar in last7Days.reversed()) {
            if (dailyAllowanceCents > 0L && bar.spentCents <= dailyAllowanceCents) {
                streak++
            } else {
                break
            }
        }

        // Category breakdown & budget progress for current month
        val budgetMap = categoryBudgets.associateBy { ExpenseCategory.fromKey(it.category) }
        val spentByCategory = monthExpenses.groupBy { ExpenseCategory.fromKey(it.category) }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }

        val categoryProgress = ExpenseCategory.entries.map { cat ->
            val spent = spentByCategory[cat] ?: 0L
            val limit = budgetMap[cat]?.monthlyLimitCents ?: 0L
            val percentOfSpend = if (monthSpentCents > 0L) spent.toFloat() / monthSpentCents.toFloat() else 0f
            val ratio = if (limit > 0L) spent.toFloat() / limit.toFloat() else 0f
            val status = when {
                limit > 0L && ratio >= 1.0f -> BudgetStatusLevel.EXCEEDED
                limit > 0L && ratio >= 0.8f -> BudgetStatusLevel.APPROACHING
                else -> BudgetStatusLevel.ON_TRACK
            }
            CategoryProgressItem(
                category = cat,
                spentCents = spent,
                limitCents = limit,
                percentOfSpend = percentOfSpend,
                budgetRatio = ratio,
                status = status
            )
        }.sortedWith(
            compareByDescending<CategoryProgressItem> { it.limitCents > 0L && it.status != BudgetStatusLevel.ON_TRACK }
                .thenByDescending { it.spentCents }
                .thenByDescending { it.limitCents }
        )

        val activeAlerts = categoryProgress.filter {
            it.limitCents > 0L && (it.status == BudgetStatusLevel.APPROACHING || it.status == BudgetStatusLevel.EXCEEDED)
        }

        val projectedMonthSpend = if (monthSpentCents > 0L) {
            ((monthSpentCents.toDouble() / dayOfMonth.toDouble()) * daysInMonth.toDouble()).roundToLong()
        } else 0L

        val tips = buildTips(
            monthExpenses = monthExpenses,
            todaySpentCents = todaySpentCents,
            dailyAllowanceCents = dailyAllowanceCents,
            projectedMonthSpendCents = projectedMonthSpend,
            spendableMonthBudgetCents = spendableMonthBudget,
            categoryProgress = categoryProgress,
            last7Days = last7Days,
            currency = prefs.currencySymbol
        )

        val accountSummaries = accounts.map { acct ->
            val acctExpenses = expenses.filter {
                it.accountName.trim().equals(acct.name.trim(), ignoreCase = true)
            }
            val acctTransferIn = transfers
                .filter { it.toAccountName.trim().equals(acct.name.trim(), ignoreCase = true) }
                .sumOf { it.amountCents }
            val acctTransferOut = transfers
                .filter { it.fromAccountName.trim().equals(acct.name.trim(), ignoreCase = true) }
                .sumOf { it.amountCents }
            AccountSummaryItem(
                account = acct,
                totalSpentCents = acctExpenses.sumOf { it.amountCents },
                todaySpentCents = acctExpenses
                    .filter { it.timestampMillis in todayStart..todayEnd }
                    .sumOf { it.amountCents },
                monthSpentCents = acctExpenses
                    .filter { it.timestampMillis in monthStart..todayEnd }
                    .sumOf { it.amountCents },
                transactionCount = acctExpenses.size,
                transferredInCents = acctTransferIn,
                transferredOutCents = acctTransferOut
            )
        }

        return DailySummarySnapshot(
            todaySpentCents = todaySpentCents,
            dailyAllowanceCents = dailyAllowanceCents,
            todayRemainingCents = todayRemainingCents,
            weekSpentCents = weekSpentCents,
            weeklyAllowanceCents = weeklyAllowanceCents,
            weeklyRemainingCents = weeklyRemainingCents,
            monthSpentCents = monthSpentCents,
            spendableMonthBudgetCents = spendableMonthBudget,
            monthRemainingCents = monthRemainingCents,
            daysRemainingInMonth = daysRemaining,
            projectedMonthSpendCents = projectedMonthSpend,
            paceStatus = paceStatus,
            last7Days = last7Days,
            last30DaysTrend = last30DaysTrend,
            categoryProgress = categoryProgress,
            activeBudgetAlerts = activeAlerts,
            tips = tips,
            underLimitStreakDays = streak,
            accountSummaries = accountSummaries
        )
    }

    fun computeThirtyDayTrend(
        expenses: List<ExpenseEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        preGroupedByDayKey: Map<String, List<ExpenseEntity>>? = null
    ): ThirtyDayTrendSnapshot {
        val grouped = preGroupedByDayKey ?: expenses.groupBy { DateUtils.dayKey(it.timestampMillis) }
        val dayStarts30 = DateUtils.lastNDaysStarts(30, nowMillis)
        val days = dayStarts30.map { start ->
            val key = DateUtils.dayKey(start)
            val total = grouped[key]?.sumOf { it.amountCents } ?: 0L
            DailyDayBar(
                dayStartMillis = start,
                label = DateUtils.formatShortMonthDay(start),
                fullDateLabel = DateUtils.formatHeaderDate(start),
                spentCents = total
            )
        }
        val totalCents = days.sumOf { it.spentCents }
        val avgCents = if (days.isNotEmpty()) (totalCents.toDouble() / days.size.toDouble()).roundToLong() else 0L
        val peakDay = days.maxByOrNull { it.spentCents }?.takeIf { it.spentCents > 0L }
        val noSpendCount = days.count { it.spentCents == 0L }
        return ThirtyDayTrendSnapshot(
            days = days,
            totalCents = totalCents,
            dailyAverageCents = avgCents,
            peakDay = peakDay,
            noSpendDaysCount = noSpendCount
        )
    }

    private fun buildTips(
        monthExpenses: List<ExpenseEntity>,
        todaySpentCents: Long,
        dailyAllowanceCents: Long,
        projectedMonthSpendCents: Long,
        spendableMonthBudgetCents: Long,
        categoryProgress: List<CategoryProgressItem>,
        last7Days: List<DailyDayBar>,
        currency: String
    ): List<FinancialInsightTip> {
        if (monthExpenses.isEmpty()) {
            return listOf(
                FinancialInsightTip(
                    title = "Start your daily rhythm",
                    message = "Log your first expense today — SpendWise will auto-categorize it and calculate your daily safe-to-spend pace."
                )
            )
        }

        val tips = mutableListOf<FinancialInsightTip>()

        // 1. Over-budget / approaching category warning
        val topAlert = categoryProgress.firstOrNull { it.limitCents > 0L && it.status != BudgetStatusLevel.ON_TRACK }
        if (topAlert != null) {
            val pct = (topAlert.budgetRatio * 100f).toInt()
            if (topAlert.status == BudgetStatusLevel.EXCEEDED) {
                tips.add(
                    FinancialInsightTip(
                        title = "${topAlert.category.displayName} over limit ($pct%)",
                        message = "You've spent ${MoneyFormatter.format(topAlert.spentCents, currency)} against a ${MoneyFormatter.format(topAlert.limitCents, currency)} limit. Pause discretionary items in this category this week.",
                        isWarning = true
                    )
                )
            } else {
                val left = (topAlert.limitCents - topAlert.spentCents).coerceAtLeast(0L)
                tips.add(
                    FinancialInsightTip(
                        title = "${topAlert.category.displayName} at $pct%",
                        message = "Only ${MoneyFormatter.format(left, currency)} remains in your ${topAlert.category.displayName} budget for the month.",
                        isWarning = true
                    )
                )
            }
        }

        // 2. Projection vs monthly budget
        if (spendableMonthBudgetCents > 0L) {
            if (projectedMonthSpendCents > spendableMonthBudgetCents) {
                val over = projectedMonthSpendCents - spendableMonthBudgetCents
                tips.add(
                    FinancialInsightTip(
                        title = "Pace projection alert",
                        message = "At this month's daily pace, spending could reach ${MoneyFormatter.format(projectedMonthSpendCents, currency)} — about ${MoneyFormatter.format(over, currency)} over target.",
                        isWarning = true
                    )
                )
            } else {
                val cushion = spendableMonthBudgetCents - projectedMonthSpendCents
                tips.add(
                    FinancialInsightTip(
                        title = "Projected monthly surplus",
                        message = "At your current pace, you're projected to finish the month ${MoneyFormatter.format(cushion, currency)} under your spendable budget."
                    )
                )
            }
        }

        // 3. Top spending category insight
        val topCategory = categoryProgress.maxByOrNull { it.spentCents }
        if (topCategory != null && topCategory.spentCents > 0L) {
            val sharePct = (topCategory.percentOfSpend * 100f).toInt()
            tips.add(
                FinancialInsightTip(
                    title = "Top category: ${topCategory.category.displayName}",
                    message = "${topCategory.category.displayName} represents $sharePct% of your monthly expenses (${MoneyFormatter.format(topCategory.spentCents, currency)})."
                )
            )
        }

        // 4. Weekend vs weekday pattern if enough data
        val cal = Calendar.getInstance()
        val weekendAvg = last7Days.filter {
            cal.timeInMillis = it.dayStartMillis
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        }.map { it.spentCents }.average()
        val weekdayAvg = last7Days.filter {
            cal.timeInMillis = it.dayStartMillis
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
        }.map { it.spentCents }.average()

        if (!weekendAvg.isNaN() && !weekdayAvg.isNaN() && weekdayAvg > 0 && weekendAvg > weekdayAvg * 1.35) {
            tips.add(
                FinancialInsightTip(
                    title = "Higher weekend spending",
                    message = "Your weekend daily average is ${(weekendAvg / weekdayAvg).let { String.format(java.util.Locale.US, "%.1f", it) }}x higher than weekdays. Leaving a small weekday buffer helps balance the week."
                )
            )
        } else if (dailyAllowanceCents > 0L && todaySpentCents <= dailyAllowanceCents) {
            tips.add(
                FinancialInsightTip(
                    title = "On pace today",
                    message = "You still have ${MoneyFormatter.format(dailyAllowanceCents - todaySpentCents, currency)} safe to spend today."
                )
            )
        }

        return tips.take(4)
    }
}
