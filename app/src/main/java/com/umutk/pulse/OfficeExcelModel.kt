package com.umutk.pulse

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Excel data model: xlsx / csv reader, number formats, tiny formula evaluator, xlsx writer (keeps every untouched zip part).

/** One cell format. [fontId] / [fillId] point at the original styles.xml entries, -1 once changed (written as a new one). */
data class XStyle(
    val bold: Boolean = false, val italic: Boolean = false, val fill: Int = 0, val color: Int = 0,
    val fmt: String = "General", val align: String = "", val border: Int = 0,
    val fontId: Int = 0, val fillId: Int = 0, val fsz: String = "11", val fname: String = "Calibri",
    val underline: Boolean = false, val wrap: Boolean = false,
    val strike: Boolean = false, val valign: String = "", val indent: Int = 0,
    val bm: Int = -1,   // own borders: bits 1 left, 2 right, 4 top, 8 bottom; -1 = the file's border [border]
)

class XCell(var s: String, var kind: Char = 's', var f: String? = null, var st: Int = 0)

fun xKey(r: Int, c: Int) = r.toLong() * 20000L + c
fun xRow(k: Long) = (k / 20000L).toInt()
fun xCol(k: Long) = (k % 20000L).toInt()

class XSheet(var name: String, val path: String?) {
    val cells = HashMap<Long, XCell>()
    val colW = HashMap<Int, Float>()
    val merges = ArrayList<IntArray>()   // r1, c1, r2, c2
    val tables = ArrayList<XTable>()
    val pics = ArrayList<XPic>()
    var drawingPath: String? = null      // existing xl/drawings part of this sheet
    var picsDirty = false
    var maxR = 0
    var maxC = 0
    var dirty = false
    var shifted = false                  // rows / columns inserted: widths and merges are rewritten
    fun put(r: Int, c: Int, x: XCell) { cells[xKey(r, c)] = x; if (r > maxR) maxR = r; if (c > maxC) maxC = c }
}

/** A picture on a sheet: two-cell anchor (col, colOff, row, rowOff in EMU), [anchor] = index of its twoCellAnchor in the file's drawing (-1 = new, then [bytes] + [ext] are embedded). */
class XPic(var c1: Int, var co1: Int, var r1: Int, var ro1: Int, var c2: Int, var co2: Int, var r2: Int, var ro2: Int,
           val bmp: Bitmap?, val anchor: Int, val bytes: ByteArray?, val ext: String) {
    var moved = false
    var deleted = false
}

fun xDecode(b: ByteArray, max: Int = 1600): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(b, 0, b.size, o)
    var s = 1; while (o.outWidth / s > max || o.outHeight / s > max) s *= 2
    BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
}.getOrNull()

/** New picture with its top-left corner in cell (r, c), [wPx] x [hPx] CSS pixels (9525 EMU each). */
fun xNewPic(sh: XSheet, bmp: Bitmap, bytes: ByteArray, ext: String, r: Int, c: Int, wPx: Float, hPx: Float): XPic {
    var rem = wPx * 9525f; var cc = c
    while (cc < 16383) { val w = ((sh.colW[cc] ?: 8.43f) * 7f + 5f) * 9525f; if (rem <= w) break; rem -= w; cc++ }
    val rh = 20f * 9525f; val h = hPx * 9525f
    return XPic(c, 0, r, 0, cc, rem.toInt(), r + (h / rh).toInt(), (h % rh).toInt(), bmp, -1, bytes, ext)
}

/** Colours of one table style: [head] 0 = no header fill, [band] / [band2] 0 = none, [line] outline / grid colour. */
class XTLook(val head: Int, val headInk: Int, val band: Int, val band2: Int, val line: Int, val grid: Boolean)

/** An Excel table (xl/tables/table*.xml): range, header row, style and stripes. */
class XTable(var r1: Int, var c1: Int, var r2: Int, var c2: Int, val style: String, val header: Boolean, val stripes: Boolean, val firstCol: Boolean, val filter: Boolean, val totals: Boolean) {
    val look = xTableLook(style)
    fun rowFill(r: Int): Int {
        if (header && r == r1) return look.head
        val i = r - r1 - (if (header) 1 else 0)
        return if (stripes && i % 2 == 0) look.band else look.band2
    }
}

