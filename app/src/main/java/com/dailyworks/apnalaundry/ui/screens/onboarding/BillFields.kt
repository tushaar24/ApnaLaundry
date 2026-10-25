package com.dailyworks.apnalaundry.ui.screens.onboarding

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlinx.coroutines.launch
import java.io.File

/**
 * "+ Add fields" (handoff §7.1/§7.2): a list of optional bill fields, each
 * opening its editor in the same sheet. Done / dismiss in an editor returns
 * to the list (several fields in one go); Done on the list closes the sheet.
 * Every change goes straight to [onChange], so the bill behind updates live.
 */
private enum class FieldKey { Upi, Logo, Address, Terms, Gstin, Phone, Email }

@Composable
fun FieldsSheet(shopVm: ShopViewModel, details: BillDetails.Fields, onChange: (BillDetails.Fields) -> Unit, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<FieldKey?>(null) }
    val back = { editing = null }
    when (editing) {
        FieldKey.Upi -> return UpiEditor(details.upiId, onSave = { onChange(details.copy(upiId = it)); back() }, onBack = back)
        FieldKey.Logo -> return LogoEditor(shopVm, details.logoId, onChange = { onChange(details.copy(logoId = it)) }, onBack = back)
        FieldKey.Address -> return AddressEditor(details.address, onSave = { onChange(details.copy(address = it)); back() }, onBack = back)
        FieldKey.Terms -> return TermsEditor(details.terms, details.termsCustom, onSave = { t, c -> onChange(details.copy(terms = t, termsCustom = c)); back() }, onBack = back)
        FieldKey.Gstin -> return GstinEditor(details.gstin, onSave = { onChange(details.copy(gstin = it)); back() }, onBack = back)
        FieldKey.Phone -> return PhoneEditor(details.billPhone, onSave = { onChange(details.copy(billPhone = it)); back() }, onBack = back)
        FieldKey.Email -> return EmailEditor(details.email, onSave = { onChange(details.copy(email = it)); back() }, onBack = back)
        null -> Unit
    }

    val terms = BillDetails.termLines(details.terms, details.termsCustom).size
    data class Row4(val key: FieldKey, val icon: ImageVector, val title: String, val empty: String, val set: String)
    val rows = listOf(
        Row4(FieldKey.Upi, Icons.Outlined.QrCode2, tr("UPI QR on bill"), tr("Customers scan and pay you. Money goes to your bank."),
            if (BillDetails.isUpiValid(details.upiId)) tr("UPI · {id}", "id" to details.upiId) else ""),
        Row4(FieldKey.Logo, Icons.Outlined.Image, tr("Shop logo"), tr("Your logo at the top of every bill"), if (details.logoId.isNotEmpty()) tr("Logo added") else ""),
        Row4(FieldKey.Address, Icons.Outlined.Place, tr("Shop address"), tr("Helps new customers find your shop"), details.address),
        Row4(FieldKey.Terms, Icons.AutoMirrored.Outlined.List, tr("Terms & conditions"), tr("e.g. not responsible for colour fading"),
            when {
                terms > 1 -> tr("{n} lines at the bottom", "n" to terms)
                terms > 0 -> tr("{n} line at the bottom", "n" to terms)
                else -> ""
            }),
        Row4(FieldKey.Gstin, Icons.Outlined.Badge, tr("GSTIN"), tr("Only for GST-registered shops"), if (BillDetails.isGstinValid(details.gstin)) details.gstin else ""),
        Row4(FieldKey.Phone, Icons.Outlined.Call, tr("Phone number"), tr("Customers call or WhatsApp you on this"),
            if (BillDetails.isPhoneValid(details.billPhone)) BillDetails.fmtBillPhone(details.billPhone) else ""),
        Row4(FieldKey.Email, Icons.Outlined.Email, tr("Email address"), tr("For customers who prefer to write to you"),
            if (BillDetails.isEmailValid(details.email)) details.email else ""),
    )

    AppBottomSheet(title = tr("Add fields to your bill"), subtitle = tr("All optional. Your bill already works without these."), onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp))) {
                rows.forEachIndexed { i, r ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).tap { editing = r.key }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).rounded(12.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
                            Icon(r.icon, null, tint = Tokens.Blue, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = fig(15, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                r.set.ifEmpty { r.empty },
                                style = if (r.set.isNotEmpty()) fig(13, FontWeight.SemiBold, Green) else fig(13, color = Tokens.Muted),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (r.set.isEmpty()) Text(tr("Add"), style = fig(14, FontWeight.Bold, Tokens.Blue))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
                    }
                }
            }
            PrimaryButton(tr("Done"), height = 56.dp) { onClose() }
        }
    }
}

/** Focuses the editor's input (keyboard up) when the sheet opens, like the web's autoFocus. */
@Composable
private fun autoFocus(): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return Modifier.focusRequester(focus)
}

