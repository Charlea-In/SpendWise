package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CommitmentEntity
import com.example.model.CommitmentType
import com.example.model.CommitmentWithStatus
import com.example.model.RecurrenceCycle
import com.example.ui.theme.AmountDisplayStyle
import com.example.ui.theme.AmountRowStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.MoneyFormatter
import com.example.viewmodel.SpendWiseUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlansScreen(
    uiState: SpendWiseUiState,
    onCreateCommitment: (CommitmentType) -> Unit,
    onEditCommitment: (CommitmentEntity) -> Unit,
    onFilterChange: (CommitmentType?) -> Unit,
    onRecordPayment: (CommitmentEntity, Boolean) -> Unit,
    onToggleActive: (CommitmentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalSpendWiseColors.current
    val currency = uiState.preferences.currencySymbol
    val planSummary = uiState.commitmentsSummary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("plans_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scheduled Finances",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Debts, Cards & Goals",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                FilledTonalButton(
                    onClick = { onCreateCommitment(uiState.selectedCommitmentFilter ?: CommitmentType.DEBT) },
                    modifier = Modifier.testTag("plans_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Plan")
                }
            }
        }

        // Monthly Commitments Hero Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plans_summary_hero_card"),
                shape = MaterialTheme.shapes.extraLarge,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(semanticColors.heroStart, semanticColors.heroEnd)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Fixed Outflows",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                            val badgeText = when {
                                planSummary.overdueCount > 0 -> "${planSummary.overdueCount} Overdue"
                                planSummary.dueWithin7DaysCount > 0 -> "${planSummary.dueWithin7DaysCount} Due Soon"
                                else -> "All Clear"
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = when {
                                    planSummary.overdueCount > 0 -> semanticColors.dangerContainer
                                    planSummary.dueWithin7DaysCount > 0 -> semanticColors.warningContainer
                                    else -> Color.White.copy(alpha = 0.18f)
                                }
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        planSummary.overdueCount > 0 -> semanticColors.danger
                                        planSummary.dueWithin7DaysCount > 0 -> semanticColors.warning
                                        else -> Color.White
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = MoneyFormatter.format(planSummary.monthlyScheduledOutflowCents, currency),
                            style = AmountDisplayStyle,
                            color = Color.White
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PlanHeroMetric(
                                label = "Debt Remaining",
                                value = MoneyFormatter.format(planSummary.totalDebtRemainingCents, currency, compactWhole = true)
                            )
                            PlanHeroMetric(
                                label = "Goals Saved",
                                value = MoneyFormatter.format(planSummary.totalGoalSavedCents, currency, compactWhole = true)
                            )
                            PlanHeroMetric(
                                label = "Paydays / Mo",
                                value = MoneyFormatter.format(planSummary.monthlyExpectedIncomeCents, currency, compactWhole = true)
                            )
                        }
                    }
                }
            }
        }

        // Quick Create Pills for every option
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Create",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CommitmentType.entries.forEach { type ->
                        Surface(
                            onClick = { onCreateCommitment(type) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, type.accentColor.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("quick_add_plan_${type.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = null,
                                    tint = type.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "+ ${type.singularTitle}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Type Filter Chips
        if (uiState.commitments.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCommitmentFilter == null,
                        onClick = { onFilterChange(null) },
                        label = { Text("All (${uiState.commitments.size})") },
                        modifier = Modifier.testTag("filter_plan_all")
                    )
                    CommitmentType.entries.forEach { type ->
                        val count = uiState.commitments.count { it.commitmentType == type }
                        if (count > 0) {
                            FilterChip(
                                selected = uiState.selectedCommitmentFilter == type,
                                onClick = { onFilterChange(type) },
                                label = { Text("${type.pluralTitle} ($count)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("filter_plan_${type.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Empty State or Commitment List
        if (uiState.filteredCommitments.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plans_empty_state"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventRepeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = if (uiState.selectedCommitmentFilter != null) {
                                "No ${uiState.selectedCommitmentFilter.pluralTitle.lowercase()} added yet"
                            } else {
                                "Track upcoming payments & goals"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Add debts with payment days, credit cards (with closing & due days), subscriptions, savings goals, bills, installments, or paydays.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                CommitmentType.DEBT,
                                CommitmentType.CREDIT_CARD,
                                CommitmentType.SUBSCRIPTION,
                                CommitmentType.GOAL
                            ).forEach { type ->
                                OutlinedButton(
                                    onClick = { onCreateCommitment(type) },
                                    modifier = Modifier.testTag("empty_add_${type.name.lowercase()}")
                                ) {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        tint = type.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(type.singularTitle)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(uiState.filteredCommitments, key = { it.commitment.id }) { item ->
                CommitmentCard(
                    item = item,
                    currencySymbol = currency,
                    onClick = { onEditCommitment(item.commitment) },
                    onRecordPayment = { logExpense ->
                        onRecordPayment(item.commitment, logExpense)
                    },
                    onToggleActive = { onToggleActive(item.commitment) }
                )
            }
        }
    }
}

@Composable
private fun CommitmentCard(
    item: CommitmentWithStatus,
    currencySymbol: String,
    onClick: () -> Unit,
    onRecordPayment: (logAsExpense: Boolean) -> Unit,
    onToggleActive: () -> Unit
) {
    val semanticColors = LocalSpendWiseColors.current
    val c = item.commitment
    val type = item.commitmentType
    val status = item.status

    val pillColors = when {
        !c.isActive -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        status.isFullyPaidOff || status.isCompletedThisCycle -> semanticColors.positiveContainer to semanticColors.positive
        status.isOverdue -> semanticColors.dangerContainer to semanticColors.danger
        status.isDueSoon -> semanticColors.warningContainer to semanticColors.warning
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (c.isActive) 1f else 0.72f)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("commitment_card_${c.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (status.isOverdue && c.isActive) {
                semanticColors.danger.copy(alpha = 0.5f)
            } else {
                semanticColors.cardBorder
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(type.accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.singularTitle,
                        tint = type.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = c.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        append(type.singularTitle)
                        append(" • ")
                        append(item.recurrenceCycle.label)
                        if (item.recurrenceCycle == RecurrenceCycle.MONTHLY) {
                            append(" (Day ${c.dueDayOfMonth})")
                        }
                        if (type == CommitmentType.CREDIT_CARD && c.statementDayOfMonth > 0) {
                            append(" • Closes ${c.statementDayOfMonth}")
                        }
                        if (c.accountName.isNotBlank()) {
                            append(" • ${c.accountName}")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyFormatter.format(c.amountCents, currencySymbol),
                        style = AmountRowStyle,
                        color = if (type == CommitmentType.INCOME) {
                            semanticColors.positive
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pillColors.first
                    ) {
                        Text(
                            text = status.statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = pillColors.second,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Optional progress bar for Debt payoff, Goal savings, or Installments
            val showMoneyProgress = (type == CommitmentType.DEBT || type == CommitmentType.GOAL) && c.targetOrTotalCents > 0L
            val showInstallmentProgress = type == CommitmentType.INSTALLMENT && c.totalInstallments > 0
            if (showMoneyProgress) {
                val ratio = (c.progressCents.toFloat() / c.targetOrTotalCents.toFloat()).coerceIn(0f, 1f)
                val remaining = (c.targetOrTotalCents - c.progressCents).coerceAtLeast(0L)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (type == CommitmentType.DEBT) {
                                "Paid ${MoneyFormatter.format(c.progressCents, currencySymbol)} of ${MoneyFormatter.format(c.targetOrTotalCents, currencySymbol)}"
                            } else {
                                "Saved ${MoneyFormatter.format(c.progressCents, currencySymbol)} of ${MoneyFormatter.format(c.targetOrTotalCents, currencySymbol)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${MoneyFormatter.format(remaining, currencySymbol)} left",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = type.accentColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = type.accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            } else if (showInstallmentProgress) {
                val ratio = (c.completedInstallments.toFloat() / c.totalInstallments.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${c.completedInstallments} of ${c.totalInstallments} installments paid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = type.accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            } else if (type == CommitmentType.CREDIT_CARD && c.targetOrTotalCents > 0L) {
                Text(
                    text = "Credit limit: ${MoneyFormatter.format(c.targetOrTotalCents, currencySymbol, compactWhole = true)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (c.note.isNotBlank()) {
                Text(
                    text = c.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleActive,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_commitment_active_${c.id}")
                ) {
                    Icon(
                        imageVector = if (c.isActive) Icons.Rounded.PauseCircleOutline else Icons.Rounded.PlayCircleOutline,
                        contentDescription = if (c.isActive) "Pause ${c.title}" else "Resume ${c.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (c.isActive && !status.isFullyPaidOff) {
                    if (status.isCompletedThisCycle) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Completed",
                                tint = semanticColors.positive,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Done for this cycle",
                                style = MaterialTheme.typography.labelMedium,
                                color = semanticColors.positive
                            )
                        }
                    } else {
                        Button(
                            onClick = { onRecordPayment(type.logsAsExpenseOnPay) },
                            colors = ButtonDefaults.buttonColors(containerColor = type.accentColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("commitment_action_${c.id}")
                        ) {
                            Text(
                                text = if (type.logsAsExpenseOnPay) "${type.actionLabel} & Log" else type.actionLabel,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanHeroMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.74f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}
