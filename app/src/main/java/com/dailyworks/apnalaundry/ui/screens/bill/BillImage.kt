package com.dailyworks.apnalaundry.ui.screens.bill

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.data.LogoStore
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.dailyworks.apnalaundry.ui.ShopViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Renders bills off the main thread for display (null while drawing). */
@Composable
fun rememberBillImages(shopVm: ShopViewModel, receipts: List<BillReceipt>, equalHeight: Boolean, debounceMs: Long = 0): State<List<ImageBitmap>?> {
    val context = LocalContext.current
    val tick by shopVm.logoTick.collectAsStateWithLifecycle()
    return produceState<List<ImageBitmap>?>(initialValue = null, receipts, tick) {
        if (debounceMs > 0) delay(debounceMs)
        value = withContext(Dispatchers.Default) { renderAll(context, shopVm.logos, receipts, equalHeight).map { it.asImageBitmap() } }
    }
}

/** Every receipt drawn; with [equalHeight] all stretch to the tallest. */
fun renderAll(context: Context, logos: LogoStore, receipts: List<BillReceipt>, equalHeight: Boolean): List<Bitmap> {
    val h = if (equalHeight) receipts.maxOf { BillRender.height(context, it, logos.cached(it.logoId)) } else 0f
    return receipts.map { BillRender.render(context, it, logos.cached(it.logoId), h) }
}
