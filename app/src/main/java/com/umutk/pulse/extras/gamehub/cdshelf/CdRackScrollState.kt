/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.extras.gamehub.cdshelf.internal.clamped
import com.umutk.pulse.extras.gamehub.cdshelf.internal.cubicBezierEase
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Every timing below was fitted to the rack's position, recovered frame by
 * frame from a recording of the reference: the top-left corner of every tape,
 * pooled, collapses onto one line with one pitch and one number per frame, to
 * a pixel. The reference moves in two ways and they want different curves.
 */

/**
 * A throw: the rack leaves at full speed and eases into the slot. Fitted
 * against a 3.13-tape run in 0.823 s to 0.50 px. The curve starts at
 * 0.501 / 0.283 = 1.770 of its distance per unit of its duration, so a
 * duration of [THROW_SLOPE] x distance / speed hands over at exactly the
 * speed the finger let go at.
 */
private val throwEase = cubicBezierEase(0.283f, 0.501f, 0.698f, 0.955f)
private const val THROW_SLOPE = 1.770f

/**
 * Seconds of parting speed a throw carries the rack on by: the same run went
 * 3.13 tapes from 6.73 tapes a second.
 */
private const val THROW_REACH = 0.465f

/**
 * A run from rest, as a tap or a wheel notch makes: an ease-in-out. The two
 * the reference has, 4.10 tapes in 1.381 s and 6.80 in 1.682 s, fit to 0.6 px
 * each; this is their mean, and the duration grows as the root of the
 * distance, which both come to within five percent of.
 */
private val travelEase = cubicBezierEase(0.280f, 0.027f, 0.248f, 0.935f)
private const val TRAVEL_SECONDS = 0.664f

/** Below this, in tapes a second, a let-go is a drop rather than a throw. */
private const val THROW_FLOOR = 0.4f

/** Drag samples kept to read a throw off the end of a gesture. */
private const val SAMPLES = 5

/** Longest a sample stays worth counting towards the throw, in seconds. */
private const val WINDOW = 0.1f

/**
 * Taking a case out of the rack, in seconds: the rest of the row leaves over
 * the first part, and the case turns to face you and comes forward over the
 * second, the two overlapping. Putting it back runs the same backwards.
 */
internal const val EXIT_SECONDS = 0.9f
internal const val COME_FROM = 0.45f
internal const val COME_SECONDS = 0.7f
internal const val FOCUS_SECONDS = COME_FROM + COME_SECONDS

/** No case chosen. */
internal const val NONE = Int.MIN_VALUE

/**
 * Where the rack has slid to, in tapes.
 *
 * The rack is one number: tape `i` stands `i - position` pitches up the row
 * from the slot, and how high it is lifted is a fixed function of that. So
 * there is nothing per tape to animate — moving the number moves everything.
 */
class CdRackScrollState internal constructor(intro: Int) {

    /** Unbounded; wrapped only when a tape is drawn. */
    internal var position = 0f
        private set

    /** Bumped once per frame, so the rack repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** The tape nearest the slot, unwrapped. Changes only as one passes it. */
    var slotted by mutableIntStateOf(0)
        private set

    /**
     * Whether a case has been taken out of the rack to look at — the rest of
     * the row gone, the case turned to face you, there to drag round and open.
     * [leave] puts it back.
     */
    var inspecting by mutableStateOf(false)
        private set

    /** The case taken out, unwrapped, while it is out or on its way out or back. */
    internal var chosen = NONE
        private set

    /** Seconds into taking it out: 0 in the rack, [FOCUS_SECONDS] all the way out. */
    internal var focus = 0f
        private set

    /** The case once it is out: its turn, its lid. */
    internal val case = CdCaseState()

    /** Tapped, and on its way to the slot to be taken out from there. */
    internal var wanted by mutableIntStateOf(NONE)
        private set

    /**
     * The case whose disc is baked for drawing out of the rack; one tapped
     * waits in the slot for it, so the bake is not done in the frame it leaves.
     */
    internal var primed = NONE

    /** Asked to go back while open: shutting first. */
    private var leaving = false

    /** The rack takes no input while a case is out, or going or coming. */
    internal val racked: Boolean get() = !inspecting && focus <= 0f

    private var dragging = false
    private var moving = false
    private var from = 0f
    private var target = 0
    private var seconds = 0f
    private var clock = 0f
    private var ease = travelEase

    /** Tapes a second, as of the last frame — what a retarget starts from. */
    private var speed = 0f

    private val at = FloatArray(SAMPLES)
    private val of = FloatArray(SAMPLES)
    private var taken = 0

    init {
        if (intro != 0) aim(intro, 0f)
    }

    /** Slides tape [index] into the slot. Unwrapped, so it can go either way round. */
    fun scrollTo(index: Int) {
        if (dragging || !racked) return
        aim(index, speed)
    }

    /** Slides the rack on by [by] tapes from wherever it is heading. */
    fun step(by: Int) {
        if (by == 0 || dragging || !racked) return
        aim((if (moving) target else position.roundToInt()) + by, speed)
    }

