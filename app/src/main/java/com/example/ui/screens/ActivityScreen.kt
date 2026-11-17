package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AccountTransferEntity
import com.example.data.ExpenseEntity
import com.example.model.ExpenseCategory
import com.example.ui.components.ExpenseRowCard
import com.example.ui.components.TransferRowCard
import com.example.ui.theme.AmountRowStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.DateUtils
import com.example.util.MoneyFormatter
import com.example.viewmodel.ActivityTimeframe
import com.example.viewmodel.SpendWiseUiState

@Composable
fun ActivityScreen(
    uiState: SpendWiseUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategoryFilterChange: (ExpenseCategory?) -> Unit,
    onAccountFilterChange: (String?) -> Unit,
    onTimeframeChange: (ActivityTimeframe) -> Unit,
    onEditExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onCreateTransfer: () -> Unit,
    onEditTransfer: (AccountTransferEntity) -> Unit,
    onDeleteTransfer: (AccountTransferEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalSpendWiseColors.current
    val currency = uiState.preferences.currencySymbol

    val groupedByDay = remember(uiState.filteredExpenses) {
        uiState.filteredExpenses.groupBy { DateUtils.dayKey(it.timestampMillis) }
    }
    val filteredTotal = remember(uiState.filteredExpenses) {
        uiState.filteredExpenses.sumOf { it.amountCents }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("activity_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Expense Activity",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Search input
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search merchant, category, account, or note") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search expenses"
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activity_search_input")
            )
        }

        // Timeframe filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityTimeframe.entries.forEach { tf ->
                    FilterChip(
                        selected = uiState.timeframeFilter == tf,
                        onClick = { onTimeframeChange(tf) },
                        label = { Text(tf.label) },
                        modifier = Modifier.testTag("timeframe_chip_${tf.name.lowercase()}")
                    )
                }
            }
        }

        // Category filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedCategoryFilter == null,
                    onClick = { onCategoryFilterChange(null) },
                    label = { Text("All Categories") },
                    modifier = Modifier.testTag("filter_category_all")
                )
                ExpenseCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = uiState.selectedCategoryFilter == cat,
                        onClick = { onCategoryFilterChange(cat) },
                        label = { Text(cat.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("filter_category_${cat.name.lowercase()}")
                    )
                }
            }
        }

        // Custom Account filter chips (only shown when user has created accounts)
        if (uiState.accounts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedActivityAccountFilter == null,
                        onClick = { onAccountFilterChange(null) },
                        label = { Text("All Accounts") },
                        modifier = Modifier.testTag("filter_account_all")
                    )
                    uiState.accounts.forEach { acct ->
                        val selected = uiState.selectedActivityAccountFilter.equals(acct.name, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { onAccountFilterChange(acct.name) },
                            label = { Text(acct.name) },
                            modifier = Modifier.testTag("filter_account_${acct.id}")
                        )
                    }
                }
            }
        }

        // Filtered summary bar
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.filteredExpenses.size} expenses matched",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = MoneyFormatter.format(filteredTotal, currency),
                        style = AmountRowStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Internal Transfers section (kept strictly separate from expense totals)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.filteredTransfers.isNotEmpty()) {
                            "Internal Transfers (${uiState.filteredTransfers.size})"
                        } else {
                            "Internal Transfers"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.filteredTransfers.isNotEmpty()) {
                            "Account-to-account moves • excluded from expense totals"
                        } else {
                            "Move money between your custom accounts without counting as spending"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onCreateTransfer,
                    modifier = Modifier.testTag("activity_new_transfer_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Transfer")
                }
            }
        }

        if (uiState.filteredTransfers.isNotEmpty()) {
            items(uiState.filteredTransfers, key = { "transfer_${it.id}" }) { transfer ->
                TransferRowCard(
                    transfer = transfer,
                    currencySymbol = currency,
                    onClick = { onEditTransfer(transfer) },
                    onDelete = { onDeleteTransfer(transfer) }
                )
            }
        }

        if (uiState.filteredExpenses.isEmpty()) {
            if (uiState.filteredTransfers.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semanticColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No matching expenses",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Try adjusting your filters or tap + Log expense to add a transaction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            groupedByDay.forEach { (_, dayItems) ->
                val sampleMillis = dayItems.first().timestampMillis
                val daySubtotal = dayItems.sumOf { it.amountCents }

                item(key = "header_${DateUtils.dayKey(sampleMillis)}") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.daySectionLabel(sampleMillis),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MoneyFormatter.format(daySubtotal, currency),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(dayItems, key = { it.id }) { expense ->
                    ExpenseRowCard(
                        expense = expense,
                        currencySymbol = currency,
                        onClick = { onEditExpense(expense) },
                        onDelete = { onDeleteExpense(expense) }
                    )
                }
            }
        }
    }
}
