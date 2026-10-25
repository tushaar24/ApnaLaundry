package com.dailyworks.apnalaundry.ui.sheets

import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewModelScope
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.LaundryTags
import com.dailyworks.apnalaundry.print.TagPrefs
import com.dailyworks.apnalaundry.print.TagPrinter
import com.dailyworks.apnalaundry.print.TagRender
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * Print tags: a tag for each cloth (or one for the whole order) on the shop's
 * Bluetooth thermal printer. First sorts out whatever is missing — Bluetooth
 * allowed, Bluetooth on, a paired printer — then shows the tag and prints the
 * lot with a running count. [from] = "bill" | "order_detail" | "menu".
 */
@Composable
fun PrintTagsSheet(state: LaundryState, orderId: Int, from: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val context = LocalContext.current
    val saved = remember { TagPrefs(context) }

    // Bluetooth is checked again on coming back from the phone's settings or the "turn on" prompt.
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { check++; onPauseOrDispose { } }
    var asked by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true; check++ }
    val turnOn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { check++ }

    val hasBluetooth = remember { TagPrinter.adapter(context) != null }
    val permitted = remember(check) { TagPrinter.hasPermission(context) }
    val on = remember(check) { TagPrinter.isOn(context) }
    val devices = remember(check, permitted, on) { TagPrinter.paired(context) }

    var address by remember { mutableStateOf(saved.address) }
    var wide by remember { mutableStateOf(saved.wide) }
    var perPiece by remember { mutableStateOf(saved.perPiece) }
    var choosing by remember { mutableStateOf(false) }
    val printer = devices.firstOrNull { it.address == address }

    val pieces = remember(o) { LaundryTags.pieceCount(o) }
    val tags = remember(state.shop, o, c, perPiece) {
        if (perPiece) LaundryTags.perPiece(state.shop, o, c) else listOf(LaundryTags.forOrder(state.shop, o, c))
    }
    val dots = if (wide) TagRender.WIDE else TagRender.NARROW
    val preview = remember(tags, dots) { tags.firstOrNull()?.let { TagRender.render(it, dots).asImageBitmap() } }

    var printing by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf<Int?>(null) } // null = still connecting
    var error by remember { mutableStateOf<String?>(null) }
    val open = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { open.set(false) } }

    fun print() {
        val p = printer ?: return
        error = null; printing = true; sent = null
        // The app's scope, not the sheet's: closing the sheet (swipe, Back) mid-print
        // used to cancel the job and leave half the tags unprinted, silently.
        vm.viewModelScope.launch {
            try {
                TagPrinter.print(context, p.address, p.name, tags, dots) { sent = it }
                Analytics.tagsPrinted(o.id, tags.size, if (perPiece) "piece" else "order", if (wide) "80mm" else "58mm", from)
                vm.showInfo(if (tags.size == 1) tr("1 tag printed") else tr("{n} tags printed", "n" to tags.size))
                if (open.get()) onDismiss()
            } catch (e: TagPrinter.PrintError) {
                error = e.message
                Analytics.tagsPrintFailed(o.id, e.reason, from)
                // The sheet is gone: say it here, or the owner never learns the tags didn't print.
                if (!open.get()) vm.showInfo(tr("Tags not printed · {reason}", "reason" to e.message))
            } finally {
                printing = false
            }
        }
    }

    AppBottomSheet(title = tr("Print tags"), subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                !hasBluetooth -> Notice(tr("This phone has no Bluetooth, so it can't reach a printer."))

                !permitted -> {
                    Notice(
                        if (!asked) tr("To find your printer, tap ‘Allow Bluetooth’. Then tap ‘Allow’ when the phone asks.")
                        else tr("Bluetooth is not allowed yet. Tap ‘Allow Bluetooth’ again, or open phone settings → Permissions → Nearby devices → Allow."),
                    )
                    PrimaryButton(tr("Allow Bluetooth"), height = 56.dp) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) askPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (asked) OutlineButton(tr("Open phone settings"), Modifier.fillMaxWidth(), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) {
                        openAppSettings(context)
                    }
                }

                !on -> {
                    Notice(tr("Bluetooth is off. Turn it on to print."))
                    PrimaryButton(tr("Turn on Bluetooth"), height = 56.dp) {
                        runCatching { turnOn.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                            .onFailure { openBluetoothSettings(context) }
                    }
                }

                choosing || printer == null -> PrinterPicker(
                    devices = devices, selected = address, wide = wide,
                    onPick = { d ->
                        address = d.address; saved.address = d.address; saved.name = d.name
                        choosing = false
                    },
                    onWide = { wide = it; saved.wide = it },
                    onDone = if (printer != null) ({ choosing = false }) else null,
                )

                else -> {
                    // The printer, and its paper
                    Row(
                        Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card)
                            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Print, null, tint = Tokens.Blue, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(printer.name, style = fig(15, FontWeight.Bold))
                            Text(if (wide) tr("3 inch paper") else tr("2 inch paper"), style = fig(13, color = Tokens.Muted))
                        }
                        Text(
                            tr("Change"), style = fig(14, FontWeight.Bold, Tokens.Blue),
                            modifier = Modifier.tap(enabled = !printing) { choosing = true }.padding(4.dp),
                        )
                    }

                    // How many
                    Text(tr("How many tags?"), style = fig(15, FontWeight.Bold))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillChip(tr("One for each cloth · {n}", "n" to pieces), perPiece) { if (!printing) { perPiece = true; saved.perPiece = true } }
                        PillChip(tr("One for the order"), !perPiece) { if (!printing) { perPiece = false; saved.perPiece = false } }
                    }

                    // What it looks like
                    preview?.let { img ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (tags.size > 1) tr("Tag 1 of {n} looks like this", "n" to tags.size) else tr("Your tag looks like this"),
                                style = fig(13, FontWeight.SemiBold, Tokens.Muted),
                            )
                            Box(
                                Modifier.fillMaxWidth().rounded(12.dp).background(Color.White)
                                    .border(1.dp, Tokens.CardBorder, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(img, contentDescription = tr("Tag for order #{no}", "no" to o.no()), modifier = Modifier.size(width = 220.dp, height = (220f * img.height / img.width).dp), contentScale = ContentScale.Fit)
                            }
                        }
                    }

                    error?.let {
                        Text(
                            it, style = fig(14, FontWeight.SemiBold, Tokens.OrangeDeep),
                            modifier = Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.OrangeLight).padding(14.dp),
                        )
                    }

                    val n = tags.size
                    PrimaryButton(
                        when {
                            printing && sent == null -> tr("Connecting to printer…")
                            printing -> tr("Printing {i} of {n}…", "i" to minOf((sent ?: 0) + 1, n), "n" to n)
                            error != null -> tr("Try again")
                            n == 1 -> tr("Print 1 tag")
                            else -> tr("Print {n} tags", "n" to n)
                        },
                        enabled = !printing && n > 0, height = 56.dp,
                    ) { print() }
                    Text(
                        tr("Tear or cut along the dashed line between tags."),
                        style = fig(13, color = Tokens.Muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Paired devices to choose from (printers first), the paper size, and a way to pair a new one. */
@Composable
private fun PrinterPicker(
    devices: List<TagPrinter.Device>, selected: String, wide: Boolean,
    onPick: (TagPrinter.Device) -> Unit, onWide: (Boolean) -> Unit, onDone: (() -> Unit)?,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Choose your printer"), style = fig(15, FontWeight.Bold))
        if (devices.isEmpty()) {
            Notice(tr("No printer found. Switch on the printer, tap ‘Pair a printer’, and tap the printer’s name. If it asks for a PIN, type 0000 or 1234. Then come back here."))
        } else {
            devices.forEach { d ->
                val on = d.address == selected
                Row(
                    Modifier.fillMaxWidth().rounded(14.dp)
                        .background(if (on) Tokens.BlueLight else Tokens.Card)
                        .border(1.5.dp, if (on) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
                        .tap { onPick(d) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        if (d.isPrinter) Icons.Outlined.Print else Icons.Outlined.Bluetooth, null,
                        tint = if (d.isPrinter) Tokens.Blue else Tokens.Muted, modifier = Modifier.size(22.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = fig(15, FontWeight.Bold))
                        Text(if (d.isPrinter) tr("Printer") else tr("Other device"), style = fig(12, color = Tokens.Muted))
                    }
                    if (on) Text(tr("Chosen"), style = fig(12, FontWeight.Bold, Tokens.BlueText))
                }
            }
        }
        OutlineButton(tr("Pair a printer"), Modifier.fillMaxWidth(), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) {
            openBluetoothSettings(context)
        }
        Text(tr("Paper size"), style = fig(15, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip(tr("2 inch · 58 mm"), !wide) { onWide(false) }
            PillChip(tr("3 inch · 80 mm"), wide) { onWide(true) }
        }
        if (onDone != null) PrimaryButton(tr("Done"), height = 56.dp) { onDone() }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text, style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary),
        modifier = Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.Card)
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(12.dp)).padding(14.dp),
    )
}

private fun openBluetoothSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}
