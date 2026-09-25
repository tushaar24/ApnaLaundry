package com.dailyworks.apnalaundry.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

private val LightColors = lightColorScheme(
    primary = Tokens.Blue,
    onPrimary = Tokens.OnDark,
    secondary = Tokens.Ink,
    onSecondary = Tokens.OnDark,
    background = Tokens.Bg,
    onBackground = Tokens.Ink,
    surface = Tokens.Card,
    onSurface = Tokens.Ink,
    error = Tokens.Orange,
    outline = Tokens.CardBorder,
)

private val AppTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Bricolage),
        displayMedium = base.displayMedium.copy(fontFamily = Bricolage),
        displaySmall = base.displaySmall.copy(fontFamily = Bricolage),
        headlineLarge = base.headlineLarge.copy(fontFamily = Bricolage),
        headlineMedium = base.headlineMedium.copy(fontFamily = Bricolage),
        headlineSmall = base.headlineSmall.copy(fontFamily = Bricolage),
        titleLarge = base.titleLarge.copy(fontFamily = Figtree),
        titleMedium = base.titleMedium.copy(fontFamily = Figtree),
        titleSmall = base.titleSmall.copy(fontFamily = Figtree),
        bodyLarge = base.bodyLarge.copy(fontFamily = Figtree),
        bodyMedium = base.bodyMedium.copy(fontFamily = Figtree),
        bodySmall = base.bodySmall.copy(fontFamily = Figtree),
        labelLarge = base.labelLarge.copy(fontFamily = Figtree),
        labelMedium = base.labelMedium.copy(fontFamily = Figtree),
        labelSmall = base.labelSmall.copy(fontFamily = Figtree),
    )
}

/** Shared text style used as the app default (Figtree, ink). */
val DefaultTextStyle = TextStyle(fontFamily = Figtree, color = Tokens.Ink)

@Composable
fun ApnaLaundryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
