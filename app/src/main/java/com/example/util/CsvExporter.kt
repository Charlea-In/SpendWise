package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.ExpenseEntity
import com.example.model.ExpenseCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun shareExpensesCsv(context: Context, expenses: List<ExpenseEntity>, currencySymbol: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("Date,Description,Category,Amount ($currencySymbol),Account,Payment Method,Note")
        for (item in expenses) {
            val date = dateFormat.format(Date(item.timestampMillis))
            val desc = escapeCsv(item.description)
            val cat = escapeCsv(ExpenseCategory.fromKey(item.category).displayName)
            val amt = MoneyFormatter.centsToEditText(item.amountCents)
            val account = escapeCsv(item.accountName)
            val method = escapeCsv(item.paymentMethod)
            val note = escapeCsv(item.note)
            sb.appendLine("$date,$desc,$cat,$amt,$account,$method,$note")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "SpendWise Daily Expenses Export")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        val chooser = Intent.createChooser(sendIntent, "Export Expenses CSV")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return if (clean.contains(",") || clean.contains("\"") || clean.contains("\n")) {
            "\"$clean\""
        } else {
            clean
        }
    }
}
