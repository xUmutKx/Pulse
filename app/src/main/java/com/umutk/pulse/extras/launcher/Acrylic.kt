package com.umutk.pulse.extras.launcher

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * A see-through surface of Windows 11 (Mica / acrylic): the desktop picture, blurred, is drawn behind the surface at the spot where it sits, and a light tint goes over it, so the colours of
 * the desktop show through the taskbar, the flyouts and the menus. [tint] sets how much of the picture shows (the less opaque, the more).
 */
@Composable
fun AcrylicBox(lookId: String, tint: Color, modifier: Modifier = Modifier, shape: Shape = androidx.compose.ui.graphics.RectangleShape, blur: Dp = 26.dp, origin: Offset? = null, content: @Composable BoxScope.() -> Unit) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val land = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val wall = remember(lookId, land) {
        wallpaperRes(lookId, land)?.let { r -> runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, r, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() }
    }
    var pos by remember { mutableStateOf(Offset.Zero) }
    // the picture is as big as the window really is (the launcher may scale the screen, so the screen size of the configuration is not the window's)
    val view = androidx.compose.ui.platform.LocalView.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    // inside a popup (a menu) the root is the popup's own small window: the screen's size and the popup's place on it are used instead
    val screen = if (origin != null) generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()?.window?.decorView else null
    val rootV = screen ?: view.rootView
    val rootW = with(dens) { rootV.width.coerceAtLeast(1).toDp() }; val rootH = with(dens) { rootV.height.coerceAtLeast(1).toDp() }
    val at = pos + (origin ?: Offset.Zero)
    val amount0 = (LauncherPrefs.blurAmt.dp).coerceAtLeast(4.dp)
    // the glass style (Settings > Launcher): darker, less blurred or clearer than Pulse's own
    val style = LauncherPrefs.glassStyle
    val amount = when (style) { "soft" -> amount0 * .35f; "clear" -> amount0 * .5f; else -> amount0 }
    val blurUse = when (style) { "soft" -> blur * .35f; "clear" -> blur * .5f; else -> blur }
    val tintUse = when (style) {
        // Windows Material: a neutral Mica grey that goes darker or lighter with the tone setting, and as see-through as the person set it (the blur stays the launcher's own)
        "material" -> {
            val t = LauncherPrefs.matTone / 100f
            val base = Color(0xFF26262B)
            val c = if (t < 0) androidx.compose.ui.graphics.lerp(base, Color.Black, -t) else androidx.compose.ui.graphics.lerp(base, Color(0xFFF2F2F7), t * .9f)
            c.copy(alpha = (1f - LauncherPrefs.matClear / 100f).coerceIn(.06f, 1f))
        }
        "dark" -> androidx.compose.ui.graphics.lerp(tint, Color.Black, .55f).copy(alpha = (tint.alpha + .22f).coerceAtMost(.93f))
        "clear" -> tint.copy(alpha = tint.alpha * .55f)
        else -> tint
    }
    Box(modifier.clip(shape).onGloballyPositioned { pos = it.positionInRoot() }) {
        // the picture and the tint only fill the box: they never make it bigger (the screen-sized picture made every panel as tall as the screen)
        if (wall != null) Box(Modifier.matchParentSize().clipToBounds()) {
            Image(wall, null, Modifier.wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true).requiredSize(rootW, rootH).offset { IntOffset(-at.x.toInt(), -at.y.toInt()) }.blur(if (LauncherPrefs.blurAll) amount else blurUse), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(tintUse))
        content()
    }
}