// ───────────────────────── editors ─────────────────────────

@Composable
private fun EditorFooter(canRemove: Boolean, onRemove: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (canRemove) {
            OutlineButton(tr("Remove"), Modifier.weight(1f), border = Tokens.Orange, fg = Tokens.DeleteRed) { onRemove() }
        }
        Box(Modifier.weight(1f)) { PrimaryButton(tr("Done"), height = 56.dp) { onDone() } }
    }
}

@Composable
private fun CheckLine(check: BillDetails.Check?) {
    if (check == null) return
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (check.ok) Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(16.dp))
        Text(tr(check.message), style = fig(13, FontWeight.SemiBold, if (check.ok) Green else Tokens.OrangeText), modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun UpiEditor(value: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var v by remember { mutableStateOf(value) }
    val check = BillDetails.upiCheck(v)
    AppBottomSheet(title = tr("UPI for payments"), subtitle = tr("A pay QR prints on every bill, so customers pay you straight to your bank."), onDismiss = onBack) {
        Column {
            Text(tr("UPI ID"), style = fig(13, FontWeight.Bold, Tokens.InkSecondary))
            FieldBox(
                v, { v = BillDetails.cleanUpi(it) }, Modifier.padding(top = 6.dp),
                placeholder = tr("e.g. sharmalaundry@okaxis"), height = 56.dp, keyboardType = KeyboardType.Email,
                fieldModifier = autoFocus(),
                borderColor = if (check?.ok == false) Tokens.Orange else Tokens.FieldBorder,
            )
            CheckLine(check)
            Text(tr("Find it in GPay, PhonePe or Paytm — tap your photo at the top."), style = fig(13, color = Tokens.Muted), modifier = Modifier.padding(top = 8.dp))
            EditorFooter(value.isNotEmpty(), onRemove = { onSave("") }, onDone = { onSave(v) })
        }
    }
}

@Composable
private fun AddressEditor(value: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var v by remember { mutableStateOf(value) }
    AppBottomSheet(title = tr("Shop address"), subtitle = tr("Prints under your laundry name, so new customers can find you."), onDismiss = onBack) {
        Column {
            FieldBox(
                v, { v = it.take(120) }, placeholder = tr("Shop no., road, area, city"), height = 104.dp, fieldModifier = autoFocus(),
                singleLine = false, capitalization = KeyboardCapitalization.Words,
            )
            Text("${v.length}/120", style = fig(12, color = Tokens.Faint), modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
            EditorFooter(value.isNotEmpty(), onRemove = { onSave("") }, onDone = { onSave(BillDetails.cleanAddress(v)) })
        }
    }
}

@Composable
private fun GstinEditor(value: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var v by remember { mutableStateOf(value) }
    val check = BillDetails.gstinCheck(v)
    AppBottomSheet(title = tr("GSTIN"), subtitle = tr("15 letters and numbers. Skip this if your shop is not GST registered."), onDismiss = onBack) {
        Column {
            FieldBox(
                v, { v = BillDetails.cleanGstin(it) }, placeholder = tr("e.g. 27ABCDE1234F1Z5"), height = 56.dp, fieldModifier = autoFocus(),
                capitalization = KeyboardCapitalization.Characters, textStyle = fig(18, FontWeight.Bold),
                borderColor = if (check?.ok == false) Tokens.Orange else Tokens.FieldBorder,
            )
            CheckLine(check)
            EditorFooter(value.isNotEmpty(), onRemove = { onSave("") }, onDone = { onSave(v) })
        }
    }
}

@Composable
private fun PhoneEditor(value: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var v by remember { mutableStateOf(value) }
    AppBottomSheet(title = tr("Phone number on bill"), subtitle = tr("Customers call or WhatsApp you on this number."), onDismiss = onBack) {
        Column {
            FieldBox(
                v, { v = it.filter { c -> c.isDigit() }.take(10) }, prefix = "+91", placeholder = "98765 43210",
                height = 56.dp, keyboardType = KeyboardType.Number, fieldModifier = autoFocus(),
            )
            EditorFooter(value.isNotEmpty(), onRemove = { onSave("") }, onDone = { onSave(BillDetails.cleanPhone(v)) })
        }
    }
}

@Composable
private fun EmailEditor(value: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var v by remember { mutableStateOf(value) }
    val check = BillDetails.emailCheck(v)
    AppBottomSheet(title = tr("Email on bill"), subtitle = tr("Prints under your laundry name, so customers can write to you."), onDismiss = onBack) {
        Column {
            Text(tr("Email address"), style = fig(13, FontWeight.Bold, Tokens.InkSecondary))
            FieldBox(
                v, { v = BillDetails.cleanEmail(it) }, Modifier.padding(top = 6.dp),
                placeholder = tr("e.g. sharmalaundry@gmail.com"), height = 56.dp, keyboardType = KeyboardType.Email,
                fieldModifier = autoFocus(),
                borderColor = if (check?.ok == false) Tokens.Orange else Tokens.FieldBorder,
            )
            CheckLine(check)
            EditorFooter(value.isNotEmpty(), onRemove = { onSave("") }, onDone = { onSave(v) })
        }
    }
}

@Composable
private fun TermsEditor(terms: List<String>, custom: String, onSave: (List<String>, String) -> Unit, onBack: () -> Unit) {
    var picked by remember { mutableStateOf(terms.toSet()) }
    var own by remember { mutableStateOf(custom) }
    AppBottomSheet(title = tr("Terms & conditions"), subtitle = tr("Small lines at the bottom of every bill. Tick the ones you want."), onDismiss = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BillDetails.TERM_PRESETS.forEach { t ->
                val on = t.id in picked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).rounded(12.dp).background(Tokens.Card)
                        .border(1.5.dp, if (on) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(12.dp))
                        .tap { picked = if (on) picked - t.id else picked + t.id }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = on, onCheckedChange = { picked = if (on) picked - t.id else picked + t.id },
                        colors = CheckboxDefaults.colors(checkedColor = Tokens.Blue),
                    )
                    // Shown in the app's language; the bill prints the English line (saved by id).
                    Text(tr(t.text), style = fig(14, FontWeight.Medium), modifier = Modifier.weight(1f))
                }
            }
            Text(tr("Your own line"), style = fig(13, FontWeight.Bold, Tokens.InkSecondary), modifier = Modifier.padding(top = 8.dp))
            FieldBox(own, { own = it.take(80) }, placeholder = tr("e.g. Sunday closed"), height = 52.dp)
            EditorFooter(
                canRemove = terms.isNotEmpty() || custom.isNotEmpty(),
                onRemove = { onSave(emptyList(), "") },
                onDone = { onSave(BillDetails.TERM_PRESETS.map { it.id }.filter { it in picked }, BillDetails.cleanTermsCustom(own)) },
            )
        }
    }
}

