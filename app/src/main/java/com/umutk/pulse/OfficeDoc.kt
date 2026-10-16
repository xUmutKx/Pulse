package com.umutk.pulse

/** Legacy binary Word 97-2003 (.doc, OLE2) reader: paragraphs and tables as plain text. Returns null when it is not a readable .doc. */
class DBlk(val para: String?, val rows: List<List<String>>?, val bounds: List<IntArray?>? = null)

object OfBinDoc {
    fun isDoc(b: ByteArray) = b.size > 512 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() && b[2] == 0x11.toByte() && b[3] == 0xE0.toByte()

    fun parse(bytes: ByteArray): List<DBlk>? = try { run(bytes) } catch (e: Throwable) { null }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, o: Int): Long = (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)

    fun cfb(b: ByteArray): (String) -> ByteArray? {
        val ss = 1 shl u16(b, 30); val ms = 1 shl u16(b, 32)
        val nfat = u32(b, 44).toInt(); val dirStart = u32(b, 48).toInt(); val cutoff = u32(b, 56); val mfs = u32(b, 60).toInt()
        var difN = u32(b, 72).toInt(); var dif = u32(b, 68).toInt()
        fun sec(i: Int): ByteArray { val o = 512 + i.toLong() * ss; if (i < 0 || o + ss > b.size) throw IllegalStateException(); return b.copyOfRange(o.toInt(), o.toInt() + ss) }
        val ids = ArrayList<Int>(); for (k in 0 until 109) ids.add(u32(b, 76 + 4 * k).toInt())
        while (difN-- > 0 && dif >= 0) { val s = sec(dif); for (k in 0 until ss / 4 - 1) ids.add(u32(s, 4 * k).toInt()); dif = u32(s, ss - 4).toInt() }
        val fat = ArrayList<Int>()
        for (k in 0 until minOf(nfat, ids.size)) { val s = sec(ids[k]); for (j in 0 until ss / 4) fat.add(u32(s, 4 * j).toInt()) }
        fun chain(s0: Int, tab: List<Int>): List<Int> { val out = ArrayList<Int>(); var s = s0; while (s >= 0 && s < tab.size && out.size <= tab.size) { out.add(s); s = tab[s] }; return out }
        fun rd(s: Int): ByteArray { val o = java.io.ByteArrayOutputStream(); chain(s, fat).forEach { o.write(sec(it)) }; return o.toByteArray() }
        val dir = rd(dirStart); val ents = HashMap<String, Pair<Int, Long>>()
        var o = 0
        while (o + 128 <= dir.size) {
            val nl = u16(dir, o + 64)
            if (nl >= 2) ents[String(dir, o, nl - 2, Charsets.UTF_16LE)] = u32(dir, o + 116).toInt() to u32(dir, o + 120)
            o += 128
        }
        val mini = ents["Root Entry"]?.let { rd(it.first) } ?: ByteArray(0)
        val mfat = ArrayList<Int>(); chain(mfs, fat).forEach { val s = sec(it); for (j in 0 until ss / 4) mfat.add(u32(s, 4 * j).toInt()) }
        fun stream(n: String): ByteArray? {
            val (s, sz) = ents[n] ?: return null
            return if (sz < cutoff) {
                val out = java.io.ByteArrayOutputStream()
                chain(s, mfat).forEach { out.write(mini, it * ms, ms) }
                out.toByteArray().copyOf(sz.toInt())
            } else rd(s).copyOf(sz.toInt())
        }
        return ::stream
    }

    /** PAPX flag bits: 1 = in a table, 2 = row-end mark */
    private var lastTd: IntArray? = null // column edges (twips) of the last sprmTDefTable seen by papFlags
    private fun papFlags(g: ByteArray): Int {
        lastTd = null
        var i = 2; var f = 0 // skip istd
        while (i + 2 <= g.size) {
            val op = u16(g, i); i += 2
            if (i >= g.size) break
            if (op == 0x2417 && g[i].toInt() != 0) f = f or 2
            if (op == 0x2416 && g[i].toInt() != 0) f = f or 1
            if (op == 0xD608 && i + 3 <= g.size) { val n = g[i + 2].toInt() and 255; if (i + 3 + 2 * (n + 1) <= g.size) lastTd = IntArray(n + 1) { k -> u16(g, i + 3 + 2 * k).toShort().toInt() } }
            when (op shr 13) {
                0, 1 -> i += 1; 2, 4, 5 -> i += 2; 3 -> i += 4; 7 -> i += 3
                6 -> i += if (op == 0xD608) u16(g, i) + 2 else (g[i].toInt() and 255) + 1
            }
        }
        return f
    }

    private fun run(bytes: ByteArray): List<DBlk>? {
        val c = cfb(bytes)
        val wd = c("WordDocument") ?: return null
        if (u16(wd, 0) != 0xA5EC) return null
        val tbl = c(if (u16(wd, 10) and 0x200 != 0) "1Table" else "0Table") ?: return null
        val ccp = u32(wd, 76).toInt(); val fcClx = u32(wd, 418).toInt(); val lcbClx = u32(wd, 422).toInt()
        var i = fcClx; val end = fcClx + lcbClx
        while (tbl[i].toInt() == 1) i += 3 + u16(tbl, i + 1)
        if (tbl[i].toInt() != 2) return null
        val n = (u32(tbl, i + 1).toInt() - 4) / 12; val p = i + 5; val pcd = p + 4 * (n + 1)
        if (pcd + 8 * n > end) return null
        // characters of the main story, with the file offset of each (for PAPX lookup)
        val chars = StringBuilder(); val fcs = ArrayList<Int>()
        for (k in 0 until n) {
            val a = u32(tbl, p + 4 * k).toInt(); if (a >= ccp) break
            val e = minOf(u32(tbl, p + 4 * k + 4).toInt(), ccp)
            val raw = u32(tbl, pcd + k * 8 + 2); val comp = raw and 0x40000000L != 0L; val fc = (raw and 0x3FFFFFFFL).toInt().let { if (comp) it / 2 else it }
            for (cp in a until e) {
                if (comp) { val o = fc + cp - a; chars.append(String(byteArrayOf(wd[o]), charset("windows-1252"))); fcs.add(o) }
                else { val o = fc + 2 * (cp - a); chars.append((u16(wd, o)).toChar()); fcs.add(o) }
            }
        }
        // PAPX runs: (start fc, end fc, flags), sorted
        val runs = ArrayList<IntArray>(); val tds = ArrayList<IntArray?>()
        val fcP = u32(wd, 258).toInt(); val lcbP = u32(wd, 262).toInt(); val nb = (lcbP - 4) / 8
        for (k in 0 until nb) {
            val pn = (u32(tbl, fcP + 4 * (nb + 1) + 4 * k).toInt() and 0x3FFFFF)
            val f = wd.copyOfRange(pn * 512, pn * 512 + 512); val cr = f[511].toInt() and 255
            for (r in 0 until cr) {
                val off = (f[4 * (cr + 1) + 13 * r].toInt() and 255) * 2; if (off == 0) continue
                val cb = f[off].toInt() and 255
                val g = if (cb == 0) f.copyOfRange(off + 2, off + 2 + 2 * (f[off + 1].toInt() and 255)) else f.copyOfRange(off + 1, off + 2 * cb)
                val pf = papFlags(g); if (pf != 0) { runs.add(intArrayOf(u32(f, 4 * r).toInt(), u32(f, 4 * r + 4).toInt(), pf, tds.size)); tds.add(lastTd) }
            }
        }
        runs.sortBy { it[0] }
        fun fr(fc: Int): IntArray? { var lo = 0; var hi = runs.size - 1; while (lo <= hi) { val m = (lo + hi) / 2; if (runs[m][0] <= fc) lo = m + 1 else hi = m - 1 }; return if (hi >= 0 && fc < runs[hi][1]) runs[hi] else null }
        fun fl(fc: Int): Int { var lo = 0; var hi = runs.size - 1; while (lo <= hi) { val m = (lo + hi) / 2; if (runs[m][0] <= fc) lo = m + 1 else hi = m - 1 }; return if (hi >= 0 && fc < runs[hi][1]) runs[hi][2] else 0 }

        val out = ArrayList<DBlk>(); var tb: ArrayList<List<String>>? = null; var tbd: ArrayList<IntArray?>? = null
        var row: ArrayList<String>? = null; val cur = StringBuilder(); val fld = ArrayList<Boolean>() // open fields; true while still in the field code
        fun para() { if (cur.isEmpty() && out.lastOrNull()?.para?.isEmpty() == true) return; out.add(DBlk(cur.toString(), null)); cur.setLength(0) }
        for (k in chars.indices) {
            val ch = chars[k]; val fc = fcs[k]
            when {
                ch == '\u0013' -> fld.add(true)
                ch == '\u0014' -> if (fld.isNotEmpty()) fld[fld.size - 1] = false
                ch == '\u0015' -> if (fld.isNotEmpty()) fld.removeAt(fld.size - 1)
                fld.contains(true) || ch == '\u0001' || ch == '\u000C' || ch == '\u0008' -> {}
                ch == '\u0007' -> if (fl(fc) and 2 != 0) {
                    if (tb == null) { tb = ArrayList(); tbd = ArrayList(); out.add(DBlk(null, tb, tbd)) }
                    tb.add(row ?: ArrayList()); tbd!!.add(fr(fc)?.let { tds[it[3]] }); row = null; cur.setLength(0)
                } else { if (row == null) row = ArrayList(); row.add(cur.toString().trim()); cur.setLength(0) }
                ch == '\r' -> if (fl(fc) != 0) cur.append('\n') else { tb = null; para() }
                ch == '\u000B' -> cur.append('\n')
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) para()
        return if (out.any { it.rows != null || !it.para.isNullOrBlank() }) out else null
    }
}
