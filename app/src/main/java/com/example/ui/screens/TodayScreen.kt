package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Loyalty
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.AccountDepositEntity
import com.example.data.AccountEntity
import com.example.data.AccountTransferEntity
import com.example.data.CommitmentEntity
import com.example.data.DetectedTransactionEntity
import com.example.data.ExpenseEntity
import com.example.data.PointsAccountEntity
import com.example.data.PointsFormatter
import com.example.model.CommitmentType
import com.example.model.DefaultQuickPresets
import com.example.model.ExpenseCategory
import com.example.model.QuickPreset
import com.example.ui.components.BudgetAlertBannerCard
import com.example.ui.components.DepositRowCard
import com.example.ui.components.ExpenseRowCard
import com.example.ui.components.TransferRowCard
import com.example.ui.theme.AmountDisplayStyle
import com.example.ui.theme.AmountMediumStyle
import com.example.ui.theme.AmountRowStyle
import com.example.ui.theme.LocalSpendWiseColors
import com.example.util.BudgetStatusLevel
import com.example.util.DateUtils
import com.example.util.MoneyFormatter
import com.example.viewmodel.SpendWiseUiState
import kotlin.math.abs

@Composable
fun TodayScreen(
    uiState: SpendWiseUiState,
    onOpenNewExpense: () -> Unit,
    onEditExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onQuickPreset: (QuickPreset) -> Unit,
    onDismissAlertBanner: () -> Unit,
    onOpenBudgetTab: () -> Unit,
    onOpenPlansTab: () -> Unit,
    onCreateCommitment: (CommitmentType) -> Unit,
    onRecordCommitmentPayment: (CommitmentEntity, Boolean) -> Unit,
    onOpenTodaySourceDialog: () -> Unit,
    onOpenSheetSyncDialog: () -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onSelectTodayAccount: (String?) -> Unit,
    onCreateAccount: () -> Unit,
    onEditAccount: (AccountEntity) -> Unit,
    onAddFundsClick: (AccountEntity?) -> Unit,
    onTransferClick: () -> Unit,
    onEditTransfer: (AccountTransferEntity) -> Unit,
    onDeleteTransfer: (AccountTransferEntity) -> Unit,
    onEditDeposit: (AccountDepositEntity) -> Unit,
    onDeleteDeposit: (AccountDepositEntity) -> Unit,
    onCreatePointsAccount: () -> Unit,
    onEditPointsAccount: (PointsAccountEntity) -> Unit,
    onGeminiPromptChanged: (String) -> Unit,
    onSubmitGeminiExpense: (String?) -> Unit,
    onStartVoiceRecording: () -> Unit,
    onStopVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onVoicePermissionDenied: () -> Unit,
    onClearGeminiFeedback: () -> Unit,
    onApproveDetectedCharge: (DetectedTransactionEntity) -> Unit,
    onEditDetectedCharge: (DetectedTransactionEntity) -> Unit,
    onDismissDetectedCharge: (DetectedTransactionEntity) -> Unit,
    onOpenBankMessageDialog: () -> Unit,
    onPermissionResult: () -> Unit,
    voiceTriggerSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semanticColors = LocalSpendWiseColors.current
    val prefs = uiState.preferences
    val currency = prefs.currencySymbol
    val summary = uiState.summary

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onPermissionResult()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceRecording()
        } else {
            onVoicePermissionDenied()
        }
    }

    LaunchedEffect(voiceTriggerSignal) {
        if (voiceTriggerSignal > 0 && prefs.geminiAssistEnabled) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                onStartVoiceRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onCancelVoiceRecording()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Sync / Demo quick chip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DateUtils.formatHeaderDate(System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Daily Ledger",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = onOpenSheetSyncDialog,
                        label = {
                            Text(
                                text = when {
                                    prefs.isDemoMode -> "Demo"
                                    prefs.isSheetConnected -> "Sheet"
                                    else -> "Connect Sheet"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when {
                                    prefs.isDemoMode -> Icons.Rounded.Science
                                    prefs.isSheetConnected -> Icons.Rounded.CloudDone
                                    else -> Icons.Rounded.TableChart
                                },
                                contentDescription = "Google Sheet sync status",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("today_sync_status_chip")
                    )

                    IconButton(
                        onClick = onOpenTodaySourceDialog,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("today_edit_plan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Configure Today balance source",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Demo Mode active indicator banner
        if (prefs.isDemoMode) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("demo_mode_banner"),
                    shape = MaterialTheme.shapes.medium,
                    color = semanticColors.warningContainer,
                    border = BorderStroke(1.dp, semanticColors.warning.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = "Demo Mode",
                            tint = semanticColors.warning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Demo Mode Active",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Exploring sample data. Connect a Google Sheet anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { onToggleDemoMode(false) },
                            modifier = Modifier.testTag("exit_demo_mode_button")
                        ) {
                            Text("Exit Demo")
                        }
                    }
                }
            }
        }

        // Daily Hero Card (Account Balances by default, or Monthly Budget if switched)
        item {
            if (prefs.todayUseAccountsBalance) {
                val selectedAcctSummary = uiState.selectedTodayAccount?.let { selectedName ->
                    summary.accountSummaries.firstOrNull {
                        it.account.name.equals(selectedName, ignoreCase = true)
                    }
                }

                val heroTopLabel: String
                val heroBadgeText: String
                val heroBadgeStatus: BudgetStatusLevel
                val heroAmountText: String
                val heroProgress: Float
                val heroHelperNote: String?
                val heroActionLabel: String?
                val heroActionClick: (() -> Unit)?
                val metric2Label: String
                val metric2Value: String
                val metric1Value: String

                if (selectedAcctSummary != null) {
                    if (selectedAcctSummary.hasBalanceOrTransferActivity) {
                        val acctBal = selectedAcctSummary.remainingBalanceCents
                        val isOverdrawn = acctBal < 0L
                        val dailyPace = selectedAcctSummary.dailyPaceCents(summary.daysRemainingInMonth)
                        val acctToday = selectedAcctSummary.todaySpentCents
                        val ratio = if (dailyPace > 0L) acctToday.toFloat() / dailyPace.toFloat() else 0f

                        heroBadgeStatus = when {
                            isOverdrawn -> BudgetStatusLevel.EXCEEDED
                            dailyPace > 0L && ratio >= 1.0f -> BudgetStatusLevel.EXCEEDED
                            dailyPace > 0L && ratio >= 0.8f -> BudgetStatusLevel.APPROACHING
                            dailyPace == 0L && acctToday > 0L -> BudgetStatusLevel.EXCEEDED
                            else -> BudgetStatusLevel.ON_TRACK
                        }
                        heroTopLabel = if (isOverdrawn) {
                            "${selectedAcctSummary.account.name} overdrawn by"
                        } else {
                            "${selectedAcctSummary.account.name} available"
                        }
                        val paceCompact = MoneyFormatter.format(dailyPace, currency, compactWhole = true)
                        heroBadgeText = when {
                            isOverdrawn -> "Overdrawn"
                            heroBadgeStatus == BudgetStatusLevel.EXCEEDED && dailyPace > 0L -> "Above $paceCompact/d pace"
                            heroBadgeStatus == BudgetStatusLevel.APPROACHING && dailyPace > 0L -> "Near $paceCompact/d pace"
                            dailyPace > 0L -> "~$paceCompact/day safe"
                            else -> "Account Balance"
                        }
                        heroAmountText = MoneyFormatter.format(abs(acctBal), currency)
                        heroProgress = if (isOverdrawn) 1f else ratio.coerceIn(0f, 1f)
                        heroHelperNote = null
                        heroActionLabel = null
                        heroActionClick = null
                        metric1Value = MoneyFormatter.format(acctToday, currency)
                        metric2Label = "Safe / Day"
                        metric2Value = MoneyFormatter.format(dailyPace, currency)
                    } else {
                        heroTopLabel = "${selectedAcctSummary.account.name} • No balance set"
                        heroBadgeStatus = BudgetStatusLevel.ON_TRACK
                        heroBadgeText = "Spend-only"
                        heroAmountText = MoneyFormatter.format(selectedAcctSummary.totalSpentCents, currency)
                        heroProgress = 0f
                        heroHelperNote = "Total spent on ${selectedAcctSummary.account.name}. Add a starting balance to track remaining money."
                        heroActionLabel = "Set Starting Balance"
                        heroActionClick = { onEditAccount(selectedAcctSummary.account) }
                        metric1Value = MoneyFormatter.format(selectedAcctSummary.todaySpentCents, currency)
                        metric2Label = "Month Spent"
                        metric2Value = MoneyFormatter.format(selectedAcctSummary.monthSpentCents, currency)
                    }
                } else if (summary.balanceTrackedAccountsCount > 0) {
                    val totalBal = summary.totalAccountsRemainingCents
                    val isOverdrawn = totalBal < 0L
                    val dailyPace = summary.accountDailyAllowanceCents
                    val paceCompact = MoneyFormatter.format(dailyPace, currency, compactWhole = true)

                    heroBadgeStatus = summary.accountPaceStatus
                    heroTopLabel = if (isOverdrawn) {
                        "Accounts overdrawn by"
                    } else {
                        "Available across accounts"
                    }
                    heroBadgeText = when {
                        isOverdrawn -> "Overdrawn"
                        heroBadgeStatus == BudgetStatusLevel.EXCEEDED && dailyPace > 0L -> "Above $paceCompact/d pace"
                        heroBadgeStatus == BudgetStatusLevel.APPROACHING && dailyPace > 0L -> "Near $paceCompact/d pace"
                        dailyPace > 0L -> "~$paceCompact/day safe"
                        else -> "${summary.balanceTrackedAccountsCount} account(s)"
                    }
                    heroAmountText = MoneyFormatter.format(abs(totalBal), currency)
                    heroProgress = when {
                        isOverdrawn -> 1f
                        dailyPace > 0L -> (summary.todayAccountLinkedSpentCents.toFloat() / dailyPace.toFloat()).coerceIn(0f, 1f)
                        summary.todayAccountLinkedSpentCents > 0L -> 1f
                        else -> 0f
                    }
                    heroHelperNote = null
                    heroActionLabel = null
                    heroActionClick = null
                    metric1Value = MoneyFormatter.format(summary.todaySpentCents, currency)
                    metric2Label = "Safe / Day"
                    metric2Value = MoneyFormatter.format(dailyPace, currency)
                } else {
                    heroTopLabel = "Money in Accounts"
                    heroBadgeStatus = BudgetStatusLevel.ON_TRACK
                    heroBadgeText = "Add Balance"
                    heroAmountText = MoneyFormatter.format(0L, currency)
                    heroProgress = 0f
                    val firstAccount = summary.accountSummaries.firstOrNull()?.account
                    heroHelperNote = if (firstAccount == null) {
                        "Add an account with your current money so Today shows your available balance."
                    } else {
                        "Add a starting balance to ${firstAccount.name} or another account to track available money here."
                    }
                    heroActionLabel = if (firstAccount == null) "Add Account Balance" else "Set Account Balance"
                    heroActionClick = if (firstAccount == null) {
                        onCreateAccount
                    } else {
                        { onEditAccount(firstAccount) }
                    }
                    metric1Value = MoneyFormatter.format(summary.todaySpentCents, currency)
                    metric2Label = "Month Spent"
                    metric2Value = MoneyFormatter.format(summary.monthSpentCents, currency)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_allowance_hero_card"),
                    shape = MaterialTheme.shapes.extraLarge,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(semanticColors.heroStart, semanticColors.heroEnd)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = heroTopLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White.copy(alpha = 0.86f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = when (heroBadgeStatus) {
                                        BudgetStatusLevel.ON_TRACK -> Color.White.copy(alpha = 0.18f)
                                        BudgetStatusLevel.APPROACHING -> semanticColors.warningContainer
                                        BudgetStatusLevel.EXCEEDED -> semanticColors.dangerContainer
                                    }
                                ) {
                                    Text(
                                        text = heroBadgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = when (heroBadgeStatus) {
                                            BudgetStatusLevel.ON_TRACK -> Color.White
                                            BudgetStatusLevel.APPROACHING -> semanticColors.warning
                                            BudgetStatusLevel.EXCEEDED -> semanticColors.danger
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = heroAmountText,
                                style = AmountDisplayStyle,
                                color = Color.White
                            )

                            if (heroHelperNote != null && heroActionLabel != null && heroActionClick != null) {
                                Text(
                                    text = heroHelperNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.86f)
                                )
                                Button(
                                    onClick = heroActionClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = semanticColors.heroStart
                                    ),
                                    modifier = Modifier
                                        .height(44.dp)
                                        .testTag("today_hero_add_account_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(heroActionLabel)
                                }
                            } else {
                                LinearProgressIndicator(
                                    progress = { heroProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = when (heroBadgeStatus) {
                                        BudgetStatusLevel.ON_TRACK -> Color.White
                                        BudgetStatusLevel.APPROACHING -> semanticColors.warningContainer
                                        BudgetStatusLevel.EXCEEDED -> semanticColors.dangerContainer
                                    },
                                    trackColor = Color.White.copy(alpha = 0.22f),
                                    strokeCap = StrokeCap.Round
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                HeroMetricItem(
                                    label = "Spent Today",
                                    value = metric1Value
                                )
                                HeroMetricItem(
                                    label = metric2Label,
                                    value = metric2Value
                                )
                                HeroMetricItem(
                                    label = "Days Left",
                                    value = "${summary.daysRemainingInMonth}d"
                                )
                            }
                        }
                    }
                }
            } else {
                val isOver = summary.todayRemainingCents < 0L
                val paceLabel = when (summary.paceStatus) {
                    BudgetStatusLevel.ON_TRACK -> "On Pace"
                    BudgetStatusLevel.APPROACHING -> "Close to Daily Limit"
                    BudgetStatusLevel.EXCEEDED -> "Over Daily Pace"
                }
                val progress = if (summary.dailyAllowanceCents > 0L) {
                    (summary.todaySpentCents.toFloat() / summary.dailyAllowanceCents.toFloat()).coerceIn(0f, 1f)
                } else {
                    if (summary.todaySpentCents > 0L) 1f else 0f
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_allowance_hero_card"),
                    shape = MaterialTheme.shapes.extraLarge,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(semanticColors.heroStart, semanticColors.heroEnd)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isOver) "Over today's allowance by" else "Safe to spend today",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White.copy(alpha = 0.86f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = when (summary.paceStatus) {
                                        BudgetStatusLevel.ON_TRACK -> Color.White.copy(alpha = 0.18f)
                                        BudgetStatusLevel.APPROACHING -> semanticColors.warningContainer
                                        BudgetStatusLevel.EXCEEDED -> semanticColors.dangerContainer
                                    }
                                ) {
                                    Text(
                                        text = paceLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = when (summary.paceStatus) {
                                            BudgetStatusLevel.ON_TRACK -> Color.White
                                            BudgetStatusLevel.APPROACHING -> semanticColors.warning
                                            BudgetStatusLevel.EXCEEDED -> semanticColors.danger
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = MoneyFormatter.format(
                                    cents = abs(summary.todayRemainingCents),
                                    currencySymbol = currency
                                ),
                                style = AmountDisplayStyle,
                                color = Color.White
                            )

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when (summary.paceStatus) {
                                    BudgetStatusLevel.ON_TRACK -> Color.White
                                    BudgetStatusLevel.APPROACHING -> semanticColors.warningContainer
                                    BudgetStatusLevel.EXCEEDED -> semanticColors.dangerContainer
                                },
                                trackColor = Color.White.copy(alpha = 0.22f),
                                strokeCap = StrokeCap.Round
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                HeroMetricItem(
                                    label = "Spent Today",
                                    value = MoneyFormatter.format(summary.todaySpentCents, currency)
                                )
                                HeroMetricItem(
                                    label = if (summary.weeklyRemainingCents < 0L) "Week Over" else "Week Balance",
                                    value = MoneyFormatter.format(abs(summary.weeklyRemainingCents), currency)
                                )
                                HeroMetricItem(
                                    label = "Days Left",
                                    value = "${summary.daysRemainingInMonth}d"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Separate Accounts (100% customizable, zero preset options)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Separate Accounts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onCreateAccount,
                        modifier = Modifier.testTag("today_add_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            val selectedAcct = uiState.selectedTodayAccount?.let { sel ->
                                uiState.accounts.firstOrNull { it.name.equals(sel, ignoreCase = true) }
                            }
                            onAddFundsClick(selectedAcct)
                        },
                        label = { Text("Add funds") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AddCircleOutline,
                                contentDescription = null,
                                tint = semanticColors.positive,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("today_add_funds_button")
                    )
                    AssistChip(
                        onClick = onTransferClick,
                        label = { Text("Transfer") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("today_transfer_button")
                    )
                }

                if (summary.accountSummaries.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("today_no_accounts_card"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semanticColors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "100% custom accounts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "No presets — create your own separate accounts with custom names & optional balances.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = onCreateAccount,
                                modifier = Modifier.testTag("today_create_account_button")
                            ) {
                                Text("Create")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("today_accounts_strip"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val allSelected = uiState.selectedTodayAccount == null
                        Surface(
                            onClick = { onSelectTodayAccount(null) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (allSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (allSelected) 1.5.dp else 1.dp,
                                color = if (allSelected) MaterialTheme.colorScheme.primary else semanticColors.cardBorder
                            ),
                            modifier = Modifier
                                .widthIn(min = 124.dp)
                                .testTag("today_account_card_all")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "All Accounts",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (summary.balanceTrackedAccountsCount > 0) {
                                    val isNegAll = summary.totalAccountsRemainingCents < 0L
                                    Text(
                                        text = if (isNegAll) {
                                            "-${MoneyFormatter.format(abs(summary.totalAccountsRemainingCents), currency)}"
                                        } else {
                                            MoneyFormatter.format(summary.totalAccountsRemainingCents, currency)
                                        },
                                        style = AmountRowStyle,
                                        color = if (isNegAll) semanticColors.danger else semanticColors.positive
                                    )
                                    Text(
                                        text = "Today ${MoneyFormatter.format(summary.todaySpentCents, currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = MoneyFormatter.format(summary.todaySpentCents, currency),
                                        style = AmountRowStyle,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Today total",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        summary.accountSummaries.forEach { item ->
                            val isSelected = uiState.selectedTodayAccount.equals(item.account.name, ignoreCase = true)
                            Surface(
                                onClick = { onSelectTodayAccount(item.account.name) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else semanticColors.cardBorder
                                ),
                                modifier = Modifier
                                    .widthIn(min = 156.dp, max = 210.dp)
                                    .testTag("today_account_card_${item.account.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.account.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onEditAccount(item.account) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("edit_account_${item.account.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Edit ${item.account.name}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

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
                                    } else {
                                        Text(
                                            text = MoneyFormatter.format(item.totalSpentCents, currency),
                                            style = AmountRowStyle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = "Today ${MoneyFormatter.format(item.todaySpentCents, currency)} • ${item.transactionCount} tx",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.addedFundsCents > 0L) {
                                        Text(
                                            text = "Funds +${MoneyFormatter.format(item.addedFundsCents, currency, compactWhole = true)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semanticColors.positive,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (item.transferredInCents > 0L || item.transferredOutCents > 0L) {
                                        val transferParts = buildList {
                                            if (item.transferredInCents > 0L) {
                                                add("In +${MoneyFormatter.format(item.transferredInCents, currency, compactWhole = true)}")
                                            }
                                            if (item.transferredOutCents > 0L) {
                                                add("Out -${MoneyFormatter.format(item.transferredOutCents, currency, compactWhole = true)}")
                                            }
                                        }
                                        Text(
                                            text = transferParts.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { onAddFundsClick(item.account) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("today_add_funds_account_${item.account.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AddCircleOutline,
                                            contentDescription = "Add funds to ${item.account.name}",
                                            tint = semanticColors.positive,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Add funds",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semanticColors.positive
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Points & Rewards Quick View (manual quick check, does not affect cash balances)
        item {
            Column(
                modifier = Modifier.testTag("today_points_section"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Loyalty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (uiState.pointsAccounts.isNotEmpty()) {
                                    val totalPts = uiState.pointsAccounts.sumOf { it.pointsBalance }
                                    "Points & Rewards (${PointsFormatter.formatCount(totalPts)})"
                                } else {
                                    "Points & Rewards"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Quick view only • doesn't affect balances",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(
                        onClick = onCreatePointsAccount,
                        modifier = Modifier.testTag("today_add_points_button")
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("today_no_points_card"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semanticColors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Track points in one glance",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Save card, airline, or store point totals here so you don't need to open other apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = onCreatePointsAccount,
                                modifier = Modifier.testTag("today_create_points_button")
                            ) {
                                Text("Add")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("today_points_strip"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.pointsAccounts.forEach { pts ->
                            Surface(
                                onClick = { onEditPointsAccount(pts) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, semanticColors.cardBorder),
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 212.dp)
                                    .testTag("today_points_card_${pts.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = pts.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onEditPointsAccount(pts) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("edit_points_${pts.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Update ${pts.name}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = PointsFormatter.formatWithUnit(pts.pointsBalance, pts.unitLabel),
                                        style = AmountRowStyle,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val subLine = buildString {
                                        if (pts.estimatedValueCents > 0L) {
                                            append("≈ ${MoneyFormatter.format(pts.estimatedValueCents, currency)}")
                                            append(" • ")
                                        }
                                        append(PointsFormatter.formatFreshness(pts.updatedAtMillis))
                                    }
                                    Text(
                                        text = subLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pts.note.isNotBlank()) {
                                        Text(
                                            text = pts.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Debts, Credit Cards, Subscriptions, Goals & More (always visible & 1-tap accessible)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today_upcoming_commitments_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semanticColors.cardBorder)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EventRepeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (uiState.upcomingCommitments.isNotEmpty()) {
                                    "Upcoming Plans (${uiState.upcomingCommitments.size})"
                                } else if (uiState.commitments.isNotEmpty()) {
                                    "Plans & Schedules (${uiState.commitments.size})"
                                } else {
                                    "Debts, Cards, Subscriptions & Goals"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = onOpenPlansTab,
                            modifier = Modifier.testTag("today_view_all_plans_button")
                        ) {
                            Text("Open Plans")
                        }
                    }

                    if (uiState.upcomingCommitments.isNotEmpty()) {
                        uiState.upcomingCommitments.take(3).forEach { item ->
                            val c = item.commitment
                            val type = item.commitmentType
                            val status = item.status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = type.singularTitle,
                                    tint = type.accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${c.title} • ${MoneyFormatter.format(c.amountCents, currency)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(type.singularTitle)
                                            append(" • ")
                                            append(status.statusLabel)
                                            if (c.accountName.isNotBlank()) {
                                                append(" • ${c.accountName}")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (status.isOverdue) semanticColors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onRecordCommitmentPayment(c, type.logsAsExpenseOnPay) },
                                    modifier = Modifier.testTag("today_pay_commitment_${c.id}")
                                ) {
                                    Text(type.actionLabel)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = if (uiState.commitments.isEmpty()) {
                                "Track payment days for debts & credit cards, subscription charges, and goal dates:"
                            } else {
                                "No payments due in the next 7 days. Tap below to add another plan:"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CommitmentType.entries.forEach { type ->
                            Surface(
                                onClick = { onCreateCommitment(type) },
                                shape = RoundedCornerShape(10.dp),
                                color = type.accentColor.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, type.accentColor.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("today_add_plan_${type.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        tint = type.accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "+ ${type.singularTitle}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Detected Bank Charges Queue (from Notification Listener or Pasted Bank SMS)
        if (uiState.pendingDetectedCharges.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_detected_bank_charges_card"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MarkEmailRead,
                                    contentDescription = "Detected bank charges",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Detected Bank Charges (${uiState.pendingDetectedCharges.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = onOpenBankMessageDialog,
                                modifier = Modifier.testTag("today_paste_bank_message_button")
                            ) {
                                Text("Paste SMS")
                            }
                        }

                        uiState.pendingDetectedCharges.forEach { item ->
                            val category = ExpenseCategory.fromKey(item.suggestedCategory)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("detected_charge_row_${item.id}"),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, semanticColors.cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.merchant,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    append(item.sourceAppLabel)
                                                    if (item.cardLastFour.isNotBlank()) {
                                                        append(" • •••• ${item.cardLastFour}")
                                                    }
                                                    append(" • ${category.displayName}")
                                                    if (item.matchedAccountName.isNotBlank()) {
                                                        append(" • ${item.matchedAccountName}")
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
                                            text = MoneyFormatter.format(item.amountCents, currency),
                                            style = AmountMediumStyle,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onApproveDetectedCharge(item) },
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(48.dp)
                                                .testTag("approve_detected_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Log")
                                        }
                                        OutlinedButton(
                                            onClick = { onEditDetectedCharge(item) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("edit_detected_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                        IconButton(
                                            onClick = { onDismissDetectedCharge(item) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("dismiss_detected_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Dismiss detected charge",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Just-triggered alert banner
        if (uiState.latestAlertBanner != null) {
            item {
                BudgetAlertBannerCard(
                    alert = uiState.latestAlertBanner,
                    onDismiss = onDismissAlertBanner
                )
            }
        }

        // Notification permission prompt on Android 13+ if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !uiState.hasNotificationPermission &&
            prefs.notificationsEnabled
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Get budget limit alerts",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Allow notifications so SpendWise warns you at 80% and 100% of any category limit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.testTag("enable_notifications_button")
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        // Ongoing category alerts summary if any category >= 80%
        if (summary.activeBudgetAlerts.isNotEmpty()) {
            item {
                val top = summary.activeBudgetAlerts.first()
                val isOver = top.status == BudgetStatusLevel.EXCEEDED
                val bg = if (isOver) semanticColors.dangerContainer else semanticColors.warningContainer
                val fg = if (isOver) semanticColors.danger else semanticColors.warning
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = bg,
                    border = BorderStroke(1.dp, fg.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Category budget alert",
                            tint = fg
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val countExtra = if (summary.activeBudgetAlerts.size > 1) {
                                " (+${summary.activeBudgetAlerts.size - 1} more)"
                            } else ""
                            Text(
                                text = "${top.category.displayName} at ${(top.budgetRatio * 100).toInt()}%$countExtra",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${MoneyFormatter.format(top.spentCents, currency)} of ${MoneyFormatter.format(top.limitCents, currency)} monthly limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onOpenBudgetTab,
                            modifier = Modifier.testTag("view_budget_alerts_button")
                        ) {
                            Text("Budgets")
                        }
                    }
                }
            }
        }

        // Optional Gemini Natural-Language Expense Logger
        if (prefs.geminiAssistEnabled) {
            item {
                val gemini = uiState.geminiState
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_quick_log_card"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Gemini AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ask Gemini to Log",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Type or tap the mic — include item, cost & account",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (gemini.isRecording) {
                            val pulseScale by animateFloatAsState(
                                targetValue = 1f + gemini.audioAmplitudeNormalized * 0.32f,
                                label = "mic_pulse"
                            )
                            val mins = gemini.recordingSeconds / 60
                            val secs = gemini.recordingSeconds % 60
                            val timerLabel = "$mins:${secs.toString().padStart(2, '0')}"

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_recording_bar"),
                                shape = MaterialTheme.shapes.medium,
                                color = semanticColors.dangerContainer,
                                border = BorderStroke(1.dp, semanticColors.danger.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .scale(pulseScale)
                                                .clip(CircleShape)
                                                .background(semanticColors.danger.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Mic,
                                                contentDescription = "Recording audio",
                                                tint = semanticColors.danger,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Listening... $timerLabel",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Say e.g. \"Bought a churro for 0.50 on account chequera\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onCancelVoiceRecording,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("gemini_cancel_recording_button")
                                        ) {
                                            Text("Cancel")
                                        }
                                        Button(
                                            onClick = onStopVoiceRecording,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = semanticColors.danger,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .weight(1.4f)
                                                .height(48.dp)
                                                .testTag("gemini_stop_recording_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Stop,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Stop & Log")
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = gemini.promptText,
                                    onValueChange = onGeminiPromptChanged,
                                    enabled = !gemini.isParsing,
                                    placeholder = {
                                        Text("e.g., Bought a churro for 0.50 on account chequera")
                                    },
                                    singleLine = true,
                                    maxLines = 1,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = { onSubmitGeminiExpense(null) }
                                    ),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("gemini_expense_input")
                                )

                                FilledTonalIconButton(
                                    onClick = {
                                        val hasMicPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasMicPermission) {
                                            onStartVoiceRecording()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    enabled = !gemini.isParsing,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("gemini_mic_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Record expense by voice",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Button(
                                    onClick = { onSubmitGeminiExpense(null) },
                                    enabled = !gemini.isParsing && gemini.promptText.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("gemini_submit_button")
                                ) {
                                    if (gemini.isParsing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "Log expense with Gemini",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = gemini.isParsing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = gemini.parsingStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    onGeminiPromptChanged(
                                        "I just bought a churro from my local store, it cost me 0.50 cents, log it on account chequera"
                                    )
                                },
                                enabled = !gemini.isParsing && !gemini.isRecording,
                                label = {
                                    Text(
                                        text = "Try: \"Churro 0.50 on account chequera\"",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                modifier = Modifier.testTag("gemini_example_chip")
                            )
                            AssistChip(
                                onClick = onOpenBankMessageDialog,
                                enabled = !gemini.isParsing && !gemini.isRecording,
                                label = {
                                    Text(
                                        text = if (uiState.hasNotificationListenerAccess) {
                                            "Bank Reader: On • Paste SMS"
                                        } else {
                                            "Read / Paste Bank SMS"
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.MarkEmailRead,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                modifier = Modifier.testTag("today_bank_reader_chip")
                            )
                        }

                        AnimatedVisibility(visible = gemini.lastLoggedSummary != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_success_banner"),
                                shape = MaterialTheme.shapes.small,
                                color = semanticColors.positiveContainer,
                                border = BorderStroke(1.dp, semanticColors.positive.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Logged",
                                        tint = semanticColors.positive,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = gemini.lastLoggedSummary.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = onClearGeminiFeedback,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Dismiss Gemini confirmation",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = gemini.errorMessage != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_error_banner"),
                                shape = MaterialTheme.shapes.small,
                                color = semanticColors.dangerContainer,
                                border = BorderStroke(1.dp, semanticColors.danger.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = "Gemini error",
                                        tint = semanticColors.danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = gemini.errorMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semanticColors.danger,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = onClearGeminiFeedback,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Dismiss error",
                                            tint = semanticColors.danger,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick-Log Presets
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Quick Log",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefaultQuickPresets.forEach { preset ->
                        Surface(
                            onClick = { onQuickPreset(preset) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semanticColors.cardBorder),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("quick_preset_${preset.label.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(preset.category.accentColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = preset.category.icon,
                                        contentDescription = null,
                                        tint = preset.category.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = MoneyFormatter.format(preset.amountCents, currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today's Expenses Section Header
        item {
            val activeAccountLabel = uiState.selectedTodayAccount
            val filteredTodayTotal = uiState.todayExpenses.sumOf { it.amountCents }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (activeAccountLabel != null) {
                            "$activeAccountLabel • Today (${uiState.todayExpenses.size})"
                        } else {
                            "Today's Expenses (${uiState.todayExpenses.size})"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeAccountLabel != null) {
                        TextButton(onClick = { onSelectTodayAccount(null) }) {
                            Text("All")
                        }
                    }
                }
                if (uiState.todayExpenses.isNotEmpty()) {
                    Text(
                        text = MoneyFormatter.format(filteredTodayTotal, currency),
                        style = AmountMediumStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Today's Expenses List or Empty State
        if (uiState.todayExpenses.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_empty_state"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_wallet_empty),
                            contentDescription = stringResource(R.string.cd_empty_wallet),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        val activeAcct = uiState.selectedTodayAccount
                        Text(
                            text = if (activeAcct != null) {
                                "No expenses in $activeAcct today"
                            } else {
                                "Clean slate for today"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activeAcct != null) {
                                "Log an expense for $activeAcct, or switch back to view all accounts."
                            } else {
                                "Log a purchase, explore Demo Mode with sample data, or connect your Google Sheet via Apps Script."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (activeAcct != null) {
                                OutlinedButton(
                                    onClick = { onSelectTodayAccount(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("show_all_accounts_button")
                                ) {
                                    Text("Show All")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onToggleDemoMode(true) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("try_demo_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayCircleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Demo Mode")
                                }
                            }
                            Button(
                                onClick = onOpenNewExpense,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp)
                                    .testTag("empty_log_expense_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Expense")
                            }
                        }

                        if (!prefs.isSheetConnected) {
                            TextButton(
                                onClick = onOpenSheetSyncDialog,
                                modifier = Modifier.testTag("empty_connect_sheet_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TableChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect Google Sheet (Apps Script)")
                            }
                        }
                    }
                }
            }
        } else {
            items(uiState.todayExpenses, key = { it.id }) { expense ->
                ExpenseRowCard(
                    expense = expense,
                    currencySymbol = currency,
                    onClick = { onEditExpense(expense) },
                    onDelete = { onDeleteExpense(expense) }
                )
            }
        }

        // Today's Internal Transfers (never counted as expenses)
        if (uiState.todayTransfers.isNotEmpty()) {
            item {
                val transfersTotal = uiState.todayTransfers.sumOf { it.amountCents }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Today's Transfers (${uiState.todayTransfers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Internal moves between accounts (not counted as spending)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = MoneyFormatter.format(transfersTotal, currency),
                        style = AmountRowStyle,
                        color = semanticColors.positive
                    )
                }
            }

            items(uiState.todayTransfers, key = { "today_transfer_${it.id}" }) { transfer ->
                TransferRowCard(
                    transfer = transfer,
                    currencySymbol = currency,
                    onClick = { onEditTransfer(transfer) },
                    onDelete = { onDeleteTransfer(transfer) }
                )
            }
        }

        // Today's Added Funds (never counted as expenses)
        if (uiState.todayDeposits.isNotEmpty()) {
            item {
                val depositsTotal = uiState.todayDeposits.sumOf { it.amountCents }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Today's Added Funds (${uiState.todayDeposits.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Money received into accounts (not counted as spending)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "+${MoneyFormatter.format(depositsTotal, currency)}",
                        style = AmountRowStyle,
                        color = semanticColors.positive
                    )
                }
            }

            items(uiState.todayDeposits, key = { "today_deposit_${it.id}" }) { deposit ->
                DepositRowCard(
                    deposit = deposit,
                    currencySymbol = currency,
                    onClick = { onEditDeposit(deposit) },
                    onDelete = { onDeleteDeposit(deposit) }
                )
            }
        }
    }
}

@Composable
private fun HeroMetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.74f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}
