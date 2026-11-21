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
import com.umutk.pulse.extras.gamehub.cdshelf.internal.inOutCubic
import com.umutk.pulse.extras.gamehub.cdshelf.internal.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The motion is the reference's, key for key: the open and the close are
 * hand-keyed curves for the turn, the lid and the spread, smoothed with
 * monotone cubic splines, and a grabbed case springs back when let go.
 */

private const val TWO_PI = (2.0 * PI).toFloat()

/** How far open the lid swings, round the hinge. */
private const val OPEN_ANGLE = 3.05f
private const val OPEN_T = 2.3f
private const val CLOSE_T = 1.95f

/** The disc turns this fast, in radians a second, and eases to and from it. */
private val SPIN = 18f / 180f * PI.toFloat()
private const val SPIN_EASE = 0.9f

/** Turn per dp of drag, and how far a drag can turn and tip it. */
private const val GRAB_RATE = 0.008f
private const val GRAB_YAW = 0.9f
private const val GRAB_TILT = 0.45f

/** The spring a let-go case comes back on. */
private const val GRAB_K = 16f
private const val GRAB_C = 5.5f

/** A let-go after the finger has been still this long throws nothing. */
private const val STILL_MS = 90L

private val openYaw = monotone(
    0f to 0f, 0.55f to 0.3f, 1.0f to 0.85f, 1.2f to 2.0f, 1.41f to 3.4f,
    1.63f to 4.9f, 1.85f to 6.05f, 2.05f to 6.36f, 2.3f to TWO_PI,
)
private val openLid = monotone(
    0f to 0f, 0.97f to 0f, 1.2f to 0.45f, 1.41f to 1.35f, 1.63f to 2.3f,
    1.85f to 2.92f, 2.05f to 3.12f, 2.3f to OPEN_ANGLE,
)
private val openSpread = monotone(0f to 0f, 1.12f to 0f, 1.9f to 0.9f, 2.3f to 1f)
private val openPitch = monotone(0f to 0f, 1.35f to 0f, 2.3f to -0.1f)

private val closeLid = monotone(
    0f to OPEN_ANGLE, 0.22f to 1.75f, 0.48f to 1.55f, 1.05f to 1.08f, 1.42f to 0.6f,
    1.6f to 0.26f, 1.7f to 0f, 1.78f to 0.07f, 1.88f to 0f,
)
private val closeSpread = monotone(0f to 1f, 0.3f to 0.92f, 1.2f to 0.55f, 1.72f to 0f)
private val closeYaw = monotone(0f to 0f, 0.5f to 0.2f, 1.3f to 0.12f, 1.88f to 0f)
private val closePitch = monotone(
    0f to -0.1f, 1.6f to -0.05f, 1.7f to 0.035f, 1.82f to -0.01f, 1.95f to 0f,
)
private val closeRoll = monotone(0f to 0f, 0.8f to -0.05f, 1.95f to 0f)

/*
 * Taking the cover out, in seconds: it lifts off the open lid, then turns
 * over and comes down beside or under the case, which moves the other way,
 * still open, as it goes; it shuts once the cover has cleared it. Stepping
 * back plays the same run in reverse. Putting it back from anywhere else
 * slides it up into the shut lid, face on.
 */
private const val COVER_LIFT_T = 0.45f
private const val COVER_LIFT_MM = 36f
private const val COVER_MOVE_FROM = COVER_LIFT_T
private const val COVER_MOVE_T = 1.35f
private const val COVER_TURN_FROM = COVER_MOVE_FROM + 0.1f
private const val COVER_TURN_T = 1.2f
private const val COVER_OUT_T = COVER_MOVE_FROM + COVER_MOVE_T
private const val SEPARATE_T = COVER_OUT_T + CLOSE_T
private const val COVER_BACK_T = 1.35f
private const val COVER_BACK_ARC_MM = 22f

/*
 * The manual the cover is the front of, once it is out: turned a leaf at a
 * time, each turn one fold over the spine, after Omma's sketchbook. Shut on
 * its front it is -1, open it is a spread, shut on its back it is the count
 * of spreads. Opening it brings it up into the middle of the frame and sends
 * the case up out of the way; shutting it sends it back under the case.
 */
