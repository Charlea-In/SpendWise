package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DetectedTransactionStatus {
    PENDING,
    APPROVED,
    DISMISSED;

    companion object {
        fun fromKey(raw: String): DetectedTransactionStatus {
            return entries.firstOrNull { it.name == raw } ?: PENDING
        }
    }
}

@Entity(tableName = "detected_transactions")
data class DetectedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fingerprint: String,
    val sourcePackage: String,
    val sourceAppLabel: String,
    val rawTitle: String,
    val rawMessage: String,
    val merchant: String,
    val amountCents: Long,
    val suggestedCategory: String,
    val paymentMethod: String,
    val cardLastFour: String = "",
    val matchedAccountName: String = "",
    val status: String = DetectedTransactionStatus.PENDING.name,
    val receivedAtMillis: Long = System.currentTimeMillis()
)
