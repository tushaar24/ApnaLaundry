package com.dailyworks.apnalaundry.print

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

/** The few ESC/POS commands tag printing needs — understood by any 58 / 80 mm thermal printer. */
object EscPos {
    /** ESC @ — reset the printer (clears modes another app left on). */
    val INIT = byteArrayOf(0x1B, 0x40)

    /** ESC d n — feed [lines] blank lines, so the last tag clears the tear bar. */
    fun feed(lines: Int): ByteArray = byteArrayOf(0x1B, 0x64, lines.coerceIn(0, 255).toByte())

    /**
     * [bmp] as GS v 0 raster images, [band] rows each — cheap printers choke on
     * one tall image. Dark pixels print; the rest stays paper.
     */
    fun raster(bmp: Bitmap, band: Int = 128): ByteArray {
        val out = ByteArrayOutputStream()
        rasterBands(bmp, band).forEach { out.write(it) }
        return out.toByteArray()
    }

    /**
     * The same image as one byte array per band — each a complete GS v 0 command.
     * A band must reach the printer in one go: pausing inside it makes cheap
     * printers abandon the image and print the remaining bytes as text (seen on
     * the owner's printer, 2026-11-03). Pauses go between bands.
     */
    fun rasterBands(bmp: Bitmap, band: Int = 128): List<ByteArray> {
        val w = bmp.width
        val h = bmp.height
        val rowBytes = (w + 7) / 8
        val px = IntArray(w)
        val bands = ArrayList<ByteArray>(h / band + 1)
        var top = 0
        while (top < h) {
            val rows = minOf(band, h - top)
            val out = ByteArrayOutputStream(rows * rowBytes + 8)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, rowBytes.toByte(), (rowBytes shr 8).toByte(), rows.toByte(), (rows shr 8).toByte()))
            for (y in top until top + rows) {
                bmp.getPixels(px, 0, w, 0, y, w, 1)
                for (b in 0 until rowBytes) {
                    var bits = 0
                    for (bit in 0 until 8) {
                        val x = b * 8 + bit
                        if (x < w && dark(px[x])) bits = bits or (0x80 ushr bit)
                    }
                    out.write(bits)
                }
            }
            bands.add(out.toByteArray())
            top += rows
        }
        return bands
    }

    /** A little under mid-grey counts as black: anti-aliased text edges print solid. */
    private fun dark(c: Int): Boolean =
        Color.alpha(c) >= 128 && (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 < 160
}
