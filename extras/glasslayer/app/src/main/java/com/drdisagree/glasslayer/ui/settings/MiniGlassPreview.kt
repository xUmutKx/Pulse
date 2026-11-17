package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
internal fun MiniGlassPreview(
    blurDp: Float,
    refraction: Float,
    reflection: Float,
    rim: Float,
    shadow: Float,
    modifier: Modifier = Modifier
) {
    val scene = rememberLayerBackdrop()
    val surface = if (LocalPalette.current.isDark) DARK_SURFACE else LIGHT_SURFACE
    Box(
        modifier
            .fillMaxWidth()
            .clip(SceneShape),
        contentAlignment = Alignment.Center
    ) {
        GlassScene(
            Modifier
                .fillMaxSize()
                .layerBackdrop(scene),
            orbScale = ORB_SCALE
        )
        Box(
            Modifier
                .size(LENS_WIDTH, LENS_HEIGHT)
                .drawBackdrop(
                    backdrop = scene,
                    shape = { LensShape },
                    effects = {
                        blur(blurDp.dp.toPx())
                        val depth = LENS_DEPTH.toPx() * refraction
                        lens(depth, depth * 2f, depthEffect = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = rim.coerceIn(0f, 2f) * RIM_ALPHA) },
                    shadow = { Shadow(radius = SHADOW_RADIUS, alpha = (shadow * SHADOW_ALPHA).coerceIn(0f, 1f)) },
                    onDrawSurface = {
                        drawRect(surface.copy(alpha = SURFACE_ALPHA))
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

private val SceneShape = RoundedCornerShape(20.dp)
private val LensShape = RoundedCornerShape(22.dp)
private val LENS_WIDTH = 84.dp
private val LENS_HEIGHT = 40.dp
private const val ORB_SCALE = 0.75f
private val LENS_DEPTH = 10.dp
private val SHADOW_RADIUS = 12.dp
private val LIGHT_SURFACE = Color(0xFFF4F4F6)
private val DARK_SURFACE = Color(0xFF2A2A2E)
private const val SURFACE_ALPHA = 0.12f
private const val RIM_ALPHA = 0.5f
private const val SHADOW_ALPHA = 0.25f
private const val SHEEN_ALPHA = 0.22f
private const val SHEEN_DEPTH = 0.55f
