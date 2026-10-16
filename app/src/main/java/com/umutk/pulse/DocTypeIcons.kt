package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/** Type colour of a document extension (shared with the Files list). */
fun docTypeColor(ext: String): Color = when (ext.lowercase().removePrefix(".")) {
    "doc", "docx", "dot", "dotx", "rtf", "odt" -> Color(0xFF2B579A)
    "xls", "xlsx", "csv", "ods" -> Color(0xFF217346)
    "ppt", "pptx", "odp" -> Color(0xFFC43E1C)
    "pdf" -> Color(0xFFB30B00)
    else -> Color(0xFF5F6368)
}

/** Ribbon text of a document extension: upper case, at most 4 letters. */
fun docTypeLabel(ext: String): String = ext.removePrefix(".").uppercase().take(4)

/** Document file icon: white page, folded corner, coloured ribbon with the extension, grey text lines. Era look from [base] (95, 2003, 2013, 2017, 365, material...). */
@Composable
fun OfDocTypeIcon(ext: String, size: Dp, modifier: Modifier = Modifier, base: String = ofResolveId(OfficePrefs.themeFor(OfKind.WORD)).removeSuffix("d")) {
    val kind = when (docTypeColor(ext)) { OfKind.WORD.accent -> OfKind.WORD; OfKind.EXCEL.accent -> OfKind.EXCEL; Color(0xFFC43E1C) -> OfKind.PPT; Color(0xFFB30B00) -> OfKind.PDF; else -> null }
    val c = if (kind != null) ofEraColor(kind, base) else docTypeColor(ext) // same hues as the title-bar tiles
    val label = docTypeLabel(ext).ifEmpty { "FILE" }
    val b95 = base == "95"; val xp = base == "2003"; val flat = base == "2013" || base == "2017" || base == "365"
    val round = !b95 && !xp && !flat
    val out = ofOutlineBase(base)
    val d = LocalDensity.current
    val rh = if (out) .17f else .24f; val ry = .27f; val rx = if (b95 || out) .14f else .08f; val rw = if (b95 || out) .72f else .84f
    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.width; val x0 = s * .14f; val pw = s * .72f; val f = pw * .3f
            val pg = Path().apply { moveTo(x0, s * .02f); lineTo(x0 + pw - f, s * .02f); lineTo(x0 + pw, s * .02f + f); lineTo(x0 + pw, s * .98f); lineTo(x0, s * .98f); close() }
            val fold = Path().apply { moveTo(x0 + pw - f, s * .02f); lineTo(x0 + pw - f, s * .02f + f); lineTo(x0 + pw, s * .02f + f); close() }
            val line = if (out) Color(0xFF8A8F96) else if (b95) Color.Black else if (xp) Color(0xFF6D8FD6) else if (flat) c else Color(0xFFB8BCC2)
            val st = Stroke(if (out) s * .025f else if (round) s * .03f else maxOf(1f, s * .04f), join = if (round) StrokeJoin.Round else StrokeJoin.Miter)
            drawPath(pg, Color.White); if (!out) drawPath(fold, if (b95) Color(0xFF808080) else if (xp) Color(0xFFC8D6F0) else c.copy(alpha = .25f))
            drawPath(pg, line, style = st); drawPath(fold, line, style = st)
            val rc = c
            val rr = if (round) CornerRadius(s * .08f) else CornerRadius.Zero
            drawRoundRect(rc, Offset(s * rx, s * ry), Size(s * rw, s * rh), rr)
            if (b95) drawRect(Color.Black, Offset(s * rx, s * ry), Size(s * rw, s * rh), style = Stroke(1f))
            val th = s * .04f
            for (i in 0..2) drawRect(if (b95) Color(0xFF808080) else Color(0xFFB8BCC2), Offset(x0 + pw * .14f, s * (.6f + i * .1f)), Size(pw * (if (i == 2) .45f else .72f), th))
        }
        val fs = minOf(rh * .72f, rw * .92f / (label.length * .64f))
        Box(Modifier.offset(size * rx, size * ry).size(size * rw, size * rh), contentAlignment = Alignment.Center) {
            val fsp = with(d) { (size * fs).toSp() }
            Text(label, color = Color.White, fontSize = fsp, style = ofCenterStyle(fsp), fontWeight = FontWeight.Bold, fontFamily = if (b95) FontFamily.Monospace else null, maxLines = 1, softWrap = false)
        }
    }
}
