package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import com.umutk.pulse.features.ThemeIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import com.umutk.pulse.Icon
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** The Office entry icon in the style of the chosen Office look: Fluent document stack (365/2016/2013), classic squares (2003) / box (95), Material or pack icon. */
@Composable
fun OfSuiteLogo(size: Dp, modifier: Modifier = Modifier, tint: Color = Color.Gray) {
    val base = ofResolveId(OfficePrefs.themeFor(OfKind.WORD)).removeSuffix("d")
    val ctx = LocalContext.current
    when (base) {
        "material", "amoled", "oneui", "coloros" -> Canvas(modifier.size(size)) { outlineTile(null, Look.accent, base) }
        else -> Canvas(modifier.size(size)) {
            when (base) {
                "2003" -> squares2003()
                "95" -> box95()
                "2013" -> door2013(Color(0xFFD83B01))
                "2017" -> door2013(Color(0xFFEB3C00))
                else -> fluentDocs(Color(0xFFEB3C00), Color(0xFFFFA06A))
            }
        }
    }
}

private fun DrawScope.p(x: Float, y: Float) = Offset(x / 24f * size.width, y / 24f * size.height)

/** Back page (light) and front page (accent) with a folded corner and text lines. */
private fun DrawScope.fluentDocs(a: Color, light: Color) {
    fun page(x: Float, y: Float, w: Float, h: Float, f: Float) = Path().apply {
        moveTo(p(x + 2, y).x, p(x + 2, y).y); lineTo(p(x + w - f, y).x, p(x + w - f, y).y); lineTo(p(x + w, y + f).x, p(x + w, y + f).y)
        lineTo(p(x + w, y + h - 2).x, p(x + w, y + h - 2).y); quadraticBezierTo(p(x + w, y + h).x, p(x + w, y + h).y, p(x + w - 2, y + h).x, p(x + w - 2, y + h).y)
        lineTo(p(x + 2, y + h).x, p(x + 2, y + h).y); quadraticBezierTo(p(x, y + h).x, p(x, y + h).y, p(x, y + h - 2).x, p(x, y + h - 2).y)
        lineTo(p(x, y + 2).x, p(x, y + 2).y); quadraticBezierTo(p(x, y).x, p(x, y).y, p(x + 2, y).x, p(x + 2, y).y); close()
    }
    drawPath(page(7f, 1.5f, 15f, 18f, 5f), light)
    drawPath(page(2f, 5f, 15f, 18f, 5f), SolidColor(a))
    drawPath(Path().apply { moveTo(p(12f, 5f).x, p(12f, 5f).y); lineTo(p(12f, 10f).x, p(12f, 10f).y); lineTo(p(17f, 10f).x, p(17f, 10f).y); close() }, Color.White.copy(alpha = .45f))
    for (i in 0..2) drawRoundRect(Color.White, p(5f, 13f + i * 3f), Size(7f / 24f * size.width * (if (i == 2) .8f else 1.3f), 1.5f / 24f * size.height), CornerRadius(size.width * .02f))
}

private fun DrawScope.squares2003() {
    val s = size.width
    val g = s * .06f; val w = (s - g * 3) / 2; val r = CornerRadius(s * .06f)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF07A4E), Color(0xFFD04A1E))), Offset(g, g), Size(w, w), r)
    drawRoundRect(Color(0xFF6BA43A), Offset(g * 2 + w, g), Size(w, w), r)
    drawRoundRect(Color(0xFF2F72C4), Offset(g, g * 2 + w), Size(w, w), r)
    drawRoundRect(Color(0xFFF2B01E), Offset(g * 2 + w, g * 2 + w), Size(w, w), r)
}

private fun DrawScope.box95() { // Office 95/97 suite: stacked paper in four flat colours, black outline
    val s = size.width; val o = Stroke(maxOf(1f, s * .06f))
    fun pg(x: Float, y: Float, c: Color) { drawRect(c, Offset(x * s, y * s), Size(.5f * s, .6f * s)); drawRect(Color.Black, Offset(x * s, y * s), Size(.5f * s, .6f * s), style = o) }
    pg(.06f, .06f, Color(0xFFFFC000)); pg(.44f, .06f, Color(0xFFC00000)); pg(.06f, .34f, Color(0xFF008000)); pg(.44f, .34f, Color(0xFF0000C0))
    drawRect(Color.White, Offset(.2f * s, .62f * s), Size(.6f * s, .32f * s)); drawRect(Color.Black, Offset(.2f * s, .62f * s), Size(.6f * s, .32f * s), style = o)
}

