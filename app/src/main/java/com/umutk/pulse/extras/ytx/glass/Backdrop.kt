package com.umutk.pulse.extras.ytx.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * A light stand-in for the Kyant "backdrop" library of YTXTube. That library samples what lies behind a surface and bends it through shaders (refraction, blur):
 * pretty, but it redraws the page behind every glass surface on every frame, which is what made YTXTube slow on many phones, and it needs a newer Compose than Pulse.
 * Here a "glass" surface is its shape and its tint only (a translucent fill): the same calls, a fraction of the work. The Liquid Glass setting switches between this
 * translucent look and solid surfaces.
 */

/** What lies behind a glass surface. Here only a marker: nothing is recorded. */
interface Backdrop

private object NoBackdrop : Backdrop

@Composable
fun rememberLayerBackdrop(): Backdrop = remember { object : Backdrop {} }

@Composable
fun rememberCombinedBackdrop(vararg parts: Backdrop): Backdrop = remember { object : Backdrop {} }

fun emptyBackdrop(): Backdrop = NoBackdrop

/** Records the content as the backdrop of the glass over it. Nothing is recorded here. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.layerBackdrop(backdrop: Backdrop): Modifier = this

/** Whether the refraction shaders could run. The stand-in never runs shaders, so the translucent look is the "glass" one. */
fun isRuntimeShaderSupported(): Boolean = true

/** The effects of a glass surface. The stand-in keeps the calls and draws none of them. */
class BackdropEffectScope(density: Density) : Density by density {
    fun vibrancy() {}
    @Suppress("UNUSED_PARAMETER") fun blur(radius: Float) {}
    @Suppress("UNUSED_PARAMETER") fun lens(height: Float, amount: Float, chromaticAberration: Boolean = false) {}
}

@Immutable
data class Highlight(val alpha: Float = 1f) { companion object { val Default = Highlight() } }

@Immutable
data class Shadow(val radius: Dp = 24.dp, val color: Color = Color.Black.copy(alpha = .1f), val alpha: Float = 1f) { companion object { val Default = Shadow() } }

@Immutable
data class InnerShadow(val radius: Dp = 24.dp, val alpha: Float = 1f) { companion object { val Default = InnerShadow() } }

/** A glass surface: its shape, its tint ([onDrawSurface]) and its transform ([layerBlock]). No sampling of the backdrop. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit = {},
    highlight: (() -> Highlight?)? = null,
    shadow: (() -> Shadow?)? = null,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
): Modifier = this
    .graphicsLayer {
        layerBlock?.invoke(this)
        this.shape = shape()
        clip = true
    }
    .drawBehind { onDrawSurface?.invoke(this) }

/** A capsule: fully rounded ends. */
@Suppress("FunctionName")
fun Capsule(): Shape = RoundedCornerShape(percent = 50)
