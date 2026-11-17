package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.AccountEntity
import com.example.data.AccountTransferEntity
import com.example.data.CommitmentEntity
import com.example.data.PointsAccountEntity
import com.example.model.CommitmentType
import com.example.model.ExpenseCategory
import com.example.notifications.BankNotificationAccessHelper
import com.example.ui.components.AccountEditorDialog
import com.example.ui.components.AccountTransferSheet
import com.example.ui.components.AddEditExpenseSheet
import com.example.ui.components.BankMessageTestDialog
import com.example.ui.components.CommitmentEditorSheet
import com.example.ui.components.EditCategoryBudgetDialog
import com.example.ui.components.EditMonthlyBudgetDialog
import com.example.ui.components.GoogleSheetsSyncDialog
import com.example.ui.components.PointsTrackerSheet
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.PlansScreen
import com.example.ui.screens.TodayScreen
import com.example.viewmodel.SpendWiseViewModel
import com.example.viewmodel.UiEffect

const val ROUTE_TODAY = "today"
const val ROUTE_ACTIVITY = "activity"
const val ROUTE_PLANS = "plans"
const val ROUTE_INSIGHTS = "insights"
const val ROUTE_BUDGET = "budget"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(
        route = ROUTE_TODAY,
        labelRes = R.string.nav_today,
        selectedIcon = Icons.Rounded.Today,
        unselectedIcon = Icons.Outlined.Today,
        testTag = "nav_today"
    ),
    NavDestination(
        route = ROUTE_ACTIVITY,
        labelRes = R.string.nav_activity,
        selectedIcon = Icons.Rounded.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        testTag = "nav_activity"
    ),
    NavDestination(
        route = ROUTE_PLANS,
        labelRes = R.string.nav_plans,
        selectedIcon = Icons.Rounded.EventRepeat,
        unselectedIcon = Icons.Outlined.EventRepeat,
        testTag = "nav_plans"
    ),
    NavDestination(
        route = ROUTE_INSIGHTS,
        labelRes = R.string.nav_insights,
        selectedIcon = Icons.Rounded.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_insights"
    ),
    NavDestination(
        route = ROUTE_BUDGET,
        labelRes = R.string.nav_budget,
        selectedIcon = Icons.Rounded.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_budget"
    )
)