private fun mixC(c: Int, to: Int, t: Float): Int {
    fun ch(sh: Int) = (((c shr sh) and 255) * (1 - t) + ((to shr sh) and 255) * t).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Built-in table styles by name (Light / Medium / Dark, 1..21): accent = (n-1) % 7, 0 = dark grey; unknown names look like Medium2. */
fun xTableLook(name: String): XTLook {
    val m = Regex("TableStyle(Light|Medium|Dark)(\\d+)").find(name)
    val fam = m?.groupValues?.get(1) ?: "Medium"; val n = m?.groupValues?.get(2)?.toIntOrNull() ?: 2
    val acc = (intArrayOf(0x404040, 0x4472C4, 0xED7D31, 0xA5A5A5, 0xFFC000, 0x5B9BD5, 0x70AD47)[(n - 1).coerceAtLeast(0) % 7]) or (0xFF shl 24)
    val g = (n - 1) / 7; val W = -1; val B = 0xFF000000.toInt()
    return when (fam) {
        "Light" -> when (g) { 0 -> XTLook(0, mixC(acc, B, .3f), mixC(acc, W, .85f), 0, acc, false); 1 -> XTLook(acc, W, 0, 0, acc, false); else -> XTLook(0, B, mixC(acc, W, .85f), 0, acc, true) }
        "Dark" -> XTLook(B, W, mixC(acc, W, .45f), mixC(acc, W, .7f), acc, false)
        else -> when (g) { 0 -> XTLook(acc, W, mixC(acc, W, .8f), 0, acc, false); 1 -> XTLook(acc, W, mixC(acc, W, .6f), mixC(acc, W, .8f), acc, false); else -> XTLook(mixC(acc, B, .25f), W, mixC(acc, W, .8f), mixC(acc, W, .9f), acc, false) }
    }
}

class XBook(val file: File, val csv: Boolean, val delim: Char = ',') {
    val sheets = ArrayList<XSheet>()
    val styles = ArrayList<XStyle>()
    var origStyles = 0
    var stylesPath: String? = null
    var fontCount = 1
    var fillCount = 2
    var borderCount = 1
    val fmts = HashMap<String, Int>()    // custom number formats already in the file: code -> id
    var shifted = false

    fun style(i: Int) = styles.getOrElse(i) { XStyle() }
    fun restyle(old: Int, f: (XStyle) -> XStyle): Int {
        val n = f(style(old)); val i = styles.indexOf(n)
        if (i >= 0) return i
        styles.add(n); return styles.size - 1
    }
}

fun xColName(c: Int): String { var n = c + 1; val sb = StringBuilder(); while (n > 0) { n--; sb.append('A' + n % 26); n /= 26 }; return sb.reverse().toString() }
fun xRef(r: Int, c: Int) = xColName(c) + (r + 1)
/** "B12" -> (11, 1) or null */
fun xParseRef(s: String): Pair<Int, Int>? {
    var i = 0; var c = 0
    while (i < s.length && (s[i].isLetter() || s[i] == '$')) { if (s[i] != '$') c = c * 26 + (s[i].uppercaseChar() - 'A' + 1); i++ }
    val r = s.substring(i).replace("$", "").toIntOrNull() ?: return null
    if (c == 0 || r < 1 || c > 16384) return null
    return (r - 1) to (c - 1)
}

private val BUILTIN = mapOf(0 to "General", 1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00", 9 to "0%", 10 to "0.00%",
    14 to "mm-dd-yy", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy", 18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss", 22 to "m/d/yy h:mm",
    37 to "#,##0 ;(#,##0)", 38 to "#,##0 ;[Red](#,##0)", 39 to "#,##0.00;(#,##0.00)", 40 to "#,##0.00;[Red](#,##0.00)", 45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mmss.0", 48 to "##0.0E+0", 49 to "@")
private val THEME = intArrayOf(0xFFFFFF, 0x000000, 0xE7E6E6, 0x44546A, 0x4472C4, 0xED7D31, 0xA5A5A5, 0xFFC000, 0x5B9BD5, 0x70AD47)

private fun nm(p: XmlPullParser) = (p.name ?: "").substringAfter(':')
private fun at(p: XmlPullParser, n: String): String? { for (i in 0 until p.attributeCount) if (p.getAttributeName(i).substringAfter(':') == n) return p.getAttributeValue(i); return null }
private fun parser(i: InputStream): XmlPullParser = Xml.newPullParser().also { it.setInput(i, null) }

private fun argb(rgb: String?, theme: String?, tint: String?): Int {
    var c = when {
        rgb != null -> (rgb.toLongOrNull(16) ?: return 0).toInt() or (0xFF shl 24)
        theme != null -> THEME.getOrNull(theme.toIntOrNull() ?: return 0)?.let { it or (0xFF shl 24) } ?: return 0
        else -> return 0
    }
    val t = tint?.toFloatOrNull() ?: return c
    fun ch(v: Int) = (if (t > 0) v + (255 - v) * t else v * (1 + t)).toInt().coerceIn(0, 255)
    c = (0xFF shl 24) or (ch((c shr 16) and 255) shl 16) or (ch((c shr 8) and 255) shl 8) or ch(c and 255)
    return c
}

// ------------------------------------------------------------------ reading

fun xLoad(f: File, name: String): XBook {
    val head = f.inputStream().use { i -> ByteArray(4).also { i.read(it) } }
    val zip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
    if (!zip) {
        if (head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte()) return xLoad(OfXls.toXlsx(f), name) // legacy .xls: converted to a temporary .xlsx; saving always writes xlsx
        val text = f.readText()
        val first = text.lineSequence().firstOrNull() ?: ""
        val d = if (name.endsWith(".tsv", true) || first.count { it == '\t' } > first.count { it == ',' }) '\t' else if (first.count { it == ';' } > first.count { it == ',' }) ';' else ','
        return XBook(f, true, d).also { b -> b.styles.add(XStyle()); b.sheets.add(csvSheet(text, d, name.substringBeforeLast('.'))) }
    }
    val b = XBook(f, false)
    ZipFile(f).use { z ->
        fun entry(p: String) = z.getEntry(p.removePrefix("/"))
        val rels = HashMap<String, String>()
        entry("xl/_rels/workbook.xml.rels")?.let { e ->
            val p = parser(z.getInputStream(e))
            while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && nm(p) == "Relationship") {
                val t = at(p, "Target") ?: continue
                rels[at(p, "Id") ?: continue] = if (t.startsWith("/")) t.removePrefix("/") else "xl/" + t
            }
        }
        val shared = ArrayList<String>()
        entry("xl/sharedStrings.xml")?.let { e ->
            val p = parser(z.getInputStream(e)); var sb: StringBuilder? = null; var inT = false; var ph = 0
            while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
                XmlPullParser.START_TAG -> when (nm(p)) { "si" -> sb = StringBuilder(); "rPh" -> ph++; "t" -> inT = ph == 0 }
                XmlPullParser.TEXT -> if (inT) sb?.append(p.text)
                XmlPullParser.END_TAG -> when (nm(p)) { "t" -> inT = false; "rPh" -> ph--; "si" -> { shared.add(sb.toString()); sb = null } }
            }
        }
        // styles
        b.stylesPath = rels.values.firstOrNull { it.endsWith("styles.xml") } ?: "xl/styles.xml"
        entry(b.stylesPath!!)?.let { e -> readStyles(b, z.getInputStream(e)) }
        if (b.styles.isEmpty()) b.styles.add(XStyle())
        b.origStyles = b.styles.size
        // sheets
        val wb = entry("xl/workbook.xml") ?: throw IllegalStateException("not an Excel workbook")
        val p = parser(z.getInputStream(wb))
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && nm(p) == "sheet") {
            val path = rels[at(p, "id") ?: ""]
            val sh = XSheet(at(p, "name") ?: "Sheet", path)
            if (path != null && entry(path) != null) {
                readSheet(sh, z.getInputStream(entry(path)), shared)
                runCatching { readTables(z, path, sh) }
                runCatching { readPics(z, path, sh) }
            }
            b.sheets.add(sh)
        }
        if (b.sheets.isEmpty()) throw IllegalStateException("no sheets")
    }
    return b
}

private fun readTables(z: ZipFile, path: String, sh: XSheet) {
    val rel = z.getEntry(path.substringBeforeLast('/', "") + "/_rels/" + path.substringAfterLast('/') + ".rels") ?: return
    val targets = ArrayList<String>()
    parser(z.getInputStream(rel)).let { p -> while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && nm(p) == "Relationship" && at(p, "Type")?.endsWith("/table") == true) at(p, "Target")?.let { targets.add(it) } }
    for (t in targets) {
        val e = z.getEntry(java.net.URI("/$path").resolve(t.replace(" ", "%20")).path.removePrefix("/")) ?: continue
        val p = parser(z.getInputStream(e))
        var ref: Pair<Pair<Int, Int>, Pair<Int, Int>>? = null; var hdr = true; var tot = false; var filter = false
        var style = ""; var stripes = false; var first = false
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG) when (nm(p)) {
            "table" -> { val q = (at(p, "ref") ?: "").split(':'); val a = xParseRef(q[0]); val b = xParseRef(q.getOrElse(1) { q[0] }); if (a != null && b != null) ref = a to b
                hdr = at(p, "headerRowCount") != "0"; tot = (at(p, "totalsRowCount")?.toIntOrNull() ?: 0) > 0 }
            "autoFilter" -> filter = true
            "tableStyleInfo" -> { style = at(p, "name") ?: ""; stripes = at(p, "showRowStripes") == "1" || at(p, "showRowStripes") == "true"; first = at(p, "showFirstColumn") == "1" }
        }
        val r = ref ?: continue
        sh.tables.add(XTable(r.first.first, r.first.second, r.second.first, r.second.second, style, hdr, stripes, first, filter && hdr, tot))
    }
}

private fun relsOf(p: String) = p.substringBeforeLast('/', "") + "/_rels/" + p.substringAfterLast('/') + ".rels"
private fun resolve(base: String, t: String) = if (t.startsWith("/")) t.removePrefix("/") else java.net.URI("/$base").resolve(t.replace(" ", "%20")).path.removePrefix("/")

