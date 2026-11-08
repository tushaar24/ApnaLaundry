package com.dailyworks.apnalaundry.ui.screens.staff

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.shops.ShopRef
import com.dailyworks.apnalaundry.data.shops.StaffEvent
import com.dailyworks.apnalaundry.domain.StaffAccess
import com.dailyworks.apnalaundry.domain.StaffPermission
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens

// Staff mode on the staff member's phone (1.0.20, docs/specs/staff.md): the strip and welcome on
// Home, "added you / removed you", and the screen while the owner's plan has ended.

/**
 * What the person may do on the shop on screen — provided once around the app (AppNavGraph), read
 * by any screen or sheet: `LocalStaffAccess.current.can(StaffPermission.X)`, `.isOwner`. The
 * server checks the same switches on every sync, so a missed button can't change anything.
 */
val LocalStaffAccess = androidx.compose.runtime.compositionLocalOf { StaffAccess.OWNER }

/** "Ramesh · Sharma Laundry": always on Home in a shop worked as staff. */
@Composable
fun StaffStrip(access: StaffAccess, modifier: Modifier = Modifier) {
    if (!access.isStaff) return
    Row(
        modifier.fillMaxWidth().background(Tokens.BlueLight).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("👤", style = fig(13))
        Text(
            tr("{name} · staff at {shop}", "name" to Selectors.firstName(access.staffName).ifBlank { tr("You") }, "shop" to access.shopName),
            style = fig(13, FontWeight.SemiBold, Tokens.BlueText), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Once, after the first staff login: what this person can do here. */
@Composable
fun StaffWelcomeCard(access: StaffAccess, onClose: () -> Unit) {
    LaunchedEffect(Unit) { Analytics.staffWelcome("shown") }
    val can = StaffPermission.entries.filter { access.can(it) }.map { tr(it.label) }
    AppCard(bg = Tokens.BlueLight, borderColor = Tokens.BlueLight) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                tr("Welcome {name}! You're staff at {shop}.", "name" to Selectors.firstName(access.staffName).ifBlank { tr("you") }, "shop" to access.shopName),
                style = fig(16, FontWeight.Bold),
            )
            Text(tr("You can see orders, mark picked up and ready, and print tags."), style = fig(14, color = Tokens.InkSecondary))
            if (can.isNotEmpty()) Text(tr("{owner} also lets you: {list}", "owner" to access.ownerName.ifBlank { tr("The owner") }, "list" to can.joinToString(", ")), style = fig(14, color = Tokens.InkSecondary))
            Text(
                tr("Got it"), style = fig(15, FontWeight.Bold, Tokens.Blue),
                modifier = Modifier.tap { Analytics.staffWelcome("closed"); onClose() }.padding(vertical = 4.dp),
            )
        }
    }
}

/** "Sharma Laundry added you as staff — Open it?" and "… removed you", app-wide. */
@Composable
fun StaffEventsHost(shopVm: ShopViewModel) {
    var added by remember { mutableStateOf<ShopRef?>(null) }
    LaunchedEffect(Unit) {
        shopVm.staffEvents.collect { e ->
            when (e) {
                is StaffEvent.Added -> { added = e.ref; Analytics.staffAddedPrompt("shown") }
                is StaffEvent.Removed -> {
                    Analytics.staffRemovedNotice()
                    shopVm.showInfo(tr("{shop} removed you as staff", "shop" to e.shopName.ifBlank { tr("A shop") }))
                }
            }
        }
    }
    added?.let { ref ->
        val owner = ref.staff?.ownerName.orEmpty()
        AlertDialog(
            onDismissRequest = { added = null; Analytics.staffAddedPrompt("later") },
            title = { Text(tr("{shop} added you as staff", "shop" to ref.name), style = fig(18, FontWeight.Bold)) },
            text = {
                Text(
                    if (owner.isNotBlank()) tr("{owner} added you to work at {shop}. Switch shops anytime in Settings.", "owner" to owner, "shop" to ref.name)
                    else tr("You can work at {shop} from this app. Switch shops anytime in Settings.", "shop" to ref.name),
                    style = fig(15),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    added = null
                    Analytics.staffAddedPrompt("open")
                    shopVm.switchShop(ref.id)
                    Analytics.staffShopOpened("added_prompt")
                }) { Text(tr("Open {shop}", "shop" to ref.name), style = fig(15, FontWeight.Bold, Tokens.Blue)) }
            },
            dismissButton = { TextButton(onClick = { added = null; Analytics.staffAddedPrompt("later") }) { Text(tr("Later"), style = fig(15, FontWeight.Bold)) } },
            containerColor = Tokens.Card,
        )
    }
}

/**
 * The owner's plan has ended: staff can't work in the shop until it's renewed (the server says
 * 402). Over the whole app; it goes away by itself at the first sync after the owner renews.
 */
@Composable
fun StaffRenewGate(shopVm: ShopViewModel, active: Boolean) {
    if (!active) return
    val access by shopVm.access.collectAsStateWithLifecycle()
    val state by shopVm.state.collectAsStateWithLifecycle()
    val shops by shopVm.shops.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(access.staffId) { Analytics.staffRenewShown() }
    BackHandler { }
    val others = shops.filter { it.id != shopVm.activeShopId.value }
    Box(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).tap { }, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(72.dp).rounded(999.dp).background(Tokens.OrangeLight), contentAlignment = Alignment.Center) { Text("⏸", style = fig(32)) }
            Text(tr("{shop}'s plan has ended", "shop" to access.shopName), style = bric(22, FontWeight.Bold), textAlign = TextAlign.Center)
            Text(
                tr("Ask {owner} to renew MyLaundry. You can work here again as soon as they do.", "owner" to access.ownerName.ifBlank { tr("the owner") }),
                style = fig(15, color = Tokens.InkSecondary), textAlign = TextAlign.Center,
            )
            val phone = state.shop.phone.filter(Char::isDigit)
            if (phone.length >= 10) {
                PrimaryButton(tr("Call the shop"), Modifier.fillMaxWidth(), height = 52.dp) {
                    Analytics.staffRenewAction("call")
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                }
            }
            if (others.isNotEmpty()) {
                OutlineButton(tr("Switch shop"), Modifier.fillMaxWidth(), height = 52.dp) {
                    Analytics.staffRenewAction("switch")
                    val to = others.first()
                    shopVm.switchShop(to.id)
                    if (to.staff != null) Analytics.staffShopOpened("switch") else Analytics.staffOwnShopOpened()
                }
            }
        }
    }
}
