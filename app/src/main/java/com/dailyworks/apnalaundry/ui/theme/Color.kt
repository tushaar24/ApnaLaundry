package com.dailyworks.apnalaundry.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens from PRODUCT_SPEC.md §13 and the prototype's inline styles.
 * These are the single source of truth for colour across the app.
 */
object Tokens {
    // Surfaces
    val Bg = Color(0xFFF5F3EE)           // app background
    val Card = Color(0xFFFFFFFF)         // cards
    val CardBorder = Color(0xFFE2DED6)   // 1px card border
    val Divider = Color(0xFFEEEBE4)
    val FieldBorder = Color(0xFFD5D0C6)
    val DashBorder = Color(0xFFB9B3A8)
    val SegTrack = Color(0xFFEAE6DE)
    val NeutralFill = Color(0xFFEEEBE4)

    // Ink / text
    val Ink = Color(0xFF16191D)          // primary text + selected fills
    val InkSecondary = Color(0xFF3F444A)
    val Muted = Color(0xFF5B6168)
    val Faint = Color(0xFF8A8F95)
    val MutedDot = Color(0xFF6B7178)

    // Action blue
    val Blue = Color(0xFF1D4ED8)
    val BlueDisabled = Color(0xFF9AAEE6)
    val BlueLight = Color(0xFFE6ECFB)    // next-step buttons / positive states
    val BlueText = Color(0xFF1E3A8A)     // text on light blue
    val BlueBorder = Color(0xFFC9D4F4)
    val BlueBar = Color(0xFF9DB5F5)

    // Warning orange
    val Orange = Color(0xFFC2410C)       // dots, badges
    val OrangeText = Color(0xFF8A3A06)
    val OrangeDeep = Color(0xFF6B2D05)
    val OrangeLight = Color(0xFFFBEEDC)  // baaki / late / unpaid backgrounds
    val OrangeBorder = Color(0xFFE8C9A6)
    val OrangeBar = Color(0xFFF6B98A)
    val DeleteRed = Color(0xFF9A3412)

    // Login (blue hero) states
    val Green = Color(0xFF16A34A)
    val GreenText = Color(0xFF15803D)
    val ErrorRed = Color(0xFFB42318)
    val OnBlueMuted = Color(0xFFDCE5FB)
    val Placeholder = Color(0xFF8A847A)
    val DisabledFg = Color(0xFF6B655C)

    // On-dark
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Color(0xFFD9D6CF)
    val OnDarkFaint = Color(0xFFC9C5BD)
    val DarkChipTrack = Color(0xFF3A3F46)

    val ServiceBarGrey = Color(0xFF9A948A)
    val ScrimColor = Color(0x66161921)
}
