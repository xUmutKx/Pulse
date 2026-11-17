package com.drdisagree.glasslayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.theme.Ink
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

@Composable
fun ProgressiveBlurBar(
    height: Dp,
    exported: LayerBackdrop,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 1f }
) {
    val tint = Ink
    val base = modifier
        .fillMaxWidth()
        .height(height)
        .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
    Box(
        base.drawPlainBackdrop(
            backdrop = LocalBackdrop.current,
            shape = { RectangleShape },
            exportedBackdrop = exported,
            effects = {
                blur(BLUR_RADIUS.toPx() * progress().coerceIn(0f, 1f))
                runtimeShaderEffect(SHADER_KEY, MASK_SHADER, "content") {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
                    setFloatUniform("tintIntensity", TINT_INTENSITY * progress().coerceIn(0f, 1f))
                }
            }
        )
    )
}

private val BLUR_RADIUS = 28.dp
private const val TINT_INTENSITY = 0.4f
private const val SHADER_KEY = "GlassLayerProgressiveBlur"
private const val MASK_SHADER = """
uniform shader content;
uniform float2 size;
uniform float4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
    float alpha = smoothstep(size.y, size.y * 0.55, coord.y);
    half4 blurred = content.eval(coord) * alpha;
    half4 tinted = half4(tint.rgb * tint.a, tint.a) * alpha;
    return mix(blurred, tinted, tintIntensity);
}"""
