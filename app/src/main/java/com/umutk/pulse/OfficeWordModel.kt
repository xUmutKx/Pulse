package com.umutk.pulse

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Word model: a generic XML tree (so the whole document.xml round-trips) plus paragraph helpers.

class XN(var name: String, val attrs: LinkedHashMap<String, String> = LinkedHashMap(), val kids: MutableList<XN> = ArrayList(), var text: String? = null) {
    fun child(n: String) = kids.firstOrNull { it.name == n }
    fun copy(): XN = XN(name, LinkedHashMap(attrs), kids.mapTo(ArrayList()) { it.copy() }, text)
    fun descendant(n: String): XN? { for (k in kids) { if (k.name == n) return k; k.descendant(n)?.let { return it } }; return null }
    fun v(a: String = "w:val") = attrs[a]
    fun write(sb: StringBuilder) {
        if (name == "#t") { esc(text ?: "", sb, false); return }
        sb.append('<').append(name)
        for ((k, v) in attrs) { sb.append(' ').append(k).append("=\""); esc(v, sb, true); sb.append('"') }
        if (kids.isEmpty()) sb.append("/>") else { sb.append('>'); kids.forEach { it.write(sb) }; sb.append("</").append(name).append('>') }
    }
    /** put [n] into this property list at its schema position, replacing any element of the same name */
    fun put(order: List<String>, n: XN) {
        kids.removeAll { it.name == n.name }
        val idx = order.indexOf(n.name)
        var at = kids.indexOfFirst { order.indexOf(it.name) > idx }
        if (at < 0) at = kids.size
        kids.add(at, n)
    }
}

private fun esc(s: String, sb: StringBuilder, attr: Boolean) {
    for (c in s) when {
        c == '&' -> sb.append("&amp;"); c == '<' -> sb.append("&lt;"); c == '>' -> sb.append("&gt;")
        c == '"' && attr -> sb.append("&quot;")
        c < ' ' && c != '\t' && c != '\n' && c != '\r' -> {}
        else -> sb.append(c)
    }
}

fun lhm(vararg p: Pair<String, String>) = LinkedHashMap<String, String>().apply { p.forEach { put(it.first, it.second) } }

/** set by [xParse] when the XML was damaged and only the part before the damage was read */
@Volatile var xPartial = false

fun xParse(ins: InputStream): XN = xParse(ins.readBytes())

fun xParse(b: ByteArray): XN {
    xPartial = false
    var off = 0; var cs: java.nio.charset.Charset = Charsets.UTF_8
    if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) off = 3
    else if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) { cs = Charsets.UTF_16LE; off = 2 }
    else if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) { cs = Charsets.UTF_16BE; off = 2 }
    else Regex("encoding=[\"']([A-Za-z0-9_.:-]+)").find(String(b, 0, minOf(b.size, 200), Charsets.ISO_8859_1))?.let { m ->
        runCatching { java.nio.charset.Charset.forName(m.groupValues[1]) }.getOrNull()?.let { cs = it }
    }
    val s = String(b, off, b.size - off, cs)
    return try { xParseText(s, false) } catch (e: Exception) {
        // second try: drop illegal control characters and unknown named entities, accept a truncated tree
        val clean = s.filter { it >= ' ' || it == '\t' || it == '\n' || it == '\r' }.replace(Regex("&(?!(amp|lt|gt|quot|apos|#))[A-Za-z0-9_]+;"), "")
        xParseText(clean, true)
    }
}

private fun xParseText(s: String, partialOk: Boolean): XN {
    val p = Xml.newPullParser()
    p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    p.setInput(java.io.StringReader(s))
    val stack = ArrayList<XN>(); var root: XN? = null
    try {
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> {
                    val n = XN(p.name)
                    for (i in 0 until p.attributeCount) n.attrs[p.getAttributeName(i)] = p.getAttributeValue(i)
                    stack.lastOrNull()?.kids?.add(n); if (root == null) root = n
                    stack.add(n)
                }
                XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                XmlPullParser.TEXT -> { val t = p.text; val par = stack.lastOrNull(); if (par != null && t != null && (par.name == "w:t" || t.isNotBlank())) par.kids.add(XN("#t", text = t)) }
            }
            e = p.next()
        }
    } catch (e: Exception) {
        if (!partialOk || root == null) throw e
        xPartial = true
    }
    return root ?: throw IOException("empty xml")
}

/** zip entry by name, tolerant of case and backslashes */
fun zEntry(z: ZipFile, name: String): ZipEntry? =
    z.getEntry(name) ?: z.entries().asSequence().firstOrNull { it.name.replace('\\', '/').equals(name, true) }

/** a block level wrapper whose children are blocks too (content control, tracked insert, custom xml, alternate content) */
fun contOf(n: XN): XN? = when (n.name) {
    "w:sdt" -> n.child("w:sdtContent")
    "w:ins", "w:moveTo", "w:customXml", "w:smartTag" -> n
    "mc:AlternateContent" -> n.child("mc:Fallback") ?: n.child("mc:Choice")
    else -> null
}
fun rowsOf(t: XN): List<XN> { val o = ArrayList<XN>(); fun go(n: XN) { for (k in n.kids) if (k.name == "w:tr") o.add(k) else contOf(k)?.let { go(it) } }; go(t); return o }
fun cellsOf(tr: XN): List<XN> { val o = ArrayList<XN>(); fun go(n: XN) { for (k in n.kids) if (k.name == "w:tc") o.add(k) else contOf(k)?.let { go(it) } }; go(tr); return o }
private fun XN.addAfter(ref: XN, n: XN): Boolean {
    val i = kids.indexOfFirst { it === ref }
    if (i >= 0) { kids.add(i + 1, n); return true }
    return kids.any { it.addAfter(ref, n) }
}
private fun XN.removeDeep(t: XN): Boolean = kids.removeAll { it === t } || kids.any { it.removeDeep(t) }

data class RP(var b: Boolean? = null, var i: Boolean? = null, var u: Boolean? = null, var strike: Boolean? = null, var sz: Int? = null, var color: String? = null, var hl: String? = null, var font: String? = null)
class PP(var jc: String? = null, var before: Float = 0f, var after: Float = 0f, var left: Float = 0f, var first: Float = 0f, var hang: Float = 0f, var line: Float = 1f, var numId: String? = null, var ilvl: Int = 0)
class WStyle(val id: String, val name: String?, val basedOn: String?, val rPr: XN?, val pPr: XN?, val isDefault: Boolean, val type: String?)
class WFlat(val ps: ArrayList<XN> = ArrayList(), val parents: ArrayList<XN> = ArrayList(), val tops: ArrayList<Int> = ArrayList(), val idx: IdentityHashMap<XN, Int> = IdentityHashMap())

private fun tog(k: XN) = k.attrs["w:val"].let { it == null || (it != "0" && it != "false" && it != "off") }
private fun hexOf(s: String?) = s?.takeIf { it.length == 6 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }

fun rpInto(rPr: XN?, o: RP) {
    rPr ?: return
    for (k in rPr.kids) when (k.name) {
        "w:b" -> o.b = tog(k); "w:i" -> o.i = tog(k); "w:strike" -> o.strike = tog(k)
        "w:u" -> o.u = k.v() != "none"
        "w:sz" -> k.v()?.toIntOrNull()?.let { o.sz = it }
        "w:color" -> hexOf(k.v())?.let { o.color = it }
        "w:highlight" -> o.hl = k.v()
        "w:rFonts" -> (k.attrs["w:ascii"] ?: k.attrs["w:hAnsi"])?.let { o.font = it }
    }
}

fun ppInto(pPr: XN?, o: PP) {
    pPr ?: return
    for (k in pPr.kids) when (k.name) {
        "w:jc" -> o.jc = k.v()
        "w:spacing" -> {
            k.attrs["w:before"]?.toFloatOrNull()?.let { o.before = it }; k.attrs["w:after"]?.toFloatOrNull()?.let { o.after = it }
            val l = k.attrs["w:line"]?.toFloatOrNull(); if (l != null && k.attrs["w:lineRule"].let { it == null || it == "auto" }) o.line = (l / 240f).coerceIn(.8f, 3f)
        }
        "w:ind" -> {
            (k.attrs["w:left"] ?: k.attrs["w:start"])?.toFloatOrNull()?.let { o.left = it }
            k.attrs["w:firstLine"]?.toFloatOrNull()?.let { o.first = it; o.hang = 0f }
            k.attrs["w:hanging"]?.toFloatOrNull()?.let { o.hang = it; o.first = 0f }
        }
        "w:numPr" -> { k.child("w:ilvl")?.v()?.toIntOrNull()?.let { o.ilvl = it }; k.child("w:numId")?.v()?.let { o.numId = it } }
    }
}

