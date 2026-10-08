package com.dailyworks.mylaundry.support.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dailyworks.mylaundry.support.R

/** MyLaundry design tokens (same values as the main app's ui/theme/Color.kt). */
object Tokens {
    val Bg = Color(0xFFF5F3EE)
    val Card = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFE2DED6)
    val Divider = Color(0xFFEEEBE4)
    val Ink = Color(0xFF16191D)
    val InkSecondary = Color(0xFF3F444A)
    val Muted = Color(0xFF5B6168)
    val Faint = Color(0xFF8A8F95)
    val Blue = Color(0xFF1D4ED8)
    val BlueLight = Color(0xFFE6ECFB)
    val BlueText = Color(0xFF1E3A8A)
    val Green = Color(0xFF16A34A)
    val GreenLight = Color(0xFFDCFCE7)
    val GreenText = Color(0xFF15803D)
    val Orange = Color(0xFFC2410C)
    val OrangeLight = Color(0xFFFBEEDC)
    val OrangeText = Color(0xFF8A3A06)
    val Red = Color(0xFFB42318)
    val RedLight = Color(0xFFFDE8E7)
    val Purple = Color(0xFF7C3AED)
    val PurpleLight = Color(0xFFEDE7FD)
    val Neutral = Color(0xFFEEEBE4)
    val OnDark = Color(0xFFFFFFFF)
}

private fun figtree(w: Int) = Font(R.font.figtree, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
private fun bricolage(w: Int) = Font(R.font.bricolage_grotesque, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))
val Bricolage = FontFamily(bricolage(600), bricolage(700))

fun bric(size: Int, weight: FontWeight = FontWeight.Bold, color: Color = Tokens.Ink) =
    TextStyle(fontFamily = Bricolage, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = (-0.02).em, lineHeight = (size * 1.15f).sp)

fun fig(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Tokens.Ink) =
    TextStyle(fontFamily = Figtree, fontSize = size.sp, fontWeight = weight, color = color, lineHeight = (size * 1.35f).sp)

private val colors = lightColorScheme(
    primary = Tokens.Blue,
    onPrimary = Tokens.OnDark,
    primaryContainer = Tokens.BlueLight,
    onPrimaryContainer = Tokens.BlueText,
    secondaryContainer = Tokens.BlueLight,
    onSecondaryContainer = Tokens.BlueText,
    background = Tokens.Bg,
    onBackground = Tokens.Ink,
    surface = Tokens.Bg,
    onSurface = Tokens.Ink,
    surfaceVariant = Tokens.Card,
    onSurfaceVariant = Tokens.Muted,
    surfaceContainer = Tokens.Card,
    surfaceContainerLow = Tokens.Card,
    surfaceContainerHigh = Tokens.Card,
    surfaceContainerHighest = Tokens.Card,
    outline = Tokens.CardBorder,
    outlineVariant = Tokens.Divider,
    error = Tokens.Red,
)

@Composable
fun SupportTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            titleLarge = base.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontFamily = Figtree),
            bodyMedium = base.bodyMedium.copy(fontFamily = Figtree),
            bodySmall = base.bodySmall.copy(fontFamily = Figtree),
            labelLarge = base.labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
            labelMedium = base.labelMedium.copy(fontFamily = Figtree),
            labelSmall = base.labelSmall.copy(fontFamily = Figtree),
        ),
        content = content,
    )
}
