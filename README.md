# SpendWise — Daily Expense Helper & Budget Assistant

**SpendWise** is a modern Android financial helper app built with **Kotlin**, **Jetpack Compose**, **Material Design 3**, **Room**, and **DataStore**. It helps you track daily expenses effortlessly, automatically categorizes transactions, watches your monthly category budgets with real system notifications, and optionally syncs directly to your own **Google Sheet** via **Google Apps Script**.

---

## Key Features

### 1. Daily Safe-to-Spend Ledger & Separate Custom Accounts (`Today`)
- Calculates your **daily spending allowance** either dynamically from your remaining monthly budget & days left (*Auto-Paced Daily*) or from a *Fixed Daily Cap*.
- **100% Custom Separate Accounts & Internal Transfers:** Create, rename, and manage completely custom accounts (with optional starting balances and zero preset options), transfer money between accounts without double-counting expenses, filter Today's ledger per account, or auto-register new accounts when logging via form, voice, or Google Sheets.
- **Points & Rewards Quick View:** Track manual points, miles, or store rewards balances on **Today**, **Budget & Sync**, and the **Home Screen Widget** for fast at-a-glance checks without affecting cash balances or budgets.
- Shows live pace status (**On Pace**, **Close to Daily Limit**, **Over Daily Pace**) and includes **Quick Log** presets (*Coffee*, *Lunch*, *Transit*, *Groceries*) with full edit/delete and **Undo**.

### 2. Automatic Expense Categorization
- Analyzes **transaction descriptions**, **amounts**, and **your past expense history** locally in `ExpenseCategorizer`.
- Supports 9 categories: `Groceries`, `Food & Dining`, `Housing`, `Utilities`, `Transportation`, `Entertainment`, `Shopping`, `Health`, and `Other`.
- **Smart Uncertainty Prompt:** If a merchant is ambiguous (e.g., *Target*, *Walmart*) or unrecognized, SpendWise highlights top suggested categories and prompts you to confirm before saving—learning your choice for future entries.

### 3. Budget Management & Threshold Alerts (`Budget & Sync`)
- Set an overall monthly budget, savings goal, preferred currency symbol, and individual **monthly spending limits** per category.
- Visualizes progress with color-coded bars (on track, `80%+` approaching warning, `100%+` exceeded).
- Fires **Android notifications** (and in-app alert banners) when spending hits **80%** or **100%** of any category or overall budget limit.

### 4. Activity Search & Insights (`Activity` & `Insights`)
- Filter expenses by keyword, category, and timeframe (*Today*, *7 Days*, *This Month*, *All Time*), grouped by day.
- Interactive **Past 30 Days** Jetpack Compose `Canvas` line & area graph with touch/drag scrubbing, daily average & target reference lines, per-account filtering, peak-day & no-spend pattern metrics, plus a 7-day spending bar chart, month-end projection, under-limit streak, and deterministic financial coaching tips.
- Export all expenses to CSV via the Android share sheet.

### 5. Debts, Credit Cards, Subscriptions, Goals & More (`Plans`)
- Track **Debts** (monthly/recurring payment day, optional total owed & payoff progress bar).
- Track **Credit Cards** (payment due day, optional statement closing day, and optional credit limit).
- Track **Subscriptions** (when each service is charged — monthly, weekly, every 2 weeks, or yearly).
- Track **Goals** (target amount, saved progress, and scheduled charge / target contribution date).
- Plus **Bills & Rent**, **Installments** (e.g., 3 of 12 paid), and **Paydays / Income** for full upcoming cashflow visibility.
- Upcoming items due within 7 days appear right on **Today**, with one-tap **Pay & Log** / **Contribute** actions that link to your 100% custom accounts and advance to the next billing cycle.

