package com.dailyworks.apnalaundry.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dailyworks.apnalaundry.R
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlinx.coroutines.delay

/**
 * The "order created" sound — res/raw/order_created.mp3, the owner's pick ("Koiroylers
 * Get Coin", 3 s; 2026-10-25). Played at media volume, like the guide's voice. Loaded
 * once at app start so the first order has it.
 */
object OrderChime {
    private var pool: SoundPool? = null
    private var sound = 0
    @Volatile private var ready = false

    fun preload(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        p.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        sound = runCatching { p.load(context.applicationContext, R.raw.order_created, 1) }.getOrDefault(0)
        pool = p
    }

    fun play(context: Context) {
        preload(context)
        if (ready) pool?.play(sound, 1f, 1f, 1, 0, 1f)
    }
}

/**
 * Every new order (New order, Ask): a big green tick, "Order created!", the order's
 * number, customer and amount, and the sound (owner, 2026-10-25). Closes by itself
 * after 2 seconds (owner) — or sooner on OK / a tap outside.
 */
@Composable
fun OrderCreatedDialog(state: LaundryState, orderId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val order = state.orders.firstOrNull { it.id == orderId }
    val name = order?.let { Selectors.customer(state, it.custId).name }.orEmpty()
    val amount = order?.let { LaundryMath.amtOf(it) } ?: 0
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(orderId) {
        OrderChime.play(context)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(orderId) {
        delay(AUTO_CLOSE_MS)
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().rounded(24.dp).background(Tokens.Card).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(84.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .rounded(999.dp).background(Tokens.GreenLight),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(60.dp).rounded(999.dp).background(Tokens.Green), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Tokens.OnDark, modifier = Modifier.size(38.dp))
                }
            }
            Text(tr("Order created!"), style = bric(24, FontWeight.Bold), textAlign = TextAlign.Center)
            if (order != null) {
                val line = if (name.isNotBlank()) tr("Order #{no} · {name}", "no" to order.no(), "name" to name) else tr("Order #{no}", "no" to order.no())
                Text(line, style = fig(16, FontWeight.SemiBold, Tokens.InkSecondary), textAlign = TextAlign.Center)
                if (amount > 0) Text(Money.rupees(amount), style = bric(22, FontWeight.Bold, Tokens.GreenText))
            }
            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 52.dp).rounded(14.dp).background(Tokens.Blue).tap(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text(tr("OK"), style = fig(16, FontWeight.Bold, Tokens.OnDark)) }
        }
    }
}

private const val AUTO_CLOSE_MS = 2000L
