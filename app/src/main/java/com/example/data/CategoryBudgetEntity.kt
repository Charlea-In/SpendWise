package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimitCents: Long,
    val alert80SentMonth: String = "",
    val alert100SentMonth: String = ""
)
