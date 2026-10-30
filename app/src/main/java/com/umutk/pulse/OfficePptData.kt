package com.umutk.pulse

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// PowerPoint model: the slides are kept as DOM documents; the render model is rebuilt from them, edits change the DOM, save rewrites only touched parts.

const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
const val NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
const val NS_CT = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val CT_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"
private const val REL_SLIDE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"
private const val REL_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
private const val REL_LAYOUT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout"

fun Element.kids(name: String? = null): List<Element> {
    val l = ArrayList<Element>(); var n = firstChild
    while (n != null) { if (n is Element && (name == null || n.localName == name)) l.add(n); n = n.nextSibling }
    return l
}
fun Element.k(vararg path: String): Element? { var e: Element? = this; for (p in path) e = e?.kids(p)?.firstOrNull(); return e }
private fun Element.num(a: String) = getAttribute(a).toFloatOrNull()

fun pptDom(b: ByteArray): Document = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
}.newDocumentBuilder().parse(b.inputStream())

fun pptBytes(d: Document): ByteArray = ByteArrayOutputStream().also { TransformerFactory.newInstance().newTransformer().transform(DOMSource(d), StreamResult(it)) }.toByteArray()

fun pptResolve(base: String, t: String): String {
    if (t.startsWith("/")) return t.substring(1)
    val parts = base.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
    for (s in t.split('/')) when (s) { "", "." -> {}; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1); else -> parts.add(s) }
    return parts.joinToString("/")
}
fun pptRelsPath(part: String) = part.substringBeforeLast('/', "") + "/_rels/" + part.substringAfterLast('/') + ".rels"

class PRun(val text: String, val bold: Boolean, val italic: Boolean, val under: Boolean, val sizePt: Float, val color: Int, val hl: Int? = null)
class PPara(val runs: List<PRun>, val align: Int, val bullet: Boolean)
class PCell(val paras: List<PPara>, val fill: Int?)
class PShape(
    val el: Element, val kind: String, val x: Float, val y: Float, val w: Float, val h: Float, val rot: Float, val geom: String,
    val fill: Int?, val line: Int?, val lineW: Float, val paras: List<PPara>, val anchor: Int, val ins: FloatArray,
    val media: String?, val table: List<List<PCell>>?, val colW: List<Float>, val rowH: List<Float>, val ph: Boolean,
)
class PRender(val bg: Int, val shapes: List<PShape>)
class PptSlide(var part: String, var rId: String, var sid: Int, var isNew: Boolean)

private class Ctx(val layout: Document?, val master: Document?, val colors: Map<String, Int>, val map: Map<String, String>)

class PptModel(val file: File) {
    private val zip = ZipFile(file)
    val names: MutableSet<String> = zip.entries().asSequence().map { it.name }.toMutableSet()
    private val docs = HashMap<String, Document?>()
    val slides = mutableListOf<PptSlide>()
    val touched = linkedSetOf<String>()
    private val removed = linkedSetOf<String>()
    var cx = 9144000f; var cy = 5143500f
    private val presPart: String
    // about 16 MB of slide pictures, by bytes
    private val bmps = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val ctxs = HashMap<String, Ctx>()
    private val blobs = LinkedHashMap<String, ByteArray>()
    private val newExts = linkedSetOf<String>()
    var clip: Element? = null
    private var clipMedia: String? = null

    fun bytes(n: String): ByteArray? = blobs[n] ?: runCatching { zip.getEntry(n)?.takeIf { it.size < 40_000_000 }?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }.getOrNull()
    fun doc(n: String): Document? = if (docs.containsKey(n)) docs[n] else (bytes(n)?.let { runCatching { pptDom(it) }.getOrNull() }).also { docs[n] = it }
    fun rels(part: String): List<Triple<String, String, String>> =
        doc(pptRelsPath(part))?.documentElement?.kids("Relationship")?.filter { it.getAttribute("TargetMode") != "External" }
            ?.map { Triple(it.getAttribute("Id"), it.getAttribute("Type"), pptResolve(part, it.getAttribute("Target"))) } ?: emptyList()

    init {
        presPart = doc("_rels/.rels")?.documentElement?.kids("Relationship")?.firstOrNull { it.getAttribute("Type").endsWith("/officeDocument") }
            ?.getAttribute("Target")?.let { pptResolve("x", it) } ?: "ppt/presentation.xml"
        val p = doc(presPart)?.documentElement ?: throw java.io.IOException("not a presentation")
        p.k("sldSz")?.let { s -> s.num("cx")?.let { cx = it }; s.num("cy")?.let { cy = it } }
        val rl = rels(presPart)
        p.k("sldIdLst")?.kids("sldId")?.forEach { s ->
            val rid = s.getAttributeNS(NS_R, "id")
            rl.firstOrNull { it.first == rid }?.let { slides.add(PptSlide(it.third, rid, s.getAttribute("id").toIntOrNull() ?: 256, false)) }
        }
        if (slides.isEmpty()) throw java.io.IOException("no slides")
    }