private const val LEAF_T = 0.85f
private val leafEase = cubicBezierEase(0.42f, 0.05f, 0.25f, 1f)

/*
 * Turned straight to a spread further than a leaf away, it riffles there, a
 * leaf at a time, after the sketchbook's opening riffle: each leaf lands as
 * the next lifts, slowest at either end of the run and quickest half way, on
 * half a sine.
 */
private const val RIFFLE_SLOW = 0.2f
private const val RIFFLE_FAST = 0.05f

/** Where the case is this frame: its turn, the lid's swing, how far it has moved over for being open. */
internal class CasePose {
    var x = 0f
    var y = 0f
    var z = 0f
    var scale = 1f
    var yaw = 0f
    var pitch = 0f
    var roll = 0f
    var lid = 0f
    var spread = 0f
    var spin = 0f
}

/** Where the manual is this frame: a leaf turning from [from] to [to], [turn] of the way over. */
internal class BookPose {
    var spreads = 0
    var from = -1
    var to = -1
    var turn = 1f

    /** How fast the leaf is riffling: 0 for none, 1 at the quickest of a run. */
    var riffle = 0f
}

private enum class Phase { Rest, Opening, Open, Closing }

/**
 * The cover: in the lid, on its way out, out under the case, sliding back
 * into the shut lid, or going back the way it came out.
 */
private enum class Cover { In, Leaving, Out, Returning, Docking }

@androidx.compose.runtime.Composable
fun rememberCdCaseState(): CdCaseState = androidx.compose.runtime.remember { CdCaseState() }

/**
 * A single CD case: shut and idling, opening, open, or shutting again. Tap it
 * to open or shut it; drag it to turn it.
 */
class CdCaseState internal constructor() {

    /** Whether the case is open or on its way open. */
    var isOpen by mutableStateOf(false)
        private set

    /**
     * Whether the cover is out of the lid, or on its way out — [inspectCover]
     * takes it out, [returnCover] puts it back.
     */
    var coverOut by mutableStateOf(false)
        private set

    /**
     * The spread the cover's manual is open at, or turning to: -1 shut on its
     * front, the count of spreads shut on its back.
     */
    var manualPage by mutableIntStateOf(-1)
        private set

    /** Bumped once per frame, so the case repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** Whether there is a cover in the lid to take out; set by whatever draws the case. */
    internal var hasCover by mutableStateOf(true)

    /** Spreads in the manual behind the cover, none for a single sheet; set by whatever draws the case. */
    internal var manualSpreads by mutableIntStateOf(0)

    /** The cover going back as it came out, the case opening again the way it shut. */
    private var docking by mutableStateOf(false)

    private var t = 0f
    private var phase = Phase.Rest
    private var phaseAt = 0f
    private var closeAfter = false

    private var cover = Cover.In
    private var coverAt = 0f

    /**
     * How far into taking the cover out, in seconds, case and cover both: it
     * runs on while the cover leaves and back down while it docks.
     */
    private var sep = 0f
    private var inspectAfter = false
    private var returnAfter = false

    private var leafFrom = -1
    private var leafTo = -1
    private var leafAt = 0f
    private var leafT = LEAF_T
    private var returnAfterLeaf = false

    /** A riffle's run: the spread it is going to, the one it set out from, and how fast this leaf is. */
    private var riffling = false
    private var riffleEnd = -1
    private var riffleFrom = -1
    private var riffleSpeed = 0f

    /** The case as last posed, and the cover in its lid as it was when it was taken out. */
    private val last = CasePose()
    private val coverFrom = CasePose()
    private val rest = CasePose()

    private var spin = 0f
    private var spinRate = SPIN

    private var grabbing = false
    private var grabX = 0f
    private var grabY = 0f
    private var grabVx = 0f
    private var grabVy = 0f
    private var flickX = 0f
    private var flickY = 0f
    private var movedAt = 0L

    /** Opens the case, if it is shut. */
    fun open() {
        if (phase != Phase.Rest) return
        phase = Phase.Opening
        phaseAt = t
        isOpen = true
    }

    /** Shuts the case; one still opening shuts as soon as it is open. */
    fun close() {
        if (phase == Phase.Opening) {
            closeAfter = true
            return
        }
        if (phase != Phase.Open) return
        phase = Phase.Closing
        phaseAt = t
        isOpen = false
    }

