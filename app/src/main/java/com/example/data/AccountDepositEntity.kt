package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "account_deposits")
data class AccountDepositEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val remoteId: String = UUID.randomUUID().toString(),
    val accountName: String,
    val source: String,
    val amountCents: Long,
    val timestampMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val isDemo: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
