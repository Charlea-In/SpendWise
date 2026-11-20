package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.CommitmentType
import com.example.model.ExpenseCategory
import com.example.model.RecurrenceCycle
import java.util.UUID

@Entity(tableName = "financial_commitments")
data class CommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String = UUID.randomUUID().toString(),
    val title: String,
    val type: String = CommitmentType.DEBT.name,
    val amountCents: Long,
    val targetOrTotalCents: Long = 0L,
    val progressCents: Long = 0L,
    val cycle: String = RecurrenceCycle.MONTHLY.name,
    val dueDayOfMonth: Int = 1,
    val statementDayOfMonth: Int = 0,
    val scheduledDateMillis: Long = 0L,
    val totalInstallments: Int = 0,
    val completedInstallments: Int = 0,
    val accountName: String = "",
    val category: String = ExpenseCategory.OTHER.name,
    val note: String = "",
    val reminderEnabled: Boolean = true,
    val isActive: Boolean = true,
    val lastCompletedCycleKey: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
