package com.umutk.pulse.features

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PSlider
import com.umutk.pulse.PulsePick
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Photo tools of the Toolkit: ASCII art, a halftone QR code and a tilt-shift miniature. Each takes a photo (or text), shows the result at once and saves it to the gallery
 * (Pictures/Pulse). The methods follow the public ones: ASCII modes and dithering as in Atelier ASCII (MIT, github.com/ — see THIRD_PARTY.md), the halftone QR as in the
 * "submodule" method of kloet.net/usr/halftone (a QR module is cut into 3x3 blocks, the middle one keeps the module's colour, the other eight carry the picture).
 */

// ---- shared helpers ------------------------------------------------------------------------------------------------------------------------------------

internal object PhotoToolsCore {
    /** The photo at [uri], its longest side at most [maxSide], turned upright by its EXIF tag. */
    fun load(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var step = 1
        while (max(o.outWidth, o.outHeight) / (step * 2) >= maxSide) step *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = step }) } ?: return@runCatching null
        val deg = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            } ?: 0f
        }.getOrDefault(0f)
        val turned = if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg) }, true)
        val k = maxSide.toFloat() / max(turned.width, turned.height)
        if (k >= 1f) turned else Bitmap.createScaledBitmap(turned, (turned.width * k).roundToInt().coerceAtLeast(1), (turned.height * k).roundToInt().coerceAtLeast(1), true)
    }.getOrNull()

    /** Saves [bmp] as a PNG in Pictures/Pulse; true when it is there. */
    fun save(ctx: Context, bmp: Bitmap, prefix: String): Boolean {
        val name = prefix + "-" + System.currentTimeMillis() + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Pulse")
        }
        val ok = runCatching {
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        Toast.makeText(ctx, if (ok) "Pictures/Pulse/$name" else tr("Could not save", "Kaydedilemedi"), Toast.LENGTH_SHORT).show()
        return ok
    }

    /** Luminance 0..1 of the picture scaled to [w] x [h]. */
    fun gray(src: Bitmap, w: Int, h: Int): FloatArray {
        val b = Bitmap.createScaledBitmap(src, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return FloatArray(px.size) { i -> val p = px[i]; val a = (p ushr 24) / 255f; val l = (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) / 255f; l * a + (1 - a) }
    }

    /** Stretches the tones so the darkest is 0 and the lightest 1 (auto levels). */
    fun levels(g: FloatArray) {
        var lo = 1f; var hi = 0f
        for (v in g) { if (v < lo) lo = v; if (v > hi) hi = v }
        if (hi - lo < .02f) return
        for (i in g.indices) g[i] = ((g[i] - lo) / (hi - lo)).coerceIn(0f, 1f)
    }

    /**
     * Dithers [g] (0..1, w x h) to 0 / 1 in place. kind: 0 none (threshold [thr]), 1 Floyd-Steinberg, 2 Atkinson, 3 Bayer 4x4.
     * Cells with [fixed] >= 0 are set to that value and spread no error (the middle blocks of the halftone QR).
     */
    fun dither(g: FloatArray, w: Int, h: Int, kind: Int, thr: Float = .5f, strength: Float = 1f, fixed: ByteArray? = null) {
        val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val f = fixed?.get(i)?.toInt() ?: -1
            if (f >= 0) { g[i] = f.toFloat(); continue }
            val old = g[i]
            val nv = when (kind) { 3 -> if (old > (bayer[(y and 3) * 4 + (x and 3)] + .5f) / 16f) 1f else 0f; else -> if (old > thr) 1f else 0f }
            g[i] = nv
            val err = (old - nv) * strength
            fun add(dx: Int, dy: Int, k: Float) {
                val xx = x + dx; val yy = y + dy
                if (xx < 0 || xx >= w || yy >= h) return
                val j = yy * w + xx
                if (fixed != null && fixed[j] >= 0) return
                g[j] += err * k
            }
            when (kind) {
                1 -> { add(1, 0, 7f / 16); add(-1, 1, 3f / 16); add(0, 1, 5f / 16); add(1, 1, 1f / 16) }
                2 -> { for ((dx, dy) in listOf(1 to 0, 2 to 0, -1 to 1, 0 to 1, 1 to 1, 0 to 2)) add(dx, dy, 1f / 8) }
            }
        }
    }

    // ---- ASCII -------------------------------------------------------------------------------------------------------------------------------------------

    const val MODE_TONES = 0; const val MODE_CHARS = 1; const val MODE_TEXT = 2; const val MODE_BITS = 3; const val MODE_BRAILLE = 4

    /** The picture as rows of characters, [cols] wide. [dark] true means ink on light paper (dark tones get the dense characters). */
    fun asciiRows(src: Bitmap, mode: Int, cols: Int, invert: Boolean, dither: Int, text: String, dark: Boolean): List<String> {
        val aspect = src.height.toFloat() / src.width
        fun dens(g: Float): Float { val v = if (invert) g else 1f - g; return if (dark) v else 1f - v }   // 0 = empty cell, 1 = dense cell
        return when (mode) {
            MODE_BRAILLE -> {
                val gw = cols * 2
                val gh = (max(4f, gw * aspect) / 4f).roundToInt().coerceAtLeast(1) * 4
                val g = gray(src, gw, gh); levels(g)
                for (i in g.indices) g[i] = 1f - dens(g[i])      // 1 = dot off (light) for the dither, so a dense cell is a dot
                dither(g, gw, gh, dither, .5f)
                val bits = intArrayOf(0x01, 0x02, 0x04, 0x40, 0x08, 0x10, 0x20, 0x80)
                List(gh / 4) { r ->
                    val sb = StringBuilder()
                    for (cx in 0 until cols) {
                        var code = 0
                        for (dx in 0..1) for (dy in 0..3) if (g[(r * 4 + dy) * gw + cx * 2 + dx] < .5f) code = code or bits[dx * 4 + dy]
                        sb.append((0x2800 + code).toChar())
                    }
                    sb.toString()
                }
            }
            else -> {
                val rows = (cols * aspect * .5f).roundToInt().coerceAtLeast(1)
                val g = gray(src, cols, rows); levels(g)
                if (mode == MODE_BITS) {
                    for (i in g.indices) g[i] = 1f - dens(g[i])
                    dither(g, cols, rows, dither, .5f)
                    return List(rows) { r -> String(CharArray(cols) { c -> if (g[r * cols + c] < .5f) '█' else ' ' }) }
                }
                val ramp = if (mode == MODE_TONES) " ░▒▓█" else " .:-=+*#%@"
                val letters = text.filter { !it.isWhitespace() }.ifEmpty { "ASCII" }
                var n = 0
                List(rows) { r ->
                    val sb = StringBuilder()
                    for (c in 0 until cols) {
                        val d = dens(g[r * cols + c])
                        if (mode == MODE_TEXT) { if (d > .22f) { sb.append(letters[n % letters.length]); n++ } else sb.append(' ') }
                        else sb.append(ramp[(d * (ramp.length - 1)).roundToInt().coerceIn(0, ramp.length - 1)])
                    }
                    sb.toString()
                }
            }
        }
    }

    /** The rows drawn as a picture: [ink] on [paper]; with [colour] each character takes the colour of its place in [src]. */
    fun asciiBitmap(rows: List<String>, paper: Int, ink: Int, src: Bitmap?, colour: Boolean): Bitmap {
        val cw = 10f; val ch = 18f
        val cols = rows.maxOfOrNull { it.length } ?: 1
        val bmp = Bitmap.createBitmap((cols * cw).toInt().coerceAtLeast(1), (rows.size * ch).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(paper)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = 16f; color = ink }
        val small = if (colour && src != null) Bitmap.createScaledBitmap(src, cols.coerceAtLeast(1), rows.size.coerceAtLeast(1), true) else null
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { x, chr ->
                if (chr == ' ') return@forEachIndexed
                if (small != null) p.color = small.getPixel(min(x, small.width - 1), min(r, small.height - 1)) or (0xFF shl 24)
                c.drawText(chr.toString(), x * cw, r * ch + 14f, p)
            }
        }
        return bmp
    }

    // ---- tilt-shift --------------------------------------------------------------------------------------------------------------------------------------

    /** A box blur (running sums) over [px] ([w] x [h]), [r] pixels, [passes] times: three passes look like a Gaussian blur. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int, passes: Int) {
        if (r < 1) return
        val tmp = IntArray(px.size)
        repeat(passes) {
            // horizontal
            for (y in 0 until h) {
                var sa = 0; var sr = 0; var sg = 0; var sb = 0
                val row = y * w
                for (x in -r..r) { val p = px[row + x.coerceIn(0, w - 1)]; sa += p ushr 24; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
                val d = 2 * r + 1
                for (x in 0 until w) {
                    tmp[row + x] = ((sa / d) shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                    val o = px[row + (x - r).coerceIn(0, w - 1)]; val n = px[row + (x + r + 1).coerceIn(0, w - 1)]
                    sa += (n ushr 24) - (o ushr 24); sr += ((n shr 16) and 255) - ((o shr 16) and 255); sg += ((n shr 8) and 255) - ((o shr 8) and 255); sb += (n and 255) - (o and 255)
                }
            }
            // vertical
            for (x in 0 until w) {
                var sa = 0; var sr = 0; var sg = 0; var sb = 0
                for (y in -r..r) { val p = tmp[y.coerceIn(0, h - 1) * w + x]; sa += p ushr 24; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
                val d = 2 * r + 1
                for (y in 0 until h) {
                    px[y * w + x] = ((sa / d) shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                    val o = tmp[(y - r).coerceIn(0, h - 1) * w + x]; val n = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                    sa += (n ushr 24) - (o ushr 24); sr += ((n shr 16) and 255) - ((o shr 16) and 255); sg += ((n shr 8) and 255) - ((o shr 8) and 255); sb += (n and 255) - (o and 255)
                }
            }
        }
    }

    /**
     * The miniature look: the picture is blurred away from the focus (a band across or down it, or an oval), then made more colourful and contrasty, as a model photo is.
     * [mode] 0 horizontal band, 1 vertical band, 2 oval; [centre] and [size] are 0..1 of the height (width), [soft] how wide the change from sharp to blurred is (0..1).
     */
    fun tiltShift(src: Bitmap, mode: Int, centre: Float, size: Float, soft: Float, blur: Float, sat: Float, contrast: Float): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        // the blurred layer is made small (quick) and brought back to the full size
        val k = min(1f, 700f / max(w, h))
        val sw = (w * k).roundToInt().coerceAtLeast(1); val sh = (h * k).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sp = IntArray(sw * sh); small.getPixels(sp, 0, sw, 0, 0, sw, sh)
        boxBlur(sp, sw, sh, (blur * 16f * max(sw, sh) / 700f).roundToInt().coerceAtLeast(1), 3)
        val bl = Bitmap.createBitmap(sp, sw, sh, Bitmap.Config.ARGB_8888)
        val big = Bitmap.createScaledBitmap(bl, w, h, true)
        val bp = IntArray(w * h); big.getPixels(bp, 0, w, 0, 0, w, h)
        val half = size / 2f
        val edge = (soft * .5f).coerceAtLeast(.01f)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val fy = y.toFloat() / h; val fx = x.toFloat() / w
            val d = when (mode) {
                1 -> abs(fx - centre)
                2 -> kotlin.math.sqrt(((fx - .5f) / .5f).pow(2) + ((fy - centre) / .5f).pow(2)) * .5f
                else -> abs(fy - centre)
            }
            val t = ((d - half) / edge).coerceIn(0f, 1f)
            val a = t * t * (3f - 2f * t)        // smooth step: no visible line where the blur starts
            val s = px[y * w + x]; val b = bp[y * w + x]
            fun mix(sh: Int) = (((s shr sh) and 255) * (1 - a) + ((b shr sh) and 255) * a).toInt().coerceIn(0, 255)
            out[y * w + x] = (255 shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
        val mixed = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        // colour and contrast
        val cm = ColorMatrix().apply { setSaturation(sat) }
        val ct = contrast; val tr0 = (1f - ct) * 128f
        cm.postConcat(ColorMatrix(floatArrayOf(ct, 0f, 0f, 0f, tr0, 0f, ct, 0f, 0f, tr0, 0f, 0f, ct, 0f, tr0, 0f, 0f, 0f, 1f, 0f)))
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(res).drawBitmap(mixed, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return res
    }

    // ---- effects for the photo editor ----------------------------------------------------------------------------------------------------------------------

    /** The editor's effects, by id: 0 shadow (vignette), 1 halftone dots, 2 tilt-shift, 3 pixels, 4 grain, 5 soft blur. */
    fun effect(src: Bitmap, id: Int): Bitmap = when (id) {
        0 -> vignette(src)
        1 -> dots(src)
        2 -> tiltShift(src, 0, .55f, .2f, .5f, .6f, 1.35f, 1.1f)
        3 -> pixelate(src)
        4 -> grain(src)
        else -> softBlur(src)
    }

    private fun copyOf(src: Bitmap): Bitmap = src.copy(Bitmap.Config.ARGB_8888, true)

    /** A shadow around the edges: the picture darkens towards the corners. */
    private fun vignette(src: Bitmap): Bitmap {
        val out = copyOf(src); val c = Canvas(out)
        val w = out.width.toFloat(); val h = out.height.toFloat()
        val r = kotlin.math.sqrt(w * w + h * h) / 2f
        val p = Paint().apply { shader = android.graphics.RadialGradient(w / 2f, h / 2f, r, intArrayOf(0x00000000, 0x00000000, 0xB3000000.toInt()), floatArrayOf(0f, .45f, 1f), android.graphics.Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, p)
        return out
    }

    /** Halftone: the picture as coloured dots of different sizes on paper. */
    private fun dots(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val cellPx = max(6, max(w, h) / 90)
        val cols = (w + cellPx - 1) / cellPx; val rows = (h + cellPx - 1) / cellPx
        val small = Bitmap.createScaledBitmap(src, cols, rows, true)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(android.graphics.Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until rows) for (x in 0 until cols) {
            val px = small.getPixel(x, y)
            val l = (0.299f * ((px shr 16) and 255) + 0.587f * ((px shr 8) and 255) + 0.114f * (px and 255)) / 255f
            val r = cellPx * .62f * kotlin.math.sqrt(1f - l * .92f)
            p.color = px or (0xFF shl 24)
            c.drawCircle((x + .5f) * cellPx, (y + .5f) * cellPx, r, p)
        }
        return out
    }

    private fun pixelate(src: Bitmap): Bitmap {
        val block = max(6, max(src.width, src.height) / 90)
        val small = Bitmap.createScaledBitmap(src, max(1, src.width / block), max(1, src.height / block), false)
        return Bitmap.createScaledBitmap(small, src.width, src.height, false)
    }

    private fun grain(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val r = java.util.Random(7)
        for (i in px.indices) {
            val n = (r.nextGaussian() * 22).toInt(); val p = px[i]
            px[i] = (p and (0xFF shl 24)) or (((((p shr 16) and 255) + n).coerceIn(0, 255)) shl 16) or (((((p shr 8) and 255) + n).coerceIn(0, 255)) shl 8) or (((p and 255) + n).coerceIn(0, 255))
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun softBlur(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val k = min(1f, 900f / max(w, h))
        val sw = (w * k).roundToInt().coerceAtLeast(1); val sh = (h * k).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh); small.getPixels(px, 0, sw, 0, 0, sw, sh)
        boxBlur(px, sw, sh, max(2, max(sw, sh) / 140), 3)
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888), w, h, true)
    }

    // ---- halftone QR -------------------------------------------------------------------------------------------------------------------------------------

    /** Modules that must keep their own colour whole: the finder patterns and their separators, the timing lines, the alignment patterns and the format / version areas. */
    private fun isFunction(x: Int, y: Int, n: Int, version: Int): Boolean {
        if ((x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8)) return true
        if (x == 6 || y == 6) return true
        if ((x == 8 && (y < 9 || y >= n - 8)) || (y == 8 && (x < 9 || x >= n - 8))) return true
        if (version >= 7 && ((x < 6 && y >= n - 11 && y <= n - 9) || (y < 6 && x >= n - 11 && x <= n - 9))) return true
        if (version >= 2) {
            val count = version / 7 + 2
            val last = n - 7
            val step = if (count == 2) last - 6 else (((last - 6) + (count - 1) * 2 - 1) / ((count - 1) * 2)) * 2
            val pos = ArrayList<Int>(); pos.add(6)
            var p = last - step * (count - 2)
            while (p <= last) { pos.add(p); p += step }
            for (i in pos) for (j in pos) {
                if ((i == 6 && j == 6) || (i == 6 && j == last) || (i == last && j == 6)) continue
                if (abs(x - i) <= 2 && abs(y - j) <= 2) return true
            }
        }
        return false
    }

    /**
     * A QR code whose free submodules show a picture. Each module is 3x3 blocks of [factor] x [factor] cells of [cell] pixels; the middle block keeps the module's colour,
     * the other eight (outside the function patterns) are dithered from the picture ([bgKind] 0 picture, 1 noise, 2 plain code). Null when the text does not fit.
     */
    fun halftoneQr(text: String, level: Int, cell: Int, factor: Int, bg: Bitmap?, bgKind: Int, ditherKind: Int, thr: Float, strength: Float, gamma: Float): Bitmap? {
        if (text.isEmpty()) return null
        val ec = when (level) { 0 -> ErrorCorrectionLevel.L; 1 -> ErrorCorrectionLevel.M; 2 -> ErrorCorrectionLevel.Q; else -> ErrorCorrectionLevel.H }
        val code = runCatching { Encoder.encode(text, ec, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 0)) }.getOrNull() ?: return null
        val m = code.matrix; val n = m.width; val version = code.version.versionNumber
        val f = factor.coerceIn(1, 4)
        val gw = n * 3 * f
        val g = FloatArray(gw * gw)
        val fixed = ByteArray(gw * gw) { -1 }
        // the picture (or noise) under the whole code, centre-cropped to a square
        when {
            bgKind == 0 && bg != null -> {
                val side = min(bg.width, bg.height)
                val sq = Bitmap.createBitmap(bg, (bg.width - side) / 2, (bg.height - side) / 2, side, side)
                val v = gray(sq, gw, gw); levels(v)
                for (i in v.indices) g[i] = v[i].toDouble().pow(gamma.toDouble()).toFloat()
            }
            else -> { val r = java.util.Random(text.hashCode().toLong()); for (i in g.indices) g[i] = r.nextFloat() }
        }
        for (my in 0 until n) for (mx in 0 until n) {
            val dark = m.get(mx, my).toInt() == 1
            val whole = bgKind == 2 || isFunction(mx, my, n, version)
            for (by in 0 until 3 * f) for (bx in 0 until 3 * f) {
                val inMiddle = bx in f until 2 * f && by in f until 2 * f
                if (whole || inMiddle) fixed[(my * 3 * f + by) * gw + mx * 3 * f + bx] = if (dark) 0 else 1
            }
        }
        dither(g, gw, gw, ditherKind, thr, strength, fixed)
        val quiet = 4 * 3 * f
        val total = (gw + quiet * 2) * cell
        val bmp = Bitmap.createBitmap(total, total, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(android.graphics.Color.WHITE)
        val p = Paint().apply { color = android.graphics.Color.BLACK }
        for (y in 0 until gw) for (x in 0 until gw) if (g[y * gw + x] < .5f)
            c.drawRect(((x + quiet) * cell).toFloat(), ((y + quiet) * cell).toFloat(), ((x + quiet + 1) * cell).toFloat(), ((y + quiet + 1) * cell).toFloat(), p)
        return bmp
    }
}

