package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Savings
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AccountDepositEntity
import com.example.data.AccountEntity
import com.example.ui.theme.AmountRowStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.AccountSummaryItem
import com.example.util.DateUtils
import com.example.util.MoneyFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFundsSheet(
    isOpen: Boolean,
    existingDeposit: AccountDepositEntity?,
    initialAccountName: String?,
    availableAccounts: List<AccountEntity>,
    accountSummaries: List<AccountSummaryItem>,
    recentSources: List<String>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        accountName: String,
        source: String,
        amountCents: Long,
        timestampMillis: Long,
        note: String
    ) -> Unit,
    onDelete: (AccountDepositEntity) -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semanticColors = LocalSpendWiseColors.current

    var accountName by remember(isOpen, existingDeposit, initialAccountName) {
        mutableStateOf(
            existingDeposit?.accountName
                ?: initialAccountName?.takeIf { it.isNotBlank() }
                ?: availableAccounts.firstOrNull()?.name.orEmpty()
        )
    }
    var sourceText by remember(isOpen, existingDeposit) {
        mutableStateOf(existingDeposit?.source.orEmpty())
    }
    var amountText by remember(isOpen, existingDeposit) {
        mutableStateOf(
            existingDeposit?.let { MoneyFormatter.centsToEditText(it.amountCents) }.orEmpty()
        )
    }
    var timestampMillis by remember(isOpen, existingDeposit) {
        mutableLongStateOf(existingDeposit?.timestampMillis ?: System.currentTimeMillis())
    }
    var noteText by remember(isOpen, existingDeposit) {
        mutableStateOf(existingDeposit?.note.orEmpty())
    }
    var validationError by remember(isOpen, existingDeposit) {
        mutableStateOf<String?>(null)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember(isOpen, existingDeposit) { mutableStateOf(false) }

    val matchingSummary = remember(accountName, accountSummaries) {
        val clean = accountName.trim()
        if (clean.isBlank()) null
        else accountSummaries.firstOrNull { it.account.name.equals(clean, ignoreCase = true) }
    }
    val enteredCents = MoneyFormatter.parseCents(amountText) ?: 0L
    val distinctRecentSources = remember(recentSources) {
        recentSources.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(6)
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (existingDeposit != null) "Edit Added Funds" else "Add Funds to Account",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Add incoming money and note where it came from without affecting your expense totals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close add funds sheet"
                    )
                }
            }

            // Target Account Selector (100% custom)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Add to Account",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (availableAccounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableAccounts.forEach { acct ->
                            val selected = accountName.trim().equals(acct.name, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    accountName = acct.name
                                    validationError = null
                                },
                                label = { Text(acct.name) },
                                modifier = Modifier.testTag("deposit_account_chip_${acct.id}")
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = accountName,
                    onValueChange = {
                        accountName = it
                        validationError = null
                    },
                    label = { Text("Account name") },
                    placeholder = { Text("Pick or type any custom account name") },
                    supportingText = {
                        Text("New account names are automatically created in Separate Accounts.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deposit_account_input")
                )
            }

            // Source ("Where is the money coming from?") — 100% customizable
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Where is the money coming from?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = {
                        sourceText = it
                        validationError = null
                    },
                    label = { Text("Funds source") },
                    placeholder = { Text("e.g., Friend paid me back, Uber trip, Refund...") },
                    supportingText = {
                        Text("100% customizable — describe who paid you or where the funds came from.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deposit_source_input")
                )

                if (distinctRecentSources.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        distinctRecentSources.forEachIndexed { idx, recent ->
                            AssistChip(
                                onClick = {
                                    sourceText = recent
                                    validationError = null
                                },
                                label = { Text(recent) },
                                modifier = Modifier.testTag("deposit_recent_source_$idx")
                            )
                        }
                    }
                }
            }

            // Amount + Quick Add Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() || it == '.' }
                        if (cleaned.count { it == '.' } <= 1) {
                            val parts = cleaned.split('.')
                            if (parts.size <= 1 || parts[1].length <= 2) {
                                amountText = cleaned
                                validationError = null
                            }
                        }
                    },
                    label = { Text("Amount to add") },
                    placeholder = { Text("0.00") },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deposit_amount_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 25, 50, 100).forEach { delta ->
                        AssistChip(
                            onClick = {
                                val currentCents = MoneyFormatter.parseCents(amountText) ?: 0L
                                val nextCents = currentCents + (delta * 100L)
                                amountText = MoneyFormatter.centsToEditText(nextCents)
                                validationError = null
                            },
                            label = { Text("+$currencySymbol$delta") },
                            modifier = Modifier.testTag("deposit_quick_add_$delta")
                        )
                    }
                }
            }

            // Live Account Balance Preview
            if (matchingSummary != null && matchingSummary.hasBalanceOrTransferActivity && enteredCents > 0L) {
                val baseBalance = if (existingDeposit != null &&
                    existingDeposit.accountName.equals(matchingSummary.account.name, ignoreCase = true)
                ) {
                    matchingSummary.remainingBalanceCents - existingDeposit.amountCents
                } else {
                    matchingSummary.remainingBalanceCents
                }
                val newBalance = baseBalance + enteredCents
                val formatSigned = { cents: Long ->
                    if (cents < 0L) "-${MoneyFormatter.format(abs(cents), currencySymbol)}"
                    else MoneyFormatter.format(cents, currencySymbol)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deposit_balance_preview"),
                    shape = MaterialTheme.shapes.small,
                    color = semanticColors.positiveContainer,
                    border = BorderStroke(1.dp, semanticColors.positive.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${matchingSummary.account.name} balance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatSigned(baseBalance)} -> ${formatSigned(newBalance)}",
                            style = AmountRowStyle,
                            color = semanticColors.positive
                        )
                    }
                }
            }

            // Date & Note
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(DateUtils.formatHeaderDate(timestampMillis)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = "Deposit date",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("deposit_date_chip")
                )
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g., Paid back for dinner a couple days ago") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deposit_note_input")
            )

            if (validationError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = semanticColors.dangerContainer
                ) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.danger,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (existingDeposit != null) {
                if (confirmDelete) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { confirmDelete = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Keep")
                        }
                        TextButton(
                            onClick = {
                                onDelete(existingDeposit)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_delete_deposit_button")
                        ) {
                            Text("Delete Funds", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("delete_deposit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Funds Entry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val cleanAccount = accountName.trim()
                        val cleanSource = sourceText.trim()
                        val cents = MoneyFormatter.parseCents(amountText)
                        when {
                            cleanAccount.isBlank() -> {
                                validationError = "Please enter or select a destination account."
                            }
                            cleanSource.isBlank() -> {
                                validationError = "Please enter where the money is coming from."
                            }
                            cents == null || cents <= 0L -> {
                                validationError = "Please enter an amount greater than 0."
                            }
                            else -> {
                                onSave(
                                    existingDeposit?.id,
                                    cleanAccount,
                                    cleanSource,
                                    cents,
                                    timestampMillis,
                                    noteText
                                )
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                        .testTag("save_deposit_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (existingDeposit != null) "Save" else "Add Funds")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestampMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtc ->
                            timestampMillis = selectedUtc
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun DepositRowCard(
    deposit: AccountDepositEntity,
    currencySymbol: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalSpendWiseColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("deposit_row_${deposit.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semanticColors.positive.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(semanticColors.positiveContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Savings,
                    contentDescription = "Funds added to ${deposit.accountName}",
                    tint = semanticColors.positive,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = deposit.source,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildString {
                        append("Added to ${deposit.accountName} • ")
                        append(DateUtils.formatTime(deposit.timestampMillis))
                        if (deposit.note.isNotBlank()) {
                            append(" • ${deposit.note}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "+${MoneyFormatter.format(deposit.amountCents, currencySymbol)}",
                style = AmountRowStyle,
                color = semanticColors.positive
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_deposit_row_${deposit.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete added funds from ${deposit.source}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
