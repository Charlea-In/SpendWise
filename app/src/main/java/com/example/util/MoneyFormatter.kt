package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object MoneyFormatter {

    private val amountFormat = DecimalFormat("#,##0.00")
    private val wholeFormat = DecimalFormat("#,##0")

    fun format(cents: Long, currencySymbol: String = "$", compactWhole: Boolean = false): String {
        val sign = if (cents < 0L) "-" else ""
        val absDollars = abs(cents) / 100.0
        val formatted = if (compactWhole && abs(cents) % 100L == 0L) {
            wholeFormat.format(abs(cents) / 100L)
        } else {
            amountFormat.format(absDollars)
        }
        return "$sign$currencySymbol$formatted"
    }

    fun parseCents(input: String): Long? {
        val sanitized = input.trim().replace(",", "")
        if (sanitized.isBlank()) return null
        val value = sanitized.toDoubleOrNull() ?: return null
        if (value <= 0.0 || value.isNaN() || value.isInfinite()) return null
        return (value * 100.0).roundToLong()
    }

    fun centsToEditText(cents: Long): String {
        if (cents <= 0L) return ""
        return if (cents % 100L == 0L) {
            (cents / 100L).toString()
        } else {
            String.format(Locale.US, "%.2f", cents / 100.0)
        }
    }
}

object DateUtils {
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val headerDateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    private val shortDayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val shortMonthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val monthTitleFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun currentMonthKey(nowMillis: Long = System.currentTimeMillis()): String {
        return monthKeyFormat.format(Date(nowMillis))
    }

    fun currentMonthTitle(nowMillis: Long = System.currentTimeMillis()): String {
        return monthTitleFormat.format(Date(nowMillis))
    }

    fun formatHeaderDate(millis: Long): String {
        return headerDateFormat.format(Date(millis))
    }

    fun formatTime(millis: Long): String {
        return timeFormat.format(Date(millis))
    }

    fun formatShortWeekday(millis: Long): String {
        return shortDayFormat.format(Date(millis))
    }

    fun formatShortMonthDay(millis: Long): String {
        return shortMonthDayFormat.format(Date(millis))
    }

    fun dayKey(millis: Long): String {
        return dayKeyFormat.format(Date(millis))
    }

    fun daySectionLabel(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val todayKey = dayKey(nowMillis)
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayKey = dayKey(yesterdayCal.timeInMillis)
        val key = dayKey(millis)
        return when (key) {
            todayKey -> "Today"
            yesterdayKey -> "Yesterday"
            else -> headerDateFormat.format(Date(millis))
        }
    }

    fun startOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun startOfMonthMillis(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun daysInCurrentMonth(millis: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun currentDayOfMonth(millis: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    fun remainingDaysInMonthIncludingToday(millis: Long = System.currentTimeMillis()): Int {
        val total = daysInCurrentMonth(millis)
        val current = currentDayOfMonth(millis)
        return (total - current + 1).coerceAtLeast(1)
    }

    fun lastNDaysStarts(days: Int, nowMillis: Long = System.currentTimeMillis()): List<Long> {
        val result = mutableListOf<Long>()
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis(nowMillis)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }
        repeat(days) {
            result.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }
}
