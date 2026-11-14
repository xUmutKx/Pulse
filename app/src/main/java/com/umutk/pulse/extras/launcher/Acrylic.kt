package com.umutk.pulse.extras.launcher

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
fun AcrylicBox(lookId: String, tint: Color, modifier: Modifier = Modifier, shape: Shape = androidx.compose.ui.graphics.RectangleShape, blur: Dp = 26.dp, content: @Composable BoxScope.() -> Unit) {
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
    val rootW = with(dens) { view.rootView.width.coerceAtLeast(1).toDp() }; val rootH = with(dens) { view.rootView.height.coerceAtLeast(1).toDp() }
    val amount = (LauncherPrefs.blurAmt.dp).coerceAtLeast(4.dp)
    Box(modifier.clip(shape).onGloballyPositioned { pos = it.positionInRoot() }) {
        if (wall != null) Image(wall, null, Modifier.requiredSize(rootW, rootH).offset { IntOffset(-pos.x.toInt(), -pos.y.toInt()) }.blur(if (LauncherPrefs.blurAll) amount else blur), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(tint))
        content()
    }
}
