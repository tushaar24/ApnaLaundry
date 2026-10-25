package com.dailyworks.apnalaundry.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppLanguage
import com.dailyworks.apnalaundry.core.I18n
import com.dailyworks.apnalaundry.ui.theme.Tokens

/**
 * Pick the app's language — each in its own script, so it reads at a glance.
 * [onDismiss] null = a language must be picked (first open, before login).
 */
@Composable
fun LanguagePickerDialog(onPick: (AppLanguage) -> Unit, onDismiss: (() -> Unit)?) {
    val current = I18n.language
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(dismissOnBackPress = onDismiss != null, dismissOnClickOutside = onDismiss != null),
    ) {
        Column(
            Modifier.fillMaxWidth().rounded(20.dp).background(Tokens.Card).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(com.dailyworks.apnalaundry.core.tr("Choose your language"), style = bric(22, FontWeight.Bold))
            Text("भाषा चुनें · மொழி · భాష · ಭಾಷೆ · ভাষা", style = fig(14, color = Tokens.Muted))
            AppLanguage.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { lang ->
                        val on = lang == current && onDismiss != null
                        Column(
                            Modifier.weight(1f).heightIn(min = 64.dp).rounded(14.dp)
                                .background(if (on) Tokens.BlueLight else Tokens.Bg)
                                .border(1.5.dp, if (on) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
                                .tap { onPick(lang) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(lang.native, style = bric(19, FontWeight.Bold, if (on) Tokens.BlueText else Tokens.Ink))
                            if (lang != AppLanguage.EN) Text(lang.english, style = fig(12, FontWeight.SemiBold, Tokens.Muted))
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** The current language as a small chip (login, top right): globe + its name + ▾. */
@Composable
fun LanguageChip(onClick: () -> Unit, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val fg = if (onDark) Tokens.OnDark else Tokens.Ink
    Row(
        modifier.heightIn(min = 40.dp).rounded(999.dp)
            .background(if (onDark) Color.White.copy(alpha = 0.16f) else Tokens.Card)
            .border(1.dp, if (onDark) Color.White.copy(alpha = 0.35f) else Tokens.CardBorder, RoundedCornerShape(999.dp))
            .tap(onClick = onClick).padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Language, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(I18n.language.native, style = fig(14, FontWeight.Bold, fg))
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = fg, modifier = Modifier.size(18.dp))
    }
}

/** Switches the app to [lang] (saved on this phone) and redraws the screen in it. [where]: login | settings. */
fun applyLanguage(context: Context, lang: AppLanguage, where: String) {
    val from = I18n.language
    I18n.set(context, lang)
    Analytics.languageChanged(from.code, lang.code, where)
    if (from != lang) context.findActivity()?.recreate()
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