/** Pictures of the sheet's drawing part (twoCellAnchor only; charts and shapes stay untouched in the file). */
private fun readPics(z: ZipFile, path: String, sh: XSheet) {
    val rel = z.getEntry(relsOf(path)) ?: return
    var dp: String? = null
    parser(z.getInputStream(rel)).let { p -> while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && nm(p) == "Relationship" && at(p, "Type")?.endsWith("/drawing") == true) at(p, "Target")?.let { dp = resolve(path, it) } }
    val d = dp ?: return
    sh.drawingPath = d
    val e = z.getEntry(d) ?: return
    val rels = HashMap<String, String>()
    z.getEntry(relsOf(d))?.let { re -> parser(z.getInputStream(re)).let { p -> while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && nm(p) == "Relationship") rels[at(p, "Id") ?: continue] = resolve(d, at(p, "Target") ?: continue) } }
    val p = parser(z.getInputStream(e))
    var idx = -1; var f = IntArray(4); var t = IntArray(4); var cur: IntArray? = null; var fi = -1; var emb: String? = null; var pic = false
    while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
        XmlPullParser.START_TAG -> when (nm(p)) {
            "twoCellAnchor" -> { idx++; f = IntArray(4); t = IntArray(4); emb = null; pic = false }
            "from" -> cur = f
            "to" -> cur = t
            "col" -> fi = 0
            "colOff" -> fi = 1
            "row" -> fi = 2
            "rowOff" -> fi = 3
            "pic" -> pic = true
            "blip" -> emb = at(p, "embed")
        }
        XmlPullParser.TEXT -> { val c = cur; if (fi >= 0 && c != null) c[fi] = p.text.trim().toIntOrNull() ?: 0 }
        XmlPullParser.END_TAG -> when (nm(p)) {
            "col", "colOff", "row", "rowOff" -> fi = -1
            "from", "to" -> cur = null
            "twoCellAnchor" -> if (pic && emb != null) {
                val bmp = rels[emb]?.let { z.getEntry(it) }?.let { me -> xDecode(z.getInputStream(me).readBytes()) }
                sh.pics.add(XPic(f[0], f[1], f[2], f[3], t[0], t[1], t[2], t[3], bmp, idx, null, ""))
            }
        }
    }
}

private fun readStyles(b: XBook, ins: InputStream) {
    val p = parser(ins)
    class Fnt(var strike: Boolean = false, var u: Boolean = false, var bold: Boolean = false, var italic: Boolean = false, var color: Int = 0, var sz: String = "11", var name: String = "Calibri")
    val fonts = ArrayList<Fnt>(); val fills = ArrayList<Int>(); val custom = HashMap<Int, String>()
    var sec = ""; var curFill = 0; var inFill = false; var nb = 0
    while (p.next() != XmlPullParser.END_DOCUMENT) {
        if (p.eventType == XmlPullParser.END_TAG) { if (nm(p) == "fill" && sec == "fills") { fills.add(curFill); inFill = false }; if (nm(p) in setOf("fonts", "fills", "cellXfs", "numFmts", "borders", "cellStyleXfs")) sec = ""; continue }
        if (p.eventType != XmlPullParser.START_TAG) continue
        when (val n = nm(p)) {
            "numFmts", "fonts", "fills", "cellXfs", "borders", "cellStyleXfs" -> sec = n
            "numFmt" -> { val id = at(p, "numFmtId")?.toIntOrNull(); val code = at(p, "formatCode"); if (id != null && code != null) { custom[id] = code; b.fmts[code] = id } }
            "font" -> if (sec == "fonts") fonts.add(Fnt())
            "border" -> if (sec == "borders") nb++
            "strike" -> if (sec == "fonts") fonts.lastOrNull()?.strike = at(p, "val") != "0"
            "u" -> if (sec == "fonts") fonts.lastOrNull()?.u = at(p, "val") != "none"
            "b" -> if (sec == "fonts" && at(p, "val") != "0") fonts.lastOrNull()?.bold = true
            "i" -> if (sec == "fonts" && at(p, "val") != "0") fonts.lastOrNull()?.italic = true
            "sz" -> if (sec == "fonts") fonts.lastOrNull()?.sz = at(p, "val") ?: "11"
            "name" -> if (sec == "fonts") fonts.lastOrNull()?.name = at(p, "val") ?: "Calibri"
            "color" -> if (sec == "fonts") fonts.lastOrNull()?.color = argb(at(p, "rgb"), at(p, "theme"), at(p, "tint")).let { if (at(p, "theme") == "1" && at(p, "rgb") == null && at(p, "tint") == null) 0 else it }
            "fill" -> if (sec == "fills") { curFill = 0; inFill = true }
            "patternFill" -> if (at(p, "patternType") != "solid") inFill = false
            "fgColor" -> if (inFill) curFill = argb(at(p, "rgb"), at(p, "theme"), at(p, "tint"))
            "xf" -> if (sec == "cellXfs") {
                val fid = at(p, "fontId")?.toIntOrNull() ?: 0; val fl = at(p, "fillId")?.toIntOrNull() ?: 0; val nf = at(p, "numFmtId")?.toIntOrNull() ?: 0
                val ft = fonts.getOrNull(fid) ?: Fnt()
                b.styles.add(XStyle(ft.bold, ft.italic, fills.getOrElse(fl) { 0 }, ft.color, custom[nf] ?: BUILTIN[nf] ?: "General", "", at(p, "borderId")?.toIntOrNull() ?: 0, fid, fl, ft.sz, ft.name, ft.u, strike = ft.strike))
            }
            "alignment" -> if (sec == "cellXfs" && b.styles.isNotEmpty()) b.styles[b.styles.size - 1] = b.styles.last().copy(align = at(p, "horizontal") ?: "", wrap = at(p, "wrapText").let { it == "1" || it == "true" }, valign = at(p, "vertical") ?: "", indent = at(p, "indent")?.toIntOrNull() ?: 0)
        }
    }
    b.borderCount = nb.coerceAtLeast(1); b.fontCount = fonts.size.coerceAtLeast(1); b.fillCount = fills.size.coerceAtLeast(2)
}

