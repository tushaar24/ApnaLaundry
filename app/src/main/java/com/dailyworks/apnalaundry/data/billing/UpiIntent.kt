package com.dailyworks.apnalaundry.data.billing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the backend's UPI AutoPay intent so the user can pick their UPI app to
 * approve the mandate. No Razorpay SDK involved. A `upi://` link shows the UPI
 * app chooser; an https authorization link opens the browser. Returns false if
 * nothing can handle it — the UI should then show the QR code (rendered from
 * the same intentUrl) to scan from another device.
 */
object UpiIntent {
    fun open(context: Context, intentUrl: String): Boolean {
        if (intentUrl.isBlank()) return false
        return try {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intentUrl.startsWith("upi:")) {
                val chooser = Intent.createChooser(view, "Pay with UPI")
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(chooser)
            } else {
                context.startActivity(view)
            }
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
