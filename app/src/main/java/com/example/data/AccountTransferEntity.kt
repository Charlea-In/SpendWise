package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_transfers")
data class AccountTransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fromAccountName: String,
    val toAccountName: String,
    val amountCents: Long,
    val timestampMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val isDemo: Boolean = false
)
