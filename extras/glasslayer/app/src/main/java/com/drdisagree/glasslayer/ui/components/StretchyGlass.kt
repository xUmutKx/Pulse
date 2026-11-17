package com.drdisagree.glasslayer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.drdisagree.glasslayer.ui.components.liquid.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

fun Modifier.stretchyGlass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    highlight: InteractiveHighlight,
    blurRadius: Dp = 16.dp
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(LENS_HEIGHT.toPx(), LENS_HEIGHT.toPx() * 2f, depthEffect = true)
    },
    highlight = { Highlight.Default.copy(alpha = RIM_ALPHA) },
    layerBlock = {
        val width = size.width
        val height = size.height
        val reach = size.minDimension
        val grow = lerp(1f, 1f + PRESS_GROW.toPx() / reach, highlight.pressProgress)
        val offset = highlight.offset
        translationX = reach * tanh(PULL * offset.x / reach)
        translationY = reach * tanh(PULL * offset.y / reach)
        val stretch = DRAG_STRETCH.toPx() / reach
        val angle = atan2(offset.y, offset.x)
        scaleX = grow + stretch * abs(cos(angle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f)
        scaleY = grow + stretch * abs(sin(angle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f)
    },
    onDrawSurface = { drawRect(tint) }
)
    .then(highlight.modifier)
    .then(highlight.gestureModifier)

private val LENS_HEIGHT = 14.dp
private val PRESS_GROW = 6.dp
private val DRAG_STRETCH = 12.dp
private const val PULL = 0.05f
private const val RIM_ALPHA = 0.5f
