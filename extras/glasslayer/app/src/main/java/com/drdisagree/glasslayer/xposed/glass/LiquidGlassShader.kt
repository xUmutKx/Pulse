package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Color
import android.graphics.RuntimeShader

object LiquidGlassShader {

    private const val REFRACTION_RATIO = 0.18f
    private const val MAX_EDGE_PX = 40f
    private const val DISPERSION = 0.06f
    private const val SATURATION = 1.15f
    private const val RIM_WIDTH = 2.5f

    private const val SOURCE = """
        uniform shader image;
        uniform float2 origin;
        uniform float2 size;
        uniform float4 radii;
        uniform float refraction;
        uniform float edge;
        uniform float dispersion;
        uniform float rim;
        uniform float rimWidth;
        uniform float overlay;
        uniform float reflection;
        uniform float shadow;
        uniform float saturation;
        uniform float4 dim;
        uniform float join;
        uniform float2 joinOrigin;
        uniform float2 joinSize;
        uniform float4 joinRadii;

        float roundedBox(float2 p, float2 halfSize, float4 corners) {
            float r = p.x > 0.0 ? (p.y > 0.0 ? corners.z : corners.y) : (p.y > 0.0 ? corners.w : corners.x);
            float2 q = abs(p) - halfSize + r;
            float k = max(edge * 0.5, 1.0);
            float h = clamp(0.5 + 0.5 * (q.y - q.x) / k, 0.0, 1.0);
            float inside = mix(q.x, q.y, h) + k * h * (1.0 - h);
            return length(max(q, 0.0)) + min(inside, 0.0) - r;
        }

        float shape(float2 coord) {
            float2 halfSize = size * 0.5;
            float d = roundedBox(coord - origin - halfSize, halfSize, radii);
            if (join > 0.5) {
                float2 joinHalf = joinSize * 0.5;
                d = min(d, roundedBox(coord - joinOrigin - joinHalf, joinHalf, joinRadii));
            }
            return d;
        }

        half4 main(float2 coord) {
            float2 e = float2(1.0, 0.0);
            float d = shape(coord);
            float2 normal = float2(
                shape(coord + e.xy) - shape(coord - e.xy),
                shape(coord + e.yx) - shape(coord - e.yx)
            );
            normal = normal / max(length(normal), 0.0001);

            float band = clamp(1.0 + d / max(edge, 1.0), 0.0, 1.0);
            float bend = band * band * band;
            float2 offset = -normal * refraction * bend;
            float rimBand = smoothstep(-rimWidth, 0.0, d);
            float light = clamp(dot(normal, normalize(float2(-0.5, -0.85))), 0.0, 1.0);
            float shade = clamp(dot(normal, normalize(float2(0.5, 0.85))), 0.0, 1.0);

            if (overlay > 0.5) {
                half highlight = half(clamp(rim * rimBand * (0.18 + 0.5 * light) + reflection * bend * 0.12 * light, 0.0, 1.0));
                half darken = half(clamp(shadow * bend * 0.06 * shade, 0.0, 1.0));
                return half4(highlight, highlight, highlight, clamp(highlight + darken, 0.0, 1.0));
            }

            half4 color;
            color.r = image.eval(coord + offset * (1.0 + dispersion)).r;
            color.g = image.eval(coord + offset).g;
            color.b = image.eval(coord + offset * (1.0 - dispersion)).b;
            color.a = image.eval(coord + offset).a;
            half luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
            color.rgb = clamp(mix(half3(luma), color.rgb, half(saturation)), 0.0, 1.0);
            color.rgb = mix(color.rgb, half3(dim.rgb), half(dim.a));

            half lit = half(clamp(rim * rimBand * (0.18 + 0.5 * light) + reflection * bend * 0.12 * light, 0.0, 1.0));
            half dark = half(clamp(shadow * bend * 0.06 * shade, 0.0, 1.0));
            color.rgb += half3(lit);
            color.rgb -= half3(dark * color.a);
            color.a = clamp(max(color.a, lit + dark), 0.0, 1.0);
            color.rgb = clamp(color.rgb, 0.0, color.a);
            if (color.a < 0.02) return half4(0.0);
            return color;
        }
    """

    fun create(): RuntimeShader = RuntimeShader(SOURCE)

    fun join(shader: RuntimeShader, join: GlassJoin, offset: Float, scale: Float) {
        val rect = join.rect
        if (rect.isEmpty) return
        val width = rect.width() * scale
        val height = rect.height() * scale
        val maxRadius = minOf(width, height) / 2f
        val radii = join.radii
        shader.setFloatUniform("join", 1f)
        shader.setFloatUniform("joinOrigin", offset + rect.left * scale, offset + rect.top * scale)
        shader.setFloatUniform("joinSize", width, height)
        shader.setFloatUniform(
            "joinRadii",
            (radii[0] * scale).coerceIn(0f, maxRadius),
            (radii[2] * scale).coerceIn(0f, maxRadius),
            (radii[4] * scale).coerceIn(0f, maxRadius),
            (radii[6] * scale).coerceIn(0f, maxRadius)
        )
    }

    fun update(
        shader: RuntimeShader,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        radii: FloatArray,
        tint: Int,
        style: LiquidGlassStyle,
        resolution: Float = 1f
    ) {
        val base = (minOf(width, height) * REFRACTION_RATIO).coerceAtMost(MAX_EDGE_PX * resolution)
        shader.setFloatUniform("origin", originX, originY)
        shader.setFloatUniform("size", width, height)
        val maxRadius = minOf(width, height) / 2f
        shader.setFloatUniform(
            "radii",
            radii[0].coerceIn(0f, maxRadius),
            radii[1].coerceIn(0f, maxRadius),
            radii[2].coerceIn(0f, maxRadius),
            radii[3].coerceIn(0f, maxRadius)
        )
        shader.setFloatUniform("refraction", base * style.refraction)
        shader.setFloatUniform("edge", base.coerceAtLeast(1f))
        shader.setFloatUniform("dispersion", DISPERSION)
        shader.setFloatUniform("rim", style.rim)
        shader.setFloatUniform("overlay", if (style.overlay) 1f else 0f)
        shader.setFloatUniform("rimWidth", (RIM_WIDTH * resolution).coerceAtLeast(1f))
        shader.setFloatUniform("reflection", style.reflection)
        shader.setFloatUniform("shadow", style.shadow)
        shader.setFloatUniform("saturation", SATURATION)
        shader.setFloatUniform("join", 0f)
        shader.setFloatUniform(
            "dim",
            Color.red(tint) / 255f,
            Color.green(tint) / 255f,
            Color.blue(tint) / 255f,
            Color.alpha(tint) / 255f
        )
    }
}
