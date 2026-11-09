package com.umutk.pulse.extras.ai.cc

import com.umutk.pulse.R
import com.umutk.pulse.tr

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.MotionEvent
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Always-on status overlay (pill / line / curtain). It never hides on its own:
 * a visible overlay is what keeps Termux alive on the user's device.
 */
class Overlay(base: Context) {
    private val ctx = ContextThemeWrapper(base, android.R.style.Theme_Material)
    private val sys = base
    private var wm = PillAccess.wm(base)
    private var otype = PillAccess.type()
    /** Takes the accessibility layer when the service is on (above the shade and Settings); only while nothing is attached, so add and remove use the same manager. */
    private fun pickLayer() { if (root == null && handle == null && blackLayer == null) { wm = PillAccess.wm(sys); otype = PillAccess.type() } }
    private var root: View? = null
    private var style = ""

    private var mascot: ImageView? = null
    private var spinner: ProgressBar? = null
    private var mark: TextView? = null
    private var glowAnim: ValueAnimator? = null
    private var idleAnim: ValueAnimator? = null
    private var idlePose = ""

    /** The glow follows the choice in Customize right away (none = off). */
    private var bgAnim: ValueAnimator? = null
    /** The pill's optional moving background: off unless the user picks one in Customize. */
    private fun applyPillBg() {
        val cap = capsule ?: return
        val pick = Prefs.pillBg.value
        if (cap.pattern == pick && (pick == "none") == (bgAnim == null)) return
        cap.pattern = pick
        bgAnim?.cancel(); bgAnim = null
        if (pick != "none") bgAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9000; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { cap.phase = it.animatedValue as Float; cap.invalidateSelf() }
            start()
        }
        cap.invalidateSelf()
    }

    private fun applyGlow() {
        val cap = capsule ?: return
        val g = glowArgb(Prefs.pillGlow.value)
        if (cap.glow == g) return
        cap.glow = g; glowAnim?.cancel(); glowAnim = null
        if (g != 0) glowAnim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 2400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; addUpdateListener { cap.glowT = it.animatedValue as Float; cap.invalidateSelf() }; start() }
        cap.invalidateSelf()
    }

    /** While Claude is idle the pill's mascot plays the chosen idle animation (the same motions as the Customize list). */
    private fun updateIdlePose(st: Status) {
        val m = mascot ?: return
        val pose = Prefs.mascotPose.value
        val idle = st == Status.Idle && pose != "idle" && pose != "pc"
        if (!idle) {
            if (idleAnim != null) { idleAnim?.cancel(); idleAnim = null; idlePose = ""; m.translationX = 0f; m.translationY = 0f; m.rotation = 0f; m.rotationY = 0f; m.rotationX = 0f; m.scaleX = 1f; m.scaleY = 1f; m.alpha = 1f }
            return
        }
        if (idleAnim != null && idlePose == pose) return
        idleAnim?.cancel(); idlePose = pose
        idleAnim = ValueAnimator.ofFloat(0f, 1f).apply { duration = Poses.period(pose).toLong(); repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator(); addUpdateListener { viewPose(m, pose, it.animatedFraction) }; start() }
    }

    private fun viewPose(v: View, p: String, t: Float) {
        val s = Math.sin(2 * Math.PI * t).toFloat(); val a = Math.abs(s); val d = v.resources.displayMetrics.density
        when (p) {
            "jump", "hop", "bounce" -> v.translationY = -a * 14f * d
            "spin" -> v.rotationY = t * 360f
            "flip" -> v.rotationX = t * 360f
            "sway", "wobble", "read", "dance" -> v.rotation = s * 8f
            "pulse", "breath", "zoom" -> {}
            "fade", "moonwalk" -> v.alpha = .4f + .6f * a
            "slide" -> v.translationX = s * 14f * d
            else -> {}
        }
    }
    private var buildOn = false
    private var idleNow = false                   // asleep with no build: the pill hides and shows no mark
    private var cycle: ValueAnimator? = null       // a colour cycle: "rainbow" through the hues, "both" between build colour and blue
    private var cycleKey = ""
    private var bodyHue = 0f                      // rainbow body colour: the hue the body is drawn in now
    private var bodyHueAnim: ValueAnimator? = null
    private var pairAnim: ValueAnimator? = null   // turns the pill's outline while two colours show
    private var pcAnim: ValueAnimator? = null     // laptop frames: typing arms, pulsing green light
    private var zAnim: ObjectAnimator? = null      // the drifting "z" of the sleeping pill
    private var asleepHidden = false
    private var sleepTimer: Runnable? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var term: TextView? = null            // Termux-style ">_" shown on the right while a shell command runs
    private var thought: ImageView? = null        // empty thought bubble while Claude thinks
    private var hammerView: ImageView? = null     // the hammer while a build runs (its own view, so it never sits on the bubble)
    private var hammerAnim: ObjectAnimator? = null
    private var thoughtAnim: ObjectAnimator? = null // the thought bubble breathes while Claude thinks
    private var doneSleep = false                  // done for a while: the mascot has gone to sleep, so the mark shows "zZ" instead of the tick
    private var blink: ValueAnimator? = null
    private var label: TextView? = null
    private var sub: TextView? = null
    private var bar: View? = null
    private var pulse: ObjectAnimator? = null
    private var bob: ObjectAnimator? = null
    private var eyes: ObjectAnimator? = null
    private var bMascot: ImageView? = null
    private var bBob: ObjectAnimator? = null
    private var ring: RingView? = null
    private var lastStatus: Status? = null
    private var blackLayer: View? = null
    private var bStatus: TextView? = null
    private var bDet: TextView? = null
    private var bLast: TextView? = null
    private var bBatt: TextView? = null
    private var cache = Triple(Status.Idle, "", "")
    private var lastStyleIn = ""
    private var capsule: Capsule? = null
    private var pillRect: android.graphics.Rect? = null
    private var iconBox: View? = null
    private var pillVertical = false
    private var pillSide = 0
    private var mascotK = 1f                      // how far (in side-widths) the mascot slides while the pill opens; sign = direction
    private var iconK = -1f
    private var ringFrac = 0.3f
    private var evProg = 1f                       // event pill: 0 = just the camera ring, 1 = fully open
    private var evAnim: ValueAnimator? = null
    private var evHold: Runnable? = null
    private var evTarget = -1                    // always-on pill: 1 = open, 0 = closed (animated), -1 = not decided yet
    private var curColor = 0
    private var glide: ValueAnimator? = null
    val buddy = Buddy(base)

    /** The pill's state across a rebuild (the screen turned), so the new view does not replay "something changed" and pop open. */
    private class Keep(val last: Status?, val asleepHidden: Boolean, val prog: Float, val target: Int)
    private var restore: Keep? = null
    private var handle: View? = null                 // invisible strip under the pill: the pill sits where touches often never arrive
    private var dockZone: android.graphics.Rect? = null
    private var buddyFailed = false   // the buddy was asked to show but its window could not be added
    private var hDownX = 0f; private var hDownY = 0f; private var hDownT = 0L
    private var hDragging = false; private var hIgnore = false
    private var taps = 0; private var tapLast = 0L; private var tapRun: Runnable? = null

    init {
        buddy.onDrop = { x, y -> tryDock(x, y) }
        buddy.onDrag = { x, y -> buddy.setDockHint(dockZone?.contains(x.toInt(), y.toInt()) == true) }
    }

    /** Both can be on at once, but the mascot lives in one place: the buddy when it is chosen as home, or always when there is no pill. */
    private fun buddyHome(style: String) = Prefs.buddyOn.value && (Prefs.mascotHome.value == "buddy" || Prefs.mascotHome.value == "both" || style != "pill")

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** The mascot's dance while Claude works: steps through Dance.ROT / Dance.UP at Dance.FRAME_MS per frame, like the notification. */
    // tilt: a standing pill (camera on a side edge) only hops, so the mascot never leans out of the pill
    private fun dancer(v: View, hop: Float, tilt: Boolean = true): ObjectAnimator {
        val n = Dance.ROT.size
        fun kf(f: (Int) -> Float) = Array(n + 1) { i -> android.animation.Keyframe.ofFloat(i / n.toFloat(), f(i % n)) }
        return ObjectAnimator.ofPropertyValuesHolder(v,
            android.animation.PropertyValuesHolder.ofKeyframe(View.ROTATION, *kf { if (tilt) Dance.ROT[it] else 0f }),
            android.animation.PropertyValuesHolder.ofKeyframe(View.TRANSLATION_Y, *kf { -Dance.UP[it] * hop }),
        ).apply {
            duration = Dance.FRAME_MS * n; repeatCount = ValueAnimator.INFINITE
            // frame by frame: snap time to whole frames; smooth: let it glide between them
            interpolator = if (Prefs.danceMode.value == "smooth") android.view.animation.LinearInterpolator()
                           else android.animation.TimeInterpolator { t -> kotlin.math.floor(t * n) / n }
        }
    }

    /** The mascot picture: awake or asleep (closed eyes), with the chosen outfit on its head. */
    private fun mascotDrawable(sleep: Boolean, computer: Boolean = false, alt: Boolean = false): android.graphics.drawable.Drawable {
        val base = mascotDrawableBase(sleep, computer, alt)
        val sc = if (Prefs.sceneInPill.value == "1") Outfit.scene() else 0
        if (sc == 0) return base
        val shade = android.graphics.drawable.ColorDrawable(android.graphics.Color.argb((255 * (Prefs.sceneDim.value.toIntOrNull() ?: 35) / 100), 0, 0, 0))
        return android.graphics.drawable.LayerDrawable(arrayOf(androidx.core.content.ContextCompat.getDrawable(ctx, sc)!!.mutate(), shade, base))
    }

    private var errFace = false

    private fun mascotDrawableBase(sleep: Boolean, computer: Boolean = false, alt: Boolean = false): android.graphics.drawable.Drawable {
        val hat = Outfit.hat()
        val skin = if (sleep) null else Outfit.skinMatrix(hue = bodyHue)   // asleep the body stays the default orange
        fun d(id: Int) = androidx.core.content.ContextCompat.getDrawable(ctx, id)!!.mutate().also { if (skin != null) it.colorFilter = android.graphics.ColorMatrixColorFilter(skin) }
        fun h(id: Int) = Mascots.hat(ctx, id)   // moved and scaled to sit on this character's head
        if (computer) {
            val faces = Mascots.pc(alt).map { d(it) }
            val body: android.graphics.drawable.Drawable = if (faces.size == 1) faces[0] else android.graphics.drawable.LayerDrawable(faces.toTypedArray())
            if (hat == 0) return body
            return android.graphics.drawable.LayerDrawable(arrayOf(body, h(hat)))
        }
        if (errFace) { // error: crossed-out eyes
            val x = d(R.drawable.cc_mascot_x18)
            return if (hat == 0) x else android.graphics.drawable.LayerDrawable(arrayOf(x, h(hat)))
        }
        val blanket = sleep && Prefs.buddyBlanket.value == "1"   // asleep under its blanket, like the buddy
        val worn = Wear.worn(sleep)
        if (hat == 0 && !blanket && worn.isEmpty()) return d(if (sleep) Mascots.r(R.drawable.cc_mascot_sleep) else Mascots.r(R.drawable.cc_mascot))
        // outfit and blanket drawables share a taller 20x18 canvas so the hat fits above the head
        val layers = ArrayList<android.graphics.drawable.Drawable>()
        layers.add(d(if (sleep) Mascots.r(R.drawable.cc_mascot_sleep18) else Mascots.r(R.drawable.cc_mascot18)))
        worn.forEach { layers.add(androidx.core.content.ContextCompat.getDrawable(ctx, it)!!.mutate()) }
        if (blanket) layers.add(androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.cc_blanket)!!.mutate())
        if (hat != 0) h(hat)?.let { layers.add(it) }
        return android.graphics.drawable.LayerDrawable(layers.toTypedArray())
    }

    /** Rainbow body colour: the pill's body turns through the hues. Only the body layer is recoloured (hat and clothes keep theirs). */
    private fun syncBodyRainbow() {
        if (Prefs.mascotSkin.value != "rainbow") { bodyHueAnim?.cancel(); bodyHueAnim = null; bodyHue = 0f; return }
        if (bodyHueAnim != null) return
        var last = -99f
        bodyHueAnim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                val h = it.animatedValue as Float
                if (Math.abs(h - last) < 8f) return@addUpdateListener   // a redraw every few degrees is plenty
                last = h; bodyHue = h
                val dr = mascot?.drawable ?: return@addUpdateListener
                val body = if (dr is android.graphics.drawable.LayerDrawable) dr.getDrawable(0) else dr
                Outfit.skinMatrix("rainbow", h)?.let { body?.colorFilter = android.graphics.ColorMatrixColorFilter(it) }
                mascot?.invalidate()
            }
            start()
        }
    }

    private fun color(st: Status) = when (st) {
        Status.Working -> 0xFFFF9800.toInt()
        Status.Background -> 0xFF2196F3.toInt()
        Status.Done -> 0xFF4CAF50.toInt()
        Status.Error -> 0xFFF44336.toInt()
        Status.Offline -> 0xFF9E9E9E.toInt()
        Status.Idle -> 0xFF757575.toInt()
    }

    private fun text(st: Status, det: String) = when (st) {
        Status.Working, Status.Background -> if (det.isEmpty()) tr("Pulse AI is working…", "Pulse AI çalışıyor…") else "Pulse AI · $det"
        Status.Done -> tr("Pulse AI is done", "Pulse AI bitirdi")
        Status.Error -> tr("Pulse AI hit an error", "Pulse AI hata verdi")
        Status.Offline -> tr("Model not ready", "Model hazır değil")
        Status.Idle -> tr("Pulse AI is idle", "Pulse AI boşta")
    }

    private fun markOf(st: Status) = when (st) {
        Status.Done -> if (doneSleep) "zZ" else "✓"
        Status.Error -> "✕"
        Status.Offline -> "!"
        Status.Idle -> "zZ" // asleep: a small z and a bigger Z, still
        else -> "•"
    }

    fun render(styleIn: String, st: Status, det: String, last: String) {
        syncBodyRainbow()
        val black = styleIn.endsWith("+black")
        val style = styleIn.removeSuffix("+black")
        lastStyleIn = styleIn; cache = Triple(st, det, last)
        if (!Settings.canDrawOverlays(ctx)) { hide(); return }
        // the floating buddy runs next to the bar overlay
        if (buddyHome(style)) { buddy.clearBubbleOnly(); buddy.render(st, det, last, (st == Status.Working && Engine.shell.value) || Engine.demoShell.value || st == Status.Background); buddyFailed = !buddy.active }
        else {
            if (buddy.active) buddy.hide()
            if (style == "pill" && Prefs.pillBubble.value == "1" && pillRect != null && !asleepHidden) buddy.bubbleOnly(st, det, last, pillRect) else buddy.clearBubbleOnly()
        }
        if (style == "off") { hidePill(); return }
        if (style != this.style || root == null || black != (blackLayer != null)) {
            hidePill(); pickLayer(); if (black) showBlack(); show(style)
            restore?.let { lastStatus = it.last; asleepHidden = it.asleepHidden }
        }
        val shell = (st == Status.Working && Engine.shell.value) || Engine.demoShell.value
        val thinking = st == Status.Working && !shell && det == tr("Thinking…", "Düşünüyor…")
        val bash = shell && !buildOn   // one glyph at a time: build (hammer) > Bash (>_) > thinking (bubble) > asleep (zZ)
        idleNow = st == Status.Idle && !buildOn
        val working = st.running
        spinner?.visibility = if (working && style != "pill") View.VISIBLE else View.GONE
        ring?.set(working || buildOn, st != Status.Idle || buildOn)
        applyStateColor(st, shell, thinking)
        // no tick while a build runs or while asleep: the mark only shows Done and errors
        // laptop: swap two frames while it is shown
        if (st == Status.Background || shell) {
            if (pcAnim == null) pcAnim = ValueAnimator.ofInt(0, 1).apply {
                duration = 900; repeatCount = ValueAnimator.INFINITE
                var last = false
                addUpdateListener { val a = it.animatedFraction >= 0.5f; if (a != last) { last = a; mascot?.setImageDrawable(mascotDrawable(false, true, a)) } }
                start()
            }
        } else { pcAnim?.cancel(); pcAnim = null }
        // asleep (idle / done) for a while: hide the pill until Claude works again
        run {
            val secs = Prefs.pillSleepHide.value.toIntOrNull() ?: 0
            if (eventMode() || st.running || st == Status.Error || st == Status.Offline || secs <= 0) {
                sleepTimer?.let { main.removeCallbacks(it) }; sleepTimer = null
                if (asleepHidden) { asleepHidden = false; applyQuiet() }
            } else if (!asleepHidden && sleepTimer == null) {
                sleepTimer = Runnable { sleepTimer = null; asleepHidden = true; applyQuiet() }.also { main.postDelayed(it, secs * 1000L) }
            }
        }
        term?.let { t ->
            // like the colon of a digital clock: the underscore comes and goes
            if (bash && style == "pill") {
                t.visibility = View.VISIBLE
                if (blink == null) blink = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1000; repeatCount = ValueAnimator.INFINITE
                    var on = true
                    addUpdateListener { val n = (it.animatedValue as Float) < 0.5f; if (n != on) { on = n; t.text = if (n) ">_" else "> " } }
                    start()
                }
            } else { blink?.cancel(); blink = null; t.visibility = View.GONE; t.text = ">_" }
        }
        if (st != Status.Done) doneSleep = false
        mark?.text = if (buildOn) buildTag() else markOf(st)
        mark?.textSize = if (buildOn) 8f else 15f
        (mark?.layoutParams as? FrameLayout.LayoutParams)?.let { it.gravity = if (buildOn) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER; mark?.layoutParams = it }
        // the bubble and the build's hammer sit in the same spot: while a build runs, the hammer wins
        // one glyph in the pill at a time: build (hammers and time) > Bash (>_) > thinking (bubble) > asleep or done (mark)
        val glyph = when { buildOn -> "build"; bash -> "bash"; thinking -> "think"; working -> "none"; else -> "mark" }
        hammerView?.visibility = if (glyph == "build") View.VISIBLE else View.GONE
        // the hammer strikes like a nail is being driven in: it swings down and back while a build runs
        hammerView?.let { hv ->
            if (glyph == "build") {
                if (hammerAnim == null) hammerAnim = ObjectAnimator.ofFloat(hv, View.ROTATION, -28f, 4f).apply {
                    duration = 360; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateInterpolator(); start()
                }
            } else { hammerAnim?.cancel(); hammerAnim = null; hv.rotation = 0f }
        }
        mark?.visibility = if (glyph == "build" || glyph == "mark") View.VISIBLE else View.GONE
        thought?.visibility = if (glyph == "think" && style == "pill") View.VISIBLE else View.GONE
        // the bubble swells and shrinks while Claude thinks
        thought?.let { tv ->
            if (glyph == "think" && style == "pill") {
                if (thoughtAnim == null) thoughtAnim = ObjectAnimator.ofPropertyValuesHolder(tv,
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.15f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.15f)).apply {
                    duration = 900; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator(); start()
                }
            } else { thoughtAnim?.cancel(); thoughtAnim = null; tv.scaleX = 1f; tv.scaleY = 1f }
        }
        errFace = st == Status.Error || st == Status.Offline
        mascot?.setImageDrawable(mascotDrawable(idleNow, st == Status.Background || shell))
        updateIdlePose(st)
        applyPillBg()
        applyGlow() // background shell: laptop + blue face; idle: eyes closed + "z"
        // the mascot is out walking as the buddy only while the buddy window really is on screen; if that failed, it comes back into the pill
        // hidden in the pill only while the buddy window is really there; a buddy that is still starting does not make it blink
        mascot?.visibility = if (buddyHome(style) && Prefs.mascotHome.value != "both" && !buddyFailed) View.INVISIBLE else View.VISIBLE
        label?.text = text(st, det)
        if (style == "pill") {
            // idle: the mascot sleeps in the pill (eyes closed, "z"); done turns into sleep after a moment
            if (st == Status.Done) root?.let { r -> r.postDelayed({
                if (lastStatus == Status.Done && !buildOn) { doneSleep = true; mascot?.setImageDrawable(mascotDrawable(true)); mark?.text = markOf(Status.Done) }
            }, 3500) }
            val show = !eventMode()
            val sides = emptyList<View>()
            sides.forEach { v ->
                if (show && v.visibility != View.VISIBLE) { v.visibility = View.VISIBLE; v.scaleX = 0f; v.scaleY = 0f; v.animate().scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(android.view.animation.DecelerateInterpolator()).start() }
                else if (!show) { v.animate().cancel(); v.visibility = View.GONE }
            }
        }
        sub?.text = last.trim().lines().lastOrNull { it.isNotBlank() }?.take(140).orEmpty()
        pulse?.let { if (working) { if (!it.isRunning) it.start() } else { it.cancel(); bar?.alpha = 1f } }
        (root as? LinearLayout)?.let { r ->
            if (working) r.animate().alpha(0.9f).setDuration(1800).withEndAction { r.animate().alpha(1f).setDuration(1800).start() }.start() else { r.animate().cancel(); r.alpha = 1f }
        }
        bob?.let { if (working && st != Status.Background && !shell) { if (!it.isRunning) it.start() } else { it.cancel(); mascot?.translationY = 0f; mascot?.rotation = 0f } }
        if (blackLayer != null) updateBlack(st, det, last)
        // Eyes animation on status change (start/done)
        if (st != lastStatus) {
            lastStatus = st
            if (eventMode()) { if (st != Status.Idle) showEvent() }
            }
        applyQuiet()
        updateZ()
    }

    /** The hammer and the build's elapsed time, two short lines in the pill. */
    private fun buildTag(): String = ""   // no seconds next to the hammer; the hammer alone shows the build

    /** The sleeping pill's "z" floats up and fades in and out, like in the chat header. */
    private fun updateZ() {
        val m = mark ?: return
        // no drifting "z" while asleep: it is just the text, still
        zAnim?.cancel(); zAnim = null; m.alpha = 1f; m.translationY = 0f
    }

    /** Opacity sliders moved: redraw the pill at its current open state. */
    fun refreshOpacity() { if (root != null && style == "pill") applyProg(evProg) }

    /** Re-draw with the last known state (look settings or the shell preview changed). */
    fun redraw() { if (lastStyleIn.isNotEmpty()) render(lastStyleIn, cache.first, cache.second, cache.third) }

    /** While the chat is open the pill/curtain are hidden but stay attached (the window itself keeps Termux alive). */
    private var quiet = false
    fun setQuiet(q: Boolean) { quiet = q; buddy.setQuiet(q); applyQuiet() }
    private fun applyQuiet() {
        // only the island hides; the black layer is the whole point of black mode and stays (the island stays too while it is on)
        // inside the chat the pill is always hidden; a build shows it only when the app is in the background
        // asleep (idle, no build) hides the pill at once, black mode too; a build still shows it in the background
        val hide = (idleNow || asleepHidden || ((quiet || AppState.foreground.value || (eventMode() && evProg <= 0.001f && evAnim?.isRunning != true)) && blackLayer == null)) && !(buildOn && !AppState.foreground.value)
        if (style == "pill" && !eventMode()) {
            val want = if (hide) 0 else 1
            if (want != evTarget) animatePill(want == 1)
        } else root?.visibility = if (hide) View.INVISIBLE else View.VISIBLE
        handle?.visibility = if (hide) View.GONE else View.VISIBLE
        blackLayer?.visibility = View.VISIBLE
    }

    /** Mascot pops bigger (eyes widen) on start/finish, then settles back. */
    // A Gradle build while the pill is up: the pill goes yellow and the mascot pulses; the build's end sends the hearts.
    private var buildWas = false
    private var buildPulse: ObjectAnimator? = null
    private val buildPoll: Runnable = object : Runnable {
        override fun run() {
            val r = root ?: return
            Thread {
                try { Engine.buildLog() } catch (_: Throwable) { }
                val now = Engine.buildAlive
                r.post { if (now != buildWas && root != null) { buildWas = now; applyBuild(now) } else if (now && root != null) redraw() }   // redraw: the elapsed time moves on
            }.start()
            r.postDelayed(this, 3000)
        }
    }

    /** A build starts or ends: the pill, ring and mascot follow it right away (the colour is the build colour while it runs). */
    private fun applyBuild(on: Boolean) {
        buildOn = on
        redraw()
        buildPulse?.cancel(); buildPulse = null
        if (!on) {
            // the build is done: the mascot shows its wide-eyed "wow" face (as the buddy does when held), then goes back
            mascot?.let { m ->
                m.setImageResource(Mascots.r(R.drawable.cc_mascot_wow18))
                m.animate().cancel()
                m.postDelayed({ m.setImageDrawable(mascotDrawable(idleNow)) }, 900)
            }
            hearts()
        }
    }

    /** The pill's outside and ring: the build colour while a build runs, the done colour when Claude is done, the Bash and thinking colours, grey otherwise. "rainbow" cycles; a build while Bash runs turns the ring through both colours. */
    private fun applyStateColor(st: Status, shell: Boolean, thinking: Boolean) {
        // build together with Bash or with thinking: the ring and the outline turn through the build colour and the other one
        val both = buildOn && (shell || thinking)
        val second = if (shell) StateColors.argb(Prefs.bashColor.value, 0f, "blue") else StateColors.argb(Prefs.thinkColor.value, 0f, "white")
        val key = when { buildOn -> Prefs.buildColor.value; shell -> Prefs.bashColor.value; thinking -> Prefs.thinkColor.value; st == Status.Done -> Prefs.doneColor.value; else -> "" }
        ring?.setPair(if (both) StateColors.argb(Prefs.buildColor.value, 0f, "pink") else null, if (both) second else null)
        // the pill's outline turns through the same two colours as the ring
        val pr = if (both) intArrayOf(StateColors.argb(Prefs.buildColor.value, 0f, "pink"), second, StateColors.argb(Prefs.buildColor.value, 0f, "pink")) else null
        capsule?.pair = pr
        if (both && pairAnim == null) pairAnim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { capsule?.spin = it.animatedValue as Float; capsule?.invalidateSelf() }
            start()
        } else if (!both) { pairAnim?.cancel(); pairAnim = null }
        if (key != cycleKey) { cycle?.cancel(); cycle = null; cycleKey = key }
        if (key == "rainbow" && !both) {
            glide?.cancel(); glide = null
            if (cycle == null) cycle = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 4000; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { applyColor(StateColors.argb("rainbow", it.animatedValue as Float, "pink")) }
                start()
            }
            return
        }
        glideTo(when { buildOn -> StateColors.argb(Prefs.buildColor.value, 0f, "pink"); shell -> StateColors.argb(Prefs.bashColor.value, 0f, "blue"); thinking -> StateColors.argb(Prefs.thinkColor.value, 0f, "white"); st == Status.Done -> StateColors.argb(Prefs.doneColor.value, 0f, "green"); else -> color(st) })
    }

    /** Pink pixels leave the mascot's side and fade out inside the pill (no extra view, so the pill does not grow). */
    private fun hearts() {
        val r = root ?: return
        val m = mascot ?: return
        val ov = (r as? android.view.ViewGroup)?.overlay ?: return
        val d = ctx.resources.displayMetrics.density
        val cx = m.x + m.width / 2f; val cy = m.y + m.height / 2f
        val px = 3 * d
        val rnd = java.util.Random()
        repeat(8) { i ->
            val dot = android.graphics.drawable.ColorDrawable(StateColors.argb(Prefs.buildColor.value, 0f, "pink"))
            ov.add(dot)
            val ang = rnd.nextFloat() * 6.2831f
            val dist = (6 + rnd.nextInt(6)) * d
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 800L; startDelay = i * 50L
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    val x = cx + kotlin.math.cos(ang) * dist * t; val y = cy + kotlin.math.sin(ang) * dist * t
                    dot.setBounds((x - px / 2).toInt(), (y - px / 2).toInt(), (x + px / 2).toInt(), (y + px / 2).toInt())
                    dot.alpha = ((1f - t) * 255).toInt()
                    r.invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { ov.remove(dot) }
                })
                start()
            }
        }
    }

    private fun playEyesAnimation(v: View) {
        v.animate().cancel()
        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(800).setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(900).start() }.start()
    }

    /** The pill's background: a capsule that can be drawn narrower than its bounds (centred), so it can grow out of the camera. */
    private class Capsule(var fill: Int, var stroke: Int, val strokePx: Float, val vertical: Boolean = false, val anchor: Float = 0.5f) : android.graphics.drawable.Drawable() {
        var frac = 1f
        var fillMul = 1f
        var glow = 0          // 0 = none, else the glow colour
        var glowT = 0f        // 0..1 breathing
        var pair: IntArray? = null   // two colours at once (build while Bash runs): the outline turns through them
        var spin = 0f
        private var a = 255
        var pattern = "none"   // optional moving gradient behind the pill (Customize), off by default
        var phase = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(c: Canvas) {
            val b = bounds
            val rect: RectF; val r: Float
            if (vertical) {
                val h = b.height() * frac; val top = b.top + (b.height() - h) * anchor
                rect = RectF(b.left.toFloat(), top, b.right.toFloat(), top + h); r = b.width() / 2f
            } else {
                val w = b.width() * frac; val left = b.left + (b.width() - w) * anchor
                rect = RectF(left, b.top.toFloat(), left + w, b.bottom.toFloat()); r = b.height() / 2f
            }
            p.style = Paint.Style.FILL; p.color = fill; p.alpha = (a * fillMul).toInt()
            p.shader = patternShader(rect)
            c.drawRoundRect(rect, r, r, p)
            p.shader = null
            if (glow != 0) {
                // the glow sits behind the mascot's side of the pill and breathes with glowT
                val cx = rect.centerX(); val cy = rect.centerY(); val rad = (if (vertical) rect.width() else rect.height()) * 1.4f
                val col = (glow and 0xFFFFFF) or ((60 + (110 * glowT).toInt()) shl 24)
                p.shader = android.graphics.RadialGradient(cx, cy, rad, col, glow and 0xFFFFFF, android.graphics.Shader.TileMode.CLAMP)
                c.save(); c.clipRect(rect); c.drawRoundRect(rect, r, r, p); c.restore()
                p.shader = null
            }
            if (strokePx > 0f) {
                val h = strokePx / 2f
                p.style = Paint.Style.STROKE; p.strokeWidth = strokePx; p.color = stroke; p.alpha = a
                pair?.let { pr ->
                    val m = android.graphics.Matrix().apply { setRotate(spin, rect.centerX(), rect.centerY()) }
                    p.shader = android.graphics.SweepGradient(rect.centerX(), rect.centerY(), pr, null).also { it.setLocalMatrix(m) }
                }
                c.drawRoundRect(RectF(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h), r - h, r - h, p)
                p.shader = null
            }
        }
        private fun patternShader(r: RectF): android.graphics.Shader? {
            val cols = when (pattern) {
                "aurora" -> intArrayOf(0xFF0E2A2A.toInt(), 0xFF2B1B5C.toInt(), 0xFF0E3B33.toInt())
                "sunset" -> intArrayOf(0xFF3B1F4A.toInt(), 0xFF8A3B2B.toInt(), 0xFF4A1F35.toInt())
                "ocean" -> intArrayOf(0xFF0B2A44.toInt(), 0xFF0F5A6B.toInt(), 0xFF0B2A44.toInt())
                "sakura" -> intArrayOf(0xFF3A1F2B.toInt(), 0xFF5B2C3F.toInt(), 0xFF3A1F2B.toInt())
                else -> return null
            }
            // mirrored tiling: the colours sway back and forth as the phase moves, with no visible seam
            val shift = phase * r.width()
            return android.graphics.LinearGradient(r.left - shift, 0f, r.left - shift + r.width(), 0f, cols, null, android.graphics.Shader.TileMode.MIRROR)
        }
        override fun setAlpha(alpha: Int) { a = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun eventMode() = style == "pill" && Prefs.pillEvents.value == "1"

    /** Colours change gently (orange to blue to green) instead of snapping. */
    private fun glideTo(target: Int) {
        if (curColor == 0) { applyColor(target); return }
        if (target == curColor && glide == null) return
        glide?.cancel()
        glide = ValueAnimator.ofObject(android.animation.ArgbEvaluator(), curColor, target).apply {
            duration = 700
            addUpdateListener { applyColor(it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { glide = null } })
            start()
        }
    }

    private fun applyColor(c: Int) {
        curColor = c
        ring?.setColor(c)
        mark?.setTextColor(c)
        thought?.imageTintList = ColorStateList.valueOf(c)
        hammerView?.imageTintList = ColorStateList.valueOf(c)
        term?.setTextColor(c)
        spinner?.indeterminateTintList = ColorStateList.valueOf(c)
        capsule?.let { it.stroke = c; it.invalidateSelf() }
        bar?.setBackgroundColor(c)
        (root?.background as? GradientDrawable)?.setStroke(dp(2), c)
    }

    /** Opens/closes the event pill: 0 = only the ring around the camera, 1 = the whole pill. */
    private fun applyProg(p: Float) {
        evProg = p
        val ringA = (p / 0.25f).coerceIn(0f, 1f)
        val q = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val all = ((Prefs.pillAlpha.value.toIntOrNull() ?: 100).coerceIn(10, 100)) / 100f
        ring?.alpha = ringA * all
        capsule?.let { it.frac = ringFrac + (1f - ringFrac) * q; it.fillMul = ((Prefs.pillFill.value.toIntOrNull() ?: 100).coerceIn(0, 100)) / 100f; it.alpha = (255 * ringA * all).toInt(); it.invalidateSelf() }
        val sideA = ((q - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val sc = 0.7f + 0.3f * sideA
        mascot?.let { it.alpha = sideA * all; it.scaleX = sc; it.scaleY = sc; if (!pillVertical) it.translationX = (1f - q) * pillSide * 0.9f * mascotK }
        iconBox?.let { it.alpha = sideA * all; it.scaleX = sc; it.scaleY = sc; if (!pillVertical) it.translationX = (1f - q) * pillSide * 0.9f * iconK }
        root?.invalidate()
    }

    /** Something changed: the ring spins once around the camera, then the pill grows out to both sides, stays a while and shrinks back. */
    private fun showEvent() {
        evHold?.let { main.removeCallbacks(it) }; evAnim?.cancel()
        root?.visibility = View.VISIBLE
        handle?.visibility = View.VISIBLE
        ring?.burst()
        val from = evProg
        evAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (1250 * (1f - from) + 350).toLong()
            interpolator = android.animation.TimeInterpolator { t ->
                val e = if (t < 0.3f) 0.25f * (t / 0.3f) else 0.25f + 0.75f * (1f - Math.pow(1.0 - ((t - 0.3f) / 0.7f), 3.0).toFloat())
                from + (e - from).coerceAtLeast(0f)
            }
            addUpdateListener { applyProg(it.animatedValue as Float) }
            start()
        }
        val hold = (Prefs.pillHold.value.toIntOrNull() ?: 4).coerceIn(1, 15) * 1000L
        evHold = Runnable { hideEvent() }.also { main.postDelayed(it, 1300 + hold) }
    }

    /** Always-on pill: opens like the event pill (ring lap, then grows out of the camera) or closes back into it. */
    private fun animatePill(open: Boolean) {
        evTarget = if (open) 1 else 0
        evAnim?.cancel()
        val from = evProg
        if (open) { root?.visibility = View.VISIBLE; if (from < 0.05f) ring?.burst() }
        else if (from <= 0.001f) { root?.visibility = View.INVISIBLE; return }
        evAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (open) (1250 * (1f - from) + 350).toLong() else 750
            interpolator = if (open) android.animation.TimeInterpolator { t ->
                val e = if (t < 0.3f) 0.25f * (t / 0.3f) else 0.25f + 0.75f * (1f - Math.pow(1.0 - ((t - 0.3f) / 0.7f), 3.0).toFloat())
                from + (e - from).coerceAtLeast(0f)
            } else android.animation.TimeInterpolator { t -> from * (1f - android.view.animation.AccelerateDecelerateInterpolator().getInterpolation(t)) }
            addUpdateListener { applyProg(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { evAnim = null; if (!open) root?.visibility = View.INVISIBLE }
            })
            start()
        }
    }

    private fun hideEvent() {
        evAnim?.cancel()
        val from = evProg
        evAnim = ValueAnimator.ofFloat(from, 0f).apply {
            duration = 750; interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { applyProg(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { evAnim = null; applyQuiet() } })
            start()
        }
    }

    /** Thin ring drawn around the camera hole: a spinning arc while working, a full ring otherwise. */
    private class RingView(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private var spin = 0f
        private var working = false
        private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { spin = it.animatedValue as Float; invalidate() }
        }

        private var shown = true
        private var bursting = false
        private var burstAnim: ValueAnimator? = null
        fun setColor(color: Int) { p.color = color; invalidate() }
        /** Two colours at once (a build while Bash runs): the ring becomes one gradient that turns; null = one colour. */
        private var pair: IntArray? = null
        fun setPair(a: Int?, b: Int?) { pair = if (a == null || b == null) null else intArrayOf(a, b, a); invalidate() }
        fun set(working: Boolean, shown: Boolean = true) {
            this.working = working; this.shown = shown
            if (working) { if (!anim.isRunning) anim.start() } else if (!bursting) { anim.cancel(); spin = 0f }
            invalidate()
        }
        /** One quick lap of the arc around the camera. */
        fun burst() {
            burstAnim?.cancel(); bursting = true
            burstAnim = ValueAnimator.ofFloat(0f, 720f).apply {
                duration = 950; interpolator = android.view.animation.DecelerateInterpolator(1.2f)
                addUpdateListener { spin = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { bursting = false; invalidate() } })
                start()
            }
        }

        override fun onDetachedFromWindow() { anim.cancel(); burstAnim?.cancel(); super.onDetachedFromWindow() }

        override fun onDraw(c: Canvas) {
            val d = resources.displayMetrics.density
            if (!shown) return
            p.strokeWidth = 1.2f * d
            val r0 = minOf(width, height) / 2f - p.strokeWidth - 1f // 1px off the radius = 2px smaller diameter
            // the new ring: 1 px smaller in radius (was 3, too tight) and cut 6 px at the top, so it sits round the camera (like the face-scan ring)
            val newRing = Prefs.ringStyle.value != "old"
            val r = if (newRing) r0 - 1 * d else r0
            val top = height / 2f - r + (if (newRing) 6 * d else 0f)
            val box = RectF(width / 2f - r, top, width / 2f + r, height / 2f + r)
            val pr = pair
            if (pr != null) {
                val m = android.graphics.Matrix().apply { setRotate(spin, width / 2f, height / 2f) }
                p.shader = android.graphics.SweepGradient(width / 2f, height / 2f, pr, null).also { it.setLocalMatrix(m) }
                c.drawArc(box, 0f, 360f, false, p); p.shader = null
            } else if (working || bursting) c.drawArc(box, spin, 110f, false, p) else c.drawArc(box, 0f, 360f, false, p)
        }
    }

    private fun params(w: Int, h: Int, flags: Int, gravity: Int, y: Int = 0) =
        WindowManager.LayoutParams(w, h, otype, flags, PixelFormat.TRANSLUCENT).also {
            it.gravity = gravity; it.y = y
        }

    private fun statusBarHeight(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width() else ctx.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height() else ctx.resources.displayMetrics.heightPixels

    private enum class Edge { TOP, LEFT, RIGHT, BOTTOM }

    /**
     * The camera hole in the current rotation and the screen edge it sits on (top in portrait, a side edge in
     * landscape); a centred stand-in at the top when the device has no cutout.
     */
    private fun cameraSpot(): Pair<Edge, android.graphics.Rect> {
        val w = screenWidth(); val h = screenHeight()
        if (Build.VERSION.SDK_INT >= 30) {
            val r = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
                ?.filter { it.width() < w / 2 && it.height() < h / 2 }
                ?.minByOrNull { it.width() * it.height() }
            if (r != null) {
                // nearest edge wins; on a tie top, then left, then right, then bottom
                val d = listOf(Edge.TOP to r.top, Edge.LEFT to r.left, Edge.RIGHT to w - r.right, Edge.BOTTOM to h - r.bottom)
                return d.minByOrNull { it.second }!!.first to android.graphics.Rect(r)
            }
        }
        return Edge.TOP to android.graphics.Rect(w / 2 - dp(14), dp(8), w / 2 + dp(14), dp(8) + dp(28))
    }

    /** The screen was rotated (or folded): put the pill back around the camera. */
    fun onRotate() {
        if (buddy.active) buddy.onRotate()
        if (root == null) return
        val s = lastStyleIn
        // the rebuilt view must come back in the state the old one was in (hidden stays hidden), not replay "status changed"
        restore = Keep(lastStatus, asleepHidden, evProg, evTarget)
        hidePill()
        render(s, cache.first, cache.second, cache.third)
        // an event pill that was open when the screen turned lost its timer with the old view: close it again
        if (eventMode() && evProg > 0.001f) evHold = Runnable { hideEvent() }.also { main.postDelayed(it, 1500) }
        restore = null
    }

    /** Full-screen AMOLED black under the island; keeps the screen on, double-tap leaves. */
    private fun showBlack() {
        val dim = 0xFF9A9A9A.toInt()
        val v = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK); setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        fun tv(size: Float, color: Int = dim, mono: Boolean = false, lines: Int = 1) = TextView(ctx).apply {
            textSize = size; setTextColor(color); gravity = Gravity.CENTER; maxLines = lines
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (mono) typeface = android.graphics.Typeface.MONOSPACE
        }
        fun add(w: View, top: Int = 0) = v.addView(w, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top) })
        if (Prefs.blackMascot.value) {
            val m = ImageView(ctx).apply { setImageDrawable(mascotDrawableBase(idleNow)) }
            bMascot = m; bBob = dancer(m, dp(6).toFloat())
            v.addView(m, LinearLayout.LayoutParams(dp(56), dp(40)).apply { bottomMargin = dp(12) })
        }
        if (Prefs.blackClock.value) {
            val face = clockFont(ctx, Prefs.blackFont.value)
            val size = (Prefs.blackSize.value.toFloatOrNull() ?: 72f).coerceIn(32f, 120f)
            val accent = 0xFFD97757.toInt()
            fun clock(fmt12: String, fmt24: String, color: Int, sp: Float) = android.widget.TextClock(ctx).apply {
                format12Hour = fmt12; format24Hour = fmt24; textSize = sp; setTextColor(color); gravity = Gravity.CENTER; typeface = face
                includeFontPadding = false
            }
            when (Prefs.blackStyle.value) {
                "stacked" -> { // Samsung-style: hours over minutes
                    add(clock("h", "HH", 0xFFE8E8E8.toInt(), size * 1.25f))
                    add(clock("mm", "mm", accent, size * 1.25f), 2)
                }
                "analog", "ticks" -> v.addView(ClockView(ctx, Prefs.blackStyle.value == "analog", face, accent),
                    LinearLayout.LayoutParams(dp((size * 2.6f).toInt()), dp((size * 2.6f).toInt())))
                else -> add(clock("h:mm", "HH:mm", 0xFFD0D0D0.toInt(), size))
            }
        }
        if (Prefs.blackDate.value) add(android.widget.TextClock(ctx).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; textSize = 16f; setTextColor(dim); gravity = Gravity.CENTER
        })
        bStatus = if (Prefs.blackStatus.value) tv(18f).also { add(it, 28) } else null
        bDet = if (Prefs.blackStatus.value) tv(14f).also { add(it, 4) } else null
        bLast = if (Prefs.blackLast.value) tv(13f, 0xFF7A7A7A.toInt(), mono = true, lines = 5).also { add(it, 20) } else null
        bBatt = if (Prefs.blackBattery.value) tv(14f).also { add(it, 24) } else null
        Prefs.blackText.value.takeIf { it.isNotBlank() }?.let { add(tv(16f, 0xFFB0B0B0.toInt(), lines = 3).apply { text = it }, 20) }
        val gd = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean { Prefs.black.value = false; return true }
        })
        v.setOnTouchListener { _, e -> gd.onTouchEvent(e) }
        blackLayer = v
        v.postDelayed(object : Runnable {
            override fun run() { if (blackLayer === v) { bBatt?.text = battText(); v.postDelayed(this, 60_000) } }
        }, 60_000)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        wm.addView(v, params(-1, -1, flags, Gravity.TOP).also {
            it.screenBrightness = (Prefs.blackDim.value.toIntOrNull() ?: 15).coerceIn(1, 100) / 100f // dim to save power
            if (Build.VERSION.SDK_INT >= 28) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        })
        updateBlack(cache.first, cache.second, cache.third)
    }

    private fun battText(): String {
        val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra("level", -1) ?: -1
        val scale = (i?.getIntExtra("scale", 100) ?: 100).coerceAtLeast(1)
        val plugged = (i?.getIntExtra("plugged", 0) ?: 0) != 0
        return if (lvl < 0) "" else "${lvl * 100 / scale}%" + if (plugged) " ⚡" else ""
    }

    /** Fills the black-mode widgets with the current status, what Claude is doing and its last lines. */
    private fun updateBlack(st: Status, det: String, last: String) {
        cache = Triple(st, det, last)
        bStatus?.apply { text = text(st, ""); setTextColor(color(st)) }
        bDet?.text = det
        bLast?.text = last.trim().takeLast(240)
        bBatt?.text = battText()
        bMascot?.setImageDrawable(mascotDrawableBase(idleNow))
        bBob?.let { if (st.running) { if (!it.isRunning) it.start() } else { it.cancel(); bMascot?.translationY = 0f; bMascot?.rotation = 0f } }
    }

    /** Black-mode settings changed: rebuild the layers (black first, island on top). */
    fun refreshBlack() {
        if (blackLayer == null) return
        val s = lastStyleIn
        hidePill()
        render(s, cache.first, cache.second, cache.third)
    }

    private fun show(style: String) {
        pickLayer()
        this.style = style
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        when (style) {
            "line" -> {
                val v = View(ctx)
                bar = v
                pulse = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.35f).apply { duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE }
                root = v
                wm.addView(v, params(WindowManager.LayoutParams.MATCH_PARENT, dp(4),
                    base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, Gravity.TOP))
            }
            "pill" -> { // long thin pill around the camera: dancing mascot left, loading / tick / cross right
                val (edge, cut) = cameraSpot()
                val vertical = edge == Edge.LEFT || edge == Edge.RIGHT // camera on a side edge (landscape): stand the pill up
                val rim = dp(Prefs.pillGap.value.toIntOrNull()?.coerceIn(0, 8) ?: 1) // equal rim on both sides of the camera: more room = the ring floats further out
                val hole = (if (vertical) cut.width() else cut.height()).coerceAtLeast(dp(18))
                val trim = 1 // px taken off each long side of the pill (the pill is 2 px thinner than the camera rim)
                val thick = hole + 2 * rim - 2 * trim - 2 // pill thickness across the edge; 2 px more off the bottom so it clears the bar above
                val ringLen = (if (vertical) cut.height() else cut.width()) + 2 * rim + dp(6)
                val extra = (Prefs.pillExtra.value.toIntOrNull() ?: 0).coerceIn(0, 200) // optional px added to each end (setting, default 0: only as long as the mascot / tick need)
                val side = dp(40) + extra
                // room on both sides of the camera along the edge: centred pill if both fit; a camera near a corner grows the pill one way, away from the edge
                val along = if (vertical) cut.centerY() else cut.centerX()
                val span = if (vertical) screenHeight() else screenWidth()
                val roomA = along - ringLen / 2; val roomB = span - along - ringLen / 2
                val mode = when { roomA >= side && roomB >= side -> 0; roomA < side && roomB >= 2 * side -> 1; roomB < side && roomA >= 2 * side -> 2; else -> 0 }
                mascotK = when (mode) { 1 -> -1f; 2 -> 2f; else -> 1f }
                iconK = when (mode) { 1 -> -2f; 2 -> 1f; else -> -1f }
                val cap = Capsule(if (Prefs.pillColor.value == "white") Color.WHITE else Color.BLACK, Color.TRANSPARENT, dp(2).toFloat(), vertical, when (mode) { 1 -> 0f; 2 -> 1f; else -> 0.5f })
                capsule = cap
                cap.glow = glowArgb(Prefs.pillGlow.value)
                glowAnim?.cancel(); glowAnim = null
                if (cap.glow != 0) glowAnim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 2400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; addUpdateListener { cap.glowT = it.animatedValue as Float; cap.invalidateSelf() }; start() }
                val box = LinearLayout(ctx).apply {
                    orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    background = cap
                }
                fun lp(len: Int) = if (vertical) LinearLayout.LayoutParams(thick, len) else LinearLayout.LayoutParams(len, thick)
                mascot = ImageView(ctx).apply { setImageResource(Mascots.r(R.drawable.cc_mascot)); setPadding(dp(6), dp(3), dp(6), dp(3)) }
                ring = RingView(ctx) // thin ring around the camera hole (spinner while working)
                if (mode == 1) box.addView(ring, lp(ringLen))
                box.addView(mascot, lp(side))
                if (mode == 0) box.addView(ring, lp(ringLen))
                val icon = FrameLayout(ctx)
                spinner = ProgressBar(ctx).apply { isIndeterminate = true }
                mark = TextView(ctx).apply { textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                icon.addView(spinner, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
                icon.addView(mark, FrameLayout.LayoutParams(dp(30), dp(20), Gravity.CENTER))
                term = TextView(ctx).apply {
                    text = ">_"; textSize = 12f; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    visibility = View.GONE
                }
                icon.addView(term, FrameLayout.LayoutParams(dp(30), dp(20), Gravity.CENTER))
                thought = ImageView(ctx).apply { setImageResource(R.drawable.cc_thought); visibility = View.GONE }
                icon.addView(thought, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                hammerView = ImageView(ctx).apply {
                    setImageResource(R.drawable.cc_hammer); visibility = View.GONE
                    // the hammer turns around the bottom end of its handle, which stays fixed; set once the size is known
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.pivotX = v.width * 0.23f; v.pivotY = v.height * 0.9f }
                }
                icon.addView(hammerView, FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER))
                box.addView(icon, lp(side))
                if (mode == 2) box.addView(ring, lp(ringLen))
                iconBox = icon; pillVertical = vertical; pillSide = side
                ringFrac = ringLen.toFloat() / (2 * side + ringLen)
                label = TextView(ctx) // off-screen: the pill is a single strip
                bob = dancer(mascot!!, dp(2).toFloat(), tilt = !vertical)
                root = box
                buildWas = false; box.removeCallbacks(buildPoll); box.postDelayed(buildPoll, 500)
                val total = 2 * side + ringLen // camera sits in the middle segment
                box.setOnTouchListener { v, e -> handleTouch(v, e) }
                val flags = base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                val pillLp = params(-2, -2, flags, Gravity.TOP or Gravity.START, 0).also {
                    // absolute position so the middle segment lands exactly on the camera, whatever the edge
                    val start = when (mode) { 1 -> along - ringLen / 2; 2 -> along + ringLen / 2 - total; else -> along - total / 2 }.coerceIn(0, maxOf(0, span - total))
                    if (vertical) { it.x = (cut.left - rim + trim).coerceAtLeast(0); it.y = start }
                    else { it.x = start; it.y = (cut.top - rim + trim).coerceAtLeast(0) }
                    if (Build.VERSION.SDK_INT >= 30) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else if (Build.VERSION.SDK_INT >= 28) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                wm.addView(box, pillLp)
                pillRect = if (vertical) android.graphics.Rect(pillLp.x, pillLp.y, pillLp.x + thick, pillLp.y + total) else android.graphics.Rect(pillLp.x, pillLp.y, pillLp.x + total, pillLp.y + thick)
                addHandle(edge, pillLp.x, pillLp.y, thick, total, vertical)
                val rs = restore
                if (rs != null) { evTarget = rs.target; applyProg(rs.prog) } else { evTarget = -1; applyProg(0f) }
            }
            "bubble" -> { // a draggable round head: tap opens the chat, drag moves it, it snaps to the nearest side
                val size = dp(58)
                val frame = FrameLayout(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (Prefs.pillColor.value == "white") Color.WHITE else Color.BLACK) }
                    elevation = dp(6).toFloat()
                }
                ring = RingView(ctx); frame.addView(ring, FrameLayout.LayoutParams(size, size))
                mascot = ImageView(ctx).apply { setImageResource(Mascots.r(R.drawable.cc_mascot)) }
                frame.addView(mascot, FrameLayout.LayoutParams(dp(36), dp(28), Gravity.CENTER))
                spinner = null; mark = null; label = TextView(ctx); bob = dancer(mascot!!, dp(2).toFloat())
                root = frame
                val lp = params(size, size, base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, Gravity.TOP or Gravity.START).also {
                    it.x = Prefs.bubbleX.value.toIntOrNull() ?: (screenWidth() - size - dp(8)); it.y = Prefs.bubbleY.value.toIntOrNull() ?: (screenHeight() / 3)
                }
                var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
                frame.setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false; true }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (Math.abs(e.rawX - dx) > dp(6) || Math.abs(e.rawY - dy) > dp(6)) moved = true
                            if (moved) { lp.x = (sx + (e.rawX - dx)).toInt(); lp.y = (sy + (e.rawY - dy)).toInt(); try { wm.updateViewLayout(v, lp) } catch (_: Exception) {} }
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (!moved) {
                                ChatLauncher.open(ctx)
                            } else {
                                lp.x = if (lp.x + size / 2 < screenWidth() / 2) dp(4) else screenWidth() - size - dp(4)
                                lp.y = lp.y.coerceIn(statusBarHeight(), screenHeight() - size - dp(24))
                                try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                                Prefs.bubbleX.value = lp.x.toString(); Prefs.bubbleY.value = lp.y.toString()
                            }
                            true
                        }
                        else -> false
                    }
                }
                wm.addView(frame, lp)
            }
            else -> { // curtain
                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(Color.BLACK); setPadding(dp(16), statusBarHeight(), dp(16), dp(6))
                }
                mascot = ImageView(ctx).apply { setImageResource(Mascots.r(R.drawable.cc_mascot)) }
                col.addView(mascot, LinearLayout.LayoutParams(dp(40), dp(28)))
                label = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 14f; maxLines = 1; setPadding(dp(12), 0, dp(12), 0) }
                col.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                val icon = FrameLayout(ctx)
                spinner = ProgressBar(ctx).apply { isIndeterminate = true }
                mark = TextView(ctx).apply { textSize = 18f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                icon.addView(spinner, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                icon.addView(mark, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
                col.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))
                bob = dancer(mascot!!, dp(3).toFloat())
                col.setOnLongClickListener { Prefs.overlay.value = "pill"; true }
                var flags = base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                if (Prefs.screenOn.value) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                root = col
                wm.addView(col, params(-1, -2, flags, Gravity.TOP).also {
                    if (Build.VERSION.SDK_INT >= 28) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                })
            }
        }
    }

    /**
     * The pill sits over the status bar, where touches often never arrive: a strip right beside it takes the taps and drags instead.
     * Dragging out of it pulls the mascot out as the buddy; a buddy let go on the pill or the strip docks back in.
     */
    private fun addHandle(edge: Edge, wx: Int, wy: Int, thick: Int, total: Int, vertical: Boolean) {
        val w = screenWidth(); val h = screenHeight()
        val pad = dp(12); val band = dp(16)
        val pill = android.graphics.Rect(wx, wy, wx + (if (vertical) thick else total), wy + (if (vertical) total else thick))
        val strip = when (edge) {
            Edge.TOP -> android.graphics.Rect(pill.left - pad, pill.bottom, pill.right + pad, pill.bottom + band)
            Edge.BOTTOM -> android.graphics.Rect(pill.left - pad, pill.top - band, pill.right + pad, pill.top)
            Edge.LEFT -> android.graphics.Rect(pill.right, pill.top - pad, pill.right + band, pill.bottom + pad)
            Edge.RIGHT -> android.graphics.Rect(pill.left - band, pill.top - pad, pill.left, pill.bottom + pad)
        }
        strip.intersect(0, 0, w, h)
        dockZone = android.graphics.Rect(minOf(pill.left, strip.left), minOf(pill.top, strip.top), maxOf(pill.right, strip.right), maxOf(pill.bottom, strip.bottom)).also { it.inset(-dp(24), -dp(24)) }
        val mode = Prefs.pillHandle.value
        if (mode == "0" || strip.isEmpty) return
        val v = View(ctx)
        if (mode == "2") v.setBackgroundColor(0x33FFFFFF)
        v.setOnTouchListener { view, e -> handleTouch(view, e) }
        val lp = params(strip.width(), strip.height(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            Gravity.TOP or Gravity.START).also { it.x = strip.left; it.y = strip.top }
        try { wm.addView(v, lp); handle = v } catch (_: Exception) { }
    }

    private fun styleNow() = lastStyleIn.removeSuffix("+black")

    /** Shared by the pill and its strip: a tap opens the chat bubble (three taps close everything), a drag pulls the mascot out as the buddy. */
    private fun handleTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { hDownX = e.rawX; hDownY = e.rawY; hDownT = e.eventTime; hDragging = false; hIgnore = false }
            MotionEvent.ACTION_MOVE -> {
                if (!hDragging && !hIgnore && Math.hypot((e.rawX - hDownX).toDouble(), (e.rawY - hDownY).toDouble()) > dp(14)) {
                    if (Prefs.buddyOn.value && !buddyHome(styleNow())) { hDragging = true; undock(e.rawX, e.rawY) } else hIgnore = true
                }
                if (hDragging) buddy.dragTo(e.rawX, e.rawY, e.eventTime)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (hDragging) { buddy.release(); hDragging = false }
                else if (e.actionMasked == MotionEvent.ACTION_UP && !hIgnore && e.eventTime - hDownT < 400) tapped()
                hIgnore = false
            }
        }
        return true
    }

    private fun tapped() {
        val now = System.currentTimeMillis()
        taps = if (now - tapLast < 700) taps + 1 else 1
        tapLast = now
        tapRun?.let { main.removeCallbacks(it) }; tapRun = null
        if (taps >= 3) { taps = 0; closeNow(); return }
        tapRun = Runnable { if (taps == 1) ChatLauncher.open(ctx); taps = 0; tapRun = null }.also { main.postDelayed(it, 380) }
    }

    /** The mascot leaves the pill: it becomes the buddy right under the finger and follows it. */
    private fun undock(x: Float, y: Float) {
        Prefs.mascotHome.value = "buddy"
        render(lastStyleIn, cache.first, cache.second, cache.third)
        buddy.grab(x, y)
    }

    /** A buddy that is let go on the pill (or its strip) goes back into it. */
    private fun tryDock(x: Float, y: Float): Boolean {
        val z = dockZone ?: return false
        if (styleNow() != "pill" || !z.contains(x.toInt(), y.toInt())) return false
        Prefs.mascotHome.value = "pill"
        render(lastStyleIn, cache.first, cache.second, cache.third)
        ring?.burst(); mascot?.let { playEyesAnimation(it) }
        return true
    }

    /** Triple tap on the island: switch everything off (service, overlay, notification). */
    private fun closeNow() {
        android.widget.Toast.makeText(ctx, tr("Pill closed", "Hap kapatıldı"), android.widget.Toast.LENGTH_SHORT).show()
        ctx.startService(android.content.Intent(ctx, com.umutk.pulse.extras.ai.AiBuddyService::class.java).setAction("stop"))
    }

    /** Rebuilds everything on the current window layer (after the accessibility service was switched on or off). */
    fun relayout() { val s = lastStyleIn; val c = cache; hide(); if (s.isNotEmpty()) render(s, c.first, c.second, c.third) }

    /** Everything off: the buddy and the bar overlay. */
    fun hide() { buddy.hide(); hidePill() }

    /** Just the bar overlay (pill / line / curtain / bubble head) and the black layer; the buddy keeps walking. */
    private fun hidePill() {
        handle?.let { try { wm.removeView(it) } catch (e: Exception) { } }; handle = null; dockZone = null
        tapRun?.let { main.removeCallbacks(it) }; tapRun = null; taps = 0
        pulse?.cancel(); bob?.cancel(); eyes?.cancel(); bBob?.cancel(); blink?.cancel(); blink = null; term = null
        pcAnim?.cancel(); pcAnim = null; zAnim?.cancel(); zAnim = null; sleepTimer?.let { main.removeCallbacks(it) }; sleepTimer = null; asleepHidden = false
        // the hammer and the bubble belong to the views being removed: a new pill starts their animations again
        hammerAnim?.cancel(); hammerAnim = null; hammerView = null; thoughtAnim?.cancel(); thoughtAnim = null; doneSleep = false
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        blackLayer?.let { try { wm.removeView(it) } catch (e: Exception) { } }; blackLayer = null
        bStatus = null; bDet = null; bLast = null; bBatt = null; bMascot = null; bBob = null
        evAnim?.cancel(); evAnim = null; evHold?.let { main.removeCallbacks(it) }; evHold = null; glide?.cancel(); glide = null; cycle?.cancel(); cycle = null; cycleKey = ""; pairAnim?.cancel(); pairAnim = null; curColor = 0
        bodyHueAnim?.cancel(); bodyHueAnim = null
        capsule = null; iconBox = null; evProg = 1f; evTarget = -1
        root = null; style = ""; pillRect = null
        mascot = null; spinner = null; mark = null; thought = null; label = null; sub = null; bar = null; pulse = null; bob = null; eyes = null; ring = null
        lastStatus = null
    }
}

