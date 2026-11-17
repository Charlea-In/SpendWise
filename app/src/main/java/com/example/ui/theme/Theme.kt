package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class SpendWiseSemanticColors(
    val positive: Color,
    val positiveContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val cardBorder: Color,
    val heroStart: Color,
    val heroEnd: Color
)

private val LightSemanticColors = SpendWiseSemanticColors(
    positive = DeepForestPrimary,
    positiveContainer = SoftSageContainer,
    warning = BurnishedGoldTertiary,
    warningContainer = GoldSoftContainer,
    danger = TerracottaError,
    dangerContainer = TerracottaSoftContainer,
    cardBorder = HairlineOutline,
    heroStart = DeepForestPrimary,
    heroEnd = PineSecondary
)

private val DarkSemanticColors = SpendWiseSemanticColors(
    positive = MintEmeraldPrimary,
    positiveContainer = SoftSageDarkContainer,
    warning = WarmGoldDark,
    warningContainer = GoldDarkContainer,
    danger = CoralErrorDark,
    dangerContainer = CoralDarkContainer,
    cardBorder = PineHairlineDark,
    heroStart = PineElevatedContainer,
    heroEnd = SoftSageDarkContainer
)

val LocalSpendWiseColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = DeepForestPrimary,
    onPrimary = Color.White,
    primaryContainer = SoftSageContainer,
    onPrimaryContainer = DeepForestPrimary,
    secondary = PineSecondary,
    onSecondary = Color.White,
    secondaryContainer = MutedSandContainer,
    onSecondaryContainer = EspressoInk,
    tertiary = BurnishedGoldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = GoldSoftContainer,
    onTertiaryContainer = EspressoInk,
    background = IvoryBackground,
    onBackground = EspressoInk,
    surface = WarmPaperSurface,
    onSurface = EspressoInk,
    surfaceVariant = MutedSandContainer,
    onSurfaceVariant = SlateMutedInk,
    outline = HairlineOutline,
    outlineVariant = HairlineOutline,
    error = TerracottaError,
    onError = Color.White,
    errorContainer = TerracottaSoftContainer,
    onErrorContainer = TerracottaError
)

private val DarkColorScheme = darkColorScheme(
    primary = MintEmeraldPrimary,
    onPrimary = MintOnPrimary,
    primaryContainer = SoftSageDarkContainer,
    onPrimaryContainer = MintEmeraldPrimary,
    secondary = MintEmeraldPrimary,
    onSecondary = MintOnPrimary,
    secondaryContainer = PineElevatedContainer,
    onSecondaryContainer = IvoryInkDark,
    tertiary = WarmGoldDark,
    onTertiary = EspressoInk,
    tertiaryContainer = GoldDarkContainer,
    onTertiaryContainer = WarmGoldDark,
    background = DeepPineBackground,
    onBackground = IvoryInkDark,
    surface = PineCardSurface,
    onSurface = IvoryInkDark,
    surfaceVariant = PineElevatedContainer,
    onSurfaceVariant = SageMutedInkDark,
    outline = PineHairlineDark,
    outlineVariant = PineHairlineDark,
    error = CoralErrorDark,
    onError = CoralDarkContainer,
    errorContainer = CoralDarkContainer,
    onErrorContainer = CoralErrorDark
)

val SpendWiseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SpendWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSpendWiseColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpendWiseTypography,
            shapes = SpendWiseShapes,
            content = content
        )
    }
}
