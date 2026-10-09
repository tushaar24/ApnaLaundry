package com.dailyworks.apnalaundry.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** A dashed rounded border (web's `dash-border`). */
fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
    val stroke = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f)))
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(radius.toPx()))
}
