package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.GlassScene
import com.drdisagree.glasslayer.ui.theme.LocalPalette
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

@Composable
internal fun GlassPreview(
    blurDp: Float,
    refraction: Float,
    reflection: Float,
    rim: Float,
    shadow: Float,
    tint: Float,
    modifier: Modifier = Modifier,
    sceneDim: Float = 0f,
    sceneBlurDp: Float = 0f,
    showGlass: Boolean = true
) {
    val scene = rememberLayerBackdrop()
    val surface = if (LocalPalette.current.isDark) DARK_SURFACE else LIGHT_SURFACE
    GlassContainer(CardShape, modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT)
                .padding(8.dp)
                .clip(InnerShape),
            contentAlignment = Alignment.Center
        ) {
            GlassScene(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(scene)
                    .blur(sceneBlurDp.dp),
                dim = sceneDim
            )
            if (showGlass) {
                Box(
                    Modifier
                        .size(GLASS_WIDTH, GLASS_HEIGHT)
                        .drawBackdrop(
                            backdrop = scene,
                            shape = { GlassShape },
                            effects = {
                                blur(blurDp.dp.toPx())
                                val lensHeight = LENS_HEIGHT.toPx() * refraction
                                lens(lensHeight, lensHeight * 2f, depthEffect = true)
                            },
                            highlight = { Highlight.Default.copy(alpha = rim.coerceIn(0f, 2f) * RIM_ALPHA) },
                            shadow = { Shadow(radius = SHADOW_RADIUS, alpha = (shadow * SHADOW_ALPHA).coerceIn(0f, 1f)) },
                            onDrawSurface = {
                                drawRect(surface.copy(alpha = tint.coerceIn(0f, 1f)))
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = (reflection * SHEEN_ALPHA).coerceIn(0f, 1f)), Color.Transparent),
                                        0f,
                                        size.height * SHEEN_DEPTH
                                    )
                                )
                            }
                        )
                )
            }
        }
    }
}

private val PREVIEW_HEIGHT = 196.dp
private val GLASS_WIDTH = 220.dp
private val GLASS_HEIGHT = 96.dp
private val InnerShape = RoundedCornerShape(22.dp)
private val GlassShape = RoundedCornerShape(30.dp)
private val LENS_HEIGHT = 14.dp
private val SHADOW_RADIUS = 16.dp
private const val RIM_ALPHA = 0.5f
private const val SHADOW_ALPHA = 0.25f
private const val SHEEN_ALPHA = 0.22f
private const val SHEEN_DEPTH = 0.55f
private val LIGHT_SURFACE = Color(0xFFF4F4F6)
private val DARK_SURFACE = Color(0xFF2A2A2E)