private fun DrawScope.door2013(a: Color) { // flat Office 2013/2017 mark: door shape with white page lines
    val s = size.width
    drawPath(Path().apply { moveTo(.12f * s, .2f * s); lineTo(.62f * s, .04f * s); lineTo(.62f * s, .96f * s); lineTo(.12f * s, .8f * s); close() }, a)
    drawRect(Color(0xFFFFA06A), Offset(.62f * s, .14f * s), Size(.26f * s, .72f * s))
    for (i in 0..2) drawRect(Color.White, Offset(.66f * s, (.26f + i * .16f) * s), Size(.18f * s, .05f * s))
}

/** Text style that centres glyphs exactly in their box: no font padding, line height = font size, centred trim. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Suppress("DEPRECATION")
fun ofCenterStyle(fs: androidx.compose.ui.unit.TextUnit) = androidx.compose.ui.text.TextStyle(
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false), lineHeight = fs,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
    textAlign = androidx.compose.ui.text.style.TextAlign.Center)

fun ofOutlineBase(b: String) = b == "material" || b == "amoled" || b == "oneui" || b == "coloros"

/** Flat black tile (AMOLED #000, others #111) with a white single-stroke document outline; [k] null = suite (two lines), else app tile (letter drawn on top, bar in the app colour). Accent mode tints the tile from [acc]. */
fun DrawScope.outlineTile(k: OfKind?, acc: Color, base: String) {
    val s = size.width; val byAccent = OfficePrefs.iconAccent
    val det = if (k == null) acc else k.accent
    val tile = if (byAccent) androidx.compose.ui.graphics.lerp(det, Color.Black, .65f) else if (base == "amoled") Color.Black else Color(0xFF111111)
    val hi = if (byAccent) androidx.compose.ui.graphics.lerp(det, Color.White, .45f) else det
    drawRoundRect(tile, cornerRadius = CornerRadius(s * .22f))
    val st = Stroke(s * .06f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = StrokeJoin.Round)
    val pg = Path().apply { moveTo(.3f * s, .17f * s); lineTo(.55f * s, .17f * s); lineTo(.72f * s, .34f * s); lineTo(.72f * s, .83f * s); lineTo(.3f * s, .83f * s); close() }
    val fold = Path().apply { moveTo(.55f * s, .17f * s); lineTo(.55f * s, .34f * s); lineTo(.72f * s, .34f * s) }
    drawPath(pg, Color.White, style = st)
    drawPath(fold, hi, style = st)
    if (k == null) for (i in 0..1) drawLine(if (i == 1) hi else Color.White, Offset(.4f * s, (.52f + i * .14f) * s), Offset((if (i == 0) .62f else .54f) * s, (.52f + i * .14f) * s), s * .06f, androidx.compose.ui.graphics.StrokeCap.Round)
    else drawLine(hi, Offset(.4f * s, .74f * s), Offset(.62f * s, .74f * s), s * .05f, androidx.compose.ui.graphics.StrokeCap.Round)
}

/** Era colour of an app logo: 95 / 2003 / 2013 had their own hues, later looks use the app accent. */
fun ofEraColor(k: OfKind, base: String): Color = when (base) {
    "95" -> when (k) { OfKind.WORD -> Color(0xFF1B3FA6); OfKind.EXCEL -> Color(0xFF117A1E); OfKind.PPT -> Color(0xFFD0101A); OfKind.PDF -> Color(0xFFC0392B) }
    "2003" -> when (k) { OfKind.WORD -> Color(0xFF3A62C0); OfKind.EXCEL -> Color(0xFF3B9440); OfKind.PPT -> Color(0xFFE0782A); OfKind.PDF -> Color(0xFFC0392B) }
    "2013" -> if (k == OfKind.PPT) Color(0xFFD24726) else k.accent
    else -> k.accent
}

