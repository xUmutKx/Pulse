package com.drdisagree.glasslayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.theme.Surface
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    tint: (() -> Color)? = null,
    blurRadius: Dp = 4.dp,
    refraction: Dp = 16.dp,
    exported: LayerBackdrop? = null
): Modifier {
    val surface = Surface
    val surfaceTint = tint ?: { surface }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            lens(refraction.toPx(), refraction.toPx() * 2f, depthEffect = true)
        },
        highlight = { Highlight.Default.copy(alpha = RIM_ALPHA) },
        exportedBackdrop = exported,
        onDrawSurface = {
            drawRect(surfaceTint())
        }
    )
}

private const val RIM_ALPHA = 0.5f
