/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf

import androidx.compose.ui.graphics.Matrix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/*
 * Space, after the reference: millimetres, y up, the camera on the z axis
 * looking down it through a 28° lens. Each piece of the case is drawn in its
 * own frame — x right, y *down* as Compose draws, z out of the lid — so a
 * frame's local y is the world's minus y.
 */

internal const val FOV_DEGREES = 28f

/** The camera stands far enough back that the case fills this much of the height. */
private const val FIT = 0.6f

/** The shut case and its cover under it fill at most this much of the height, this far apart. */
private const val PAIR_FIT = 0.8f
private const val PAIR_GAP = 16f

/** The open manual fills at most this much of the width left between the insets, and of the height. */
private const val READ_ACROSS = 0.96f
private const val READ_TALL = 0.88f

/** Past the frame, how far the case's soft shadow goes up out of it too. */
private const val SHADOW_CLEAR = 16f

/** The light the backdrop's shadows fall from. */
internal val LIGHT = normalized(-0.3f, 0.48f, 1f)

internal fun normalized(x: Float, y: Float, z: Float): FloatArray {
    val l = kotlin.math.sqrt(x * x + y * y + z * z)
    return floatArrayOf(x / l, y / l, z / l)
}

/** A pinhole camera on the z axis, centred on the frame. */
internal class Camera {
    var width = -1f
        private set
    var height = -1f
        private set
    var focal = 1f
        private set
    var distance = 1f
        private set

    private var baseFocal = 1f
    private var baseDistance = 1f

    /**
     * How far the camera has dollied out, keeping what stands at the origin the
     * same size: the lens lengthens as it backs away, so depth flattens out —
     * at a large zoom the view is all but orthographic.
     */
    var zoom = 1f
        set(value) {
            field = value
            focal = baseFocal * value
            distance = baseDistance * value
        }

    /** How small the opened case is drawn, so lid and tray both fit across. */
    var openScale = 1f
        private set

    /**
     * The shut case with its cover out under it: how small the two are drawn
     * so they fit one over the other, how far up the case stands for it and
     * how far down the cover hangs, in millimetres at the case.
     */
    var pairScale = 1f
        private set
    var pairRise = 0f
        private set
    var pairDrop = 0f
        private set

    /**
     * Wider than tall, the cover stands beside the case instead of under it:
     * how far the case goes left and the cover goes right, in millimetres at
     * the case.
     */
    var pairShift = 0f
        private set
    var pairAside = 0f
        private set
    var beside = false
        private set

    /**
     * The manual open in the middle of the frame: how large it is drawn so
     * the spread fits, and how far up the case goes to clear the frame for it.
     */
    var readScale = 1f
        private set
    var readRise = 0f
        private set

    /**
     * Pixels kept clear down either side of the frame for the open manual —
     * its scrubber on the right, its button on the left.
     */
    var readInset = 0f
    private var fittedInset = -1f

    private val cx get() = width / 2f
    private val cy get() = height / 2f

    fun fit(w: Float, h: Float) {
        if (w == width && h == height && readInset == fittedInset) return
        width = w
        height = h
        fittedInset = readInset
        val tanHalf = tan(FOV_DEGREES / 2f / 180f * kotlin.math.PI.toFloat())
        val aspect = w / max(h, 1f)
        baseFocal = h / 2f / tanHalf
        val fitH = CASE_MM / FIT / 2f / tanHalf
        val fitW = CASE_MM * 1.3f / 2f / (tanHalf * aspect)
        baseDistance = max(fitH, fitW)
        zoom = zoom
        val across = 2f * baseDistance * tanHalf * aspect
        openScale = minOf(1.04f, across / (CASE_MM * 2.25f))
        val tall = 2f * baseDistance * tanHalf
        val stack = CASE_MM + PAIR_GAP + 2f * COVER_HALF
        beside = aspect > 1f
        if (beside) {
            pairScale = minOf(1f, across * PAIR_FIT / stack, tall * PAIR_FIT / (2f * COVER_HALF))
            pairShift = pairScale * (stack / 2f - HALF_MM)
            pairAside = pairScale * (stack / 2f - COVER_HALF)
            pairRise = 0f
            pairDrop = 0f
        } else {
            pairScale = minOf(1f, tall * PAIR_FIT / stack)
            pairRise = pairScale * (stack / 2f - HALF_MM)
            pairDrop = pairScale * (stack / 2f - COVER_HALF)
            pairShift = 0f
            pairAside = 0f
        }
        val inset = across * (readInset / max(w, 1f)).coerceIn(0f, 0.3f)
        readScale = minOf((across - 2f * inset) * READ_ACROSS / (4f * COVER_HALF), tall * READ_TALL / (2f * COVER_HALF))
        // Clear of the frame, shadow and all: the shadow falls this far below
        // the case on the backdrop, where the frame stands taller.
        val wall = tall / 2f * (baseDistance - BACKDROP_Z) / baseDistance
        val drop = LIGHT[1] / LIGHT[2] * (HINGE_Z - BACKDROP_Z)
        readRise = wall + HALF_MM + drop + SHADOW_CLEAR
    }

