package com.umutk.pulse

import java.io.File
import java.util.zip.ZipFile

/**
 * Legacy PowerPoint 97-2003 (.ppt) reader. Converts the deck into a temporary .pptx (slide size, text boxes with basic run/paragraph
 * style, shape boxes with fill/line, JPEG/PNG pictures) that the normal PowerPoint viewer opens; saving writes .pptx, never the original.
 * Not read: master/layout shapes, animations, notes, EMF/WMF pictures, tables as tables, gradients. Slides without readable shapes
 * fall back to a title + body layout of their texts.
 */
object OfPptBin {
    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, o: Int): Long = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
    private fun s32(b: ByteArray, o: Int) = u32(b, o).toInt()
    private fun s16(b: ByteArray, o: Int) = u16(b, o).toShort().toInt()
    private const val EMU = 1587.5 // EMU per master unit (576 per inch)

    private class Rc(val ver: Int, val inst: Int, val type: Int, val off: Int, val len: Int)
    private fun rcAt(b: ByteArray, o: Int): Rc { val v = u16(b, o); return Rc(v and 15, v shr 4, u16(b, o + 2), o + 8, minOf(u32(b, o + 4), (b.size - o - 8).toLong()).toInt().coerceAtLeast(0)) }
    private fun kids(b: ByteArray, from: Int, to: Int): List<Rc> {
        val out = ArrayList<Rc>(); var p = from
        while (p + 8 <= to && out.size < 20000) {
            val r = rcAt(b, p); val len = u32(b, p + 4)
            if (len < 0 || p + 8 + len > to) { out.add(Rc(r.ver, r.inst, r.type, r.off, to - p - 8)); break }
            out.add(r); p += 8 + len.toInt()
        }
        return out
    }
    private fun ch(b: ByteArray, r: Rc) = kids(b, r.off, r.off + r.len)

    private class Txt(val kind: Int, var s: String = "", var sty: ByteArray? = null, var ref: Int = -1)
    private class Rn(val text: String, val bold: Boolean, val italic: Boolean, val under: Boolean, val sz: Int, val color: Int)
    private class Para(val align: Int, val bullet: Boolean, val runs: List<Rn>)
    private class Sp(var x: Double, var y: Double, var w: Double, var h: Double, var prst: String = "rect", var fill: Int = -1, var line: Int = -1, var lw: Long = 9525,
                     var rot: Int = 0, var paras: List<Para>? = null, var pib: Int = 0, var anchor: String = "t", var kind: Int = -1, var placed: Boolean = true)

    private val DEF_SCHEME = intArrayOf(0xFFFFFF, 0x000000, 0x808080, 0x000000, 0x99CC00, 0x3333CC, 0x0099CC, 0xFF6600)

    private fun texts(b: ByteArray, rs: List<Rc>): List<Txt> {
        val out = ArrayList<Txt>(); var cur: Txt? = null
        fun t(): Txt { cur?.let { return it }; return Txt(4).also { out.add(it); cur = it } }
        for (r in rs) when (r.type) {
            3999 -> { cur = Txt(u32(b, r.off).toInt()).also { out.add(it) } }
            4000 -> t().s = String(b, r.off, r.len and 1.inv(), Charsets.UTF_16LE)
            4008 -> t().s = String(b, r.off, r.len, charset("windows-1252"))
            4001 -> t().sty = b.copyOfRange(r.off, r.off + r.len)
            3998 -> t().ref = u32(b, r.off).toInt()
        }
        return out
    }

    private fun paras(t: Txt, scheme: IntArray): List<Para> {
        val text = t.s.take(200_000); val n = text.length
        val title = t.kind == 0 || t.kind == 6; val body = t.kind == 1 || t.kind == 5 || t.kind == 7 || t.kind == 8
        val dsz = if (title) 32 else if (body) (if (n > 600) 14 else if (n > 300) 16 else 20) else 18
        val dcol = scheme[if (title) 3 else 1]
        val al = IntArray(n + 1) { -1 }; val bu = IntArray(n + 1) { -1 }
        val bo = BooleanArray(n + 1); val ia = BooleanArray(n + 1); val un = BooleanArray(n + 1); val sz = IntArray(n + 1) { dsz }; val co = IntArray(n + 1) { dcol }
        fun clr(b: ByteArray, p: Int): Int = if ((b[p + 3].toInt() and 255) == 0xFE) ((b[p].toInt() and 255) shl 16) or ((b[p + 1].toInt() and 255) shl 8) or (b[p + 2].toInt() and 255) else scheme[(b[p].toInt() and 255).coerceIn(0, 7)]
        t.sty?.let { b ->
            try {
                var p = 0; var pos = 0
                while (pos <= n && p + 10 <= b.size) {
                    val cnt = u32(b, p).toInt(); val m = u32(b, p + 6).toInt(); p += 10
                    var bf = -1; var a = -1
                    if ((m and 0xF) != 0) { bf = u16(b, p); p += 2 }
                    if ((m and 0x80) != 0) p += 2
                    if ((m and 0x10) != 0) p += 2
                    if ((m and 0x40) != 0) p += 2
                    if ((m and 0x20) != 0) p += 4
                    if ((m and 0x800) != 0) { a = u16(b, p); p += 2 }
                    if ((m and 0x1000) != 0) p += 2
                    if ((m and 0x2000) != 0) p += 2
                    if ((m and 0x4000) != 0) p += 2
                    if ((m and 0x100) != 0) p += 2
                    if ((m and 0x400) != 0) p += 2
                    if ((m and 0x8000) != 0) p += 2
                    if ((m and 0x100000) != 0) p += 2 + 4 * u16(b, p)
                    if ((m and 0x10000) != 0) p += 2
                    if ((m and 0xE0000) != 0) p += 2
                    if ((m and 0x200000) != 0) p += 2
                    for (i in pos until minOf(pos + cnt.coerceAtLeast(0), n + 1)) { al[i] = a; if (bf >= 0 && (m and 1) != 0) bu[i] = bf and 1 }
                    pos += cnt.coerceAtLeast(1)
                }
                pos = 0
                while (pos <= n && p + 8 <= b.size) {
                    val cnt = u32(b, p).toInt(); val m = u32(b, p + 4).toInt(); p += 8
                    var fs = 0; var s = -1; var c = -1
                    if ((m and 0x3FFF) != 0) { fs = u16(b, p); p += 2 }
                    if ((m and 0x10000) != 0) p += 2
                    if ((m and 0x200000) != 0) p += 2
                    if ((m and 0x400000) != 0) p += 2
                    if ((m and 0x800000) != 0) p += 2
                    if ((m and 0x20000) != 0) { s = u16(b, p); p += 2 }
                    if ((m and 0x40000) != 0) { c = clr(b, p); p += 4 }
                    if ((m and 0x80000) != 0) p += 2
                    for (i in pos until minOf(pos + cnt.coerceAtLeast(0), n + 1)) {
                        bo[i] = (m and 1) != 0 && (fs and 1) != 0; ia[i] = (m and 2) != 0 && (fs and 2) != 0; un[i] = (m and 4) != 0 && (fs and 4) != 0
                        if (s in 1..400) sz[i] = s; if (c >= 0) co[i] = c
                    }
                    pos += cnt.coerceAtLeast(1)
                }
            } catch (e: Exception) { }
        }
        val out = ArrayList<Para>(); var s = 0
        while (s <= n) {
            var e = s; while (e < n && text[e] != '\r') e++
            val runs = ArrayList<Rn>(); var a = s
            for (i in s..e) {
                if (i == e || (i > a && (bo[i] != bo[a] || ia[i] != ia[a] || un[i] != un[a] || sz[i] != sz[a] || co[i] != co[a]))) {
                    if (i > a) runs.add(Rn(text.substring(a, i).replace('\u000B', '\n'), bo[a], ia[a], un[a], sz[a], co[a]))
                    a = i
                }
            }
            out.add(Para(al[s], if (bu[s] >= 0) bu[s] == 1 else body, runs))
            s = e + 1
        }
        if (out.size > 1 && out.last().runs.isEmpty()) out.removeAt(out.size - 1)
        return out
    }

    private fun col(v: Long, scheme: IntArray): Int =
        if (((v shr 24) and 8L) != 0L) scheme[(v and 7L).toInt()] else ((v and 255L).toInt() shl 16) or (((v shr 8) and 255L).toInt() shl 8) or ((v shr 16) and 255L).toInt()

    private fun props(b: ByteArray, r: Rc): HashMap<Int, Long> {
        val m = HashMap<Int, Long>()
        for (i in 0 until minOf(r.inst, r.len / 6)) m[u16(b, r.off + 6 * i) and 0x3FFF] = u32(b, r.off + 6 * i + 2)
        return m
    }

    private fun anchorOf(b: ByteArray, ks: List<Rc>): DoubleArray? {
        for (k in ks) {
            if (k.type == 0xF010) return if (k.len >= 16) doubleArrayOf(s32(b, k.off + 4).toDouble(), s32(b, k.off).toDouble(), s32(b, k.off + 8).toDouble(), s32(b, k.off + 12).toDouble())
                else if (k.len >= 8) doubleArrayOf(s16(b, k.off + 2).toDouble(), s16(b, k.off).toDouble(), s16(b, k.off + 4).toDouble(), s16(b, k.off + 6).toDouble()) else null
            if (k.type == 0xF00F) return if (k.len >= 16) doubleArrayOf(s32(b, k.off).toDouble(), s32(b, k.off + 4).toDouble(), s32(b, k.off + 8).toDouble(), s32(b, k.off + 12).toDouble())
                else if (k.len >= 8) doubleArrayOf(s16(b, k.off).toDouble(), s16(b, k.off + 2).toDouble(), s16(b, k.off + 4).toDouble(), s16(b, k.off + 6).toDouble()) else null
        }
        return null // [left, top, right, bottom]
    }

    private val GEOM = mapOf(1 to "rect", 2 to "roundRect", 3 to "ellipse", 4 to "diamond", 5 to "triangle", 6 to "rtTriangle", 7 to "parallelogram", 8 to "trapezoid", 9 to "hexagon", 10 to "octagon", 11 to "plus", 13 to "rightArrow", 20 to "line", 32 to "line")

    /** Shapes below an Escher container; [xf] = sx, sy, dx, dy mapping child units to slide master units. */
    private fun shapes(b: ByteArray, rc: Rc, xf: DoubleArray, scheme: IntArray, stexts: List<Txt>, out: MutableList<Sp>, depth: Int) {
        if (depth > 8 || out.size > 2000) return
        val ks = ch(b, rc)
        var nx = xf
        if (rc.type == 0xF003) {
            val g = ks.firstOrNull { it.type == 0xF004 }
            if (g != null) {
                val gk = ch(b, g); val gr = gk.firstOrNull { it.type == 0xF009 }; val an = anchorOf(b, gk)
                if (gr != null && an != null && gr.len >= 16) {
                    val cl = s32(b, gr.off).toDouble(); val ct = s32(b, gr.off + 4).toDouble(); val cr = s32(b, gr.off + 8).toDouble(); val cb = s32(b, gr.off + 12).toDouble()
                    if (cr != cl && cb != ct) {
                        val x1 = an[0] * xf[0] + xf[2]; val x2 = an[2] * xf[0] + xf[2]; val y1 = an[1] * xf[1] + xf[3]; val y2 = an[3] * xf[1] + xf[3]
                        val sx = (x2 - x1) / (cr - cl); val sy = (y2 - y1) / (cb - ct)
                        nx = doubleArrayOf(sx, sy, x1 - cl * sx, y1 - ct * sy)
                    }
                }
            }
        }
        var first = true
        for (k in ks) when (k.type) {
            0xF003 -> shapes(b, k, nx, scheme, stexts, out, depth + 1)
            0xF004 -> if (rc.type == 0xF003 && first) first = false else shape(b, k, nx, scheme, stexts)?.let { out.add(it) }
        }
    }

    private fun shape(b: ByteArray, rc: Rc, xf: DoubleArray, scheme: IntArray, stexts: List<Txt>): Sp? {
        val ks = ch(b, rc)
        var flags = 0; var type = 0; var pr = HashMap<Int, Long>(); var tb: Rc? = null
        for (k in ks) when (k.type) {
            0xF00A -> { type = k.inst; flags = s32(b, k.off + 4) }
            0xF00B -> pr = props(b, k)
            0xF00D -> tb = k
        }
        if ((flags and (1 or 8 or 0x400)) != 0) return null
        val an = anchorOf(b, ks)
        var txt: Txt? = null
        tb?.let { t -> val l = texts(b, ch(b, t)); txt = l.firstOrNull { it.s.isNotEmpty() }?.takeIf { true } ?: l.firstOrNull { it.ref >= 0 }?.let { r -> stexts.getOrNull(r.ref)?.let { s -> Txt(s.kind, s.s, s.sty) } } }
        val ps = txt?.takeIf { it.s.isNotBlank() }?.let { paras(it, scheme) }
        val fillOff = pr[0x1BF]?.let { (it and 0x10L) == 0L && (it and 0x100000L) != 0L } ?: false
        val lineOff = pr[0x1FF]?.let { (it and 8L) == 0L && (it and 0x80000L) != 0L } ?: false
        val prst = GEOM[type] ?: "rect"
        val fill = if (fillOff) -1 else pr[0x181]?.let { col(it, scheme) } ?: -1
        var line = if (lineOff) -1 else pr[0x1C0]?.let { col(it, scheme) } ?: -1
        if (prst == "line" && line < 0 && !lineOff) line = 0
        val pib = (pr[0x104] ?: 0L).toInt()
        if (ps == null && fill < 0 && line < 0 && pib <= 0) return null
        val sp = Sp(0.0, 0.0, 0.0, 0.0, prst, fill, line, pr[0x1CB] ?: 9525L, (pr[4] ?: 0L).toInt() shr 16, ps, pib, when (pr[0x87]) { 1L -> "ctr"; 2L -> "b"; else -> "t" }, txt?.kind ?: -1)
        if (an == null) { sp.placed = false; return if (ps != null) sp else null }
        val x1 = an[0] * xf[0] + xf[2]; val x2 = an[2] * xf[0] + xf[2]; val y1 = an[1] * xf[1] + xf[3]; val y2 = an[3] * xf[1] + xf[3]
        sp.x = minOf(x1, x2); sp.y = minOf(y1, y2); sp.w = Math.abs(x2 - x1); sp.h = Math.abs(y2 - y1)
        return sp
    }

    /** JPEG / PNG bytes of a blip record at [o] (null for other formats). */
    private fun blip(a: ByteArray, o: Int): Pair<String, ByteArray>? {
        if (o < 0 || o + 8 > a.size) return null
        val inst = u16(a, o) shr 4; val type = u16(a, o + 2); val len = u32(a, o + 4).toInt()
        val two = when (type) { 0xF01D -> inst == 0x46B || inst == 0x6E3; 0xF01E -> inst == 0x6E1; else -> return null }
        val s = o + 8 + 16 * (if (two) 2 else 1) + 1; val e = minOf(o + 8 + len, a.size)
        return if (s < e) (if (type == 0xF01D) "jpg" else "png") to a.copyOfRange(s, e) else null
    }

    fun toPptx(src: File): File {
        val head = ByteArray(4); src.inputStream().use { it.read(head) }
        if (head[0] != 0xD0.toByte() || head[1] != 0xCF.toByte()) return src
        val by = src.readBytes()
        val c = try { OfBinDoc.cfb(by) } catch (e: Throwable) { throw IllegalStateException("Cannot read this .ppt file (damaged or unsupported container)") }
        val b = c("PowerPoint Document") ?: throw IllegalStateException("No PowerPoint stream found in this file")
        val pics: ByteArray? = try { c("Pictures") } catch (e: Throwable) { null }
        if (b.size < 16) throw IllegalStateException("Empty PowerPoint stream")
        // persist directory (newest edit wins) -> stream offsets
        val persist = HashMap<Int, Int>(); var docRef = -1
        try {
            c("Current User")?.takeIf { it.size >= 8 }?.let { cu ->
                var eo = u32(cu, 4).toInt(); var guard = 0
                while (eo > 0 && eo + 36 <= b.size && guard++ < 200 && u16(b, eo + 2) == 4085) {
                    val d = eo + 8
                    if (docRef < 0) docRef = s32(b, d + 16)
                    val pd = s32(b, d + 12)
                    if (pd > 0 && pd + 8 <= b.size && u16(b, pd + 2) == 6002) {
                        val end = minOf(pd + 8 + s32(b, pd + 4), b.size); var q = pd + 8
                        while (q + 4 <= end) {
                            val h = u32(b, q); val id = (h and 0xFFFFF).toInt(); val cnt = (h shr 20).toInt(); q += 4
                            for (i in 0 until cnt) { if (q + 4 > end) break; persist.putIfAbsent(id + i, s32(b, q)); q += 4 }
                        }
                    }
                    eo = s32(b, d + 8)
                }
            }
        } catch (e: Exception) { }
        val top = kids(b, 0, b.size)
        val doc = persist[docRef]?.takeIf { it > 0 && it + 8 <= b.size && u16(b, it + 2) == 1000 }?.let { rcAt(b, it) } ?: top.firstOrNull { it.type == 1000 } ?: throw IllegalStateException("No document record in this .ppt")
        val dk = ch(b, doc)
        var cx = 5760.0; var cy = 4320.0
        dk.firstOrNull { it.type == 1001 }?.let { if (it.len >= 8) { cx = u32(b, it.off).toDouble().coerceIn(576.0, 40000.0); cy = u32(b, it.off + 4).toDouble().coerceIn(576.0, 40000.0) } }
        // slide list: persist id, slide id, texts of that slide
        class Ent(val persist: Int, val sid: Int, val texts: List<Txt>)
        fun slwt(inst: Int): List<Ent> {
            val out = ArrayList<Ent>()
            for (l in dk.filter { it.type == 4080 && it.inst == inst }) {
                var cur: Pair<Int, Int>? = null; var acc = ArrayList<Rc>()
                fun flush() { cur?.let { out.add(Ent(it.first, it.second, texts(b, acc))) }; acc = ArrayList() }
                for (k in ch(b, l)) if (k.type == 1011 && k.len >= 16) { flush(); cur = s32(b, k.off) to s32(b, k.off + 12) } else acc.add(k)
                flush()
            }
            return out
        }
        val slides = slwt(0); val masters = slwt(1)
        // picture table
        val bses = ArrayList<Rc>()
        try { dk.firstOrNull { it.type == 1035 }?.let { g -> for (d in ch(b, g)) if (d.type == 0xF000) for (s in ch(b, d)) if (s.type == 0xF001) bses.addAll(ch(b, s).filter { it.type == 0xF007 }) } } catch (e: Exception) { }
        val media = LinkedHashMap<Int, Pair<String, ByteArray>?>()
        fun image(pib: Int): Pair<String, ByteArray>? = media.getOrPut(pib) {
            try {
                val r = bses.getOrNull(pib - 1) ?: return@getOrPut null
                val fo = u32(b, r.off + 28).toInt(); val nm = b[r.off + 33].toInt() and 255
                (if (pics != null && fo >= 0) blip(pics, fo) else null) ?: (if (r.len > 36 + nm) blip(b, r.off + 36 + nm) else null)
            } catch (e: Exception) { null }
        }

        data class Out(val xml: String, val imgs: List<Pair<String, ByteArray>>)
        fun scheme(rc: Rc?): IntArray? = rc?.let { r -> ch(b, r).firstOrNull { it.type == 2032 && it.len >= 32 }?.let { a -> IntArray(8) { i -> (b[a.off + 4 * i].toInt() and 255 shl 16) or (b[a.off + 4 * i + 1].toInt() and 255 shl 8) or (b[a.off + 4 * i + 2].toInt() and 255) } } }
        fun bgOf(rc: Rc?, sc: IntArray): Int = try {
            val pd = rc?.let { r -> ch(b, r).firstOrNull { it.type == 1036 } }
            val dg = pd?.let { ch(b, it).firstOrNull { k -> k.type == 0xF002 } }
            val bgSp = dg?.let { ch(b, it).firstOrNull { k -> k.type == 0xF004 } }
            val fo = bgSp?.let { ch(b, it).firstOrNull { k -> k.type == 0xF00B } }
            fo?.let { props(b, it)[0x181] }?.let { col(it, sc) } ?: -1
        } catch (e: Exception) { -1 }

        val sl = ArrayList<Out>()
        val srcSlides: List<Triple<Rc?, List<Txt>, Int>> =
            if (slides.isNotEmpty()) slides.map { e -> Triple(persist[e.persist]?.takeIf { it > 0 && it + 8 <= b.size && u16(b, it + 2) == 1006 }?.let { rcAt(b, it) }, e.texts, e.sid) }
            else top.filter { it.type == 1006 }.map { Triple<Rc?, List<Txt>, Int>(it, emptyList(), 0) }
        for ((srec, stexts, _) in srcSlides.take(500)) {
            val sps = ArrayList<Sp>(); var bg = -1
            try {
                val mrec = srec?.let { r -> ch(b, r).firstOrNull { it.type == 1007 && it.len >= 16 }?.let { a -> s32(b, a.off + 12) } }?.let { mid -> masters.firstOrNull { it.sid == mid } }?.let { m -> persist[m.persist]?.takeIf { it > 0 && it + 8 <= b.size }?.let { rcAt(b, it) } }
                val sc = scheme(srec) ?: scheme(mrec) ?: DEF_SCHEME
                bg = bgOf(srec, sc).let { if (it >= 0) it else bgOf(mrec, sc) }
                if (bg < 0) bg = sc[0]
                srec?.let { r -> ch(b, r).firstOrNull { it.type == 1036 }?.let { pd -> for (dg in ch(b, pd)) if (dg.type == 0xF002) for (g in ch(b, dg)) if (g.type == 0xF003) shapes(b, g, doubleArrayOf(1.0, 1.0, 0.0, 0.0), sc, stexts, sps, 0) } }
                // text shapes without a position and slides without any text shape fall back to a title / body layout
                val loose = sps.filter { !it.placed }; val hasText = sps.any { it.paras != null }
                val extra = if (!hasText && sps.none { it.pib > 0 }) stexts.filter { it.s.isNotBlank() && it.kind != 2 }.map { Sp(0.0, 0.0, 0.0, 0.0, paras = paras(it, sc), kind = it.kind, placed = false) } else loose
                sps.removeAll(loose.toSet())
                val ti = extra.filter { it.kind == 0 || it.kind == 6 }; val bd = extra.filter { it.kind != 0 && it.kind != 6 }
                ti.forEach { it.x = cx * .05; it.y = cy * .04; it.w = cx * .9; it.h = cy * .18; it.anchor = "ctr" }
                if (ti.size > 1) sps.addAll(ti.take(1)) else sps.addAll(ti)
                if (bd.isNotEmpty()) {
                    val body = Sp(cx * .05, if (ti.isEmpty()) cy * .05 else cy * .25, cx * .9, if (ti.isEmpty()) cy * .9 else cy * .68, paras = bd.flatMap { it.paras!! }, kind = 1)
                    sps.add(body)
                }
            } catch (e: Exception) { }
            if (bg < 0) bg = 0xFFFFFF
            val imgs = ArrayList<Pair<String, ByteArray>>(); val rid = HashMap<Int, String>()
            val x = StringBuilder("<p:sld xmlns:a=\"A\" xmlns:r=\"R\" xmlns:p=\"P\"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"%06X\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>".format(bg))
            var id = 2
            for (s in sps) {
                val xf = "<a:xfrm${if (s.rot != 0) " rot=\"${s.rot * 60000}\"" else ""}><a:off x=\"${(s.x * EMU).toLong()}\" y=\"${(s.y * EMU).toLong()}\"/><a:ext cx=\"${(s.w * EMU).toLong()}\" cy=\"${(s.h * EMU).toLong()}\"/></a:xfrm>"
                val geom = "<a:prstGeom prst=\"${s.prst}\"><a:avLst/></a:prstGeom>"
                val img = if (s.pib > 0) image(s.pib) else null
                if (img != null && s.paras == null) {
                    val r = rid.getOrPut(s.pib) { imgs.add(img); "rId${imgs.size + 1}" }
                    x.append("<p:pic><p:nvPicPr><p:cNvPr id=\"${id++}\" name=\"Picture\"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"$r\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>$xf$geom</p:spPr></p:pic>")
                    continue
                }
                val fill = if (s.fill >= 0) "<a:solidFill><a:srgbClr val=\"%06X\"/></a:solidFill>".format(s.fill) else "<a:noFill/>"
                val ln = if (s.line >= 0) "<a:ln w=\"${s.lw.coerceIn(0, 200000)}\"><a:solidFill><a:srgbClr val=\"%06X\"/></a:solidFill></a:ln>".format(s.line) else "<a:ln><a:noFill/></a:ln>"
                x.append("<p:sp><p:nvSpPr><p:cNvPr id=\"${id++}\" name=\"Shape\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>$xf$geom$fill$ln</p:spPr><p:txBody><a:bodyPr wrap=\"square\" anchor=\"${s.anchor}\"/><a:lstStyle/>")
                val ps = s.paras
                if (ps == null || ps.isEmpty()) x.append("<a:p/>") else for (p in ps) {
                    x.append("<a:p><a:pPr algn=\"${when (p.align) { 1 -> "ctr"; 2 -> "r"; 3 -> "just"; else -> "l" }}\"")
                    x.append(if (p.bullet) " marL=\"285750\" indent=\"-285750\"><a:buChar char=\"&#8226;\"/></a:pPr>" else "><a:buNone/></a:pPr>")
                    for (r in p.runs) r.text.split('\n').forEachIndexed { i, piece ->
                        if (i > 0) x.append("<a:br/>")
                        if (piece.isNotEmpty()) x.append("<a:r><a:rPr lang=\"en-US\" sz=\"${r.sz * 100}\"${if (r.bold) " b=\"1\"" else ""}${if (r.italic) " i=\"1\"" else ""}${if (r.under) " u=\"sng\"" else ""} dirty=\"0\"><a:solidFill><a:srgbClr val=\"%06X\"/></a:solidFill></a:rPr><a:t>${ofXmlEsc(piece)}</a:t></a:r>".format(r.color))
                    }
                    x.append("</a:p>")
                }
                x.append("</p:txBody></p:sp>")
            }
            x.append("</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>")
            sl.add(Out(x.toString(), imgs))
        }
        if (sl.isEmpty()) throw IllegalStateException("No slides found in this .ppt")
        return assemble(File(src.path + ".pptx"), sl.map { it.xml to it.imgs }, (cx * EMU).toLong(), (cy * EMU).toLong())
    }

    private fun assemble(out: File, sl: List<Pair<String, List<Pair<String, ByteArray>>>>, cx: Long, cy: Long): File {
        val base = File(out.path + ".base"); ofNewPptx(base)
        val parts = LinkedHashMap<String, ByteArray>()
        ZipFile(base).use { z -> for (e in z.entries()) if (!e.isDirectory && e.name != "ppt/slides/slide1.xml" && e.name != "ppt/slides/_rels/slide1.xml.rels") parts[e.name] = z.getInputStream(e).readBytes() }
        base.delete()
        val H = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        var ovr = ""; var ids = ""; var rels = ""; var media = 0; val exts = HashSet<String>()
        sl.forEachIndexed { i, (xml, imgs) ->
            val n = i + 1
            parts["ppt/slides/slide$n.xml"] = (H + xml.replace("xmlns:a=\"A\"", "xmlns:a=\"$NS_A\"").replace("xmlns:r=\"R\"", "xmlns:r=\"$rel\"").replace("xmlns:p=\"P\"", "xmlns:p=\"$NS_P\"")).toByteArray()
            var sr = "<Relationship Id=\"rId1\" Type=\"$rel/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>"
            imgs.forEachIndexed { k, (ext, data) -> media++; exts.add(ext); parts["ppt/media/image$media.$ext"] = data; sr += "<Relationship Id=\"rId${k + 2}\" Type=\"$rel/image\" Target=\"../media/image$media.$ext\"/>" }
            parts["ppt/slides/_rels/slide$n.xml.rels"] = "$H<Relationships xmlns=\"$NS_REL\">$sr</Relationships>".toByteArray()
            ovr += "<Override PartName=\"/ppt/slides/slide$n.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
            ids += "<p:sldId id=\"${255 + n}\" r:id=\"rId${100 + n}\"/>"
            rels += "<Relationship Id=\"rId${100 + n}\" Type=\"$rel/slide\" Target=\"slides/slide$n.xml\"/>"
        }
        val def = exts.joinToString("") { "<Default Extension=\"$it\" ContentType=\"${if (it == "jpg") "image/jpeg" else "image/png"}\"/>" }
        fun edit(n: String, f: (String) -> String) { parts[n] = f(String(parts[n]!!, Charsets.UTF_8)).toByteArray() }
        edit("[Content_Types].xml") { it.replace(Regex("<Override PartName=\"/ppt/slides/slide1.xml\"[^>]*/>"), "").replace("</Types>", "$def$ovr</Types>") }
        edit("ppt/presentation.xml") { it.replace(Regex("<p:sldIdLst>.*?</p:sldIdLst>"), "<p:sldIdLst>$ids</p:sldIdLst>").replace(Regex("<p:sldSz[^>]*/>"), "<p:sldSz cx=\"$cx\" cy=\"$cy\"/>") }
        edit("ppt/_rels/presentation.xml.rels") { it.replace(Regex("<Relationship Id=\"rId2\"[^>]*/>"), "").replace("</Relationships>", "$rels</Relationships>") }
        ofZipParts(out, parts)
        return out
    }
}
