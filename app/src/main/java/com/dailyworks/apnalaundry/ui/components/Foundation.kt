package com.dailyworks.apnalaundry.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dailyworks.apnalaundry.ui.theme.Bricolage
import com.dailyworks.apnalaundry.ui.theme.Figtree
import com.dailyworks.apnalaundry.ui.theme.Tokens

// ---------- text styles ----------
// The type scale — use only these sizes (sp; the app caps the phone's font
// scale at 115%, see MainActivity):
//   fig  (Figtree, text):     11 tag · 12 caption · 13 meta · 14 body S · 15 body · 16 title/button · 18 lead
//   bric (Bricolage, display): 18 · 20 · 22 sheet/screen title · 24 · 28 hero · 34 big number
// Element sizes: buttons 56dp (main action) / 48dp (others), chips 44dp,
// tap targets ≥ 48dp, icons 16 · 18 · 20 · 22 · 24dp.
fun bric(size: Int, weight: FontWeight = FontWeight.Bold, color: Color = Tokens.Ink): TextStyle =
    TextStyle(fontFamily = Bricolage, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = (-0.02).em, lineHeight = (size * 1.1f).sp)

fun fig(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Tokens.Ink): TextStyle =
    TextStyle(fontFamily = Figtree, fontSize = size.sp, fontWeight = weight, color = color, lineHeight = (size * 1.35f).sp)

/**
 * One line of text that shrinks (down to [minScale]) instead of wrapping or
 * being cut — for amounts: "₹1,23,456" must never break into "₹1,23,45 / 6".
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    minScale: Float = 0.6f,
) {
    var scale by remember(text, style.fontSize) { mutableFloatStateOf(1f) }
    Text(
        text, modifier,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        textAlign = textAlign,
        maxLines = 1, softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > minScale) scale = (scale * 0.9f).coerceAtLeast(minScale) },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Tokens.Muted) {
    Text(
        text.uppercase(),
        style = fig(12, FontWeight.Bold, color).copy(letterSpacing = 0.04.em),
        modifier = modifier,
    )
}

// ---------- ripple-free tap ----------
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

fun Modifier.rounded(r: Dp): Modifier = clip(RoundedCornerShape(r))

// ---------- buttons ----------
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    bg: Color = Tokens.Blue,
    onClick: () -> Unit,
) {
    // heightIn: a label that wraps (big font, narrow phone) grows the button
    // instead of being cut off.
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .rounded(14.dp)
            .background(if (enabled) bg else Tokens.BlueDisabled)
            .tap(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = fig(16, FontWeight.Bold, Tokens.OnDark), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    border: Color = Tokens.Blue,
    fg: Color = Tokens.Blue,
    bg: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = height)
            .rounded(14.dp)
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = fig(16, FontWeight.Bold, fg), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- pill chip ----------
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .rounded(999.dp)
            .background(if (selected) Tokens.Ink else Tokens.Card)
            .border(1.5.dp, if (selected) Tokens.Ink else Tokens.CardBorder, RoundedCornerShape(999.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = fig(14, FontWeight.Bold, if (selected) Tokens.OnDark else Tokens.Ink),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- segmented control ----------
@Composable
fun Segmented(
    options: List<Pair<String, Boolean>>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .rounded(12.dp)
            .background(Tokens.SegTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, (label, sel) ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .rounded(9.dp)
                    .background(if (sel) Tokens.Card else Color.Transparent)
                    .tap { onSelect(i) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = fig(15, FontWeight.Bold, if (sel) Tokens.Ink else Tokens.InkSecondary), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- bordered text field ----------
@Composable
fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    height: Dp = 52.dp,
    borderColor: Color = Tokens.FieldBorder,
    borderWidth: Dp = 1.5.dp,
    textStyle: TextStyle = fig(16, FontWeight.SemiBold),
    textAlign: TextAlign = TextAlign.Start,
    singleLine: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: Dp = 14.dp,
) {
    // The text field is the whole box, so a tap anywhere on it (the padding,
    // the ₹, the empty space) opens the keyboard — not only on the text line.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle.copy(textAlign = textAlign),
        cursorBrush = SolidColor(Tokens.Blue),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
        modifier = modifier
            .then(fieldModifier)
            .height(height)
            .rounded(12.dp)
            .background(Tokens.Card)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = contentPadding, vertical = if (singleLine) 0.dp else 12.dp),
                // A multi-line box (address) starts at the top, like paper.
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leading?.invoke()
                if (prefix != null) Text(prefix, style = fig(16, FontWeight.SemiBold, Tokens.Muted))
                // propagateMinConstraints: the text gets the full width, so a centred field stays centred.
                Box(Modifier.weight(1f), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart, propagateMinConstraints = true) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder, style = textStyle.copy(color = Tokens.Muted, textAlign = textAlign), modifier = Modifier.fillMaxWidth(),
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (suffix != null) Text(suffix, style = fig(15, FontWeight.SemiBold, Tokens.Muted))
                trailing?.invoke()
            }
        },
    )
}

/** Local override so stray Text() calls inherit Figtree/ink. */
val AppTextStyle: TextStyle
    @Composable get() = LocalTextStyle.current.copy(fontFamily = Figtree, color = Tokens.Ink)
