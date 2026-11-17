package com.drdisagree.glasslayer.ui.components.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.drdisagree.glasslayer.ui.theme.ToggleOn
import com.drdisagree.glasslayer.ui.theme.ToggleTrack
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidToggle(
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected) 1f else 0f) }
    val animation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    didDrag = false
                } else {
                    fraction = if (currentSelected) 0f else 1f
                }
                currentOnSelect(fraction == 1f)
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = (if (isLtr) fraction + delta else fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(animation) {
        snapshotFlow { fraction }.collectLatest { animation.updateValue(it) }
    }
    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (target != fraction) {
            fraction = target
            animation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val trackColor = ToggleTrack
    val onColor = ToggleOn

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind { drawRect(lerp(trackColor, onColor, animation.value)) }
                .size(64f.dp, 28f.dp)
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2f.dp.toPx()
                    translationX = if (isLtr) lerp(padding, padding + dragWidth, animation.value)
                    else lerp(-padding, -(padding + dragWidth), animation.value)
                }
                .semantics {
                    role = Role.Switch
                    stateDescription = if (selected) "on" else "off"
                }
                .then(animation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = animation.pressProgress
                            scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = animation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(5f.dp.toPx() * progress, 10f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = animation.pressProgress
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = animation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        val velocity = animation.velocity / 50f
                        scaleX = animation.scaleX / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                        scaleY = animation.scaleY * (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - animation.pressProgress)) }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