    fun sdoc(i: Int): Document = slides[i].part.let { doc(it) ?: pptDom(blankSlide().toByteArray()).also { d -> docs[it] = d } }

    // ---- theme / context ----
    private fun ctxOf(slide: PptSlide): Ctx = ctxs.getOrPut(slide.part) {
        val lp = rels(slide.part).firstOrNull { it.second == REL_LAYOUT }?.third
        val mp = lp?.let { l -> rels(l).firstOrNull { it.second.endsWith("/slideMaster") }?.third }
        val md = mp?.let { doc(it) }
        val tp = mp?.let { m -> rels(m).firstOrNull { it.second.endsWith("/theme") }?.third }
        val cols = HashMap<String, Int>()
        doc(tp ?: "")?.documentElement?.k("themeElements", "clrScheme")?.kids()?.forEach { c ->
            val v = c.kids().firstOrNull()
            val s = v?.getAttribute("val")?.takeIf { v?.localName == "srgbClr" } ?: v?.getAttribute("lastClr")
            s?.toLongOrNull(16)?.let { cols[c.localName] = (0xFF000000 or it).toInt() }
        }
        val cm = mutableMapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2")
        md?.documentElement?.k("clrMap")?.let { e -> for (i in 0 until e.attributes.length) e.attributes.item(i).let { cm[it.nodeName] = it.nodeValue } }
        Ctx(lp?.let { doc(it) }, md, cols, cm)
    }

    private fun clr(c: Ctx, e: Element?): Int? {
        val v = e?.kids()?.firstOrNull { it.localName in setOf("srgbClr", "schemeClr", "sysClr") } ?: return null
        var c0: Int = when (v.localName) {
            "srgbClr" -> v.getAttribute("val").toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
            "sysClr" -> v.getAttribute("lastClr").toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
            else -> v.getAttribute("val").let { n -> c.colors[c.map[n] ?: n] ?: if (n == "phClr") 0xFF4472C4.toInt() else null }
        } ?: return null
        v.kids().forEach { m ->
            val q = (m.num("val") ?: 100000f) / 100000f
            when (m.localName) {
                "lumMod" -> c0 = hsl(c0, q, 0f)
                "lumOff" -> c0 = hsl(c0, 1f, q)
                "tint" -> c0 = mix(c0, 0xFFFFFFFF.toInt(), 1 - q)
                "shade" -> c0 = mix(c0, 0xFF000000.toInt(), 1 - q)
            }
        }
        return c0
    }
    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    private fun hsl(c: Int, mod: Float, off: Float): Int {
        val r = ((c shr 16) and 255) / 255f; val g = ((c shr 8) and 255) / 255f; val b = (c and 255) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        var l = (mx + mn) / 2; var h = 0f; var s = 0f
        if (d > 0f) {
            s = if (l > .5f) d / (2 - mx - mn) else d / (mx + mn)
            h = (when (mx) { r -> (g - b) / d + (if (g < b) 6 else 0); g -> (b - r) / d + 2; else -> (r - g) / d + 4 }) / 6
        }
        l = (l * mod + off).coerceIn(0f, 1f)
        fun f(p: Float, q: Float, t0: Float): Float { var t = t0; if (t < 0) t += 1; if (t > 1) t -= 1
            return when { t < 1f / 6 -> p + (q - p) * 6 * t; t < .5f -> q; t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6; else -> p } }
        val rr: Float; val gg: Float; val bb: Float
        if (s == 0f) { rr = l; gg = l; bb = l } else {
            val q = if (l < .5f) l * (1 + s) else l + s - l * s; val p = 2 * l - q
            rr = f(p, q, h + 1f / 3); gg = f(p, q, h); bb = f(p, q, h - 1f / 3)
        }
        return (0xFF shl 24) or ((rr * 255).toInt().coerceIn(0, 255) shl 16) or ((gg * 255).toInt().coerceIn(0, 255) shl 8) or (bb * 255).toInt().coerceIn(0, 255)
    }

    private fun bgOf(c: Ctx, d: Document?): Int? {
        val bg = d?.documentElement?.k("cSld", "bg") ?: return null
        return clr(c, bg.k("bgPr", "solidFill")) ?: clr(c, bg.k("bgRef"))
    }

    private fun shapesOf(d: Document?): List<Element> = d?.documentElement?.k("cSld", "spTree")?.kids("sp") ?: emptyList()
    private fun findPh(d: Document?, type: String, idx: String, master: Boolean): Element? {
        val norm = { t: String -> when (t) { "ctrTitle" -> "title"; "", "obj" -> "body"; else -> t } }
        return shapesOf(d).firstOrNull { s ->
            val ph = s.k("nvSpPr", "nvPr", "ph") ?: return@firstOrNull false
            if (!master && idx.isNotEmpty() && ph.getAttribute("idx") == idx) true
            else if (!master && idx.isNotEmpty()) false
            else norm(ph.getAttribute("type")) == norm(type)
        }
    }

