package com.dailyworks.apnalaundry.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.ui.screens.bill.openWhatsApp

/** MyLaundry support: shop owners call or WhatsApp this number for help. */
const val SUPPORT_PHONE = "7676363793"
const val SUPPORT_PHONE_SHOWN = "+91 76763 63793"

/** Opens the dialer with the support number typed in. */
fun callSupport(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91$SUPPORT_PHONE"))) }
}

/** Opens a WhatsApp chat with support, a "need help" message typed in. */
fun whatsAppSupport(context: Context, shopName: String) {
    val text = if (shopName.isNotBlank()) tr("Hi, I need help with the MyLaundry app. Shop: {shop}", "shop" to shopName)
    else tr("Hi, I need help with the MyLaundry app.")
    openWhatsApp(context, "https://wa.me/91$SUPPORT_PHONE?text=" + Uri.encode(text))
}
