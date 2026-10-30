package com.umutk.pulse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import android.graphics.pdf.PdfDocument
import java.io.BufferedOutputStream
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Blank, valid documents written with java.util.zip / PdfDocument (they open in the real Office apps). */

private const val N_XH = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
private const val N_NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val N_NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val N_NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val N_NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
private const val N_NS_S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
private const val N_NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val N_NS_CT = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val N_RT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
private const val N_CT = "application/vnd.openxmlformats-officedocument."

private fun zipTo(out: File, parts: List<Pair<String, String>>) {
    out.parentFile?.mkdirs()
    ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
        parts.forEach { (n, s) -> z.putNextEntry(ZipEntry(n)); z.write((if (n.endsWith(".xml") || n.endsWith(".rels")) N_XH + s else s).toByteArray()); z.closeEntry() }
    }
}

private fun rels(vararg r: Triple<String, String, String>) =
    "<Relationships xmlns=\"$N_NS_REL\">" + r.joinToString("") { "<Relationship Id=\"${it.first}\" Type=\"$N_RT${it.second}\" Target=\"${it.third}\"/>" } + "</Relationships>"

private fun types(vararg o: Pair<String, String>) =
    "<Types xmlns=\"$N_NS_CT\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        o.joinToString("") { "<Override PartName=\"${it.first}\" ContentType=\"${it.second}\"/>" } + "</Types>"

fun ofNewDocx(f: File) {
    val styles = StringBuilder("<w:styles xmlns:w=\"$N_NS_W\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>")
    styles.append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>")
    listOf(32, 26, 24).forEachIndexed { i, sz ->
        styles.append("<w:style w:type=\"paragraph\" w:styleId=\"Heading${i + 1}\"><w:name w:val=\"heading ${i + 1}\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"0\"/><w:outlineLvl w:val=\"$i\"/></w:pPr><w:rPr><w:color w:val=\"2F5496\"/><w:sz w:val=\"$sz\"/></w:rPr></w:style>")
    }
    styles.append("<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/><w:basedOn w:val=\"Normal\"/><w:qFormat/><w:pPr><w:ind w:left=\"720\"/></w:pPr></w:style></w:styles>")
    fun lvl(fmt: String, txt: String) = "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"$fmt\"/><w:lvlText w:val=\"$txt\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl>"
    val num = "<w:numbering xmlns:w=\"$N_NS_W\"><w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"hybridMultilevel\"/>${lvl("bullet", "•")}</w:abstractNum><w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"hybridMultilevel\"/>${lvl("decimal", "%1.")}</w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num><w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num></w:numbering>"
    val doc = "<w:document xmlns:w=\"$N_NS_W\" xmlns:r=\"$N_NS_R\"><w:body><w:p/><w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
    zipTo(f, listOf(
        "[Content_Types].xml" to types("/word/document.xml" to "${N_CT}wordprocessingml.document.main+xml", "/word/styles.xml" to "${N_CT}wordprocessingml.styles+xml", "/word/numbering.xml" to "${N_CT}wordprocessingml.numbering+xml"),
        "_rels/.rels" to rels(Triple("rId1", "officeDocument", "word/document.xml")),
        "word/document.xml" to doc, "word/styles.xml" to styles.toString(), "word/numbering.xml" to num,
        "word/_rels/document.xml.rels" to rels(Triple("rId1", "styles", "styles.xml"), Triple("rId2", "numbering", "numbering.xml"))))
}

fun ofNewXlsx(f: File) {
    val styles = "<styleSheet xmlns=\"$N_NS_S\"><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"
    zipTo(f, listOf(
        "[Content_Types].xml" to types("/xl/workbook.xml" to "${N_CT}spreadsheetml.sheet.main+xml", "/xl/worksheets/sheet1.xml" to "${N_CT}spreadsheetml.worksheet+xml", "/xl/styles.xml" to "${N_CT}spreadsheetml.styles+xml"),
        "_rels/.rels" to rels(Triple("rId1", "officeDocument", "xl/workbook.xml")),
        "xl/workbook.xml" to "<workbook xmlns=\"$N_NS_S\" xmlns:r=\"$N_NS_R\"><sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
        "xl/_rels/workbook.xml.rels" to rels(Triple("rId1", "worksheet", "worksheets/sheet1.xml"), Triple("rId2", "styles", "styles.xml")),
        "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"$N_NS_S\"><sheetData/></worksheet>", "xl/styles.xml" to styles))
}