    // ---- render ----
    fun render(i: Int): PRender = runCatching {
        val s = slides[i]; val c = ctxOf(s); val d = sdoc(i)
        val bg = bgOf(c, d) ?: bgOf(c, c.layout) ?: bgOf(c, c.master) ?: c.colors[c.map["bg1"]] ?: 0xFFFFFFFF.toInt()
        val out = ArrayList<PShape>()
        val rl = rels(s.part)
        d.documentElement.k("cSld", "spTree")?.let { walk(it, c, rl, s.part, out, { }) }
        PRender(bg, out)
    }.getOrElse { PRender(0xFFFFFFFF.toInt(), emptyList()) }

    private fun walk(tree: Element, c: Ctx, rl: List<Triple<String, String, String>>, part: String, out: MutableList<PShape>, tf: (FloatArray) -> Unit) {
        for (e in tree.kids()) when (e.localName) {
            "grpSp" -> {
                val x = e.k("grpSpPr", "xfrm")
                if (x == null) walk(e, c, rl, part, out, tf) else {
                    val ox = x.k("off")?.num("x") ?: 0f; val oy = x.k("off")?.num("y") ?: 0f
                    val ex = x.k("ext")?.num("cx") ?: 1f; val ey = x.k("ext")?.num("cy") ?: 1f
                    val cox = x.k("chOff")?.num("x") ?: ox; val coy = x.k("chOff")?.num("y") ?: oy
                    val cex = (x.k("chExt")?.num("cx") ?: ex).takeIf { it != 0f } ?: 1f; val cey = (x.k("chExt")?.num("cy") ?: ey).takeIf { it != 0f } ?: 1f
                    walk(e, c, rl, part, out) { r -> r[0] = ox + (r[0] - cox) * ex / cex; r[1] = oy + (r[1] - coy) * ey / cey; r[2] = r[2] * ex / cex; r[3] = r[3] * ey / cey; tf(r) }
                }
            }
            "sp", "cxnSp", "pic", "graphicFrame" -> runCatching { shape(e, c, rl, part, tf)?.let { out.add(it) } }
        }
    }

    private fun shape(e: Element, c: Ctx, rl: List<Triple<String, String, String>>, part: String, tf: (FloatArray) -> Unit): PShape? {
        val sp = if (e.localName == "graphicFrame") null else e.k("spPr")
        val ph = e.k("nvSpPr", "nvPr", "ph") ?: e.k("nvPicPr", "nvPr", "ph")
        val type = ph?.getAttribute("type") ?: ""; val idx = ph?.getAttribute("idx") ?: ""
        val lay = if (ph != null) findPh(c.layout, type, idx, false) else null
        val mas = if (ph != null) findPh(c.master, type, idx, true) else null
        val chain = listOfNotNull(e, lay, mas)
        val xf = (if (e.localName == "graphicFrame") e.k("xfrm") else null) ?: chain.firstNotNullOfOrNull { it.k("spPr", "xfrm") } ?: return null
        val r = floatArrayOf(xf.k("off")?.num("x") ?: 0f, xf.k("off")?.num("y") ?: 0f, xf.k("ext")?.num("cx") ?: 0f, xf.k("ext")?.num("cy") ?: 0f)
        tf(r)
        val rot = (xf.num("rot") ?: 0f) / 60000f
        val geom = sp?.k("prstGeom")?.getAttribute("prst") ?: chain.firstNotNullOfOrNull { it.k("spPr", "prstGeom") }?.getAttribute("prst") ?: if (e.localName == "cxnSp") "line" else "rect"
        // fill / line
        val fe = chain.firstNotNullOfOrNull { it.k("spPr")?.kids()?.firstOrNull { k -> k.localName in setOf("solidFill", "noFill", "gradFill", "blipFill", "pattFill") } }
        var fill: Int? = when (fe?.localName) {
            "solidFill" -> clr(c, fe); "gradFill" -> clr(c, fe?.k("gsLst", "gs")); else -> null
        }
        if (fe == null) e.k("style", "fillRef")?.takeIf { it.getAttribute("idx") != "0" }?.let { fill = clr(c, it) }
        val ln = chain.firstNotNullOfOrNull { it.k("spPr", "ln") }
        var line: Int? = if (ln?.k("noFill") != null) null else clr(c, ln?.k("solidFill"))
        if (ln == null || (ln.k("noFill") == null && ln.k("solidFill") == null)) e.k("style", "lnRef")?.takeIf { it.getAttribute("idx") != "0" }?.let { line = clr(c, it) }
        val lw = ln?.num("w") ?: 12700f
        // text
        val body = e.k("txBody")
        val bp = chain.firstNotNullOfOrNull { it.k("txBody", "bodyPr") }
        val scale = (chain.firstNotNullOfOrNull { it.k("txBody", "bodyPr", "normAutofit") }?.num("fontScale") ?: 100000f) / 100000f
        val ins = floatArrayOf(bp?.num("lIns") ?: 91440f, bp?.num("tIns") ?: 45720f, bp?.num("rIns") ?: 91440f, bp?.num("bIns") ?: 45720f)
        val anchor = when (chain.firstNotNullOfOrNull { it.k("txBody", "bodyPr")?.getAttribute("anchor")?.ifEmpty { null } }) { "ctr" -> 1; "b" -> 2; else -> 0 }
        val paras = body?.kids("p")?.map { para(it, chain, c, type, ph != null, scale) } ?: emptyList()
        // picture / table
        var media: String? = null
        if (e.localName == "pic") media = e.k("blipFill", "blip")?.getAttributeNS(NS_R, "embed")?.let { id -> rl.firstOrNull { it.first == id }?.third }
        var table: List<List<PCell>>? = null; var cw = emptyList<Float>(); var rh = emptyList<Float>()
        if (e.localName == "graphicFrame") {
            val t = e.k("graphic", "graphicData", "tbl")
            if (t != null) {
                cw = t.k("tblGrid")?.kids("gridCol")?.map { it.num("w") ?: 0f } ?: emptyList()
                val rows = t.kids("tr"); rh = rows.map { it.num("h") ?: 0f }
                table = rows.map { tr -> tr.kids("tc").map { tc -> PCell(tc.k("txBody")?.kids("p")?.map { para(it, listOf(tc), c, "", false, 1f) } ?: emptyList(), clr(c, tc.k("tcPr", "solidFill"))) } }
            } else return null
        }
        val kind = when { table != null -> "tbl"; media != null || e.localName == "pic" -> "pic"; else -> "sp" }
        return PShape(e, kind, r[0], r[1], r[2], r[3], rot, geom, fill, line, lw, paras, anchor, ins, media, table, cw, rh, ph != null)
    }

