/**
 * SpendWise — Google Sheets Sync Backend (Google Apps Script)
 *
 * Setup:
 * 1. Create a Google Sheet and open Extensions -> Apps Script.
 * 2. Paste this entire file into Code.gs and save.
 * 3. (Optional) Set a secret token in Project Settings -> Script Properties:
 *    Key: SYNC_TOKEN   Value: your-secret-passphrase
 * 4. Click Deploy -> New deployment -> Web app:
 *    - Execute as: Me
 *    - Who has access: Anyone
 * 5. Copy the Web App URL (ending in /exec) and paste it into SpendWise -> Budget & Sync.
 */

const EXPENSES_SHEET = "Expenses";
const BUDGETS_SHEET = "CategoryBudgets";

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

function ensureSheets_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  let expSheet = ss.getSheetByName(EXPENSES_SHEET);
  if (!expSheet) {
    expSheet = ss.insertSheet(EXPENSES_SHEET);
    expSheet.appendRow([
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
      "Account"
    ]);
    expSheet.getRange(1, 1, 1, 11).setFontWeight("bold");
    expSheet.setFrozenRows(1);
  } else if (expSheet.getLastColumn() < 11) {
    expSheet.getRange(1, 11).setValue("Account").setFontWeight("bold");
  }

  let budgetSheet = ss.getSheetByName(BUDGETS_SHEET);
  if (!budgetSheet) {
    budgetSheet = ss.insertSheet(BUDGETS_SHEET);
    budgetSheet.appendRow(["category", "MonthlyLimit", "monthlyLimitCents"]);
    budgetSheet.getRange(1, 1, 1, 3).setFontWeight("bold");
    budgetSheet.setFrozenRows(1);
  }

  return { ss, expSheet, budgetSheet };
}

function readAllExpenses_(expSheet) {
  const lastRow = expSheet.getLastRow();
  if (lastRow < 2) return [];
  const colCount = Math.max(10, Math.min(11, expSheet.getLastColumn()));
  const rows = expSheet.getRange(2, 1, lastRow - 1, colCount).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const remoteId = String(r[0] || "").trim();
    if (!remoteId) continue;
    const amountCents = Number(r[8]) || Math.round((Number(r[4]) || 0) * 100);
    const ts = Number(r[9]) || (r[1] instanceof Date ? r[1].getTime() : Date.now());
    list.push({
      remoteId: remoteId,
      description: String(r[2] || "Expense"),
      category: String(r[3] || "OTHER"),
      paymentMethod: String(r[5] || "CARD"),
      note: String(r[6] || ""),
      wasAutoCategorized: Boolean(r[7]),
      amountCents: amountCents,
      timestampMillis: ts,
      accountName: String((colCount >= 11 ? r[10] : "") || "")
    });
  }
  return list;
}

function readAllBudgets_(budgetSheet) {
  const lastRow = budgetSheet.getLastRow();
  if (lastRow < 2) return [];
  const rows = budgetSheet.getRange(2, 1, lastRow - 1, 3).getValues();
  const list = [];
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const category = String(r[0] || "").trim();
    if (!category) continue;
    const limitCents = Number(r[2]) || Math.round((Number(r[1]) || 0) * 100);
    if (limitCents > 0) {
      list.push({
        category: category,
        monthlyLimitCents: limitCents
      });
    }
  }
  return list;
}

function doGet(e) {
  try {
    const params = (e && e.parameter) || {};
    if (!validateToken_(params.token)) {
      return jsonResponse_({ status: "error", error: "Invalid sync token." });
    }

    const { ss, expSheet, budgetSheet } = ensureSheets_();
    const action = params.action || "ping";

    if (action === "ping") {
      return jsonResponse_({
        status: "ok",
        sheetName: ss.getName(),
        expensesCount: Math.max(0, expSheet.getLastRow() - 1)
      });
    }

    if (action === "fetchAll") {
      return jsonResponse_({
        status: "ok",
        expenses: readAllExpenses_(expSheet),
        budgets: readAllBudgets_(budgetSheet)
      });
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

    const { expSheet, budgetSheet } = ensureSheets_();
    const action = body.action || "syncAll";

    if (action === "deleteExpense") {
      const targetId = String(body.remoteId || "").trim();
      if (targetId && expSheet.getLastRow() >= 2) {
        const ids = expSheet.getRange(2, 1, expSheet.getLastRow() - 1, 1).getValues();
        for (let i = ids.length - 1; i >= 0; i--) {
          if (String(ids[i][0]).trim() === targetId) {
            expSheet.deleteRow(i + 2);
          }
        }
      }
      return jsonResponse_({ status: "ok" });
    }

    if (action === "syncAll") {
      // 1. Merge Expenses by remoteId
      const existingRows = readAllExpenses_(expSheet);
      const rowIndexById = {};
      for (let i = 0; i < existingRows.length; i++) {
        rowIndexById[existingRows[i].remoteId] = i + 2;
      }

      const incomingExpenses = Array.isArray(body.expenses) ? body.expenses : [];
      const rowsToAppend = [];

      for (let i = 0; i < incomingExpenses.length; i++) {
        const item = incomingExpenses[i];
        const remoteId = String(item.remoteId || "").trim();
        if (!remoteId) continue;

        const amountCents = Number(item.amountCents) || 0;
        const ts = Number(item.timestampMillis) || Date.now();
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
          String(item.accountName || "")
        ];

        const existingRowNum = rowIndexById[remoteId];
        if (existingRowNum) {
          expSheet.getRange(existingRowNum, 1, 1, 11).setValues([rowValues]);
        } else {
          rowsToAppend.push(rowValues);
        }
      }

      if (rowsToAppend.length > 0) {
        const startRow = expSheet.getLastRow() + 1;
        expSheet.getRange(startRow, 1, rowsToAppend.length, 11).setValues(rowsToAppend);
      }

      // 2. Merge Category Budgets by category key
      const existingBudgets = readAllBudgets_(budgetSheet);
      const budgetRowByCat = {};
      for (let i = 0; i < existingBudgets.length; i++) {
        budgetRowByCat[existingBudgets[i].category] = i + 2;
      }

      const incomingBudgets = Array.isArray(body.budgets) ? body.budgets : [];
      const budgetsToAppend = [];
      for (let i = 0; i < incomingBudgets.length; i++) {
        const b = incomingBudgets[i];
        const cat = String(b.category || "").trim();
        const limitCents = Number(b.monthlyLimitCents) || 0;
        if (!cat || limitCents <= 0) continue;

        const bRow = [cat, limitCents / 100.0, limitCents];
        const existingRowNum = budgetRowByCat[cat];
        if (existingRowNum) {
          budgetSheet.getRange(existingRowNum, 1, 1, 3).setValues([bRow]);
        } else {
          budgetsToAppend.push(bRow);
        }
      }

      if (budgetsToAppend.length > 0) {
        const startRow = budgetSheet.getLastRow() + 1;
        budgetSheet.getRange(startRow, 1, budgetsToAppend.length, 3).setValues(budgetsToAppend);
      }

      return jsonResponse_({
        status: "ok",
        expenses: readAllExpenses_(expSheet),
        budgets: readAllBudgets_(budgetSheet)
      });
    }

    return jsonResponse_({ status: "error", error: "Unknown POST action: " + action });
  } catch (err) {
    return jsonResponse_({ status: "error", error: String(err) });
  } finally {
    lock.releaseLock();
  }
}
