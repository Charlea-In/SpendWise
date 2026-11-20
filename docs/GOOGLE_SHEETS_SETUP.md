# Google Sheets Sync Setup (Apps Script)

SpendWise works offline-first out of the box (and includes **Demo Mode** for instant exploration), and you can connect it to your own **Google Sheet** in under 5 minutes using **Google Apps Script**. No Firebase account, GCP billing, or OAuth client setup is required.

Connecting the **same Apps Script Web App URL** (and optional token) on multiple phones or tablets syncs **all** of your financial data across devices:
- **Expenses** (`Expenses` tab)
- **Category Budgets** (`CategoryBudgets` tab)
- **Custom Accounts & Initial Balances** (`Accounts` tab)
- **Added Funds / Deposits** (`AddedFunds` tab)
- **Internal Account Transfers** (`Transfers` tab)
- **Plans & Financial Commitments** (`Plans` tab — Debts, Credit Cards, Subscriptions, Bills, Goals, Installments, and Paydays)
- **Points & Rewards Trackers** (`Points` tab)
- **Monthly Budget Plan, Currency & Today Balance Source** (`Settings` tab)

---

## Step 1: Create Your Google Sheet
1. Open [Google Sheets](https://sheets.google.com) and create a **Blank spreadsheet** (for example, name it `SpendWise Finance`).
2. You do not need to create tabs or columns manually—the Apps Script will automatically create all 8 tabs on first sync.

---

## Step 2: Add the Apps Script (`Code.gs`)
1. In your Google Sheet, click **Extensions → Apps Script** in the top menu.
2. Delete any placeholder `myFunction()` code in `Code.gs`.
3. Copy the entire contents of [`docs/Code.gs`](./Code.gs) from this repository and paste it into `Code.gs`.
4. Press **Ctrl + S** (or **Cmd + S**) to save the script project.

### Optional: Protect Your Endpoint with a Secret Token
Because a Web App deployed for mobile access uses *"Who has access: Anyone"*, we strongly recommend adding a secret token if you share or publish your app:
1. In the Apps Script editor, click the **Project Settings** gear icon on the left sidebar.
2. Scroll to **Script Properties** → **Add script property**.
3. Set Property = `SYNC_TOKEN` and Value = a passphrase of your choice (e.g., `spendwise-2026-secret`).
4. Click **Save script properties**.

---

## Step 3: Deploy as a Web App
1. In the Apps Script editor, click **Deploy → New deployment** (top right).
2. Click the gear icon next to **Select type** and choose **Web app**.
3. Configure:
   - **Description:** `SpendWise Sync v2`
   - **Execute as:** `Me (your email)`
   - **Who has access:** `Anyone`
4. Click **Deploy**.
5. Click **Authorize access**, select your Google account, and if Google shows an *"App isn't verified"* warning (because it's your own personal script), click **Advanced → Go to ... (unsafe) → Allow**.
6. Copy the **Web app URL** (it looks like `https://script.google.com/macros/s/AKfy.../exec`).

> **Upgrading from an earlier version of `Code.gs`:**
> After replacing the code in `Code.gs`, you **must** publish a new version for your `/exec` URL to pick up the new tabs:
> **Deploy → Manage deployments → Edit (pencil icon) → Version: New version → Deploy**.
> Your existing `/exec` URL stays the same, and your existing `Expenses` and `CategoryBudgets` rows are preserved automatically.

---

## Step 4: Connect Inside SpendWise (Device 1 & Device 2+)
1. Open **SpendWise** on your phone or tablet.
2. Tap **Connect Sheet** at the top of the **Today** tab (or open **Budget & Sync → Add Script URL**).
3. Paste your `/exec` **Apps Script Web App URL**.
4. If you set `SYNC_TOKEN` in Step 2, enter the same token in **Sync Secret Token**.
5. Tap **Test Connection** to verify it prints `Connected to "<Your Sheet Name>" (Full multi-device sync ready)`.
6. Tap **Save & Sync**.

SpendWise merges your local and remote data using conflict-safe UUIDs and `updatedAtMillis` timestamps, and pushes updates automatically whenever **Auto-sync on changes** is enabled.

---

## Sheet Tabs Created Automatically
- **`Expenses`**: `remoteId`, `Date`, `Description`, `Category`, `Amount`, `PaymentMethod`, `Note`, `AutoCategorized`, `amountCents`, `timestampMillis`, `Account`, `updatedAtMillis`
- **`CategoryBudgets`**: `category`, `MonthlyLimit`, `monthlyLimitCents`, `updatedAtMillis`
- **`Accounts`**: `remoteId`, `Name`, `InitialBalance`, `initialBalanceCents`, `createdAtMillis`, `updatedAtMillis`
- **`AddedFunds`**: `remoteId`, `Date`, `Account`, `Source`, `Amount`, `Note`, `amountCents`, `timestampMillis`, `updatedAtMillis`
- **`Transfers`**: `remoteId`, `Date`, `FromAccount`, `ToAccount`, `Amount`, `Note`, `amountCents`, `timestampMillis`, `updatedAtMillis`
- **`Plans`**: `remoteId`, `Title`, `Type`, `Amount`, `Progress`, `TargetOrTotal`, `Cycle`, `DueDay`, `StatementDay`, `Account`, `Category`, `Note`, `Active`, `Reminder`, `amountCents`, `targetOrTotalCents`, `progressCents`, `scheduledDateMillis`, `totalInstallments`, `completedInstallments`, `lastCompletedCycleKey`, `createdAtMillis`, `updatedAtMillis`
- **`Points`**: `remoteId`, `Name`, `PointsBalance`, `Unit`, `Note`, `centsPerPointTimes100`, `createdAtMillis`, `updatedAtMillis`
- **`Settings`**: `key`, `monthlyBudgetCents`, `monthlySavingsGoalCents`, `dailyMode`, `fixedDailyLimitCents`, `currencySymbol`, `todayUseAccountsBalance`, `updatedAtMillis`

---

## Troubleshooting
- **HTTP 401 / 403 or HTML login page returned:** Make sure **Who has access** is set to **Anyone** (not *Only myself*) in your Apps Script deployment.
- **`Invalid sync token.`:** Ensure the token entered in SpendWise matches `SYNC_TOKEN` in Apps Script **Project Settings → Script Properties** (no extra spaces).
- **Updated `Code.gs` but only Expenses/Budgets sync:** Re-deploy using **Deploy → Manage deployments → Edit → New version → Deploy**; saving `Code.gs` alone does not update the `/exec` endpoint.
- **Demo Mode entries:** Sample items created by **Demo Mode** (`isDemo = true`) are automatically excluded from Sheet sync so your real spreadsheet stays clean.
- **Device-only SMS notification queue:** Pending unapproved notifications in the **Bank SMS/Notification Review Queue** stay private on the device that received them until you approve them (approved expenses sync automatically).