// ---- screens ---------------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun PtLabel(s: String) = Text(s, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun PtChips(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, s -> PChip(i == selected, s) { onPick(i) } }
    }
}

@Composable
private fun PtPreview(bmp: Bitmap?, hint: String, bg: Color = T.panel) {
    Box(Modifier.fillMaxWidth().height(300.dp).background(bg), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Fit)
        else Text(hint, color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
    }
}

/** ASCII art: a photo as characters (tones, characters, your own text, 1-bit, Braille), with dithering and screen themes; copied as text or saved as a picture. */
@Composable
internal fun AsciiArtTool() {
    val ctx = LocalContext.current
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableIntStateOf(PhotoToolsCore.MODE_TONES) }
    var cols by remember { mutableFloatStateOf(90f) }
    var invert by remember { mutableStateOf(false) }
    var dither by remember { mutableIntStateOf(1) }
    var theme by remember { mutableIntStateOf(0) }
    var colour by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("PULSE") }
    var rows by remember { mutableStateOf<List<String>>(emptyList()) }
    var out by remember { mutableStateOf<Bitmap?>(null) }
    val themes = listOf(
        Triple(tr("Paper", "Kağıt"), 0xFFF5F1E8.toInt(), 0xFF1A1A1A.toInt()), Triple(tr("Terminal", "Terminal"), 0xFF0B0B0B.toInt(), 0xFFE6E6E6.toInt()),
        Triple("Phosphor", 0xFF001A00.toInt(), 0xFF33FF66.toInt()), Triple(tr("Amber", "Kehribar"), 0xFF1A1000.toInt(), 0xFFFFB000.toInt()),
        Triple("Sepia", 0xFFE9D8B8.toInt(), 0xFF4A3520.toInt()), Triple("Riso", 0xFFF2F0E6.toInt(), 0xFF1D4ED8.toInt()), Triple("Cyber", 0xFF0A0014.toInt(), 0xFFFF2BD6.toInt()),
    )
    val pick = rememberLauncherForActivityResult(PulsePick.GetContent()) { uri -> if (uri != null) src = PhotoToolsCore.load(ctx, uri, 1200) }
    LaunchedEffect(src, mode, cols, invert, dither, theme, colour, text) {
        val s = src ?: return@LaunchedEffect
        delay(150)
        val th = themes[theme]
        val dark = ((th.second shr 16) and 255) > 128   // a light paper: dark ink, dense characters on dark tones
        withContext(Dispatchers.Default) {
            val r = PhotoToolsCore.asciiRows(s, mode, cols.roundToInt(), invert, dither, text, dark)
            val b = PhotoToolsCore.asciiBitmap(r, th.second, th.third, s, colour)
            rows = r; out = b
        }
    }
    PButton(tr("Choose a photo", "Fotoğraf seç"), Modifier.fillMaxWidth()) { pick.launch("image/*") }
    PtPreview(out, tr("Choose a photo to turn it into ASCII art.", "ASCII sanatı için bir fotoğraf seç."), Color(themes[theme].second))
    PtLabel(tr("Mode", "Mod"))
    PtChips(listOf(tr("Tones ░▒▓█", "Tonlar ░▒▓█"), tr("Characters", "Karakterler"), tr("Text", "Yazı"), "1-bit", "Braille"), mode) { mode = it }
    if (mode == PhotoToolsCore.MODE_TEXT) PField(text, { text = it.take(40) }, tr("The text the picture is written with", "Resmin yazılacağı yazı"), Modifier.fillMaxWidth())
    PtLabel(tr("Width: ${cols.roundToInt()} characters", "Genişlik: ${cols.roundToInt()} karakter"))
    PSlider(cols, { cols = it }, 30f..220f) { }
    if (mode == PhotoToolsCore.MODE_BITS || mode == PhotoToolsCore.MODE_BRAILLE) {
        PtLabel(tr("Dithering", "Dither"))
        PtChips(listOf(tr("None", "Yok"), "Floyd–Steinberg", "Atkinson", "Bayer"), dither) { dither = it }
    }
    PtLabel(tr("Screen theme", "Ekran teması"))
    PtChips(themes.map { it.first }, theme) { theme = it }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PChip(invert, tr("Invert", "Ters çevir")) { invert = !invert }
        PChip(colour, tr("Colour", "Renkli")) { colour = !colour }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PButton(tr("Save to gallery", "Galeriye kaydet"), Modifier.weight(1f)) { out?.let { PhotoToolsCore.save(ctx, it, "ASCII") } }
        PButton(tr("Copy text", "Metni kopyala"), Modifier.weight(1f)) {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("", rows.joinToString("\n")))
            Toast.makeText(ctx, tr("Copied", "Kopyalandı"), Toast.LENGTH_SHORT).show()
        }
    }
}

