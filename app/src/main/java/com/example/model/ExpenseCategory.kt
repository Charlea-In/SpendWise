package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CatEntertainment
import com.example.ui.theme.CatFoodDining
import com.example.ui.theme.CatGroceries
import com.example.ui.theme.CatHealth
import com.example.ui.theme.CatHousing
import com.example.ui.theme.CatOther
import com.example.ui.theme.CatShopping
import com.example.ui.theme.CatTransportation
import com.example.ui.theme.CatUtilities

enum class ExpenseCategory(
    val displayName: String,
    val icon: ImageVector,
    val accentColor: Color,
    val suggestedMonthlyCents: Long
) {
    GROCERIES(
        displayName = "Groceries",
        icon = Icons.Rounded.LocalGroceryStore,
        accentColor = CatGroceries,
        suggestedMonthlyCents = 450_00L
    ),
    FOOD_DINING(
        displayName = "Food & Dining",
        icon = Icons.Rounded.Restaurant,
        accentColor = CatFoodDining,
        suggestedMonthlyCents = 300_00L
    ),
    HOUSING(
        displayName = "Housing",
        icon = Icons.Rounded.Home,
        accentColor = CatHousing,
        suggestedMonthlyCents = 1200_00L
    ),
    UTILITIES(
        displayName = "Utilities",
        icon = Icons.Rounded.Bolt,
        accentColor = CatUtilities,
        suggestedMonthlyCents = 180_00L
    ),
    TRANSPORTATION(
        displayName = "Transportation",
        icon = Icons.Rounded.DirectionsCar,
        accentColor = CatTransportation,
        suggestedMonthlyCents = 160_00L
    ),
    ENTERTAINMENT(
        displayName = "Entertainment",
        icon = Icons.Rounded.Theaters,
        accentColor = CatEntertainment,
        suggestedMonthlyCents = 120_00L
    ),
    SHOPPING(
        displayName = "Shopping",
        icon = Icons.Rounded.ShoppingBag,
        accentColor = CatShopping,
        suggestedMonthlyCents = 200_00L
    ),
    HEALTH(
        displayName = "Health",
        icon = Icons.Rounded.Favorite,
        accentColor = CatHealth,
        suggestedMonthlyCents = 120_00L
    ),
    OTHER(
        displayName = "Other",
        icon = Icons.Rounded.Category,
        accentColor = CatOther,
        suggestedMonthlyCents = 100_00L
    );

    companion object {
        fun fromKey(raw: String?): ExpenseCategory {
            if (raw.isNullOrBlank()) return OTHER
            return entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) ||
                    it.displayName.equals(raw, ignoreCase = true)
            } ?: OTHER
        }
    }
}

enum class PaymentMethod(
    val displayName: String,
    val icon: ImageVector
) {
    CARD("Card", Icons.Rounded.CreditCard),
    CASH("Cash", Icons.Rounded.Payments),
    WALLET("Digital Wallet", Icons.Rounded.Smartphone),
    BANK("Bank Transfer", Icons.Rounded.AccountBalanceWallet);

    companion object {
        fun fromKey(raw: String?): PaymentMethod {
            if (raw.isNullOrBlank()) return CARD
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CARD
        }
    }
}

enum class DailyBudgetMode(val displayName: String, val subtitle: String) {
    AUTO_PACED(
        displayName = "Auto-Paced Daily",
        subtitle = "Adjusts each day from remaining monthly budget & days left"
    ),
    FIXED_DAILY(
        displayName = "Fixed Daily Cap",
        subtitle = "Uses a consistent daily spending target every day"
    );

    companion object {
        fun fromKey(raw: String?): DailyBudgetMode {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO_PACED
        }
    }
}

data class QuickPreset(
    val label: String,
    val description: String,
    val amountCents: Long,
    val category: ExpenseCategory
)

val DefaultQuickPresets = listOf(
    QuickPreset("Coffee", "Morning coffee", 4_75L, ExpenseCategory.FOOD_DINING),
    QuickPreset("Lunch", "Quick lunch", 14_50L, ExpenseCategory.FOOD_DINING),
    QuickPreset("Transit", "Metro / transit fare", 3_25L, ExpenseCategory.TRANSPORTATION),
    QuickPreset("Groceries", "Fresh groceries", 28_00L, ExpenseCategory.GROCERIES)
)
