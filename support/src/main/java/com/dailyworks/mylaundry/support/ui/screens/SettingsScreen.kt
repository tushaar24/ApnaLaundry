package com.dailyworks.mylaundry.support.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.Settings
import com.dailyworks.mylaundry.support.ui.components.SectionCard
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.launch

/** Runtime permissions the call flow uses (all optional except placing calls). */
fun supportPermissions(): Array<String> = buildList {
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CALL_LOG)
    add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
}.toTypedArray()

private val PERMISSION_LABELS = mapOf(
    Manifest.permission.CALL_PHONE to "Place calls",
    Manifest.permission.READ_PHONE_STATE to "Know when a call ends",
    Manifest.permission.READ_CALL_LOG to "Read talk time",
    Manifest.permission.READ_MEDIA_AUDIO to "Find recordings",
    Manifest.permission.READ_EXTERNAL_STORAGE to "Find recordings",
)

/** Readable folder name from a SAF tree URI ("primary:Recordings/Call" -> "Recordings/Call"). */
private fun folderLabel(uri: String?): String? = uri?.let {
    runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)).substringAfter(':') }.getOrNull()
        ?.ifBlank { "Internal storage" }
}

/**
 * First-run setup and the Settings tab: who the agent is, permissions, and the
 * folder the dialer saves call recordings in.
 */
@Composable
fun SettingsScreen(graph: Graph, isSetup: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(Settings())
    var name by remember { mutableStateOf("") }
    LaunchedEffect(settings.agentName) { if (name.isBlank()) name = settings.agentName }

    // Re-check permissions whenever we come back from the system dialog / settings.
    var permTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val missing = remember(permTick) {
        supportPermissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch { graph.prefs.setRecordingsTree(uri.toString()) }
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (isSetup) "Set up MyLaundry Support" else "Settings", style = bric(26))
        if (isSetup) {
            Text("Three quick steps so your calls are logged and recorded.", style = fig(15, color = Tokens.Muted))
        }

        if (!graph.api.isConfigured) {
            SectionCard {
                Text(
                    "This build has no support API key. Add support.apiKey to local.properties and rebuild.",
                    style = fig(14, FontWeight.SemiBold, Tokens.Red),
                )
            }
        }

        SectionCard("1 · Your name") {
            Text("Shown on every call you make.", style = fig(14, color = Tokens.Muted))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = { Text("e.g. Asha", style = fig(16, color = Tokens.Faint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = fig(16),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Tokens.CardBorder, focusedBorderColor = Tokens.Blue),
            )
            if (!isSetup && name.trim() != settings.agentName && name.isNotBlank()) {
                OutlinedButton(onClick = { scope.launch { graph.prefs.setAgentName(name) } }, shape = RoundedCornerShape(12.dp)) {
                    Text("Save name", style = fig(14, FontWeight.Bold, Tokens.Blue))
                }
            }
        }

        SectionCard("2 · Permissions") {
            supportPermissions().forEach { p ->
                val ok = p !in missing
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null,
                        tint = if (ok) Tokens.Green else Tokens.Orange, modifier = Modifier.size(18.dp),
                    )
                    Text(PERMISSION_LABELS[p] ?: p, style = fig(14))
                }
            }
            if (missing.isNotEmpty()) {
                OutlinedButton(onClick = { askPerms.launch(missing.toTypedArray()) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Allow", style = fig(14, FontWeight.Bold, Tokens.Blue))
                }
            }
        }

        SectionCard("3 · Call recordings") {
            Text(
                "Turn on automatic call recording in your phone's dialer (Phone app › Settings › Call recording › " +
                    "Record all calls). Then pick the folder it saves recordings in, so each call's recording is " +
                    "uploaded automatically.",
                style = fig(14, color = Tokens.InkSecondary),
            )
            Text(
                "Usually: Samsung › Recordings/Call · Xiaomi/Redmi › MIUI/sound_recorder/call_rec · " +
                    "Realme/Oppo/OnePlus › Music/Recordings/Call Recordings or Recordings/Call · Vivo › Record/Call",
                style = fig(13, color = Tokens.Muted),
            )
            Text(
                "Google's Phone app keeps recordings private, so they can't be uploaded from those phones.",
                style = fig(13, FontWeight.SemiBold, Tokens.OrangeText),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = folderLabel(settings.recordingsTree)
                Icon(
                    if (label != null) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null,
                    tint = if (label != null) Tokens.Green else Tokens.Orange, modifier = Modifier.size(18.dp),
                )
                Text(label ?: "No folder picked (will search the audio library instead)", style = fig(14, FontWeight.SemiBold))
            }
            OutlinedButton(onClick = { pickFolder.launch(null) }, shape = RoundedCornerShape(12.dp)) {
                Text(if (settings.recordingsTree == null) "Pick recordings folder" else "Change folder", style = fig(14, FontWeight.Bold, Tokens.Blue))
            }
        }

        if (isSetup) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { scope.launch { graph.prefs.setAgentName(name); onDone() } },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Blue),
            ) { Text("Start", style = fig(17, FontWeight.Bold, Tokens.OnDark)) }
        }
    }
}
