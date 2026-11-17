package com.drdisagree.glasslayer.xposed.systemui

import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

internal class TrackGlass(
    private val tag: String,
    private val surfaceKey: String,
    private val dim: (Any) -> Int
) {

    private val nodes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val modifierToOriginal = WeakHashMap<Any, Any>()
    private var pass = 0

    fun wrap(method: Method, args: Array<Any?>) {
        val modifierIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (modifierIndex == -1) return
        val passed = args[modifierIndex]
        val original = passed?.let { modifierToOriginal[it] ?: it } ?: ComposeToolkit.emptyModifier ?: return
        val drawer = ComposeModifiers.contentProxy("$tag.track") { drawScope ->
            pass++
            try {
                drawScope.call("drawContent")
            } finally {
                pass--
            }
        } ?: return
        ComposeModifiers.drawWithContent(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[modifierIndex] = it
            if (passed == null) ComposeToolkit.clearDefaultBit(method, args, modifierIndex)
        }
    }

    fun draw(args: List<Any?>) {
        if (args.size < TRACK_PATH_ARGS) return
        val drawScope = args[0] ?: return
        val node = drawScope.field(DRAW_NODE) ?: return
        if (pass > 0) nodes.add(node) else if (node !in nodes) return
        val offset = args[2] as? Long ?: return
        val size = args[3] as? Long ?: return
        val startRadius = args[5] as? Float ?: return
        val endRadius = args[6] as? Float ?: return
        val vertical = args[1].toString() == VERTICAL
        val left = Float.fromBits((offset ushr 32).toInt())
        val top = Float.fromBits((offset and LOW_BITS).toInt())
        val width = Float.fromBits((size ushr 32).toInt())
        val height = Float.fromBits((size and LOW_BITS).toInt())
        val total = ComposeToolkit.drawSize(drawScope) ?: return
        val start = if (vertical) top else left
        val length = if (vertical) height else width
        val extent = Float.fromBits((if (vertical) total and LOW_BITS else total ushr 32).toInt())
        val key = when {
            start <= EDGE_SLOP -> START_SEGMENT
            start + length >= extent - EDGE_SLOP -> END_SEGMENT
            else -> MIDDLE_SEGMENT
        }
        val radii = if (vertical) {
            floatArrayOf(
                startRadius, startRadius, startRadius, startRadius,
                endRadius, endRadius, endRadius, endRadius
            )
        } else {
            floatArrayOf(
                startRadius, startRadius, endRadius, endRadius,
                endRadius, endRadius, startRadius, startRadius
            )
        }
        val surface = GlassConfig.surface(surfaceKey)
        ComposeLiquidGlass.drawSegment(
            drawScope,
            key,
            left,
            top,
            width,
            height,
            radii,
            surface.blurRadiusPx,
            dim(drawScope),
            surface.style
        )
    }

    private companion object {
        const val VERTICAL = "Vertical"
        const val DRAW_NODE = "drawNode"
        const val EDGE_SLOP = 0.5f
        const val START_SEGMENT = 0
        const val MIDDLE_SEGMENT = 1
        const val END_SEGMENT = 2
        const val TRACK_PATH_ARGS = 7
        const val LOW_BITS = 0xFFFFFFFFL
    }
}