private fun readSheet(sh: XSheet, ins: InputStream, shared: List<String>) {
    val p = parser(ins)
    var r = -1; var c = -1; var cell: XCell? = null; var t = ""; var inV = false; var inF = false; var inT = false
    var v = StringBuilder(); var fs = StringBuilder(); var isT = StringBuilder(); var ph = 0; var fShared = false
    while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
        XmlPullParser.START_TAG -> when (nm(p)) {
            "row" -> { r = (at(p, "r")?.toIntOrNull() ?: (r + 2)) - 1; c = -1 }
            "col" -> { val a = (at(p, "min")?.toIntOrNull() ?: 1) - 1; val z = ((at(p, "max")?.toIntOrNull() ?: 1) - 1).coerceAtMost(a + 200); val w = at(p, "width")?.toFloatOrNull(); if (w != null) for (i in a..z) sh.colW[i] = w }
            "c" -> {
                val ref = at(p, "r")?.let { xParseRef(it) }
                if (ref != null) { r = ref.first; c = ref.second } else c++
                t = at(p, "t") ?: "n"; v = StringBuilder(); fs = StringBuilder(); isT = StringBuilder(); fShared = false
                cell = XCell("", 'n', null, at(p, "s")?.toIntOrNull() ?: 0)
            }
            "v" -> inV = true
            "f" -> { inF = true; fShared = at(p, "t") == "shared" }
            "rPh" -> ph++
            "t" -> inT = ph == 0
            "mergeCell" -> at(p, "ref")?.split(':')?.let { q -> val a = xParseRef(q[0]); val z = xParseRef(q.getOrElse(1) { q[0] }); if (a != null && z != null) sh.merges.add(intArrayOf(a.first, a.second, z.first, z.second)) }
        }
        XmlPullParser.TEXT -> { if (inV) v.append(p.text) else if (inF) fs.append(p.text) else if (inT) isT.append(p.text) }
        XmlPullParser.END_TAG -> when (nm(p)) {
            "v" -> inV = false; "f" -> inF = false; "t" -> inT = false; "rPh" -> ph--
            "c" -> cell?.let { x ->
                when (t) {
                    "s" -> { x.s = shared.getOrElse(v.toString().trim().toIntOrNull() ?: -1) { "" }; x.kind = 's' }
                    "str" -> { x.s = v.toString(); x.kind = 's' }
                    "inlineStr" -> { x.s = isT.toString(); x.kind = 's' }
                    "b" -> { x.s = if (v.toString().trim() == "1") "TRUE" else "FALSE"; x.kind = 'b' }
                    "e" -> { x.s = v.toString(); x.kind = 'e' }
                    else -> { x.s = v.toString().trim(); x.kind = 'n'; if (x.s.toDoubleOrNull() == null) { x.kind = 's' } }
                }
                if (fs.isNotEmpty()) x.f = fs.toString()
                if (x.s.isNotEmpty() || x.f != null || x.st != 0) { if (r in 0..1048575 && c in 0..16383) sh.put(r, c, x) }
                cell = null
            }
        }
    }
}

private fun csvSheet(text: String, d: Char, name: String): XSheet {
    val sh = XSheet(name.ifBlank { "Sheet1" }, null)
    var r = 0; var c = 0; val sb = StringBuilder(); var q = false; var i = 0; var any = false
    fun endCell() { val s = sb.toString(); sb.setLength(0); if (s.isNotEmpty()) sh.put(r, c, xTyped(s, 0).first); c++ }
    while (i < text.length) {
        val ch = text[i]
        if (q) { if (ch == '"') { if (text.getOrNull(i + 1) == '"') { sb.append('"'); i++ } else q = false } else sb.append(ch) }
        else when (ch) {
            '"' -> q = true
            d -> { endCell(); any = true }
            '\n' -> { endCell(); r++; c = 0; any = false }
            '\r' -> {}
            else -> { sb.append(ch); any = true }
        }
        i++
    }
    if (any || sb.isNotEmpty()) endCell()
    return sh
}

/** What the user typed -> a cell: formula, number or text. */
fun xTyped(t: String, st: Int): Pair<XCell, Boolean> {
    if (t.startsWith("=") && t.length > 1) return XCell("", 'n', t.substring(1), st) to false
    val n = t.trim().toDoubleOrNull()
    if (n != null && !n.isNaN() && !n.isInfinite() && !t.trim().endsWith("d", true) && !t.trim().endsWith("f", true)) return XCell(t.trim(), 'n', null, st) to false
    if (t.trim().endsWith("%")) t.trim().dropLast(1).toDoubleOrNull()?.let { return XCell((it / 100).toString(), 'n', null, st) to true }
    return XCell(t, 's', null, st) to false
}

// ------------------------------------------------------------------ number formats

fun xGeneral(v: Double): String {
    if (v == Math.rint(v) && Math.abs(v) < 1e11) return v.toLong().toString()
    if (Math.abs(v) >= 1e11 || (Math.abs(v) < 1e-5 && v != 0.0)) return "%.5E".format(v)
    return java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()
}

private val MONTHS = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

fun xIsDate(code: String): Boolean {
    val c = code.substringBefore(';').replace(Regex("\"[^\"]*\"|\\[[^\\]]*\\]|\\\\."), "").lowercase()
    return c.contains('y') || c.contains('d') || c.contains('h') || c.contains("s") && c.contains('m') || c.contains("mmm")
}

fun xFormat(code: String, v: Double): String {
    if (code == "General" || code.isBlank() || code == "@") return xGeneral(v)
    val sections = code.split(';')
    var c = if (v < 0 && sections.size > 1) sections[1] else sections[0]
    val neg = v < 0 && sections.size > 1
    if (xIsDate(c)) return try { xDate(c, v) } catch (e: Exception) { xGeneral(v) }
    var x = Math.abs(v)
    c = c.replace(Regex("\\[\\$([^\\-\\]]*)[^\\]]*\\]"), "$1").replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("_."), "").replace(Regex("\\*."), "").replace("\\", "")
    if (c.contains('%')) x *= 100
    val num = Regex("[#0?,.]+").find(c)
    val pre = c.substring(0, num?.range?.first ?: c.length).replace("\"", ""); val suf = if (num == null) "" else c.substring(num.range.last + 1).replace("\"", "")
    val pat = num?.value ?: "0"
    val dec = if (pat.contains('.')) pat.substringAfter('.').count { it == '0' || it == '#' } else 0
    val minDec = if (pat.contains('.')) pat.substringAfter('.').count { it == '0' } else 0
    var ip = java.math.BigDecimal(x).setScale(dec, java.math.RoundingMode.HALF_UP).toPlainString().substringBefore('.')
    var fp = java.math.BigDecimal(x).setScale(dec, java.math.RoundingMode.HALF_UP).toPlainString().substringAfter('.', "")
    while (fp.length > minDec && fp.endsWith("0")) fp = fp.dropLast(1)
    if (pat.contains(',') && ip.length < 19) ip = "%,d".format(ip.toLong())
    return (if (v < 0 && !neg) "-" else "") + pre + ip + (if (fp.isNotEmpty()) ".$fp" else "") + suf
}

// ------------------------------------------------------------------ formulas

class XErr(msg: String) : Exception(msg)

/** Evaluates cells of one sheet (SUM AVERAGE MIN MAX COUNT ABS ROUND, + - * / ^, ranges); results are cached until the sheet changes. */
class XEval(val sh: XSheet) {
    private val cache = HashMap<Long, Any>()
    private val busy = HashSet<Long>()

    /** Double, a String (text / error), or null for an empty cell. */
    fun value(r: Int, c: Int): Any? {
        val k = xKey(r, c); val x = sh.cells[k] ?: return null
        val f = x.f
        if (f == null) return when (x.kind) { 'n' -> x.s.toDoubleOrNull() ?: x.s; else -> x.s }
        cache[k]?.let { return it }
        if (!busy.add(k)) return "#REF!"
        val res: Any = try { P(f).run { val d = expr(); if (i < s.length) throw IllegalStateException(); d } }
        catch (e: XErr) { e.message ?: "#VALUE!" }
        catch (e: Exception) { if (x.kind == 'n') x.s.toDoubleOrNull() ?: x.s else x.s }
        busy.remove(k); cache[k] = res
        return res
    }

