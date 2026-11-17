package com.drdisagree.glasslayer.xposed.systemui

import android.R as AndroidR
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import java.lang.reflect.Field

internal object ShapePaints {

    private const val MAX_DEPTH = 6

    private val paintFields = object : ClassValue<List<Field>>() {
        override fun computeValue(type: Class<*>): List<Field> {
            val fields = ArrayList<Field>()
            var current: Class<*>? = type
            while (current != null && current != Drawable::class.java && current != Any::class.java) {
                current.declaredFields
                    .filter { it.type == Paint::class.java }
                    .forEach { field ->
                        runCatching { field.isAccessible = true }.onSuccess { fields.add(field) }
                    }
                current = current.superclass
            }
            return fields
        }
    }

    fun hasStroke(drawable: Drawable?): Boolean = find(drawable, 0) { stroked(it) }

    fun hasFill(drawable: Drawable?): Boolean = find(drawable, 0) { filled(it) }

    private fun find(drawable: Drawable?, depth: Int, test: (Drawable) -> Boolean): Boolean {
        if (drawable == null || depth > MAX_DEPTH) return false
        if (test(drawable)) return true
        return when (drawable) {
            is DrawableWrapper -> find(drawable.drawable, depth + 1, test)
            is DrawableContainer -> find(drawable.current, depth + 1, test)
            is LayerDrawable -> (0 until drawable.numberOfLayers).any { index ->
                drawable.getId(index) != AndroidR.id.mask && find(drawable.getDrawable(index), depth + 1, test)
            }
            else -> false
        }
    }

    private fun stroked(drawable: Drawable): Boolean = paints(drawable).any { paint ->
        paint.style == Paint.Style.STROKE && paint.strokeWidth > 0f && Color.alpha(paint.color) > 0
    }

    private fun filled(drawable: Drawable): Boolean = paints(drawable).any { paint ->
        paint.style != Paint.Style.STROKE && paint.xfermode == null && Color.alpha(paint.color) > 0 && paint.alpha > 0
    }

    private fun paints(drawable: Drawable): List<Paint> = paintFields.get(drawable.javaClass).mapNotNull { field ->
        runCatching { field.get(drawable) as? Paint }.getOrNull()
    }
}