fun ofNewPptx(f: File) {
    val hdr = "xmlns:a=\"$N_NS_A\" xmlns:r=\"$N_NS_R\" xmlns:p=\"$N_NS_P\""
    val grp = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
    fun sp(id: Int, name: String, y: Int, h: Int, sz: Int, ph: String) = "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"609600\" y=\"$y\"/><a:ext cx=\"10972800\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap=\"square\" rtlCol=\"0\"/><a:lstStyle/><a:p><a:r><a:rPr lang=\"en-US\" sz=\"$sz\"/><a:t>$ph</a:t></a:r></a:p></p:txBody></p:sp>"
    val slide = "<p:sld $hdr><p:cSld><p:spTree>$grp${sp(2, "Title", 457200, 1143000, 4000, "Title")}${sp(3, "Text", 1828800, 4114800, 2400, "Click to edit")}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
    val clr = "bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\""
    val master = "<p:sldMaster $hdr><p:cSld><p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg><p:spTree>$grp</p:spTree></p:cSld><p:clrMap $clr/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>"
    val layout = "<p:sldLayout $hdr type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>$grp</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
    val pres = "<p:presentation $hdr><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/></p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"
    fun c(n: String, v: String) = "<a:$n><a:srgbClr val=\"$v\"/></a:$n>"
    val fill = "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
    val theme = "<a:theme xmlns:a=\"$N_NS_A\" name=\"Office Theme\"><a:themeElements><a:clrScheme name=\"Office\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1><a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>" +
        c("dk2", "44546A") + c("lt2", "E7E6E6") + c("accent1", "4472C4") + c("accent2", "ED7D31") + c("accent3", "A5A5A5") + c("accent4", "FFC000") + c("accent5", "5B9BD5") + c("accent6", "70AD47") + c("hlink", "0563C1") + c("folHlink", "954F72") +
        "</a:clrScheme><a:fontScheme name=\"Office\"><a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont><a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
        "<a:fmtScheme name=\"Office\"><a:fillStyleLst>$fill$fill$fill</a:fillStyleLst><a:lnStyleLst>" + "<a:ln w=\"6350\">$fill</a:ln>".repeat(3) + "</a:lnStyleLst><a:effectStyleLst>" + "<a:effectStyle><a:effectLst/></a:effectStyle>".repeat(3) + "</a:effectStyleLst><a:bgFillStyleLst>$fill$fill$fill</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"
    zipTo(f, listOf(
        "[Content_Types].xml" to types("/ppt/presentation.xml" to "${N_CT}presentationml.presentation.main+xml", "/ppt/slides/slide1.xml" to "${N_CT}presentationml.slide+xml",
            "/ppt/slideMasters/slideMaster1.xml" to "${N_CT}presentationml.slideMaster+xml", "/ppt/slideLayouts/slideLayout1.xml" to "${N_CT}presentationml.slideLayout+xml", "/ppt/theme/theme1.xml" to "${N_CT}theme+xml"),
        "_rels/.rels" to rels(Triple("rId1", "officeDocument", "ppt/presentation.xml")),
        "ppt/presentation.xml" to pres,
        "ppt/_rels/presentation.xml.rels" to rels(Triple("rId1", "slideMaster", "slideMasters/slideMaster1.xml"), Triple("rId2", "slide", "slides/slide1.xml"), Triple("rId3", "theme", "theme/theme1.xml")),
        "ppt/slides/slide1.xml" to slide, "ppt/slides/_rels/slide1.xml.rels" to rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml")),
        "ppt/slideMasters/slideMaster1.xml" to master, "ppt/slideMasters/_rels/slideMaster1.xml.rels" to rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"), Triple("rId2", "theme", "../theme/theme1.xml")),
        "ppt/slideLayouts/slideLayout1.xml" to layout, "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to rels(Triple("rId1", "slideMaster", "../slideMasters/slideMaster1.xml")),
        "ppt/theme/theme1.xml" to theme))
}

