package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.BudgetPreferences
import com.example.ui.theme.LocalSpendWiseColors
import com.example.viewmodel.SyncUiState

@Composable
fun GoogleSheetsSyncDialog(
    preferences: BudgetPreferences,
    syncState: SyncUiState,
    onDismiss: () -> Unit,
    onTestConnection: (url: String, token: String) -> Unit,
    onSaveAndSync: (url: String, token: String, autoSync: Boolean) -> Unit
) {
    var urlText by remember(preferences.appsScriptUrl) { mutableStateOf(preferences.appsScriptUrl) }
    var tokenText by remember(preferences.appsScriptToken) { mutableStateOf(preferences.appsScriptToken) }
    var autoSync by remember(preferences.autoSyncEnabled) { mutableStateOf(preferences.autoSyncEnabled) }
    var showInstructions by remember { mutableStateOf(preferences.appsScriptUrl.isBlank()) }

    val semanticColors = LocalSpendWiseColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (preferences.isSheetConnected) Icons.Rounded.CloudDone else Icons.Rounded.TableChart,
                contentDescription = "Google Sheets Sync",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Connect Google Sheet") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Paste your deployed Google Apps Script Web App URL to sync daily expenses and category budgets directly with your own Google Sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Apps Script Web App URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apps_script_url_input")
                )

                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it },
                    label = { Text("Sync Secret Token (optional)") },
                    placeholder = { Text("Matches SYNC_TOKEN in Code.gs") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apps_script_token_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-sync on changes",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Push expenses & limits automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = { autoSync = it },
                        modifier = Modifier.testTag("apps_script_autosync_switch")
                    )
                }

                // Test Connection button + feedback
                OutlinedButton(
                    onClick = { onTestConnection(urlText, tokenText) },
                    enabled = urlText.isNotBlank() && !syncState.isTestingConnection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_sheet_connection_button")
                ) {
                    if (syncState.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...")
                    } else {
                        Text("Test Connection")
                    }
                }

                if (syncState.testFeedback != null) {
                    val bg = if (syncState.testSucceeded) semanticColors.positiveContainer else semanticColors.dangerContainer
                    val fg = if (syncState.testSucceeded) semanticColors.positive else semanticColors.danger
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = bg,
                        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = fg,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = syncState.testFeedback,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Collapsible quick instructions
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { showInstructions = !showInstructions }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "4-Step Apps Script Setup",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (showInstructions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(visible = showInstructions) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "1. Create a Google Sheet and open Extensions → Apps Script.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "2. Paste docs/Code.gs from the GitHub repo and save.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "3. Click Deploy → New deployment → Web app (Execute as: Me, Who has access: Anyone).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "4. Copy the /exec Web App URL above and tap Save & Sync.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAndSync(urlText.trim(), tokenText.trim(), autoSync)
                    onDismiss()
                },
                enabled = urlText.isNotBlank(),
                modifier = Modifier.testTag("save_sheet_config_button")
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (preferences.isSheetConnected) {
                    TextButton(
                        onClick = {
                            onSaveAndSync("", "", false)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("disconnect_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
