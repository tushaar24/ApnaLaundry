package com.dailyworks.apnalaundry.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.youTubeId
import com.dailyworks.apnalaundry.ui.theme.Tokens

/** YouTube's red play mark (for "Demo"). */
@Composable
fun YouTubeMark(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 24.dp, height = 17.dp).rounded(5.dp).background(Color(0xFFFF0000)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/**
 * A YouTube video playing inside the app: YouTube's own embedded player in a
 * WebView, served from mylaundry.work (YouTube refuses embeds with no page
 * behind them). Its full-screen button opens the video over the whole screen.
 * A link that isn't a YouTube video opens as a page, still in the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(url: String, modifier: Modifier = Modifier, autoplay: Boolean = true) {
    val id = remember(url) { youTubeId(url) }
    var full by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    key(url) {
        AndroidView(
            modifier = modifier.background(Color.Black),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        // Stay on YouTube (its logo / "watch on" links); nothing else opens in here.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host.orEmpty()
                            return !(host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("google.com") || host.endsWith("mylaundry.work"))
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) { full = view to callback }
                        override fun onHideCustomView() { full = null }
                    }
                    if (id != null) loadDataWithBaseURL("https://mylaundry.work/", embedHtml(id, autoplay), "text/html", "utf-8", null)
                    else loadUrl(url)
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
        )
    }
    // Full screen: YouTube's own video view over everything; Back or its button leaves it.
    full?.let { (view, callback) ->
        Dialog(
            onDismissRequest = { callback.onCustomViewHidden(); full = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            key(view) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    factory = { ctx ->
                        (view.parent as? ViewGroup)?.removeView(view)
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    },
                )
            }
        }
    }
}

private fun embedHtml(id: String, autoplay: Boolean) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:fixed;top:0;left:0;width:100%;height:100%;border:0}</style>
</head><body>
<iframe src="https://www.youtube.com/embed/$id?autoplay=${if (autoplay) 1 else 0}&playsinline=1&rel=0&modestbranding=1&fs=1"
 allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen
 referrerpolicy="strict-origin-when-cross-origin"></iframe>
</body></html>
""".trimIndent()

/** Home's "Demo": the video over a dark screen, ✕ to close. */
@Composable
fun DemoVideoDialog(url: String, from: String = "home", onClose: () -> Unit) {
    // How long it stayed open (≈ watched) — "Demo Video Closed".
    val openedAt = remember { System.currentTimeMillis() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { com.dailyworks.apnalaundry.analytics.Analytics.demoVideoClosed(from, (System.currentTimeMillis() - openedAt) / 1000) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0D10)).windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                YouTubeMark()
                Text(tr("How to use MyLaundry"), style = fig(17, FontWeight.Bold, Color.White), modifier = Modifier.weight(1f).padding(start = 10.dp))
                Box(Modifier.size(48.dp).tap(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, tr("Close"), tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                YouTubePlayer(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            Text(
                tr("Tap ⛶ on the video for full screen."),
                style = fig(13, color = Color(0xFFB8BCC2)),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
            )
        }
    }
}

/**
 * Back from New order before the first order: "Confused how to create an
 * order?" with the video playing right here (full screen allowed). "Make my
 * order" stays; "Leave" goes back.
 */
@Composable
fun FirstOrderHelpDialog(url: String, onStay: () -> Unit, onLeave: () -> Unit) {
    Dialog(onDismissRequest = onStay, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth().rounded(20.dp).background(Tokens.Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("Confused how to create an order?"), style = bric(22, FontWeight.Bold))
            Text(tr("Watch this short video — it shows every step."), style = fig(15, color = Tokens.InkSecondary))
            YouTubePlayer(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f).rounded(12.dp))
            Text(tr("Tap ⛶ on the video for full screen."), style = fig(12, color = Tokens.Muted))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(tr("Leave"), Modifier.weight(1f), height = 52.dp, border = Tokens.CardBorder, fg = Tokens.InkSecondary, onClick = onLeave)
                PrimaryButton(tr("Make my order"), Modifier.weight(1.4f), height = 52.dp, onClick = onStay)
            }
        }
    }
}