@Composable
private fun LogoEditor(shopVm: ShopViewModel, logoId: String, onChange: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val tick by shopVm.logoTick.collectAsStateWithLifecycle()
    val logo = remember(logoId, tick) { shopVm.logos.cached(logoId) }

    fun upload(uri: Uri?) {
        if (uri == null) return
        busy = true
        scope.launch {
            shopVm.uploadLogo(uri)
                .onSuccess { onChange(it) }
                .onFailure { shopVm.showInfo(it.message?.takeIf { m -> m.isNotBlank() && m.length < 80 } ?: tr("Couldn't add the logo — check internet and try again")) }
            busy = false
        }
    }
    // Permissions are asked only by the system pickers, only when tapped.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { upload(it) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) upload(photoUri) }

    AppBottomSheet(title = tr("Shop logo"), subtitle = tr("Prints at the top of every bill, next to your laundry name."), onDismiss = onBack) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(72.dp).rounded(16.dp).background(Tokens.Card)
                        .border(if (logo != null) 1.dp else 2.dp, if (logo != null) Tokens.CardBorder else Tokens.DashBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        busy -> Text(tr("Adding…"), style = fig(12, FontWeight.SemiBold, Tokens.Muted))
                        logo != null -> Image(logo.asImageBitmap(), tr("Your shop logo"), contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp))
                        else -> Icon(Icons.Outlined.Image, null, tint = Tokens.Faint, modifier = Modifier.size(28.dp))
                    }
                }
                Text(
                    if (logoId.isNotEmpty()) tr("Looks good. It shows on all three designs. Tap below to change it.")
                    else tr("A square logo works best. A clear photo of your shop board also works."),
                    style = fig(14, color = Tokens.Muted), modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerButton(Icons.Outlined.Image, tr("From gallery"), Modifier.weight(1f).fillMaxHeight()) {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                PickerButton(Icons.Outlined.PhotoCamera, tr("Take photo"), Modifier.weight(1f).fillMaxHeight()) {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "logo-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    photoUri = uri
                    camera.launch(uri)
                }
            }
            Text(
                tr("No logo? Skip this — your laundry name already looks good on the bill."),
                style = fig(13, color = Tokens.Muted), modifier = Modifier.padding(top = 12.dp),
            )
            EditorFooter(logoId.isNotEmpty(), onRemove = { onChange(""); onBack() }, onDone = onBack)
        }
    }
}

@Composable
private fun PickerButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 48.dp).rounded(14.dp).border(1.5.dp, Tokens.Blue, RoundedCornerShape(14.dp)).tap(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
        Text(
            label, style = fig(15, FontWeight.Bold, Tokens.Blue), textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
    }
}
