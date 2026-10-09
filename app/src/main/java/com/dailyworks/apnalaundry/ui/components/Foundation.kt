package com.dailyworks.apnalaundry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
fun bric(size: Int, weight: FontWeight = FontWeight.Bold, color: Color = Tokens.Ink): TextStyle =
    TextStyle(fontFamily = Bricolage, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = (-0.02).em, lineHeight = (size * 1.1f).sp)

fun fig(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Tokens.Ink): TextStyle =
    TextStyle(fontFamily = Figtree, fontSize = size.sp, fontWeight = weight, color = color, lineHeight = (size * 1.35f).sp)

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
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .rounded(14.dp)
            .background(if (enabled) bg else Tokens.BlueDisabled)
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = fig(17, FontWeight.Bold, Tokens.OnDark))
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
            .height(height)
            .rounded(14.dp)
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = fig(16, FontWeight.Bold, fg))
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
            .height(44.dp)
            .rounded(999.dp)
            .background(if (selected) Tokens.Ink else Tokens.Card)
            .border(1.5.dp, if (selected) Tokens.Ink else Tokens.CardBorder, RoundedCornerShape(999.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = fig(14, FontWeight.Bold, if (selected) Tokens.OnDark else Tokens.Ink), maxLines = 1)
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
                    .height(48.dp)
                    .rounded(9.dp)
                    .background(if (sel) Tokens.Card else Color.Transparent)
                    .tap { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = fig(15, FontWeight.Bold, if (sel) Tokens.Ink else Tokens.InkSecondary))
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
    textStyle: TextStyle = fig(17, FontWeight.SemiBold),
    textAlign: TextAlign = TextAlign.Start,
    singleLine: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .height(height)
            .rounded(12.dp)
            .background(Tokens.Card)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        if (prefix != null) Text(prefix, style = fig(17, FontWeight.SemiBold, Tokens.Muted))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = textStyle.copy(color = Tokens.Muted), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = textStyle.copy(textAlign = textAlign),
                cursorBrush = SolidColor(Tokens.Blue),
                keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
        if (suffix != null) Text(suffix, style = fig(15, FontWeight.SemiBold, Tokens.Muted))
    }
}

/** Local override so stray Text() calls inherit Figtree/ink. */
val AppTextStyle: TextStyle
    @Composable get() = LocalTextStyle.current.copy(fontFamily = Figtree, color = Tokens.Ink)