    fun toggle() {
        when (cover) {
            Cover.In -> if (phase == Phase.Opening || phase == Phase.Open) close() else open()
            else -> returnCover()
        }
    }

    /**
     * Takes the cover out of the open lid: it lifts off, then turns over and
     * comes to rest beside or under the case, which moves the other way and
     * shuts once it is clear. Asked while the case is still opening, it waits
     * until it is open; asked while the cover is going back, it turns round.
     */
    fun inspectCover() {
        if (cover == Cover.Docking) {
            cover = Cover.Leaving
            docking = false
            coverOut = true
            return
        }
        if (cover != Cover.In || !hasCover) return
        if (phase == Phase.Opening) {
            inspectAfter = true
            return
        }
        if (phase != Phase.Open) return
        // The lid's turn as a pose of its own holds only with the case
        // unrolled, as it is while it stands open.
        coverInLid(last, coverFrom)
        cover = Cover.Leaving
        sep = 0f
        coverOut = true
        closeAfter = false
    }

    /**
     * Slides the cover back up into the shut lid; one still coming out goes
     * back once it is out, and an open manual is shut first.
     */
    fun returnCover() {
        when (cover) {
            Cover.Leaving -> returnAfter = true
            Cover.Out -> {
                if (leafFrom != -1 || leafTo != -1) {
                    returnAfterLeaf = true
                    turnTo(-1)
                    return
                }
                cover = Cover.Returning
                coverAt = t
                coverOut = false
            }
            else -> Unit
        }
    }

    /**
     * Puts the cover back as it came out, the whole run in reverse: the case
     * opens again the way it shut and moves back as the cover comes over,
     * and the cover goes down into the lid. One still coming out turns round.
     */
    private fun dockCover() {
        if (cover != Cover.Leaving && cover != Cover.Out) return
        cover = Cover.Docking
        docking = true
        coverOut = false
        returnAfter = false
    }

    /** Whether the cover is out with a manual behind it to leaf through. */
    internal val canRead: Boolean get() = cover == Cover.Out && manualSpreads > 0

    /** Whether the manual is open, or shut on its back: anywhere but shut on its front. */
    internal val manualOpen: Boolean get() = leafTo != -1

    /**
     * Turns the cover's manual a leaf on, or back for a negative [by], from
     * wherever it is heading; a leaf still turning lands at once. Shut, it
     * opens.
     */
    fun turnPage(by: Int) {
        if (!canRead || by == 0) return
        turnTo((leafTo + by.sign).coerceIn(-1, manualSpreads))
    }

    /**
     * Turns the manual straight to [page]: -1 shuts it on its front, the
     * count of spreads on its back. A leaf away it is one fold; further, it
     * riffles there. Riffling, it heads on to the new page, and back the
     * other way from the leaf in the air. A finger dragged along the ruler
     * asks a page at a time, so a fold still turning the way it goes carries
     * on as a riffle's first leaf rather than landing at once.
     */
    internal fun riffleTo(page: Int) {
        if (!canRead) return
        val to = page.coerceIn(-1, manualSpreads)
        if (to == riffleEnd) return
        manualPage = to
        if (riffling) {
            if ((to - leafTo).sign != (leafTo - leafFrom).sign) riffleFrom = leafTo
            riffleEnd = to
            return
        }
        if (abs(leafTo - leafFrom) == 1 && (to - leafTo).sign == (leafTo - leafFrom).sign) {
            // Sped up to a riffle's slowest leaf, as far over as it was.
            val over = ((t - leafAt) / leafT).clamped(0f, 1f)
            leafT = RIFFLE_SLOW
            leafAt = t - over * leafT
            riffling = true
            riffleFrom = leafFrom
            riffleEnd = to
            return
        }
        if (abs(to - leafTo) <= 1) {
            turnTo(to)
            return
        }
        // A fold still turning lands at once, and the run sets out from there.
        riffling = true
        riffleFrom = leafTo
        riffleEnd = to
        nextLeaf(t)
    }

