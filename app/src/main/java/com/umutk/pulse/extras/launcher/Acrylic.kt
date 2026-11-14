package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
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
fun AcrylicBox(lookId: String, tint: Color, modifier: Modifier = Modifier, shape: Shape = androidx.compose.foundation.shape.RectangleShape, blur: Dp = 26.dp, content: @Composable BoxScope.() -> Unit) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val land = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val wall = remember(lookId, land) {
        wallpaperRes(lookId, land)?.let { r -> runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, r, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() }
    }
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.clip(shape).onGloballyPositioned { pos = it.positionInRoot() }) {
        if (wall != null) Image(wall, null, Modifier.requiredSize(cfg.screenWidthDp.dp, cfg.screenHeightDp.dp).offset { IntOffset(-pos.x.toInt(), -pos.y.toInt()) }.blur(blur), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(tint))
        content()
    }
}
