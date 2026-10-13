package com.dailyworks.apnalaundry.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** A dashed rounded border (web's `dash-border`). */
fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
    // Dashes in dp (the same on every phone); the line drawn inside the edge, not half cut off.
    val w = width.toPx()
    val stroke = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    drawRoundRect(
        color = color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        style = stroke, cornerRadius = CornerRadius((radius.toPx() - w / 2).coerceAtLeast(0f)),
    )
}
