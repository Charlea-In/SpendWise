/**
 * SpendWise — Full Multi-Device Google Sheets Sync Backend (Google Apps Script)
 * Schema v2: Syncs Expenses, CategoryBudgets, Accounts, AddedFunds, Transfers, Plans, Points & Settings.
 *
 * Setup / Upgrade:
 * 1. Open your Google Sheet -> Extensions -> Apps Script.
 * 2. Paste this entire file into Code.gs and save (Ctrl+S / Cmd+S).
 * 3. (Optional) Set a secret token in Project Settings -> Script Properties:
 *    Key: SYNC_TOKEN   Value: your-secret-passphrase
 * 4. First time: Deploy -> New deployment -> Web app (Execute as: Me, Who has access: Anyone).
 *    Upgrading an existing deployment: Deploy -> Manage deployments -> Edit (pencil) ->
 *    Version: New version -> Deploy (keeps your same /exec URL!).
 * 5. Paste the /exec Web App URL into SpendWise -> Sync on any of your devices.
 */

const SCHEMA_VERSION = 2;

const EXPENSES_SHEET = "Expenses";
const BUDGETS_SHEET = "CategoryBudgets";
const ACCOUNTS_SHEET = "Accounts";
const DEPOSITS_SHEET = "AddedFunds";
const TRANSFERS_SHEET = "Transfers";
const COMMITMENTS_SHEET = "Plans";
const POINTS_SHEET = "Points";
const SETTINGS_SHEET = "Settings";

// Optional fallback token if not using Script Properties (leave "" for open personal URL)
const FALLBACK_SYNC_TOKEN = "";

function getExpectedToken_() {
  const propToken = PropertiesService.getScriptProperties().getProperty("SYNC_TOKEN");
  return (propToken || FALLBACK_SYNC_TOKEN || "").trim();
}

function validateToken_(providedToken) {
  const expected = getExpectedToken_();
  if (!expected) return true;
  return String(providedToken || "").trim() === expected;
}

function jsonResponse_(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}

function ensureSheetHeaders_(ss, name, headers) {
  let sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
    return sheet;
  }
  const existingCols = sheet.getLastColumn();
  if (existingCols < headers.length) {
    for (let col = existingCols + 1; col <= headers.length; col++) {
      sheet.getRange(1, col).setValue(headers[col - 1]).setFontWeight("bold");
    }
  }
  return sheet;
}

function ensureSheets_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  const expSheet = ensureSheetHeaders_(ss, EXPENSES_SHEET, [
    "remoteId",
    "Date",
    "Description",
    "Category",
    "Amount",
    "PaymentMethod",
    "Note",
    "AutoCategorized",
    "amountCents",
    "timestampMillis",
    "Account",
    "updatedAtMillis"
  ]);

  const budgetSheet = ensureSheetHeaders_(ss, BUDGETS_SHEET, [
    "category",
    "MonthlyLimit",
    "monthlyLimitCents",
    "updatedAtMillis"
  ]);

  const accountSheet = ensureSheetHeaders_(ss, ACCOUNTS_SHEET, [
    "remoteId",
    "Name",
    "InitialBalance",
    "initialBalanceCents",
    "createdAtMillis",
    "updatedAtMillis"
  ]);

  const depositSheet = ensureSheetHeaders_(ss, DEPOSITS_SHEET, [
    "remoteId",
    "Date",
    "Account",
    "Source",
    "Amount",
    "Note",
    "amountCents",
    "timestampMillis",
    "updatedAtMillis"
  ]);

  const transferSheet = ensureSheetHeaders_(ss, TRANSFERS_SHEET, [
    "remoteId",
    "Date",
    "FromAccount",
    "ToAccount",
    "Amount",
    "Note",
    "amountCents",
    "timestampMillis",
    "updatedAtMillis"
  ]);

  const commitmentSheet = ensureSheetHeaders_(ss, COMMITMENTS_SHEET, [
    "remoteId",
    "Title",
    "Type",
    "Amount",
    "Progress",
    "TargetOrTotal",
    "Cycle",
    "DueDay",
    "StatementDay",
    "Account",
    "Category",
    "Note",
    "Active",
    "Reminder",
    "amountCents",
    "targetOrTotalCents",
    "progressCents",
    "scheduledDateMillis",
    "totalInstallments",
    "completedInstallments",
    "lastCompletedCycleKey",
    "createdAtMillis",
    "updatedAtMillis"
  ]);

  const pointsSheet = ensureSheetHeaders_(ss, POINTS_SHEET, [
    "remoteId",
    "Name",
    "PointsBalance",
    "Unit",
    "Note",
    "centsPerPointTimes100",
    "createdAtMillis",
    "updatedAtMillis"
  ]);

  const settingsSheet = ensureSheetHeaders_(ss, SETTINGS_SHEET, [
    "key",
    "monthlyBudgetCents",
    "monthlySavingsGoalCents",
    "dailyMode",
    "fixedDailyLimitCents",
    "currencySymbol",
    "todayUseAccountsBalance",
    "updatedAtMillis"
  ]);

  return {
    ss,
    expSheet,
    budgetSheet,
    accountSheet,
    depositSheet,
    transferSheet,
    commitmentSheet,
    pointsSheet,
    settingsSheet
  };
}

