package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.CommitmentEntity
import com.example.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CommitmentType(
    val pluralTitle: String,
    val singularTitle: String,
    val actionLabel: String,
    val dayFieldLabel: String,
    val icon: ImageVector,
    val accentColor: Color,
    val defaultCategory: ExpenseCategory,
    val logsAsExpenseOnPay: Boolean = true
) {
    DEBT(
        pluralTitle = "Debts",
        singularTitle = "Debt",
        actionLabel = "Pay Debt",
        dayFieldLabel = "Payment day",
        icon = Icons.Rounded.RequestQuote,
        accentColor = Color(0xFFDC2626),
        defaultCategory = ExpenseCategory.OTHER
    ),
    CREDIT_CARD(
        pluralTitle = "Credit Cards",
        singularTitle = "Credit Card",
        actionLabel = "Pay Card",
        dayFieldLabel = "Payment day",
        icon = Icons.Rounded.CreditCard,
        accentColor = Color(0xFF7C3AED),
        defaultCategory = ExpenseCategory.OTHER
    ),
    SUBSCRIPTION(
        pluralTitle = "Subscriptions",
        singularTitle = "Subscription",
        actionLabel = "Log Charge",
        dayFieldLabel = "Charged on day",
        icon = Icons.Rounded.Autorenew,
        accentColor = Color(0xFF0284C7),
        defaultCategory = ExpenseCategory.ENTERTAINMENT
    ),
    GOAL(
        pluralTitle = "Goals",
        singularTitle = "Goal",
        actionLabel = "Contribute",
        dayFieldLabel = "Charge / contribution day",
        icon = Icons.Rounded.Flag,
        accentColor = Color(0xFF059669),
        defaultCategory = ExpenseCategory.OTHER,
        logsAsExpenseOnPay = false
    ),
    BILL(
        pluralTitle = "Bills & Rent",
        singularTitle = "Bill",
        actionLabel = "Pay Bill",
        dayFieldLabel = "Due day",
        icon = Icons.Rounded.Receipt,
        accentColor = Color(0xFFD97706),
        defaultCategory = ExpenseCategory.UTILITIES
    ),
    INSTALLMENT(
        pluralTitle = "Installments",
        singularTitle = "Installment",
        actionLabel = "Pay Installment",
        dayFieldLabel = "Payment day",
        icon = Icons.Rounded.Payments,
        accentColor = Color(0xFFEA580C),
        defaultCategory = ExpenseCategory.SHOPPING
    ),
    INCOME(
        pluralTitle = "Paydays",
        singularTitle = "Payday / Income",
        actionLabel = "Mark Received",
        dayFieldLabel = "Payday",
        icon = Icons.Rounded.Savings,
        accentColor = Color(0xFF0F766E),
        defaultCategory = ExpenseCategory.OTHER,
        logsAsExpenseOnPay = false
    );

    companion object {
        fun fromKey(raw: String): CommitmentType {
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEBT
        }
    }
}

enum class RecurrenceCycle(val label: String) {
    MONTHLY("Monthly"),
    BIWEEKLY("Every 2 Weeks"),
    WEEKLY("Weekly"),
    YEARLY("Yearly"),
    ONE_TIME("Specific Date");

    companion object {
        fun fromKey(raw: String): RecurrenceCycle {
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: MONTHLY
        }
    }
}

data class CommitmentScheduleStatus(
    val nextDueMillis: Long,
    val daysUntilDue: Int,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
    val isDueSoon: Boolean, // 0..7 days
    val isCompletedThisCycle: Boolean,
    val isFullyPaidOff: Boolean,
    val cycleKey: String,
    val statusLabel: String,
    val formattedDueDate: String
)

data class CommitmentWithStatus(
    val commitment: CommitmentEntity,
    val commitmentType: CommitmentType,
    val recurrenceCycle: RecurrenceCycle,
    val status: CommitmentScheduleStatus
)

data class MonthlyCommitmentsSummary(
    val monthlyScheduledOutflowCents: Long,
    val monthlyExpectedIncomeCents: Long,
    val totalDebtRemainingCents: Long,
    val totalGoalSavedCents: Long,
    val totalGoalTargetCents: Long,
    val dueWithin7DaysCount: Int,
    val overdueCount: Int
)

object CommitmentScheduleHelper {

