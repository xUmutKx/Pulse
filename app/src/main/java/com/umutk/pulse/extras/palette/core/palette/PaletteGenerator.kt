package com.umutk.pulse.extras.palette.core.palette

import com.umutk.pulse.extras.palette.core.color.Lab
import com.umutk.pulse.extras.palette.core.color.contrastRatio
import com.umutk.pulse.extras.palette.core.color.toColor
import com.umutk.pulse.extras.palette.core.color.toLab
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural palette synthesis — the offline "AI palette" engine.
 *
 * Instead of a network model we use constrained generative colour theory: pick a
 * hue anchor, apply a harmony rule, then solve for a lightness ramp that is
 * guaranteed monotonic and contrast-safe. Results are deterministic per seed, so
 * "regenerate" is reproducible and shareable.
 */
object PaletteGenerator {

    enum class Harmony { MONOCHROME, ANALOGOUS, COMPLEMENTARY, SPLIT, TRIAD, TETRAD, NEUTRAL_POP }

    data class Recipe(
        val harmony: Harmony = Harmony.ANALOGOUS,
        val count: Int = 5,
        val baseHue: Float = Random.nextFloat() * 360f,
        val chroma: Float = 0.10f,
        val minL: Float = 0.18f,
        val maxL: Float = 0.94f,
        val seed: Long = System.nanoTime()
    )

    fun generate(r: Recipe): ColorPalette {
        val rnd = Random(r.seed)
        val hues = hueSet(r.harmony, r.baseHue, r.count, rnd)
        val n = r.count.coerceAtLeast(2)
        val colors = ArrayList<Int>(n)
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val l = r.minL + (r.maxL - r.minL) * t
            // chroma peaks in the middle of the ramp, like real designer palettes
            val cScale = 1f - (2f * t - 1f) * (2f * t - 1f) * 0.55f
            val c = r.chroma * cScale * (0.85f + rnd.nextFloat() * 0.3f)
            val h = (hues[i % hues.size] + rnd.nextFloat() * 6f - 3f) * (PI / 180f).toFloat()
            colors += Lab(l, c * cos(h), c * sin(h)).toColor()
        }
        return ColorPalette(
            name = nameFor(r.baseHue, r.harmony),
            colors = colors,
            source = "generated"
        )
    }

    /** Derives a palette that harmonises with an already-chosen key colour. */
    fun fromSeedColor(seed: Int, count: Int = 5, harmony: Harmony = Harmony.ANALOGOUS): ColorPalette {
        val lab = seed.toLab()
        val hue = (Math.toDegrees(lab.hue.toDouble()).toFloat() + 360f) % 360f
        return generate(
            Recipe(
                harmony = harmony,
                count = count,
                baseHue = hue,
                chroma = lab.chroma.coerceIn(0.03f, 0.20f),
                seed = seed.toLong()
            )
        ).copy(name = "From colour", source = "seed")
    }

    /** Rebalances any palette so neighbouring steps stay visually separable. */
    fun normalize(p: ColorPalette, minStep: Float = 0.07f): ColorPalette {
        if (p.size < 2) return p
        val labs = p.ramp.map { it.toLab() }.toMutableList()
        for (i in 1 until labs.size) {
            val prev = labs[i - 1]
            if (labs[i].l - prev.l < minStep) labs[i] = labs[i].withL((prev.l + minStep).coerceAtMost(1f))
        }
        return p.copy(colors = labs.map { it.toColor() })
    }

    /** True when a palette can carry legible labels/glyphs at both ends. */
    fun isAccessible(p: ColorPalette): Boolean =
        p.size >= 2 && contrastRatio(p.darkest, p.lightest) >= 4.5f

    private fun hueSet(h: Harmony, base: Float, count: Int, rnd: Random): List<Float> = when (h) {
        Harmony.MONOCHROME -> List(count) { base }
        Harmony.ANALOGOUS -> List(count) { base + (it - count / 2f) * 14f }
        Harmony.COMPLEMENTARY -> List(count) { if (it % 2 == 0) base else base + 180f }
        Harmony.SPLIT -> listOf(base, base + 150f, base + 210f, base + 12f, base - 12f)
        Harmony.TRIAD -> listOf(base, base + 120f, base + 240f, base + 8f, base + 128f)
        Harmony.TETRAD -> listOf(base, base + 90f, base + 180f, base + 270f, base + 45f)
        Harmony.NEUTRAL_POP -> List(count) { if (it == count - 2) base else base + rnd.nextFloat() * 8f - 4f }
    }.map { (it + 720f) % 360f }

    private fun nameFor(hue: Float, h: Harmony): String {
        val family = when (((hue % 360f) + 360f) % 360f) {
            in 0f..14f, in 345f..360f -> "Crimson"
            in 14f..40f -> "Ember"
            in 40f..65f -> "Amber"
            in 65f..95f -> "Citrus"
            in 95f..150f -> "Fern"
            in 150f..185f -> "Jade"
            in 185f..215f -> "Lagoon"
            in 215f..250f -> "Cobalt"
            in 250f..280f -> "Indigo"
            in 280f..310f -> "Lilac"
            else -> "Rose"
        }
        return "$family ${h.name.lowercase().replaceFirstChar { it.uppercase() }}"
    }
}