function readAllExpenses_(expSheet) {
  const lastRow = expSheet.getLastRow();
  if (lastRow < 2) return [];
  const colCount = Math.max(10, Math.min(12, expSheet.getLastColumn()));
  const rows = expSheet.getRange(2, 1, lastRow - 1, colCount).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const remoteId = String(r[0] || "").trim();
    if (!remoteId) continue;
    const amountCents = Number(r[8]) || Math.round((Number(r[4]) || 0) * 100);
    const ts = Number(r[9]) || (r[1] instanceof Date ? r[1].getTime() : Date.now());
    list.push({
      rowNum: i + 2,
      remoteId: remoteId,
      description: String(r[2] || "Expense"),
      category: String(r[3] || "OTHER"),
      paymentMethod: String(r[5] || "CARD"),
      note: String(r[6] || ""),
      wasAutoCategorized: Boolean(r[7]),
      amountCents: amountCents,
      timestampMillis: ts,
      accountName: String((colCount >= 11 ? r[10] : "") || ""),
      updatedAtMillis: Number((colCount >= 12 ? r[11] : 0) || 0)
    });
  }
  return list;
}

function readAllBudgets_(budgetSheet) {
  const lastRow = budgetSheet.getLastRow();
  if (lastRow < 2) return [];
  const colCount = Math.max(3, Math.min(4, budgetSheet.getLastColumn()));
  const rows = budgetSheet.getRange(2, 1, lastRow - 1, colCount).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const category = String(r[0] || "").trim();
    if (!category) continue;
    const limitCents = Number(r[2]) || Math.round((Number(r[1]) || 0) * 100);
    if (limitCents > 0) {
      list.push({
        rowNum: i + 2,
        category: category,
        monthlyLimitCents: limitCents,
        updatedAtMillis: Number((colCount >= 4 ? r[3] : 0) || 0)
      });
    }
  }
  return list;
}

function readAllAccounts_(accountSheet) {
  const lastRow = accountSheet.getLastRow();
  if (lastRow < 2) return [];
  const rows = accountSheet.getRange(2, 1, lastRow - 1, 6).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const remoteId = String(r[0] || "").trim();
    const name = String(r[1] || "").trim();
    if (!remoteId || !name) continue;
    const initialCents = Number(r[3]) || Math.round((Number(r[2]) || 0) * 100);
    list.push({
      rowNum: i + 2,
      remoteId: remoteId,
      name: name,
      initialBalanceCents: Math.max(0, initialCents),
      createdAtMillis: Number(r[4]) || Date.now(),
      updatedAtMillis: Number(r[5]) || 0
    });
  }
  return list;
}

