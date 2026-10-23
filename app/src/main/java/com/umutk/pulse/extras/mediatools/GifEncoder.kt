package com.umutk.pulse.extras.mediatools

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * A GIF89a writer: every frame gets its own palette (median cut), and with [optimize] a frame stores only the part that changed from the one before
 * (the rest is transparent), which is what makes an optimised GIF small. [colors] is 2..255 (one place is kept for the transparent colour).
 */
class GifEncoder(private val out: OutputStream, private val width: Int, private val height: Int, private val loops: Int = 0, private val colors: Int = 255, private val optimize: Boolean = true, private val dither: Boolean = false) {
    private var prev: IntArray? = null
    private var started = false

    private fun w16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }

    private fun header() {
        out.write("GIF89a".toByteArray())
        w16(width); w16(height)
        out.write(0x70); out.write(0); out.write(0)   // no global table, 8 bits of colour resolution
        if (loops >= 0) {
            out.write(0x21); out.write(0xFF); out.write(11); out.write("NETSCAPE2.0".toByteArray())
            out.write(3); out.write(1); w16(loops); out.write(0)
        }
        started = true
    }

    /** Adds [bmp] (it must be width x height) shown for [delayMs]. */
    fun add(bmp: Bitmap, delayMs: Int) {
        if (!started) header()
        val px = IntArray(width * height)
        bmp.getPixels(px, 0, width, 0, 0, width, height)
        // flatten on white: a frame is opaque unless it is a repeat of the one before
        for (i in px.indices) {
            val a = px[i] ushr 24
            if (a < 255) {
                val r = ((px[i] shr 16) and 255) * a / 255 + 255 - a; val g = ((px[i] shr 8) and 255) * a / 255 + 255 - a; val b = (px[i] and 255) * a / 255 + 255 - a
                px[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val p = prev
        var x0 = 0; var y0 = 0; var x1 = width - 1; var y1 = height - 1
        var transparent = false
        if (optimize && p != null) {
            x0 = width; y0 = height; x1 = -1; y1 = -1
            for (y in 0 until height) for (x in 0 until width) {
                val i = y * width + x
                if (close(px[i], p[i])) continue
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            if (x1 < 0) { x0 = 0; y0 = 0; x1 = 0; y1 = 0 }   // nothing changed: a one pixel frame keeps the time
            transparent = true
        }
        val fw = x1 - x0 + 1; val fh = y1 - y0 + 1
        val sub = IntArray(fw * fh)
        for (y in 0 until fh) System.arraycopy(px, (y0 + y) * width + x0, sub, y * fw, fw)
        val maxCol = if (transparent) minOf(colors, 255) else minOf(colors, 256)
        val pal = medianCut(sub, maxCol.coerceAtLeast(2))
        val tIndex = if (transparent) pal.size else -1
        val total = pal.size + (if (transparent) 1 else 0)
        var bits = 1; while ((1 shl bits) < total) bits++
        val idx = ByteArray(fw * fh)
        val cache = IntArray(32768) { -1 }
        val er = if (dither) IntArray(fw + 2) else IntArray(0); val eg = if (dither) IntArray(fw + 2) else IntArray(0); val eb = if (dither) IntArray(fw + 2) else IntArray(0)
        var nr = IntArray(fw + 2); var ng = IntArray(fw + 2); var nb = IntArray(fw + 2)
        var cr = er; var cg = eg; var cb = eb
        for (y in 0 until fh) {
            for (x in 0 until fw) {
                val c = sub[y * fw + x]
                val gx = x0 + x; val gy = y0 + y
                if (transparent && p != null && close(c, p[gy * width + gx])) { idx[y * fw + x] = tIndex.toByte(); continue }
                var r = (c shr 16) and 255; var g = (c shr 8) and 255; var b = c and 255
                if (dither) { r = (r + cr[x + 1] / 16).coerceIn(0, 255); g = (g + cg[x + 1] / 16).coerceIn(0, 255); b = (b + cb[x + 1] / 16).coerceIn(0, 255) }
                val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                var n = if (dither) -1 else cache[key]
                if (n < 0) { n = nearest(pal, r, g, b); if (!dither) cache[key] = n }
                idx[y * fw + x] = n.toByte()
                if (dither) {
                    val pr = pal[n]; val dr = r - ((pr shr 16) and 255); val dg = g - ((pr shr 8) and 255); val db = b - (pr and 255)
                    cr[x + 2] += dr * 7; cg[x + 2] += dg * 7; cb[x + 2] += db * 7
                    nr[x] += dr * 3; ng[x] += dg * 3; nb[x] += db * 3
                    nr[x + 1] += dr * 5; ng[x + 1] += dg * 5; nb[x + 1] += db * 5
                    nr[x + 2] += dr; ng[x + 2] += dg; nb[x + 2] += db
                }
            }
            if (dither) { cr = nr; cg = ng; cb = nb; nr = IntArray(fw + 2); ng = IntArray(fw + 2); nb = IntArray(fw + 2) }
        }
        // graphic control extension: the time, the transparent colour, and "leave the picture as it is" for the next frame
        out.write(0x21); out.write(0xF9); out.write(4)
        out.write((1 shl 2) or (if (transparent) 1 else 0)); w16((delayMs / 10).coerceAtLeast(2)); out.write(if (transparent) tIndex else 0); out.write(0)
        out.write(0x2C); w16(x0); w16(y0); w16(fw); w16(fh)
        out.write(0x80 or (bits - 1))
        for (i in 0 until (1 shl bits)) {
            val c = if (i < pal.size) pal[i] else 0
            out.write((c shr 16) and 255); out.write((c shr 8) and 255); out.write(c and 255)
        }
        lzw(idx, bits.coerceAtLeast(2))
        // what the screen shows after this frame, for the next comparison: the pixels written change, the transparent ones stay as they were
        if (!transparent || p == null) prev = px
        else for (y in 0 until fh) for (x in 0 until fw) if ((idx[y * fw + x].toInt() and 255) != tIndex) p[(y0 + y) * width + x0 + x] = sub[y * fw + x]
    }

    fun finish() { if (!started) header(); out.write(0x3B); out.flush() }

    /** Two colours that are the same to the eye, so the second need not be stored again. */
    private fun close(a: Int, b: Int): Boolean {
        val dr = ((a shr 16) and 255) - ((b shr 16) and 255); val dg = ((a shr 8) and 255) - ((b shr 8) and 255); val db = (a and 255) - (b and 255)
        return dr * dr + dg * dg + db * db <= 48
    }

    private fun nearest(pal: IntArray, r: Int, g: Int, b: Int): Int {
        var best = 0; var bd = Int.MAX_VALUE
        for (i in pal.indices) {
            val c = pal[i]
            val dr = r - ((c shr 16) and 255); val dg = g - ((c shr 8) and 255); val db = b - (c and 255)
            val d = dr * dr * 2 + dg * dg * 4 + db * db * 3
            if (d < bd) { bd = d; best = i; if (d == 0) break }
        }
        return best
    }

    /** Median cut over a sample of the picture: the box with the widest spread is split at its middle until there are [n] boxes. */
    private fun medianCut(px: IntArray, n: Int): IntArray {
        val step = maxOf(1, px.size / 20000)
        val sample = IntArray((px.size + step - 1) / step) { px[it * step] and 0xFFFFFF }
        class Box(var from: Int, var to: Int)
        val boxes = ArrayList<Box>().apply { add(Box(0, sample.size)) }
        fun spread(b: Box): Triple<Int, Int, Int> {
            var rmin = 255; var rmax = 0; var gmin = 255; var gmax = 0; var bmin = 255; var bmax = 0
            for (i in b.from until b.to) {
                val c = sample[i]; val r = c shr 16; val g = (c shr 8) and 255; val bl = c and 255
                if (r < rmin) rmin = r; if (r > rmax) rmax = r; if (g < gmin) gmin = g; if (g > gmax) gmax = g; if (bl < bmin) bmin = bl; if (bl > bmax) bmax = bl
            }
            return Triple(rmax - rmin, gmax - gmin, bmax - bmin)
        }
        while (boxes.size < n) {
            var pick = -1; var widest = 0
            for (i in boxes.indices) {
                val b = boxes[i]; if (b.to - b.from < 2) continue
                val (a, g, c) = spread(b); val m = maxOf(a, g, c)
                if (m > widest) { widest = m; pick = i }
            }
            if (pick < 0 || widest == 0) break
            val b = boxes[pick]
            val (a, g, c) = spread(b)
            val ch = if (a >= g && a >= c) 16 else if (g >= c) 8 else 0
            val part = sample.copyOfRange(b.from, b.to).sortedBy { (it shr ch) and 255 }
            for (i in part.indices) sample[b.from + i] = part[i]
            val mid = b.from + (b.to - b.from) / 2
            boxes[pick] = Box(b.from, mid); boxes.add(Box(mid, b.to))
        }
        return IntArray(boxes.size) { i ->
            val b = boxes[i]; var r = 0L; var g = 0L; var bl = 0L; val cnt = (b.to - b.from).coerceAtLeast(1)
            for (k in b.from until b.to) { val c = sample[k]; r += c shr 16; g += (c shr 8) and 255; bl += c and 255 }
            ((r / cnt).toInt() shl 16) or ((g / cnt).toInt() shl 8) or (bl / cnt).toInt()
        }
    }

    /** The GIF flavour of LZW: variable code size up to 12 bits, clear and end codes, sub-blocks of at most 255 bytes. */
    private fun lzw(data: ByteArray, minBits: Int) {
        out.write(minBits)
        val clear = 1 shl minBits; val eoi = clear + 1
        val buf = ByteArrayOutputStream()
        var acc = 0; var nacc = 0
        fun put(code: Int, size: Int) { acc = acc or (code shl nacc); nacc += size; while (nacc >= 8) { buf.write(acc and 255); acc = acc ushr 8; nacc -= 8 } }
        var size = minBits + 1; var next = eoi + 1
        val table = HashMap<Int, Int>()
        put(clear, size)
        var cur = data[0].toInt() and 255
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 255
            val key = (cur shl 8) or c
            val hit = table[key]
            if (hit != null) { cur = hit; continue }
            put(cur, size)
            if (next < 4096) {
                table[key] = next++
                if (next > (1 shl size) && size < 12) size++
            } else { put(clear, size); table.clear(); size = minBits + 1; next = eoi + 1 }
            cur = c
        }
        put(cur, size); put(eoi, size)
        if (nacc > 0) buf.write(acc and 255)
        val bytes = buf.toByteArray()
        var o = 0
        while (o < bytes.size) { val n = minOf(255, bytes.size - o); out.write(n); out.write(bytes, o, n); o += n }
        out.write(0)
    }
}
