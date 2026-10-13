package com.dailyworks.apnalaundry.ui.components

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import android.content.res.Configuration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.ui.theme.Tokens

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    // Optional badge left of the title (e.g. a warning icon).
    leading: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Taller lines than bric's default: a Hindi name's matras need the room when it wraps.
    val titleStyle = bric(24, FontWeight.Bold).copy(lineHeight = 30.sp)
    // The app is light only. The sheet picks its bar icons from the phone's dark
    // mode (isSystemInDarkTheme), which turned them white on our light sheet:
    // tell it the phone is in light mode.
    val cfg = LocalConfiguration.current
    val lightCfg = remember(cfg) {
        Configuration(cfg).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO }
    }
    CompositionLocalProvider(LocalConfiguration provides lightCfg) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        containerColor = Tokens.Bg,
        dragHandle = null,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
    ) {
        // Stay clear of the navigation bar and the keyboard (whichever is taller),
        // and scroll when the content doesn't fit — so a form's Save button is
        // never cut off or hidden under the keyboard.
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        ) {
            Box(
                Modifier
                    .padding(bottom = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .rounded(999.dp)
                    .background(Tokens.CardBorder)
                    .align(Alignment.CenterHorizontally),
            ) {}
            if (leading != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    leading()
                    Text(title, style = titleStyle, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(title, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = fig(14, FontWeight.Normal, Tokens.Muted), modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.padding(top = 16.dp)) { content() }
        }
    }
    }
}

@Composable
fun Stepper(
    qty: Int,
    onDec: () -> Unit,
    onInc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton(Icons.Filled.Remove, "Decrease", bg = Tokens.NeutralFill, fg = Tokens.InkSecondary, enabled = qty > 0, onClick = onDec)
        Text(qty.toString(), style = fig(16, FontWeight.Bold), textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 20.dp))
        StepButton(Icons.Filled.Add, "Increase", bg = if (qty > 0) Tokens.Blue else Tokens.BlueLight, fg = if (qty > 0) Tokens.OnDark else Tokens.Blue, enabled = true, onClick = onInc)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, bg: Color, fg: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .rounded(12.dp)
            .background(bg)
            .then(if (enabled) Modifier.tap(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, cd, tint = fg, modifier = Modifier.size(20.dp))
    }
}

/** Unused padding helper kept for call-site clarity. */
val ZeroPadding = PaddingValues(0.dp)