function readAllDeposits_(depositSheet) {
  const lastRow = depositSheet.getLastRow();
  if (lastRow < 2) return [];
  const rows = depositSheet.getRange(2, 1, lastRow - 1, 9).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const remoteId = String(r[0] || "").trim();
    const accountName = String(r[2] || "").trim();
    const source = String(r[3] || "").trim();
    const amountCents = Number(r[6]) || Math.round((Number(r[4]) || 0) * 100);
    if (!remoteId || !accountName || !source || amountCents <= 0) continue;
    const ts = Number(r[7]) || (r[1] instanceof Date ? r[1].getTime() : Date.now());
    list.push({
      rowNum: i + 2,
      remoteId: remoteId,
      accountName: accountName,
      source: source,
      amountCents: amountCents,
      timestampMillis: ts,
      note: String(r[5] || ""),
      updatedAtMillis: Number(r[8]) || 0
    });
  }
  return list;
}

function readAllTransfers_(transferSheet) {
  const lastRow = transferSheet.getLastRow();
  if (lastRow < 2) return [];
  const rows = transferSheet.getRange(2, 1, lastRow - 1, 9).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const remoteId = String(r[0] || "").trim();
    const fromAccountName = String(r[2] || "").trim();
    const toAccountName = String(r[3] || "").trim();
    const amountCents = Number(r[6]) || Math.round((Number(r[4]) || 0) * 100);
    if (!remoteId || !fromAccountName || !toAccountName || amountCents <= 0) continue;
    const ts = Number(r[7]) || (r[1] instanceof Date ? r[1].getTime() : Date.now());
    list.push({
      rowNum: i + 2,
      remoteId: remoteId,
      fromAccountName: fromAccountName,
      toAccountName: toAccountName,
      amountCents: amountCents,
      timestampMillis: ts,
      note: String(r[5] || ""),
      updatedAtMillis: Number(r[8]) || 0
    });
  }
  return list;
}

function readAllCommitments_(commitmentSheet) {
  const lastRow = commitmentSheet.getLastRow();
  if (lastRow < 2) return [];
  const rows = commitmentSheet.getRange(2, 1, lastRow - 1, 23).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const remoteId = String(r[0] || "").trim();
    const title = String(r[1] || "").trim();
    const amountCents = Number(r[14]) || Math.round((Number(r[3]) || 0) * 100);
    if (!remoteId || !title || amountCents <= 0) continue;
    list.push({
      rowNum: i + 2,
      remoteId: remoteId,
      title: title,
      type: String(r[2] || "DEBT"),
      amountCents: amountCents,
      targetOrTotalCents: Number(r[15]) || Math.round((Number(r[5]) || 0) * 100),
      progressCents: Number(r[16]) || Math.round((Number(r[4]) || 0) * 100),
      cycle: String(r[6] || "MONTHLY"),
      dueDayOfMonth: Number(r[7]) || 1,
      statementDayOfMonth: Number(r[8]) || 0,
      accountName: String(r[9] || ""),
      category: String(r[10] || "OTHER"),
      note: String(r[11] || ""),
      isActive: r[12] === "" ? true : Boolean(r[12]),
      reminderEnabled: r[13] === "" ? true : Boolean(r[13]),
      scheduledDateMillis: Number(r[17]) || 0,
      totalInstallments: Number(r[18]) || 0,
      completedInstallments: Number(r[19]) || 0,
      lastCompletedCycleKey: String(r[20] || ""),
      createdAtMillis: Number(r[21]) || Date.now(),
      updatedAtMillis: Number(r[22]) || 0
    });
  }
  return list;
}

function readAllPoints_(pointsSheet) {
  const lastRow = pointsSheet.getLastRow();
  if (lastRow < 2) return [];
  const rows = pointsSheet.getRange(2, 1, lastRow - 1, 8).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const remoteId = String(r[0] || "").trim();
    const name = String(r[1] || "").trim();
    if (!remoteId || !name) continue;
    list.push({
      rowNum: i + 2,
      remoteId: remoteId,
      name: name,
      pointsBalance: Math.max(0, Number(r[2]) || 0),
      unitLabel: String(r[3] || "pts") || "pts",
      note: String(r[4] || ""),
      centsPerPointTimes100: Math.max(0, Number(r[5]) || 0),
      createdAtMillis: Number(r[6]) || Date.now(),
      updatedAtMillis: Number(r[7]) || 0
    });
  }
  return list;
}