/** One blank A4 page. */
fun ofNewPdf(f: File) {
    f.parentFile?.mkdirs()
    val d = PdfDocument()
    try {
        val pg = d.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()); d.finishPage(pg)
        FileOutputStream(f).use { d.writeTo(it) }
    } finally { d.close() }
}

/** Creates a blank "<base>.<ext>" (docx, xlsx, pptx, pdf, txt) in [dir]; a number is added when the name is taken. */
fun ofCreateIn(dir: File, base: String, ext: String): File {
    dir.mkdirs()
    val clean = base.trim().ifEmpty { "Document" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    var f = File(dir, "$clean.$ext"); var n = 2
    while (f.exists()) f = File(dir, "$clean ${n++}.$ext")
    when (ext) { "docx" -> ofNewDocx(f); "xlsx" -> ofNewXlsx(f); "pptx" -> ofNewPptx(f); "pdf" -> ofNewPdf(f); else -> f.writeText("") }
    return f
}

/** Creates "<base>.<ext>" in the Office folder (a number is added when the name is taken) and returns it. */
fun ofCreate(k: OfKind, base: String): File = ofCreateIn(OfficePrefs.folder, base, k.ext)

/** "Save as" dialog shared by the viewer and the text editor: name, extension (only [exts]), folder; [onPick] gets the free target file.
 *  [overwrite] adds an "Üstüne yaz" switch: the name is then taken as it is (an existing file of that name is replaced). */
@androidx.compose.runtime.Composable
fun OfSaveAsDialog(name: String, exts: List<String>, srcDir: File?, onDismiss: () -> Unit, overwrite: Boolean = false, onPick: (File) -> Unit) {
    var base by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(name.substringBeforeLast('.')) }
    var ext by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(name.substringAfterLast('.', exts.first()).lowercase().takeIf { it in exts } ?: exts.first()) }
    var over by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val root = android.os.Environment.getExternalStorageDirectory()
    val places = listOfNotNull(OfficePrefs.folder, srcDir, File(root, "Documents"), File(root, "Download"), File(root, "DCIM"), root).filter { it.isDirectory }.distinctBy { it.path }
    var dir by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(places.first()) }
    // the folder shown like the Windows Save As window: the way up, the folders, and the files of the kinds this document can be saved as
    val entries = androidx.compose.runtime.remember(dir, ext) {
        (dir.listFiles()?.toList() ?: emptyList()).filter { !it.name.startsWith(".") && (it.isDirectory || exts.any { e -> it.name.endsWith(".$e", true) }) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
    WinAlert(onDismissRequest = onDismiss, containerColor = LocalWindowLook.current.panel, title = { androidx.compose.material3.Text(tr("Save as", "Farklı kaydet"), color = T.text) },
        text = { androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(36.dp).clickable(enabled = dir.path != root.path) { dir.parentFile?.let { dir = it } }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                    PIcon("up", if (dir.path != root.path) T.text else T.sub, 20.dp)
                }
                androidx.compose.material3.Text(dir.path.removePrefix(root.path).ifEmpty { "/" }, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = androidx.compose.ui.Modifier.weight(1f))
            }
            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                places.forEach { d -> PChip(dir.path == d.path, if (d.path == OfficePrefs.folder.path) "Office" else if (d.path == root.path) tr("Phone", "Telefon") else d.name) { dir = d } }
            }
            androidx.compose.foundation.lazy.LazyColumn(androidx.compose.ui.Modifier.fillMaxWidth().height(200.dp).border(1.dp, T.line)) {
                if (entries.isEmpty()) item { androidx.compose.material3.Text(tr("Empty folder", "Boş klasör"), color = T.sub, fontSize = 13.sp, modifier = androidx.compose.ui.Modifier.padding(10.dp)) }
                items(entries.size) { i ->
                    val f = entries[i]
                    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().clickable {
                        if (f.isDirectory) dir = f else { base = f.name.substringBeforeLast('.'); exts.firstOrNull { e -> f.name.endsWith(".$e", true) }?.let { ext = it } }
                    }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PIcon(if (f.isDirectory) "folder" else "document", if (f.isDirectory) T.accent else T.sub, 20.dp)
                        androidx.compose.material3.Text(f.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = androidx.compose.ui.Modifier.padding(start = 8.dp))
                    }
                }
            }
            PField(base, { base = it }, tr("File name", "Dosya adı"), androidx.compose.ui.Modifier.fillMaxWidth())
            if (exts.size > 1) androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                exts.forEach { e -> PChip(ext == e, ".$e") { ext = e } }
            }
            if (overwrite) PChip(over, tr("Overwrite", "Üstüne yaz")) { over = !over }
        } },
        confirmButton = { androidx.compose.material3.TextButton({
            val clean = base.trim().ifEmpty { "Document" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            dir.mkdirs()
            var f = File(dir, "$clean.$ext"); var n = 2
            while (!over && f.exists()) f = File(dir, "$clean ${n++}.$ext")
            onPick(f)
        }) { androidx.compose.material3.Text(tr("Save", "Kaydet"), color = T.accent) } },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { androidx.compose.material3.Text(tr("Cancel", "İptal"), color = T.accent) } })
}

