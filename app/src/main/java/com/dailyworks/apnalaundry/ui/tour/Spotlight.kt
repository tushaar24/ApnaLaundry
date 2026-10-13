package com.dailyworks.apnalaundry.ui.tour

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** Where each guide target sits on screen, keyed by name (root coordinates, clipped to what's visible). */
@Stable
class SpotlightTargets {
    internal val rects = mutableStateMapOf<String, Rect>()
}

@Composable
fun rememberSpotlightTargets() = remember { SpotlightTargets() }

/** Marks this element as something the guide can point at; forgotten once it leaves the screen. */
fun Modifier.spotlightTarget(targets: SpotlightTargets, key: String): Modifier = composed {
    DisposableEffect(targets, key) { onDispose { targets.rects.remove(key) } }
    onGloballyPositioned { targets.rects[key] = it.boundsInRoot() }
}

private val ArrowYellow = Color(0xFFFFC928)
private val TipBg = Color(0xFFFFF4CC)
private val TipBorder = Color(0xFFF5C84C)
private val TipText = Color(0xFF8A5A00)

/**
 * A guide card over the screen: dims everything, cuts a pulsing hole around
 * [target] with a big bouncing arrow at it, and shows the step's card. Taps
 * inside the hole reach the real button underneath, so the owner does each
 * step themselves; taps on the dark part do nothing. [target] null (or not on
 * screen yet) = a centred card only.
 *
 * Buttons: [next] (blue), [alt] (outlined, e.g. "Later"), and "Skip guide"
 * when [onSkip] is set. Without [next] the step moves on when the owner taps
 * the real button.
 */
@Composable
fun Spotlight(
    targets: SpotlightTargets,
    target: String?,
    step: TourStep,
    next: String? = null,
    onNext: () -> Unit = {},
    alt: String? = null,
    onAlt: () -> Unit = {},
    onSkip: (() -> Unit)? = null,
) {
    AnnounceStep(step)
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
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
            drawRect(Color.Black.copy(alpha = 0.72f))
            if (hole != null) {
                val r = CornerRadius(16.dp.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, r, blendMode = BlendMode.Clear)
                val grow = 3.dp.toPx() + pulse * 5.dp.toPx()
                val ring = hole.inflate(grow)
                drawRoundRect(
                    ArrowYellow.copy(alpha = 1f - pulse * 0.45f), ring.topLeft, ring.size,
                    CornerRadius(r.x + grow), style = Stroke(3.dp.toPx()),
                )
            }
        }

        // Touch blockers on every side of the hole (or the whole screen for a
        // card with no target). A target that isn't on screen (scrolled away)
        // gets no blocker, so the owner can scroll to it instead of being stuck.
        if (hole == null) {
            if (target == null) Blocker(Rect(0f, 0f, screen.right, screen.bottom))
        } else {
            Blocker(Rect(0f, 0f, screen.right, hole.top))
            Blocker(Rect(0f, hole.bottom, screen.right, screen.bottom))
            Blocker(Rect(0f, hole.top, hole.left, hole.bottom))
            Blocker(Rect(hole.right, hole.top, screen.right, hole.bottom))
        }

        // The card: below the hole when it fits there, else above, else on the
        // roomier side (capped to that room, its words scroll); the arrow sits
        // in the gap between them. The keyboard counts as the bottom edge.
        val margin = with(density) { 16.dp.toPx() }
        val arrowW = with(density) { ARROW_W.toPx() }
        val arrowH = with(density) { ARROW_H.toPx() }
        val arrowGap = arrowH + with(density) { 18.dp.toPx() }
        val insetTop = WindowInsets.systemBars.getTop(density).toFloat()
        val insetBottom = WindowInsets.systemBars.union(WindowInsets.ime).getBottom(density).toFloat()
        val spaceAbove = if (hole != null) hole.top - insetTop - margin - arrowGap else 0f
        val spaceBelow = if (hole != null) screen.bottom - insetBottom - margin - hole.bottom - arrowGap else 0f
        val below = hole != null && when {
            cardH in 1..spaceBelow.toInt() -> true
            cardH in 1..spaceAbove.toInt() -> false
            else -> spaceBelow >= spaceAbove
        }
        val maxCardH = with(density) {
            val room = if (hole == null) screen.height - insetTop - insetBottom - 2 * margin else maxOf(spaceAbove, spaceBelow)
            maxOf(room, 220.dp.toPx()).toDp()
        }
        val minY = insetTop + margin
        val y = when {
            hole == null -> (screen.height - cardH) / 2f
            below -> hole.bottom + arrowGap
            else -> hole.top - arrowGap - cardH
        }.coerceIn(minY, maxOf(minY, screen.height - insetBottom - margin - cardH))

        if (hole != null && cardH > 0) {
            val gapPx = with(density) { 6.dp.toPx() }
            val arrowTop = if (below) hole.bottom + gapPx else hole.top - gapPx - arrowH
            val fits = if (below) arrowTop + arrowH <= y else arrowTop >= y + cardH
            if (fits && arrowTop >= insetTop) {
                val ax = (hole.center.x - arrowW / 2f).coerceIn(margin, screen.width - margin - arrowW)
                BouncingArrow(
                    up = below, color = ArrowYellow,
                    modifier = Modifier.offset { IntOffset(ax.roundToInt(), arrowTop.roundToInt()) },
                )
            }
        }

        Column(
            Modifier
                .offset { IntOffset(0, y.roundToInt()) }
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(max = maxCardH)
                .onSizeChanged { cardH = it.height }
                .rounded(20.dp)
                .background(Tokens.Card)
                .swallowTouches()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardHeader(step, Tokens.Blue)
            // The words scroll when the card is short of room; header and buttons always show.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(step.title, style = bric(22, FontWeight.Bold), modifier = Modifier.padding(top = 2.dp))
                Text(step.body, style = fig(16, color = Tokens.InkSecondary))
            }
            // Two buttons need the row: "Skip guide" then goes on its own line.
            val skipBelow = alt != null && next != null
            val skip: @Composable (Modifier) -> Unit = { m ->
                if (onSkip != null) Text(
                    "Skip guide", style = fig(14, FontWeight.Bold, Tokens.Muted), maxLines = 2,
                    modifier = m.tap(onClick = onSkip).padding(vertical = 12.dp, horizontal = 2.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // weight: the buttons are measured first and keep their size; skip gets what's left.
                if (!skipBelow && onSkip != null) skip(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (alt != null) {
                    Box(
                        Modifier.heightIn(min = 48.dp).rounded(12.dp).border(1.5.dp, Tokens.CardBorder, RoundedCornerShape(12.dp))
                            .tap(onClick = onAlt).padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(alt, style = fig(16, FontWeight.Bold), maxLines = 1, softWrap = false) }
                }
                if (next != null) {
                    Box(
                        Modifier.heightIn(min = 48.dp).rounded(12.dp).background(Tokens.Blue).tap(onClick = onNext).padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(next, style = fig(16, FontWeight.Bold, Tokens.OnDark), maxLines = 1, softWrap = false) }
                }
                if (next == null && alt == null && hole != null) {
                    Text(if (below) "👆 Tap there" else "👇 Tap there", style = fig(15, FontWeight.Bold, Tokens.Blue), maxLines = 1, softWrap = false)
                }
            }
            if (skipBelow) skip(Modifier)
        }
    }
}

/**
 * The guide inside a bottom sheet (a sheet opens in its own window, so the
 * dimmed overlay can't cover it): a yellow note with the step's words and a
 * bouncing arrow pointing down at the buttons right below it.
 */
@Composable
fun TourTip(step: TourStep, modifier: Modifier = Modifier) {
    AnnounceStep(step)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.fillMaxWidth().rounded(16.dp).background(TipBg)
                .border(1.5.dp, TipBorder, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CardHeader(step, TipText)
            Text(step.title, style = bric(20, FontWeight.Bold))
            Text(step.body, style = fig(16))
        }
        BouncingArrow(up = false, color = Tokens.Orange, modifier = Modifier.padding(top = 2.dp), width = 30.dp, height = 34.dp)
    }
}

