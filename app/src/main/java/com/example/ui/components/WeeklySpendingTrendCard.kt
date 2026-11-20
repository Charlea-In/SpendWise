package com.example.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.example.util.DailyDayBar
import com.example.util.MoneyFormatter
import com.example.util.SevenDayTrendSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Interactive Recharts-inspired Cartesian area + line visualization for the 7-day spending window
 * displayed directly on the Today / Daily Ledger dashboard.
 */
@Composable
fun WeeklySpendingTrendCard(
    trend: SevenDayTrendSnapshot,
    currencySymbol: String,
    dailyTargetCents: Long,
    accounts: List<String>,
    selectedAccount: String?,
    onSelectAccount: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalSpendWiseColors.current
    val days = trend.days
    var selectedIndex by remember(days.size, selectedAccount) {
        mutableIntStateOf((days.size - 1).coerceAtLeast(0))
    }
    val safeIndex = selectedIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val selectedDay = days.getOrNull(safeIndex)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_spending_trend_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semanticColors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with week-over-week trend badge
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                            contentDescription = "Last 7 days spending trend chart",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Last 7 Days Spending",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Total ${MoneyFormatter.format(trend.totalCents, currencySymbol)} • ${MoneyFormatter.format(trend.dailyAverageCents, currencySymbol)}/d avg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val deltaPct = trend.deltaPercentVsPriorWeek
                if (deltaPct != null) {
                    val isLowerSpend = deltaPct <= 0
                    val badgeColor = if (isLowerSpend) semanticColors.positive else semanticColors.warning
                    val badgeBg = if (isLowerSpend) semanticColors.positiveContainer else semanticColors.warningContainer
                    val arrow = if (deltaPct > 0) "+" else ""
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeBg,
                        modifier = Modifier.testTag("weekly_trend_delta_badge")
                    ) {
                        Text(
                            text = "$arrow$deltaPct% vs 7d",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Optional Account Filter Chips
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
                        modifier = Modifier.testTag("weekly_trend_account_all")
                    )
                    accounts.forEach { acctName ->
                        FilterChip(
                            selected = selectedAccount.equals(acctName, ignoreCase = true),
                            onClick = { onSelectAccount(acctName) },
                            label = { Text(acctName) },
                            modifier = Modifier.testTag("weekly_trend_account_${acctName.lowercase()}")
                        )
                    }
                }
            }

            // Active Scrubbed Day Tooltip Readout
            if (selectedDay != null) {
                val isOverTarget = dailyTargetCents > 0L && selectedDay.spentCents > dailyTargetCents
                val isAboveAvg = trend.dailyAverageCents > 0L && selectedDay.spentCents > trend.dailyAverageCents
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weekly_trend_tooltip"),
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
                                text = if (safeIndex == days.lastIndex) {
                                    "Today • ${selectedDay.fullDateLabel}"
                                } else {
                                    selectedDay.fullDateLabel
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    selectedDay.spentCents == 0L -> "No-spend day"
                                    isOverTarget -> "Over daily pace (${MoneyFormatter.format(dailyTargetCents, currencySymbol, compactWhole = true)})"
                                    isAboveAvg -> "Above 7d avg (${MoneyFormatter.format(trend.dailyAverageCents, currencySymbol, compactWhole = true)}/d)"
                                    else -> "Within 7-day pace"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOverTarget) semanticColors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = MoneyFormatter.format(selectedDay.spentCents, currencySymbol),
                            style = AmountMediumStyle,
                            color = if (isOverTarget) semanticColors.danger else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Recharts-style Cartesian Area + Line Canvas Chart
            WeeklySpendingLineCanvas(
                days = days,
                dailyAverageCents = trend.dailyAverageCents,
                dailyTargetCents = dailyTargetCents,
                currencySymbol = currencySymbol,
                selectedIndex = safeIndex,
                onDaySelected = { selectedIndex = it }
            )

            // Legend row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeeklyLegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = "Daily Spend",
                    dashed = false
                )
                WeeklyLegendItem(
                    color = semanticColors.warning,
                    label = "7d Avg",
                    dashed = true
                )
                if (dailyTargetCents > 0L) {
                    WeeklyLegendItem(
                        color = semanticColors.danger,
                        label = "Daily Pace",
                        dashed = true
                    )
                }
            }

            // Summary Stat Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeeklyStatPill(
                    label = "7d Avg",
                    value = MoneyFormatter.format(trend.dailyAverageCents, currencySymbol),
                    modifier = Modifier.weight(1f)
                )
                WeeklyStatPill(
                    label = trend.peakDay?.let { "Peak (${it.label})" } ?: "Peak Day",
                    value = trend.peakDay?.let {
                        MoneyFormatter.format(it.spentCents, currencySymbol, compactWhole = true)
                    } ?: "${currencySymbol}0",
                    modifier = Modifier.weight(1f)
                )
                WeeklyStatPill(
                    label = "No-Spend",
                    value = "${trend.noSpendDaysCount}/7d",
                    modifier = Modifier.weight(1f)
                )
            }

            if (trend.totalCents == 0L) {
                Text(
                    text = "Log expenses or switch on Demo Mode to see your 7-day daily spending curve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeeklySpendingLineCanvas(
    days: List<DailyDayBar>,
    dailyAverageCents: Long,
    dailyTargetCents: Long,
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

    val maxCents = remember(days, dailyAverageCents, dailyTargetCents) {
        val peak = days.maxOfOrNull { it.spentCents } ?: 0L
        max(max(peak, max(dailyAverageCents, dailyTargetCents)), 1000L)
    }

    val yTopLabel = MoneyFormatter.format(maxCents, currencySymbol, compactWhole = true)
    val yMidLabel = MoneyFormatter.format(maxCents / 2L, currencySymbol, compactWhole = true)
    val yZeroLabel = "${currencySymbol}0"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-Axis Scale
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .height(164.dp)
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
                    .height(164.dp)
                    .testTag("weekly_trend_canvas")
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
                val horizontalPad = 8.dp.toPx()
                val plotWidth = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                val chartBottom = size.height - bottomPad
                val usableHeight = (chartBottom - topPad).coerceAtLeast(1f)

                val stepX = if (days.size > 1) plotWidth / (days.size - 1).toFloat() else 0f

                // Cartesian horizontal gridlines
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

                // Vertical Cartesian gridlines per day
                days.indices.forEach { idx ->
                    val x = horizontalPad + idx * stepX
                    drawLine(
                        color = gridColor.copy(alpha = 0.25f),
                        start = Offset(x, topPad),
                        end = Offset(x, chartBottom),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Daily Pace Reference Line
                if (dailyTargetCents > 0L) {
                    val ratio = (dailyTargetCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
                    val targetY = chartBottom - usableHeight * ratio
                    drawLine(
                        color = targetColor.copy(alpha = 0.78f),
                        start = Offset(horizontalPad, targetY),
                        end = Offset(size.width - horizontalPad, targetY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                }

                // 7-Day Average Reference Line
                if (dailyAverageCents > 0L) {
                    val ratio = (dailyAverageCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
                    val avgY = chartBottom - usableHeight * ratio
                    drawLine(
                        color = avgColor.copy(alpha = 0.85f),
                        start = Offset(horizontalPad, avgY),
                        end = Offset(size.width - horizontalPad, avgY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                val points = days.mapIndexed { idx, day ->
                    val x = horizontalPad + idx * stepX
                    val yRatio = (day.spentCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
                    val y = chartBottom - (usableHeight * yRatio)
                    Offset(x, y)
                }

                if (points.size >= 2) {
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

                // Data point dots
                points.forEachIndexed { idx, pt ->
                    if (idx != selectedIndex) {
                        val isOver = dailyTargetCents > 0L && days[idx].spentCents > dailyTargetCents
                        val dotColor = if (isOver) targetColor else lineColor
                        drawCircle(
                            color = surfaceColor,
                            radius = 4.2.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = dotColor,
                            radius = 3.0.dp.toPx(),
                            center = pt
                        )
                    }
                }

                // Active Crosshair & Selected Node Highlight
                val selectedPt = points.getOrNull(selectedIndex)
                val selectedSpent = days.getOrNull(selectedIndex)?.spentCents ?: 0L
                if (selectedPt != null) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.5f),
                        start = Offset(selectedPt.x, topPad),
                        end = Offset(selectedPt.x, chartBottom),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    val nodeColor = if (dailyTargetCents > 0L && selectedSpent > dailyTargetCents) {
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

        // Interactive 7-Day X-Axis Labels
        if (days.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                days.forEachIndexed { idx, day ->
                    val selected = idx == selectedIndex
                    val displayLabel = if (idx == days.lastIndex) "Today" else day.label
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDaySelected(idx) }
                            .testTag("weekly_trend_day_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Text(
                                text = displayLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyLegendItem(
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
private fun WeeklyStatPill(
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
