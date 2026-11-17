package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.fadingEdges(
    state: ScrollableState,
    orientation: Orientation = Orientation.Horizontal,
    length: Dp = 28.dp
): Modifier {
    val start by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(FADE_MS), label = "edgeStart")
    val end by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(FADE_MS), label = "edgeEnd")
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = length.toPx()
            val horizontal = orientation == Orientation.Horizontal
            val extent = if (horizontal) size.width else size.height
            if (start > 0f) {
                val brush = if (horizontal) {
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 1f - start), Color.Black), 0f, edge)
                } else {
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - start), Color.Black), 0f, edge)
                }
                drawRect(
                    brush,
                    size = if (horizontal) Size(edge, size.height) else Size(size.width, edge),
                    blendMode = BlendMode.DstIn
                )
            }
            if (end > 0f) {
                val from = extent - edge
                val brush = if (horizontal) {
                    Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - end)), from, extent)
                } else {
                    Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - end)), from, extent)
                }
                drawRect(
                    brush,
                    topLeft = if (horizontal) Offset(from, 0f) else Offset(0f, from),
                    size = if (horizontal) Size(edge, size.height) else Size(size.width, edge),
                    blendMode = BlendMode.DstIn
                )
            }
        }
}

private const val FADE_MS = 220
