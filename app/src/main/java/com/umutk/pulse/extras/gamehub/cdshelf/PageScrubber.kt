/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.umutk.pulse.extras.gamehub.cdshelf.internal.clamped
import com.umutk.pulse.extras.gamehub.cdshelf.internal.frameRate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The scrubber under the open manual, measured off a screen recording of a
 * time scrubber filmed on a hand-held phone. Its own ticks are evenly spaced,
 * so a projective fit along them undoes the camera, and everything is in tick
 * pitches:
 *
 * - a tick is 2.15 long, centred on the axis; within 5.79 of the playhead it
 *   grows by up to 0.668 of that on a raised cosine, centred on the playhead
 *   to 0.04 and with no lag at any speed (4,095 ticks over 196 frames, 0.073
 *   of a tick's length rms);
 * - on the same bump the ink deepens to 2.15 times the far ticks', the stroke
 *   staying the width it was;
 * - the last four ticks fade out, and the playhead's travel stops dead on the
 *   one that is gone; the first end is square;
 * - the playhead is 3.94 long, centred, a stroke 1.6 times a tick's, and
 *   follows the finger frame for frame: a 7-tick jump in one frame stops dead
 *   in the next, which no smoothing would allow;
 * - the label is a capsule 5.23 tall, as wide as what it says.
 *
 * Not in the recording, which never lets go: the playhead settling on its
 * spread once the finger is up, or the book is turned some other way.
 *
 * The recording's ruler lies along the bottom; this one stands down the right
 * edge beside the book, so the book keeps the whole height. Its ticks run top
 * to bottom, "up" from the axis is left toward the book, and the label sits
 * level with the playhead on that side, its right end just clear of the
 * swollen ticks.
 *
 * The ruler runs from the booklet shut on its front to shut on its back, the
 * spreads between; each is a page of the label's count.
 */
private const val TICK_HALF = 1.075f
private const val SWELL = 0.668f
private const val SWELL_REACH = 5.79f
private const val INK_FAR = 0.42f
private const val INK_SWELL = 1.15f
private const val FADE_TICKS = 4f
private const val HEAD_UP = 1.95f
private const val HEAD_DOWN = 1.99f
private const val STROKE = 0.22f
private const val HEAD_STROKE = STROKE * 1.6f
private const val PILL_TALL = 5.23f
private const val PILL_CLEAR = HEAD_UP + 1.1f
private const val PILL_PAD = 2.4f

/** About as wide as the label gets, "14 / 14", in dp at the full pitch. */
private const val PILL_WIDE_DP = 60f

/** About as many ticks as the recording's ruler, a whole number to a page. */
private const val TICKS = 44f

/** The tick pitch, in dp, at most; closer beside a short book. */
private const val PITCH_DP = 4.5f

/**
 * Between the book and the left of the label, between the ticks and the right
 * edge of the frame, in dp; and how far it slides in.
 */
private const val GAP_DP = 6f
private const val FLOOR_DP = 14f
private const val RISE_DP = 8f

/** How fast the playhead settles on its spread, a frame at 60Hz. */
private const val SETTLE = 0.2f

/**
 * Frame width the scrubber needs beside the book, in dp: the gap, the label,
 * the ticks either side of the axis and the floor. About what the Exit button
 * needs across the other side, so the book stays in the middle.
 */
internal const val SCRUBBER_ROOM_DP = GAP_DP + PILL_WIDE_DP + FLOOR_DP + (PILL_CLEAR + HEAD_DOWN) * PITCH_DP

/** The playhead's red, as near as a camera's white balance gives it back. */
internal val SCRUBBER_HEAD = Color(0xFFE5303F)

/** A ruler of ticks down the right of the open manual, a playhead dragged along it turning to the page beside it. */
internal class PageScrubber {

    /** Its spreads and the front and back either end. */
    private var pages = 0
    private var perPage = 1

    /** Where the playhead is, in ticks from the first. */
    private var at = 0f
    private var placed = false

    var dragging = false
        private set

    // As last laid out, in pixels: the first tick's y, the axis's x.
    private var top = 0f
    private var axis = 0f
    private var pitch = 1f
    private var shown = 0f
    private var pillWide = 0f

    private val last: Int get() = (pages - 1) * perPage

    /** The page under the playhead, 0 the front. */
    private val page: Int get() = (at / perPage).roundToInt()

    /** A manual of [spreads], none for no ruler. */
    fun setSpreads(spreads: Int) {
        val count = if (spreads > 0) spreads + 2 else 0
        if (count == pages) return
        pages = count
        perPage = if (count > 1) max(1, (TICKS / (count - 1)).roundToInt()) else 1
        placed = false
    }

    /**
     * Down the right of a frame [frameRight] wide, beside a book [bookHeight]
     * tall centred on [centreY]; [shown] from 0 to 1 as the book comes up to
     * be read.
     */
    fun layout(frameRight: Float, centreY: Float, bookHeight: Float, density: Float, shown: Float) {
        // Held out under the finger, though the book shuts and goes down.
        this.shown = if (dragging) 1f else shown
        if (pages < 2) return
        pitch = min(PITCH_DP * density, bookHeight * 0.9f / last)
        top = centreY - last * pitch / 2f
        axis = frameRight - FLOOR_DP * density - HEAD_DOWN * pitch +
            (1f - this.shown) * RISE_DP * density
    }

    /** Whether [x], [y] is on the ruler or its label, while it shows. */
    fun hit(x: Float, y: Float): Boolean =
        shown > 0.5f && pages > 1 &&
            y >= top - 3f * pitch && y <= top + (last + 3) * pitch &&
            x >= axis - PILL_CLEAR * pitch - pillWide && x <= axis + 4f * pitch

    /**
     * The finger down on the ruler, at [y]: the playhead comes to it. The
     * manual's page beside it, -1 shut on its front, its count of spreads shut
     * on its back.
     */
    fun press(y: Float): Int {
        dragging = true
        return follow(y)
    }

    /** The finger moved along the ruler to [y]. The manual's page at the playhead. */
    fun drag(y: Float): Int = follow(y)

    fun release() {
        dragging = false
    }

    private fun follow(y: Float): Int {
        at = ((y - top) / pitch).clamped(0f, last.toFloat())
        return page - 1
    }

    /** With the finger up, the playhead goes to [manualPage], -1 to the count of spreads, where the book is. */
    fun advance(deltaMs: Float, manualPage: Int) {
        if (pages < 2) return
        val target = ((manualPage + 1).coerceIn(0, pages - 1) * perPage).toFloat()
        // Hidden, it waits at the spread the book will open on.
        if (!placed || shown <= 0f) {
            at = target
            placed = true
        }
        if (!dragging) at += (target - at) * frameRate(SETTLE, deltaMs)
    }

    fun draw(scope: DrawScope, measurer: TextMeasurer, ink: Color, paper: Color) = with(scope) {
        if (shown <= 0f || pages < 2) return
        val stroke = STROKE * pitch
        for (k in 0..last) {
            val g = swell(k - at)
            val fade = ((last - k) / FADE_TICKS).clamped(0f, 1f)
            val alpha = INK_FAR * (1f + INK_SWELL * g) * fade * shown
            if (alpha <= 0.004f) continue
            // Round caps reach half a stroke past each end.
            val half = TICK_HALF * (1f + SWELL * g) * pitch - stroke / 2f
            val y = top + k * pitch
            drawLine(ink, Offset(axis - half, y), Offset(axis + half, y), stroke, StrokeCap.Round, alpha = alpha)
        }

        val y = top + at * pitch
        val head = HEAD_STROKE * pitch
        drawLine(
            SCRUBBER_HEAD,
            Offset(axis - HEAD_UP * pitch + head / 2f, y),
            Offset(axis + HEAD_DOWN * pitch - head / 2f, y),
            head,
            StrokeCap.Round,
            alpha = shown,
        )

        val tall = PILL_TALL * pitch
        val text = measurer.measure(
            "${page + 1} / $pages",
            TextStyle(color = paper, fontSize = (tall * 0.4f).toSp(), fontWeight = FontWeight.SemiBold),
        )
        val wide = text.size.width + 2f * PILL_PAD * pitch
        pillWide = wide
        val right = axis - PILL_CLEAR * pitch
        drawRoundRect(
            ink,
            topLeft = Offset(right - wide, y - tall / 2f),
            size = Size(wide, tall),
            cornerRadius = CornerRadius(tall / 2f),
            alpha = shown,
        )
        drawText(
            text,
            topLeft = Offset(right - wide / 2f - text.size.width / 2f, y - text.size.height / 2f),
            alpha = shown,
        )
    }
}

/** How far a tick [from] the playhead, in ticks, has swollen: 1 under it, 0 from [SWELL_REACH] on. */
private fun swell(from: Float): Float {
    val x = abs(from) / SWELL_REACH
    return if (x >= 1f) 0f else 0.5f * (1f + cos(PI.toFloat() * x))
}