    /** Lifts the riffle's next leaf at [at] seconds, as quick as it is far into the run. */
    private fun nextLeaf(at: Float) {
        val run = abs(riffleEnd - riffleFrom)
        val into = if (run <= 1) 1f else abs(leafTo - riffleFrom).toFloat() / (run - 1)
        riffleSpeed = sin(PI.toFloat() * into.clamped(0f, 1f))
        leafFrom = leafTo
        leafTo += (riffleEnd - leafTo).sign
        leafAt = at
        leafT = lerp(RIFFLE_SLOW, RIFFLE_FAST, riffleSpeed)
    }

    /** Whether the manual is open at a spread, or turning to one, to be read. */
    internal val reading: Boolean get() = canRead && manualPage in 0 until manualSpreads

    /** How far the manual has come up to be read, from 0 under the case to 1 open in the middle. */
    internal val readingShown: Float get() = if (canRead) read() else 0f

    /** Shuts the cover's manual onto its front in one fold, from wherever it is open. */
    fun closeManual() {
        if (cover == Cover.Out) turnTo(-1)
    }

    /** A tap on the manual: shut, it opens; open, it turns the way of the [side] tapped, -1 or 1. */
    internal fun tapManual(side: Int) {
        when {
            leafTo < 0 -> turnPage(1)
            leafTo >= manualSpreads -> turnPage(-1)
            else -> turnPage(side)
        }
    }

    /** One fold to [to]; a riffle stops once the leaf in the air lands. */
    private fun turnTo(to: Int) {
        riffling = false
        riffleEnd = to
        manualPage = to
        if (to == leafTo) return
        leafFrom = leafTo
        leafTo = to
        leafAt = t
        leafT = LEAF_T
        riffleSpeed = 0f
    }

    /** How far over the leaf turning is, eased; 1 with none turning. */
    private fun leafTurn(): Float =
        if (leafFrom == leafTo) 1f else leafEase(((t - leafAt) / leafT).clamped(0f, 1f))

    /** How far the manual has come up to be read, from 0 under the case to 1 open in the middle. */
    private fun read(): Float {
        fun open(at: Int) = if (at in 0 until manualSpreads) 1f else 0f
        return lerp(open(leafFrom), open(leafTo), leafTurn())
    }

    /**
     * Where the case is in its run of steps: 0 shut, 1 open, 2 the cover out,
     * then the cover's manual from 3 — open at its first spread — to the one
     * it is shut on its back at. The step it is heading for.
     */
    val step: Int
        get() = when {
            docking -> 1
            coverOut -> 3 + manualPage
            isOpen -> 1
            else -> 0
        }

    /** The last step there is to reach: the case alone, with its cover, or with its manual too. */
    val lastStep: Int
        get() = when {
            !hasCover -> 1
            manualSpreads <= 0 -> 2
            else -> manualSpreads + 3
        }

    /** Goes on a step: opens the shut case, takes the cover out, then turns the manual a leaf on. */
    fun stepForward() {
        when (step) {
            0 -> open()
            1 -> inspectCover()
            else -> turnPage(1)
        }
    }

    /**
     * Goes back a step, the way it came: the cover going back is the cover
     * coming out played in reverse. Does nothing on the shut case.
     */
    fun stepBack() {
        when (step) {
            0 -> Unit
            1 -> if (!docking) close()
            2 -> dockCover()
            else -> turnPage(-1)
        }
    }

    /** The cover is out of the lid, or on its way out or back. */
    internal val coverLoose: Boolean get() = cover != Cover.In

    /** The cover coming out or going back the way it came, the case keeping time with it. */
    private val separating: Boolean get() = cover == Cover.Leaving || cover == Cover.Docking

    /** Shut and still: not opening, open or closing. */
    internal val shut: Boolean get() = phase == Phase.Rest

    // --- input -----------------------------------------------------------------

    internal fun onGrab() {
        grabbing = true
        flickX = 0f
        flickY = 0f
    }

    /** [dx], [dy] in dp; [millis] is the event's uptime. */
    internal fun onGrabMove(dx: Float, dy: Float, millis: Long) {
        if (!grabbing) return
        grabY = (grabY + dx * GRAB_RATE).clamped(-GRAB_YAW, GRAB_YAW)
        grabX = (grabX + dy * GRAB_RATE).clamped(-GRAB_TILT, GRAB_TILT)
        flickX = dy * GRAB_RATE * 60f
        flickY = dx * GRAB_RATE * 60f
        movedAt = millis
    }

