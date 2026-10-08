package com.dailyworks.mylaundry.support.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.mylaundry.support.SupportApp
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.CallDto
import com.dailyworks.mylaundry.support.data.Outcome
import com.dailyworks.mylaundry.support.data.RecordingUploads
import com.dailyworks.mylaundry.support.data.UploadState
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One call in a history list. Tap to open notes / tags. */
@Composable
fun CallCard(call: CallDto, showShop: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Tokens.Card, RoundedCornerShape(14.dp))
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showShop) {
            Text(call.shopName ?: call.ownerName ?: "+91 ${call.phone}", style = fig(15, FontWeight.Bold))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${dayTime(call.startedAt)} · ${call.agentName}",
                style = fig(13, FontWeight.SemiBold, Tokens.InkSecondary),
                modifier = Modifier.weight(1f),
            )
            Text(duration(call.durationSec), style = fig(13, color = Tokens.Muted))
        }
        val outcome = Outcome.of(call.outcome)
        if (outcome != null) {
            Text(
                outcome.label,
                style = fig(13, FontWeight.Bold, if (outcome == Outcome.CONNECTED) Tokens.GreenText else Tokens.OrangeText),
            )
        }
        if (call.note.isNotBlank()) Text(call.note, style = fig(14, color = Tokens.Ink), maxLines = 6)
        if (call.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                call.tags.forEach { TagChip(it.name, it.color) }
            }
        }
        RecordingLine(call)
    }
}

/** Player when the recording is on the server; otherwise this phone's upload status. */
@Composable
fun RecordingLine(call: CallDto) {
    if (call.recording.uploaded) {
        RecordingPlayer(call.id)
        return
    }
    val context = LocalContext.current
    val state by remember(call.id) { RecordingUploads.state(context, call.id) }
        .collectAsStateWithLifecycle(UploadState.IDLE)
    val (icon, text, color) = when (state) {
        UploadState.WORKING -> Triple(Icons.Filled.CloudUpload, "Finding & uploading the recording…", Tokens.BlueText)
        UploadState.DONE -> Triple(Icons.Filled.CloudUpload, "Recording uploaded — pull to refresh", Tokens.GreenText)
        UploadState.NOT_FOUND -> Triple(Icons.Filled.ErrorOutline, "Recording not found on this phone — attach it from the call notes", Tokens.OrangeText)
        UploadState.FAILED -> Triple(Icons.Filled.ErrorOutline, "Recording upload failed — attach it from the call notes", Tokens.Red)
        UploadState.IDLE -> if (call.endedAt != null && (call.durationSec ?: 0) > 0) {
            Triple(Icons.Filled.ErrorOutline, "No recording", Tokens.Faint)
        } else {
            return
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = fig(12, FontWeight.SemiBold, color))
    }
}

/** Streams the recording from a short-lived signed URL. */
@Composable
fun RecordingPlayer(callId: String) {
    val context = LocalContext.current
    val api = (context.applicationContext as SupportApp).graph.api
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(callId) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p -> if (p.duration > 0) progress = p.currentPosition / p.duration.toFloat() }
            delay(300)
        }
    }

    fun toggle() {
        val p = player
        if (p != null) {
            if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
            return
        }
        preparing = true
        error = null
        scope.launch {
            try {
                val url = api.recordingUrl(callId)
                val mp = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(url)
                    setOnPreparedListener { it.start(); preparing = false; playing = true }
                    setOnCompletionListener { playing = false; progress = 1f }
                    setOnErrorListener { _, _, _ -> preparing = false; playing = false; error = "Can't play this recording"; true }
                    prepareAsync()
                }
                player = mp
            } catch (e: ApiException) {
                preparing = false
                error = e.message
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().background(Tokens.BlueLight, RoundedCornerShape(10.dp)).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = ::toggle) {
            if (preparing) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Tokens.Blue, strokeWidth = 2.dp)
            } else {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Tokens.Blue)
            }
        }
        if (error != null) {
            Text(error!!, style = fig(12, FontWeight.SemiBold, Tokens.Red))
        } else {
            Text("Recording", style = fig(13, FontWeight.SemiBold, Tokens.BlueText))
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f),
                color = Tokens.Blue,
                trackColor = Tokens.Card,
            )
        }
    }
}
