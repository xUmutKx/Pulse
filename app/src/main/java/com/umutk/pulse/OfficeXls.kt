package com.umutk.pulse

import java.io.File
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes [parts] (path -> bytes) as a zip file. */
fun ofZipParts(out: File, parts: Map<String, ByteArray>) {
    ZipOutputStream(out.outputStream().buffered()).use { z -> for ((n, b) in parts) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
}

fun ofXmlEsc(s: String): String {
    val sb = StringBuilder(s.length + 8)
    for (c in s) {
        if (c == '&') sb.append("&amp;") else if (c == '<') sb.append("&lt;") else if (c == '>') sb.append("&gt;") else if (c == '"') sb.append("&quot;")
        else if ((c < ' ' && c != '\t' && c != '\n' && c != '\r') || c == '￾' || c == '￿') { } else sb.append(c)
    }
    return sb.toString()
}

/**
 * Legacy Excel 97-2003 (.xls, BIFF8) reader. It converts the workbook into a temporary .xlsx (cached values, styles, widths, merges)
 * that the normal Excel viewer opens; saving goes through the xlsx writer, so the original is never touched.
 * Not read: formulas (cached results only), pictures, charts, row heights, BIFF5 and older, encrypted files.
 */
object OfXls {
    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, o: Int): Long = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
    private fun dbl(b: ByteArray, o: Int) = java.lang.Double.longBitsToDouble(u32(b, o) or (u32(b, o + 4) shl 32))

    private val PAL = intArrayOf(0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080,
        0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF, 0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF,
        0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99, 0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
        0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333)

    private fun walk(b: ByteArray, from: Int, f: (Int, Int, Int) -> Boolean) {
        var p = from
        while (p + 4 <= b.size) {
            val n = u16(b, p + 2)
            if (!f(u16(b, p), p + 4, minOf(n, b.size - p - 4))) break
            p += 4 + n
        }
    }

    /** XLUnicodeString at [o0]; returns the text and the offset after it. */
    private fun ustr(b: ByteArray, o0: Int, end: Int, wide: Boolean): Pair<String, Int> {
        var o = o0
        val cch = if (wide) { o += 2; u16(b, o - 2) } else { o += 1; b[o - 1].toInt() and 255 }
        val fl = b[o++].toInt() and 255
        var runs = 0; var ext = 0
        if ((fl and 8) != 0) { runs = u16(b, o); o += 2 }
        if ((fl and 4) != 0) { ext = u32(b, o).toInt(); o += 4 }
        val n = minOf(if ((fl and 1) != 0) cch * 2 else cch, (end - o).coerceAtLeast(0))
        val s = if ((fl and 1) != 0) String(b, o, n, Charsets.UTF_16LE) else String(b, o, n, Charsets.ISO_8859_1)
        return s to o + n + runs * 4 + ext
    }

    /** Shared string table over its SST + CONTINUE chunks (a string may continue in the next chunk with a fresh flag byte). */
    private fun sst(ch: List<ByteArray>): List<String> {
        val out = ArrayList<String>()
        if (ch.isEmpty() || ch[0].size < 8) return out
        var ci = 0; var p = 8
        fun need(): Boolean { while (ci < ch.size && p >= ch[ci].size) { ci++; p = 0 }; return ci < ch.size }
        fun skip(n0: Int) { var n = n0; while (n > 0 && need()) { val k = minOf(n, ch[ci].size - p); p += k; n -= k } }
        val total = minOf(u32(ch[0], 4), 1_000_000L).toInt()
        while (out.size < total && need()) {
            val cch = if (ch[ci].size - p >= 3) { p += 2; u16(ch[ci], p - 2) } else break
            val fl = ch[ci][p++].toInt() and 255
            var runs = 0; var ext = 0
            if ((fl and 8) != 0) { runs = u16(ch[ci], p); p += 2 }
            if ((fl and 4) != 0) { ext = u32(ch[ci], p).toInt(); p += 4 }
            var wide = (fl and 1) != 0
            val sb = StringBuilder(); var left = cch
            while (left > 0) {
                if (p >= ch[ci].size) { ci++; p = 0; if (ci >= ch.size) break; wide = (ch[ci][p++].toInt() and 1) != 0; continue }
                val k = minOf(left, (ch[ci].size - p) / (if (wide) 2 else 1))
                if (k <= 0) { p = ch[ci].size; continue }
                sb.append(if (wide) String(ch[ci], p, k * 2, Charsets.UTF_16LE) else String(ch[ci], p, k, Charsets.ISO_8859_1))
                p += if (wide) k * 2 else k; left -= k
            }
            skip(runs * 4 + ext)
            out.add(sb.toString())
        }
        return out
    }

    private fun rk(v: Long): Double {
        val d = if ((v and 2L) != 0L) (v.toInt() shr 2).toDouble() else java.lang.Double.longBitsToDouble((v and 0xFFFFFFFCL) shl 32)
        return if ((v and 1L) != 0L) d / 100.0 else d
    }
    private fun numStr(d: Double): String? = if (d.isNaN() || d.isInfinite()) null else if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    private fun err(c: Int) = when (c) { 0 -> "#NULL!"; 7 -> "#DIV/0!"; 15 -> "#VALUE!"; 23 -> "#REF!"; 29 -> "#NAME?"; 36 -> "#NUM!"; else -> "#N/A" }

    private fun isDateFmt(id: Int, code: String?): Boolean {
        if (code == null) return id in 14..22 || id in 45..47
        val c = code.replace(Regex("\"[^\"]*\"|\\[[^\\]]*\\]|\\\\.|_."), "").lowercase()
        return c.any { it == 'y' || it == 'd' || it == 'h' || it == 's' || it == 'm' }
    }

    private class Fnt(val sz: Int, val bold: Boolean, val italic: Boolean, val under: Boolean, val color: Int, val name: String)
    private class Xf(val font: Int, val fmt: Int, val h: Int, val wrap: Boolean, val bord: IntArray, val fill: Int)
    private class Sh(val name: String) {
        val rows = TreeMap<Int, TreeMap<Int, String>>(); val cols = ArrayList<IntArray>(); val merges = ArrayList<IntArray>()
        var cells = 0
        fun put(r: Int, c: Int, x: String) { if (cells < 3_000_000 && r < 1048576 && c < 16384) { rows.getOrPut(r) { TreeMap() }[c] = x; cells++ } }
    }

    fun toXlsx(src: File): File {
        val out = File(src.path + ".xlsx")
        val by = src.readBytes()
        val wb = try { OfBinDoc.cfb(by).let { it("Workbook") ?: it("Book") } } catch (e: Throwable) { throw IllegalStateException("Cannot read this .xls file (damaged or unsupported container)") }
            ?: throw IllegalStateException("No Excel workbook stream found in this file")
        val pal = PAL.copyOf()
        fun color(i: Int): Int = if (i in 0 until 8) pal[i] else if (i in 8..63) pal[i - 8] else -1
        val fonts = ArrayList<Fnt>(); val fmts = TreeMap<Int, String>(); val xfs = ArrayList<Xf>()
        val bs = ArrayList<Pair<String, Long>>(); val chunks = ArrayList<ByteArray>()
        var inSst = false; var d1904 = false; var ver = 0
        walk(wb, 0) { id, o, n ->
            if (id != 0x3C) inSst = false
            try {
                when (id) {
                    0x809 -> { ver = u16(wb, o); if (ver < 0x600) throw IllegalStateException("Excel 5.0/95 (BIFF5) workbooks are not supported; save as .xlsx") }
                    0x2F -> throw IllegalStateException("This workbook is password protected")
                    0x22 -> d1904 = u16(wb, o) == 1
                    0x31 -> { val fl = u16(wb, o + 2); val cch = wb[o + 14].toInt() and 255; val wide = (wb[o + 15].toInt() and 1) != 0
                        fonts.add(Fnt(u16(wb, o) / 20, u16(wb, o + 6) >= 700 || (fl and 1) != 0, (fl and 2) != 0, wb[o + 10].toInt() != 0, u16(wb, o + 4),
                            String(wb, o + 16, minOf(if (wide) cch * 2 else cch, n - 16).coerceAtLeast(0), if (wide) Charsets.UTF_16LE else Charsets.ISO_8859_1))) }
                    0x41E -> fmts[u16(wb, o)] = ustr(wb, o + 2, o + n, true).first
                    0xE0 -> { val al = wb[o + 6].toInt() and 255; val bo = u16(wb, o + 10); val pat = (u32(wb, o + 14) shr 26).toInt() and 63
                        xfs.add(Xf(u16(wb, o), u16(wb, o + 2), al and 7, (al and 8) != 0, intArrayOf(bo and 15, (bo shr 4) and 15, (bo shr 8) and 15, (bo shr 12) and 15),
                            if (pat == 1) color(u16(wb, o + 18) and 127) else -1)) }
                    0x92 -> { val k = minOf(u16(wb, o), (n - 2) / 4, 56); for (i in 0 until k) pal[i + 8] = (wb[o + 2 + 4 * i].toInt() and 255 shl 16) or (wb[o + 3 + 4 * i].toInt() and 255 shl 8) or (wb[o + 4 + 4 * i].toInt() and 255) }
                    0x85 -> if ((wb[o + 5].toInt() and 255) == 0) bs.add(ustr(wb, o + 6, o + n, false).first to u32(wb, o)) // worksheets only
                    0xFC -> { inSst = true; chunks.add(wb.copyOfRange(o, o + n)) }
                    0x3C -> if (inSst) chunks.add(wb.copyOfRange(o, o + n))
                }
            } catch (e: IllegalStateException) { throw e } catch (e: Exception) { }
            id != 0x0A
        }
        if (bs.isEmpty()) throw IllegalStateException("This workbook has no worksheets")
        val strings = sst(chunks)
        if (xfs.isEmpty()) xfs.add(Xf(0, 0, 0, false, IntArray(4), -1))
        val dateXf = BooleanArray(xfs.size) { isDateFmt(xfs[it].fmt, fmts[xfs[it].fmt]) }

        val sheets = ArrayList<Sh>()
        for ((name, off) in bs.take(255)) {
            val sh = Sh(name); sheets.add(sh)
            if (off < 0 || off >= wb.size) continue
            var depth = 0; var pend = -1L // formula cell waiting for its STRING record: r * 65536 + c
            var pendXf = 0
            fun xs(xf: Int) = if (xf < xfs.size) xf else 0
            fun cell(r: Int, c: Int, xf: Int, t: String?, inner: String?) = sh.put(r, c, "<c r=\"${xRef(r, c)}\" s=\"${xs(xf)}\"" + (if (t != null) " t=\"$t\"" else "") + (if (inner == null) "/>" else ">$inner</c>"))
            fun num(r: Int, c: Int, xf: Int, d0: Double) {
                val d = if (d1904 && xs(xf) < dateXf.size && dateXf[xs(xf)]) d0 + 1462 else d0
                val s = numStr(d); if (s == null) cell(r, c, xf, null, null) else cell(r, c, xf, null, "<v>$s</v>")
            }
            fun str(r: Int, c: Int, xf: Int, s: String) = cell(r, c, xf, "inlineStr", "<is><t xml:space=\"preserve\">${ofXmlEsc(s)}</t></is>")
            walk(wb, off.toInt()) { id, o, n ->
                try {
                    if (id == 0x809) depth++ else if (id == 0x0A) depth--
                    if (depth == 1) when (id) {
                        0x27E -> num(u16(wb, o), u16(wb, o + 2), u16(wb, o + 4), rk(u32(wb, o + 6)))
                        0xBD -> for (k in 0 until (n - 6) / 6) num(u16(wb, o), u16(wb, o + 2) + k, u16(wb, o + 4 + 6 * k), rk(u32(wb, o + 6 + 6 * k)))
                        0x203 -> num(u16(wb, o), u16(wb, o + 2), u16(wb, o + 4), dbl(wb, o + 6))
                        0xFD -> str(u16(wb, o), u16(wb, o + 2), u16(wb, o + 4), strings.getOrElse(u32(wb, o + 6).toInt()) { "" })
                        0x204 -> str(u16(wb, o), u16(wb, o + 2), u16(wb, o + 4), ustr(wb, o + 6, o + n, true).first)
                        0x205 -> { val r = u16(wb, o); val c = u16(wb, o + 2); val xf = u16(wb, o + 4); val v = wb[o + 6].toInt() and 255
                            if (wb[o + 7].toInt() != 0) cell(r, c, xf, "e", err(v)) else cell(r, c, xf, "b", if (v != 0) "1" else "0") }
                        0x201 -> cell(u16(wb, o), u16(wb, o + 2), u16(wb, o + 4), null, null)
                        0xBE -> for (k in 0 until (n - 6) / 2) cell(u16(wb, o), u16(wb, o + 2) + k, u16(wb, o + 4 + 2 * k), null, null)
                        0x06 -> { val r = u16(wb, o); val c = u16(wb, o + 2); val xf = u16(wb, o + 4)
                            if (u16(wb, o + 12) == 0xFFFF) {
                                val v = wb[o + 8].toInt() and 255
                                when (wb[o + 6].toInt()) { 0 -> { pend = r * 65536L + c; pendXf = xf; cell(r, c, xf, "str", "<v></v>") }; 1 -> cell(r, c, xf, "b", if (v != 0) "1" else "0"); 2 -> cell(r, c, xf, "e", err(v)); else -> cell(r, c, xf, "str", "<v></v>") }
                            } else num(r, c, xf, dbl(wb, o + 6)) }
                        0x207 -> if (pend >= 0) { val r = (pend / 65536).toInt(); val c = (pend % 65536).toInt(); cell(r, c, pendXf, "str", "<v>${ofXmlEsc(ustr(wb, o, o + n, true).first)}</v>"); pend = -1 }
                        0x7D -> { val a = u16(wb, o); val z = minOf(u16(wb, o + 2), a + 255); val w = u16(wb, o + 4); if (w > 0 && a < 256) sh.cols.add(intArrayOf(a, z, w)) }
                        0xE5 -> for (k in 0 until minOf(u16(wb, o), (n - 2) / 8)) { val q = o + 2 + 8 * k; sh.merges.add(intArrayOf(u16(wb, q), u16(wb, q + 4), u16(wb, q + 2), u16(wb, q + 6))) }
                    }
                } catch (e: Exception) { }
                depth > 0
            }
        }
        writeXlsx(out, sheets, fonts, fmts, xfs, ::color)
        return out
    }

    private fun writeXlsx(out: File, sheets: List<Sh>, fonts: List<Fnt>, fmts: Map<Int, String>, xfs: List<Xf>, color: (Int) -> Int) {
        fun rgb(c: Int) = "FF%06X".format(c and 0xFFFFFF)
        val fx = StringBuilder(); var nf = 0
        for (f in fonts) { fx.append("<font>${if (f.bold) "<b/>" else ""}${if (f.italic) "<i/>" else ""}${if (f.under) "<u/>" else ""}<sz val=\"${f.sz.coerceIn(1, 400)}\"/>${color(f.color).let { if (it >= 0 && f.color != 64) "<color rgb=\"${rgb(it)}\"/>" else "" }}<name val=\"${ofXmlEsc(f.name.ifEmpty { "Calibri" })}\"/></font>"); nf++ }
        if (nf == 0) { fx.append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"); nf = 1 }
        val fills = LinkedHashMap<Int, Int>(); val fillX = StringBuilder()
        val bords = LinkedHashMap<List<Int>, Int>(); val bordX = StringBuilder("<border><left/><right/><top/><bottom/><diagonal/></border>"); bords[listOf(0, 0, 0, 0)] = 0
        fun bstyle(s: Int) = when (s) { 0 -> ""; 2 -> "medium"; 5 -> "thick"; 3 -> "dashed"; 4 -> "dotted"; 6 -> "double"; 7 -> "hair"; else -> "thin" }
        val xx = StringBuilder()
        for (x in xfs) {
            val fid = (if (x.font >= 4) x.font - 1 else x.font).let { if (it < nf) it else 0 }
            val flid = if (x.fill < 0) 0 else fills.getOrPut(x.fill) { fillX.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"${rgb(x.fill)}\"/><bgColor indexed=\"64\"/></patternFill></fill>"); fills.size + 2 }
            val bid = bords.getOrPut(x.bord.toList()) {
                fun sd(n: String, s: Int) = if (s == 0) "<$n/>" else "<$n style=\"${bstyle(s)}\"><color auto=\"1\"/></$n>"
                bordX.append("<border>" + sd("left", x.bord[0]) + sd("right", x.bord[1]) + sd("top", x.bord[2]) + sd("bottom", x.bord[3]) + "<diagonal/></border>"); bords.size
            }
            val al = when (x.h) { 1 -> "left"; 2 -> "center"; 3 -> "right"; 4 -> "fill"; 5 -> "justify"; 6 -> "centerContinuous"; else -> "" }
            xx.append("<xf numFmtId=\"${x.fmt}\" fontId=\"$fid\" fillId=\"$flid\" borderId=\"$bid\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"")
            if (al.isNotEmpty() || x.wrap) xx.append(" applyAlignment=\"1\"><alignment${if (al.isNotEmpty()) " horizontal=\"$al\"" else ""}${if (x.wrap) " wrapText=\"1\"" else ""}/></xf>") else xx.append("/>")
        }
        val nfx = fmts.entries.joinToString("") { "<numFmt numFmtId=\"${it.key}\" formatCode=\"${ofXmlEsc(it.value)}\"/>" }
        val styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"$SN\">" + (if (fmts.isEmpty()) "" else "<numFmts count=\"${fmts.size}\">$nfx</numFmts>") +
            "<fonts count=\"$nf\">$fx</fonts><fills count=\"${fills.size + 2}\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>$fillX</fills>" +
            "<borders count=\"${bords.size}\">$bordX</borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"${xfs.size}\">$xx</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"
        val parts = LinkedHashMap<String, ByteArray>()
        val H = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        val CT = "application/vnd.openxmlformats-officedocument.spreadsheetml."
        parts["[Content_Types].xml"] = (H + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"${CT}sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"${CT}styles+xml\"/>" +
            sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"${CT}worksheet+xml\"/>" } + "</Types>").toByteArray()
        parts["_rels/.rels"] = (H + "<Relationships xmlns=\"$RELNS\"><Relationship Id=\"rId1\" Type=\"$RO/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>").toByteArray()
        parts["xl/workbook.xml"] = (H + "<workbook xmlns=\"$SN\" xmlns:r=\"$RO\"><sheets>" + sheets.mapIndexed { i, s -> "<sheet name=\"${ofXmlEsc(s.name.take(31))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>").toByteArray()
        parts["xl/_rels/workbook.xml.rels"] = (H + "<Relationships xmlns=\"$RELNS\">" + sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$RO/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
            "<Relationship Id=\"rId${sheets.size + 1}\" Type=\"$RO/styles\" Target=\"styles.xml\"/></Relationships>").toByteArray()
        parts["xl/styles.xml"] = styles.toByteArray()
        sheets.forEachIndexed { i, s ->
            val sb = StringBuilder(H).append("<worksheet xmlns=\"$SN\">")
            if (s.cols.isNotEmpty()) { sb.append("<cols>"); for (c in s.cols.sortedBy { it[0] }) sb.append("<col min=\"${c[0] + 1}\" max=\"${c[1] + 1}\" width=\"${"%.2f".format(java.util.Locale.US, c[2] / 256f)}\" customWidth=\"1\"/>"); sb.append("</cols>") }
            sb.append("<sheetData>")
            for ((r, row) in s.rows) { sb.append("<row r=\"${r + 1}\">"); for (x in row.values) sb.append(x); sb.append("</row>") }
            sb.append("</sheetData>")
            if (s.merges.isNotEmpty()) sb.append("<mergeCells count=\"${s.merges.size}\">").also { s.merges.forEach { m -> sb.append("<mergeCell ref=\"${xRef(m[0], m[1])}:${xRef(m[2], m[3])}\"/>") } }.append("</mergeCells>")
            parts["xl/worksheets/sheet${i + 1}.xml"] = sb.append("</worksheet>").toString().toByteArray()
        }
        ofZipParts(out, parts)
    }

    private const val SN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val RO = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val RELNS = "http://schemas.openxmlformats.org/package/2006/relationships"
}