/** Halftone QR: a QR code that carries a picture (the method of kloet.net/usr/halftone); the code stays readable, the picture shows in its dots. */
@Composable
internal fun HalftoneQrTool() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("https://github.com/xUmutKx/Pulse") }
    var bg by remember { mutableStateOf<Bitmap?>(null) }
    var level by remember { mutableIntStateOf(2) }
    var cell by remember { mutableFloatStateOf(4f) }
    var factor by remember { mutableFloatStateOf(1f) }
    var bgKind by remember { mutableIntStateOf(0) }
    var dither by remember { mutableIntStateOf(1) }
    var thr by remember { mutableFloatStateOf(128f) }
    var strength by remember { mutableFloatStateOf(1f) }
    var gamma by remember { mutableFloatStateOf(1f) }
    var out by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(PulsePick.GetContent()) { uri -> if (uri != null) { bg = PhotoToolsCore.load(ctx, uri, 900); bgKind = 0 } }
    LaunchedEffect(text, bg, level, cell, factor, bgKind, dither, thr, strength, gamma) {
        delay(200)
        val r = withContext(Dispatchers.Default) { PhotoToolsCore.halftoneQr(text, level, cell.roundToInt(), factor.roundToInt(), bg, bgKind, dither, thr / 255f, strength, gamma) }
        failed = r == null && text.isNotEmpty(); out = r ?: out
    }
    PField(text, { text = it.take(600) }, tr("Text or link of the code", "Kodun metni ya da bağlantısı"), Modifier.fillMaxWidth())
    PButton(tr("Choose the picture", "Resim seç"), Modifier.fillMaxWidth()) { pick.launch("image/*") }
    PtPreview(out, tr("Write the text of the code.", "Kodun metnini yaz."), Color.White)
    if (failed) Text(tr("The text is too long for this correction level.", "Metin bu düzeltme düzeyi için çok uzun."), color = Color(0xFFE53935), fontSize = 12.sp)
    PtLabel(tr("Error correction", "Hata düzeltme"))
    PtChips(listOf("Low ~7%", "Medium ~15%", "Quartile ~25%", "High ~30%"), level) { level = it }
    PtLabel(tr("Background", "Arka plan"))
    PtChips(listOf(tr("Picture", "Resim"), tr("Noise", "Gürültü"), tr("Plain code", "Düz kod")), bgKind) { bgKind = it }
    PtLabel(tr("Dithering", "Dither"))
    PtChips(listOf("Floyd–Steinberg", "Atkinson", tr("Ordered", "Sıralı"), tr("Threshold", "Eşik")), when (dither) { 1 -> 0; 2 -> 1; 3 -> 2; else -> 3 }) { dither = when (it) { 0 -> 1; 1 -> 2; 2 -> 3; else -> 0 } }
    PtLabel(tr("Dot size: ${cell.roundToInt()}", "Nokta boyutu: ${cell.roundToInt()}"))
    PSlider(cell, { cell = it }, 2f..10f) { }
    PtLabel(tr("Detail of the picture: ${factor.roundToInt()}", "Resim ayrıntısı: ${factor.roundToInt()}"))
    PSlider(factor, { factor = it }, 1f..3f) { }
    PtLabel(tr("Threshold: ${thr.roundToInt()}", "Eşik: ${thr.roundToInt()}"))
    PSlider(thr, { thr = it }, 0f..255f) { }
    PtLabel(tr("Error strength: %.1f".format(strength), "Hata gücü: %.1f".format(strength)))
    PSlider(strength, { strength = it }, 0f..1.5f) { }
    PtLabel("Gamma: %.1f".format(gamma))
    PSlider(gamma, { gamma = it }, .5f..2f) { }
    Text(tr("Check the code with a scanner before you use it.", "Kullanmadan önce kodu bir tarayıcıyla dene."), color = T.sub, fontSize = 12.sp)
    PButton(tr("Save to gallery", "Galeriye kaydet"), Modifier.fillMaxWidth()) { out?.let { PhotoToolsCore.save(ctx, it, "QR") } }
}

