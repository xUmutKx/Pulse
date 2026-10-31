package com.umutk.pulse

import android.app.WallpaperManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.features.Backdrop

/** Windows 11 materials, drawn by hand with the WinUI tint, luminosity and noise values; the blurred picture (if any) comes from [Backdrop]. */
object Effects {
    val list = listOf(
        "none" to "None",
        "mica" to "Mica",
        "micaAlt" to "Mica Alt",
        "acrylic" to "Acrylic",
        "tabbed" to "Glass",
    )
}

// the phone wallpaper's main colour, read once (an IPC call) and kept; null when the system gives none
private var wallHue: Color? = null
private var wallRead = false

/** The colour the backdrop leans towards: the neutral Windows Mica grey with a faint hint of the phone wallpaper (as Mica samples the desktop picture). */
@Composable
fun micaTint(): Color {
    val ctx = LocalContext.current
    val dark = T.dark
    return remember(dark) {
        if (!wallRead) {
            wallRead = true
            wallHue = if (android.os.Build.VERSION.SDK_INT < 27) null else runCatching { WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()?.let { Color(it) } }.getOrNull()
        }
        val grey = if (dark) Color(0xFF0A0A0A) else Color(0xFFF3F3F3)
        // 6-8%: enough to warm or cool the grey, not enough for a blue cast
        wallHue?.let { lerp(grey, it, if (dark) .08f else .06f) } ?: grey
    }
}

/** Is a material active for the current theme? Only the sidebar themes can have one (the classic Windows ones never had it). */
val Look.micaOn: Boolean get() = effect != "none" && !current.tabs

/** The tint layer over the blurred picture, per kind: WinUI tint colours, opacities folded with the luminosity layer. */
private fun overPicture(kind: String, dark: Boolean): Color = when (kind) {
    // light: a darker, denser grey than the WinUI tint, so the picture behind does not wash the content out
    "micaAlt" -> if (dark) Color(0xFF030303).copy(alpha = .72f) else Color(0xFFBDBDBD).copy(alpha = .6f)
    "acrylic" -> if (dark) Color(0xFF0A0A0A).copy(alpha = .48f) else Color(0xFFE0E0E0).copy(alpha = .5f)
    "tabbed" -> if (dark) Color(0xFF0A0A0A).copy(alpha = .42f) else Color(0xFFD6D6D6).copy(alpha = .42f)
    else -> if (dark) Color(0xFF0A0A0A).copy(alpha = .62f) else Color(0xFFD8D8D8).copy(alpha = .6f)   // mica
}

/** Paints the backdrop (the window behind the content) for [kind]. */
@Composable
fun Modifier.backdrop(kind: String): Modifier {
    val t = T
    val tint = micaTint()
    val grain = remember { grainPoints(900) }
    // Windows 11 with the whole-app picture: the root draws it blurred; here only the material's tint, grain and sheen go on top
    if (t.id == "win11" && kind != "blur" && Backdrop.mode != "off" && Backdrop.image != null) {
        if (kind == "none") return this
        val grainy = kind == "acrylic" || kind == "tabbed"
        return this.drawWithCache {
            val pts = grain.map { Offset(it.x * size.width, it.y * size.height) }
            val over = overPicture(kind, t.dark)
            val dot = if (t.dark) Color.White.copy(alpha = .035f) else Color.Black.copy(alpha = .03f)
            val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (t.dark) .01f else .1f), Color.Transparent), endY = size.height * .3f)
            onDrawBehind {
                drawRect(over)
                if (grainy) { drawPoints(pts, PointMode.Points, dot, strokeWidth = 1.4f); drawRect(sheen) }
            }
        }
    }
    if (kind == "blur" && t.id == "win11") { // the blurred wallpaper of the App lock screen, centre-cropped, darkened by the tint slider
        val ctx = LocalContext.current
        val bmp = remember(Look.blurDp, Look.blurTint) { com.umutk.pulse.features.BlurBackdrop.blurredWallpaper(ctx, Look.blurDp, ((Look.blurTint * 255).toInt() shl 24)).asImageBitmap() }
        return this.drawBehind {
            val s = maxOf(size.width / bmp.width, size.height / bmp.height)
            val sw = (size.width / s).toInt().coerceAtMost(bmp.width); val sh = (size.height / s).toInt().coerceAtMost(bmp.height)
            drawImage(bmp, IntOffset((bmp.width - sw) / 2, (bmp.height - sh) / 2), IntSize(sw, sh), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
    t.wall?.let { if (!t.tabs) return this.background(it) }   // liquid glass themes: the coloured backdrop
    if (kind == "none" || t.tabs) return this.background(t.bg)
    // no picture: Mica is a near-flat grey hinting at the wallpaper; Alt is darker with more hue; Acrylic is lighter, grainy and lit from the top
    val deep = if (t.dark) Color(0xFF020202) else Color(0xFFDADADA)
    val (a, b) = when (kind) {
        "micaAlt" -> lerp(deep, tint, .5f) to lerp(deep, tint, .8f)
        "acrylic" -> lerp(tint, Color.White, if (t.dark) .015f else .4f) to tint
        "tabbed" -> lerp(tint, Color.White, if (t.dark) .01f else .3f) to lerp(deep, tint, .7f)
        else -> tint to lerp(tint, deep, .25f)   // mica
    }
    val grainy = kind == "acrylic" || kind == "tabbed"
    return this
        .background(Brush.linearGradient(listOf(a, b), start = Offset(0f, 0f), end = Offset(1400f, 2400f)))
        .drawWithCache {
            val pts = grain.map { Offset(it.x * size.width, it.y * size.height) }
            // about 2% noise, as in the real material; Mica has a fainter one
            val dot = if (t.dark) Color.White.copy(alpha = if (grainy) .05f else .025f) else Color.Black.copy(alpha = if (grainy) .045f else .022f)
            val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (t.dark) .012f else .12f), Color.Transparent), endY = size.height * .35f)
            onDrawBehind {
                drawPoints(pts, PointMode.Points, dot, strokeWidth = 1.4f)
                if (grainy) drawRect(sheen)
            }
        }
}

