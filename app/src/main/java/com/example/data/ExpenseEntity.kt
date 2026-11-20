package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String = UUID.randomUUID().toString(),
    val description: String,
    val amountCents: Long,
    val category: String,
    val paymentMethod: String,
    val accountName: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val wasAutoCategorized: Boolean = false,
    val isDemo: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