    private inner class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i] == ' ') i++ }
        fun expr(): Double { var v = term(); while (true) { ws(); if (i < s.length && (s[i] == '+' || s[i] == '-')) { val o = s[i++]; val t = term(); v = if (o == '+') v + t else v - t } else return v } }
        fun term(): Double { var v = pow(); while (true) { ws(); if (i < s.length && (s[i] == '*' || s[i] == '/')) { val o = s[i++]; val t = pow(); if (o == '*') v *= t else { if (t == 0.0) throw XErr("#DIV/0!"); v /= t } } else return v } }
        fun pow(): Double { val b = unary(); ws(); if (i < s.length && s[i] == '^') { i++; return Math.pow(b, pow()) }; return b }
        fun unary(): Double { ws(); if (i < s.length && s[i] == '-') { i++; return -unary() }; if (i < s.length && s[i] == '+') { i++; return unary() }; return atom() }
        fun num(v: Any?): Double = when (v) { null -> 0.0; is Double -> v; is String -> if (v.startsWith("#")) throw XErr(v) else v.toDoubleOrNull() ?: throw XErr("#VALUE!"); else -> 0.0 }
        fun atom(): Double {
            ws(); if (i >= s.length) throw IllegalStateException()
            if (s[i] == '(') { i++; val v = expr(); ws(); if (i >= s.length || s[i] != ')') throw IllegalStateException(); i++; return v }
            if (s[i].isDigit() || s[i] == '.') { val a = i; while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++; if (i < s.length && (s[i] == 'E' || s[i] == 'e') && i + 1 < s.length && (s[i + 1].isDigit() || s[i + 1] == '-' || s[i + 1] == '+')) { i += 2; while (i < s.length && s[i].isDigit()) i++ }; var v = s.substring(a, i).toDouble(); if (i < s.length && s[i] == '%') { i++; v /= 100 }; return v }
            val a = i; while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '$' || s[i] == '_' || s[i] == '.')) i++
            val id = s.substring(a, i); if (id.isEmpty()) throw IllegalStateException()
            ws()
            if (i < s.length && s[i] == '(') { i++; return call(id.uppercase()) }
            val ref = xParseRef(id) ?: throw IllegalStateException()
            return num(value(ref.first, ref.second))
        }
        fun call(fn: String): Double {
            val nums = ArrayList<Double>(); var count = 0
            ws()
            if (i < s.length && s[i] == ')') i++ else while (true) {
                ws(); val save = i
                // a range A1:B3 contributes every numeric cell
                val m = Regex("\\$?[A-Za-z]{1,3}\\$?\\d+:\\$?[A-Za-z]{1,3}\\$?\\d+").matchAt(s, i)
                if (m != null) {
                    val (a, z) = m.value.split(':').map { xParseRef(it) ?: throw IllegalStateException() }
                    i = m.range.last + 1
                    val r1 = minOf(a.first, z.first); val r2 = maxOf(a.first, z.first); val c1 = minOf(a.second, z.second); val c2 = maxOf(a.second, z.second)
                    if ((r2 - r1 + 1).toLong() * (c2 - c1 + 1) > sh.cells.size) { for ((k, _) in sh.cells) { val r = xRow(k); val c = xCol(k); if (r in r1..r2 && c in c1..c2) value(r, c).let { if (it is Double) { nums.add(it); count++ } else if (it is String && it.startsWith("#")) throw XErr(it) } } }
                    else for (r in r1..r2) for (c in c1..c2) value(r, c).let { if (it is Double) { nums.add(it); count++ } else if (it is String && it.startsWith("#")) throw XErr(it) }
                } else { i = save; nums.add(expr()); count++ }
                ws()
                if (i < s.length && (s[i] == ',' || s[i] == ';')) { i++; continue }
                if (i < s.length && s[i] == ')') { i++; break }
                throw IllegalStateException()
            }
            return when (fn) {
                "SUM" -> nums.sum()
                "AVERAGE" -> if (nums.isEmpty()) throw XErr("#DIV/0!") else nums.average()
                "MIN" -> nums.minOrNull() ?: 0.0
                "MAX" -> nums.maxOrNull() ?: 0.0
                "COUNT" -> count.toDouble()
                "ABS" -> Math.abs(nums.firstOrNull() ?: 0.0)
                "ROUND" -> java.math.BigDecimal(nums[0]).setScale((nums.getOrElse(1) { 0.0 }).toInt(), java.math.RoundingMode.HALF_UP).toDouble()
                else -> throw IllegalStateException()   // unsupported function: the cached file value is shown
            }
        }
    }
}

fun xShiftRefs(f: String, rowAt: Int, dr: Int, colAt: Int, dc: Int): String =
    Regex("(?<![A-Za-z0-9_])(\\$?)([A-Z]{1,3})(\\$?)(\\d+)(?![\\w(])").replace(f) { m ->
        val col = xParseRef(m.groupValues[2] + "1")!!.second; val row = m.groupValues[4].toInt() - 1
        val nc = if (dc != 0 && col >= colAt) col + dc else col; val nr = if (dr != 0 && row >= rowAt) row + dr else row
        m.groupValues[1] + xColName(nc) + m.groupValues[3] + (nr + 1)
    }

fun xInsert(b: XBook, sh: XSheet, row: Boolean, at: Int) {
    val old = HashMap(sh.cells); sh.cells.clear()
    val dr = if (row) 1 else 0; val dc = 1 - dr
    for ((k, x) in old) {
        var r = xRow(k); var c = xCol(k)
        if (row && r >= at) r++ else if (!row && c >= at) c++
        x.f = x.f?.let { xShiftRefs(it, if (row) at else 0, dr, if (row) 0 else at, dc) }
        sh.cells[xKey(r, c)] = x
    }
    if (row) sh.maxR++ else sh.maxC++
    for (t in sh.tables) { if (row) { if (t.r1 >= at) t.r1++; if (t.r2 >= at) t.r2++ } else { if (t.c1 >= at) t.c1++; if (t.c2 >= at) t.c2++ } }
    for (m in sh.merges) { if (row) { if (m[0] >= at) m[0]++; if (m[2] >= at) m[2]++ } else { if (m[1] >= at) m[1]++; if (m[3] >= at) m[3]++ } }
    if (!row) { val w = HashMap(sh.colW); sh.colW.clear(); for ((c, v) in w) sh.colW[if (c >= at) c + 1 else c] = v }
    shiftPics(sh, row, at, 1)
    sh.shifted = true; sh.dirty = true
}

/** Pictures follow inserted (n > 0) or deleted (n < 0) rows / columns. */
private fun shiftPics(sh: XSheet, row: Boolean, at: Int, n: Int) {
    for (p in sh.pics) {
        val a = if (row) p.r1 else p.c1; val z = if (row) p.r2 else p.c2
        val na = if (a >= at) maxOf(0, a + n) else a; val nz = if (z >= at) maxOf(0, z + n) else z
        if (na == a && nz == z) continue
        if (row) { p.r1 = na; p.r2 = nz } else { p.c1 = na; p.c2 = nz }
        p.moved = true; sh.picsDirty = true
    }
}

