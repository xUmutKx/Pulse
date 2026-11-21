/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf.internal

import kotlin.math.pow

internal const val FRAME_MS = 1000f / 60f

internal fun Float.clamped(lo: Float, hi: Float): Float =
    if (this < lo) lo else if (this > hi) hi else this

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 == edge0) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).clamped(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun inOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/**
 * Converts a per-frame easing coefficient into the equivalent for a frame of
 * [deltaMs]. The published constants are all tuned at 60Hz; without this a
 * 120Hz display would reach the target twice as fast.
 */
internal fun frameRate(perFrame: Float, deltaMs: Float): Float =
    (1f - (1f - perFrame).pow(deltaMs / FRAME_MS)).clamped(0f, 1f)

/** Shortest signed distance from [a] to [b] around a cycle of [count]. */
internal fun wrapDelta(a: Float, b: Float, count: Int): Float {
    val span = count.toFloat()
    var d = (b - a) % span
    if (d > span / 2f) d -= span
    if (d < -span / 2f) d += span
    return d
}

internal const val PI_F: Float = 3.14159265358979f