@Composable
fun SpendWiseApp(
    viewModel: SpendWiseViewModel = viewModel(),
    openLogSignal: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_TODAY
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showMonthlyBudgetDialog by remember { mutableStateOf(false) }
    var showSheetSyncDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showTransferSheet by remember { mutableStateOf(false) }
    var editingTransfer by remember { mutableStateOf<AccountTransferEntity?>(null) }
    var transferInitialFrom by remember { mutableStateOf<String?>(null) }
    var showPointsSheet by remember { mutableStateOf(false) }
    var editingPointsAccount by remember { mutableStateOf<PointsAccountEntity?>(null) }
    var showCommitmentSheet by remember { mutableStateOf(false) }
    var editingCommitment by remember { mutableStateOf<CommitmentEntity?>(null) }
    var initialCommitmentType by remember { mutableStateOf(CommitmentType.DEBT) }
    var editingCategoryLimit by remember { mutableStateOf<Pair<ExpenseCategory, Long>?>(null) }
    var showBankMessageDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(openLogSignal) {
        if (openLogSignal > 0) {
            viewModel.openNewExpenseSheet()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = if (effect.showUndo) "Undo" else null,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed && effect.showUndo) {
                        viewModel.undoAction(effect.undoType)
                    }
                }
            }
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (currentRoute != ROUTE_BUDGET && currentRoute != ROUTE_PLANS) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openNewExpenseSheet() },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.action_log_expense)
                            )
                        },
                        text = { Text(stringResource(R.string.action_log_expense)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_log_expense")
                    )
                } else {
                    FloatingActionButton(
                        onClick = { viewModel.openNewExpenseSheet() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_log_expense")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.action_log_expense)
                        )
                    }
                }
            },
            bottomBar = {
                if (!isExpandedWidth) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedWidth) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TODAY,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable(ROUTE_TODAY) {
                            TodayScreen(
                                uiState = uiState,
                                onOpenNewExpense = { viewModel.openNewExpenseSheet() },
                                onEditExpense = { viewModel.openEditExpenseSheet(it) },
                                onDeleteExpense = { viewModel.deleteExpense(it) },
                                onQuickPreset = { viewModel.logQuickPreset(it) },
                                onDismissAlertBanner = { viewModel.dismissAlertBanner() },
                                onOpenBudgetTab = { navigateTo(ROUTE_BUDGET) },
                                onOpenPlansTab = { navigateTo(ROUTE_PLANS) },
                                onCreateCommitment = { type ->
                                    editingCommitment = null
                                    initialCommitmentType = type
                                    showCommitmentSheet = true
                                },
                                onRecordCommitmentPayment = viewModel::recordCommitmentPayment,
                                onEditMonthlyPlan = { showMonthlyBudgetDialog = true },
                                onOpenSheetSyncDialog = {
                                    viewModel.clearSyncTestFeedback()
                                    showSheetSyncDialog = true
                                },
                                onToggleDemoMode = viewModel::setDemoModeEnabled,
                                onSelectTodayAccount = viewModel::onTodayAccountSelected,
                                onCreateAccount = {
                                    editingAccount = null
                                    showAccountDialog = true
                                },
                                onEditAccount = { acct ->
                                    editingAccount = acct
                                    showAccountDialog = true
                                },
                                onTransferClick = {
                                    editingTransfer = null
                                    transferInitialFrom = uiState.selectedTodayAccount
                                    showTransferSheet = true
                                },
                                onEditTransfer = { transfer ->
                                    editingTransfer = transfer
                                    transferInitialFrom = null
                                    showTransferSheet = true
                                },
                                onDeleteTransfer = viewModel::deleteAccountTransfer,
                                onCreatePointsAccount = {
                                    editingPointsAccount = null
                                    showPointsSheet = true
                                },
                                onEditPointsAccount = { pts ->
                                    editingPointsAccount = pts
                                    showPointsSheet = true
                                },
                                onGeminiPromptChanged = viewModel::onGeminiPromptChanged,
                                onSubmitGeminiExpense = viewModel::submitNaturalLanguageExpense,
                                onStartVoiceRecording = viewModel::startVoiceRecording,
                                onStopVoiceRecording = viewModel::stopAndProcessVoiceRecording,
                                onCancelVoiceRecording = viewModel::cancelVoiceRecording,
                                onVoicePermissionDenied = viewModel::onVoicePermissionDenied,
                                onClearGeminiFeedback = viewModel::clearGeminiFeedback,
                                onApproveDetectedCharge = { viewModel.approveDetectedCharge(it) },
                                onEditDetectedCharge = viewModel::openDetectedChargeInEditor,
                                onDismissDetectedCharge = viewModel::dismissDetectedCharge,
                                onOpenBankMessageDialog = { showBankMessageDialog = true },
                                onPermissionResult = { viewModel.refreshNotificationPermission() }
                            )
                        }
                        composable(ROUTE_ACTIVITY) {
                            ActivityScreen(
                                uiState = uiState,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onCategoryFilterChange = viewModel::onCategoryFilterChange,
                                onAccountFilterChange = viewModel::onActivityAccountFilterChange,
                                onTimeframeChange = viewModel::onTimeframeChange,
                                onEditExpense = { viewModel.openEditExpenseSheet(it) },
                                onDeleteExpense = { viewModel.deleteExpense(it) },
                                onCreateTransfer = {
                                    editingTransfer = null
                                    transferInitialFrom = uiState.selectedActivityAccountFilter
                                    showTransferSheet = true
                                },
                                onEditTransfer = { transfer ->
                                    editingTransfer = transfer
                                    transferInitialFrom = null
                                    showTransferSheet = true
                                },
                                onDeleteTransfer = viewModel::deleteAccountTransfer
                            )
                        }
                        composable(ROUTE_PLANS) {
                            PlansScreen(
                                uiState = uiState,
                                onCreateCommitment = { type ->
                                    editingCommitment = null
                                    initialCommitmentType = type
                                    showCommitmentSheet = true
                                },
                                onEditCommitment = { commitment ->
                                    editingCommitment = commitment
                                    initialCommitmentType = CommitmentType.fromKey(commitment.type)
                                    showCommitmentSheet = true
                                },
                                onFilterChange = viewModel::onCommitmentFilterChange,
                                onRecordPayment = viewModel::recordCommitmentPayment,
                                onToggleActive = viewModel::toggleCommitmentActive
                            )
                        }
                        composable(ROUTE_INSIGHTS) {
                            InsightsScreen(uiState = uiState)
                        }
                        composable(ROUTE_BUDGET) {
                            BudgetScreen(
                                uiState = uiState,
                                onEditMonthlyPlan = { showMonthlyBudgetDialog = true },
                                onEditCategoryLimit = { cat, current ->
                                    editingCategoryLimit = cat to current
                                },
                                onApplySuggestedBudgets = { viewModel.applySuggestedBudgets() },
                                onCreateAccount = {
                                    editingAccount = null
                                    showAccountDialog = true
                                },
                                onEditAccount = { acct ->
                                    editingAccount = acct
                                    showAccountDialog = true
                                },
                                onTransferClick = {
                                    editingTransfer = null
                                    transferInitialFrom = null
                                    showTransferSheet = true
                                },
                                onCreatePointsAccount = {
                                    editingPointsAccount = null
                                    showPointsSheet = true
                                },
                                onEditPointsAccount = { pts ->
                                    editingPointsAccount = pts
                                    showPointsSheet = true
                                },
                                onOpenPlansTab = { navigateTo(ROUTE_PLANS) },
                                onCurrencyChanged = viewModel::updateCurrencySymbol,
                                onNotificationsToggled = viewModel::updateNotificationsEnabled,
                                onSendTestNotification = { viewModel.sendTestNotification() },
                                onToggleDemoMode = viewModel::setDemoModeEnabled,
                                onGeminiAssistToggled = viewModel::updateGeminiAssistEnabled,
                                onOpenBankNotificationAccess = {
                                    try {
                                        context.startActivity(BankNotificationAccessHelper.createSettingsIntent())
                                    } catch (_: Exception) {
                                        viewModel.showMessage("Open Android Settings -> Notifications -> Notification access -> SpendWise")
                                    }
                                },
                                onUpdateBankDetectionSettings = viewModel::updateBankDetectionSettings,
                                onOpenBankMessageDialog = { showBankMessageDialog = true },
                                onOpenSheetSyncDialog = {
                                    viewModel.clearSyncTestFeedback()
                                    showSheetSyncDialog = true
                                },
                                onSyncNow = { viewModel.syncWithGoogleSheetNow() },
                                onShowMessage = viewModel::showMessage,
                                onPermissionResult = { viewModel.refreshNotificationPermission() }
                            )
                        }
                    }
                }
            }
        }
    }

    AddEditExpenseSheet(
        state = editorState,
        availableAccounts = uiState.accounts,
        currencySymbol = uiState.preferences.currencySymbol,
        onDismiss = viewModel::closeExpenseSheet,
        onAmountChanged = viewModel::onEditorAmountChanged,
        onAddQuickDelta = viewModel::addQuickAmountDelta,
        onDescriptionChanged = viewModel::onEditorDescriptionChanged,
        onCategorySelected = viewModel::onEditorCategorySelected,
        onPaymentMethodSelected = viewModel::onEditorPaymentMethodSelected,
        onAccountNameChanged = viewModel::onEditorAccountNameChanged,
        onCreateAccountClick = {
            editingAccount = null
            showAccountDialog = true
        },
        onDateSelected = viewModel::onEditorDateSelected,
        onNoteChanged = viewModel::onEditorNoteChanged,
        onSave = viewModel::saveEditorExpense
    )

    if (showAccountDialog) {
        AccountEditorDialog(
            existingAccount = editingAccount,
            currencySymbol = uiState.preferences.currencySymbol,
            onDismiss = {
                showAccountDialog = false
                editingAccount = null
            },
            onSave = { existingId, name, initialBalanceCents ->
                viewModel.saveCustomAccount(existingId, name, initialBalanceCents)
            },
            onDelete = { acct ->
                viewModel.deleteCustomAccount(acct)
            }
        )
    }

    AccountTransferSheet(
        isOpen = showTransferSheet,
        existingTransfer = editingTransfer,
        initialFromAccount = transferInitialFrom,
        availableAccounts = uiState.accounts,
        currencySymbol = uiState.preferences.currencySymbol,
        onDismiss = {
            showTransferSheet = false
            editingTransfer = null
            transferInitialFrom = null
        },
        onSave = { existingId, fromName, toName, amountCents, timestampMillis, note ->
            viewModel.saveAccountTransfer(
                existingId = existingId,
                fromAccountName = fromName,
                toAccountName = toName,
                amountCents = amountCents,
                timestampMillis = timestampMillis,
                note = note
            )
        },
        onDelete = { transfer ->
            viewModel.deleteAccountTransfer(transfer)
        }
    )

    PointsTrackerSheet(
        isOpen = showPointsSheet,
        existingPointsAccount = editingPointsAccount,
        currencySymbol = uiState.preferences.currencySymbol,
        onDismiss = {
            showPointsSheet = false
            editingPointsAccount = null
        },
        onSave = { existingId, name, pointsBalance, unitLabel, centsPerPointTimes100, note ->
            viewModel.savePointsAccount(
                existingId = existingId,
                name = name,
                pointsBalance = pointsBalance,
                unitLabel = unitLabel,
                centsPerPointTimes100 = centsPerPointTimes100,
                note = note
            )
        },
        onDelete = { pts ->
            viewModel.deletePointsAccount(pts)
        }
    )

    CommitmentEditorSheet(
        isOpen = showCommitmentSheet,
        existingCommitment = editingCommitment,
        initialType = initialCommitmentType,
        availableAccounts = uiState.accounts,
        currencySymbol = uiState.preferences.currencySymbol,
        onDismiss = {
            showCommitmentSheet = false
            editingCommitment = null
        },
        onCreateAccountClick = {
            editingAccount = null
            showAccountDialog = true
        },
        onSave = { existingId, title, type, amountCents, targetCents, progressCents, cycle, dueDay, stmtDay, scheduledDate, totalInst, doneInst, acctName, category, note, reminder ->
            viewModel.saveCommitment(
                existingId = existingId,
                title = title,
                type = type,
                amountCents = amountCents,
                targetOrTotalCents = targetCents,
                progressCents = progressCents,
                cycle = cycle,
                dueDayOfMonth = dueDay,
                statementDayOfMonth = stmtDay,
                scheduledDateMillis = scheduledDate,
                totalInstallments = totalInst,
                completedInstallments = doneInst,
                accountName = acctName,
                category = category,
                note = note,
                reminderEnabled = reminder
            )
        },
        onDelete = { commitment ->
            viewModel.deleteCommitment(commitment)
        }
    )

    if (showMonthlyBudgetDialog) {
        EditMonthlyBudgetDialog(
            preferences = uiState.preferences,
            onDismiss = { showMonthlyBudgetDialog = false },
            onSave = { monthly, savings, mode, fixedDaily ->
                viewModel.updateBudgetSettings(monthly, savings, mode, fixedDaily)
            }
        )
    }

    if (showSheetSyncDialog) {
        GoogleSheetsSyncDialog(
            preferences = uiState.preferences,
            syncState = uiState.syncState,
            onDismiss = { showSheetSyncDialog = false },
            onTestConnection = viewModel::testAppsScriptConnection,
            onSaveAndSync = viewModel::saveAndSyncAppsScript
        )
    }

    editingCategoryLimit?.let { (category, limitCents) ->
        EditCategoryBudgetDialog(
            category = category,
            currentLimitCents = limitCents,
            currencySymbol = uiState.preferences.currencySymbol,
            onDismiss = { editingCategoryLimit = null },
            onSave = { newLimit ->
                viewModel.setCategoryBudget(category, newLimit)
            }
        )
    }

    if (showBankMessageDialog) {
        BankMessageTestDialog(
            onDismiss = { showBankMessageDialog = false },
            onParseMessage = viewModel::submitManualBankMessage
        )
    }
}