    private val shortDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    fun evaluate(
        commitment: CommitmentEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): CommitmentScheduleStatus {
        val type = CommitmentType.fromKey(commitment.type)
        val cycle = RecurrenceCycle.fromKey(commitment.cycle)
        val todayStart = DateUtils.startOfDayMillis(nowMillis)

        val isInstallmentFinished = type == CommitmentType.INSTALLMENT &&
            commitment.totalInstallments > 0 &&
            commitment.completedInstallments >= commitment.totalInstallments
        val isDebtPaidOff = type == CommitmentType.DEBT &&
            commitment.targetOrTotalCents > 0L &&
            commitment.progressCents >= commitment.targetOrTotalCents
        val isGoalReached = type == CommitmentType.GOAL &&
            commitment.targetOrTotalCents > 0L &&
            commitment.progressCents >= commitment.targetOrTotalCents
        val isFullyPaidOff = isInstallmentFinished || isDebtPaidOff || isGoalReached

        val currentCycleKey = computeCycleKey(cycle, commitment, nowMillis)
        val isCompletedThisCycle = !isFullyPaidOff &&
            commitment.lastCompletedCycleKey.isNotBlank() &&
            commitment.lastCompletedCycleKey == currentCycleKey

        val dueMillis = computeNextDueDateMillis(
            cycle = cycle,
            commitment = commitment,
            todayStartMillis = todayStart,
            advancePastCurrentCycle = isCompletedThisCycle
        )

        val diffDays = ((DateUtils.startOfDayMillis(dueMillis) - todayStart) / 86_400_000L).toInt()
        val isOverdue = commitment.isActive && !isFullyPaidOff && !isCompletedThisCycle && diffDays < 0
        val isDueToday = commitment.isActive && !isFullyPaidOff && diffDays == 0
        val isDueSoon = commitment.isActive && !isFullyPaidOff && diffDays in 0..7
        val formattedDate = shortDateFormat.format(Date(dueMillis))

        val label = when {
            !commitment.isActive -> "Paused"
            isDebtPaidOff -> "Paid off"
            isInstallmentFinished -> "Completed"
            isGoalReached -> "Goal reached"
            isCompletedThisCycle && cycle == RecurrenceCycle.ONE_TIME -> "Completed ($formattedDate)"
            isCompletedThisCycle -> "Done • Next $formattedDate"
            isOverdue -> "Overdue ${-diffDays}d ($formattedDate)"
            isDueToday -> "Due today"
            diffDays == 1 -> "Tomorrow ($formattedDate)"
            diffDays <= 7 -> "In ${diffDays}d • $formattedDate"
            else -> "$formattedDate (${diffDays}d)"
        }

        return CommitmentScheduleStatus(
            nextDueMillis = dueMillis,
            daysUntilDue = diffDays,
            isOverdue = isOverdue,
            isDueToday = isDueToday,
            isDueSoon = isDueSoon,
            isCompletedThisCycle = isCompletedThisCycle,
            isFullyPaidOff = isFullyPaidOff,
            cycleKey = currentCycleKey,
            statusLabel = label,
            formattedDueDate = formattedDate
        )
    }

    fun summarize(
        items: List<CommitmentWithStatus>
    ): MonthlyCommitmentsSummary {
        val active = items.filter { it.commitment.isActive && !it.status.isFullyPaidOff }

        val monthlyOutflow = active
            .filter { it.commitmentType != CommitmentType.INCOME && it.commitmentType != CommitmentType.GOAL }
            .sumOf { normalizedMonthlyCents(it.commitment.amountCents, it.recurrenceCycle) }

        val monthlyIncome = active
            .filter { it.commitmentType == CommitmentType.INCOME }
            .sumOf { normalizedMonthlyCents(it.commitment.amountCents, it.recurrenceCycle) }

        val debtRemaining = items
            .filter { it.commitment.isActive && it.commitmentType == CommitmentType.DEBT }
            .sumOf { item ->
                val total = item.commitment.targetOrTotalCents
                if (total > 0L) (total - item.commitment.progressCents).coerceAtLeast(0L)
                else item.commitment.amountCents
            }

        val goalItems = items.filter { it.commitment.isActive && it.commitmentType == CommitmentType.GOAL }
        val goalSaved = goalItems.sumOf { it.commitment.progressCents }
        val goalTarget = goalItems.sumOf { it.commitment.targetOrTotalCents }

        val dueSoon = active.count { it.status.isDueSoon }
        val overdue = active.count { it.status.isOverdue }

        return MonthlyCommitmentsSummary(
            monthlyScheduledOutflowCents = monthlyOutflow,
            monthlyExpectedIncomeCents = monthlyIncome,
            totalDebtRemainingCents = debtRemaining,
            totalGoalSavedCents = goalSaved,
            totalGoalTargetCents = goalTarget,
            dueWithin7DaysCount = dueSoon,
            overdueCount = overdue
        )
    }