    private fun para(p: Element, chain: List<Element>, c: Ctx, type: String, isPh: Boolean, scale: Float): PPara {
        val pPr = p.k("pPr"); val lvl = (pPr?.getAttribute("lvl")?.toIntOrNull() ?: 0).coerceIn(0, 8)
        val ln = "lvl${lvl + 1}pPr"
        val styleName = when { !isPh -> "otherStyle"; type == "title" || type == "ctrTitle" -> "titleStyle"; else -> "bodyStyle" }
        val lv = chain.mapNotNull { it.k("txBody", "lstStyle", ln) ?: it.k("lstStyle", ln) } + listOfNotNull(c.master?.documentElement?.k("txStyles", styleName, ln))
        val defs = lv.mapNotNull { it.k("defRPr") }
        val algn = pPr?.getAttribute("algn")?.ifEmpty { null } ?: lv.firstNotNullOfOrNull { it.getAttribute("algn").ifEmpty { null } }
        val bullet = pPr?.k("buNone") == null && (pPr?.k("buChar") != null || (pPr?.k("buAutoNum") != null) || (isPh && type != "title" && type != "ctrTitle" && lv.any { it.k("buChar") != null }))
        val runs = ArrayList<PRun>()
        for (r in p.kids()) {
            val text = when (r.localName) { "r", "fld" -> r.k("t")?.textContent ?: ""; "br" -> "\n"; else -> continue }
            val rp = r.k("rPr")
            val sz = ((rp?.getAttribute("sz")?.toIntOrNull() ?: defs.firstNotNullOfOrNull { it.getAttribute("sz").toIntOrNull() } ?: 1800) / 100f) * scale
            val b = rp?.getAttribute("b")?.ifEmpty { null } ?: defs.firstNotNullOfOrNull { it.getAttribute("b").ifEmpty { null } }
            val ital = rp?.getAttribute("i")?.ifEmpty { null } ?: defs.firstNotNullOfOrNull { it.getAttribute("i").ifEmpty { null } }
            val col = clr(c, rp?.k("solidFill")) ?: defs.firstNotNullOfOrNull { clr(c, it.k("solidFill")) } ?: c.colors[c.map["tx1"]] ?: 0xFF000000.toInt()
            runs.add(PRun(text, b == "1" || b == "true", ital == "1" || ital == "true", rp?.getAttribute("u")?.let { u -> u.isNotEmpty() && u != "none" } ?: false, sz, col, clr(c, rp?.k("highlight"))))
        }
        return PPara(runs, when (algn) { "ctr" -> 1; "r" -> 2; else -> 0 }, bullet)
    }

