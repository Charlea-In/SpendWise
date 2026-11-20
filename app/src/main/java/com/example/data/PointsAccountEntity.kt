package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Entity(tableName = "points_accounts")
data class PointsAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val remoteId: String = UUID.randomUUID().toString(),
    val name: String,
    val pointsBalance: Long = 0L,
    val unitLabel: String = "pts",
    val centsPerPointTimes100: Long = 0L,
    val note: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
) {
    val estimatedValueCents: Long
        get() = if (centsPerPointTimes100 > 0L && pointsBalance > 0L) {
            (pointsBalance * centsPerPointTimes100) / 100L
        } else {
            0L
        }
}

object PointsFormatter {
    fun formatCount(points: Long): String {
        return NumberFormat.getIntegerInstance(Locale.US).format(points)
    }

    fun formatWithUnit(points: Long, unitLabel: String): String {
        val cleanUnit = unitLabel.trim().ifBlank { "pts" }
        return "${formatCount(points)} $cleanUnit"
    }

    fun formatFreshness(updatedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diffMs = (nowMillis - updatedAtMillis).coerceAtLeast(0L)
        val days = (diffMs / 86_400_000L).toInt()
        return when {
            days <= 0 -> "Updated today"
            days == 1 -> "Updated 1d ago"
            days < 30 -> "Updated ${days}d ago"
            else -> "Updated ${days / 30}mo ago"
        }
    }

    fun parseCentsPerPointTimes100(raw: String): Long {
        val clean = raw.trim()
        if (clean.isEmpty()) return 0L
        val parsed = clean.toDoubleOrNull() ?: return 0L
        if (parsed <= 0.0) return 0L
        return kotlin.math.round(parsed * 100.0).toLong().coerceAtLeast(0L)
    }

    fun centsPerPointToEditText(centsPerPointTimes100: Long): String {
        if (centsPerPointTimes100 <= 0L) return ""
        val whole = centsPerPointTimes100 / 100L
        val frac = centsPerPointTimes100 % 100L
        return if (frac == 0L) {
            whole.toString()
        } else {
            String.format(Locale.US, "%.2f", centsPerPointTimes100 / 100.0).trimEnd('0').trimEnd('.')
        }
    }
}
