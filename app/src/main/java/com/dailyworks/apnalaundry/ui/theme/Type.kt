package com.dailyworks.apnalaundry.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.dailyworks.apnalaundry.R

/**
 * Bricolage Grotesque (headings, big numbers) + Figtree (everything else).
 * Both are bundled variable fonts; weights are pulled from the `wght` axis
 * (applies on API 26+, gracefully falls back to the default instance below).
 */

private fun figtree(weight: Int) = Font(
    R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Figtree = FontFamily(
    figtree(400),
    figtree(500),
    figtree(600),
    figtree(700),
)

val Bricolage = FontFamily(
    bricolage(600),
    bricolage(700),
)