    fun notes(i: Int): String = runCatching {
        val np = rels(slides[i].part).firstOrNull { it.second.endsWith("/notesSlide") }?.third ?: return ""
        doc(np)?.documentElement?.k("cSld", "spTree")?.kids("sp")?.filter { it.k("nvSpPr", "nvPr", "ph")?.getAttribute("type") == "body" }
            ?.joinToString("\n") { sp -> sp.k("txBody")?.kids("p")?.joinToString("\n") { p -> p.kids("r").joinToString("") { it.k("t")?.textContent ?: "" } } ?: "" } ?: ""
    }.getOrDefault("")

    fun bitmap(path: String): ImageBitmap? {
        bmps.get(path)?.let { return it }
        val b = bytes(path) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var s = 1; while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
        val bm = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        return bm.asImageBitmap().also { bmps.put(path, it) }
    }

    // ---- edits (all on the slide DOM) ----
    private fun touch(i: Int) { touched.add(slides[i].part); ctxs.remove(slides[i].part) }
    private fun mk(d: Document, ns: String, n: String) = d.createElementNS(ns, n)

    fun setText(i: Int, sp: Element, text: String) {
        val d = sdoc(i); val tb = sp.k("txBody") ?: return
        val old = tb.kids("p")
        val lines = text.split("\n")
        lines.forEachIndexed { n, line ->
            val src = old.getOrNull(n) ?: old.lastOrNull()
            val p = if (n < old.size) old[n] else (src?.cloneNode(true) as? Element ?: mk(d, NS_A, "a:p")).also { tb.appendChild(it) }
            val rp = (p.kids("r").firstOrNull() ?: src?.kids("r")?.firstOrNull())?.k("rPr")?.cloneNode(true) as? Element
            p.kids().filter { it.localName in setOf("r", "br", "fld") }.forEach { p.removeChild(it) }
            val r = mk(d, NS_A, "a:r"); rp?.let { r.appendChild(it) }
            r.appendChild(mk(d, NS_A, "a:t").also { it.textContent = line })
            val end = p.k("endParaRPr"); if (end != null) p.insertBefore(r, end) else p.appendChild(r)
        }
        for (n in lines.size until old.size) tb.removeChild(old[n])
        touch(i)
    }

    /** applies [f] to the rPr of every run of the shape (created when missing) */
    fun eachRun(i: Int, sp: Element, f: (Element) -> Unit) {
        val d = sdoc(i)
        sp.k("txBody")?.kids("p")?.forEach { p -> p.kids().filter { it.localName == "r" || it.localName == "fld" }.forEach { r ->
            val rp = r.k("rPr") ?: mk(d, NS_A, "a:rPr").also { r.insertBefore(it, r.firstChild) }
            f(rp)
        } }
        touch(i)
    }

    fun setFill(i: Int, sp: Element, rgb: Int) {
        val d = sdoc(i)
        val spPr = sp.k("spPr") ?: mk(d, NS_P, "p:spPr").also { sp.insertBefore(it, sp.k("txBody")) }
        spPr.kids().filter { it.localName in setOf("noFill", "solidFill", "gradFill", "blipFill", "pattFill") }.forEach { spPr.removeChild(it) }
        val f = mk(d, NS_A, "a:solidFill"); f.appendChild(mk(d, NS_A, "a:srgbClr").also { it.setAttribute("val", "%06X".format(rgb and 0xFFFFFF)) })
        val anchor = spPr.k("prstGeom") ?: spPr.k("custGeom") ?: spPr.k("xfrm")
        spPr.insertBefore(f, anchor?.nextSibling)
        if (spPr.k("prstGeom") == null && spPr.k("custGeom") == null) spPr.insertBefore(mk(d, NS_A, "a:prstGeom").also { g -> g.setAttribute("prst", "rect"); g.appendChild(mk(d, NS_A, "a:avLst")) }, f)
        touch(i)
    }

    fun setBackground(i: Int, rgb: Int) {
        val d = sdoc(i); val cs = d.documentElement.k("cSld") ?: return
        cs.k("bg")?.let { cs.removeChild(it) }
        val bg = mk(d, NS_P, "p:bg"); val pr = mk(d, NS_P, "p:bgPr"); val f = mk(d, NS_A, "a:solidFill")
        f.appendChild(mk(d, NS_A, "a:srgbClr").also { it.setAttribute("val", "%06X".format(rgb and 0xFFFFFF)) })
        pr.appendChild(f); pr.appendChild(mk(d, NS_A, "a:effectLst")); bg.appendChild(pr)
        cs.insertBefore(bg, cs.firstChild); touch(i)
    }