private val RPR_ORDER = listOf("w:rStyle", "w:rFonts", "w:b", "w:bCs", "w:i", "w:iCs", "w:caps", "w:smallCaps", "w:strike", "w:dstrike", "w:outline", "w:shadow", "w:emboss", "w:imprint", "w:noProof", "w:snapToGrid", "w:vanish", "w:webHidden", "w:color", "w:spacing", "w:w", "w:kern", "w:position", "w:sz", "w:szCs", "w:highlight", "w:u", "w:effect", "w:bdr", "w:shd", "w:fitText", "w:vertAlign")
private val PPR_ORDER = listOf("w:pStyle", "w:keepNext", "w:keepLines", "w:pageBreakBefore", "w:framePr", "w:widowControl", "w:numPr", "w:suppressLineNumbers", "w:pBdr", "w:shd", "w:tabs", "w:suppressAutoHyphens", "w:kinsoku", "w:wordWrap", "w:overflowPunct", "w:topLinePunct", "w:autoSpaceDE", "w:autoSpaceDN", "w:bidi", "w:adjustRightInd", "w:snapToGrid", "w:spacing", "w:ind", "w:contextualSpacing", "w:mirrorIndents", "w:suppressOverlap", "w:jc", "w:textDirection", "w:textAlignment", "w:textboxTightWrap", "w:outlineLvl", "w:divId", "w:cnfStyle", "w:rPr", "w:sectPr", "w:pPrChange")

fun runsOf(p: XN): List<XN> {
    val out = ArrayList<XN>()
    fun go(n: XN) { for (k in n.kids) when (k.name) { "w:r" -> out.add(k); "w:hyperlink", "w:ins", "w:moveTo", "w:smartTag", "w:fldSimple", "w:sdt", "w:sdtContent", "w:customXml", "w:dir", "w:bdo" -> go(k)
        "mc:AlternateContent" -> (k.child("mc:Fallback") ?: k.child("mc:Choice"))?.let { go(it) } } }
    go(p); return out
}
fun hasDrawing(r: XN) = r.descendant("w:drawing") != null || r.descendant("w:pict") != null
fun runText(r: XN): String {
    val sb = StringBuilder()
    for (k in r.kids) when (k.name) {
        "w:t" -> k.kids.forEach { sb.append(it.text ?: "") }
        "w:tab", "w:ptab" -> sb.append('\t'); "w:br", "w:cr" -> if (k.attrs["w:type"] != "page") sb.append('\n'); "w:noBreakHyphen" -> sb.append('-')
    }
    return sb.toString()
}
fun paraText(p: XN): String = runsOf(p).joinToString("") { runText(it) }
fun hasPageBreak(p: XN) = runsOf(p).any { r -> r.kids.any { it.name == "w:br" && it.attrs["w:type"] == "page" } } || p.child("w:pPr")?.child("w:pageBreakBefore") != null

private fun makeRun(rPr: XN?, text: String): XN {
    val r = XN("w:r"); rPr?.let { r.kids.add(it.copy()) }
    val sb = StringBuilder()
    fun flush() { if (sb.isNotEmpty()) { r.kids.add(XN("w:t", lhm("xml:space" to "preserve"), mutableListOf(XN("#t", text = sb.toString())))); sb.setLength(0) } }
    for (c in text) when (c) { '\t' -> { flush(); r.kids.add(XN("w:tab")) }; '\n' -> { flush(); r.kids.add(XN("w:br")) }; else -> sb.append(c) }
    flush(); return r
}

/** word/media image refs of a paragraph: (relationship id, width pt, height pt) */
fun imagesOf(p: XN): List<Triple<String, Float, Float>> {
    val out = ArrayList<Triple<String, Float, Float>>()
    for (r in runsOf(p)) {
        val d = r.descendant("w:drawing")
        if (d != null) {
            val id = d.descendant("a:blip")?.attrs?.get("r:embed") ?: continue
            val ext = d.descendant("wp:extent")
            out.add(Triple(id, (ext?.attrs?.get("cx")?.toFloatOrNull() ?: 2540000f) / 12700f, (ext?.attrs?.get("cy")?.toFloatOrNull() ?: 1905000f) / 12700f))
        } else r.descendant("w:pict")?.descendant("v:imagedata")?.attrs?.get("r:id")?.let { out.add(Triple(it, 200f, 150f)) }
    }
    return out
}

/** table style bits that reach the paragraphs of a cell */
class TCtx(val rprs: List<XN>, val pprs: List<XN>, val dark: Boolean, val filled: Boolean = false)
class WBord(val pt: Float, val color: String?, val none: Boolean)
class WCellL(val tc: XN, val start: Int, val span: Int, val wTw: Float, val fill: String?, val vm: Int, val b: Array<WBord?>, val t: TCtx, val mar: FloatArray, val valign: String?)
class WRowL(val minH: Float, val cells: List<WCellL>, val before: Float = 0f, val exact: Boolean = false)

/** Layout of one table; rows are computed on demand so a table of thousands of rows stays cheap. */
class WTbl(val w: WDoc, val tbl: XN, availTw: Float) {
    val rows: List<XN> = rowsOf(tbl)
    private val pr = tbl.child("w:tblPr")
    private val chain: List<XN> = w.tableStyleChain(pr?.child("w:tblStyle")?.v())
    private val lk = pr?.child("w:tblLook")
    val grid: List<Float>
    val fac: Float
    /** table alignment left / center / right and the left indent in twips (only meaningful for left) */
    val jc: String = when (pr?.child("w:jc")?.v()) { "center" -> "center"; "right", "end" -> "right"; else -> "left" }
    val indTw: Float = pr?.child("w:tblInd")?.v("w:w")?.toFloatOrNull() ?: 0f
    init {
        var g = tbl.child("w:tblGrid")?.kids?.filter { it.name == "w:gridCol" }?.map { it.attrs["w:w"]?.toFloatOrNull()?.coerceAtLeast(0f) ?: 1440f } ?: emptyList()
        var maxSpan = 0
        for (tr in rows) { var n = tr.child("w:trPr")?.child("w:gridBefore")?.v()?.toIntOrNull() ?: 0; for (tc in cellsOf(tr)) n += spanOf(tc); if (n > maxSpan) maxSpan = n }
        if (g.size < maxSpan) {
            val widths = rows.maxByOrNull { cellsOf(it).size }?.let { cellsOf(it).map { tc -> tc.child("w:tcPr")?.child("w:tcW")?.takeIf { x -> x.v("w:type") == "dxa" }?.v("w:w")?.toFloatOrNull() ?: 0f } } ?: emptyList()
            val padW = (g.lastOrNull() ?: widths.firstOrNull { it > 0f } ?: 1440f).coerceAtLeast(300f)
            g = if (g.isEmpty() && widths.size == maxSpan && widths.all { it > 0f }) widths else g + List(maxSpan - g.size) { padW }
        }
        grid = g
        val total = g.sum().coerceAtLeast(1f)
        val tw = pr?.child("w:tblW"); val tvs = tw?.v("w:w") ?: ""; val tv = tvs.trimEnd('%').toFloatOrNull() ?: 0f
        val want = when (tw?.v("w:type")) { "pct" -> if (tvs.endsWith("%")) availTw * tv / 100f else availTw * tv / 5000f; "dxa" -> tv; else -> if (tvs.endsWith("%")) availTw * tv / 100f else 0f }
        // shrink only tables that really do not fit; a table that fits keeps its grid
        val room = if (jc == "left") (availTw - indTw).coerceAtLeast(availTw * .2f) else availTw
        fac = (if (want > 0f && want <= availTw * 1.01f) want / total else if (total > room) room / total else 1f).coerceIn(.05f, 4f)
    }
    val totalTw get() = grid.sum() * fac

    fun row(r: Int): WRowL = try { build(r) } catch (e: Exception) { // unstyled row so the text is still shown
        var gi = 0
        WRowL(0f, cellsOf(rows[r]).map { tc -> val sp = spanOf(tc); WCellL(tc, gi, sp, (gi until gi + sp).sumOf { (grid.getOrNull(it) ?: 1440f).toDouble() }.toFloat() * fac, null, 0, arrayOfNulls(4), TCtx(emptyList(), emptyList(), false), floatArrayOf(108f, 108f, 0f, 0f), null).also { gi += sp } }, 0f, false)
    }