/** Tilt-shift: a photo that looks like a model: a band (or an oval) stays sharp, the rest is blurred, colours and contrast are lifted. */
@Composable
internal fun TiltShiftTool() {
    val ctx = LocalContext.current
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var centre by remember { mutableFloatStateOf(.55f) }
    var size by remember { mutableFloatStateOf(.22f) }
    var soft by remember { mutableFloatStateOf(.5f) }
    var blur by remember { mutableFloatStateOf(.6f) }
    var sat by remember { mutableFloatStateOf(1.4f) }
    var contrast by remember { mutableFloatStateOf(1.12f) }
    var out by remember { mutableStateOf<Bitmap?>(null) }
    val pick = rememberLauncherForActivityResult(PulsePick.GetContent()) { uri -> if (uri != null) src = PhotoToolsCore.load(ctx, uri, 1000) }
    LaunchedEffect(src, mode, centre, size, soft, blur, sat, contrast) {
        val s = src ?: return@LaunchedEffect
        delay(120)
        out = withContext(Dispatchers.Default) { PhotoToolsCore.tiltShift(s, mode, centre, size, soft, blur, sat, contrast) }
    }
    PButton(tr("Choose a photo", "Fotoğraf seç"), Modifier.fillMaxWidth()) { pick.launch("image/*") }
    PtPreview(out, tr("A photo of a whole scene seen from above works best.", "Yukarıdan görülen geniş bir sahne en iyi sonucu verir."))
    PtLabel(tr("Focus", "Odak"))
    PtChips(listOf(tr("Horizontal", "Yatay"), tr("Vertical", "Dikey"), tr("Oval", "Oval")), mode) { mode = it }
    PtLabel(tr("Position", "Konum"))
    PSlider(centre, { centre = it }, 0f..1f) { }
    PtLabel(tr("Sharp area", "Net alan"))
    PSlider(size, { size = it }, .05f..0.8f) { }
    PtLabel(tr("Transition", "Geçiş"))
    PSlider(soft, { soft = it }, .05f..1f) { }
    PtLabel(tr("Blur", "Bulanıklık"))
    PSlider(blur, { blur = it }, .1f..1f) { }
    PtLabel(tr("Colour", "Renk"))
    PSlider(sat, { sat = it }, .5f..2.2f) { }
    PtLabel(tr("Contrast", "Kontrast"))
    PSlider(contrast, { contrast = it }, .8f..1.5f) { }
    PButton(tr("Save to gallery", "Galeriye kaydet"), Modifier.fillMaxWidth()) {
        // saved at the size of the preview picture (up to 1000 px on the long side)
        out?.let { PhotoToolsCore.save(ctx, it, "TiltShift") }
    }
}