### 6. Home Screen Balance Widget
- Add a resizable **Daily & Weekly Balance** widget directly from **Budget & Sync → Add Widget to Home Screen** (or from your launcher's widget picker).
- Displays both today's remaining allowance and your 7-day rolling weekly balance, updates automatically on every expense change, and includes a **`+ Log`** shortcut straight to the expense sheet.

### 7. Optional Gemini Text & Voice Expense Logging
- Powered by **Firebase AI Logic** (`gemini-3.8-flash`) with **Firebase App Check** protection (no API keys required in source code).
- **Type or Speak:** From the **Today** screen, type naturally or tap the **microphone** button to record your expense by voice — e.g., *"I just bought a churro from my local store, it cost me 0.50 cents, log it on account chequera"*.
- Gemini processes the text or audio recording, extracts the item, amount, category, payment method, and account (`Chequera`), and logs it automatically (or opens a prefilled confirmation sheet if anything needs clarification).
- Toggle **Gemini Natural-Language Logging** on or off anytime under **Budget & Sync**.

### 8. Demo Mode & Zero-Backend Google Sheets Sync
- **Demo Mode:** Toggle **Demo Mode** from the Today empty state or **Budget & Sync** tab to populate 7 days of realistic sample expenses and budget alerts—without touching your real data or Google Sheet.
- **Google Sheets (Apps Script) Sync:** Paste any deployed Google Apps Script Web App URL directly inside the app to sync expenses (including `Account`), and category budgets two-way with your own Google Sheet.

### 9. Automatic Bank Charge Reader (Play-Compliant Notification Access)
- Uses Android's standard `NotificationListenerService` (configured under **Budget & Sync → Auto-Read Bank Messages → Grant Access**) so SpendWise can read incoming bank app push alerts and SMS charge notifications on-device—without requiring Google Play–restricted `READ_SMS` permissions.
- Automatically extracts the **amount**, **merchant**, **card last 4 digits**, **payment method**, and **category** in English & Spanish, deduplicates double alerts within 5 minutes, and filters out OTP/2FA codes, declined transactions, balance-only updates, and promos.
- Choose between **Review Queue** (approve/edit/dismiss on **Today**) or **Auto-Log Without Review**, or test immediately with **Paste / Test SMS**.

---

## Quick Start: Connect to Google Sheets

You do **not** need to hardcode any URLs or API keys into this repository before building or pushing to GitHub. Users configure their own Apps Script Web App URL directly inside the app.

Follow the 5-minute guide in **[`docs/GOOGLE_SHEETS_SETUP.md`](docs/GOOGLE_SHEETS_SETUP.md)** using the ready-to-paste script at **[`docs/Code.gs`](docs/Code.gs)**.

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                      # Edge-to-edge Compose entry point
├── data/
│   ├── AccountEntity.kt                 # Room entity for 100% custom user accounts
│   ├── AccountTransferEntity.kt         # Room entity for internal account-to-account transfers
│   ├── AppsScriptSyncClient.kt          # OkHttp client for Google Apps Script /exec endpoint
│   ├── BudgetPreferencesRepository.kt   # DataStore preferences (budget plan, demo mode, Sheet URL)
│   ├── CommitmentEntity.kt              # Room entity for debts, cards, subscriptions, goals & bills
│   ├── DemoDataSeeder.kt                # Sample 7-day dataset for Demo Mode
│   ├── DetectedTransactionEntity.kt     # Room entity for parsed bank charge alerts
│   ├── ExpenseEntity.kt                 # Room entity with remoteId & isDemo isolation
│   ├── CategoryBudgetEntity.kt          # Room entity for category monthly caps & alert tracking
│   ├── PointsAccountEntity.kt           # Room entity for quick-view points & rewards trackers
│   ├── SpendWiseDao.kt                  # Room DAOs
│   ├── SpendWiseDatabase.kt             # Room database singleton
│   └── SpendWiseRepository.kt           # Unified data & Sheet sync repository
├── model/
│   ├── BankMessageParser.kt             # On-device bank SMS & notification parser + OTP filter
│   ├── ExpenseCategory.kt               # Categories, payment methods, budget modes
│   ├── ExpenseCategorizer.kt            # Description + amount + history categorization engine
│   └── FinancialCommitmentModels.kt     # Types, cycles & due-date schedule helper
├── notifications/
│   ├── BankNotificationListenerService.kt # On-device NotificationListenerService for bank alerts
│   └── BudgetNotificationHelper.kt      # NotificationChannel & 80%/100% threshold alerts
├── ui/
│   ├── SpendWiseApp.kt                  # Adaptive NavigationBar / NavigationRail & NavHost
│   ├── components/                      # AddEditExpenseSheet, CommitmentEditorSheet, Dialogs
│   ├── screens/                         # TodayScreen, ActivityScreen, PlansScreen, InsightsScreen, BudgetScreen
│   └── theme/                           # Warm Editorial Ledger Material 3 theme & custom fonts
├── util/
│   ├── CsvExporter.kt                   # CSV share sheet export
│   ├── FinancialInsightsEngine.kt       # Daily allowance, 7-day chart & coaching tips
│   └── MoneyFormatter.kt                # Currency formatting & minSdk 24 date utilities
└── viewmodel/
    └── SpendWiseViewModel.kt            # Reactive StateFlow & UI actions
docs/
├── Code.gs                              # Copy-paste Google Apps Script backend
└── GOOGLE_SHEETS_SETUP.md               # Step-by-step Google Sheets setup guide
```

---

## Building Locally

1. Clone this repository and open it in **Android Studio** (AGP 9.1+, Kotlin 2.2+, compileSdk 36, minSdk 24).
2. Sync Gradle and run the `app` configuration on an Android device or emulator.
3. Run unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

## Privacy & Open-Source Safety
- All data stays locally on-device unless you explicitly configure a Google Apps Script Web App URL in the app.
- No Sheet URLs or secret tokens are stored in source control—users enter their own URL/token in-app (persisted locally via Jetpack DataStore).