/** Deletes [n] rows / columns from [at] (cells, merges, tables, widths shift; references into the deleted band are not repaired). */
fun xDelete(sh: XSheet, row: Boolean, at: Int, n: Int) {
    val old = HashMap(sh.cells); sh.cells.clear()
    for ((k, x) in old) {
        var r = xRow(k); var c = xCol(k); val v = if (row) r else c
        if (v in at until at + n) continue
        if (v >= at + n) { if (row) r -= n else c -= n }
        x.f = x.f?.let { xShiftRefs(it, if (row) at + n else 0, if (row) -n else 0, if (row) 0 else at + n, if (row) 0 else -n) }
        sh.cells[xKey(r, c)] = x
    }
    sh.maxR = sh.cells.keys.maxOfOrNull { xRow(it) } ?: 0; sh.maxC = sh.cells.keys.maxOfOrNull { xCol(it) } ?: 0
    fun sh1(v: Int) = if (v >= at + n) v - n else v
    val i0 = if (row) 0 else 1; val i1 = i0 + 2
    sh.merges.removeAll { it[i0] <= at + n - 1 && it[i1] >= at }
    for (m in sh.merges) { m[i0] = sh1(m[i0]); m[i1] = sh1(m[i1]) }
    sh.tables.removeAll { (if (row) it.r1 else it.c1) <= at + n - 1 && (if (row) it.r2 else it.c2) >= at }
    for (t in sh.tables) { if (row) { t.r1 = sh1(t.r1); t.r2 = sh1(t.r2) } else { t.c1 = sh1(t.c1); t.c2 = sh1(t.c2) } }
    if (!row) { val w = HashMap(sh.colW); sh.colW.clear(); for ((c, v) in w) if (c !in at until at + n) sh.colW[sh1(c)] = v }
    shiftPics(sh, row, at + n, -n)
    sh.shifted = true; sh.dirty = true
}

// ------------------------------------------------------------------ writing

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
private fun attrEsc(s: String) = esc(s).replace("\"", "&quot;")

fun xCsvText(b: XBook, sh: XSheet): String {
    val ev = XEval(sh); val sb = StringBuilder()
    for (r in 0..sh.maxR) {
        for (c in 0..sh.maxC) {
            if (c > 0) sb.append(b.delim)
            val v = ev.value(r, c); val t = when (v) { null -> ""; is Double -> xGeneral(v); else -> v.toString() }
            if (t.any { it == b.delim || it == '"' || it == '\n' || it == '\r' }) sb.append('"').append(t.replace("\"", "\"\"")).append('"') else sb.append(t)
        }
        sb.append('\n')
    }
    return sb.toString()
}

private fun sheetData(sh: XSheet): String {
    val ev = XEval(sh); val sb = StringBuilder("<sheetData>")
    val byRow = sh.cells.entries.groupBy { xRow(it.key) }.toSortedMap()
    for ((r, es) in byRow) {
        sb.append("<row r=\"").append(r + 1).append("\">")
        for (e in es.sortedBy { xCol(it.key) }) {
            val x = e.value; val ref = xRef(r, xCol(e.key)); val s = if (x.st != 0) " s=\"${x.st}\"" else ""
            if (x.f != null) {
                val v = ev.value(r, xCol(e.key))
                val fx = "<f>${esc(x.f!!)}</f>"
                when (v) {
                    is Double -> sb.append("<c r=\"$ref\"$s>$fx<v>${v}</v></c>")
                    is String -> sb.append("<c r=\"$ref\"$s t=\"${if (v.startsWith("#")) "e" else "str"}\">$fx<v>${esc(v)}</v></c>")
                    else -> sb.append("<c r=\"$ref\"$s>$fx</c>")
                }
            } else if (x.s.isEmpty()) sb.append("<c r=\"$ref\"$s/>")
            else when (x.kind) {
                'n' -> sb.append("<c r=\"$ref\"$s><v>${esc(x.s)}</v></c>")
                'b' -> sb.append("<c r=\"$ref\"$s t=\"b\"><v>${if (x.s == "TRUE") 1 else 0}</v></c>")
                'e' -> sb.append("<c r=\"$ref\"$s t=\"e\"><v>${esc(x.s)}</v></c>")
                else -> sb.append("<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(x.s)}</t></is></c>")
            }
        }
        sb.append("</row>")
    }
    return sb.append("</sheetData>").toString()
}

private fun bump(xml: String, tag: String, add: Int, ins: String): String {
    var x = xml
    val m = Regex("<$tag\\b[^>]*?count=\"(\\d+)\"").find(x)
    if (m != null) { val g = m.groups[1]!!; x = x.substring(0, g.range.first) + (g.value.toInt() + add) + x.substring(g.range.last + 1) }
    val e = x.indexOf("</$tag>")
    return if (e >= 0) x.substring(0, e) + ins + x.substring(e) else x
}

private fun newStyles(b: XBook, xml: String): String {
    var x = xml
    if (b.styles.size <= b.origStyles) return x
    val borders = StringBuilder(); val bIds = HashMap<Int, Int>(); var nB = 0
    val fonts = StringBuilder(); val fills = StringBuilder(); val nf = StringBuilder(); val xfs = StringBuilder()
    var nFont = 0; var nFill = 0; var nFmt = 0
    val fontIds = HashMap<String, Int>(); val fillIds = HashMap<Int, Int>(); val fmtIds = HashMap<String, Int>(b.fmts)
    var nextFmt = (b.fmts.values.maxOrNull() ?: 163).coerceAtLeast(163) + 1
    for (i in b.origStyles until b.styles.size) {
        val s = b.styles[i]
        val fid = if (s.fontId >= 0) s.fontId else {
            val fx = "<font>${if (s.bold) "<b/>" else ""}${if (s.italic) "<i/>" else ""}${if (s.underline) "<u/>" else ""}${if (s.strike) "<strike/>" else ""}<sz val=\"${s.fsz}\"/>${if (s.color != 0) "<color rgb=\"%08X\"/>".format(s.color) else ""}<name val=\"${attrEsc(s.fname)}\"/></font>"
            fontIds.getOrPut(fx) { fonts.append(fx); b.fontCount + nFont++ }
        }
        val flid = if (s.fillId >= 0) s.fillId else if (s.fill == 0) 0 else fillIds.getOrPut(s.fill) {
            fills.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"%08X\"/><bgColor indexed=\"64\"/></patternFill></fill>".format(s.fill)); b.fillCount + nFill++
        }
        val mid = BUILTIN.entries.firstOrNull { it.value == s.fmt }?.key ?: fmtIds.getOrPut(s.fmt) { nf.append("<numFmt numFmtId=\"$nextFmt\" formatCode=\"${attrEsc(s.fmt)}\"/>"); nFmt++; nextFmt++ }
        val bid = if (s.bm < 0) s.border else bIds.getOrPut(s.bm) {
            fun side(nm: String, on: Boolean) = if (on) "<$nm style=\"thin\"><color auto=\"1\"/></$nm>" else "<$nm/>"
            borders.append("<border>" + side("left", (s.bm and 1) != 0) + side("right", (s.bm and 2) != 0) + side("top", (s.bm and 4) != 0) + side("bottom", (s.bm and 8) != 0) + "<diagonal/></border>"); b.borderCount + nB++
        }
        xfs.append("<xf numFmtId=\"$mid\" fontId=\"$fid\" fillId=\"$flid\" borderId=\"$bid\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"")
        val al = (if (s.align.isNotEmpty()) " horizontal=\"${s.align}\"" else "") + (if (s.valign.isNotEmpty()) " vertical=\"${s.valign}\"" else "") + (if (s.indent > 0) " indent=\"${s.indent}\"" else "") + (if (s.wrap) " wrapText=\"1\"" else "")
        if (al.isNotEmpty()) xfs.append(" applyAlignment=\"1\"><alignment$al/></xf>") else xfs.append("/>")
    }
    if (nB > 0) x = bump(x, "borders", nB, borders.toString())
    if (nFont > 0) x = bump(x, "fonts", nFont, fonts.toString())
    if (nFill > 0) x = bump(x, "fills", nFill, fills.toString())
    if (nFmt > 0) x = if (x.contains("<numFmts")) bump(x, "numFmts", nFmt, nf.toString()) else Regex("<styleSheet[^>]*>").find(x)!!.let { m -> x.substring(0, m.range.last + 1) + "<numFmts count=\"$nFmt\">$nf</numFmts>" + x.substring(m.range.last + 1) }
    return bump(x, "cellXfs", b.styles.size - b.origStyles, xfs.toString())
}