    internal fun onLetGo(millis: Long) {
        if (!grabbing) return
        grabbing = false
        val still = millis - movedAt > STILL_MS
        grabVx = if (still) 0f else flickX
        grabVy = if (still) 0f else flickY
    }

    // --- frame -----------------------------------------------------------------

    internal fun advance(dt: Float, spinning: Boolean) {
        tick++
        t += dt

        val want = if (spinning) SPIN else 0f
        spinRate += (want - spinRate) * (1f - exp(-dt * SPIN_EASE * 3f))
        spin += spinRate * dt
        if (spin > TWO_PI) spin -= TWO_PI

        if (!grabbing) {
            grabVx += (-GRAB_K * grabX - GRAB_C * grabVx) * dt
            grabVy += (-GRAB_K * grabY - GRAB_C * grabVy) * dt
            grabX += grabVx * dt
            grabY += grabVy * dt
        }

        val since = t - phaseAt
        if (separating) {
            // The case keeps to the separation's clock: open until the cover
            // has cleared it, then shutting.
            sep = (sep + if (cover == Cover.Docking) -dt else dt).clamped(0f, SEPARATE_T)
            phase = if (sep < COVER_OUT_T) Phase.Open else Phase.Closing
            isOpen = cover == Cover.Docking || phase == Phase.Open
        } else if (phase == Phase.Opening && since >= OPEN_T) {
            phase = Phase.Open
            phaseAt = t
            if (inspectAfter) {
                inspectAfter = false
                inspectCover()
            } else if (closeAfter) {
                closeAfter = false
                close()
            }
        } else if (phase == Phase.Closing && since >= CLOSE_T) {
            phase = Phase.Rest
            phaseAt = t
        }

        when (cover) {
            // Out once it has landed and the case has shut.
            Cover.Leaving -> if (sep >= SEPARATE_T) {
                cover = Cover.Out
                phase = Phase.Rest
                phaseAt = t
                if (returnAfter) {
                    returnAfter = false
                    returnCover()
                }
            }
            // In once it is down in the lid, the case open again.
            Cover.Docking -> if (sep <= 0f) {
                cover = Cover.In
                docking = false
                phaseAt = t
            }
            Cover.Returning -> if (t - coverAt >= COVER_BACK_T) cover = Cover.In
            else -> Unit
        }

        // Riffling, each leaf lifts as the last lands, on the clock rather
        // than the frame, so a long frame turns more than one.
        while (riffling && leafFrom != leafTo && t - leafAt >= leafT) {
            if (leafTo == riffleEnd) {
                riffling = false
                break
            }
            nextLeaf(leafAt + leafT)
        }
        if (leafFrom != leafTo && t - leafAt >= leafT) {
            leafFrom = leafTo
            riffleSpeed = 0f
            if (returnAfterLeaf && leafTo == -1) {
                returnAfterLeaf = false
                returnCover()
            }
        }
    }