    fun addShape(i: Int, textBox: Boolean): Element? {
        val d = sdoc(i); val tree = d.documentElement.k("cSld", "spTree") ?: return null
        val id = (tree.kids("sp").maxOfOrNull { it.k("nvSpPr", "cNvPr")?.getAttribute("id")?.toIntOrNull() ?: 0 } ?: 1) + 1
        val w = (cx / 3).toLong(); val h = (cy / 5).toLong(); val x = ((cx - w) / 2).toLong(); val y = ((cy - h) / 2).toLong()
        val xml = """<p:sp xmlns:p="$NS_P" xmlns:a="$NS_A"><p:nvSpPr><p:cNvPr id="$id" name="${if (textBox) "TextBox" else "Rectangle"} $id"/><p:cNvSpPr${if (textBox) " txBox=\"1\"" else ""}/><p:nvPr/></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$w" cy="$h"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom>${if (textBox) "<a:noFill/>" else "<a:solidFill><a:srgbClr val=\"4472C4\"/></a:solidFill><a:ln w=\"12700\"><a:solidFill><a:srgbClr val=\"2F528F\"/></a:solidFill></a:ln>"}</p:spPr>
<p:txBody><a:bodyPr wrap="square" rtlCol="0"${if (textBox) "" else " anchor=\"ctr\""}/><a:lstStyle/><a:p>${if (!textBox) "<a:pPr algn=\"ctr\"/>" else ""}<a:r><a:rPr lang="en-US" sz="2000"/><a:t>${if (textBox) "Text" else ""}</a:t></a:r></a:p></p:txBody></p:sp>"""
        val e = d.importNode(pptDom(xml.toByteArray()).documentElement, true) as Element
        tree.appendChild(e); touch(i); return e
    }

    fun eachPara(i: Int, sp: Element, f: (Element) -> Unit) {
        val d = sdoc(i)
        sp.k("txBody")?.kids("p")?.forEach { p -> f(p.k("pPr") ?: mk(d, NS_A, "a:pPr").also { p.insertBefore(it, p.firstChild) }) }
        touch(i)
    }

    fun setBullet(i: Int, sp: Element, on: Boolean) = eachPara(i, sp) { pp ->
        pp.kids().filter { it.localName in setOf("buNone", "buChar", "buAutoNum", "buFont") }.forEach { pp.removeChild(it) }
        val b = mk(pp.ownerDocument, NS_A, if (on) "a:buChar" else "a:buNone"); if (on) b.setAttribute("char", "\u2022")
        pp.insertBefore(b, pp.kids().firstOrNull { it.localName in setOf("tabLst", "defRPr", "extLst") })
        pp.setAttribute("marL", if (on) "342900" else "0"); pp.setAttribute("indent", if (on) "-342900" else "0")
    }

    private fun parentTree(el: Element) = (el.parentNode as? Element)?.takeIf { it.localName == "spTree" }
    fun canMove(el: Element) = parentTree(el) != null

    fun remove(i: Int, el: Element) { el.parentNode?.removeChild(el); touch(i) }
    fun front(i: Int, el: Element) { el.parentNode?.appendChild(el); touch(i) }

    fun copy(i: Int, el: Element) {
        clip = el.cloneNode(true) as Element
        clipMedia = el.k("blipFill", "blip")?.getAttributeNS(NS_R, "embed")?.let { id -> rels(slides[i].part).firstOrNull { it.first == id }?.third }
    }

    private fun nextId(d: Document): Int {
        val l = d.getElementsByTagNameNS(NS_P, "cNvPr"); var m = 1
        for (n in 0 until l.length) (l.item(n) as Element).getAttribute("id").toIntOrNull()?.let { if (it > m) m = it }
        return m + 1
    }

    private fun addRel(i: Int, type: String, target: String): String {
        val rp = pptRelsPath(slides[i].part)
        val rd = doc(rp) ?: pptDom("""<Relationships xmlns="$NS_REL"/>""".toByteArray()).also { docs[rp] = it; names.add(rp) }
        val root = rd.documentElement; var n = 1
        while (root.kids("Relationship").any { it.getAttribute("Id") == "rIdP$n" }) n++
        root.appendChild(mk(rd, NS_REL, "Relationship").also { it.setAttribute("Id", "rIdP$n"); it.setAttribute("Type", type); it.setAttribute("Target", target) })
        touched.add(rp); return "rIdP$n"
    }

    /** pastes the clipboard shape into slide [i] (shifted a little); returns the new element */
    fun paste(i: Int): Element? {
        val c = clip ?: return null; val d = sdoc(i); val tree = d.documentElement.k("cSld", "spTree") ?: return null
        val e = d.importNode(c, true) as Element
        e.getElementsByTagNameNS(NS_P, "cNvPr").item(0)?.let { (it as Element).setAttribute("id", nextId(d).toString()) }
        e.getElementsByTagNameNS(NS_A, "off").item(0)?.let { o -> (o as Element).let { it.setAttribute("x", ((it.num("x") ?: 0f) + 228600).toLong().toString()); it.setAttribute("y", ((it.num("y") ?: 0f) + 228600).toLong().toString()) } }
        val mp = clipMedia; val blip = e.k("blipFill", "blip")
        if (mp != null && blip != null) {
            val rid = rels(slides[i].part).firstOrNull { it.third == mp && it.second.endsWith("/image") }?.first ?: addRel(i, REL_IMAGE, "/$mp")
            blip.setAttributeNS(NS_R, "r:embed", rid)
        }
        tree.appendChild(e); touch(i); return e
    }

