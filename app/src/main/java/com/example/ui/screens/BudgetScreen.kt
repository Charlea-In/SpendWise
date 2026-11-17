package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Loyalty
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AccountEntity
import com.example.data.PointsAccountEntity
import com.example.data.PointsFormatter
import com.example.model.ExpenseCategory
import com.example.ui.theme.AmountMediumStyle
import com.example.ui.theme.AmountRowStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.BudgetStatusLevel
import com.example.util.CategoryProgressItem
import com.example.util.CsvExporter
import com.example.util.DateUtils
import com.example.util.MoneyFormatter
import com.example.viewmodel.SpendWiseUiState
import com.example.widget.SpendWiseBalanceWidgetProvider
import kotlin.math.abs
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun BudgetScreen(
    uiState: SpendWiseUiState,
    onEditMonthlyPlan: () -> Unit,
    onEditCategoryLimit: (ExpenseCategory, Long) -> Unit,
    onApplySuggestedBudgets: () -> Unit,
    onCreateAccount: () -> Unit,
    onEditAccount: (AccountEntity) -> Unit,
    onTransferClick: () -> Unit,
    onCreatePointsAccount: () -> Unit,
    onEditPointsAccount: (PointsAccountEntity) -> Unit,
    onOpenPlansTab: () -> Unit,
    onCurrencyChanged: (String) -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onGeminiAssistToggled: (Boolean) -> Unit,
    onOpenBankNotificationAccess: () -> Unit,
    onUpdateBankDetectionSettings: (Boolean, Boolean, String) -> Unit,
    onOpenBankMessageDialog: () -> Unit,
    onOpenSheetSyncDialog: () -> Unit,
    onSyncNow: () -> Unit,
    onShowMessage: (String) -> Unit,
    onPermissionResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semanticColors = LocalSpendWiseColors.current
    val prefs = uiState.preferences
    val currency = prefs.currencySymbol
    val summary = uiState.summary

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult()
        if (granted) {
            onNotificationsToggled(true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("budget_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Budget & Sync",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Monthly Plan Hero Card with generated finance banner
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                shadowElevation = 3.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_finance_banner),
                        contentDescription = stringResource(R.string.cd_budget_banner),
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = DateUtils.currentMonthTitle(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "${MoneyFormatter.format(prefs.monthlyBudgetCents, currency, compactWhole = true)} Monthly Plan",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = onEditMonthlyPlan,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F5C4D)
                                ),
                                modifier = Modifier.testTag("budget_edit_plan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Savings Target",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                                Text(
                                    text = MoneyFormatter.format(prefs.monthlySavingsGoalCents, currency, compactWhole = true),
                                    style = AmountMediumStyle,
                                    color = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Spendable Cap",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                                Text(
                                    text = MoneyFormatter.format(summary.spendableMonthBudgetCents, currency, compactWhole = true),
                                    style = AmountMediumStyle,
                                    color = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Month Spent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                                Text(
                                    text = MoneyFormatter.format(summary.monthSpentCents, currency, compactWhole = true),
                                    style = AmountMediumStyle,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = "${prefs.dailyMode.displayName} • ${prefs.dailyMode.subtitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // My Custom Accounts (100% customizable, zero preset options)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_custom_accounts_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = "Custom Accounts",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Separate Accounts",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "100% custom names, balances & internal transfers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTransferClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("budget_transfer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transfer")
                        }
                        FilledTonalButton(
                            onClick = onCreateAccount,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("budget_add_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Account")
                        }
                    }

                    if (summary.accountSummaries.isEmpty()) {
                        Text(
                            text = "No custom accounts yet. Tap New Account or Transfer to set up your separate accounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.accountSummaries.forEachIndexed { idx, item ->
                            if (idx > 0) {
                                HorizontalDivider(color = semanticColors.cardBorder)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onEditAccount(item.account) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("budget_account_row_${item.account.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.account.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val detailParts = buildList {
                                        if (item.hasStartingBalance) {
                                            add("Start ${MoneyFormatter.format(item.account.initialBalanceCents, currency)}")
                                        }
                                        if (item.transferredInCents > 0L) {
                                            add("In +${MoneyFormatter.format(item.transferredInCents, currency)}")
                                        }
                                        if (item.transferredOutCents > 0L) {
                                            add("Out -${MoneyFormatter.format(item.transferredOutCents, currency)}")
                                        }
                                        add("Spent ${MoneyFormatter.format(item.totalSpentCents, currency)}")
                                        add("${item.transactionCount} tx")
                                    }
                                    Text(
                                        text = detailParts.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (item.hasBalanceOrTransferActivity) {
                                        val isNeg = item.remainingBalanceCents < 0L
                                        Text(
                                            text = if (isNeg) {
                                                "-${MoneyFormatter.format(abs(item.remainingBalanceCents), currency)}"
                                            } else {
                                                MoneyFormatter.format(item.remainingBalanceCents, currency)
                                            },
                                            style = AmountRowStyle,
                                            color = if (isNeg) semanticColors.danger else semanticColors.positive
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit ${item.account.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Points & Rewards Quick View Management Card (informational only, no balance impact)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_points_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Loyalty,
                                contentDescription = "Points & Rewards",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Points & Rewards",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Quick view only — does not affect cash balances",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = onCreatePointsAccount,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("budget_add_points_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (uiState.pointsAccounts.isEmpty()) {
                        Text(
                            text = "No points trackers yet. Add custom card, airline, or store rewards to keep totals visible on Today and your Home Widget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.pointsAccounts.forEachIndexed { idx, pts ->
                            if (idx > 0) {
                                HorizontalDivider(color = semanticColors.cardBorder)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onEditPointsAccount(pts) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("budget_points_row_${pts.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pts.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val metaParts = buildList {
                                        add(PointsFormatter.formatFreshness(pts.updatedAtMillis))
                                        if (pts.estimatedValueCents > 0L) {
                                            add("≈ ${MoneyFormatter.format(pts.estimatedValueCents, currency)}")
                                        }
                                        if (pts.note.isNotBlank()) {
                                            add(pts.note)
                                        }
                                    }
                                    Text(
                                        text = metaParts.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = PointsFormatter.formatWithUnit(pts.pointsBalance, pts.unitLabel),
                                        style = AmountRowStyle,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Update ${pts.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shortcut to Debts, Credit Cards, Subscriptions & Goals
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable(onClick = onOpenPlansTab)
                    .testTag("budget_open_plans_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventRepeat,
                            contentDescription = "Scheduled Finances",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Debts, Cards, Subscriptions & Goals",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${uiState.commitments.size} active plans • Payment days, bills & paydays",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenPlansTab,
                        modifier = Modifier.testTag("budget_open_plans_button")
                    ) {
                        Text("Open")
                    }
                }
            }
        }

        // Google Sheets (Apps Script) & Demo Mode Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheets_and_demo_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Demo Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Science,
                                contentDescription = "Demo Mode",
                                tint = semanticColors.warning
                            )
                            Column {
                                Text(
                                    text = "Demo Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Load 7 days of sample expenses & budgets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = prefs.isDemoMode,
                            onCheckedChange = onToggleDemoMode,
                            modifier = Modifier.testTag("demo_mode_switch")
                        )
                    }

                    HorizontalDivider(color = semanticColors.cardBorder)

                    // Google Apps Script Connection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (prefs.isSheetConnected) Icons.Rounded.CloudDone else Icons.Rounded.TableChart,
                            contentDescription = "Google Sheets Sync",
                            tint = if (prefs.isSheetConnected) semanticColors.positive else MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (prefs.isSheetConnected) "Google Sheet Connected" else "Google Sheets Sync (Apps Script)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (prefs.isSheetConnected) {
                                    prefs.lastSyncMessage.ifBlank { prefs.appsScriptUrl }
                                } else {
                                    "Add your Apps Script Web App URL to back up & sync with Google Sheets"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSheetSyncDialog,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("configure_apps_script_button")
                        ) {
                            Text(if (prefs.isSheetConnected) "Edit URL" else "Add Script URL")
                        }
                        if (prefs.isSheetConnected) {
                            Button(
                                onClick = onSyncNow,
                                enabled = !uiState.syncState.isSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_sheet_now_button")
                            ) {
                                if (uiState.syncState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Syncing...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gemini AI Assistant Toggle Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_assist_settings_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Gemini AI Assistant",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Gemini Natural-Language Logging",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Type or record voice memos (item, cost & account like \"Chequera\") to auto-log expenses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = prefs.geminiAssistEnabled,
                        onCheckedChange = onGeminiAssistToggled,
                        modifier = Modifier.testTag("gemini_assist_switch")
                    )
                }
            }
        }

        // Automatic Bank Charge & SMS Notification Reader Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_message_reader_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MarkEmailRead,
                                contentDescription = "Automatic bank charge reader",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Auto-Read Bank Messages",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Detects charges from bank push & SMS notifications on-device (ignores OTP codes)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (uiState.hasNotificationListenerAccess) {
                                semanticColors.positiveContainer
                            } else {
                                semanticColors.warningContainer
                            }
                        ) {
                            Text(
                                text = if (uiState.hasNotificationListenerAccess) "Active" else "Off",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (uiState.hasNotificationListenerAccess) {
                                    semanticColors.positive
                                } else {
                                    semanticColors.warning
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenBankNotificationAccess,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp)
                                .testTag("bank_notification_access_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (uiState.hasNotificationListenerAccess) "Notification Access"
                                else "Grant Access"
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenBankMessageDialog,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("budget_test_bank_message_button")
                        ) {
                            Text("Paste / Test SMS")
                        }
                    }

                    HorizontalDivider(color = semanticColors.cardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Listen for Bank Charge Alerts",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Process incoming banking & card notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.bankDetectionEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateBankDetectionSettings(
                                    enabled,
                                    prefs.bankAutoLogEnabled,
                                    prefs.bankDefaultAccountName
                                )
                            },
                            modifier = Modifier.testTag("bank_detection_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Log Without Review",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Off = review queue on Today • On = save immediately",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.bankAutoLogEnabled,
                            enabled = prefs.bankDetectionEnabled,
                            onCheckedChange = { autoLog ->
                                onUpdateBankDetectionSettings(
                                    prefs.bankDetectionEnabled,
                                    autoLog,
                                    prefs.bankDefaultAccountName
                                )
                            },
                            modifier = Modifier.testTag("bank_autolog_switch")
                        )
                    }

                    if (uiState.accounts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Fallback Account (when card digits don't match an account)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = prefs.bankDefaultAccountName.isBlank(),
                                    onClick = {
                                        onUpdateBankDetectionSettings(
                                            prefs.bankDetectionEnabled,
                                            prefs.bankAutoLogEnabled,
                                            ""
                                        )
                                    },
                                    label = { Text("None / Ask") }
                                )
                                uiState.accounts.forEach { account ->
                                    FilterChip(
                                        selected = prefs.bankDefaultAccountName.equals(account.name, ignoreCase = true),
                                        onClick = {
                                            onUpdateBankDetectionSettings(
                                                prefs.bankDetectionEnabled,
                                                prefs.bankAutoLogEnabled,
                                                account.name
                                            )
                                        },
                                        label = { Text(account.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Home Screen Daily & Weekly Balance Widget Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_widget_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = "Home Screen Widget",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Balance Home Widget",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pin a live Daily & Weekly balance card to your home screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Live Widget Preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val dailyOver = summary.todayRemainingCents < 0L
                        val weeklyOver = summary.weeklyRemainingCents < 0L

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (dailyOver) "DAILY OVER" else "DAILY BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MoneyFormatter.format(abs(summary.todayRemainingCents), currency),
                                    style = AmountMediumStyle,
                                    color = if (dailyOver) semanticColors.danger else semanticColors.positive
                                )
                                Text(
                                    text = "Spent ${MoneyFormatter.format(summary.todaySpentCents, currency, compactWhole = true)} / ${MoneyFormatter.format(summary.dailyAllowanceCents, currency, compactWhole = true)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (weeklyOver) "WEEKLY OVER" else "WEEKLY BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MoneyFormatter.format(abs(summary.weeklyRemainingCents), currency),
                                    style = AmountMediumStyle,
                                    color = if (weeklyOver) semanticColors.danger else semanticColors.positive
                                )
                                Text(
                                    text = "7d ${MoneyFormatter.format(summary.weekSpentCents, currency, compactWhole = true)} / ${MoneyFormatter.format(summary.weeklyAllowanceCents, currency, compactWhole = true)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (SpendWiseBalanceWidgetProvider.canRequestPinWidget(context)) {
                                val requested = SpendWiseBalanceWidgetProvider.requestPinWidget(context)
                                if (requested) {
                                    SpendWiseBalanceWidgetProvider.refreshAll(context)
                                    onShowMessage("Confirm adding SpendWise Balance widget on your home screen")
                                } else {
                                    onShowMessage("Long-press your home screen -> Widgets -> SpendWise")
                                }
                            } else {
                                onShowMessage("Long-press your home screen -> Widgets -> SpendWise")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_home_widget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Widget to Home Screen")
                    }
                }
            }
        }

        // Category Monthly Limits Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Category Monthly Limits",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Visual progress & automatic alerts at 80% and 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.categoryBudgets.size < 3) {
                    TextButton(
                        onClick = onApplySuggestedBudgets,
                        modifier = Modifier.testTag("apply_starter_budgets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Starter Limits")
                    }
                }
            }
        }

        // Category Budget Cards
        items(summary.categoryProgress, key = { it.category.name }) { item ->
            CategoryBudgetProgressCard(
                item = item,
                currencySymbol = currency,
                onClick = { onEditCategoryLimit(item.category, item.limitCents) }
            )
        }

        // Notifications & Alerts Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Budget alerts",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Budget Limit Notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Alert when nearing (80%) or exceeding (100%) a limit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = prefs.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.hasNotificationPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onNotificationsToggled(enabled)
                            },
                            modifier = Modifier.testTag("budget_notifications_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.hasNotificationPermission) {
                            OutlinedButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Grant Permission")
                            }
                        }
                        OutlinedButton(
                            onClick = onSendTestNotification,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_notification_button")
                        ) {
                            Text("Send Test Alert")
                        }
                    }
                }
            }
        }

        // Currency & Local Data Export Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Currency & Local Export",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("$", "€", "£", "¥", "₹", "₱", "₩").forEach { sym ->
                            FilterChip(
                                selected = prefs.currencySymbol == sym,
                                onClick = { onCurrencyChanged(sym) },
                                label = { Text(sym) },
                                modifier = Modifier.testTag("currency_chip_$sym")
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            CsvExporter.shareExpensesCsv(context, uiState.expenses, currency)
                        },
                        enabled = uiState.expenses.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export ${uiState.expenses.size} Expenses to CSV")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetProgressCard(
    item: CategoryProgressItem,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val semanticColors = LocalSpendWiseColors.current
    val hasLimit = item.limitCents > 0L
    val statusColor = when (item.status) {
        BudgetStatusLevel.EXCEEDED -> semanticColors.danger
        BudgetStatusLevel.APPROACHING -> semanticColors.warning
        BudgetStatusLevel.ON_TRACK -> semanticColors.positive
    }
    val statusBg = when (item.status) {
        BudgetStatusLevel.EXCEEDED -> semanticColors.dangerContainer
        BudgetStatusLevel.APPROACHING -> semanticColors.warningContainer
        BudgetStatusLevel.ON_TRACK -> semanticColors.positiveContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("category_budget_card_${item.category.name.lowercase()}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasLimit && item.status != BudgetStatusLevel.ON_TRACK) {
                statusColor.copy(alpha = 0.45f)
            } else {
                semanticColors.cardBorder
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(item.category.accentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.category.icon,
                            contentDescription = item.category.displayName,
                            tint = item.category.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = item.category.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasLimit) {
                                "${MoneyFormatter.format(item.spentCents, currencySymbol)} of ${MoneyFormatter.format(item.limitCents, currencySymbol, compactWhole = true)}"
                            } else {
                                "Spent ${MoneyFormatter.format(item.spentCents, currencySymbol)} • No limit set"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasLimit) {
                    val pct = (item.budgetRatio * 100f).toInt()
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.status != BudgetStatusLevel.ON_TRACK) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = when (item.status) {
                                    BudgetStatusLevel.EXCEEDED -> "$pct% Over"
                                    BudgetStatusLevel.APPROACHING -> "$pct% Near"
                                    BudgetStatusLevel.ON_TRACK -> "$pct%"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = statusColor
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Set Limit",
                        style = AmountRowStyle.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (hasLimit) {
                LinearProgressIndicator(
                    progress = { item.budgetRatio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
