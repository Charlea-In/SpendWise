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
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.CommitmentEntity
import com.example.model.CommitmentType
import com.example.model.ExpenseCategory
import com.example.model.RecurrenceCycle
import com.example.ui.theme.AmountMediumStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.DateUtils
import com.example.util.MoneyFormatter
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommitmentEditorSheet(
    isOpen: Boolean,
    existingCommitment: CommitmentEntity?,
    initialType: CommitmentType,
    availableAccounts: List<AccountEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onSave: (
        existingId: Long?,
        title: String,
        type: CommitmentType,
        amountCents: Long,
        targetOrTotalCents: Long,
        progressCents: Long,
        cycle: RecurrenceCycle,
        dueDayOfMonth: Int,
        statementDayOfMonth: Int,
        scheduledDateMillis: Long,
        totalInstallments: Int,
        completedInstallments: Int,
        accountName: String,
        category: ExpenseCategory,
        note: String,
        reminderEnabled: Boolean
    ) -> Unit,
    onDelete: ((CommitmentEntity) -> Unit)? = null
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semanticColors = LocalSpendWiseColors.current

    var selectedType by remember(existingCommitment, initialType) {
        mutableStateOf(
            if (existingCommitment != null) CommitmentType.fromKey(existingCommitment.type)
            else initialType
        )
    }
    var titleText by remember(existingCommitment) {
        mutableStateOf(existingCommitment?.title.orEmpty())
    }
    var amountText by remember(existingCommitment) {
        mutableStateOf(
            if (existingCommitment != null) MoneyFormatter.centsToEditText(existingCommitment.amountCents)
            else ""
        )
    }
    var targetText by remember(existingCommitment) {
        mutableStateOf(
            if (existingCommitment != null && existingCommitment.targetOrTotalCents > 0L) {
                MoneyFormatter.centsToEditText(existingCommitment.targetOrTotalCents)
            } else ""
        )
    }
    var progressText by remember(existingCommitment) {
        mutableStateOf(
            if (existingCommitment != null && existingCommitment.progressCents > 0L) {
                MoneyFormatter.centsToEditText(existingCommitment.progressCents)
            } else ""
        )
    }
    var selectedCycle by remember(existingCommitment, initialType) {
        mutableStateOf(
            if (existingCommitment != null) {
                RecurrenceCycle.fromKey(existingCommitment.cycle)
            } else if (initialType == CommitmentType.GOAL) {
                RecurrenceCycle.ONE_TIME
            } else {
                RecurrenceCycle.MONTHLY
            }
        )
    }
    var dueDay by remember(existingCommitment) {
        mutableIntStateOf(
            existingCommitment?.dueDayOfMonth ?: DateUtils.currentDayOfMonth()
        )
    }
    var statementDayText by remember(existingCommitment) {
        mutableStateOf(
            if (existingCommitment != null && existingCommitment.statementDayOfMonth > 0) {
                existingCommitment.statementDayOfMonth.toString()
            } else ""
        )
    }
    var scheduledDateMillis by remember(existingCommitment) {
        mutableLongStateOf(
            existingCommitment?.scheduledDateMillis?.takeIf { it > 0L }
                ?: System.currentTimeMillis()
        )
    }
    var totalInstallmentsText by remember(existingCommitment) {
        mutableStateOf(
            if (existingCommitment != null && existingCommitment.totalInstallments > 0) {
                existingCommitment.totalInstallments.toString()
            } else ""
        )
    }
    var completedInstallmentsText by remember(existingCommitment) {
        mutableStateOf(
            if (existingCommitment != null && existingCommitment.completedInstallments > 0) {
                existingCommitment.completedInstallments.toString()
            } else ""
        )
    }
    var accountName by remember(existingCommitment) {
        mutableStateOf(existingCommitment?.accountName.orEmpty())
    }
    var selectedCategory by remember(existingCommitment, initialType) {
        mutableStateOf(
            if (existingCommitment != null) ExpenseCategory.fromKey(existingCommitment.category)
            else initialType.defaultCategory
        )
    }
    var noteText by remember(existingCommitment) {
        mutableStateOf(existingCommitment?.note.orEmpty())
    }
    var reminderEnabled by remember(existingCommitment) {
        mutableStateOf(existingCommitment?.reminderEnabled ?: true)
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
            Text(
                text = if (existingCommitment != null) {
                    "Edit ${selectedType.singularTitle}"
                } else {
                    "New Financial Plan"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Type Selector
            Text(
                text = "Type",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommitmentType.entries.forEach { type ->
                    val isSelected = selectedType == type
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedType = type
                                if (existingCommitment == null) {
                                    selectedCategory = type.defaultCategory
                                    selectedCycle = if (type == CommitmentType.GOAL) {
                                        RecurrenceCycle.ONE_TIME
                                    } else {
                                        RecurrenceCycle.MONTHLY
                                    }
                                }
                            }
                            .testTag("commitment_type_${type.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            type.accentColor.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) type.accentColor else semanticColors.cardBorder
                        )
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
                                text = type.singularTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Title
            OutlinedTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    validationError = null
                },
                label = {
                    Text(
                        when (selectedType) {
                            CommitmentType.DEBT -> "Debt or lender name"
                            CommitmentType.CREDIT_CARD -> "Credit card name"
                            CommitmentType.SUBSCRIPTION -> "Subscription service"
                            CommitmentType.GOAL -> "Goal name"
                            CommitmentType.BILL -> "Bill or rent name"
                            CommitmentType.INSTALLMENT -> "Installment item name"
                            CommitmentType.INCOME -> "Income / payday source"
                        }
                    )
                },
                placeholder = {
                    Text(
                        when (selectedType) {
                            CommitmentType.DEBT -> "e.g., Car Loan, Personal Debt"
                            CommitmentType.CREDIT_CARD -> "e.g., Visa Sapphire, Store Card"
                            CommitmentType.SUBSCRIPTION -> "e.g., Spotify, Gym, iCloud"
                            CommitmentType.GOAL -> "e.g., Emergency Fund, New Laptop"
                            CommitmentType.BILL -> "e.g., Apartment Rent, Fiber Internet"
                            CommitmentType.INSTALLMENT -> "e.g., Phone 12-Month Plan"
                            CommitmentType.INCOME -> "e.g., Biweekly Salary, Freelance"
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commitment_title_input")
            )

            // Primary Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                    amountText = filterDecimalInput(raw)
                    validationError = null
                },
                label = {
                    Text(
                        when (selectedType) {
                            CommitmentType.DEBT -> "Payment amount ($currencySymbol)"
                            CommitmentType.CREDIT_CARD -> "Payment / bill amount ($currencySymbol)"
                            CommitmentType.SUBSCRIPTION -> "Charge amount ($currencySymbol)"
                            CommitmentType.GOAL -> "Scheduled charge / contribution ($currencySymbol)"
                            CommitmentType.BILL -> "Bill amount ($currencySymbol)"
                            CommitmentType.INSTALLMENT -> "Each installment ($currencySymbol)"
                            CommitmentType.INCOME -> "Expected amount ($currencySymbol)"
                        }
                    )
                },
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
                    .testTag("commitment_amount_input")
            )

            // Type-specific optional balance / limit / goal / installment fields
            when (selectedType) {
                CommitmentType.DEBT, CommitmentType.GOAL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { targetText = filterDecimalInput(it) },
                            label = {
                                Text(
                                    if (selectedType == CommitmentType.DEBT) "Total debt (opt.)"
                                    else "Goal target (opt.)"
                                )
                            },
                            prefix = { Text(currencySymbol) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("commitment_target_input")
                        )
                        OutlinedTextField(
                            value = progressText,
                            onValueChange = { progressText = filterDecimalInput(it) },
                            label = {
                                Text(
                                    if (selectedType == CommitmentType.DEBT) "Paid so far"
                                    else "Saved so far"
                                )
                            },
                            prefix = { Text(currencySymbol) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("commitment_progress_input")
                        )
                    }
                }
                CommitmentType.CREDIT_CARD -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { targetText = filterDecimalInput(it) },
                            label = { Text("Credit limit (opt.)") },
                            prefix = { Text(currencySymbol) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("commitment_credit_limit_input")
                        )
                        OutlinedTextField(
                            value = statementDayText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(2)
                                val num = digits.toIntOrNull()
                                if (digits.isEmpty() || (num != null && num in 1..31)) {
                                    statementDayText = digits
                                }
                            },
                            label = { Text("Closing day (1-31)") },
                            placeholder = { Text("e.g., 5") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("commitment_closing_day_input")
                        )
                    }
                }
                CommitmentType.INSTALLMENT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = totalInstallmentsText,
                            onValueChange = { raw ->
                                totalInstallmentsText = raw.filter { it.isDigit() }.take(3)
                            },
                            label = { Text("Total months") },
                            placeholder = { Text("e.g., 12") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("commitment_total_installments_input")
                        )
                        OutlinedTextField(
                            value = completedInstallmentsText,
                            onValueChange = { raw ->
                                completedInstallmentsText = raw.filter { it.isDigit() }.take(3)
                            },
                            label = { Text("Paid count") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("commitment_paid_installments_input")
                        )
                    }
                }
                else -> Unit
            }

            // Recurrence Cycle & Payment / Charge Schedule
            Text(
                text = when (selectedType) {
                    CommitmentType.SUBSCRIPTION -> "When is it charged?"
                    CommitmentType.GOAL -> "When will it be charged / target date?"
                    CommitmentType.INCOME -> "Payday schedule"
                    else -> "Payment day & frequency"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecurrenceCycle.entries.forEach { cycle ->
                    FilterChip(
                        selected = selectedCycle == cycle,
                        onClick = { selectedCycle = cycle },
                        label = { Text(cycle.label) },
                        modifier = Modifier.testTag("commitment_cycle_${cycle.name.lowercase()}")
                    )
                }
            }

            if (selectedCycle == RecurrenceCycle.MONTHLY) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedType.dayFieldLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Day $dueDay of every month",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { dueDay = if (dueDay <= 1) 31 else dueDay - 1 },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("commitment_day_minus_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Decrease day"
                                )
                            }
                            Text(
                                text = dueDay.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { dueDay = if (dueDay >= 31) 1 else dueDay + 1 },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("commitment_day_plus_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Increase day"
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("commitment_pick_date_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${selectedType.dayFieldLabel}: ${DateUtils.formatHeaderDate(scheduledDateMillis)}"
                    )
                }
            }

            // Linked 100% Custom Account (no preset options)
            Text(
                text = "Linked account (optional)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableAccounts.forEach { acct ->
                    val isSelected = accountName.equals(acct.name, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            accountName = if (isSelected) "" else acct.name
                        },
                        label = { Text(acct.name) },
                        modifier = Modifier.testTag("commitment_account_chip_${acct.id}")
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
                    modifier = Modifier.testTag("commitment_create_account_chip")
                )
            }
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("Custom account name (optional)") },
                placeholder = { Text("Type any custom account name, or leave blank") },
                trailingIcon = {
                    if (accountName.isNotEmpty()) {
                        IconButton(onClick = { accountName = "" }) {
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
                    .testTag("commitment_account_input")
            )

            // Expense Category when logging charges
            if (selectedType.logsAsExpenseOnPay) {
                Text(
                    text = "Expense category when paid",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Note & Reminder toggle
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g., Interest rate, confirmation #, or plan details") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commitment_note_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Highlight when due soon",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Shows upcoming reminders on Today & Plans tabs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it }
                )
            }

            AnimatedVisibility(visible = validationError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(semanticColors.dangerContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = semanticColors.danger
                    )
                }
            }

            if (existingCommitment != null && onDelete != null) {
                if (confirmDelete) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { confirmDelete = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keep")
                        }
                        TextButton(
                            onClick = {
                                onDelete(existingCommitment)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_delete_commitment_button")
                        ) {
                            Text("Confirm Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("delete_commitment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete ${selectedType.singularTitle}", color = MaterialTheme.colorScheme.error)
                    }
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
                        .testTag("cancel_commitment_button")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val cleanTitle = titleText.trim()
                        if (cleanTitle.isBlank()) {
                            validationError = "Please enter a name."
                            return@Button
                        }
                        val amtCents = MoneyFormatter.parseCents(amountText)
                        if (amtCents == null || amtCents <= 0L) {
                            validationError = "Please enter an amount greater than 0."
                            return@Button
                        }
                        val targetCents = MoneyFormatter.parseCents(targetText) ?: 0L
                        val progressCents = MoneyFormatter.parseCents(progressText) ?: 0L
                        val stmtDay = statementDayText.toIntOrNull()?.coerceIn(1, 31) ?: 0
                        val totalInst = totalInstallmentsText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val doneInst = completedInstallmentsText.toIntOrNull()?.coerceAtLeast(0) ?: 0

                        onSave(
                            existingCommitment?.id,
                            cleanTitle,
                            selectedType,
                            amtCents,
                            targetCents,
                            progressCents,
                            selectedCycle,
                            dueDay,
                            stmtDay,
                            scheduledDateMillis,
                            totalInst,
                            doneInst,
                            accountName.trim(),
                            selectedCategory,
                            noteText.trim(),
                            reminderEnabled
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.6f)
                        .height(50.dp)
                        .testTag("save_commitment_button")
                ) {
                    Text(if (existingCommitment != null) "Save Changes" else "Save Plan")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = scheduledDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtcMidnight ->
                            val pickedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtcMidnight
                            }
                            val localCal = Calendar.getInstance().apply {
                                set(
                                    pickedCal.get(Calendar.YEAR),
                                    pickedCal.get(Calendar.MONTH),
                                    pickedCal.get(Calendar.DAY_OF_MONTH),
                                    9,
                                    0,
                                    0
                                )
                                set(Calendar.MILLISECOND, 0)
                            }
                            scheduledDateMillis = localCal.timeInMillis
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

private fun filterDecimalInput(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    if (cleaned.count { it == '.' } > 1) return cleaned.substringBeforeLast('.')
    val parts = cleaned.split('.')
    return if (parts.size == 2 && parts[1].length > 2) {
        "${parts[0]}.${parts[1].take(2)}"
    } else {
        cleaned
    }
}
