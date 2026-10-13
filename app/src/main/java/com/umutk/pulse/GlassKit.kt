package com.umutk.pulse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt

/*
 * GlassKit: liquid-glass surfaces for Compose. Only depends on Compose, so another app can copy this one file.
 *
 * How it works: the wallpaper is painted twice. Once sharp, as the background. Once into a tiny bitmap that is
 * stretched back up, which gives a soft blur. A glass surface does not blur anything itself. It draws the blurred
 * copy at the place where it sits on screen, then adds a tint, a sheen and a light edge.
 *
 * Usage:
 *   GlassRoot(wallpaperKey = "sky", wallpaper = { drawRect(...) /* use size, not pixel constants */ }) {
 *       GlassSurface { Text("Hello") }
 *   }
 *
 * Shortcut: the blur is a cheap downscale, not a real Gaussian. Good enough for a glass look, not for text behind glass.
 */

/** Paints a wallpaper. Must use [DrawScope.size] for positions, because it is also drawn at a small size for the blur. */
typealias GlassPainter = DrawScope.() -> Unit

data class GlassStyle(
    /** Draw the blurred backdrop. False gives a flat tinted surface (for example a classic opaque window). */
    val blur: Boolean = true,
    val tint: Color = Color.White,
    /** 0..1, how strong the tint is over the backdrop. */
    val tintAlpha: Float = 0.2f,
    /** 1 keeps colours, above 1 makes them richer, below 1 greyer. */
    val saturation: Float = 1.3f,
    /** 0..1, brightness of the light along the edge. */
    val edgeAlpha: Float = 0.6f,
    val cornerRadius: Dp = 24.dp,
)

/** The same style with the tint and edge light scaled by [factor] (1 = unchanged). */
fun GlassStyle.scaled(factor: Float): GlassStyle = copy(
    tintAlpha = (tintAlpha * factor).coerceIn(0f, 1f),
    edgeAlpha = (edgeAlpha * factor).coerceIn(0f, 1f),
)

private class GlassBackdrop(val image: ImageBitmap, val rootSize: IntSize)

private class GlassPosition {
    var at by mutableStateOf(Offset.Zero)
}

private val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Root of a glass screen. Paints [wallpaper] behind [content] and prepares its blurred copy for glass surfaces inside.
 * Put every glass surface inside this, or it has no backdrop and only shows its tint.
 */
@Composable
fun GlassRoot(
    wallpaperKey: Any,
    modifier: Modifier = Modifier.fillMaxSize(),
    /** Shrink factor of the blurred copy: bigger = softer. Changing it rebuilds the backdrop. */
    blurDiv: Int = 12,
    wallpaper: GlassPainter,
    content: @Composable BoxScope.() -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val backdrop = remember(size, wallpaperKey, blurDiv) {
        if (size.width <= 0 || size.height <= 0) null else makeBackdrop(size, density, direction, blurDiv, wallpaper)
    }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(
            modifier
                .onSizeChanged { size = it }
                .drawBehind { wallpaper() },
            content = content,
        )
    }
}

private fun makeBackdrop(size: IntSize, density: Density, direction: LayoutDirection, div: Int, wallpaper: GlassPainter): GlassBackdrop {
    val w = (size.width / div).coerceAtLeast(2)
    val h = (size.height / div).coerceAtLeast(2)
    val small = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, direction, Canvas(small), Size(w.toFloat(), h.toFloat())) { wallpaper() }
    return GlassBackdrop(small, size)
}

/** Glass look for this element: blurred backdrop, tint, sheen and a light edge, clipped to [shape]. */
@Composable
fun Modifier.glass(
    style: GlassStyle = GlassStyle(),
    shape: Shape = RoundedCornerShape(style.cornerRadius),
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val pos = remember { GlassPosition() }
    return this
        .onGloballyPositioned { pos.at = it.positionInRoot() }
        .clip(shape)
        .drawWithCache {
            val path = shape.createOutline(size, layoutDirection, this).asPath()
            val sheen = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), 0f, size.height * 0.6f,
            )
            val edge = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = style.edgeAlpha),
                    Color.White.copy(alpha = style.edgeAlpha * 0.15f),
                    Color.White.copy(alpha = style.edgeAlpha * 0.4f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val saturate = ColorMatrix().apply { setToSaturation(style.saturation) }
            val edgeWidth = 1.dp.toPx()
            onDrawBehind {
                if (style.blur && backdrop != null) drawBlurredBackdrop(backdrop, pos.at, saturate)
                drawRect(style.tint.copy(alpha = style.tintAlpha))
                drawPath(path, sheen)
                drawPath(path, edge, style = Stroke(edgeWidth))
            }
        }
}

/** A glass panel. Put the padding inside [content], not on [modifier]. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle(),
    shape: Shape = RoundedCornerShape(style.cornerRadius),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(style, shape), contentAlignment = contentAlignment, content = content)
}

private fun Outline.asPath(): Path {
    val p = Path()
    when (this) {
        is Outline.Rectangle -> p.addRect(rect)
        is Outline.Rounded -> p.addRoundRect(roundRect)
        is Outline.Generic -> p.addPath(path)
    }
    return p
}

private fun DrawScope.drawBlurredBackdrop(backdrop: GlassBackdrop, at: Offset, saturate: ColorMatrix) {
    val img = backdrop.image
    val sx = img.width / backdrop.rootSize.width.toFloat()
    val sy = img.height / backdrop.rootSize.height.toFloat()
    val x = (at.x * sx).roundToInt().coerceIn(0, img.width - 1)
    val y = (at.y * sy).roundToInt().coerceIn(0, img.height - 1)
    val w = (size.width * sx).roundToInt().coerceIn(1, img.width - x)
    val h = (size.height * sy).roundToInt().coerceIn(1, img.height - y)
    drawImage(
        image = img,
        srcOffset = IntOffset(x, y),
        srcSize = IntSize(w, h),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        colorFilter = ColorFilter.colorMatrix(saturate),
    )
}