    internal fun pose(out: CasePose, camera: Camera) {
        // Idling, it sways a little, turned a touch off square.
        out.y = 1.8f * sin(t * 1.1f)
        out.yaw = -0.2f + 0.1f * sin(t * 0.9f) + 0.03f * sin(t * 2.1f)
        out.pitch = -0.12f + 0.045f * sin(t * 0.7f)
        out.roll = 0.03f + 0.018f * sin(t * 0.5f)
        out.lid = 0f
        out.spread = 0f

        // Separating, it is posed by the separation's clock, so docking plays
        // the shutting back.
        val since = if (separating) sep - COVER_OUT_T else t - phaseAt
        when (phase) {
            Phase.Rest -> Unit
            Phase.Opening -> {
                val q = minOf(since, OPEN_T)
                out.yaw = lerp(out.yaw, 0f, (q / 1f).clamped(0f, 1f)) + openYaw(q)
                out.lid = openLid(q)
                out.spread = openSpread(q)
                out.pitch = lerp(out.pitch, openPitch(q), (q / 1.45f).clamped(0f, 1f))
                out.roll *= 1f - (q / 1.45f).clamped(0f, 1f)
            }
            Phase.Open -> {
                out.yaw = 0.06f * sin(t * 0.6f)
                out.pitch = -0.1f + 0.02f * sin(t * 0.8f)
                out.roll = 0f
                out.lid = OPEN_ANGLE + 0.025f * sin(t * 1.3f)
                out.spread = 1f
            }
            Phase.Closing -> {
                val q = minOf(since, CLOSE_T)
                val back = inOutCubic(((q - 0.9f) / (CLOSE_T - 0.9f)).clamped(0f, 1f))
                out.yaw = lerp(closeYaw(q), out.yaw, back)
                out.pitch = lerp(closePitch(q), out.pitch, back)
                out.roll = lerp(closeRoll(q), out.roll, back)
                out.lid = closeLid(q)
                out.spread = closeSpread(q)
            }
        }

        out.pitch += grabX
        out.yaw += grabY
        // Open, the pair is centred: the tray moves over by half a case, the
        // lid swung out to its left. With the cover out, the case stands
        // smaller and higher, the cover under it; with its manual open, up
        // out of the frame.
        val room = room()
        val s = lerp(1f, camera.openScale, out.spread) * lerp(1f, camera.pairScale, room)
        out.x = HALF_MM * s * out.spread * 0.92f
        // Beside the cover, the tray goes over to where the shut case will
        // stand as the cover comes the other way, still open, so the lid
        // shuts clear of it.
        if (camera.beside) out.x = lerp(out.x, -camera.pairShift, room)
        out.y += camera.pairRise * room + camera.readRise * read()
        out.z = 0f
        out.scale = s
        out.spin = spin
        copy(out, last)
    }

    /** How far the case has moved over for the cover, from 0 to 1, in step with the cover's own move. */
    private fun room(): Float = when (cover) {
        Cover.In -> 0f
        Cover.Leaving, Cover.Docking -> coverMove()
        Cover.Out -> 1f
        Cover.Returning -> 1f - inOutCubic(((t - coverAt) / COVER_BACK_T).clamped(0f, 1f))
    }

    /** How far the cover has come over from the lid, eased, once it has lifted off it. */
    private fun coverMove(): Float = inOutCubic(((sep - COVER_MOVE_FROM) / COVER_MOVE_T).clamped(0f, 1f))

    /**
     * Where the cover is out of the lid, into [out], with the case at [case]
     * this frame; false while it is in the lid, drawn with it.
     */
    internal fun coverPose(out: CasePose, case: CasePose, camera: Camera): Boolean {
        if (cover == Cover.In) return false
        // At rest under the case, face on, swaying a little out of step with
        // it; open to be read, in the middle and steadier.
        val read = read()
        val sway = 1f - 0.7f * read
        rest.x = lerp(camera.pairAside, 0f, read)
        rest.y = lerp(-camera.pairDrop, 0f, read) + 1.2f * sway * sin(t * 0.95f + 1.3f)
        rest.z = 0f
        rest.pitch = (-0.04f + 0.035f * sin(t * 0.6f + 0.8f)) * sway
        rest.yaw = 0.06f * sway * sin(t * 0.75f + 2.1f)
        rest.roll = (-0.01f + 0.012f * sin(t * 0.45f)) * sway
        rest.scale = lerp(camera.pairScale, camera.readScale, read)
        when (cover) {
            Cover.Leaving, Cover.Out, Cover.Docking -> {
                // Docking, it heads for the lid wherever the case has got to,
                // and lands in it as it now stands.
                if (cover == Cover.Docking && sep < COVER_OUT_T) coverInLid(case, coverFrom)
                val lift = 1f - (1f - (sep / COVER_LIFT_T).clamped(0f, 1f)).let { it * it * it }
                val move = coverMove()
                val turn = inOutCubic(((sep - COVER_TURN_FROM) / COVER_TURN_T).clamped(0f, 1f))
                val from = coverFrom
                out.x = lerp(from.x, rest.x, move)
                out.y = lerp(from.y, rest.y, move)
                out.z = lerp(from.z + COVER_LIFT_MM * from.scale * lift, rest.z, move)
                // Seen from the open lid's side it is the back of the paper;
                // it turns over on its way down.
                out.pitch = lerp(from.pitch, rest.pitch, turn)
                out.yaw = lerp(from.yaw, rest.yaw, turn)
                out.roll = lerp(from.roll, rest.roll, turn)
                out.scale = lerp(from.scale, rest.scale, move)
            }
            else -> {
                // Back into the shut lid, wherever the case has got to.
                coverInLid(case, coverFrom)
                val move = inOutCubic(((t - coverAt) / COVER_BACK_T).clamped(0f, 1f))
                val to = coverFrom
                out.x = lerp(rest.x, to.x, move)
                out.y = lerp(rest.y, to.y, move)
                out.z = lerp(rest.z, to.z, move) + COVER_BACK_ARC_MM * to.scale * sin(PI.toFloat() * move)
                out.pitch = lerp(rest.pitch, to.pitch, move)
                out.yaw = lerp(rest.yaw, to.yaw, move)
                out.roll = lerp(rest.roll, to.roll, move)
                out.scale = lerp(rest.scale, to.scale, move)
            }
        }
        out.lid = 0f
        out.spread = 0f
        out.spin = 0f
        return true
    }