    private fun cond(r: Int, start: Int, span: Int): List<XN> {
        val n = rows.size
        val fr = look("firstRow", 0x20); val lr = look("lastRow", 0x40); val fc = look("firstColumn", 0x80); val lc = look("lastColumn", 0x100)
        val nh = !look("noHBand", 0x200, false); val nv = !look("noVBand", 0x400, false)
        val on = HashSet<String>(); on.add("wholeTable")
        if (nh) { val b = r - (if (fr) 1 else 0); if (b >= 0 && !(lr && r == n - 1)) on.add(if (b % 2 == 0) "band1Horz" else "band2Horz") }
        if (nv) { val b = start - (if (fc) 1 else 0); if (b >= 0 && !(lc && start + span == grid.size)) on.add(if (b % 2 == 0) "band1Vert" else "band2Vert") }
        if (lc && start + span == grid.size) on.add("lastCol"); if (fc && start == 0) on.add("firstCol")
        if (lr && r == n - 1) on.add("lastRow"); if (fr && r == 0) on.add("firstRow")
        val out = ArrayList<XN>()
        for (t in listOf("wholeTable", "band1Vert", "band2Vert", "band1Horz", "band2Horz", "lastCol", "firstCol", "lastRow", "firstRow")) if (t in on)
            for (st in chain) for (x in st.kids) if (x.name == "w:tblStylePr" && x.v("w:type") == t) out.add(x)
        return out
    }
    private fun look(name: String, bit: Int, def: Boolean = false): Boolean {
        val n = lk ?: return def
        n.attrs["w:$name"]?.let { return it == "1" || it == "true" }
        n.attrs["w:val"]?.toIntOrNull(16)?.let { return it and bit != 0 }
        return def
    }

    private fun gridAfter(rr: Int) = rows[rr].child("w:trPr")?.child("w:gridAfter")?.v()?.toIntOrNull() ?: 0
    private fun cellAt(rr: Int, g: Int): Triple<XN, Int, Int>? {
        if (rr !in rows.indices) return null
        var x = rows[rr].child("w:trPr")?.child("w:gridBefore")?.v()?.toIntOrNull() ?: 0
        for (tc in cellsOf(rows[rr])) { val sp = spanOf(tc); if (g >= x && g < x + sp) return Triple(tc, x, sp); x += sp }
        return null
    }
    /** one border (k: 0 top, 1 left, 2 bottom, 3 right) of the cell at (rr, g..g+sp) from cell, conditional and table level */
    private fun side(rr: Int, tc: XN, g: Int, sp: Int, k: Int): WBord? {
        val cs = cond(rr, g, sp); val tcPr = tc.child("w:tcPr")
        val tcSrc = ArrayList<XN>(); tcPr?.child("w:tcBorders")?.let { tcSrc.add(it) }; cs.asReversed().forEach { c -> c.child("w:tcPr")?.child("w:tcBorders")?.let { tcSrc.add(it) } }
        val tblSrc = ArrayList<XN>(); pr?.child("w:tblBorders")?.let { tblSrc.add(it) }; cs.asReversed().forEach { c -> c.child("w:tblPr")?.child("w:tblBorders")?.let { tblSrc.add(it) } }
        chain.asReversed().forEach { st -> st.child("w:tblPr")?.child("w:tblBorders")?.let { tblSrc.add(it) } }
        val up = rr == 0; val down = rr == rows.size - 1; val lf = g == 0; val rt = g + sp >= grid.size - gridAfter(rr)
        val tcN = when (k) { 0 -> listOf("w:top"); 1 -> listOf("w:left", "w:start"); 2 -> listOf("w:bottom"); else -> listOf("w:right", "w:end") }
        val tN = when (k) { 0 -> listOf(if (up) "w:top" else "w:insideH"); 1 -> if (lf) listOf("w:left", "w:start") else listOf("w:insideV"); 2 -> listOf(if (down) "w:bottom" else "w:insideH"); else -> if (rt) listOf("w:right", "w:end") else listOf("w:insideV") }
        for (c in tcSrc) for (nm in tcN) c.child(nm)?.let { return bd(it) }
        for (c in tblSrc) for (nm in tN) c.child(nm)?.let { return bd(it) }
        return null
    }
    /** shared edge of two neighbours: the thicker one wins, nil weighs nothing */
    private fun win(a: WBord?, b: WBord?): WBord? = when { a == null -> b; b == null -> a; b.none -> a; a.none -> b; b.pt > a.pt -> b; else -> a }

    private fun build(r: Int): WRowL {
        val tr = rows[r]; val trPr = tr.child("w:trPr")
        val th = trPr?.child("w:trHeight"); val minH = th?.v()?.toFloatOrNull() ?: 0f
        val before = trPr?.child("w:gridBefore")?.v()?.toIntOrNull() ?: 0
        var gi = before
        val tcs = cellsOf(tr)
        fun isHCont(tc: XN) = tc.child("w:tcPr")?.child("w:hMerge")?.let { it.v() != "restart" } ?: false
        val out = ArrayList<WCellL>()
        var prev: Triple<XN, Int, Int>? = null
        for ((idx, tc) in tcs.withIndex()) {
            val tcPr = tc.child("w:tcPr"); val span = spanOf(tc)
            if (isHCont(tc) && out.isNotEmpty()) { // old style horizontal merge: the cell joins the one before it
                val o = out.removeAt(out.size - 1); var add = 0f; for (k in gi until gi + span) add += grid.getOrNull(k) ?: 0f
                if (add <= 0f) add = tcPr?.child("w:tcW")?.takeIf { it.v("w:type") == "dxa" }?.v("w:w")?.toFloatOrNull() ?: 0f
                out.add(WCellL(o.tc, o.start, o.span + span, o.wTw + add * fac, o.fill, o.vm, o.b, o.t, o.mar, o.valign))
                prev = prev?.let { Triple(it.first, it.second, it.third + span) }; gi += span; continue
            }
            val cs = cond(r, gi, span)
            fun fillOf(n: XN?): String? = n?.child("w:shd")?.v("w:fill")?.takeIf { f -> f.length == 6 && f.all { it.isLetterOrDigit() } }
            val fill = fillOf(tcPr) ?: cs.asReversed().firstNotNullOfOrNull { fillOf(it.child("w:tcPr")) } ?: fillOf(pr) ?: chain.asReversed().firstNotNullOfOrNull { fillOf(it.child("w:tcPr")) }
            val vm = tcPr?.child("w:vMerge")?.let { if (it.v() == "restart") 1 else 2 } ?: 0
            // each cell draws its top and left edge (already resolved against the neighbour above / before it) and bottom / right only at the table edge
            val up = if (vm == 2) null else win(side(r, tc, gi, span, 0), cellAt(r - 1, gi)?.let { (t2, g2, s2) -> side(r - 1, t2, g2, s2, 2) })
            val lf = win(side(r, tc, gi, span, 1), prev?.let { (t2, g2, s2) -> side(r, t2, g2, s2, 3) })
            val dn = if (r == rows.size - 1 || cellAt(r + 1, gi) == null) side(r, tc, gi, span, 2) else null
            val rt = if (tcs.drop(idx + 1).all { isHCont(it) }) side(r, tc, gi, span, 3) else null
            val b = arrayOf(up, lf, dn, rt)
            // margins l, r, t, b (twips)
            val mars = ArrayList<XN>(); tcPr?.child("w:tcMar")?.let { mars.add(it) }; pr?.child("w:tblCellMar")?.let { mars.add(it) }; chain.asReversed().forEach { st -> st.child("w:tblPr")?.child("w:tblCellMar")?.let { mars.add(it) } }
            fun mar(names: List<String>, d: Float): Float { for (m in mars) for (nm in names) m.child(nm)?.v("w:w")?.toFloatOrNull()?.let { return it }; return d }
            val mar = floatArrayOf(mar(listOf("w:left", "w:start"), 108f), mar(listOf("w:right", "w:end"), 108f), mar(listOf("w:top"), 40f), mar(listOf("w:bottom"), 40f))
            val rprs = ArrayList<XN>(); val pprs = ArrayList<XN>()
            for (st in chain) { st.child("w:rPr")?.let { rprs.add(it) }; st.child("w:pPr")?.let { pprs.add(it) } }
            for (c in cs) { c.child("w:rPr")?.let { rprs.add(it) }; c.child("w:pPr")?.let { pprs.add(it) } }
            val dark = fill?.toIntOrNull(16)?.let { v -> ((v shr 16 and 255) * 299 + (v shr 8 and 255) * 587 + (v and 255) * 114) / 1000 < 100 } ?: false
            var wTw = 0f; for (k in gi until gi + span) wTw += grid.getOrNull(k) ?: 0f
            if (wTw <= 0f) wTw = (tcPr?.child("w:tcW")?.takeIf { it.v("w:type") == "dxa" }?.v("w:w")?.toFloatOrNull() ?: 1440f) / fac
            out.add(WCellL(tc, gi, span, wTw * fac, fill, vm, b, TCtx(rprs, pprs, dark, fill?.toIntOrNull(16) != null), mar, tcPr?.child("w:vAlign")?.v()))
            prev = Triple(tc, gi, span); gi += span
        }
        var bf = 0f; for (k in 0 until before) bf += grid.getOrNull(k) ?: 0f
        return WRowL(minH, out, bf * fac, th?.v("w:hRule") == "exact" && minH > 0f)
    }
    private fun bd(n: XN): WBord {
        val v = n.v() ?: "single"
        if (v == "nil" || v == "none") return WBord(0f, null, true)
        return WBord(((n.attrs["w:sz"]?.toFloatOrNull() ?: 4f) / 8f).coerceIn(.25f, 12f), n.attrs["w:color"]?.takeIf { it.length == 6 && it.all { c -> c.isLetterOrDigit() } }, false)
    }
}
private fun spanOf(tc: XN) = tc.child("w:tcPr")?.child("w:gridSpan")?.v()?.toIntOrNull()?.coerceAtLeast(1) ?: 1

