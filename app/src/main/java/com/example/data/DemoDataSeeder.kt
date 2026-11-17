package com.example.data

import com.example.model.ExpenseCategory
import com.example.model.PaymentMethod
import java.util.Calendar
import java.util.UUID

object DemoDataSeeder {

    fun buildDemoExpenses(nowMillis: Long = System.currentTimeMillis()): List<ExpenseEntity> {
        fun daysAgoAt(daysAgo: Int, hour: Int, minute: Int): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        return listOf(
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Blue Bottle Coffee",
                amountCents = 5_75L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = PaymentMethod.WALLET.name,
                timestampMillis = daysAgoAt(0, 8, 40),
                note = "Oat latte & croissant",
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Trader Joe's",
                amountCents = 48_60L,
                category = ExpenseCategory.GROCERIES.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(0, 13, 15),
                note = "Weekly produce & snacks",
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Metro Transit Pass",
                amountCents = 3_25L,
                category = ExpenseCategory.TRANSPORTATION.name,
                paymentMethod = PaymentMethod.WALLET.name,
                timestampMillis = daysAgoAt(0, 17, 5),
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Sweetgreen Lunch",
                amountCents = 16_80L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(1, 12, 30),
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "AMC Cinema Tickets",
                amountCents = 34_00L,
                category = ExpenseCategory.ENTERTAINMENT.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(1, 19, 45),
                note = "Evening movie",
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Whole Foods Market",
                amountCents = 64_90L,
                category = ExpenseCategory.GROCERIES.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(2, 16, 20),
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "City Electric & Fiber",
                amountCents = 92_40L,
                category = ExpenseCategory.UTILITIES.name,
                paymentMethod = PaymentMethod.BANK.name,
                timestampMillis = daysAgoAt(3, 9, 10),
                note = "Monthly electric bill",
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Uber Ride",
                amountCents = 22_50L,
                category = ExpenseCategory.TRANSPORTATION.name,
                paymentMethod = PaymentMethod.WALLET.name,
                timestampMillis = daysAgoAt(3, 21, 15),
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Ramen Night",
                amountCents = 42_00L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(4, 20, 0),
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "CVS Pharmacy",
                amountCents = 24_30L,
                category = ExpenseCategory.HEALTH.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(5, 11, 25),
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Spotify & Streaming",
                amountCents = 28_00L,
                category = ExpenseCategory.ENTERTAINMENT.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(6, 10, 0),
                wasAutoCategorized = true,
                isDemo = true
            ),
            ExpenseEntity(
                remoteId = "demo-${UUID.randomUUID()}",
                description = "Sushi Bento",
                amountCents = 19_50L,
                category = ExpenseCategory.FOOD_DINING.name,
                paymentMethod = PaymentMethod.CARD.name,
                timestampMillis = daysAgoAt(6, 13, 40),
                wasAutoCategorized = true,
                isDemo = true
            )
        )
    }

    fun buildDemoBudgets(): List<CategoryBudgetEntity> {
        return listOf(
            CategoryBudgetEntity(ExpenseCategory.GROCERIES.name, 250_00L),
            // 5.75 + 16.80 + 42.00 + 19.50 = 84.05 against $100 -> 84% (Approaching warning!)
            CategoryBudgetEntity(ExpenseCategory.FOOD_DINING.name, 100_00L),
            CategoryBudgetEntity(ExpenseCategory.UTILITIES.name, 150_00L),
            CategoryBudgetEntity(ExpenseCategory.TRANSPORTATION.name, 100_00L),
            // 34 + 28 = 62 against $60 -> 103% (Exceeded demo alert!)
            CategoryBudgetEntity(ExpenseCategory.ENTERTAINMENT.name, 60_00L)
        )
    }
}
