package com.dailyworks.apnalaundry.ui.screens.settings

import com.dailyworks.apnalaundry.domain.BillDetails
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.BottomNav
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.NavTab
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.paywall.PaywallViewModel
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.SUPPORT_PHONE_SHOWN
import com.dailyworks.apnalaundry.ui.components.callSupport
import com.dailyworks.apnalaundry.ui.components.whatsAppSupport

private val WhatsAppGreen = Color(0xFF25D366)

@Composable
fun SettingsScreen(shopVm: ShopViewModel, navigator: AppNavigator, onLogout: (onFailed: () -> Unit) -> Unit) {
    var loggingOut by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val paywallVm: PaywallViewModel = koinViewModel()
    val state by shopVm.state.collectAsStateWithLifecycle()
    val shop = state.shop

    var name by rememberSaveable { mutableStateOf(shop.name) }
    var nameFocused by remember { mutableStateOf(false) }
    LaunchedEffect(shop.name) { if (!nameFocused) name = shop.name }
    LaunchedEffect(Unit) { Analytics.screen("settings") }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("Settings"), style = bric(28, FontWeight.Bold))

            // Your shops (multi-shop): switch / add another (paid)
            val planUi by paywallVm.ui.collectAsStateWithLifecycle()
            var shopsOpen by remember { mutableStateOf(false) }
            com.dailyworks.apnalaundry.ui.screens.shops.ShopsCard(
                shopVm, hasPlan = planUi.hasActive || planUi.inGrace,
                onSwitch = { shopsOpen = true }, onAdd = { navigator.openAddShop("settings") },
            )
            if (shopsOpen) com.dailyworks.apnalaundry.ui.screens.shops.ShopsSheet(shopVm, navigator, "settings") { shopsOpen = false }

            // Shop name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(tr("Shop name"))
                FieldBox(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                        if (BillDetails.isNameOk(name)) shopVm.updateShop(BillDetails.cleanName(name), shop.expressPct)
                    },
                    height = 52.dp,
                    capitalization = KeyboardCapitalization.Words,
                    fieldModifier = Modifier.onFocusChanged { nameFocused = it.isFocused },
                )
            }

            // Services
            AppCard {
                Row(
                    Modifier.fillMaxWidth().tap { navigator.openRates("settings") }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(tr("Services"), style = fig(16, FontWeight.Bold))
                        Text(tr("Prices for each service"), style = fig(13, color = Tokens.Muted))
                    }
                    Text(tr("Edit"), style = fig(15, FontWeight.Bold, Tokens.Blue))
                }
            }

            // Language: the whole app (and Ask) in it; saved on this phone.
            var languageOpen by remember { mutableStateOf(false) }
            AppCard {
                Row(
                    Modifier.fillMaxWidth().tap { languageOpen = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(com.dailyworks.apnalaundry.core.tr("Language"), style = fig(16, FontWeight.Bold))
                        Text(com.dailyworks.apnalaundry.core.I18n.language.native, style = fig(13, color = Tokens.Muted))
                    }
                    Text(com.dailyworks.apnalaundry.core.tr("Change"), style = fig(15, FontWeight.Bold, Tokens.Blue))
                }
            }
            if (languageOpen) {
                com.dailyworks.apnalaundry.ui.components.LanguagePickerDialog(
                    onPick = { languageOpen = false; com.dailyworks.apnalaundry.ui.components.applyLanguage(context, it, "settings") },
                    onDismiss = { languageOpen = false },
                )
            }

            // Bill design & details (same screen as onboarding step 3)
            AppCard {
                Row(
                    Modifier.fillMaxWidth().tap { navigator.openBillDesign() }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Bill design & details"), style = fig(16, FontWeight.Bold))
                        Text(tr("Design, logo, UPI QR, address, email, GSTIN, terms"), style = fig(13, color = Tokens.Muted))
                    }
                    Text(tr("Edit"), style = fig(15, FontWeight.Bold, Tokens.Blue))
                }
            }

            // Subscription row (manage / cancel the plan) — also while a cancelled
            // plan still runs, so the owner can see until when.
            val billing by paywallVm.ui.collectAsStateWithLifecycle()
            if (billing.hasActive || billing.inGrace) {
                val until = if (billing.inGrace) com.dailyworks.apnalaundry.ui.screens.paywall.fmtChargeDate(
                    com.dailyworks.apnalaundry.ui.screens.paywall.accessUntilIso(billing.status?.subscription),
                ) else ""
                AppCard {
                    Row(
                        Modifier.fillMaxWidth().tap { navigator.openSubscription() }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            // One text, so "Cancelled" wraps as a word, not letter by letter.
                            Text(
                                buildAnnotatedString {
                                    append(if (billing.status?.subscription?.plan == "annual") tr("Yearly plan") else tr("Monthly plan"))
                                    if (billing.inGrace) withStyle(SpanStyle(color = Tokens.OrangeText)) { append(" · " + tr("Cancelled")) }
                                },
                                style = fig(16, FontWeight.Bold),
                            )
                            Text(
                                when {
                                    !billing.inGrace -> tr("Unlimited orders")
                                    until.isNotEmpty() -> tr("Works till {date}", "date" to until)
                                    else -> tr("Works till the end of the paid period")
                                },
                                style = fig(13, color = Tokens.Muted),
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tokens.Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Help: talk to us, or walk through the app again with the guide.
            SectionLabel(tr("Help"))
            AppCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(tr("Need help? Talk to us"), style = fig(16, FontWeight.Bold))
                        Text(SUPPORT_PHONE_SHOWN, style = fig(14, color = Tokens.Muted))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(tr("Call us"), Modifier.weight(1f), height = 48.dp) {
                            Analytics.supportContacted("call", "settings")
                            callSupport(context)
                        }
                        PrimaryButton(tr("WhatsApp us"), Modifier.weight(1f), height = 48.dp, bg = WhatsAppGreen) {
                            Analytics.supportContacted("whatsapp", "settings")
                            whatsAppSupport(context, shop.name)
                        }
                    }
                }
            }
            // The guide is an A/B test: only its group gets the replay.
            val guideOn by shopVm.guideOn.collectAsStateWithLifecycle()
            if (guideOn) AppCard {
                Row(
                    Modifier.fillMaxWidth().tap {
                        shopVm.tourReplay()
                        navigator.openHome()
                    }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("How to use the app"), style = fig(16, FontWeight.Bold))
                        Text(tr("Step by step, with sound: new order, ready, money, khata, earnings"), style = fig(13, color = Tokens.Muted))
                    }
                    Text(tr("Show me"), style = fig(15, FontWeight.Bold, Tokens.Blue))
                }
            }

            // Phone
            AppCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Phone"), style = fig(15, FontWeight.SemiBold, Tokens.Muted))
                    Text("+91 ${shop.phone}", style = fig(15, FontWeight.Bold), maxLines = 1, softWrap = false)
                }
            }

            // Coming later
            AppCard(bg = Tokens.NeutralFill, borderColor = Tokens.NeutralFill) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Coming later"), style = fig(13, FontWeight.Bold, Tokens.Muted))
                    Text(tr("Staff logins"), style = fig(14, color = Tokens.InkSecondary))
                }
            }

            Box(
                Modifier.fillMaxWidth().height(48.dp).tap(enabled = !loggingOut) {
                    loggingOut = true
                    onLogout { loggingOut = false }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (loggingOut) tr("Logging out…") else tr("Log out / restart"), style = fig(15, FontWeight.Bold, Tokens.OrangeText))
            }
            Spacer(Modifier.height(8.dp))
        }

        BottomNav(current = NavTab.SETTINGS, onNewOrder = { navigator.openNewOrder(from = "nav") }, onSelect = navigator::selectTab)
    }
}