function readSettings_(settingsSheet) {
  if (settingsSheet.getLastRow() < 2) return null;
  const r = settingsSheet.getRange(2, 1, 1, 8).getValues()[0];
  if (!String(r[0] || "").trim()) return null;
  return {
    monthlyBudgetCents: Math.max(0, Number(r[1]) || 0),
    monthlySavingsGoalCents: Math.max(0, Number(r[2]) || 0),
    dailyMode: String(r[3] || "AUTO_PACED"),
    fixedDailyLimitCents: Math.max(0, Number(r[4]) || 0),
    currencySymbol: String(r[5] || "$") || "$",
    todayUseAccountsBalance: r[6] === "" ? true : Boolean(r[6]),
    updatedAtMillis: Number(r[7]) || 0
  };
}

function stripRowNum_(items) {
  return items.map(function (item) {
    const copy = Object.assign({}, item);
    delete copy.rowNum;
    return copy;
  });
}

function deleteRowsByKey_(sheet, targetKey) {
  const cleanKey = String(targetKey || "").trim();
  if (!cleanKey || sheet.getLastRow() < 2) return;
  const keys = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
  for (let i = keys.length - 1; i >= 0; i--) {
    if (String(keys[i][0] || "").trim() === cleanKey) {
      sheet.deleteRow(i + 2);
    }
  }
}

function getSheetForRecordType_(sheets, recordType) {
  switch (String(recordType || "").trim()) {
    case "expense": return sheets.expSheet;
    case "budget": return sheets.budgetSheet;
    case "account": return sheets.accountSheet;
    case "deposit": return sheets.depositSheet;
    case "transfer": return sheets.transferSheet;
    case "commitment": return sheets.commitmentSheet;
    case "points": return sheets.pointsSheet;
    default: return null;
  }
}

function buildFullPayload_(sheets) {
  return {
    status: "ok",
    schemaVersion: SCHEMA_VERSION,
    expenses: stripRowNum_(readAllExpenses_(sheets.expSheet)),
    budgets: stripRowNum_(readAllBudgets_(sheets.budgetSheet)),
    accounts: stripRowNum_(readAllAccounts_(sheets.accountSheet)),
    deposits: stripRowNum_(readAllDeposits_(sheets.depositSheet)),
    transfers: stripRowNum_(readAllTransfers_(sheets.transferSheet)),
    commitments: stripRowNum_(readAllCommitments_(sheets.commitmentSheet)),
    pointsAccounts: stripRowNum_(readAllPoints_(sheets.pointsSheet)),
    settings: readSettings_(sheets.settingsSheet)
  };
}