class WPage(val w: Float, val h: Float, val l: Float, val r: Float, val t: Float, val b: Float)

class WDoc(val file: File, val fmt: String) {
    var root: XN = XN("w:document", lhm("xmlns:w" to "http://schemas.openxmlformats.org/wordprocessingml/2006/main"))
    var stylesRoot: XN? = null
    private var stylesDirty = false
    val styles = HashMap<String, WStyle>()
    private var defRp = RP(sz = 22)
    private var defPp = PP()
    private var defStyle: String? = null
    val rels = HashMap<String, String>()
    var partial = false
    private var docName = "word/document.xml"; private var stylesName = "word/styles.xml"
    private var relsRoot: XN? = null; private var relsName = "word/_rels/document.xml.rels"; private var relsDirty = false
    private var ctRoot: XN? = null; private var ctName = "[Content_Types].xml"; private var ctDirty = false
    private val zipNames = HashSet<String>(); private val media = ArrayList<Pair<String, ByteArray>>()
    private val tstyles = HashMap<String, List<XN>>()
    private val numFmt = HashMap<String, String>()
    var bulletNumId: String? = null
    /** doc / rtf / odt are edited in memory and can only be saved as a new .docx */
    val converted get() = fmt == "doc" || fmt == "rtf" || fmt == "odt"
    val editableFmt get() = (fmt == "docx" && !partial) || fmt == "txt" || converted
    val body: XN get() = root.child("w:body") ?: XN("w:body").also { root.kids.add(it) }

    private val undoS = ArrayList<String>(); private val redoS = ArrayList<String>()
    val canUndo get() = undoS.isNotEmpty()
    val canRedo get() = redoS.isNotEmpty()

    companion object {
        fun open(f: File): WDoc {
            val head = ByteArray(8).also { b -> f.inputStream().use { it.read(b) } }
            val d = when {
                head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> {
                    val z = ZipFile(f)
                    z.use { zz ->
                        if (zEntry(zz, "word/document.xml") != null || zEntry(zz, "_rels/.rels") != null && zz.getEntry("content.xml") == null) WDoc(f, "docx").also { it.loadDocx(zz) }
                        else if (zz.getEntry("content.xml") != null) WDoc(f, "odt").also { it.loadOdt(zz) }
                        else throw IOException("Not a Word document")
                    }
                }
                head[0] == '{'.code.toByte() && head[1] == '\\'.code.toByte() -> WDoc(f, "rtf").also { it.fromText(rtfText(f.readText(Charsets.ISO_8859_1))) }
                head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() -> WDoc(f, "doc").also { d -> val by = f.readBytes(); val bl = OfBinDoc.parse(by); if (bl != null) d.fromBlocks(bl) else d.fromText(docText(by)) }
                else -> WDoc(f, "txt").also { it.fromText(String(f.readBytes(), Charsets.UTF_8)) }
            }
            return d
        }
    }

    private fun loadDocx(z: ZipFile) {
        val de = docEntry(z) ?: throw IOException("Not a Word document")
        docName = de.name
        root = z.getInputStream(de).use { xParse(it) }
        partial = xPartial
        body
        for (e in z.entries()) zipNames.add(e.name)
        val dir = docName.substringBeforeLast('/', "")
        val base = docName.substringAfterLast('/')
        stylesName = (if (dir.isEmpty()) "" else "$dir/") + "styles.xml"
        zEntry(z, stylesName)?.let { e -> stylesName = e.name; runCatching { stylesRoot = z.getInputStream(e).use { xParse(it) }; indexStyles() } }
        relsName = (if (dir.isEmpty()) "" else "$dir/") + "_rels/$base.rels"
        val re = zEntry(z, relsName)
        if (re != null) relsName = re.name
        runCatching {
            relsRoot = if (re != null) z.getInputStream(re).use { xParse(it) } else XN("Relationships", lhm("xmlns" to "http://schemas.openxmlformats.org/package/2006/relationships"))
            relsRoot!!.kids.forEach { r -> val id = r.attrs["Id"]; val t = r.attrs["Target"]; if (id != null && t != null && r.attrs["TargetMode"] != "External") rels[id] = resolve(dir, t) }
        }
        zEntry(z, (if (dir.isEmpty()) "" else "$dir/") + "numbering.xml")?.let { e -> runCatching { indexNumbering(xParse(z.getInputStream(e))) } }
        zEntry(z, ctName)?.let { e -> ctName = e.name; runCatching { ctRoot = z.getInputStream(e).use { xParse(it) } } }
    }

    private fun docEntry(z: ZipFile): ZipEntry? {
        zEntry(z, "word/document.xml")?.let { return it }
        val t = zEntry(z, "_rels/.rels")?.let { e -> runCatching { xParse(z.getInputStream(e)).kids.firstOrNull { it.attrs["Type"]?.endsWith("/officeDocument") == true }?.attrs?.get("Target") }.getOrNull() }
        return t?.let { zEntry(z, it.trimStart('/')) }
    }