/** Where the letter goes: x offset, width, vertical centre, font size (all as fractions of the logo size), and its colour. */
fun ofLetterBox(k: OfKind, base: String): Pair<FloatArray, Color> {
    val c = ofEraColor(k, base)
    if (ofOutlineBase(base)) return (if (k == OfKind.PDF) floatArrayOf(0f, 1f, .56f, .17f) else floatArrayOf(0f, 1f, .54f, .27f)) to Color.White
    return if (k == OfKind.PDF) floatArrayOf(0f, 1f, .62f, .2f) to Color.White else when (base) {
        "95" -> floatArrayOf(0f, 1f, .5f, .5f) to c
        "2003" -> floatArrayOf(0f, 1f, .5f, .46f) to c
        "2013" -> floatArrayOf(0f, .5f, .5f, .36f) to Color.White
        "2017" -> floatArrayOf(.04f, .56f, .5f, .34f) to Color.White
        else -> floatArrayOf(0f, 1f, .5f, .42f) to Color.White
    }
}

/** The tile / page behind the letter, in the style of the look [base] (95, 2003, 2013, 2017, anything else = 2025). */
fun DrawScope.ofGlyph(k: OfKind, base: String) {
    val s = size.width
    val c = ofEraColor(k, base)
    if (ofOutlineBase(base)) { outlineTile(k, c, base); return }
    fun lt(x: Color, f: Float) = androidx.compose.ui.graphics.lerp(x, Color.White, f)
    fun dk(x: Color, f: Float) = androidx.compose.ui.graphics.lerp(x, Color.Black, f)
    fun rr(x: Float, y: Float, w: Float, h: Float, col: Color, r: Float = .04f) = drawRoundRect(col, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s))
    if (k == OfKind.PDF) {
        val page = Path().apply { moveTo(.16f * s, .04f * s); lineTo(.66f * s, .04f * s); lineTo(.86f * s, .24f * s); lineTo(.86f * s, .96f * s); lineTo(.16f * s, .96f * s); close() }
        val fold = Path().apply { moveTo(.66f * s, .04f * s); lineTo(.66f * s, .24f * s); lineTo(.86f * s, .24f * s); close() }
        if (base == "95" || base == "2003") {
            drawPath(page, c)
            if (base == "2003") drawPath(page, dk(c, .35f), style = androidx.compose.ui.graphics.drawscope.Stroke(s * .05f))
        } else drawPath(page, c)
        drawPath(fold, Color.White.copy(alpha = .6f))
        return
    }
    when (base) {
        "95" -> {
            val w = s * .12f
            drawRect(Color.White)
            drawRect(c, Offset(w / 2, w / 2), Size(s - w, s - w), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        }
        "2003" -> {
            drawRoundRect(Brush.linearGradient(listOf(lt(c, .55f), dk(c, .25f)), Offset.Zero, Offset(s, s)), cornerRadius = CornerRadius(s * .14f))
            rr(.16f, .16f, .68f, .68f, Color(0xFFFAFAF5), .05f)
            drawRoundRect(dk(c, .2f), Offset(s * .05f, s * .05f), Size(s * .9f, s * .9f), CornerRadius(s * .12f), style = androidx.compose.ui.graphics.drawscope.Stroke(s * .03f))
        }
        "2013" -> {
            rr(.2f, .04f, .76f, .92f, c, .03f)
            for (i in 0..3) rr(.58f, .24f + i * .17f, .3f, .06f, Color.White.copy(alpha = .85f), .01f)
            val door = Path().apply { moveTo(0f, .16f * s); lineTo(.52f * s, .04f * s); lineTo(.52f * s, .96f * s); lineTo(0f, .84f * s); close() }
            drawPath(door, dk(c, .3f))
        }
        "2017" -> {
            rr(.42f, .08f, .5f, .38f, lt(c, .45f), .02f)
            rr(.42f, .54f, .5f, .38f, lt(c, .2f), .02f)
            rr(.5f, .2f, .3f, .05f, Color.White, .01f); rr(.5f, .66f, .3f, .05f, Color.White, .01f)
            rr(.04f, .2f, .56f, .6f, dk(c, .15f), .02f)
        }
        else -> {
            val br = SolidColor(c)
            if (k == OfKind.PPT) drawCircle(br, s * .48f, Offset(s / 2, s / 2))
            else drawRoundRect(br, cornerRadius = CornerRadius(s * .22f))
        }
    }
}
