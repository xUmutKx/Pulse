package com.umutk.pulse.extras.palette.core.palette

import com.umutk.pulse.extras.palette.core.color.Lab
import com.umutk.pulse.extras.palette.core.color.mix
import com.umutk.pulse.extras.palette.core.color.toColor
import com.umutk.pulse.extras.palette.core.color.toHex
import com.umutk.pulse.extras.palette.core.color.toLab

/**
 * A palette is just an ordered colour list, but the engine always consumes it as
 * a *ramp* (sorted dark -> light in Oklab) so that any palette of any size, from
 * two colours to fifty, drives the same generation pipeline.
 */
data class ColorPalette(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Palette",
    val colors: List<Int> = emptyList(),
    val source: String = "manual",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val size: Int get() = colors.size

    /** Colours sorted by perceptual lightness. */
    val ramp: List<Int> by lazy { colors.sortedBy { it.toLab().l } }

    val darkest: Int get() = ramp.firstOrNull() ?: 0xFF1B1B1F.toInt()
    val lightest: Int get() = ramp.lastOrNull() ?: 0xFFF6F2FA.toInt()
    val accent: Int get() = colors.maxByOrNull { it.toLab().chroma } ?: lightest

    /** Continuous sample of the ramp: t in [0,1] -> interpolated colour. */
    fun sample(t: Float): Int {
        if (ramp.isEmpty()) return 0
        if (ramp.size == 1) return ramp[0]
        val x = t.coerceIn(0f, 1f) * (ramp.size - 1)
        val i = x.toInt().coerceAtMost(ramp.size - 2)
        return mix(ramp[i].toLab(), ramp[i + 1].toLab(), x - i).toColor()
    }

    /** Extends a short palette into a smooth [steps]-stop ramp for tinting. */
    fun expanded(steps: Int): List<Int> = List(steps) { sample(it / (steps - 1f)) }

    fun toHexList(): String = colors.joinToString(",") { it.toHex() }

    companion object {
        fun of(vararg hex: Long) = ColorPalette(colors = hex.map { it.toInt() })
    }
}

/** Distance between two colours in Oklab; used for nearest-palette-colour lookup. */
fun labDistance(a: Lab, b: Lab): Float {
    val dl = (a.l - b.l) * 1.6f   // lightness weighted: humans read it first
    val da = a.a - b.a
    val db = a.b - b.b
    return dl * dl + da * da + db * db
}

fun ColorPalette.nearest(color: Int): Int {
    val lab = color.toLab()
    return colors.minByOrNull { labDistance(it.toLab(), lab) } ?: lightest
}

fun ColorPalette.nearestIndex(color: Int): Int {
    val lab = color.toLab()
    return colors.indices.minByOrNull { labDistance(colors[it].toLab(), lab) } ?: 0
}

/** Mean nearest-colour distance between two palettes — lower means "look alike". */
fun paletteDistance(a: ColorPalette, b: ColorPalette): Float {
    if (a.colors.isEmpty() || b.colors.isEmpty()) return Float.MAX_VALUE
    val bLabs = b.colors.map { it.toLab() }
    return a.colors.map { c -> val l = c.toLab(); bLabs.minOf { labDistance(l, it) } }.average().toFloat()
}

/** The [n] curated presets whose overall colours are closest to [target]. */
fun List<ColorPalette>.closestTo(target: ColorPalette, n: Int): List<ColorPalette> =
    sortedBy { paletteDistance(it, target) }.take(n)