private const val REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

private fun nextRid(rels: String) = "rId" + ((Regex("Id=\"rId(\\d+)\"").findAll(rels).maxOfOrNull { it.groupValues[1].toInt() } ?: 0) + 1)

private fun anchorPoint(tag: String, p: String, c: Int, co: Int, r: Int, ro: Int) = "<$p$tag><${p}col>$c</${p}col><${p}colOff>$co</${p}colOff><${p}row>$r</${p}row><${p}rowOff>$ro</${p}rowOff></$p$tag>"

/**
 * Pictures: patches moved / deleted anchors of the existing drawing, appends new ones (media + rels + content types), or creates a whole drawing part for a sheet without one.
 * Edited or new parts go into [repl] / [extra]; [ct] collects content type entries; [drawTag] = sheet path -> rId of a <drawing> tag to add.
 */
private fun picParts(z: ZipFile, sheets: List<XSheet>, repl: MutableMap<String, ByteArray>, extra: MutableList<Pair<String, ByteArray>>, ct: StringBuilder, ctXml: String, drawTag: MutableMap<String, String>) {
    fun text(n: String) = z.getEntry(n)?.let { z.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
    val taken = HashSet<String>(); for (e in z.entries()) taken.add(e.name)
    for (sh in sheets) {
        val sp = sh.path ?: continue
        if (sh.drawingPath == null && sh.pics.none { !it.deleted }) continue
        var drawing = sh.drawingPath
        var dx: String
        if (drawing == null) {
            var n = 1; while ("xl/drawings/drawing$n.xml" in taken) n++
            drawing = "xl/drawings/drawing$n.xml"; taken.add(drawing)
            dx = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"></xdr:wsDr>"
            val rn = sp.substringBeforeLast('/', "") + "/_rels/" + sp.substringAfterLast('/') + ".rels"
            val old = repl[rn]?.toString(Charsets.UTF_8) ?: text(rn) ?: "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$REL_NS\"></Relationships>"
            val rid = nextRid(old)
            val nr = old.replace("</Relationships>", "<Relationship Id=\"$rid\" Type=\"$NS_R/drawing\" Target=\"../drawings/drawing$n.xml\"/></Relationships>")
            if (z.getEntry(rn) != null) repl[rn] = nr.toByteArray() else extra.add(rn to nr.toByteArray())
            drawTag[sp] = rid
            ct.append("<Override PartName=\"/$drawing\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
        } else dx = text(drawing) ?: continue
        val dName: String = drawing ?: continue
        val dr = dName.substringBeforeLast('/', "") + "/_rels/" + dName.substringAfterLast('/') + ".rels"
        var rels = text(dr) ?: "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"$REL_NS\"></Relationships>"
        // existing anchors: patch moved ones, drop deleted ones
        var i = -1
        dx = Regex("(?s)<(\\w+:)?twoCellAnchor\\b.*?</(\\w+:)?twoCellAnchor>").replace(dx) { m ->
            i++
            val pic = sh.pics.firstOrNull { it.anchor == i }
            when {
                pic == null -> m.value
                pic.deleted -> ""
                pic.moved -> m.value
                    .replace(Regex("(?s)<(\\w+:)?from>.*?</(\\w+:)?from>")) { f -> anchorPoint("from", f.groupValues[1], pic.c1, pic.co1, pic.r1, pic.ro1) }
                    .replace(Regex("(?s)<(\\w+:)?to>.*?</(\\w+:)?to>")) { f -> anchorPoint("to", f.groupValues[1], pic.c2, pic.co2, pic.r2, pic.ro2) }
                else -> m.value
            }
        }
        val add = StringBuilder(); var mediaN = 1; var shape = 1000 + sh.pics.size
        for (pic in sh.pics) {
            if (pic.anchor >= 0 || pic.deleted || pic.bytes == null) continue
            while (taken.any { it.startsWith("xl/media/image$mediaN.") }) mediaN++
            val mn = "xl/media/image$mediaN.${pic.ext}"; taken.add(mn); extra.add(mn to pic.bytes)
            val mime = if (pic.ext == "png") "image/png" else "image/jpeg"
            if (!Regex("Extension=\"${pic.ext}\"", RegexOption.IGNORE_CASE).containsMatchIn(ctXml) && !ct.contains("Extension=\"${pic.ext}\"")) ct.append("<Default Extension=\"${pic.ext}\" ContentType=\"$mime\"/>")
            val rid = nextRid(rels)
            rels = rels.replace("</Relationships>", "<Relationship Id=\"$rid\" Type=\"$NS_R/image\" Target=\"../media/image$mediaN.${pic.ext}\"/></Relationships>")
            val w = ((pic.c2 - pic.c1) * 64 * 9525L).coerceAtLeast(9525L); shape++
            add.append("<xdr:twoCellAnchor editAs=\"oneCell\" xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">")
                .append(anchorPoint("from", "xdr:", pic.c1, pic.co1, pic.r1, pic.ro1)).append(anchorPoint("to", "xdr:", pic.c2, pic.co2, pic.r2, pic.ro2))
                .append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"$shape\" name=\"Picture $shape\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
                .append("<xdr:blipFill><a:blip xmlns:r=\"$NS_R\" r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                .append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$w\" cy=\"$w\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor>")
        }
        if (add.isNotEmpty()) {
            val close = Regex("</(\\w+:)?wsDr>").findAll(dx).lastOrNull() ?: throw IllegalStateException("unsupported drawing xml")
            dx = dx.substring(0, close.range.first) + add + dx.substring(close.range.first)
        }
        if (z.getEntry(dName) != null) repl[dName] = dx.toByteArray() else extra.add(dName to dx.toByteArray())
        if (z.getEntry(dr) != null) repl[dr] = rels.toByteArray() else extra.add(dr to rels.toByteArray())
    }
}

/** Adds <drawing r:id> to a worksheet xml (before the elements that must follow it). */
private fun addDrawingTag(x: String, rid: String): String {
    var s = x
    val root = Regex("<worksheet\\b[^>]*>").find(s) ?: throw IllegalStateException("unsupported sheet xml")
    if (!root.value.contains("xmlns:r=")) s = s.substring(0, root.range.first + 10) + " xmlns:r=\"$NS_R\"" + s.substring(root.range.first + 10)
    val at = Regex("<(legacyDrawing|legacyDrawingHF|drawingHF|picture|oleObjects|controls|webPublishItems|tableParts|extLst)\\b").find(s)?.range?.first ?: s.lastIndexOf("</worksheet>")
    return s.substring(0, at) + "<drawing r:id=\"$rid\"/>" + s.substring(at)
}

/** Writes the book to [out]: xlsx keeps every untouched zip part, csv is plain text of the first sheet. */
fun xSave(b: XBook, out: File) {
    if (b.csv) { out.writeText(xCsvText(b, b.sheets[0])); return }
    val dirty = b.sheets.filter { it.dirty && it.path != null }.associateBy { it.path!! }
    ZipFile(b.file).use { z ->
        val repl = HashMap<String, ByteArray>(); val extra = ArrayList<Pair<String, ByteArray>>(); val ct = StringBuilder(); val drawTag = HashMap<String, String>()
        picParts(z, b.sheets.filter { it.picsDirty }, repl, extra, ct, z.getEntry("[Content_Types].xml")?.let { z.getInputStream(it).readBytes().toString(Charsets.UTF_8) } ?: "", drawTag)
        ZipOutputStream(out.outputStream().buffered()).use { o ->
            for (e in z.entries()) {
                if (e.isDirectory || e.name == "xl/calcChain.xml") continue
                o.putNextEntry(ZipEntry(e.name))
                val sh = dirty[e.name]
                when {
                    sh != null || drawTag.containsKey(e.name) -> {
                        var x = z.getInputStream(e).readBytes().toString(Charsets.UTF_8)
                        if (sh != null) {
                        val m = Regex("(?s)<sheetData\\s*/>|<sheetData\\b[^>]*>.*?</sheetData>").find(x) ?: throw IllegalStateException("unsupported sheet xml")
                        x = x.substring(0, m.range.first) + sheetData(sh) + x.substring(m.range.last + 1)
                        if (sh.shifted) {
                            x = x.replace(Regex("(?s)<cols>.*?</cols>"), "").replace(Regex("(?s)<mergeCells\\b.*?</mergeCells>"), "")
                            val cols = if (sh.colW.isEmpty()) "" else sh.colW.entries.sortedBy { it.key }.joinToString("", "<cols>", "</cols>") { "<col min=\"${it.key + 1}\" max=\"${it.key + 1}\" width=\"${it.value}\" customWidth=\"1\"/>" }
                            val mc = if (sh.merges.isEmpty()) "" else sh.merges.joinToString("", "<mergeCells count=\"${sh.merges.size}\">", "</mergeCells>") { "<mergeCell ref=\"${xRef(it[0], it[1])}:${xRef(it[2], it[3])}\"/>" }
                            val a = x.indexOf("<sheetData"); x = x.substring(0, a) + cols + x.substring(a)
                            val z2 = x.indexOf("</sheetData>"); x = x.substring(0, z2 + 12) + mc + x.substring(z2 + 12)
                        }
                        }
                        drawTag[e.name]?.let { x = addDrawingTag(x, it) }
                        o.write(x.toByteArray())
                    }
                    repl.containsKey(e.name) -> o.write(repl[e.name]!!)
                    e.name == b.stylesPath -> o.write(newStyles(b, z.getInputStream(e).readBytes().toString(Charsets.UTF_8)).toByteArray())
                    e.name == "[Content_Types].xml" -> o.write(z.getInputStream(e).readBytes().toString(Charsets.UTF_8).replace(Regex("<Override[^>]*calcChain[^>]*/>"), "").let { if (ct.isNotEmpty()) it.replace("</Types>", "$ct</Types>") else it }.toByteArray())
                    e.name == "xl/_rels/workbook.xml.rels" -> o.write(z.getInputStream(e).readBytes().toString(Charsets.UTF_8).replace(Regex("<Relationship\\b[^>]*calcChain[^>]*/>"), "").toByteArray())
                    else -> z.getInputStream(e).use { it.copyTo(o) }
                }
                o.closeEntry()
            }
            for ((n, bytes) in extra) { o.putNextEntry(ZipEntry(n)); o.write(bytes); o.closeEntry() }
        }
    }
}

/** Spreadsheet serial day number (1900 system) formatted with a date/time code such as yyyy-mm-dd hh:mm. */
fun xDate(code: String, v: Double): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = Math.round((v - 25569.0) * 86400000.0)
    val c = code.substringBefore(';').replace(Regex("\"([^\"]*)\"")) { it.groupValues[1] }.replace(Regex("\\[[^\\]]*\\]|\\\\|_.|\\*."), "")
    val sdf = StringBuilder()
    var i = 0
    val timeCode = c.lowercase().let { it.contains('h') || it.contains("ss") }
    val ampm = c.lowercase().contains("am/pm") || c.lowercase().contains("a/p")
    val mon = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val monL = arrayOf("January","February","March","April","May","June","July","August","September","October","November","December")
    val day = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
    val dayL = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
    val lc = c.lowercase()
    fun p(n: Int, w: Int) = n.toString().padStart(w, '0')
    var lastH = false
    while (i < c.length) {
        val ch = lc[i]
        var j = i; while (j < c.length && lc[j] == ch) j++
        val n = j - i
        when (ch) {
            'y' -> sdf.append(if (n <= 2) p(cal.get(java.util.Calendar.YEAR) % 100, 2) else p(cal.get(java.util.Calendar.YEAR), 4))
            'm' -> {
                val minute = lastH || (lc.indexOf('s', j) in j..(j + 2))
                val m = cal.get(java.util.Calendar.MONTH)
                if (minute) sdf.append(if (n == 1) cal.get(java.util.Calendar.MINUTE).toString() else p(cal.get(java.util.Calendar.MINUTE), 2))
                else sdf.append(when (n) { 1 -> (m + 1).toString(); 2 -> p(m + 1, 2); 3 -> mon[m]; else -> monL[m] })
            }
            'd' -> { val d = cal.get(java.util.Calendar.DAY_OF_MONTH); val w = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
                sdf.append(when (n) { 1 -> d.toString(); 2 -> p(d, 2); 3 -> day[w]; else -> dayL[w] }) }
            'h' -> { var h = cal.get(java.util.Calendar.HOUR_OF_DAY); if (ampm) { h %= 12; if (h == 0) h = 12 }; sdf.append(if (n == 1) h.toString() else p(h, 2)) }
            's' -> sdf.append(if (n == 1) cal.get(java.util.Calendar.SECOND).toString() else p(cal.get(java.util.Calendar.SECOND), 2))
            'a' -> { val rest = lc.substring(i); if (rest.startsWith("am/pm")) { sdf.append(if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"); j = i + 5 } else if (rest.startsWith("a/p")) { sdf.append(if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 12) "A" else "P"); j = i + 3 } else sdf.append(c.substring(i, j)) }
            else -> sdf.append(c.substring(i, j))
        }
        lastH = ch == 'h'
        i = j
    }
    if (!timeCode && sdf.isEmpty()) return xGeneral(v)
    return sdf.toString()
}