    private fun resolve(dir: String, t0: String): String {
        val t = runCatching { java.net.URLDecoder.decode(t0.replace("+", "%2B"), "UTF-8") }.getOrDefault(t0).replace('\\', '/')
        val parts = ArrayList<String>(); if (!t.startsWith("/") && dir.isNotEmpty()) parts.addAll(dir.split('/'))
        for (x in t.split('/')) when (x) { "", "." -> {}; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1); else -> parts.add(x) }
        return parts.joinToString("/")
    }

    private fun loadOdt(z: ZipFile) {
        val x = xParse(z.getInputStream(z.getEntry("content.xml")))
        val lines = ArrayList<String>()
        fun txt(n: XN, sb: StringBuilder) { if (n.name == "#t") sb.append(n.text) else if (n.name == "text:tab") sb.append('\t') else if (n.name == "text:s") sb.append(' ') else n.kids.forEach { txt(it, sb) } }
        fun go(n: XN) { if (n.name == "text:p" || n.name == "text:h") lines.add(StringBuilder().also { txt(n, it) }.toString()) else n.kids.forEach { go(it) } }
        go(x); fromText(lines.joinToString("\n"))
    }

    fun fromText(t: String) {
        root = XN("w:document", lhm("xmlns:w" to "http://schemas.openxmlformats.org/wordprocessingml/2006/main"))
        val b = XN("w:body"); root.kids.add(b)
        for (line in t.replace("\r\n", "\n").replace('\r', '\n').split('\n')) b.kids.add(XN("w:p").also { p -> if (line.isNotEmpty()) p.kids.add(makeRun(null, line)) })
    }

    /** legacy .doc: paragraphs and bordered tables built as the same XML tree a docx would give */
    fun fromBlocks(bl: List<DBlk>) {
        root = XN("w:document", lhm("xmlns:w" to "http://schemas.openxmlformats.org/wordprocessingml/2006/main"))
        val b = XN("w:body"); root.kids.add(b)
        for (x in bl) {
            val ptxt = x.para
            if (ptxt != null) { b.kids.add(XN("w:p").also { p -> if (ptxt.isNotEmpty()) p.kids.add(makeRun(null, ptxt)) }); continue }
            val rows = x.rows ?: continue
            // grid = union of every row's column edges (twips); a cell spans the grid columns between its own edges
            val edges = ArrayList<Int>()
            x.bounds?.forEach { bd -> bd?.forEach { e -> if (edges.none { Math.abs(it - e) < 20 }) edges.add(e) } }
            edges.sort()
            val useG = edges.size >= 2 && x.bounds!!.zip(rows).all { (bd, r) -> bd != null && bd.size == r.size + 1 }
            val cols = if (useG) edges.size - 1 else rows.maxOf { it.size }.coerceAtLeast(1)
            fun gi(e: Int) = edges.indexOfFirst { Math.abs(it - e) < 20 }.coerceAtLeast(0)
            val bd = XN("w:tblBorders"); for (n in listOf("top", "left", "bottom", "right", "insideH", "insideV")) bd.kids.add(XN("w:$n", lhm("w:val" to "single", "w:sz" to "4", "w:color" to "808080")))
            val t = XN("w:tbl"); t.kids.add(XN("w:tblPr").also { it.kids.add(bd) })
            t.kids.add(XN("w:tblGrid").also { g -> repeat(cols) { k -> g.kids.add(XN("w:gridCol", lhm("w:w" to (if (useG) edges[k + 1] - edges[k] else 9360 / cols).toString()))) } })
            rows.forEachIndexed { ri, r ->
                if (r.isEmpty()) return@forEachIndexed
                val tr = XN("w:tr"); val bd = x.bounds?.getOrNull(ri)
                r.forEachIndexed { i, c ->
                    val tc = XN("w:tc"); val span = if (useG && bd != null) (gi(bd[i + 1]) - gi(bd[i])).coerceAtLeast(1) else if (i == r.size - 1) cols - i else 1
                    if (span > 1) tc.kids.add(XN("w:tcPr").also { it.kids.add(XN("w:gridSpan", lhm("w:val" to span.toString()))) })
                    for (line in c.split('\n')) tc.kids.add(XN("w:p").also { p -> if (line.isNotEmpty()) p.kids.add(makeRun(null, line)) })
                    tr.kids.add(tc)
                }
                t.kids.add(tr)
            }
            b.kids.add(t); b.kids.add(XN("w:p"))
        }
    }

    private fun indexNumbering(n: XN) {
        val abs = HashMap<String, XN>()
        n.kids.filter { it.name == "w:abstractNum" }.forEach { a -> a.v("w:abstractNumId")?.let { abs[it] = a } }
        for (num in n.kids.filter { it.name == "w:num" }) {
            val id = num.v("w:numId") ?: continue
            val a = abs[num.child("w:abstractNumId")?.v() ?: ""] ?: continue
            for (l in a.kids.filter { it.name == "w:lvl" }) {
                val lv = l.v("w:ilvl") ?: continue
                val f = l.child("w:numFmt")?.v() ?: "decimal"
                numFmt["$id:$lv"] = f
                if (lv == "0" && f == "bullet" && bulletNumId == null) bulletNumId = id
            }
        }
    }

    private fun indexStyles() {
        styles.clear()
        val s = stylesRoot ?: return
        s.child("w:docDefaults")?.let { d -> rpInto(d.child("w:rPrDefault")?.child("w:rPr"), defRp); ppInto(d.child("w:pPrDefault")?.child("w:pPr"), defPp) }
        for (k in s.kids) if (k.name == "w:style") {
            val id = k.v("w:styleId") ?: continue
            val dflt = k.attrs["w:default"] == "1" && k.attrs["w:type"] == "paragraph"
            if (dflt) defStyle = id
            styles[id] = WStyle(id, k.child("w:name")?.v(), k.child("w:basedOn")?.v(), k.child("w:rPr"), k.child("w:pPr"), dflt, k.attrs["w:type"])
        }
    }

    private fun chain(id: String?): List<WStyle> {
        val out = ArrayList<WStyle>(); var c = id; var n = 0
        while (c != null && n++ < 10) { val s = styles[c] ?: break; out.add(0, s); c = s.basedOn }
        return out
    }
    fun paraStyleId(p: XN) = p.child("w:pPr")?.child("w:pStyle")?.v() ?: defStyle

    fun effRp(p: XN, r: XN?, t: TCtx? = null): RP {
        val o = defRp.copy()
        chain(paraStyleId(p)).forEach { rpInto(it.rPr, o) }
        t?.rprs?.forEach { rpInto(it, o) }
        if (r != null) { r.child("w:rPr")?.child("w:rStyle")?.v()?.let { id -> chain(id).forEach { rpInto(it.rPr, o) } }; rpInto(r.child("w:rPr"), o) }
        return o
    }
    fun pp(p: XN, t: TCtx? = null): PP {
        val o = PP(before = defPp.before, after = defPp.after, line = defPp.line)
        chain(paraStyleId(p)).forEach { ppInto(it.pPr, o) }
        if (t != null && p.child("w:pPr")?.child("w:pStyle") == null) t.pprs.forEach { ppInto(it, o) }
        ppInto(p.child("w:pPr"), o)
        if (o.numId == "0") o.numId = null
        if (o.numId != null && o.left == 0f) { o.left = 720f * (o.ilvl + 1); o.hang = 360f }
        return o
    }
    fun firstRp(p: XN, t: TCtx? = null): RP { val rs = runsOf(p); return effRp(p, rs.firstOrNull { runText(it).isNotEmpty() } ?: rs.firstOrNull(), t) }

    /** table style nodes from the base style to the used one; empty when styles.xml or the style is missing */
    fun tableStyleChain(id: String?): List<XN> {
        if (id == null) return emptyList()
        return tstyles.getOrPut(id) {
            val out = ArrayList<XN>(); var c: String? = id; var n = 0
            while (c != null && n++ < 10) {
                val st = stylesRoot?.kids?.firstOrNull { it.name == "w:style" && it.attrs["w:styleId"] == c } ?: break
                out.add(0, st); c = st.child("w:basedOn")?.v()
            }
            out
        }
    }
    fun styleName(p: XN): String = styles[paraStyleId(p) ?: ""]?.name?.lowercase() ?: ""

    fun label(numId: String, lvl: Int, n: Int): String {
        val f = numFmt["$numId:$lvl"] ?: "decimal"
        fun roman(x: Int): String { var v = x; val sb = StringBuilder(); for ((s, k) in listOf("m" to 1000, "cm" to 900, "d" to 500, "cd" to 400, "c" to 100, "xc" to 90, "l" to 50, "xl" to 40, "x" to 10, "ix" to 9, "v" to 5, "iv" to 4, "i" to 1)) while (v >= k) { sb.append(s); v -= k }; return sb.toString() }
        return when (f) {
            "bullet" -> if (lvl == 0) "•" else "◦"
            "lowerLetter" -> "${'a' + (n - 1) % 26}."; "upperLetter" -> "${'A' + (n - 1) % 26}."
            "lowerRoman" -> roman(n) + "."; "upperRoman" -> roman(n).uppercase() + "."
            "none" -> ""
            else -> "$n."
        }
    }

    fun blocks(): List<XN> = body.kids.filter { it.name == "w:p" || it.name == "w:tbl" || contOf(it) != null }

    fun flatten(): WFlat {
        val f = WFlat()
        fun go(n: XN, top: Int) {
            for (k in n.kids) when (k.name) {
                "w:p" -> { f.idx[k] = f.ps.size; f.ps.add(k); f.parents.add(n); f.tops.add(top) }
                "w:tbl" -> rowsOf(k).forEach { tr -> cellsOf(tr).forEach { go(it, top) } }
                else -> contOf(k)?.let { go(it, top) }
            }
        }
        blocks().forEachIndexed { i, b -> if (b.name == "w:p") go(XN("x", kids = mutableListOf(b)), i) else go(XN("x", kids = mutableListOf(b)), i) }
        // parents of top-level paragraphs must be the body, not the temp node
        for (i in f.ps.indices) if (f.parents[i].name == "x") f.parents[i] = body
        return f
    }

    fun page(): WPage {
        val s = body.child("w:sectPr"); val sz = s?.child("w:pgSz"); val m = s?.child("w:pgMar")
        fun g(n: XN?, a: String, d: Float) = (n?.attrs?.get(a)?.toFloatOrNull() ?: d) / 20f
        return WPage(g(sz, "w:w", 12240f).coerceIn(200f, 5000f), g(sz, "w:h", 15840f).coerceIn(200f, 5000f),
            g(m, "w:left", 1440f).coerceIn(0f, 400f), g(m, "w:right", 1440f).coerceIn(0f, 400f), g(m, "w:top", 1440f).coerceIn(0f, 400f), g(m, "w:bottom", 1440f).coerceIn(0f, 400f))
    }
    private fun sect(): XN = body.child("w:sectPr") ?: XN("w:sectPr").also { body.kids.add(it) }
    fun toggleOrientation() {
        val s = sect(); val pg = s.child("w:pgSz") ?: XN("w:pgSz", lhm("w:w" to "12240", "w:h" to "15840")).also { s.kids.add(0, it) }
        val w = pg.attrs["w:w"] ?: "12240"; val h = pg.attrs["w:h"] ?: "15840"
        pg.attrs["w:w"] = h; pg.attrs["w:h"] = w
        if ((h.toFloatOrNull() ?: 0f) > (w.toFloatOrNull() ?: 0f)) pg.attrs["w:orient"] = "landscape" else pg.attrs.remove("w:orient")
    }
    fun setMargins(tw: Int) {
        val s = sect(); val m = s.child("w:pgMar") ?: XN("w:pgMar").also { s.kids.add(minOf(1, s.kids.size), it) }
        for (a in listOf("w:top", "w:right", "w:bottom", "w:left")) m.attrs[a] = tw.toString()
        m.attrs.putIfAbsent("w:header", "720"); m.attrs.putIfAbsent("w:footer", "720"); m.attrs.putIfAbsent("w:gutter", "0")
    }

    // ---- editing -------------------------------------------------------------------------

    fun setParaText(p: XN, parent: XN, text: String) {
        val lines = text.split('\n')
        val rs = runsOf(p)
        val rPr = (rs.firstOrNull { runText(it).isNotEmpty() } ?: rs.firstOrNull())?.child("w:rPr")
        val pPr = p.child("w:pPr")
        val keep = p.kids.filter { it.name == "w:r" && hasDrawing(it) }
        // shortcut: hyperlinks / bookmarks / fields inside an edited paragraph are flattened to plain runs
        p.kids.clear(); pPr?.let { p.kids.add(it) }
        // Enter inside a paragraph is "\n" from the text field: it splits into new paragraphs
        p.kids.add(makeRun(rPr, lines[0])); p.kids.addAll(keep)
        var at = parent.kids.indexOf(p)
        for (l in lines.drop(1)) {
            val np = XN("w:p"); pPr?.let { np.kids.add(it.copy().also { c -> c.kids.removeAll { k -> k.name == "w:sectPr" } }) }
            np.kids.add(makeRun(rPr, l)); parent.kids.add(++at, np)
        }
    }

    private fun rprOf(r: XN): XN = r.child("w:rPr") ?: XN("w:rPr").also { r.kids.add(0, it) }
    private fun pprOf(p: XN): XN = p.child("w:pPr") ?: XN("w:pPr").also { p.kids.add(0, it) }
    private fun textRuns(p: XN) = runsOf(p).filter { !hasDrawing(it) }

    fun toggle(p: XN, tag: String) {
        val rs = textRuns(p); if (rs.isEmpty()) return
        val on = rs.all { r -> effRp(p, r).let { if (tag == "w:b") it.b else if (tag == "w:i") it.i else if (tag == "w:strike") it.strike else it.u } == true }
        for (r in rs) rprOf(r).put(RPR_ORDER, if (tag == "w:u") XN("w:u", lhm("w:val" to if (on) "none" else "single")) else XN(tag, if (on) lhm("w:val" to "0") else lhm()))
    }
    fun isOn(p: XN, tag: String): Boolean {
        val rs = textRuns(p); if (rs.isEmpty()) return false
        return rs.all { r -> effRp(p, r).let { if (tag == "w:b") it.b else if (tag == "w:i") it.i else if (tag == "w:strike") it.strike else it.u } == true }
    }
    fun changeSize(p: XN, delta: Int) {
        for (r in textRuns(p)) {
            val n = ((effRp(p, r).sz ?: 22) + delta).coerceIn(2, 400)
            rprOf(r).apply { put(RPR_ORDER, XN("w:sz", lhm("w:val" to "$n"))); put(RPR_ORDER, XN("w:szCs", lhm("w:val" to "$n"))) }
        }
    }
    fun setJc(p: XN, v: String) = pprOf(p).put(PPR_ORDER, XN("w:jc", lhm("w:val" to v)))

    /** returns false when the style cannot be defined (no styles.xml) */
    fun setStyle(p: XN, id: String) {
        val pPr = pprOf(p)
        if (id == "Normal") { pPr.kids.removeAll { it.name == "w:pStyle" }; return }
        ensureStyle(id)
        pPr.put(PPR_ORDER, XN("w:pStyle", lhm("w:val" to id)))
    }
    private fun ensureStyle(id: String) {
        val s = stylesRoot ?: return
        if (styles.containsKey(id)) return
        val (name, sz, color, extra) = when (id) {
            "Heading1" -> listOf("heading 1", "32", "2F5496", "240"); "Heading2" -> listOf("heading 2", "26", "2F5496", "40")
            "Heading3" -> listOf("heading 3", "24", "1F3763", "40"); else -> listOf("Title", "56", "", "0")
        }
        val st = XN("w:style", lhm("w:type" to "paragraph", "w:styleId" to id))
        st.kids.add(XN("w:name", lhm("w:val" to name))); st.kids.add(XN("w:basedOn", lhm("w:val" to "Normal"))); st.kids.add(XN("w:next", lhm("w:val" to "Normal"))); st.kids.add(XN("w:qFormat"))
        st.kids.add(XN("w:pPr").also { it.kids.add(XN("w:keepNext")); it.kids.add(XN("w:spacing", lhm("w:before" to extra, "w:after" to "0"))) })
        st.kids.add(XN("w:rPr").also { r -> if (color.isNotEmpty()) r.kids.add(XN("w:color", lhm("w:val" to color))); r.kids.add(XN("w:sz", lhm("w:val" to sz))) })
        s.kids.add(st); stylesDirty = true; indexStyles()
    }

    fun toggleBullet(p: XN, parent: XN) {
        val pPr = pprOf(p)
        val has = pPr.child("w:numPr")?.child("w:numId")?.v().let { it != null && it != "0" }
        val t = paraText(p)
        when {
            has -> pPr.kids.removeAll { it.name == "w:numPr" }
            t.startsWith("• ") -> setParaText(p, parent, t.substring(2))
            bulletNumId != null -> pPr.put(PPR_ORDER, XN("w:numPr").also { it.kids.add(XN("w:ilvl", lhm("w:val" to "0"))); it.kids.add(XN("w:numId", lhm("w:val" to bulletNumId!!))) })
            else -> setParaText(p, parent, "• $t") // shortcut: no bullet definition in numbering.xml, a literal bullet is typed
        }
    }
    fun isBullet(p: XN) = p.child("w:pPr")?.child("w:numPr") != null || paraText(p).startsWith("• ")

    fun insertParaAfter(p: XN, parent: XN) {
        val np = XN("w:p"); p.child("w:pPr")?.let { np.kids.add(it.copy().also { c -> c.kids.removeAll { k -> k.name == "w:sectPr" } }) }
        parent.kids.add(parent.kids.indexOf(p) + 1, np)
    }
    fun insertBreakAfter(p: XN, parent: XN) {
        val np = XN("w:p"); np.kids.add(XN("w:r").also { it.kids.add(XN("w:br", lhm("w:type" to "page"))) })
        parent.kids.add(parent.kids.indexOf(p) + 1, np)
    }
    fun insertTableAfter(p: XN, parent: XN, rows: Int = 3, cols: Int = 3) {
        val t = XN("w:tbl")
        val cw = (page().let { (it.w - it.l - it.r) * 20f }.toInt().coerceAtLeast(1440) / cols).coerceAtLeast(300)
        t.kids.add(XN("w:tblPr").also { pr ->
            pr.kids.add(XN("w:tblStyle", lhm("w:val" to "TableGrid")))
            pr.kids.add(XN("w:tblW", lhm("w:w" to "${cw * cols}", "w:type" to "dxa")))
            pr.kids.add(XN("w:tblBorders").also { b -> for (s in listOf("top", "left", "bottom", "right", "insideH", "insideV")) b.kids.add(XN("w:$s", lhm("w:val" to "single", "w:sz" to "4", "w:space" to "0", "w:color" to "auto"))) })
        })
        t.kids.add(XN("w:tblGrid").also { g -> repeat(cols) { g.kids.add(XN("w:gridCol", lhm("w:w" to "$cw"))) } })
        repeat(rows) { t.kids.add(XN("w:tr").also { tr -> repeat(cols) { tr.kids.add(XN("w:tc").also { tc -> tc.kids.add(XN("w:tcPr").also { it.kids.add(XN("w:tcW", lhm("w:w" to "$cw", "w:type" to "dxa"))) }); tc.kids.add(XN("w:p")) }) } }) }
        val at = parent.kids.indexOf(p) + 1
        parent.kids.add(at, t); parent.kids.add(at + 1, XN("w:p")) // Word wants a paragraph after a table
    }
    fun deletePara(p: XN, parent: XN) {
        parent.kids.remove(p)
        if (parent.name == "w:tc" && parent.kids.none { it.name == "w:p" }) parent.kids.add(XN("w:p"))
    }

    // ---- undo, save ------------------------------------------------------------------------

    fun serialize(): String = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n").also { root.write(it) }.toString()
    fun pushUndo() {
        redoS.clear(); val s = serialize()
        if (s.length > 6_000_000) { undoS.clear(); return } // shortcut: huge documents get no undo history
        undoS.add(s); if (undoS.size > 30) undoS.removeAt(0)
    }
    fun undo(): Boolean { if (undoS.isEmpty()) return false; redoS.add(serialize()); root = xParse(undoS.removeAt(undoS.size - 1).byteInputStream()); return true }
    fun redo(): Boolean { if (redoS.isEmpty()) return false; undoS.add(serialize()); root = xParse(redoS.removeAt(redoS.size - 1).byteInputStream()); return true }

    fun plain(): String = flatten().ps.joinToString("\n") { paraText(it) }

    /** captures the current content now (UI thread) and returns the writer to run later on another thread */
    private fun ser(r: XN) = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n").also { r.write(it) }.toString().toByteArray()

    fun writer(): (File) -> Unit {
        if (fmt == "txt") { val t = plain(); return { it.writeText(t) } }
        if (converted) {
            if (body.child("w:sectPr") == null) body.kids.add(XN("w:sectPr").also { sp -> sp.kids.add(XN("w:pgSz", lhm("w:w" to "11906", "w:h" to "16838"))); sp.kids.add(XN("w:pgMar", lhm("w:top" to "1440", "w:right" to "1440", "w:bottom" to "1440", "w:left" to "1440", "w:header" to "708", "w:footer" to "708", "w:gutter" to "0"))) })
            val dx = serialize().toByteArray()
            return { out ->
                // a fresh blank .docx supplies styles / numbering / rels; only document.xml is replaced
                val blank = File(out.parentFile, "blank_${System.nanoTime()}.docx")
                try {
                    ofNewDocx(blank)
                    ZipFile(blank).use { z -> ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { o ->
                        for (e in z.entries()) { o.putNextEntry(ZipEntry(e.name)); if (e.name == "word/document.xml") o.write(dx) else z.getInputStream(e).use { it.copyTo(o) }; o.closeEntry() }
                    } }
                } finally { blank.delete() }
            }
        }
        if (fmt != "docx") throw IOException("Saving ${fmt.uppercase()} files is not supported, use a .docx")
        if (partial) throw IOException("Damaged document, saving would lose content")
        val xml = serialize().toByteArray(); val sx = if (stylesDirty) stylesRoot?.let { ser(it) } else null
        val rx = if (relsDirty) relsRoot?.let { ser(it) } else null; val cx = if (ctDirty) ctRoot?.let { ser(it) } else null
        val mm = ArrayList(media)
        return { out ->
            ZipFile(file).use { z ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { o ->
                    val seen = HashSet<String>()
                    fun put(n: String, b: ByteArray) { o.putNextEntry(ZipEntry(n)); o.write(b); o.closeEntry() }
                    for (e in z.entries()) {
                        if (!seen.add(e.name)) continue // duplicate entries in the source zip
                        o.putNextEntry(ZipEntry(e.name))
                        if (e.name == docName) o.write(xml)
                        else if (e.name == stylesName && sx != null) o.write(sx)
                        else if (e.name == relsName && rx != null) o.write(rx)
                        else if (e.name == ctName && cx != null) o.write(cx)
                        else if (!e.isDirectory) z.getInputStream(e).use { it.copyTo(o) }
                        o.closeEntry()
                    }
                    if (rx != null && seen.add(relsName)) put(relsName, rx)
                    for ((n, b) in mm) if (seen.add(n)) put(n, b)
                }
            }
        }
    }

    fun imageBytes(rId: String): ByteArray? {
        val path = rels[rId] ?: return null
        media.firstOrNull { it.first == path }?.let { return it.second }
        return runCatching { ZipFile(file).use { z -> zEntry(z, path)?.let { e -> z.getInputStream(e).use { it.readBytes() } } } }.getOrNull()
    }

    /** embeds [bytes] (png / jpeg) as word/media + relationship and appends an inline picture run to [p] */
    fun addImage(p: XN, bytes: ByteArray, ext: String, wPt: Float, hPt: Float) {
        val rr = relsRoot ?: throw IOException("No relationships part")
        val dir = docName.substringBeforeLast('/', "")
        var n = 1; while ("${if (dir.isEmpty()) "" else "$dir/"}media/pulse$n.$ext" in zipNames || media.any { it.first.endsWith("pulse$n.$ext") }) n++
        val path = "${if (dir.isEmpty()) "" else "$dir/"}media/pulse$n.$ext"
        var k = 1; while (rr.kids.any { it.attrs["Id"] == "rIdP$k" }) k++
        val id = "rIdP$k"
        rr.kids.add(XN("Relationship", lhm("Id" to id, "Type" to "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image", "Target" to path.removePrefix(if (dir.isEmpty()) "" else "$dir/"))))
        rels[id] = path; media.add(path to bytes); relsDirty = true
        ctRoot?.let { c -> if (c.kids.none { it.name == "Default" && it.attrs["Extension"].equals(ext, true) }) { c.kids.add(0, XN("Default", lhm("Extension" to ext, "ContentType" to if (ext == "png") "image/png" else "image/jpeg"))); ctDirty = true } }
        val cx = "${(wPt * 12700).toLong()}"; val cy = "${(hPt * 12700).toLong()}"
        val pic = XN("pic:pic", lhm("xmlns:pic" to "http://schemas.openxmlformats.org/drawingml/2006/picture")).also { x ->
            x.kids.add(XN("pic:nvPicPr").also { it.kids.add(XN("pic:cNvPr", lhm("id" to "0", "name" to "pulse$n.$ext"))); it.kids.add(XN("pic:cNvPicPr")) })
            x.kids.add(XN("pic:blipFill").also { it.kids.add(XN("a:blip", lhm("xmlns:r" to "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "r:embed" to id))); it.kids.add(XN("a:stretch").also { s2 -> s2.kids.add(XN("a:fillRect")) }) })
            x.kids.add(XN("pic:spPr").also { sp ->
                sp.kids.add(XN("a:xfrm").also { it.kids.add(XN("a:off", lhm("x" to "0", "y" to "0"))); it.kids.add(XN("a:ext", lhm("cx" to cx, "cy" to cy))) })
                sp.kids.add(XN("a:prstGeom", lhm("prst" to "rect")).also { it.kids.add(XN("a:avLst")) })
            })
        }
        val inl = XN("wp:inline", lhm("xmlns:wp" to "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing", "distT" to "0", "distB" to "0", "distL" to "0", "distR" to "0")).also { x ->
            x.kids.add(XN("wp:extent", lhm("cx" to cx, "cy" to cy)))
            x.kids.add(XN("wp:docPr", lhm("id" to "${1000 + n}", "name" to "Picture $n")))
            x.kids.add(XN("a:graphic", lhm("xmlns:a" to "http://schemas.openxmlformats.org/drawingml/2006/main")).also { g ->
                g.kids.add(XN("a:graphicData", lhm("uri" to "http://schemas.openxmlformats.org/drawingml/2006/picture")).also { it.kids.add(pic) })
            })
        }
        p.kids.add(XN("w:r").also { it.kids.add(XN("w:drawing").also { d -> d.kids.add(inl) }) })
    }

    /** scales every picture of [p] */
    fun scaleImages(p: XN, f: Float) {
        fun go(n: XN) {
            if (n.name == "wp:extent" || n.name == "a:ext") for (a in listOf("cx", "cy")) n.attrs[a]?.toDoubleOrNull()?.let { n.attrs[a] = "${(it * f).toLong().coerceAtLeast(12700L)}" }
            n.kids.forEach { go(it) }
        }
        runsOf(p).forEach { r -> r.descendant("w:drawing")?.let { go(it) } }
    }

    // ---- character formatting ---------------------------------------------------------------

    fun setSize(p: XN, half: Int) { for (r in textRuns(p)) rprOf(r).apply { put(RPR_ORDER, XN("w:sz", lhm("w:val" to "$half"))); put(RPR_ORDER, XN("w:szCs", lhm("w:val" to "$half"))) } }
    fun setColor(p: XN, hex: String?) { for (r in textRuns(p)) rprOf(r).put(RPR_ORDER, XN("w:color", lhm("w:val" to (hex ?: "auto")))) }
    fun setFont(p: XN, name: String) { for (r in textRuns(p)) rprOf(r).put(RPR_ORDER, XN("w:rFonts", lhm("w:ascii" to name, "w:hAnsi" to name, "w:cs" to name))) }
    fun clearFormat(p: XN) { for (r in textRuns(p)) r.kids.removeAll { it.name == "w:rPr" } }
    fun indent(p: XN, delta: Int) {
        val a = LinkedHashMap<String, String>(); p.child("w:pPr")?.child("w:ind")?.let { a.putAll(it.attrs) }
        val cur = (a["w:left"] ?: a["w:start"])?.toIntOrNull() ?: 0; a.remove("w:start"); a["w:left"] = (cur + delta).coerceAtLeast(0).toString()
        pprOf(p).put(PPR_ORDER, XN("w:ind", a))
    }
    fun setHighlight(p: XN, name: String?) { for (r in textRuns(p)) { val x = rprOf(r); if (name == null) x.kids.removeAll { it.name == "w:highlight" } else x.put(RPR_ORDER, XN("w:highlight", lhm("w:val" to name))) } }

    // ---- table editing ----------------------------------------------------------------------

    private fun pathTo(n: XN, t: XN, path: ArrayList<XN>): Boolean { path.add(n); if (n === t) return true; for (k in n.kids) if (pathTo(k, t, path)) return true; path.removeAt(path.size - 1); return false }
    fun inCell(p: XN): Boolean = cellInfo(p) != null
    private fun cellInfo(p: XN): Triple<XN, XN, XN>? {
        val path = ArrayList<XN>(); if (!pathTo(body, p, path)) return null
        val i = path.indexOfLast { it.name == "w:tc" }; if (i < 1) return null
        val tr = path.subList(0, i).lastOrNull { it.name == "w:tr" } ?: return null
        val tbl = path.subList(0, i).lastOrNull { it.name == "w:tbl" } ?: return null
        return Triple(tbl, tr, path[i])
    }
    private fun newCell(like: XN, wTw: String?): XN = XN("w:tc").also { tc ->
        like.child("w:tcPr")?.copy()?.let { pr -> pr.kids.removeAll { it.name == "w:vMerge" || (wTw != null && (it.name == "w:gridSpan" || it.name == "w:tcW")) }; if (wTw != null) pr.kids.add(0, XN("w:tcW", lhm("w:w" to wTw, "w:type" to "dxa"))); tc.kids.add(pr) }
        tc.kids.add(XN("w:p"))
    }
    private fun fixCells(n: XN) { if (n.name == "w:tc" && n.kids.none { it.name == "w:p" }) n.kids.add(XN("w:p")); n.kids.forEach { fixCells(it) } }

    /** op: rowAfter, rowDel, colAfter, colDel for the table around paragraph [p] */
    fun tableOp(p: XN, op: String) {
        val (tbl, tr, tc) = cellInfo(p) ?: return
        val col = cellsOf(tr).takeWhile { it !== tc }.sumOf { spanOf(it) } + (tr.child("w:trPr")?.child("w:gridBefore")?.v()?.toIntOrNull() ?: 0)
        val gridN = tbl.child("w:tblGrid")
        val gcols = gridN?.kids?.filter { it.name == "w:gridCol" } ?: emptyList()
        fun covering(r: XN): XN? { var s = r.child("w:trPr")?.child("w:gridBefore")?.v()?.toIntOrNull() ?: 0; for (c in cellsOf(r)) { val e = s + spanOf(c); if (col in s until e) return c; s = e }; return null }
        when (op) {
            "rowAfter" -> {
                val nr = XN("w:tr"); tr.child("w:trPr")?.copy()?.let { pr -> pr.kids.removeAll { it.name == "w:tblHeader" }; nr.kids.add(pr) }
                cellsOf(tr).forEach { nr.kids.add(newCell(it, null)) }
                tbl.addAfter(tr, nr)
            }
            "rowDel" -> { body.removeDeep(tr); if (rowsOf(tbl).isEmpty()) body.removeDeep(tbl) }
            "colAfter" -> {
                val wd = gcols.getOrNull(col)?.v("w:w") ?: tc.child("w:tcPr")?.child("w:tcW")?.v("w:w") ?: "1440"
                for (r in rowsOf(tbl)) covering(r)?.let { c -> tbl.addAfter(c, newCell(c, wd)) }
                gcols.getOrNull(col)?.let { gridN?.addAfter(it, XN("w:gridCol", lhm("w:w" to wd))) }
            }
            "colDel" -> {
                for (r in rowsOf(tbl)) covering(r)?.let { c ->
                    val sp = spanOf(c)
                    if (sp > 1) c.child("w:tcPr")?.child("w:gridSpan")?.attrs?.put("w:val", "${sp - 1}") else body.removeDeep(c)
                    if (cellsOf(r).isEmpty()) body.removeDeep(r)
                }
                gcols.getOrNull(col)?.let { gridN?.kids?.remove(it) }
                if (rowsOf(tbl).isEmpty()) body.removeDeep(tbl)
            }
        }
        fixCells(body)
    }

    /** rough page start offsets (pt) of each top-level block, for "Page x of y" */
    fun estimate(blocks: List<XN>, pg: WPage): FloatArray {
        val tw = (pg.w - pg.l - pg.r).coerceAtLeast(50f); val ph = (pg.h - pg.t - pg.b).coerceAtLeast(50f)
        fun paraH(p: XN, w: Float): Float {
            val pp = pp(p); val sz = (firstRp(p).sz ?: 22) / 2f
            val cpl = ((w - pp.left / 20f) / (sz * .5f)).coerceAtLeast(4f)
            val t = paraText(p)
            val lines = t.split('\n').sumOf { Math.ceil((it.length / cpl).toDouble()).toInt().coerceAtLeast(1) }
            return lines * sz * 1.2f * pp.line + (pp.before + pp.after) / 20f + imagesOf(p).sumOf { minOf(it.third, ph).toDouble() }.toFloat()
        }
        fun h(n: XN, w: Float): Float = when (n.name) {
            "w:p" -> paraH(n, w)
            "w:tbl" -> rowsOf(n).sumOf { tr -> (cellsOf(tr).maxOfOrNull { tc -> tc.kids.sumOf { h(it, w / 3f).toDouble() }.toFloat() } ?: 14f).toDouble() }.toFloat()
            else -> contOf(n)?.kids?.sumOf { h(it, w).toDouble() }?.toFloat() ?: 0f
        }
        val cum = FloatArray(blocks.size + 1); var y = 0f
        for ((i, b) in blocks.withIndex()) {
            cum[i] = y
            y += h(b, tw)
            if (b.name == "w:p" && hasPageBreak(b)) y = (Math.floor((y / ph).toDouble()) + 1).toFloat() * ph
        }
        cum[blocks.size] = y; return cum
    }
}

fun rtfText(s: String): String {
    val sb = StringBuilder(); var i = 0; var depth = 0; var skipDepth = -1
    val skipWords = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "object", "themedata", "colorschememapping", "latentstyles", "datastore", "listtable", "listoverridetable", "rsidtbl", "generator")
    while (i < s.length) {
        val c = s[i]
        when (c) {
            '{' -> { depth++; if (s.startsWith("{\\*", i)) { if (skipDepth < 0) skipDepth = depth } }
            '}' -> { if (depth == skipDepth) skipDepth = -1; depth-- }
            '\\' -> {
                i++; if (i >= s.length) break
                val d = s[i]
                if (d == '\'') { val h = s.substring(i + 1, minOf(i + 3, s.length)).toIntOrNull(16); if (h != null && skipDepth < 0) sb.append(String(byteArrayOf(h.toByte()), charset("windows-1252"))); i += 2 }
                else if (d == '\\' || d == '{' || d == '}') { if (skipDepth < 0) sb.append(d) }
                else if (d.isLetter()) {
                    var j = i; while (j < s.length && s[j].isLetter()) j++
                    val w = s.substring(i, j); var k = j; if (k < s.length && (s[k] == '-' || s[k].isDigit())) { k++; while (k < s.length && s[k].isDigit()) k++ }
                    val arg = s.substring(j, k).toIntOrNull()
                    if (w in skipWords && skipDepth < 0) skipDepth = depth
                    if (skipDepth < 0) when (w) {
                        "par", "line", "sect", "page" -> sb.append('\n'); "tab" -> sb.append('\t')
                        "u" -> if (arg != null) { sb.append((if (arg < 0) arg + 65536 else arg).toChar()); if (k < s.length && s[k] == '?') k++ }
                    }
                    i = if (k < s.length && s[k] == ' ') k else k - 1
                }
            }
            '\r', '\n' -> {}
            else -> if (skipDepth < 0) sb.append(c)
        }
        i++
    }
    return sb.toString()
}

/** best effort for the binary .doc: pull the readable text runs out of the file */
fun docText(b: ByteArray): String {
    val out = StringBuilder(); val seg = StringBuilder()
    fun flush() { if (seg.length >= 8 && seg.count { it.isLetter() } * 10 >= seg.length * 6) { out.append(seg).append('\n') }; seg.setLength(0) }
    for (i in b.indices) {
        val v = b[i].toInt() and 0xFF
        when {
            v == 13 || v == 7 -> { if (seg.isNotEmpty()) seg.append('\n') }
            v == 0 && i > 0 && i + 1 < b.size && (b[i - 1].toInt() and 0xFF) >= 32 && (b[i + 1].toInt() and 0xFF) >= 32 -> {}
            v in 32..126 -> seg.append(v.toChar())
            v >= 160 -> seg.append(String(byteArrayOf(b[i]), charset("windows-1252")))
            v == 9 -> seg.append('\t')
            else -> flush()
        }
    }
    flush(); return out.toString().trim()
}
