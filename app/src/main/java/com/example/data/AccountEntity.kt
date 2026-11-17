package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val initialBalanceCents: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)
