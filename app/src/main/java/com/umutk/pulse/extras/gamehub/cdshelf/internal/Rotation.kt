/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf.internal

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A unit quaternion, held mutably and turned in place.
 *
 * A sphere that is dragged from any direction has no sensible pair of angles to
 * store: yaw and pitch would gimbal-lock at the poles and, worse, would make a
 * diagonal drag arrive as two separate turns. A quaternion takes the drag as
 * what it is — one rotation about one axis — and composes cleanly with the next
 * one whatever direction it comes from.
 */
internal class Quat(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f,
) {
    fun normalize() {
        val length = sqrt(x * x + y * y + z * z + w * w)
        if (length <= 1e-9f) {
            x = 0f; y = 0f; z = 0f; w = 1f
            return
        }
        x /= length; y /= length; z /= length; w /= length
    }

    /** Turns [angle] radians about a unit axis, before whatever turn is held. */
    fun turn(ax: Float, ay: Float, az: Float, angle: Float) {
        val s = sin(angle / 2f)
        val bx = ax * s
        val by = ay * s
        val bz = az * s
        val bw = cos(angle / 2f)
        val nx = bw * x + bx * w + by * z - bz * y
        val ny = bw * y - bx * z + by * w + bz * x
        val nz = bw * z + bx * y - by * x + bz * w
        val nw = bw * w - bx * x - by * y - bz * z
        x = nx; y = ny; z = nz; w = nw
        normalize()
    }

    /** Rotates a vector, writing x, y, z into [out]. */
    fun rotate(vx: Float, vy: Float, vz: Float, out: FloatArray) {
        val tx = 2f * (y * vz - z * vy)
        val ty = 2f * (z * vx - x * vz)
        val tz = 2f * (x * vy - y * vx)
        out[0] = vx + w * tx + (y * tz - z * ty)
        out[1] = vy + w * ty + (z * tx - x * tz)
        out[2] = vz + w * tz + (x * ty - y * tx)
    }

    /** Only the z of the rotated vector, for the many places that is all that is wanted. */
    fun rotatedZ(vx: Float, vy: Float, vz: Float): Float {
        val tx = 2f * (y * vz - z * vy)
        val ty = 2f * (z * vx - x * vz)
        val tz = 2f * (x * vy - y * vx)
        return vz + w * tz + (x * ty - y * tx)
    }
}

/**
 * Points spread as evenly over a sphere as a simple rule can manage, by walking
 * the golden angle while stepping down the axis. There is no exact answer for
 * an arbitrary count — the five platonic solids are the only perfect cases — and
 * this one has no seam, no pole cluster and no preferred direction, which is
 * what a sphere that can be dragged any way needs.
 */
internal fun fibonacciSphere(count: Int): FloatArray {
    val golden = PI_F * (3f - sqrt(5f))
    val points = FloatArray(count * 3)
    for (i in 0 until count) {
        val y = 1f - (2f * (i + 0.5f)) / count
        val ring = sqrt((1f - y * y).coerceAtLeast(0f))
        points[i * 3] = cos(golden * i) * ring
        points[i * 3 + 1] = y
        points[i * 3 + 2] = sin(golden * i) * ring
    }
    return points
}

internal fun Quat.setTo(other: Quat) {
    x = other.x; y = other.y; z = other.z; w = other.w
}

internal fun Quat.dot(other: Quat): Float =
    x * other.x + y * other.y + z * other.z + w * other.w

/** Composes [other] before the turn already held, as `other * this`. */
internal fun Quat.premultiplyBy(ox: Float, oy: Float, oz: Float, ow: Float) {
    val nx = ow * x + ox * w + oy * z - oz * y
    val ny = ow * y - ox * z + oy * w + oz * x
    val nz = ow * z + ox * y - oy * x + oz * w
    val nw = ow * w - ox * x - oy * y - oz * z
    x = nx; y = ny; z = nz; w = nw
    normalize()
}

/**
 * The shortest turn taking one direction onto another, written into [out].
 * Both are assumed unit length. Opposed directions have no shortest turn — any
 * half-turn through a perpendicular axis will do — so one is picked.
 */
internal fun quatBetween(
    fx: Float, fy: Float, fz: Float,
    tx: Float, ty: Float, tz: Float,
    out: Quat,
) {
    val c = (fx * tx + fy * ty + fz * tz).coerceIn(-1f, 1f)
    if (c > 0.99999f) {
        out.x = 0f; out.y = 0f; out.z = 0f; out.w = 1f
        return
    }
    var ax: Float
    var ay: Float
    var az: Float
    if (c < -0.99999f) {
        ax = 0f; ay = 1f; az = 0f
    } else {
        ax = fy * tz - fz * ty
        ay = fz * tx - fx * tz
        az = fx * ty - fy * tx
    }
    val length = sqrt(ax * ax + ay * ay + az * az)
    if (length <= 1e-9f) {
        out.x = 0f; out.y = 0f; out.z = 0f; out.w = 1f
        return
    }
    ax /= length; ay /= length; az /= length
    val angle = acos(c)
    val s = sin(angle / 2f)
    out.x = ax * s; out.y = ay * s; out.z = az * s; out.w = cos(angle / 2f)
}

/**
 * Turns along the shortest path between two orientations, written into [out].
 *
 * A quaternion and its negation are the same orientation, so the nearer of the
 * two is taken first — without that, half the turns would go the long way
 * round. Very close orientations fall back to a straight blend, where the
 * angle between them is too small to divide by.
 */
internal fun slerp(a: Quat, b: Quat, t: Float, out: Quat) {
    var bx = b.x; var by = b.y; var bz = b.z; var bw = b.w
    var c = a.dot(b)
    if (c < 0f) {
        bx = -bx; by = -by; bz = -bz; bw = -bw
        c = -c
    }
    if (c > 0.9995f) {
        out.x = a.x + (bx - a.x) * t
        out.y = a.y + (by - a.y) * t
        out.z = a.z + (bz - a.z) * t
        out.w = a.w + (bw - a.w) * t
        out.normalize()
        return
    }
    val theta = acos(c.coerceIn(-1f, 1f))
    val s = sin(theta)
    val ka = sin((1f - t) * theta) / s
    val kb = sin(t * theta) / s
    out.x = a.x * ka + bx * kb
    out.y = a.y * ka + by * kb
    out.z = a.z * ka + bz * kb
    out.w = a.w * ka + bw * kb
}

/**
 * A cubic bezier easing curve, solved by Newton's method the way browsers do.
 * Six iterations is enough at the precision a frame can show.
 */
internal fun cubicBezierEase(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float = { x ->
    var t = x
    repeat(6) {
        val one = 1f - t
        val cx = 3f * x1 * t * one * one + 3f * x2 * t * t * one + t * t * t - x
        val d = 3f * x1 * one * one + 6f * (x2 - x1) * t * one + 3f * (1f - x2) * t * t
        if (d > -1e-6f && d < 1e-6f) return@repeat
        t = (t - cx / d).coerceIn(0f, 1f)
    }
    val one = 1f - t
    3f * y1 * t * one * one + 3f * y2 * t * t * one + t * t * t
}

/** Fibonacci sphere with a phase offset, so two fields do not line up. */
internal fun fibonacciPoint(count: Int, index: Int, offset: Float, out: FloatArray) {
    val y = 1f - (2f * (index + 0.5f)) / count
    val r = sqrt((1f - y * y).coerceAtLeast(0f))
    val t = PI_F * (3f - sqrt(5f)) * index + offset
    out[0] = cos(t) * r
    out[1] = y
    out[2] = sin(t) * r
}
