package com.dailyworks.apnalaundry.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.media.ExifInterface
import com.dailyworks.apnalaundry.data.sync.SyncApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Shop logos printed on bills. A picked photo is resized (≤512px longest
 * side, JPEG) before upload, so a 12-megapixel camera shot never reaches the
 * bill renderer or the server. Logos are immutable per id, so they're cached
 * in memory and on disk once fetched.
 */
class LogoStore(private val context: Context, private val api: SyncApi) {
    private val mem = ConcurrentHashMap<String, Bitmap>()
    private val dir get() = File(context.filesDir, "logos").apply { mkdirs() }

    /** Already-loaded logo, for synchronous bill drawing. */
    fun cached(logoId: String): Bitmap? = if (logoId.isEmpty()) null else mem[logoId]

    /** Loads (disk, then network) and caches the logo; null if it can't be had. */
    suspend fun load(logoId: String): Bitmap? = withContext(Dispatchers.IO) {
        if (logoId.isEmpty()) return@withContext null
        mem[logoId]?.let { return@withContext it }
        val file = File(dir, "$logoId.jpg")
        val bytes = if (file.exists()) file.readBytes() else runCatching { api.fetchLogo(logoId) }.getOrNull()?.also { file.writeBytes(it) }
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.also { mem[logoId] = it }
    }

    /** Resizes + uploads a picked image; returns the new logo id (already cached). */
    suspend fun upload(uri: Uri): String = withContext(Dispatchers.IO) {
        val bmp = decodeResized(uri) ?: throw IllegalArgumentException("That file isn't a picture we can read")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val bytes = out.toByteArray()
        val id = api.uploadLogo(Base64.encodeToString(bytes, Base64.NO_WRAP))
        File(dir, "$id.jpg").writeBytes(bytes)
        mem[id] = bmp
        id
    }

    private fun decodeResized(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        val k = minOf(1f, MAX_SIDE.toFloat() / maxOf(raw.width, raw.height))
        val m = Matrix().apply { postScale(k, k); postRotate(rotation.toFloat()) }
        val scaled = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        // Transparent PNG logos print on white.
        val flat = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
        return flat
    }

    private companion object {
        const val MAX_SIDE = 512
    }
}