    /** Where the cover's manual is, into [out]; false with no manual behind the cover. */
    internal fun bookPose(out: BookPose): Boolean {
        if (manualSpreads <= 0) return false
        out.spreads = manualSpreads
        out.from = leafFrom
        out.to = leafTo
        out.turn = leafTurn()
        out.riffle = if (leafFrom == leafTo) 0f else riffleSpeed
        return true
    }
}

private fun copy(from: CasePose, to: CasePose) {
    to.x = from.x
    to.y = from.y
    to.z = from.z
    to.scale = from.scale
    to.yaw = from.yaw
    to.pitch = from.pitch
    to.roll = from.roll
    to.lid = from.lid
    to.spread = from.spread
    to.spin = from.spin
}

/**
 * The cover in the lid of a case at [case], as a pose of its own into [out]:
 * the middle of the paper, and the lid's turn. The lid swings about the
 * case's own y after its roll, so this is exact only with [CasePose.roll] or
 * [CasePose.lid] at 0 — open, the case stands unrolled, and shut, the lid is.
 */
private fun coverInLid(case: CasePose, out: CasePose) {
    val r = FloatArray(9)
    val turn = FloatArray(9)
    val lid = FloatArray(9)
    eulerXyz(case.pitch, case.yaw, case.roll, r)
    aboutY(-case.lid, turn)
    times(r, turn, lid)
    val s = case.scale
    // The hinge pin stays put, as the case's own drawing has it.
    val px = HINGE_X - (turn[0] * HINGE_X + turn[2] * HINGE_Z)
    val pz = HINGE_Z - (turn[6] * HINGE_X + turn[8] * HINGE_Z)
    val t = floatArrayOf(case.x, case.y, case.z)
    for (k in 0 until 3) t[k] += s * (r[k * 3] * px + r[k * 3 + 2] * pz) + s * lid[k * 3 + 2] * COVER_Z
    out.x = t[0]
    out.y = t[1]
    out.z = t[2]
    out.pitch = case.pitch
    out.yaw = case.yaw - case.lid
    out.roll = case.roll
    out.scale = s
}

/**
 * A monotone cubic through [keys] (Fritsch–Carlson), flat outside them: it
 * never overshoots a key, which is what lets a lid land on its stop.
 */
private fun monotone(vararg keys: Pair<Float, Float>): (Float) -> Float {
    val n = keys.size
    val ts = FloatArray(n) { keys[it].first }
    val vs = FloatArray(n) { keys[it].second }
    val d = FloatArray(n - 1) { (vs[it + 1] - vs[it]) / (ts[it + 1] - ts[it]) }
    val m = FloatArray(n)
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
            continue
        }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) {
            val k = 3f / sqrt(s)
            m[i] = k * a * d[i]
            m[i + 1] = k * b * d[i]
        }
    }
    return { t ->
        when {
            t <= ts[0] -> vs[0]
            t >= ts[n - 1] -> vs[n - 1]
            else -> {
                var i = 0
                while (t > ts[i + 1]) i++
                val h = ts[i + 1] - ts[i]
                val u = (t - ts[i]) / h
                val u2 = u * u
                val u3 = u2 * u
                (2f * u3 - 3f * u2 + 1f) * vs[i] + (u3 - 2f * u2 + u) * h * m[i] +
                    (-2f * u3 + 3f * u2) * vs[i + 1] + (u3 - u2) * h * m[i + 1]
            }
        }
    }
}