    /** Screen x, y of a world point into [out] at [at]; false if it is behind the lens. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray, at: Int = 0): Boolean {
        val w = distance - z
        if (w <= 1f) return false
        out[at] = cx + focal * x / w
        out[at + 1] = cy - focal * y / w
        return true
    }

    /**
     * The open manual at rest in the middle, on screen, into [out]: its
     * middle's x and y, and its height.
     */
    fun readBox(out: FloatArray) {
        out[0] = cx
        out[1] = cy
        out[2] = scaleAt(0f) * 2f * COVER_HALF * readScale
    }

    /** Screen pixels per world millimetre at depth [z]. */
    fun scaleAt(z: Float): Float = focal / max(distance - z, 1f)

    /**
     * The projective map from a plane to the screen, into [out]: the plane's
     * point (u, v) is the world point `o + u·a + v·b`.
     *
     * Compose's `Matrix` is row-vector × matrix and carries the perspective
     * terms in its fourth column, so rows 0 and 1 are what one unit of u and of
     * v add to the homogeneous screen point, and row 3 is where the origin lands.
     */
    fun planeMatrix(a: FloatArray, b: FloatArray, o: FloatArray, out: Matrix) {
        out.reset()
        out[0, 0] = focal * a[0] - cx * a[2]
        out[0, 1] = -focal * a[1] - cy * a[2]
        out[0, 3] = -a[2]
        out[1, 0] = focal * b[0] - cx * b[2]
        out[1, 1] = -focal * b[1] - cy * b[2]
        out[1, 3] = -b[2]
        val w = distance - o[2]
        out[3, 0] = focal * o[0] + cx * w
        out[3, 1] = -focal * o[1] + cy * w
        out[3, 3] = w
    }
}

/**
 * Where a piece of the case is: world = scale · rotation · local + offset,
 * with the rotation row-major. Local is the drawing frame — y down — so the
 * y column is flipped on the way out.
 */
internal class Pose {
    val r = FloatArray(9).also { it[0] = 1f; it[4] = 1f; it[8] = 1f }
    val t = FloatArray(3)
    var s = 1f

    /** World direction of the frame's x, y (down) and z axes, columns, unscaled. */
    fun axis(column: Int, out: FloatArray) {
        val flip = if (column == 1) -1f else 1f
        out[0] = r[column] * flip
        out[1] = r[3 + column] * flip
        out[2] = r[6 + column] * flip
    }

    /** World point of local [x], [y] (down), [z], into [out]. */
    fun apply(x: Float, y: Float, z: Float, out: FloatArray, at: Int = 0) {
        val yu = -y
        out[at] = s * (r[0] * x + r[1] * yu + r[2] * z) + t[0]
        out[at + 1] = s * (r[3] * x + r[4] * yu + r[5] * z) + t[1]
        out[at + 2] = s * (r[6] * x + r[7] * yu + r[8] * z) + t[2]
    }

    /** World direction of local [x], [y] (down), [z], into [out]. */
    fun turn(x: Float, y: Float, z: Float, out: FloatArray) {
        val yu = -y
        out[0] = r[0] * x + r[1] * yu + r[2] * z
        out[1] = r[3] * x + r[4] * yu + r[5] * z
        out[2] = r[6] * x + r[7] * yu + r[8] * z
    }