/** "Step 2 of 5 · Clothes ready", the five dots, and the sound button. */
@Composable
private fun CardHeader(step: TourStep, tint: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Step ${step.part.n} of ${TourPart.COUNT} · ${step.part.label}", style = fig(13, FontWeight.Bold, tint))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(TourPart.COUNT) { i ->
                    Box(
                        Modifier.size(width = if (i + 1 == step.part.n) 22.dp else 8.dp, height = 8.dp).rounded(999.dp)
                            .background(if (i + 1 <= step.part.n) tint else Tokens.NeutralFill),
                    )
                }
            }
        }
        SoundButton(step)
    }
}

/** Sound on / off for the guide's voice (remembered). Turning it on reads this card again. */
@Composable
private fun SoundButton(step: TourStep) {
    val voice: TourVoice = koinInject()
    val muted by voice.muted.collectAsStateWithLifecycle()
    Row(
        Modifier.height(44.dp).rounded(999.dp).background(if (muted) Tokens.NeutralFill else Tokens.BlueLight)
            .tap {
                val on = muted
                Analytics.tourSound(on, step.id)
                voice.setMuted(!on) { if (on) voice.say(step.say, step.id) }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
            if (muted) "Turn sound on" else "Turn sound off",
            tint = if (muted) Tokens.InkSecondary else Tokens.Blue, modifier = Modifier.size(18.dp),
        )
        Text(if (muted) "Sound off" else "Sound on", style = fig(13, FontWeight.Bold, if (muted) Tokens.InkSecondary else Tokens.Blue))
    }
}

/**
 * A card appeared: count the view and read it out. Leaving stops only its own
 * words; the app going to the background (WhatsApp opened, home button) stops all.
 */
@Composable
private fun AnnounceStep(step: TourStep) {
    val voice: TourVoice = koinInject()
    LaunchedEffect(step.id) {
        Analytics.tourStepViewed(step.id, step.part.n, step.part.key, sound = !voice.muted.value)
        voice.say(step.say, step.id)
    }
    val id by rememberUpdatedState(step.id)
    DisposableEffect(Unit) { onDispose { voice.stop(id) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) voice.stopAll() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

private val ARROW_W = 44.dp
private val ARROW_H = 52.dp

/** A thick arrow bobbing toward what it points at. */
@Composable
private fun BouncingArrow(up: Boolean, color: Color, modifier: Modifier = Modifier, width: Dp = ARROW_W, height: Dp = ARROW_H) {
    val t by rememberInfiniteTransition(label = "arrow").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce",
    )
    Canvas(modifier.size(width, height).graphicsLayer { translationY = (if (up) -1f else 1f) * t * 8.dp.toPx() }) {
        val w = size.width
        val h = size.height
        val head = h * 0.48f
        val path = Path().apply {
            if (up) {
                moveTo(w / 2f, 0f); lineTo(w, head); lineTo(w * 0.69f, head); lineTo(w * 0.69f, h)
                lineTo(w * 0.31f, h); lineTo(w * 0.31f, head); lineTo(0f, head); close()
            } else {
                moveTo(w / 2f, h); lineTo(w, h - head); lineTo(w * 0.69f, h - head); lineTo(w * 0.69f, 0f)
                lineTo(w * 0.31f, 0f); lineTo(w * 0.31f, h - head); lineTo(0f, h - head); close()
            }
        }
        drawPath(path, color)
        drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
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