/**
 * Plain Save on a file the Office page cannot write back in its own format (.doc, .xls, .ppt, .rtf, .odt, ...).
 * Nothing is written until the user picks: overwrite the converted copy beside the original (the original stays),
 * a Save as target, or a TXT copy. [onOverwrite] / [onPick] get the target file; [onTxt] hands over to the TXT flow.
 */
@androidx.compose.runtime.Composable
fun OfOldFormatDialog(name: String, convExt: String, srcDir: File?, onDismiss: () -> Unit, onOverwrite: (File) -> Unit, onPick: (File) -> Unit, onTxt: () -> Unit) {
    var asSaveAs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (asSaveAs) { OfSaveAsDialog(name.substringBeforeLast('.') + "_edited.$convExt", listOf(convExt), srcDir, onDismiss, onPick = onPick); return }
    val base = name.substringBeforeLast('.')
    WinAlert(onDismissRequest = onDismiss, title = { androidx.compose.material3.Text(tr("Save", "Kaydet")) },
        text = { androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Text(tr("$name is an old format and cannot be saved in place. The original file stays unchanged.",
                "$name eski bir biçim, olduğu yere kaydedilemez. Özgün dosya değişmeden kalır."), fontSize = 14.sp)
            if (srcDir != null) androidx.compose.material3.Text(tr("Overwrite writes $base.$convExt in the same folder (replacing one with that name).",
                "Üstüne yaz, aynı klasörde $base.$convExt dosyasını yazar (aynı adlı varsa değiştirir)."), fontSize = 13.sp)
        } },
        confirmButton = { if (srcDir != null) androidx.compose.material3.TextButton({ onOverwrite(File(srcDir, "$base.$convExt")) }) { androidx.compose.material3.Text(tr("Overwrite", "Üstüne yaz")) } },
        dismissButton = { androidx.compose.foundation.layout.Column {
            androidx.compose.material3.TextButton({ asSaveAs = true }) { androidx.compose.material3.Text(tr("Save as", "Farklı kaydet")) }
            androidx.compose.material3.TextButton(onTxt) { androidx.compose.material3.Text(tr("Save as TXT", "TXT olarak kaydet")) }
            androidx.compose.material3.TextButton(onDismiss) { androidx.compose.material3.Text(tr("Cancel", "İptal")) }
        } })
}