    /** The camera, at world (0, 0, [distance]), in this frame's millimetres. */
    fun eye(distance: Float, out: FloatArray) {
        val dx = -t[0]
        val dy = -t[1]
        val dz = distance - t[2]
        // The inverse of a rotation is its transpose; y comes back flipped.
        out[0] = (r[0] * dx + r[3] * dy + r[6] * dz) / s
        out[1] = -(r[1] * dx + r[4] * dy + r[7] * dz) / s
        out[2] = (r[2] * dx + r[5] * dy + r[8] * dz) / s
    }

    /**
     * The frame's axes as a column-major 3×3 — x, y (down), z into the world —
     * the way a shader's `float3x3` uniform takes them.
     */
    fun toWorld(out: FloatArray) {
        out[0] = r[0]; out[1] = r[3]; out[2] = r[6]
        out[3] = -r[1]; out[4] = -r[4]; out[5] = -r[7]
        out[6] = r[2]; out[7] = r[5]; out[8] = r[8]
    }

    /** The plane z = [z] of this frame, as [Camera.planeMatrix] wants it. */
    fun plane(camera: Camera, z: Float, out: Matrix, scratch: PlaneScratch) {
        axis(0, scratch.a)
        axis(1, scratch.b)
        for (k in 0 until 3) {
            scratch.a[k] *= s
            scratch.b[k] *= s
        }
        apply(0f, 0f, z, scratch.o)
        camera.planeMatrix(scratch.a, scratch.b, scratch.o, out)
    }

    /**
     * The shadow plane z = [z] of this frame casts on the backdrop at world
     * z = [floor], along [LIGHT]: each point slides back along the light until
     * it reaches the wall, which is an affine map, so the shadow is one more
     * plane through the same camera.
     */
    fun shadowPlane(camera: Camera, z: Float, floor: Float, out: Matrix, scratch: PlaneScratch) {
        axis(0, scratch.a)
        axis(1, scratch.b)
        for (k in 0 until 3) {
            scratch.a[k] *= s
            scratch.b[k] *= s
        }
        apply(0f, 0f, z, scratch.o)
        cast(scratch.a, 0f)
        cast(scratch.b, 0f)
        cast(scratch.o, floor)
        camera.planeMatrix(scratch.a, scratch.b, scratch.o, out)
    }

    private fun cast(v: FloatArray, floor: Float) {
        val k = (v[2] - floor) / LIGHT[2]
        v[0] -= LIGHT[0] * k
        v[1] -= LIGHT[1] * k
        v[2] -= LIGHT[2] * k
    }

    fun copyFrom(other: Pose) {
        other.r.copyInto(r)
        other.t.copyInto(t)
        s = other.s
    }
}

internal class PlaneScratch {
    val a = FloatArray(3)
    val b = FloatArray(3)
    val o = FloatArray(3)
}

/**
 * Rotation for three.js's 'XYZ' Euler order, into [out] row-major: about x by
 * [x], then y by [y], then z by [z], as one matrix Rx · Ry · Rz.
 */
internal fun eulerXyz(x: Float, y: Float, z: Float, out: FloatArray) {
    val a = cos(x); val b = sin(x)
    val c = cos(y); val d = sin(y)
    val e = cos(z); val f = sin(z)
    out[0] = c * e
    out[1] = -c * f
    out[2] = d
    out[3] = a * f + b * e * d
    out[4] = a * e - b * f * d
    out[5] = -b * c
    out[6] = b * f - a * e * d
    out[7] = b * e + a * f * d
    out[8] = a * c
}

/** [a] · [b], both row-major 3×3, into [out] (which may not be either). */
internal fun times(a: FloatArray, b: FloatArray, out: FloatArray) {
    for (i in 0 until 3) for (j in 0 until 3) {
        out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
    }
}

/** Turn about the y axis, row-major. */
internal fun aboutY(angle: Float, out: FloatArray) {
    val c = cos(angle)
    val s = sin(angle)
    out[0] = c; out[1] = 0f; out[2] = s
    out[3] = 0f; out[4] = 1f; out[5] = 0f
    out[6] = -s; out[7] = 0f; out[8] = c
}

/** Turn about the z axis, row-major. */
internal fun aboutZ(angle: Float, out: FloatArray) {
    val c = cos(angle)
    val s = sin(angle)
    out[0] = c; out[1] = -s; out[2] = 0f
    out[3] = s; out[4] = c; out[5] = 0f
    out[6] = 0f; out[7] = 0f; out[8] = 1f
}
