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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.AccountEntity
import com.example.data.AccountTransferEntity
import com.example.ui.theme.AmountRowStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.DateUtils
import com.example.util.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountTransferSheet(
    isOpen: Boolean,
    existingTransfer: AccountTransferEntity?,
    initialFromAccount: String?,
    availableAccounts: List<AccountEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        fromAccountName: String,
        toAccountName: String,
        amountCents: Long,
        timestampMillis: Long,
        note: String
    ) -> Unit,
    onDelete: (AccountTransferEntity) -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semanticColors = LocalSpendWiseColors.current

    var fromAccount by remember(isOpen, existingTransfer, initialFromAccount) {
        mutableStateOf(
            existingTransfer?.fromAccountName
                ?: initialFromAccount?.takeIf { it.isNotBlank() }
                ?: availableAccounts.firstOrNull()?.name.orEmpty()
        )
    }
    var toAccount by remember(isOpen, existingTransfer, initialFromAccount) {
        val fallbackTo = if (existingTransfer == null) {
            availableAccounts.firstOrNull {
                !it.name.equals(fromAccount, ignoreCase = true)
            }?.name.orEmpty()
        } else ""
        mutableStateOf(existingTransfer?.toAccountName ?: fallbackTo)
    }
    var amountText by remember(isOpen, existingTransfer) {
        mutableStateOf(
            existingTransfer?.let { MoneyFormatter.centsToEditText(it.amountCents) }.orEmpty()
        )
    }
    var timestampMillis by remember(isOpen, existingTransfer) {
        mutableLongStateOf(existingTransfer?.timestampMillis ?: System.currentTimeMillis())
    }
    var noteText by remember(isOpen, existingTransfer) {
        mutableStateOf(existingTransfer?.note.orEmpty())
    }
    var validationError by remember(isOpen, existingTransfer) {
        mutableStateOf<String?>(null)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember(isOpen, existingTransfer) { mutableStateOf(false) }

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
                        text = if (existingTransfer != null) "Edit Internal Transfer" else "Internal Account Transfer",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Moves funds between your custom accounts without counting as spending or inflating budgets.",
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
                        contentDescription = "Close transfer sheet"
                    )
                }
            }

            // From Account Selector (100% custom)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "From Account",
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
                            val selected = fromAccount.trim().equals(acct.name, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    fromAccount = acct.name
                                    validationError = null
                                },
                                label = { Text(acct.name) },
                                modifier = Modifier.testTag("transfer_from_chip_${acct.id}")
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = fromAccount,
                    onValueChange = {
                        fromAccount = it
                        validationError = null
                    },
                    label = { Text("Source account name") },
                    placeholder = { Text("Type or pick any custom account") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_from_input")
                )
            }

            // Swap From <-> To button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = semanticColors.cardBorder
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = {
                        val tmp = fromAccount
                        fromAccount = toAccount
                        toAccount = tmp
                        validationError = null
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("transfer_swap_accounts_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Swap source and destination accounts"
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = semanticColors.cardBorder
                )
            }

            // To Account Selector (100% custom)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "To Account",
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
                            val selected = toAccount.trim().equals(acct.name, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    toAccount = acct.name
                                    validationError = null
                                },
                                label = { Text(acct.name) },
                                modifier = Modifier.testTag("transfer_to_chip_${acct.id}")
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = toAccount,
                    onValueChange = {
                        toAccount = it
                        validationError = null
                    },
                    label = { Text("Destination account name") },
                    placeholder = { Text("Type or pick any custom account") },
                    supportingText = {
                        Text("New account names are automatically added to your Separate Accounts.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_to_input")
                )
            }

            // Amount + Quick Amount Chips
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
                    label = { Text("Transfer amount") },
                    placeholder = { Text("0.00") },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_amount_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 50, 100, 250).forEach { delta ->
                        AssistChip(
                            onClick = {
                                val currentCents = MoneyFormatter.parseCents(amountText) ?: 0L
                                val nextCents = currentCents + (delta * 100L)
                                amountText = MoneyFormatter.centsToEditText(nextCents)
                                validationError = null
                            },
                            label = { Text("+$currencySymbol$delta") },
                            modifier = Modifier.testTag("transfer_quick_add_$delta")
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
                            contentDescription = "Transfer date",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("transfer_date_chip")
                )
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g., Paycheck split or savings move") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transfer_note_input")
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

            if (existingTransfer != null) {
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
                                onDelete(existingTransfer)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_delete_transfer_button")
                        ) {
                            Text("Delete Transfer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("delete_transfer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Transfer", color = MaterialTheme.colorScheme.error)
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
                        val fromClean = fromAccount.trim()
                        val toClean = toAccount.trim()
                        val cents = MoneyFormatter.parseCents(amountText)
                        when {
                            fromClean.isBlank() -> {
                                validationError = "Please enter or select a source account."
                            }
                            toClean.isBlank() -> {
                                validationError = "Please enter or select a destination account."
                            }
                            fromClean.equals(toClean, ignoreCase = true) -> {
                                validationError = "Source and destination accounts must be different."
                            }
                            cents == null || cents <= 0L -> {
                                validationError = "Please enter a transfer amount greater than 0."
                            }
                            else -> {
                                onSave(
                                    existingTransfer?.id,
                                    fromClean,
                                    toClean,
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
                        .testTag("save_transfer_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (existingTransfer != null) "Save" else "Transfer")
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
fun TransferRowCard(
    transfer: AccountTransferEntity,
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
            .testTag("transfer_row_${transfer.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = "Internal account transfer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = transfer.fromAccountName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "to",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = transfer.toAccountName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = buildString {
                        append("Internal transfer • ")
                        append(DateUtils.formatTime(transfer.timestampMillis))
                        if (transfer.note.isNotBlank()) {
                            append(" • ${transfer.note}")
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
                text = MoneyFormatter.format(transfer.amountCents, currencySymbol),
                style = AmountRowStyle,
                color = semanticColors.positive
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_transfer_row_${transfer.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete transfer from ${transfer.fromAccountName} to ${transfer.toAccountName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
