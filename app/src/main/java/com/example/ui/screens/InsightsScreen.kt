package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmountMediumStyle
import com.example.ui.theme.AmountRowStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.BudgetStatusLevel
import com.example.util.DailyDayBar
import com.example.util.FinancialInsightsEngine
import com.example.util.MoneyFormatter
import com.example.util.ThirtyDayTrendSnapshot
import com.example.viewmodel.SpendWiseUiState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    uiState: SpendWiseUiState,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalSpendWiseColors.current
    val currency = uiState.preferences.currencySymbol
    val summary = uiState.summary
    var selectedBarIndex by remember { mutableIntStateOf(6) }
    var selectedLineDayIndex by remember { mutableIntStateOf(29) }
    var selectedLineAccount by remember { mutableStateOf<String?>(null) }

    val validLineAccount = selectedLineAccount?.takeIf { sel ->
        uiState.accounts.any { it.name.equals(sel, ignoreCase = true) }
    }
    val lineTrendSnapshot = remember(uiState.expenses, summary.last30DaysTrend, validLineAccount) {
        if (validLineAccount == null) {
            summary.last30DaysTrend
        } else {
            val filteredExpenses = uiState.expenses.filter {
                it.accountName.trim().equals(validLineAccount.trim(), ignoreCase = true)
            }
            FinancialInsightsEngine.computeThirtyDayTrend(filteredExpenses)
        }
    }

    val activeCategories = remember(summary.categoryProgress) {
        summary.categoryProgress.filter { it.spentCents > 0L || it.limitCents > 0L }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("insights_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Spending Insights",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 30-Day Daily Expenses Line Graph (Past Month Pattern Visualization)
        item {
            ThirtyDaySpendingLineCard(
                trend = lineTrendSnapshot,
                currencySymbol = currency,
                dailyAllowanceCents = if (validLineAccount == null) summary.dailyAllowanceCents else 0L,
                accounts = uiState.accounts.map { it.name },
                selectedAccount = validLineAccount,
                onSelectAccount = { acct ->
                    selectedLineAccount = if (selectedLineAccount.equals(acct, ignoreCase = true)) null else acct
                },
                selectedIndex = selectedLineDayIndex,
                onDaySelected = { selectedLineDayIndex = it }
            )
        }

        // 7-Day Daily Spending Bar Chart
        item {
            val bars = summary.last7Days
            val safeIndex = selectedBarIndex.coerceIn(0, (bars.size - 1).coerceAtLeast(0))
            val selectedDay = bars.getOrNull(safeIndex)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weekly_chart_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Last 7 Days",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (selectedDay != null) {
                                Text(
                                    text = "${selectedDay.fullDateLabel} • ${MoneyFormatter.format(selectedDay.spentCents, currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (summary.dailyAllowanceCents > 0L) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Target ${MoneyFormatter.format(summary.dailyAllowanceCents, currency, compactWhole = true)}/d",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    SevenDaySpendingCanvasChart(
                        bars = bars,
                        dailyAllowanceCents = summary.dailyAllowanceCents,
                        selectedIndex = safeIndex,
                        onBarSelected = { selectedBarIndex = it }
                    )
                }
            }
        }

        // Projection & Streak Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Month Projection",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = MoneyFormatter.format(summary.projectedMonthSpendCents, currency, compactWhole = true),
                            style = AmountMediumStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "vs ${MoneyFormatter.format(summary.spendableMonthBudgetCents, currency, compactWhole = true)} limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = semanticColors.warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Under-Limit Streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${summary.underLimitStreakDays} days",
                            style = AmountMediumStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "in the last 7 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Category Breakdown
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Monthly Category Mix",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (activeCategories.isEmpty()) {
                        Text(
                            text = "Log expenses this month to see how your spending distributes across categories.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        activeCategories.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(item.category.accentColor)
                                        )
                                        Text(
                                            text = item.category.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${(item.percentOfSpend * 100f).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = MoneyFormatter.format(item.spentCents, currency),
                                            style = AmountRowStyle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { item.percentOfSpend.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = when (item.status) {
                                        BudgetStatusLevel.EXCEEDED -> semanticColors.danger
                                        BudgetStatusLevel.APPROACHING -> semanticColors.warning
                                        BudgetStatusLevel.ON_TRACK -> item.category.accentColor
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

        // Separate Accounts Breakdown
        if (summary.accountSummaries.isNotEmpty()) {
            item {
                val maxAccountSpend = (summary.accountSummaries.maxOfOrNull { it.totalSpentCents } ?: 0L)
                    .coerceAtLeast(1L)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("insights_accounts_card"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Separate Accounts Breakdown",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        summary.accountSummaries.forEach { item ->
                            val ratio = (item.totalSpentCents.toFloat() / maxAccountSpend.toFloat()).coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.account.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = buildString {
                                                append("${item.transactionCount} tx • This month ${MoneyFormatter.format(item.monthSpentCents, currency)}")
                                                if (item.transferredInCents > 0L) {
                                                    append(" • In +${MoneyFormatter.format(item.transferredInCents, currency)}")
                                                }
                                                if (item.transferredOutCents > 0L) {
                                                    append(" • Out -${MoneyFormatter.format(item.transferredOutCents, currency)}")
                                                }
                                                if (item.hasBalanceOrTransferActivity) {
                                                    val remLabel = if (item.remainingBalanceCents < 0L) {
                                                        "Over ${MoneyFormatter.format(abs(item.remainingBalanceCents), currency)}"
                                                    } else {
                                                        "Balance ${MoneyFormatter.format(item.remainingBalanceCents, currency)}"
                                                    }
                                                    append(" • $remLabel")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = MoneyFormatter.format(item.totalSpentCents, currency),
                                        style = AmountRowStyle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (item.hasBalanceOrTransferActivity && item.remainingBalanceCents < 0L) {
                                        semanticColors.danger
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

        // Daily Financial Helper Tips
        item {
            Text(
                text = "Daily Financial Tips",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(summary.tips) { tip ->
            val bg = if (tip.isWarning) semanticColors.warningContainer else MaterialTheme.colorScheme.surface
            val tint = if (tip.isWarning) semanticColors.warning else MaterialTheme.colorScheme.primary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = bg,
                border = BorderStroke(1.dp, if (tip.isWarning) tint.copy(alpha = 0.35f) else semanticColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (tip.isWarning) Icons.Rounded.WarningAmber else Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = tip.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tip.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SevenDaySpendingCanvasChart(
    bars: List<DailyDayBar>,
    dailyAllowanceCents: Long,
    selectedIndex: Int,
    onBarSelected: (Int) -> Unit
) {
    val semanticColors = LocalSpendWiseColors.current
    val onTrackColor = MaterialTheme.colorScheme.primary
    val overColor = semanticColors.danger
    val targetLineColor = semanticColors.warning
    val trackBg = MaterialTheme.colorScheme.surfaceVariant

    val maxCents = remember(bars, dailyAllowanceCents) {
        val peakBar = bars.maxOfOrNull { it.spentCents } ?: 0L
        max(max(peakBar, dailyAllowanceCents), 1000L)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
        ) {
            if (bars.isEmpty()) return@Canvas
            val slotWidth = size.width / bars.size.toFloat()
            val barWidth = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())
            val chartBottom = size.height - 6.dp.toPx()
            val usableHeight = chartBottom - 8.dp.toPx()

            // Target allowance dashed line
            if (dailyAllowanceCents > 0L) {
                val ratio = (dailyAllowanceCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
                val lineY = chartBottom - (usableHeight * ratio)
                drawLine(
                    color = targetLineColor,
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
            }

            bars.forEachIndexed { idx, bar ->
                val centerX = slotWidth * idx + slotWidth / 2f
                val left = centerX - barWidth / 2f

                // Subtle background slot bar
                drawRoundRect(
                    color = trackBg.copy(alpha = 0.45f),
                    topLeft = Offset(left, 8.dp.toPx()),
                    size = Size(barWidth, usableHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                if (bar.spentCents > 0L) {
                    val ratio = (bar.spentCents.toFloat() / maxCents.toFloat()).coerceIn(0.05f, 1f)
                    val barHeight = usableHeight * ratio
                    val topY = chartBottom - barHeight
                    val isOver = dailyAllowanceCents > 0L && bar.spentCents > dailyAllowanceCents
                    val baseColor = if (isOver) overColor else onTrackColor
                    val alpha = if (idx == selectedIndex) 1f else 0.72f
                    drawRoundRect(
                        color = baseColor.copy(alpha = alpha),
                        topLeft = Offset(left, topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }
        }

        // Day labels row (tappable for inspection)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            bars.forEachIndexed { idx, bar ->
                val selected = idx == selectedIndex
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBarSelected(idx) }
                        .testTag("chart_day_$idx"),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThirtyDaySpendingLineCard(
    trend: ThirtyDayTrendSnapshot,
    currencySymbol: String,
    dailyAllowanceCents: Long,
    accounts: List<String>,
    selectedAccount: String?,
    onSelectAccount: (String?) -> Unit,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    val semanticColors = LocalSpendWiseColors.current
    val days = trend.days
    val safeIndex = selectedIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val selectedDay = days.getOrNull(safeIndex)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_line_chart_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semanticColors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = "30-day expense line graph",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Past 30 Days Trend",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Total ${MoneyFormatter.format(trend.totalCents, currencySymbol)} • Drag or tap curve to inspect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedAccount == null,
                        onClick = { onSelectAccount(null) },
                        label = { Text("All Accounts") },
                        modifier = Modifier.testTag("line_chart_account_all")
                    )
                    accounts.forEach { acctName ->
                        FilterChip(
                            selected = selectedAccount.equals(acctName, ignoreCase = true),
                            onClick = { onSelectAccount(acctName) },
                            label = { Text(acctName) },
                            modifier = Modifier.testTag("line_chart_account_${acctName.lowercase()}")
                        )
                    }
                }
            }

            // Active Scrubbed Day Tooltip Readout
            if (selectedDay != null) {
                val isOverTarget = dailyAllowanceCents > 0L && selectedDay.spentCents > dailyAllowanceCents
                val isAboveAvg = trend.dailyAverageCents > 0L && selectedDay.spentCents > trend.dailyAverageCents
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_line_tooltip"),
                    shape = MaterialTheme.shapes.small,
                    color = if (isOverTarget) {
                        semanticColors.dangerContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDay.fullDateLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    selectedDay.spentCents == 0L -> "No-spend day"
                                    isOverTarget -> "Over daily allowance (${MoneyFormatter.format(dailyAllowanceCents, currencySymbol, compactWhole = true)})"
                                    isAboveAvg -> "Above 30d avg (${MoneyFormatter.format(trend.dailyAverageCents, currencySymbol, compactWhole = true)}/d)"
                                    else -> "Within typical daily pace"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOverTarget) semanticColors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = MoneyFormatter.format(selectedDay.spentCents, currencySymbol),
                            style = AmountMediumStyle,
                            color = if (isOverTarget) semanticColors.danger else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            MonthlyLineExpenseCanvasChart(
                days = days,
                dailyAverageCents = trend.dailyAverageCents,
                dailyAllowanceCents = dailyAllowanceCents,
                currencySymbol = currencySymbol,
                selectedIndex = safeIndex,
                onDaySelected = onDaySelected
            )

            // Legend row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = "Daily Spend",
                    dashed = false
                )
                ChartLegendItem(
                    color = semanticColors.warning,
                    label = "30d Avg",
                    dashed = true
                )
                if (dailyAllowanceCents > 0L) {
                    ChartLegendItem(
                        color = semanticColors.danger,
                        label = "Daily Target",
                        dashed = true
                    )
                }
            }

            // Pattern Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PatternStatPill(
                    label = "Daily Avg",
                    value = MoneyFormatter.format(trend.dailyAverageCents, currencySymbol),
                    modifier = Modifier.weight(1f)
                )
                PatternStatPill(
                    label = trend.peakDay?.let { "Peak (${it.label})" } ?: "Peak Day",
                    value = trend.peakDay?.let { MoneyFormatter.format(it.spentCents, currencySymbol, compactWhole = true) } ?: "${currencySymbol}0",
                    modifier = Modifier.weight(1f)
                )
                PatternStatPill(
                    label = "No-Spend",
                    value = "${trend.noSpendDaysCount}/30d",
                    modifier = Modifier.weight(1f)
                )
            }

            if (trend.totalCents == 0L) {
                Text(
                    text = "Log expenses or switch on Demo Mode in Budget & Sync to visualize your 30-day spending pattern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthlyLineExpenseCanvasChart(
    days: List<DailyDayBar>,
    dailyAverageCents: Long,
    dailyAllowanceCents: Long,
    currencySymbol: String,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    val semanticColors = LocalSpendWiseColors.current
    val lineColor = MaterialTheme.colorScheme.primary
    val avgColor = semanticColors.warning
    val targetColor = semanticColors.danger
    val gridColor = semanticColors.cardBorder
    val surfaceColor = MaterialTheme.colorScheme.surface

    val maxCents = remember(days, dailyAverageCents, dailyAllowanceCents) {
        val peak = days.maxOfOrNull { it.spentCents } ?: 0L
        max(max(peak, max(dailyAverageCents, dailyAllowanceCents)), 1000L)
    }

    val yTopLabel = MoneyFormatter.format(maxCents, currencySymbol, compactWhole = true)
    val yMidLabel = MoneyFormatter.format(maxCents / 2L, currencySymbol, compactWhole = true)
    val yZeroLabel = "${currencySymbol}0"

    val tickIndices = remember(days.size) {
        if (days.size >= 30) listOf(0, 7, 14, 22, 29)
        else if (days.isNotEmpty()) listOf(0, days.lastIndex)
        else emptyList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-Axis Labels
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .height(176.dp)
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = yTopLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = yMidLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = yZeroLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(176.dp)
                    .testTag("monthly_line_canvas")
                    .pointerInput(days.size) {
                        detectTapGestures { offset ->
                            if (days.size > 1 && size.width > 0) {
                                val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val idx = (ratio * (days.size - 1)).roundToInt().coerceIn(0, days.lastIndex)
                                onDaySelected(idx)
                            }
                        }
                    }
                    .pointerInput(days.size) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (days.size > 1 && size.width > 0) {
                                val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val idx = (ratio * (days.size - 1)).roundToInt().coerceIn(0, days.lastIndex)
                                onDaySelected(idx)
                            }
                        }
                    }
            ) {
                if (days.isEmpty()) return@Canvas

                val topPad = 12.dp.toPx()
                val bottomPad = 12.dp.toPx()
                val horizontalPad = 6.dp.toPx()
                val plotWidth = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                val chartBottom = size.height - bottomPad
                val usableHeight = (chartBottom - topPad).coerceAtLeast(1f)

                // Horizontal Cartesian grid lines (Top, Mid, Baseline)
                val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                listOf(0f, 0.5f, 1f).forEach { frac ->
                    val y = topPad + usableHeight * frac
                    drawLine(
                        color = gridColor.copy(alpha = if (frac == 1f) 0.9f else 0.55f),
                        start = Offset(horizontalPad, y),
                        end = Offset(size.width - horizontalPad, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (frac < 1f) gridDash else null
                    )
                }

                // Daily Target Reference Line
                if (dailyAllowanceCents > 0L) {
                    val ratio = (dailyAllowanceCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
                    val targetY = chartBottom - usableHeight * ratio
                    drawLine(
                        color = targetColor.copy(alpha = 0.78f),
                        start = Offset(horizontalPad, targetY),
                        end = Offset(size.width - horizontalPad, targetY),
                        strokeWidth = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                }

                // 30-Day Average Reference Line
                if (dailyAverageCents > 0L) {
                    val ratio = (dailyAverageCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
                    val avgY = chartBottom - usableHeight * ratio
                    drawLine(
                        color = avgColor.copy(alpha = 0.85f),
                        start = Offset(horizontalPad, avgY),
                        end = Offset(size.width - horizontalPad, avgY),
                        strokeWidth = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                }

                // Map points across the 30-day window
                val stepX = if (days.size > 1) plotWidth / (days.size - 1).toFloat() else 0f
                val points = days.mapIndexed { idx, day ->
                    val x = horizontalPad + idx * stepX
                    val yRatio = (day.spentCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
                    val y = chartBottom - (usableHeight * yRatio)
                    Offset(x, y)
                }

                if (points.size >= 2) {
                    // Build smooth monotonic-safe cubic path
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.lastIndex) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX = (p0.x + p1.x) / 2f
                            cubicTo(
                                controlX, p0.y,
                                controlX, p1.y,
                                p1.x, p1.y
                            )
                        }
                    }

                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, chartBottom)
                        lineTo(points.first().x, chartBottom)
                        close()
                    }

                    // Gradient Area Fill
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.28f),
                                lineColor.copy(alpha = 0.02f)
                            ),
                            startY = topPad,
                            endY = chartBottom
                        )
                    )

                    // Crisp Line Curve
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Subtle dots for spending days
                points.forEachIndexed { idx, pt ->
                    if (days[idx].spentCents > 0L && idx != selectedIndex) {
                        val isOver = dailyAllowanceCents > 0L && days[idx].spentCents > dailyAllowanceCents
                        drawCircle(
                            color = if (isOver) targetColor else lineColor,
                            radius = 2.8.dp.toPx(),
                            center = pt
                        )
                    }
                }

                // Active Crosshair & Selected Node Highlight
                val selectedPt = points.getOrNull(selectedIndex)
                val selectedSpent = days.getOrNull(selectedIndex)?.spentCents ?: 0L
                if (selectedPt != null) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.45f),
                        start = Offset(selectedPt.x, topPad),
                        end = Offset(selectedPt.x, chartBottom),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    val nodeColor = if (dailyAllowanceCents > 0L && selectedSpent > dailyAllowanceCents) {
                        targetColor
                    } else {
                        lineColor
                    }
                    drawCircle(
                        color = surfaceColor,
                        radius = 6.5.dp.toPx(),
                        center = selectedPt
                    )
                    drawCircle(
                        color = nodeColor,
                        radius = 4.8.dp.toPx(),
                        center = selectedPt
                    )
                }
            }
        }

        // X-axis date labels (5 checkpoints across the 30-day span)
        if (tickIndices.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tickIndices.forEach { idx ->
                    val label = if (idx == days.lastIndex) "Today" else days[idx].label
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (idx == selectedIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(
    color: Color,
    label: String,
    dashed: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (dashed) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(2.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PatternStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = AmountRowStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
