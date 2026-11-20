package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AccountEntity
import com.example.util.MoneyFormatter

@Composable
fun AccountEditorDialog(
    existingAccount: AccountEntity?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (existingId: Long?, name: String, initialBalanceCents: Long) -> Unit,
    onDelete: ((AccountEntity) -> Unit)? = null,
    onAddFunds: ((AccountEntity) -> Unit)? = null
) {
    var nameText by remember(existingAccount) {
        mutableStateOf(existingAccount?.name.orEmpty())
    }
    var balanceText by remember(existingAccount) {
        mutableStateOf(
            if (existingAccount != null && existingAccount.initialBalanceCents > 0L) {
                MoneyFormatter.centsToEditText(existingAccount.initialBalanceCents)
            } else {
                ""
            }
        )
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(if (existingAccount != null) "Edit Custom Account" else "Create Custom Account")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Name your separate account however you like. Expenses assigned to it will be tracked separately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        nameError = null
                    },
                    label = { Text("Account name") },
                    placeholder = { Text("Enter any custom account name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_account_name_input")
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        if (clean.count { it == '.' } <= 1) {
                            val parts = clean.split('.')
                            if (parts.size <= 1 || parts[1].length <= 2) {
                                balanceText = clean
                            }
                        }
                    },
                    label = { Text("Starting balance (optional)") },
                    placeholder = { Text("0.00") },
                    prefix = { Text(currencySymbol) },
                    supportingText = {
                        Text("Optional. Leave blank to track total spent only.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_account_balance_input")
                )

                if (existingAccount != null && onAddFunds != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onAddFunds(existingAccount)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_add_funds_to_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Funds to ${existingAccount.name}")
                    }
                }

                if (existingAccount != null && onDelete != null) {
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
                                    onDelete(existingAccount)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("confirm_delete_custom_account_button")
                            ) {
                                Text("Confirm Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            text = "Existing transactions stay in your history with their account label cleared.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.testTag("delete_custom_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Account", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = nameText.trim()
                    if (trimmed.isBlank()) {
                        nameError = "Please enter an account name"
                        return@Button
                    }
                    val balanceCents = MoneyFormatter.parseCents(balanceText) ?: 0L
                    onSave(existingAccount?.id, trimmed, balanceCents)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_custom_account_button")
            ) {
                Text(if (existingAccount != null) "Save" else "Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