    /** moves / resizes a top-level shape (EMU); creates the xfrm when it was inherited from the layout */
    fun setXfrm(i: Int, el: Element, x: Float, y: Float, w: Float, h: Float) {
        if (!canMove(el)) return
        val d = sdoc(i)
        val xf = (if (el.localName == "graphicFrame") el.k("xfrm") else el.k("spPr", "xfrm")) ?: run {
            val sp = el.k("spPr") ?: return
            mk(d, NS_A, "a:xfrm").also { sp.insertBefore(it, sp.firstChild) }
        }
        val off = xf.k("off") ?: mk(d, NS_A, "a:off").also { xf.appendChild(it) }
        val ext = xf.k("ext") ?: mk(d, NS_A, "a:ext").also { xf.appendChild(it) }
        off.setAttribute("x", x.toLong().toString()); off.setAttribute("y", y.toLong().toString())
        ext.setAttribute("cx", w.toLong().coerceAtLeast(91440).toString()); ext.setAttribute("cy", h.toLong().coerceAtLeast(91440).toString())
        touch(i)
    }

    /** embeds an image (ppt/media + slide relationship + p:pic) centred on slide [i] */
    fun addPicture(i: Int, data: ByteArray, ext: String): Element? {
        val d = sdoc(i); val tree = d.documentElement.k("cSld", "spTree") ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        val ar = if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 1.5f
        var w = cx * .6f; var h = w / ar
        if (h > cy * .7f) { h = cy * .7f; w = h * ar }
        val name = "ppt/media/pulse${System.nanoTime() % 1000000000}.$ext"
        blobs[name] = data; names.add(name); newExts.add(ext)
        val rid = addRel(i, REL_IMAGE, "/$name"); val id = nextId(d)
        val xml = """<p:pic xmlns:p="$NS_P" xmlns:a="$NS_A" xmlns:r="$NS_R"><p:nvPicPr><p:cNvPr id="$id" name="Picture $id"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>
<p:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="${((cx - w) / 2).toLong()}" y="${((cy - h) / 2).toLong()}"/><a:ext cx="${w.toLong()}" cy="${h.toLong()}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""
        val e = d.importNode(pptDom(xml.toByteArray()).documentElement, true) as Element
        tree.appendChild(e); touch(i); return e
    }

    private fun blankSlide(): String {
        fun ph(id: Int, nm: String, p: String) = """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$nm"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph $p/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="en-US"/></a:p></p:txBody></p:sp>"""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>${ph(2, "Title 1", "type=\"title\"")}${ph(3, "Content 2", "idx=\"1\"")}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""
    }

    private fun newPart(): String { var n = (slides.size + 1); while (("ppt/slides/slide$n.xml") in names) n++; return "ppt/slides/slide$n.xml" }

    private fun register(at: Int, part: String, d: Document, relsDoc: Document) {
        docs[part] = d; docs[pptRelsPath(part)] = relsDoc; names.add(part); names.add(pptRelsPath(part)); removed.remove(part)
        val rid = "rIdPpt${System.nanoTime() % 100000000}"
        slides.add(at, PptSlide(part, rid, (slides.maxOf { it.sid } + 1), true))
        touched.add(part); touched.add(pptRelsPath(part))
    }

    fun addSlide(after: Int) {
        val lay = rels(slides[after].part).firstOrNull { it.second == REL_LAYOUT }?.third
        val rels = pptDom("""<Relationships xmlns="$NS_REL">${if (lay != null) "<Relationship Id=\"rId1\" Type=\"$REL_LAYOUT\" Target=\"/$lay\"/>" else ""}</Relationships>""".toByteArray())
        register(after + 1, newPart(), pptDom(blankSlide().toByteArray()), rels)
    }

    fun duplicate(i: Int) {
        val d = pptDom(pptBytes(sdoc(i)))
        val rd = doc(pptRelsPath(slides[i].part))?.let { pptDom(pptBytes(it)) } ?: pptDom("""<Relationships xmlns="$NS_REL"/>""".toByteArray())
        rd.documentElement.kids("Relationship").filter { it.getAttribute("Type").endsWith("/notesSlide") }.forEach { rd.documentElement.removeChild(it) }
        // relative targets of the copy stay valid: slides live in the same folder
        register(i + 1, newPart(), d, rd)
    }

    fun delete(i: Int) {
        if (slides.size < 2) return
        val s = slides.removeAt(i)
        for (p in listOf(s.part, pptRelsPath(s.part)) + rels(s.part).filter { it.second.endsWith("/notesSlide") }.flatMap { listOf(it.third, pptRelsPath(it.third)) }) { removed.add(p); touched.remove(p); names.remove(p) }
    }

