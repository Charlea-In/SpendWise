package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BudgetPreferences
import com.example.model.DailyBudgetMode
import com.example.model.ExpenseCategory
import com.example.util.MoneyFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCategoryBudgetDialog(
    category: ExpenseCategory,
    currentLimitCents: Long,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var limitText by remember(category, currentLimitCents) {
        mutableStateOf(
            if (currentLimitCents > 0L) MoneyFormatter.centsToEditText(currentLimitCents)
            else MoneyFormatter.centsToEditText(category.suggestedMonthlyCents)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = category.icon,
                contentDescription = category.displayName,
                tint = category.accentColor,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("${category.displayName} Monthly Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "You'll receive an alert when spending hits 80% and 100% of this limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) limitText = clean
                    },
                    label = { Text("Monthly limit ($currencySymbol)") },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_budget_limit_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 200, 350, 500, 1000).forEach { preset ->
                        FilterChip(
                            selected = limitText == preset.toString(),
                            onClick = { limitText = preset.toString() },
                            label = { Text("$currencySymbol$preset") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = MoneyFormatter.parseCents(limitText) ?: 0L
                    onSave(cents)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_category_budget_button")
            ) {
                Text("Save Limit")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (currentLimitCents > 0L) {
                    TextButton(
                        onClick = {
                            onSave(0L)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("remove_category_budget_button")
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun EditMonthlyBudgetDialog(
    preferences: BudgetPreferences,
    onDismiss: () -> Unit,
    onSave: (monthlyCents: Long, savingsCents: Long, mode: DailyBudgetMode, fixedDailyCents: Long) -> Unit
) {
    var monthlyText by remember {
        mutableStateOf(MoneyFormatter.centsToEditText(preferences.monthlyBudgetCents))
    }
    var savingsText by remember {
        mutableStateOf(MoneyFormatter.centsToEditText(preferences.monthlySavingsGoalCents))
    }
    var fixedDailyText by remember {
        mutableStateOf(MoneyFormatter.centsToEditText(preferences.fixedDailyLimitCents))
    }
    var selectedMode by remember { mutableStateOf(preferences.dailyMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly Budget Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = monthlyText,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) monthlyText = clean
                    },
                    label = { Text("Total Monthly Budget (${preferences.currencySymbol})") },
                    prefix = { Text(preferences.currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_budget_input")
                )

                OutlinedTextField(
                    value = savingsText,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) savingsText = clean
                    },
                    label = { Text("Monthly Savings Goal (${preferences.currencySymbol})") },
                    prefix = { Text(preferences.currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_savings_input")
                )

                Text(
                    text = "Daily allowance mode",
                    style = MaterialTheme.typography.titleSmall
                )

                DailyBudgetMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(mode.displayName, style = MaterialTheme.typography.labelLarge)
                            Text(
                                mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (selectedMode == DailyBudgetMode.FIXED_DAILY) {
                    OutlinedTextField(
                        value = fixedDailyText,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() || it == '.' }
                            if (clean.count { it == '.' } <= 1) fixedDailyText = clean
                        },
                        label = { Text("Fixed Daily Limit (${preferences.currencySymbol})") },
                        prefix = { Text(preferences.currencySymbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fixed_daily_limit_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val monthly = MoneyFormatter.parseCents(monthlyText) ?: preferences.monthlyBudgetCents
                    val savings = MoneyFormatter.parseCents(savingsText) ?: 0L
                    val fixedDaily = MoneyFormatter.parseCents(fixedDailyText) ?: preferences.fixedDailyLimitCents
                    onSave(monthly, savings.coerceAtMost(monthly), selectedMode, fixedDaily)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_monthly_budget_button")
            ) {
                Text("Save Plan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TodayBalanceSourceDialog(
    useAccountsBalance: Boolean,
    balanceAccountsCount: Int,
    totalAccountsBalanceCents: Long,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSelectSource: (useAccounts: Boolean) -> Unit,
    onManageAccounts: () -> Unit,
    onEditMonthlyBudget: () -> Unit
) {
    var selectedUseAccounts by remember(useAccountsBalance) { mutableStateOf(useAccountsBalance) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today Screen Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Choose what drives the main card on your Today screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedUseAccounts,
                        onClick = { selectedUseAccounts = true },
                        modifier = Modifier.testTag("today_source_accounts_radio")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Money in Accounts",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = if (balanceAccountsCount > 0) {
                                "$balanceAccountsCount account(s) • ${MoneyFormatter.format(totalAccountsBalanceCents, currencySymbol)} available"
                            } else {
                                "Uses remaining balances from your custom accounts instead of a budget."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !selectedUseAccounts,
                        onClick = { selectedUseAccounts = false },
                        modifier = Modifier.testTag("today_source_budget_radio")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Monthly Budget Plan",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Calculates daily safe-to-spend from a fixed monthly budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedUseAccounts) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onManageAccounts()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("today_source_add_account_button")
                    ) {
                        Text(if (balanceAccountsCount == 0) "Add Account with Balance" else "Add New Account")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            onSelectSource(false)
                            onDismiss()
                            onEditMonthlyBudget()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("today_source_edit_budget_button")
                    ) {
                        Text("Edit Monthly Budget")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectSource(selectedUseAccounts)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_today_source_button")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
