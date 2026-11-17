# Google Sheets Sync Setup (Apps Script)

SpendWise works offline-first out of the box (and includes **Demo Mode** for instant exploration), and you can connect it to your own **Google Sheet** in under 5 minutes using **Google Apps Script**. No Firebase account, GCP billing, or OAuth client setup is required.

---

## Step 1: Create Your Google Sheet
1. Open [Google Sheets](https://sheets.google.com) and create a **Blank spreadsheet** (for example, name it `SpendWise Daily Expenses`).
2. You do not need to create tabs or columns manually—the Apps Script will automatically create `Expenses` and `CategoryBudgets` tabs on first sync.

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
   - **Description:** `SpendWise Sync v1`
   - **Execute as:** `Me (your email)`
   - **Who has access:** `Anyone`
4. Click **Deploy**.
5. Click **Authorize access**, select your Google account, and if Google shows an *"App isn't verified"* warning (because it's your own personal script), click **Advanced → Go to ... (unsafe) → Allow**.
6. Copy the **Web app URL** (it looks like `https://script.google.com/macros/s/AKfy.../exec`).

> **Tip:** Whenever you edit `Code.gs` later, choose **Deploy → Manage deployments → Edit (pencil icon) → Version: New version → Deploy** so the live `/exec` URL picks up your changes.

---

## Step 4: Connect Inside SpendWise
1. Open **SpendWise** on your phone or emulator.
2. Tap **Connect Sheet** at the top of the **Today** tab (or open **Budget & Sync → Add Script URL**).
3. Paste your `/exec` **Apps Script Web App URL**.
4. If you set `SYNC_TOKEN` in Step 2, enter the same token in **Sync Secret Token**.
5. Tap **Test Connection** to verify it prints `Connected to "<Your Sheet Name>"`.
6. Tap **Save & Sync**.

SpendWise will now merge your local expenses and category limits with your Google Sheet and keep them synced automatically when **Auto-sync on changes** is enabled.

---

## Sheet Schema Reference

### `Expenses` Tab
| Column | Description |
| :--- | :--- |
| `remoteId` | Unique UUID used for conflict-free merging |
| `Date` | Formatted date/time of the expense |
| `Description` | Merchant or expense description |
| `Category` | Predefined category key (`GROCERIES`, `FOOD_DINING`, etc.) |
| `Amount` | Human-readable decimal amount |
| `PaymentMethod` | `CARD`, `CASH`, `WALLET`, or `BANK` |
| `Note` | Optional user note |
| `AutoCategorized` | `true` if assigned automatically by `ExpenseCategorizer` |
| `amountCents` | Exact integer cents used by the Android app |
| `timestampMillis` | Epoch milliseconds used by the Android app |
| `Account` | Custom account name assigned by the user (or blank) |

### `CategoryBudgets` Tab
| Column | Description |
| :--- | :--- |
| `category` | Category key (`GROCERIES`, `FOOD_DINING`, `HOUSING`, etc.) |
| `MonthlyLimit` | Human-readable monthly limit |
| `monthlyLimitCents` | Exact integer cents limit |

---

## Troubleshooting
- **HTTP 401 / 403 or HTML login page returned:** Make sure **Who has access** is set to **Anyone** (not *Only myself*) in your Apps Script deployment.
- **`Invalid sync token.`:** Ensure the token entered in SpendWise matches `SYNC_TOKEN` in Apps Script **Project Settings → Script Properties** (no extra spaces).
- **Updated `Code.gs` but behavior didn't change:** Re-deploy using **Manage deployments → New version**; saving `Code.gs` alone does not update the `/exec` endpoint.
- **Demo Mode entries:** Sample items created by **Demo Mode** (`isDemo = true`) are automatically excluded from Sheet sync so your real spreadsheet stays clean.