    fun move(i: Int, to: Int) { if (to in slides.indices) slides.add(to, slides.removeAt(i)) }

    // ---- save ----
    /** serialises everything that changed (call on the UI thread); the returned lambda writes the whole file */
    fun saver(): (File) -> Unit {
        val p = doc(presPart)!!; val pe = p.documentElement
        val prl = doc(pptRelsPath(presPart)); val ct = doc("[Content_Types].xml")
        val list = pe.k("sldIdLst") ?: mk(p, NS_P, "p:sldIdLst").also { l -> pe.insertBefore(l, pe.k("sldSz")) }
        list.kids().forEach { list.removeChild(it) }
        slides.forEach { s -> list.appendChild(mk(p, NS_P, "p:sldId").also { it.setAttribute("id", s.sid.toString()); it.setAttributeNS(NS_R, "r:id", s.rId) }) }
        prl?.documentElement?.let { root ->
            root.kids("Relationship").filter { pptResolve(presPart, it.getAttribute("Target")) in removed }.forEach { root.removeChild(it) }
            slides.filter { it.isNew }.forEach { s ->
                if (root.kids("Relationship").none { it.getAttribute("Id") == s.rId }) root.appendChild(mk(root.ownerDocument, NS_REL, "Relationship").also { r ->
                    r.setAttribute("Id", s.rId); r.setAttribute("Type", REL_SLIDE); r.setAttribute("Target", s.part.removePrefix("ppt/")) })
            }
        }
        ct?.documentElement?.let { root ->
            root.kids("Override").filter { it.getAttribute("PartName").removePrefix("/") in removed }.forEach { root.removeChild(it) }
            slides.filter { it.isNew }.forEach { s ->
                if (root.kids("Override").none { it.getAttribute("PartName") == "/${s.part}" }) root.appendChild(mk(root.ownerDocument, NS_CT, "Override").also { o -> o.setAttribute("PartName", "/${s.part}"); o.setAttribute("ContentType", CT_SLIDE) })
            }
        }
        ct?.documentElement?.let { root ->
            newExts.forEach { x ->
                if (root.kids("Default").none { it.getAttribute("Extension").equals(x, true) }) root.appendChild(mk(root.ownerDocument, NS_CT, "Default").also { o ->
                    o.setAttribute("Extension", x); o.setAttribute("ContentType", when (x) { "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; "bmp" -> "image/bmp"; else -> "image/png" }) })
            }
        }
        val parts = LinkedHashMap<String, ByteArray>()
        parts.putAll(blobs)
        touched.forEach { n -> doc(n)?.let { parts[n] = pptBytes(it) } }
        parts[presPart] = pptBytes(p)
        if (prl != null) parts[pptRelsPath(presPart)] = pptBytes(prl)
        if (ct != null) parts["[Content_Types].xml"] = pptBytes(ct)
        val gone = removed.toSet()
        return { out ->
            ZipOutputStream(out.outputStream().buffered()).use { z ->
                val seen = HashSet<String>()
                ZipFile(file).use { src ->
                    for (e in src.entries()) {
                        if (e.name in gone || !seen.add(e.name)) continue
                        z.putNextEntry(ZipEntry(e.name))
                        val b = parts[e.name]
                        if (b != null) z.write(b) else src.getInputStream(e).use { it.copyTo(z) }
                        z.closeEntry()
                    }
                }
                parts.forEach { (n, b) -> if (seen.add(n)) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
            }
        }
    }

    fun close() { runCatching { zip.close() } }
}

/** run property helpers: colour / highlight children must keep the schema order inside a:rPr */
fun pptRunColor(rp: Element, rgb: Int) {
    val d = rp.ownerDocument
    rp.kids().filter { it.localName in setOf("solidFill", "noFill", "gradFill") }.forEach { rp.removeChild(it) }
    val f = d.createElementNS(NS_A, "a:solidFill"); f.appendChild(d.createElementNS(NS_A, "a:srgbClr").also { it.setAttribute("val", "%06X".format(rgb and 0xFFFFFF)) })
    val ln = rp.k("ln"); rp.insertBefore(f, if (ln != null) ln.nextSibling else rp.firstChild)
}
fun pptRunHighlight(rp: Element, rgb: Int) {
    val d = rp.ownerDocument
    rp.kids("highlight").forEach { rp.removeChild(it) }
    val h = d.createElementNS(NS_A, "a:highlight"); h.appendChild(d.createElementNS(NS_A, "a:srgbClr").also { it.setAttribute("val", "%06X".format(rgb and 0xFFFFFF)) })
    rp.insertBefore(h, rp.kids().firstOrNull { it.localName in setOf("uLnTx", "uLn", "uFillTx", "uFill", "latin", "ea", "cs", "sym", "hlinkClick", "hlinkMouseOver", "rtl", "extLst") })
}
