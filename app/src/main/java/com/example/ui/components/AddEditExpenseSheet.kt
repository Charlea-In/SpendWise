package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AccountEntity
import com.example.model.CategorizationConfidence
import com.example.model.ExpenseCategory
import com.example.model.PaymentMethod
import com.example.ui.theme.AmountMediumStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.DateUtils
import com.example.viewmodel.ExpenseEditorState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditExpenseSheet(
    state: ExpenseEditorState,
    availableAccounts: List<AccountEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onAddQuickDelta: (Int) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCategorySelected: (ExpenseCategory) -> Unit,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onAccountNameChanged: (String) -> Unit,
    onCreateAccountClick: () -> Unit,
    onDateSelected: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    if (!state.isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    val semanticColors = LocalSpendWiseColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.editingId != null) "Edit Expense" else "Log Daily Expense",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(DateUtils.daySectionLabel(state.timestampMillis)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = "Choose expense date",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("expense_date_chip")
                )
            }

            // Amount Input
            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountChanged,
                label = { Text("Amount ($currencySymbol)") },
                prefix = {
                    Text(
                        text = currencySymbol,
                        style = AmountMediumStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                placeholder = { Text("0.00") },
                textStyle = AmountMediumStyle,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_amount_input")
            )

            // Quick amount adders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 20, 50).forEach { delta ->
                    OutlinedButton(
                        onClick = { onAddQuickDelta(delta) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_amount_$delta"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+${currencySymbol}$delta", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Description / Merchant Input (triggers Auto-Categorizer)
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChanged,
                label = { Text("Merchant or description") },
                placeholder = { Text("e.g., Trader Joe's, Coffee, Electric Bill, Uber") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_description_input")
            )

            // Live Auto-Categorization Status Banner
            val catResult = state.categorizationResult
            if (catResult != null && !state.isCategoryManuallyChosen) {
                val isUnsure = catResult.confidence == CategorizationConfidence.UNSURE
                val bannerBg = if (isUnsure) {
                    semanticColors.warningContainer
                } else {
                    semanticColors.positiveContainer
                }
                val bannerAccent = if (isUnsure) {
                    semanticColors.warning
                } else {
                    semanticColors.positive
                }
                val bannerIcon = if (isUnsure) {
                    Icons.Rounded.HelpOutline
                } else {
                    Icons.Rounded.AutoAwesome
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            if (isUnsure) "categorizer_unsure_prompt"
                            else "categorizer_auto_banner"
                        ),
                    shape = MaterialTheme.shapes.small,
                    color = bannerBg,
                    border = BorderStroke(1.dp, bannerAccent.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = if (isUnsure) "Category needs confirmation" else "Auto-categorized",
                            tint = bannerAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUnsure) {
                                    "Input needed: Choose a category"
                                } else {
                                    "Auto-categorized: ${catResult.suggestedCategory?.displayName.orEmpty()}"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = catResult.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Suggested quick-picks when unsure
            if (catResult != null && catResult.confidence == CategorizationConfidence.UNSURE &&
                !state.isCategoryManuallyChosen && catResult.candidates.isNotEmpty()
            ) {
                Text(
                    text = "Likely matches",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    catResult.candidates.take(3).forEach { candidate ->
                        FilterChip(
                            selected = state.selectedCategory == candidate,
                            onClick = { onCategorySelected(candidate) },
                            label = { Text(candidate.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = candidate.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("suggested_category_${candidate.name.lowercase()}")
                        )
                    }
                }
            }

            // Full Category Selector
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpenseCategory.entries.forEach { category ->
                    val selected = state.selectedCategory == category
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCategorySelected(category) }
                            .testTag("category_chip_${category.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            category.accentColor.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) category.accentColor else semanticColors.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = category.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = category.accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Payment Method
            Text(
                text = "Payment method",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected = state.paymentMethod == method,
                        onClick = { onPaymentMethodSelected(method) },
                        label = { Text(method.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = method.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("payment_method_${method.name.lowercase()}")
                    )
                }
            }

            // Custom Account / Wallet Name (100% user-customized, zero preset options)
            Text(
                text = "Account (optional)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableAccounts.forEach { acct ->
                    val isSelected = state.accountName.equals(acct.name, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onAccountNameChanged(if (isSelected) "" else acct.name)
                        },
                        label = { Text(acct.name) },
                        modifier = Modifier.testTag("account_chip_${acct.id}")
                    )
                }
                AssistChip(
                    onClick = onCreateAccountClick,
                    label = { Text("New Account") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("sheet_create_account_chip")
                )
            }
            OutlinedTextField(
                value = state.accountName,
                onValueChange = onAccountNameChanged,
                label = { Text("Custom account name (optional)") },
                placeholder = { Text("Type any custom account name, or leave blank") },
                trailingIcon = {
                    if (state.accountName.isNotEmpty()) {
                        IconButton(onClick = { onAccountNameChanged("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear account name"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_account_input")
            )

            // Optional Note
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChanged,
                label = { Text("Note (optional)") },
                placeholder = { Text("Add details or split info") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_note_input")
            )

            AnimatedVisibility(visible = state.validationError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(semanticColors.dangerContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = state.validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = semanticColors.danger
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cancel_expense_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(50.dp)
                        .testTag("save_expense_button")
                ) {
                    Text(if (state.editingId != null) "Save Changes" else "Save Expense")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.timestampMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtcMidnight ->
                            // Preserve current time-of-day on selected date
                            val nowCal = java.util.Calendar.getInstance()
                            val pickedCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtcMidnight
                            }
                            val localCal = java.util.Calendar.getInstance().apply {
                                set(
                                    pickedCal.get(java.util.Calendar.YEAR),
                                    pickedCal.get(java.util.Calendar.MONTH),
                                    pickedCal.get(java.util.Calendar.DAY_OF_MONTH),
                                    nowCal.get(java.util.Calendar.HOUR_OF_DAY),
                                    nowCal.get(java.util.Calendar.MINUTE)
                                )
                            }
                            onDateSelected(localCal.timeInMillis)
                        }
                        showDatePicker = false
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