/** Bundled fonts (SIL OFL) for the always-on clock; variable ones get a weight. */
fun clockFont(ctx: Context, key: String): android.graphics.Typeface {
    fun asset(file: String, wght: Int? = null) = runCatching {
        android.graphics.Typeface.Builder(ctx.assets, "fonts/$file.ttf").apply { if (wght != null) setFontVariationSettings("'wght' $wght") }.build()
    }.getOrNull()
    return when (key) {
        "orbitron" -> asset("orbitron", 500)
        "grotesk" -> asset("grotesk", 300)
        "audiowide" -> asset("audiowide")
        "rajdhani" -> asset("rajdhani")
        "chakra" -> asset("chakra")
        "exo2i" -> asset("exo2i", 300)
        "bungee" -> asset("bungee")
        "sharetech" -> asset("sharetech")
        "majormono" -> asset("majormono")
        "michroma" -> asset("michroma")
        "regular" -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        "bold" -> android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        "mono" -> android.graphics.Typeface.MONOSPACE
        else -> asset("outfit", 200) // default: "outfit" (older saved value "thin" lands here too)
    } ?: android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
}

/** The pill's glow colour for a choice in Customize (0 = no glow). */
fun glowArgb(k: String): Int = when (k) { "cyan" -> 0xFF3CF0FF.toInt(); "pink" -> 0xFFFF4D9D.toInt(); "gold" -> 0xFFFFC107.toInt(); "green" -> 0xFF5DE08A.toInt(); else -> if (StateColors.isHex(k)) android.graphics.Color.parseColor(k) else 0 }
