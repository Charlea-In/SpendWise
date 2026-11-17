package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Loyalty
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.PointsAccountEntity
import com.example.data.PointsFormatter
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsTrackerSheet(
    isOpen: Boolean,
    existingPointsAccount: PointsAccountEntity?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        name: String,
        pointsBalance: Long,
        unitLabel: String,
        centsPerPointTimes100: Long,
        note: String
    ) -> Unit,
    onDelete: (PointsAccountEntity) -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semanticColors = LocalSpendWiseColors.current

    var nameText by remember(isOpen, existingPointsAccount) {
        mutableStateOf(existingPointsAccount?.name.orEmpty())
    }
    var pointsText by remember(isOpen, existingPointsAccount) {
        mutableStateOf(
            existingPointsAccount?.pointsBalance?.takeIf { it > 0L }?.toString().orEmpty()
        )
    }
    var unitText by remember(isOpen, existingPointsAccount) {
        mutableStateOf(existingPointsAccount?.unitLabel?.ifBlank { "pts" } ?: "pts")
    }
    var centsPerPtText by remember(isOpen, existingPointsAccount) {
        mutableStateOf(
            existingPointsAccount?.let {
                PointsFormatter.centsPerPointToEditText(it.centsPerPointTimes100)
            }.orEmpty()
        )
    }
    var noteText by remember(isOpen, existingPointsAccount) {
        mutableStateOf(existingPointsAccount?.note.orEmpty())
    }
    var validationError by remember(isOpen, existingPointsAccount) {
        mutableStateOf<String?>(null)
    }
    var confirmDelete by remember(isOpen, existingPointsAccount) {
        mutableStateOf(false)
    }

    val parsedPoints = pointsText.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val parsedCentsPerPt = PointsFormatter.parseCentsPerPointTimes100(centsPerPtText)
    val previewEstimatedCents = if (parsedPoints > 0L && parsedCentsPerPt > 0L) {
        (parsedPoints * parsedCentsPerPt) / 100L
    } else {
        0L
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
                        text = if (existingPointsAccount != null) "Update Points Balance" else "Add Points Tracker",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Quick view only — track reward points manually without affecting cash balances or budgets.",
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
                        contentDescription = "Close points sheet"
                    )
                }
            }

            OutlinedTextField(
                value = nameText,
                onValueChange = {
                    nameText = it
                    validationError = null
                },
                label = { Text("Program or card name") },
                placeholder = { Text("Enter any custom card, airline, or store") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("points_name_input")
            )

            // Current points + fast manual delta pills
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { raw ->
                        val digitsOnly = raw.filter { it.isDigit() }
                        if (digitsOnly.length <= 12) {
                            pointsText = digitsOnly
                            validationError = null
                        }
                    },
                    label = { Text("Current points") },
                    placeholder = { Text("0") },
                    suffix = { Text(unitText.trim().ifBlank { "pts" }) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("points_balance_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(-1_000L to "-1k", 100L to "+100", 500L to "+500", 1_000L to "+1k", 5_000L to "+5k").forEach { (delta, label) ->
                        AssistChip(
                            onClick = {
                                val next = (parsedPoints + delta).coerceAtLeast(0L)
                                pointsText = if (next == 0L) "0" else next.toString()
                                validationError = null
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("points_quick_delta_$delta")
                        )
                    }
                }
            }

            // Custom unit label
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Unit label",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("pts", "miles", "stars", "coins").forEach { unit ->
                        FilterChip(
                            selected = unitText.trim().equals(unit, ignoreCase = true),
                            onClick = { unitText = unit },
                            label = { Text(unit) },
                            modifier = Modifier.testTag("points_unit_chip_$unit")
                        )
                    }
                }
                OutlinedTextField(
                    value = unitText,
                    onValueChange = { if (it.length <= 14) unitText = it },
                    label = { Text("Custom unit (optional)") },
                    placeholder = { Text("pts") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("points_unit_input")
                )
            }

            // Optional value estimator (informational only)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = centsPerPtText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() || it == '.' }
                        if (cleaned.count { it == '.' } <= 1) {
                            val parts = cleaned.split('.')
                            if (parts.size <= 1 || parts[1].length <= 2) {
                                centsPerPtText = cleaned
                            }
                        }
                    },
                    label = { Text("Value per point in cents (optional)") },
                    placeholder = { Text("e.g., 1.0") },
                    suffix = { Text("¢ / ${unitText.trim().ifBlank { "pt" }}") },
                    supportingText = {
                        val valuePreview = if (previewEstimatedCents > 0L) {
                            "Est. quick-view value: ${MoneyFormatter.format(previewEstimatedCents, currencySymbol)} (does not affect balances)"
                        } else {
                            "Optional reference value only — never changes your cash or account balances."
                        }
                        Text(valuePreview)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("points_value_input")
                )
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g., Travel portal or expiration reminder") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("points_note_input")
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

            if (existingPointsAccount != null) {
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
                                onDelete(existingPointsAccount)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_delete_points_button")
                        ) {
                            Text("Remove Tracker", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("delete_points_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Points Tracker", color = MaterialTheme.colorScheme.error)
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
                        val trimmedName = nameText.trim()
                        if (trimmedName.isBlank()) {
                            validationError = "Please enter a name for this points tracker."
                            return@Button
                        }
                        val points = pointsText.trim().toLongOrNull() ?: 0L
                        onSave(
                            existingPointsAccount?.id,
                            trimmedName,
                            points.coerceAtLeast(0L),
                            unitText.trim().ifBlank { "pts" },
                            parsedCentsPerPt,
                            noteText.trim()
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                        .testTag("save_points_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Loyalty,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (existingPointsAccount != null) "Update" else "Save")
                }
            }
        }
    }
}