function doGet(e) {
  try {
    const params = (e && e.parameter) || {};
    if (!validateToken_(params.token)) {
      return jsonResponse_({ status: "error", error: "Invalid sync token." });
    }

    const sheets = ensureSheets_();
    const action = params.action || "ping";

    if (action === "ping") {
      return jsonResponse_({
        status: "ok",
        schemaVersion: SCHEMA_VERSION,
        sheetName: sheets.ss.getName(),
        expensesCount: Math.max(0, sheets.expSheet.getLastRow() - 1),
        accountsCount: Math.max(0, sheets.accountSheet.getLastRow() - 1)
      });
    }

    if (action === "fetchAll") {
      return jsonResponse_(buildFullPayload_(sheets));
    }

    return jsonResponse_({ status: "error", error: "Unknown GET action: " + action });
  } catch (err) {
    return jsonResponse_({ status: "error", error: String(err) });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(15000);
    const body = JSON.parse((e && e.postData && e.postData.contents) || "{}");
    if (!validateToken_(body.token)) {
      return jsonResponse_({ status: "error", error: "Invalid sync token." });
    }

    const sheets = ensureSheets_();
    const action = body.action || "syncAll";

    if (action === "deleteExpense") {
      deleteRowsByKey_(sheets.expSheet, body.remoteId);
      return jsonResponse_({ status: "ok", schemaVersion: SCHEMA_VERSION });
    }

    if (action === "deleteRecord") {
      const targetSheet = getSheetForRecordType_(sheets, body.recordType);
      if (targetSheet) {
        deleteRowsByKey_(targetSheet, body.remoteId);
      }
      return jsonResponse_({ status: "ok", schemaVersion: SCHEMA_VERSION });
    }

    if (action === "syncAll") {
      // 1. Merge Custom Accounts (match by remoteId first, then case-insensitive name)
      const existingAccounts = readAllAccounts_(sheets.accountSheet);
      const acctById = {};
      const acctByName = {};
      existingAccounts.forEach(function (a) {
        acctById[a.remoteId] = a;
        acctByName[a.name.toLowerCase()] = a;
      });
      const incomingAccounts = Array.isArray(body.accounts) ? body.accounts : [];
      const accountsToAppend = [];
      for (let i = 0; i < incomingAccounts.length; i++) {
        const a = incomingAccounts[i];
        const remoteId = String(a.remoteId || "").trim();
        const name = String(a.name || "").trim();
        if (!remoteId || !name) continue;
        const initialCents = Math.max(0, Number(a.initialBalanceCents) || 0);
        const created = Number(a.createdAtMillis) || Date.now();
        const incomingUpdated = Number(a.updatedAtMillis) || 0;

        const existing = acctById[remoteId] || acctByName[name.toLowerCase()];
        if (!existing) {
          const row = [remoteId, name, initialCents / 100.0, initialCents, created, incomingUpdated];
          accountsToAppend.push(row);
          const stub = { remoteId: remoteId, name: name, updatedAtMillis: incomingUpdated, rowNum: -1 };
          acctById[remoteId] = stub;
          acctByName[name.toLowerCase()] = stub;
        } else if (existing.rowNum > 0 && incomingUpdated > existing.updatedAtMillis) {
          const canonicalId = existing.remoteId;
          const row = [canonicalId, name, initialCents / 100.0, initialCents, existing.createdAtMillis, incomingUpdated];
          sheets.accountSheet.getRange(existing.rowNum, 1, 1, 6).setValues([row]);
          existing.updatedAtMillis = incomingUpdated;
        }
      }
      if (accountsToAppend.length > 0) {
        sheets.accountSheet.getRange(sheets.accountSheet.getLastRow() + 1, 1, accountsToAppend.length, 6).setValues(accountsToAppend);
      }

      // 2. Merge Expenses by remoteId
      const existingExpenses = readAllExpenses_(sheets.expSheet);
      const expById = {};
      existingExpenses.forEach(function (item) { expById[item.remoteId] = item; });
      const incomingExpenses = Array.isArray(body.expenses) ? body.expenses : [];
      const expToAppend = [];
      for (let i = 0; i < incomingExpenses.length; i++) {
        const item = incomingExpenses[i];
        const remoteId = String(item.remoteId || "").trim();
        if (!remoteId) continue;
        const amountCents = Number(item.amountCents) || 0;
        const ts = Number(item.timestampMillis) || Date.now();
        const incomingUpdated = Number(item.updatedAtMillis) || 0;
        const rowValues = [
          remoteId,
          new Date(ts),
          String(item.description || "Expense"),
          String(item.category || "OTHER"),
          amountCents / 100.0,
          String(item.paymentMethod || "CARD"),
          String(item.note || ""),
          Boolean(item.wasAutoCategorized),
          amountCents,
          ts,
          String(item.accountName || ""),
          incomingUpdated
        ];
        const existing = expById[remoteId];
        if (!existing) {
          expToAppend.push(rowValues);
          expById[remoteId] = { remoteId: remoteId, updatedAtMillis: incomingUpdated, rowNum: -1 };
        } else if (existing.rowNum > 0 && incomingUpdated > existing.updatedAtMillis) {
          sheets.expSheet.getRange(existing.rowNum, 1, 1, 12).setValues([rowValues]);
          existing.updatedAtMillis = incomingUpdated;
        }
      }
      if (expToAppend.length > 0) {
        sheets.expSheet.getRange(sheets.expSheet.getLastRow() + 1, 1, expToAppend.length, 12).setValues(expToAppend);
      }

      // 3. Merge Category Budgets by category key
      const existingBudgets = readAllBudgets_(sheets.budgetSheet);
      const budgetByCat = {};
      existingBudgets.forEach(function (b) { budgetByCat[b.category] = b; });
      const incomingBudgets = Array.isArray(body.budgets) ? body.budgets : [];
      const budgetsToAppend = [];
      for (let i = 0; i < incomingBudgets.length; i++) {
        const b = incomingBudgets[i];
        const cat = String(b.category || "").trim();
        const limitCents = Number(b.monthlyLimitCents) || 0;
        const incomingUpdated = Number(b.updatedAtMillis) || 0;
        if (!cat || limitCents <= 0) continue;

        const bRow = [cat, limitCents / 100.0, limitCents, incomingUpdated];
        const existing = budgetByCat[cat];
        if (!existing) {
          budgetsToAppend.push(bRow);
          budgetByCat[cat] = { category: cat, updatedAtMillis: incomingUpdated, rowNum: -1 };
        } else if (existing.rowNum > 0 && incomingUpdated > existing.updatedAtMillis) {
          sheets.budgetSheet.getRange(existing.rowNum, 1, 1, 4).setValues([bRow]);
          existing.updatedAtMillis = incomingUpdated;
        }
      }
      if (budgetsToAppend.length > 0) {
        sheets.budgetSheet.getRange(sheets.budgetSheet.getLastRow() + 1, 1, budgetsToAppend.length, 4).setValues(budgetsToAppend);
      }

      // 4. Merge Added Funds / Deposits
      const existingDeposits = readAllDeposits_(sheets.depositSheet);
      const depById = {};
      existingDeposits.forEach(function (d) { depById[d.remoteId] = d; });
      const incomingDeposits = Array.isArray(body.deposits) ? body.deposits : [];
      const depsToAppend = [];
      for (let i = 0; i < incomingDeposits.length; i++) {
        const d = incomingDeposits[i];
        const remoteId = String(d.remoteId || "").trim();
        const accountName = String(d.accountName || "").trim();
        const source = String(d.source || "").trim();
        const amountCents = Number(d.amountCents) || 0;
        if (!remoteId || !accountName || !source || amountCents <= 0) continue;
        const ts = Number(d.timestampMillis) || Date.now();
        const incomingUpdated = Number(d.updatedAtMillis) || 0;
        const row = [
          remoteId,
          new Date(ts),
          accountName,
          source,
          amountCents / 100.0,
          String(d.note || ""),
          amountCents,
          ts,
          incomingUpdated
        ];
        const existing = depById[remoteId];
        if (!existing) {
          depsToAppend.push(row);
          depById[remoteId] = { remoteId: remoteId, updatedAtMillis: incomingUpdated, rowNum: -1 };
        } else if (existing.rowNum > 0 && incomingUpdated > existing.updatedAtMillis) {
          sheets.depositSheet.getRange(existing.rowNum, 1, 1, 9).setValues([row]);
          existing.updatedAtMillis = incomingUpdated;
        }
      }
      if (depsToAppend.length > 0) {
        sheets.depositSheet.getRange(sheets.depositSheet.getLastRow() + 1, 1, depsToAppend.length, 9).setValues(depsToAppend);
      }

      // 5. Merge Internal Transfers
      const existingTransfers = readAllTransfers_(sheets.transferSheet);
      const trById = {};
      existingTransfers.forEach(function (t) { trById[t.remoteId] = t; });
      const incomingTransfers = Array.isArray(body.transfers) ? body.transfers : [];
      const trToAppend = [];
      for (let i = 0; i < incomingTransfers.length; i++) {
        const t = incomingTransfers[i];
        const remoteId = String(t.remoteId || "").trim();
        const fromAcct = String(t.fromAccountName || "").trim();
        const toAcct = String(t.toAccountName || "").trim();
        const amountCents = Number(t.amountCents) || 0;
        if (!remoteId || !fromAcct || !toAcct || amountCents <= 0) continue;
        const ts = Number(t.timestampMillis) || Date.now();
        const incomingUpdated = Number(t.updatedAtMillis) || 0;
        const row = [
          remoteId,
          new Date(ts),
          fromAcct,
          toAcct,
          amountCents / 100.0,
          String(t.note || ""),
          amountCents,
          ts,
          incomingUpdated
        ];
        const existing = trById[remoteId];
        if (!existing) {
          trToAppend.push(row);
          trById[remoteId] = { remoteId: remoteId, updatedAtMillis: incomingUpdated, rowNum: -1 };
        } else if (existing.rowNum > 0 && incomingUpdated > existing.updatedAtMillis) {
          sheets.transferSheet.getRange(existing.rowNum, 1, 1, 9).setValues([row]);
          existing.updatedAtMillis = incomingUpdated;
        }
      }
      if (trToAppend.length > 0) {
        sheets.transferSheet.getRange(sheets.transferSheet.getLastRow() + 1, 1, trToAppend.length, 9).setValues(trToAppend);
      }

      // 6. Merge Plans / Financial Commitments
      const existingCommitments = readAllCommitments_(sheets.commitmentSheet);
      const comById = {};
      existingCommitments.forEach(function (c) { comById[c.remoteId] = c; });
      const incomingCommitments = Array.isArray(body.commitments) ? body.commitments : [];
      const comToAppend = [];
      for (let i = 0; i < incomingCommitments.length; i++) {
        const c = incomingCommitments[i];
        const remoteId = String(c.remoteId || "").trim();
        const title = String(c.title || "").trim();
        const amountCents = Number(c.amountCents) || 0;
        if (!remoteId || !title || amountCents <= 0) continue;
        const targetCents = Math.max(0, Number(c.targetOrTotalCents) || 0);
        const progressCents = Math.max(0, Number(c.progressCents) || 0);
        const created = Number(c.createdAtMillis) || Date.now();
        const incomingUpdated = Number(c.updatedAtMillis) || 0;
        const row = [
          remoteId,
          title,
          String(c.type || "DEBT"),
          amountCents / 100.0,
          progressCents / 100.0,
          targetCents / 100.0,
          String(c.cycle || "MONTHLY"),
          Number(c.dueDayOfMonth) || 1,
          Number(c.statementDayOfMonth) || 0,
          String(c.accountName || ""),
          String(c.category || "OTHER"),
          String(c.note || ""),
          c.isActive === undefined ? true : Boolean(c.isActive),
          c.reminderEnabled === undefined ? true : Boolean(c.reminderEnabled),
          amountCents,
          targetCents,
          progressCents,
          Number(c.scheduledDateMillis) || 0,
          Math.max(0, Number(c.totalInstallments) || 0),
          Math.max(0, Number(c.completedInstallments) || 0),
          String(c.lastCompletedCycleKey || ""),
          created,
          incomingUpdated
        ];
        const existing = comById[remoteId];
        if (!existing) {
          comToAppend.push(row);
          comById[remoteId] = { remoteId: remoteId, updatedAtMillis: incomingUpdated, rowNum: -1 };
        } else if (existing.rowNum > 0 && incomingUpdated > existing.updatedAtMillis) {
          sheets.commitmentSheet.getRange(existing.rowNum, 1, 1, 23).setValues([row]);
          existing.updatedAtMillis = incomingUpdated;
        }
      }
      if (comToAppend.length > 0) {
        sheets.commitmentSheet.getRange(sheets.commitmentSheet.getLastRow() + 1, 1, comToAppend.length, 23).setValues(comToAppend);
      }

      // 7. Merge Points & Rewards Trackers
      const existingPoints = readAllPoints_(sheets.pointsSheet);
      const ptsById = {};
      const ptsByName = {};
      existingPoints.forEach(function (p) {
        ptsById[p.remoteId] = p;
        ptsByName[p.name.toLowerCase()] = p;
      });
      const incomingPoints = Array.isArray(body.pointsAccounts) ? body.pointsAccounts : [];
      const ptsToAppend = [];
      for (let i = 0; i < incomingPoints.length; i++) {
        const p = incomingPoints[i];
        const remoteId = String(p.remoteId || "").trim();
        const name = String(p.name || "").trim();
        if (!remoteId || !name) continue;
        const pointsBalance = Math.max(0, Number(p.pointsBalance) || 0);
        const unitLabel = String(p.unitLabel || "pts").trim() || "pts";
        const rate = Math.max(0, Number(p.centsPerPointTimes100) || 0);
        const created = Number(p.createdAtMillis) || Date.now();
        const incomingUpdated = Number(p.updatedAtMillis) || 0;

        const existing = ptsById[remoteId] || ptsByName[name.toLowerCase()];
        if (!existing) {
          const row = [remoteId, name, pointsBalance, unitLabel, String(p.note || ""), rate, created, incomingUpdated];
          ptsToAppend.push(row);
          const stub = { remoteId: remoteId, name: name, updatedAtMillis: incomingUpdated, rowNum: -1 };
          ptsById[remoteId] = stub;
          ptsByName[name.toLowerCase()] = stub;
        } else if (existing.rowNum > 0 && incomingUpdated > existing.updatedAtMillis) {
          const canonicalId = existing.remoteId;
          const row = [canonicalId, name, pointsBalance, unitLabel, String(p.note || ""), rate, existing.createdAtMillis, incomingUpdated];
          sheets.pointsSheet.getRange(existing.rowNum, 1, 1, 8).setValues([row]);
          existing.updatedAtMillis = incomingUpdated;
        }
      }
      if (ptsToAppend.length > 0) {
        sheets.pointsSheet.getRange(sheets.pointsSheet.getLastRow() + 1, 1, ptsToAppend.length, 8).setValues(ptsToAppend);
      }

      // 8. Merge Budget Plan, Currency & Today Source Settings
      if (body.settings && typeof body.settings === "object") {
        const incomingSettings = body.settings;
        const existingSettings = readSettings_(sheets.settingsSheet);
        const incomingUpdated = Number(incomingSettings.updatedAtMillis) || 0;
        if (!existingSettings) {
          const stamped = incomingUpdated > 0 ? incomingUpdated : Date.now();
          sheets.settingsSheet.getRange(2, 1, 1, 8).setValues([[
            "app_settings",
            Math.max(0, Number(incomingSettings.monthlyBudgetCents) || 0),
            Math.max(0, Number(incomingSettings.monthlySavingsGoalCents) || 0),
            String(incomingSettings.dailyMode || "AUTO_PACED"),
            Math.max(0, Number(incomingSettings.fixedDailyLimitCents) || 0),
            String(incomingSettings.currencySymbol || "$") || "$",
            incomingSettings.todayUseAccountsBalance === undefined ? true : Boolean(incomingSettings.todayUseAccountsBalance),
            stamped
          ]]);
        } else if (incomingUpdated > existingSettings.updatedAtMillis) {
          sheets.settingsSheet.getRange(2, 1, 1, 8).setValues([[
            "app_settings",
            Math.max(0, Number(incomingSettings.monthlyBudgetCents) || 0),
            Math.max(0, Number(incomingSettings.monthlySavingsGoalCents) || 0),
            String(incomingSettings.dailyMode || "AUTO_PACED"),
            Math.max(0, Number(incomingSettings.fixedDailyLimitCents) || 0),
            String(incomingSettings.currencySymbol || "$") || "$",
            incomingSettings.todayUseAccountsBalance === undefined ? true : Boolean(incomingSettings.todayUseAccountsBalance),
            incomingUpdated
          ]]);
        }
      }

      return jsonResponse_(buildFullPayload_(sheets));
    }

    return jsonResponse_({ status: "error", error: "Unknown POST action: " + action });
  } catch (err) {
    return jsonResponse_({ status: "error", error: String(err) });
  } finally {
    lock.releaseLock();
  }
}
