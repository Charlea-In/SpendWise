package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalSpendWiseColors

@Composable
fun BankMessageTestDialog(
    onDismiss: () -> Unit,
    onParseMessage: (rawTitle: String, rawBody: String, onOutcome: (String, Boolean) -> Unit) -> Unit
) {
    val semanticColors = LocalSpendWiseColors.current
    var senderTitle by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var feedbackText by remember { mutableStateOf<String?>(null) }
    var feedbackSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.MarkEmailRead,
                contentDescription = "Test bank message parser",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Paste or Test Bank Message")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Paste a bank SMS or notification alert below to extract the merchant, amount, card digits & category on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = senderTitle,
                    onValueChange = {
                        senderTitle = it
                        feedbackText = null
                    },
                    label = { Text("Bank / Sender (optional)") },
                    placeholder = { Text("e.g., BBVA or Chase Alert") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_test_sender_input")
                )

                OutlinedTextField(
                    value = messageBody,
                    onValueChange = {
                        messageBody = it
                        feedbackText = null
                    },
                    label = { Text("Bank SMS or notification text") },
                    placeholder = { Text("e.g., Purchase of \$14.50 at Starbucks with card ending 4821") },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_test_body_input")
                )

                Text(
                    text = "Quick samples:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            senderTitle = "Chase Alert"
                            messageBody = "Your card ending in 4821 was charged $18.75 at Trader Joe's on Nov 17."
                            feedbackText = null
                        },
                        label = { Text("US Card Charge") },
                        modifier = Modifier.testTag("bank_sample_us_chip")
                    )
                    AssistChip(
                        onClick = {
                            senderTitle = "BBVA"
                            messageBody = "Compra aprobada por MXN 245.00 en UBER EATS con tarjeta terminacion 9012."
                            feedbackText = null
                        },
                        label = { Text("Compra MXN") },
                        modifier = Modifier.testTag("bank_sample_mx_chip")
                    )
                    AssistChip(
                        onClick = {
                            senderTitle = "Bank Security"
                            messageBody = "Your verification code is 492018. Do not share this OTP with anyone."
                            feedbackText = null
                        },
                        label = { Text("OTP (Ignored)") },
                        modifier = Modifier.testTag("bank_sample_otp_chip")
                    )
                }

                if (feedbackText != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (feedbackSuccess) semanticColors.positiveContainer else semanticColors.warningContainer,
                        border = BorderStroke(
                            1.dp,
                            if (feedbackSuccess) semanticColors.positive.copy(alpha = 0.4f)
                            else semanticColors.warning.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (feedbackSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (feedbackSuccess) semanticColors.positive else semanticColors.warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feedbackText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onParseMessage(senderTitle, messageBody) { msg, success ->
                        feedbackText = msg
                        feedbackSuccess = success
                    }
                },
                enabled = messageBody.isNotBlank(),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("bank_test_parse_button")
            ) {
                Text("Detect Charge")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Close")
            }
        }
    )
}