/** The content surface above the backdrop: the WinUI layer fill (dark #3A3A3A at ~30%, light white at ~50%), with a 1px edge lit from the top. */
@Composable
fun Modifier.layer(kind: String, shape: androidx.compose.ui.graphics.Shape, edge: Dp = 1.dp): Modifier {
    val t = T
    if (t.glassA > 0f && !t.tabs) return this.background(t.panel.copy(alpha = t.glassA), shape).border(1.dp, glassEdge(t), shape)
    if (kind == "none" || t.tabs) return this.background(t.panel, shape)
    val pic = t.id == "win11" && Backdrop.mode != "off" && Backdrop.image != null
    val fill = if (t.dark) Color(0xFF1A1A1A) else Color.White
    val alpha = when (kind) {
        "acrylic", "blur" -> if (t.dark) .22f else .45f
        "tabbed" -> if (t.dark) .14f else .38f
        "micaAlt" -> if (t.dark) .22f else .55f
        else -> if (t.dark) .22f else .5f
    } + if (pic) .06f else 0f   // a little more body over a busy picture, for the text
    val stroke = remember(t.dark) {
        if (t.dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .05f)))
        else Brush.verticalGradient(listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .09f)))
    }
    return this.background(fill.copy(alpha = alpha), shape).border(edge, stroke, shape)
}

private fun grainPoints(n: Int): List<Offset> {
    val r = java.util.Random(7)
    return List(n) { Offset(r.nextFloat(), r.nextFloat()) }
}

/**
 * Acrylic for a Compose Dialog window: the window shrinks to its panel, its background is a transparent rounded shape and the system blurs what is behind it.
 * Returns whether the blur is really on (it is off in battery saver / without GPU support or below Android 12); callers then draw a translucent tint instead.
 */
@Composable
fun rememberWindowBlur(corner: Dp): Boolean {
    val view = androidx.compose.ui.platform.LocalView.current
    val ctx = LocalContext.current
    val cornerPx = with(androidx.compose.ui.platform.LocalDensity.current) { corner.toPx() }
    var blur by remember { androidx.compose.runtime.mutableStateOf(false) }
    val win = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    // the dialog window is match-parent by default, so a blur would cover the whole screen: wrap it around the panel
    androidx.compose.runtime.SideEffect { win?.setLayout(android.view.WindowManager.LayoutParams.WRAP_CONTENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT) }
    androidx.compose.runtime.DisposableEffect(view, cornerPx) {
        if (win == null || android.os.Build.VERSION.SDK_INT < 31) return@DisposableEffect onDispose {}
        // the background drawable's outline is the blur's outline
        win.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT); cornerRadius = cornerPx })
        val wm = ctx.getSystemService(android.view.WindowManager::class.java)
        val l = java.util.function.Consumer<Boolean> { on -> blur = on; win.setBackgroundBlurRadius(if (on) 60 else 0) }
        l.accept(wm.isCrossWindowBlurEnabled)
        wm.addCrossWindowBlurEnabledListener(l)
        onDispose { wm.removeCrossWindowBlurEnabledListener(l) }
    }
    return blur
}
