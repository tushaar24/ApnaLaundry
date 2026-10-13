package com.dailyworks.apnalaundry.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlin.math.roundToInt

/** Where each tour target sits on screen, keyed by name (root coordinates, clipped to what's visible). */
@Stable
class SpotlightTargets {
    internal val rects = mutableStateMapOf<String, Rect>()
}

@Composable
fun rememberSpotlightTargets() = remember { SpotlightTargets() }

/** Marks this element as a tour target the [Spotlight] can point at. */
fun Modifier.spotlightTarget(targets: SpotlightTargets, key: String): Modifier =
    onGloballyPositioned { targets.rects[key] = it.boundsInRoot() }

/**
 * A tour step: dims the screen, cuts a softly pulsing hole around [target],
 * and shows a card with the step's words. Taps inside the hole reach the real
 * button underneath, so the owner does each step themselves; taps on the dark
 * part do nothing. [target] null (or not on screen yet) = a centred card only.
 * [nextLabel] null = no button: the step moves on when the owner taps the
 * highlighted part.
 */
@Composable
fun Spotlight(
    targets: SpotlightTargets,
    target: String?,
    step: Int,
    steps: Int,
    title: String,
    body: String,
    nextLabel: String?,
    onNext: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var cardH by remember { mutableIntStateOf(0) }

    val pad = with(density) { 8.dp.toPx() }
    val screen = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
    val hole = target?.let { targets.rects[it] }
        ?.translate(-origin)?.inflate(pad)?.intersect(screen)
        ?.takeIf { it.width > 0f && it.height > 0f }

    val pulse by rememberInfiniteTransition(label = "spot").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse",
    )

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); size = it.size },
    ) {
        // Dim layer with a clear hole — draws only, so it never takes a touch.
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.68f))
            if (hole != null) {
                val r = CornerRadius(16.dp.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, r, blendMode = BlendMode.Clear)
                val grow = 3.dp.toPx() + pulse * 4.dp.toPx()
                val ring = hole.inflate(grow)
                drawRoundRect(
                    Tokens.BlueBar.copy(alpha = 0.95f - pulse * 0.5f), ring.topLeft, ring.size,
                    CornerRadius(r.x + grow), style = Stroke(3.dp.toPx()),
                )
            }
        }

        // Touch blockers on every side of the hole (or the whole screen without one).
        if (hole == null) {
            Blocker(Rect(0f, 0f, screen.right, screen.bottom))
        } else {
            Blocker(Rect(0f, 0f, screen.right, hole.top))
            Blocker(Rect(0f, hole.bottom, screen.right, screen.bottom))
            Blocker(Rect(0f, hole.top, hole.left, hole.bottom))
            Blocker(Rect(hole.right, hole.top, screen.right, hole.bottom))
        }

        // The card: below the hole when there's room, else above, else centred.
        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        val top = margin + with(density) { 24.dp.toPx() } // clear of the status bar
        val y = when {
            hole == null -> (screen.height - cardH) / 2f
            screen.bottom - hole.bottom >= hole.top -> hole.bottom + gap
            else -> hole.top - gap - cardH
        }.coerceIn(top, maxOf(top, screen.height - cardH - margin))

        Column(
            Modifier
                .offset { IntOffset(0, y.roundToInt()) }
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .onSizeChanged { cardH = it.height }
                .rounded(18.dp)
                .background(Tokens.Card)
                .swallowTouches()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps) { i ->
                    Box(
                        Modifier.size(width = if (i + 1 == step) 18.dp else 6.dp, height = 6.dp).rounded(999.dp)
                            .background(if (i + 1 <= step) Tokens.Blue else Tokens.NeutralFill),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("Step $step of $steps", style = fig(12, FontWeight.Bold, Tokens.Muted))
            }
            Text(title, style = bric(20, FontWeight.Bold), modifier = Modifier.padding(top = 2.dp))
            Text(body, style = fig(15, color = Tokens.InkSecondary))
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (onSkip != null) {
                    Text("Skip tour", style = fig(14, FontWeight.Bold, Tokens.Muted), modifier = Modifier.tap(onClick = onSkip).padding(vertical = 10.dp, horizontal = 2.dp))
                }
                Spacer(Modifier.weight(1f))
                if (nextLabel != null) {
                    Box(
                        Modifier.height(44.dp).rounded(12.dp).background(Tokens.Blue).tap(onClick = onNext).padding(horizontal = 22.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { Text(nextLabel, style = fig(15, FontWeight.Bold, Tokens.OnDark)) }
                } else {
                    Text("👆 Tap the bright part", style = fig(14, FontWeight.Bold, Tokens.Blue))
                }
            }
        }
    }
}

/** Swallows every touch inside [r] (overlay coordinates). */
@Composable
private fun Blocker(r: Rect) {
    if (r.width <= 0f || r.height <= 0f) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
            .swallowTouches(),
    )
}

/** Takes every touch here, so nothing underneath gets it. */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        while (true) {
            val e = awaitPointerEvent()
            e.changes.forEach { it.consume() }
            if (e.changes.all { !it.pressed }) break
        }
    }
}
