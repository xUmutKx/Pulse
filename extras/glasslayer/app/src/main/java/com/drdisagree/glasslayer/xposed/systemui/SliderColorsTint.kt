package com.drdisagree.glasslayer.xposed.systemui

import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.field
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.math.roundToInt

internal object SliderColorsTint {

    const val SLIDER_COLORS_CLASS = "androidx.compose.material3.SliderColors"
    private const val OPAQUE = 255

    private val FIELDS = listOf(
        "thumbColor",
        "activeTrackColor",
        "activeTickColor",
        "inactiveTrackColor",
        "inactiveTickColor",
        "disabledThumbColor",
        "disabledActiveTrackColor",
        "disabledActiveTickColor",
        "disabledInactiveTrackColor",
        "disabledInactiveTickColor"
    )

    private val constructor: Constructor<*>? by lazy {
        HookEnv.findClass(SLIDER_COLORS_CLASS)
            ?.declaredConstructors
            ?.firstOrNull { constructor ->
                constructor.parameterTypes.size == FIELDS.size &&
                        constructor.parameterTypes.all { it == Long::class.javaPrimitiveType }
            }
            ?.apply { isAccessible = true }
    }

    fun apply(method: Method, args: Array<Any?>, inactive: Float, active: Float) {
        val index = method.parameterTypes.indexOfFirst { it.name == SLIDER_COLORS_CLASS }
        args.getOrNull(index)?.let { colors -> tinted(colors, inactive, active)?.let { args[index] = it } }
    }

    private fun tinted(original: Any, inactive: Float, active: Float): Any? {
        val values = FIELDS.map { name ->
            val color = original.field(name) as? Long ?: return null
            val factor = when {
                name.contains("InactiveTrack") || name.contains("inactiveTrack") -> inactive
                name.contains("ActiveTrack") || name.contains("activeTrack") -> active
                else -> return@map color
            }
            val argb = ComposeToolkit.toArgb(color) ?: return null
            ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, (OPAQUE * factor).roundToInt().coerceIn(0, OPAQUE)))
        }
        return try {
            constructor?.newInstance(*values.toTypedArray())
        } catch (_: Throwable) {
            null
        }
    }
}