    private fun normalizedMonthlyCents(amountCents: Long, cycle: RecurrenceCycle): Long {
        return when (cycle) {
            RecurrenceCycle.MONTHLY -> amountCents
            RecurrenceCycle.WEEKLY -> (amountCents * 4L)
            RecurrenceCycle.BIWEEKLY -> (amountCents * 2L)
            RecurrenceCycle.YEARLY -> amountCents / 12L
            RecurrenceCycle.ONE_TIME -> amountCents
        }
    }

    private fun computeCycleKey(
        cycle: RecurrenceCycle,
        commitment: CommitmentEntity,
        nowMillis: Long
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (cycle) {
            RecurrenceCycle.MONTHLY -> monthKeyFormat.format(Date(nowMillis))
            RecurrenceCycle.WEEKLY -> "${cal.get(Calendar.YEAR)}-W${cal.get(Calendar.WEEK_OF_YEAR)}"
            RecurrenceCycle.BIWEEKLY -> {
                val anchor = if (commitment.scheduledDateMillis > 0L) commitment.scheduledDateMillis else nowMillis
                val periodIndex = ((DateUtils.startOfDayMillis(nowMillis) - DateUtils.startOfDayMillis(anchor)) / (14L * 86_400_000L))
                "BW-$periodIndex"
            }
            RecurrenceCycle.YEARLY -> "${cal.get(Calendar.YEAR)}"
            RecurrenceCycle.ONE_TIME -> "ONCE"
        }
    }

    private fun computeNextDueDateMillis(
        cycle: RecurrenceCycle,
        commitment: CommitmentEntity,
        todayStartMillis: Long,
        advancePastCurrentCycle: Boolean
    ): Long {
        return when (cycle) {
            RecurrenceCycle.MONTHLY -> {
                val targetDay = commitment.dueDayOfMonth.coerceIn(1, 31)
                val cal = Calendar.getInstance().apply { timeInMillis = todayStartMillis }
                if (advancePastCurrentCycle) {
                    cal.add(Calendar.MONTH, 1)
                }
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDay))
                DateUtils.startOfDayMillis(cal.timeInMillis)
            }
            RecurrenceCycle.WEEKLY -> {
                stepAnchoredInterval(
                    anchorMillis = commitment.scheduledDateMillis,
                    todayStartMillis = todayStartMillis,
                    intervalDays = 7,
                    advanceNext = advancePastCurrentCycle
                )
            }
            RecurrenceCycle.BIWEEKLY -> {
                stepAnchoredInterval(
                    anchorMillis = commitment.scheduledDateMillis,
                    todayStartMillis = todayStartMillis,
                    intervalDays = 14,
                    advanceNext = advancePastCurrentCycle
                )
            }
            RecurrenceCycle.YEARLY -> {
                val base = if (commitment.scheduledDateMillis > 0L) {
                    DateUtils.startOfDayMillis(commitment.scheduledDateMillis)
                } else {
                    todayStartMillis
                }
                val anchorCal = Calendar.getInstance().apply { timeInMillis = base }
                val todayCal = Calendar.getInstance().apply { timeInMillis = todayStartMillis }
                val candidate = Calendar.getInstance().apply {
                    timeInMillis = todayStartMillis
                    set(Calendar.MONTH, anchorCal.get(Calendar.MONTH))
                    val maxD = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, anchorCal.get(Calendar.DAY_OF_MONTH).coerceAtMost(maxD))
                }
                if (advancePastCurrentCycle || candidate.get(Calendar.DAY_OF_YEAR) < todayCal.get(Calendar.DAY_OF_YEAR) - 7) {
                    candidate.add(Calendar.YEAR, 1)
                }
                DateUtils.startOfDayMillis(candidate.timeInMillis)
            }
            RecurrenceCycle.ONE_TIME -> {
                if (commitment.scheduledDateMillis > 0L) {
                    DateUtils.startOfDayMillis(commitment.scheduledDateMillis)
                } else {
                    todayStartMillis
                }
            }
        }
    }

    private fun stepAnchoredInterval(
        anchorMillis: Long,
        todayStartMillis: Long,
        intervalDays: Int,
        advanceNext: Boolean
    ): Long {
        val base = if (anchorMillis > 0L) DateUtils.startOfDayMillis(anchorMillis) else todayStartMillis
        val cal = Calendar.getInstance().apply { timeInMillis = base }
        while (cal.timeInMillis < todayStartMillis) {
            cal.add(Calendar.DAY_OF_YEAR, intervalDays)
        }
        if (advanceNext && cal.timeInMillis <= todayStartMillis + (intervalDays - 1) * 86_400_000L) {
            cal.add(Calendar.DAY_OF_YEAR, intervalDays)
        }
        return DateUtils.startOfDayMillis(cal.timeInMillis)
    }
}