    /**
     * Takes case [index] out to look at: it slides to the slot if it is not
     * there, then the rest of the row clears away and it turns to face you.
     */
    fun inspect(index: Int) {
        if (!racked || dragging) return
        wanted = index
        if (moving || index.toFloat() != position) aim(index, speed)
    }

    /**
     * Whether the case out of the rack is open with its cover still in the
     * lid — what [inspectCover] takes out.
     */
    val canInspectCover: Boolean
        get() = inspecting && case.isOpen && !case.coverOut && case.hasCover

    /**
     * Takes the cover out of the open case: it turns over and comes to rest
     * under the case, which shuts and rises to make room. [leave], or a tap
     * on either, puts it back.
     */
    fun inspectCover() {
        if (inspecting && focus >= FOCUS_SECONDS) case.inspectCover()
    }

    /**
     * Where the case out is in its run of steps — 0 shut, 1 open, 2 its cover
     * out, then each spread of the cover's manual — and the last of them.
     */
    val step: Int get() = case.step
    val lastStep: Int get() = case.lastStep

    /** Goes on a step: opens the case, takes its cover out, turns the manual a leaf on. */
    fun stepForward() {
        if (inspecting && focus >= FOCUS_SECONDS) case.stepForward()
    }

    /** Goes back a step; from the shut case, [leave]s it. */
    fun stepBack() {
        if (!inspecting || focus < FOCUS_SECONDS) return
        if (case.step == 0) leave() else case.stepBack()
    }

    /**
     * Puts the case back in the rack, shutting it first if it is open; with
     * its cover out, puts that back first, and with the cover's manual open,
     * shuts that before anything.
     */
    fun leave() {
        if (!inspecting) return
        if (case.coverLoose) {
            if (case.manualOpen) case.closeManual() else case.returnCover()
            return
        }
        if (!case.shut) {
            case.close()
            leaving = true
            return
        }
        inspecting = false
    }

    // --- input ---------------------------------------------------------------

    internal fun onDragStart(seconds: Float) {
        if (!racked) return
        wanted = NONE
        dragging = true
        moving = false
        speed = 0f
        taken = 0
        sample(seconds)
    }

    /** [by] is the drag down the row, already in tapes. */
    internal fun onDrag(by: Float, seconds: Float) {
        if (!dragging) return
        position += by
        sample(seconds)
    }

    internal fun onDragEnd() {
        if (!dragging) return
        dragging = false
        val parting = partingSpeed()
        aim((position + parting * THROW_REACH).roundToInt(), parting)
    }

    private fun aim(to: Int, startSpeed: Float) {
        val distance = to - position
        target = to
        from = position
        clock = 0f
        if (abs(distance) < 1e-4f) {
            moving = false
            position = to.toFloat()
            return
        }
        moving = true
        if (startSpeed * distance > 0f && abs(startSpeed) > THROW_FLOOR) {
            // Already going the right way: leave at that speed.
            ease = throwEase
            seconds = (THROW_SLOPE * abs(distance) / abs(startSpeed)).clamped(0.25f, 1.4f)
        } else {
            ease = travelEase
            seconds = (TRAVEL_SECONDS * sqrt(abs(distance))).clamped(0.35f, 2.2f)
        }
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float) {
        tick++
        val dt = deltaMs / 1000f
        val before = position
        if (moving) {
            clock += dt
            val t = (clock / seconds).clamped(0f, 1f)
            position = from + (target - from) * ease(t)
            if (t >= 1f) {
                position = target.toFloat()
                moving = false
            }
        }
        if (!dragging) speed = if (dt > 0f) (position - before) / dt else 0f
        val nearest = position.roundToInt()
        if (nearest != slotted) slotted = nearest

        // Taking a case out, once it has come to rest in the slot.
        if (wanted != NONE && primed == wanted && !moving && !dragging && position == wanted.toFloat()) {
            chosen = wanted
            wanted = NONE
            inspecting = true
        }
        if (chosen != NONE) case.advance(dt, spinning = true)
        if (leaving && case.shut) {
            leaving = false
            inspecting = false
        }
        focus = if (inspecting) minOf(focus + dt, FOCUS_SECONDS) else maxOf(focus - dt, 0f)
        if (!inspecting && focus <= 0f) chosen = NONE
    }

    // --- throw ---------------------------------------------------------------

    private fun sample(seconds: Float) {
        at[taken % SAMPLES] = seconds
        of[taken % SAMPLES] = position
        taken++
    }

    /** Tapes a second over the tail of the drag, as it was let go. */
    private fun partingSpeed(): Float {
        if (taken < 2) return 0f
        val newest = (taken - 1) % SAMPLES
        var oldest = newest
        for (i in 1 until minOf(taken, SAMPLES)) {
            val index = (taken - 1 - i + SAMPLES * SAMPLES) % SAMPLES
            if (at[newest] - at[index] > WINDOW) break
            oldest = index
        }
        val span = at[newest] - at[oldest]
        return if (span <= 1e-4f) 0f else (of[newest] - of[oldest]) / span
    }
}

/** Index of tape [index] of the endless rack, wrapped into [count]. */
internal fun wrapCase(index: Int, count: Int): Int = ((index % count) + count) % count
