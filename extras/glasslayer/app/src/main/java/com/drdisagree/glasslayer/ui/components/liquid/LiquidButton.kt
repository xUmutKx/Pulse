package com.drdisagree.glasslayer.ui.components.liquid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 20.dp,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(LENS_BLUR.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = {
                    val width = size.width
                    val heightPx = size.height
                    val progress = highlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / heightPx, progress)
                    val maxOffset = size.minDimension
                    val offset = highlight.offset
                    translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                    translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                    val maxDragScale = 4f.dp.toPx() / heightPx
                    val angle = atan2(offset.y, offset.x)
                    scaleX = scale + maxDragScale * abs(cos(angle) * offset.x / size.maxDimension) *
                            (width / heightPx).fastCoerceAtMost(1f)
                    scaleY = scale + maxDragScale * abs(sin(angle) * offset.y / size.maxDimension) *
                            (heightPx / width).fastCoerceAtMost(1f)
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        val strength = tint.alpha * if (enabled) 1f else DISABLED_TINT
                        drawRect(tint.copy(alpha = HUE_ALPHA * strength), blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = FILL_ALPHA * strength))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                }
            )
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(highlight.modifier)
            .then(if (enabled) highlight.gestureModifier else Modifier)
            .height(height)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

private const val HUE_ALPHA = 0.6f
private val LENS_BLUR = 2.dp
private const val FILL_ALPHA = 0.45f
private const val DISABLED_TINT = 0.35f
