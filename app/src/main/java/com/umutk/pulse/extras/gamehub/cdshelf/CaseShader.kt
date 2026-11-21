/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect

/**
 * The case lit a pixel at a time, after the reference's own shaders: the
 * studio it reflects, the rods that bend the page and the room through
 * themselves, and the rainbow the disc's tracks throw. Each brush is drawn in
 * the piece's own coordinates under that piece's projection, so it goes into
 * perspective with it, and is handed the camera in those same coordinates.
 */
internal interface CaseShader {
    /** A height map from the ARGB [pixels] worked out for it, [width] by [height]. */
    fun heightMap(pixels: IntArray, width: Int, height: Int): ImageBitmap

    /**
     * The glass of one layer, drawn over its [height] map in that map's own
     * pixels. [left], [top] and [mmPerPx] put a pixel back in millimetres;
     * [z] is the layer's depth; [eye] is the camera and [toWorld] the frame's
     * axes, column-major, both as [Pose] gives them. With [sparks] above
     * nought, the sparks the rods catch instead, for [stars], drawn at one
     * pixel to that many of the map's.
     */
    fun glass(
        height: ImageBitmap,
        left: Float,
        top: Float,
        mmPerPx: Float,
        z: Float,
        eye: FloatArray,
        toWorld: FloatArray,
        thickness: Float,
        overDisc: Boolean,
        sparks: Float,
    ): Brush

    /**
     * The disc, both faces, drawn in its own millimetres about its centre;
     * [art] is the square print laid across its whole diameter.
     */
    fun disc(art: ImageBitmap, eye: FloatArray, toWorld: FloatArray): Brush

    /**
     * Over a layer the sparks were drawn into, the reference's four-point
     * stars they throw, at [strength], ready to lay over the case.
     */
    fun stars(strength: Float): RenderEffect
}


