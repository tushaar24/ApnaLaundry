package com.dailyworks.apnalaundry.print

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The shop logo made printable on a thermal printer, which prints only black
 * dots (owner, 21 Nov 2026: "logo does not print on the bill"):
 *  1. the white border around it is trimmed, so the logo itself gets the room;
 *  2. it is scaled to fit — at most 60% of the paper's width and about a third
 *     of it tall — averaging the pixels each printer dot covers;
 *  3. its tones are stretched (the darkest part prints solid black) and the
 *     middle tones lightened a little, as thermal dots spread on the paper;
 *  4. it becomes pure black / white dots by Atkinson error diffusion, so a
 *     photo or a shaded logo prints as a dot pattern, not a black blob.
 * Every pixel is exactly black or white, so EscPos's threshold prints it as drawn.
 */
object ThermalLogo {
    /** The logo as black / white dots for paper [paperDots] wide; null for a blank (all-white) picture. */
    fun of(src: Bitmap, paperDots: Int): Bitmap? {
        val sw = src.width
        val sh = src.height
        if (sw <= 0 || sh <= 0) return null
        val px = IntArray(sw * sh)
        src.getPixels(px, 0, sw, 0, 0, sw, sh)
        // Luminance on white paper (a transparent pixel is paper).
        val lum = FloatArray(sw * sh) { i ->
            val c = px[i]
            val a = Color.alpha(c) / 255f
            val l = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
            l * a + 255f * (1 - a)
        }

        // 1. trim the near-white border
        var x0 = sw; var y0 = sh; var x1 = -1; var y1 = -1
        for (y in 0 until sh) for (x in 0 until sw) {
            if (lum[y * sw + x] < 235f) {
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        if (x1 < 0) return null
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1

        // 2. scale to fit (a small logo grows at most 3×), each dot the average of the pixels under it
        val k = min(min(paperDots * 0.6f / w, paperDots * 0.32f / h), 3f)
        val dw = max(1, (w * k).roundToInt())
        val dh = max(1, (h * k).roundToInt())
        val out = FloatArray(dw * dh)
        for (dy in 0 until dh) {
            val sy0 = y0 + floor(dy / k).toInt().coerceIn(0, h - 1)
            val sy1 = (y0 + ceil((dy + 1) / k).toInt()).coerceIn(sy0 + 1, y0 + h)
            for (dx in 0 until dw) {
                val sx0 = x0 + floor(dx / k).toInt().coerceIn(0, w - 1)
                val sx1 = (x0 + ceil((dx + 1) / k).toInt()).coerceIn(sx0 + 1, x0 + w)
                var sum = 0f
                for (sy in sy0 until sy1) for (sx in sx0 until sx1) sum += lum[sy * sw + sx]
                out[dy * dw + dx] = sum / ((sy1 - sy0) * (sx1 - sx0))
            }
        }

        // 3. stretch the tones (darkest → black), lighten the middle a little
        var lo = 255f; var hi = 0f
        for (v in out) { if (v < lo) lo = v; if (v > hi) hi = v }
        val range = hi - lo
        for (i in out.indices) {
            val v = if (range >= 32f) (out[i] - lo) / range else out[i] / 255f
            out[i] = 255f * v.coerceIn(0f, 1f).pow(0.85f)
        }

        // 4. Atkinson dithering: 6/8 of each dot's error goes to its neighbours (the rest is dropped → crisp)
        val dots = IntArray(dw * dh)
        for (y in 0 until dh) for (x in 0 until dw) {
            val i = y * dw + x
            val old = out[i]
            val black = old < 128f
            dots[i] = if (black) Color.BLACK else Color.WHITE
            val err = (old - if (black) 0f else 255f) / 8f
            if (x + 1 < dw) out[i + 1] += err
            if (x + 2 < dw) out[i + 2] += err
            if (y + 1 < dh) {
                val j = i + dw
                if (x > 0) out[j - 1] += err
                out[j] += err
                if (x + 1 < dw) out[j + 1] += err
            }
            if (y + 2 < dh) out[i + 2 * dw] += err
        }
        return Bitmap.createBitmap(dots, dw, dh, Bitmap.Config.ARGB_8888)
    }
}
