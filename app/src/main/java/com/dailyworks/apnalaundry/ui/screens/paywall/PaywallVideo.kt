package com.dailyworks.apnalaundry.ui.screens.paywall

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.tap

// Paywall intro video — the website's 720p faststart MP4 (~3.7 MB), streamed.
private const val PAYWALL_VIDEO_URL = "https://mylaundry.work/paywall-intro.mp4"

/**
 * Paywall intro video: autoplays WITH sound as soon as the paywall opens (no
 * browser autoplay rules in a native app). ExoPlayer takes audio focus, so it
 * ducks/pauses for calls and other media. Paused while [paused] (Checkout
 * starting) or the app is backgrounded; hidden entirely if it fails to load.
 * Tap = pause / resume / replay; the corner button mutes.
 */
@OptIn(UnstableApi::class)
@Composable
fun PaywallVideo(paused: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            setMediaItem(MediaItem.fromUri(PAYWALL_VIDEO_URL))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        var started = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying && !started) {
                    started = true
                    Analytics.paywallVideoStarted(muted = player.volume == 0f)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    ended = true
                    Analytics.paywallVideoCompleted()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Backgrounded (incl. Razorpay Checkout's activity on top) → pause; no auto-resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(paused) { if (paused) player.pause() }

    if (failed) return

    fun replay() {
        ended = false
        player.seekTo(0)
        player.play()
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        // TextureView so the rounded clip applies (a SurfaceView ignores it).
        PlayerSurface(player = player, surfaceType = SURFACE_TYPE_TEXTURE_VIEW, modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .tap {
                    when {
                        ended -> replay()
                        playing -> player.pause()
                        else -> player.play()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (ended) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Replay, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Text("Watch again", style = fig(14, FontWeight.Bold, Color.White))
                }
            } else if (!playing && player.playbackState == Player.STATE_READY) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }

        if (!ended) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .tap {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                        if (!muted) Analytics.paywallVideoUnmuted()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                    if (muted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
