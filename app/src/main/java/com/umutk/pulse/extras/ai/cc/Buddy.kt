package com.umutk.pulse.extras.ai.cc

import android.content.Context
import com.umutk.pulse.R
import com.umutk.pulse.tr
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Choreographer
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The desktop-assistant mode: the mascot walks around on top of every app, can be dragged, flung and poked,
 * and talks in a speech bubble (status while Claude works, the answer when it is done).
 */
class Buddy(base: Context) {
    private val ctx = ContextThemeWrapper(base, android.R.style.Theme_Material)
    private val sys = base
    private var wm = PillAccess.wm(base)
    private var otype = PillAccess.type()
    private val main = Handler(Looper.getMainLooper())
    private var view: BuddyView? = null
    private var bubble: BubbleView? = null
    private var vlp: WindowManager.LayoutParams? = null
    private var blp: WindowManager.LayoutParams? = null
    private var px = 0f; private var py = 0f
    private var vx = 0f; private var vy = 0f
    private var vw = 0; private var vh = 0; private var size = 0
    private var held = false
    private var flying = false
    private var quiet = false
    private var st = Status.Idle
    private var started = false
    private var bubbleOn = false
    private var bubbleSticky = false
    private var hideRun: Runnable? = null
    private var sleepRun: Runnable? = null
    private var typeRun: Runnable? = null
    private var lastFrame = 0L
    private var lastBubbleText = ""
    private var asleepHidden = false
    private var shellNow = false

    val active get() = view != null
    private var anchor: android.graphics.Rect? = null

    /** The pill's own messages as a speech bubble under it (no buddy walking): same look, hold time and opacity as the buddy's bubble. */
    fun bubbleOnly(s: Status, det: String, last: String, a: android.graphics.Rect?) {
        if (!Settings.canDrawOverlays(ctx) || Prefs.buddyBubble.value != "1") return
        anchor = a
        val changed = s != st || !started
        st = s; started = true
        when {
            s.running -> if (Prefs.buddyBubbleWork.value == "1") say(det.ifBlank { tr("Working…", "Çalışıyorum…") }, sticky = true, type = false)
            s == Status.Done -> if (changed) { val txt = clean(last); if (txt.isNotEmpty()) say(txt, sticky = false, type = true) else hideBubbleSoon() }
            s == Status.Error -> if (changed) say(tr("Something went wrong.", "Bir şeyler ters gitti.") + clean(last).let { if (it.isEmpty()) "" else "\n" + it.take(120) }, sticky = false, type = false, holdMul = 2)
            else -> if (changed) hideBubbleSoon()
        }
    }

    fun clearBubbleOnly() { if (anchor != null) { anchor = null; if (view == null) hide() } }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun pref(s: Prefs.S, def: Int) = s.value.toIntOrNull() ?: def
    private fun sw() = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width() else ctx.resources.displayMetrics.widthPixels
    private fun sh() = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height() else ctx.resources.displayMetrics.heightPixels

    /** [shell]: a command or background job runs, so the mascot sits at its laptop (like in the pill and the header). */
    fun render(s: Status, det: String, last: String, shell: Boolean = false) {
        if (!Settings.canDrawOverlays(ctx)) { hide(); return }
        shellNow = shell
        ensureShown()
        val v = view ?: return
        val changed = s != st || !started
        st = s
        if (!started) { started = true }
        when {
            s.running -> {
                cancelSleep()
                if (asleepHidden) { asleepHidden = false; if (!quiet) v.visibility = View.VISIBLE; v.setMood(Mood.IDLE) }
                v.setMood(if (shell) Mood.PC else Mood.WORK)
                if (Prefs.buddyBubbleWork.value == "1") say(det.ifBlank { tr("Working…", "Çalışıyorum…") }, sticky = true, type = false)
            }
            s == Status.Done -> if (changed) {
                v.setMood(Mood.HAPPY)
                val txt = clean(last)
                if (txt.isNotEmpty()) say(txt, sticky = false, type = true) else hideBubbleSoon()
                scheduleSleep()
            }
            s == Status.Error -> if (changed) {
                cancelSleep(); v.setMood(Mood.ERROR)
                say(tr("Something went wrong.", "Bir şeyler ters gitti.") + clean(last).let { if (it.isEmpty()) "" else "\n" + it.take(120) }, sticky = false, type = false, holdMul = 2)
            }
            s == Status.Offline -> if (changed) {
                cancelSleep(); v.setMood(Mood.ERROR)
                say(tr("I can't reach the bridge. Tap me to open the app.", "Köprüye ulaşamıyorum. Uygulamayı açmak için dokun."), sticky = false, type = false, holdMul = 2)
            }
            else -> if (changed) {
                v.setMood(Mood.IDLE)
                if (bubbleSticky) hideBubbleSoon()
                scheduleSleep()
            }
        }
    }

    fun setQuiet(q: Boolean) {
        quiet = q
        view?.visibility = if (q || asleepHidden) View.GONE else View.VISIBLE
        bubble?.visibility = if (q || !bubbleOn) View.GONE else View.VISIBLE
        if (!q) placeBubble()
    }

    /** Look settings changed: rebuild with the same state. */
    fun refresh() {
        if (view == null) return
        val s = st; val t = lastBubbleText; val on = bubbleOn; val sticky = bubbleSticky
        val m = view?.mood ?: Mood.IDLE
        hide(keepStarted = true)
        ensureShown()
        view?.setMood(m)
        if (t.isNotEmpty() && on) say(t, sticky, type = false)
        st = s
        if (Prefs.buddyGravity.value == "1") startPhysics()
    }

    fun onRotate() {
        if (view == null) return
        clampPos(); apply(); placeBubble()
    }

    fun hide(keepStarted: Boolean = false) {
        stopPhysics()
        hideRun?.let { main.removeCallbacks(it) }; hideRun = null
        typeRun?.let { main.removeCallbacks(it) }; typeRun = null
        cancelSleep()
        view?.let { it.stop(); try { wm.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null; bubble = null; vlp = null; blp = null; bubbleOn = false; asleepHidden = false
        if (!keepStarted) { started = false; st = Status.Idle; lastBubbleText = ""; bubbleSticky = false }
    }

    // ---- window ----------------------------------------------------------------------------------

    private fun ensureShown() {
        if (view != null) return
        if (bubble == null) { wm = PillAccess.wm(sys); otype = PillAccess.type() }
        size = dp(pref(Prefs.buddySize, 76).coerceIn(40, 180))
        vw = (size * 1.3f).toInt(); vh = (size * 1.2f).toInt()
        val v = BuddyView(ctx, size, vw, vh)
        view = v
        v.alpha = pref(Prefs.buddyOpacity, 100).coerceIn(20, 100) / 100f
        v.setOnTouchListener(::touch)
        px = Prefs.buddyX.value.toFloatOrNull() ?: (sw() - vw - dp(8)).toFloat()
        py = Prefs.buddyY.value.toFloatOrNull() ?: (sh() * 0.35f)
        clampPos()
        val lp = WindowManager.LayoutParams(vw, vh, otype, FLAGS, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START; lp.x = px.toInt(); lp.y = py.toInt()
        vlp = lp
        try { wm.addView(v, lp) } catch (_: Exception) { view = null; return }
        v.visibility = if (quiet) View.GONE else View.VISIBLE
        v.start()
        if (Prefs.buddyGravity.value == "1") startPhysics()
    }

    private fun ensureBubble(): BubbleView? {
        bubble?.let { return it }
        if (view == null) { wm = PillAccess.wm(sys); otype = PillAccess.type() }
        val b = BubbleView(ctx)
        b.setOnClickListener { ChatLauncher.open(ctx); hideBubbleNow() }
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            otype, FLAGS, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        b.visibility = View.GONE
        blp = lp
        try { wm.addView(b, lp) } catch (_: Exception) { return null }
        bubble = b
        return b
    }

    private fun apply() {
        val lp = vlp ?: return
        lp.x = px.toInt(); lp.y = py.toInt()
        view?.let { try { wm.updateViewLayout(it, lp) } catch (_: Exception) {} }
        placeBubble()
    }

    private fun clampPos() {
        px = px.coerceIn(0f, (sw() - vw).coerceAtLeast(0).toFloat())
        py = py.coerceIn(0f, (sh() - vh).coerceAtLeast(0).toFloat())
    }

    private fun savePos() { Prefs.buddyX.value = px.toInt().toString(); Prefs.buddyY.value = py.toInt().toString() }

    /** Tap and long press do two different things: chat bubble (or the app) and poke. The setting decides which is which. */
    private fun tapAction(long: Boolean) {
        when (Prefs.buddyTap.value) {
            "poke" -> if (long) ChatLauncher.open(ctx) else poke()
            else -> if (long) poke() else ChatLauncher.open(ctx)
        }
    }

    // ---- handed over by the pill (its strip pulls the mascot out mid-drag) and dropped back onto it ---------------------

    /** Finger position when the mascot is let go; return true when something took it (it docked into the pill). */
    var onDrop: ((Float, Float) -> Boolean)? = null
    var onDrag: ((Float, Float) -> Unit)? = null

    fun setDockHint(on: Boolean) { view?.setDockHint(on) }

    private fun placeUnder(rawX: Float, rawY: Float) { px = rawX - size / 2f; py = rawY - (vh - 0.45f * size); clampPos(); apply() }

    /** The mascot appears under the finger and from now on follows it ([dragTo]) until [release]. */
    fun grab(rawX: Float, rawY: Float) {
        ensureShown()
        val v = view ?: return
        stopPhysics(); held = true; moved = true; flying = false; vx = 0f; vy = 0f
        asleepHidden = false; v.visibility = View.VISIBLE
        placeUnder(rawX, rawY)
        lastT = SystemClock.uptimeMillis(); lastX = rawX; lastY = rawY
        v.setMood(Mood.HELD)
    }

    fun dragTo(rawX: Float, rawY: Float, t: Long) {
        if (!held) return
        placeUnder(rawX, rawY)
        val dt = (t - lastT).coerceAtLeast(1) / 1000f
        if (dt > 0.004f) {
            vx = vx * 0.6f + 0.4f * (rawX - lastX) / dt
            vy = vy * 0.6f + 0.4f * (rawY - lastY) / dt
            lastT = t; lastX = rawX; lastY = rawY
        }
        view?.lean = (vx / dp(40)).coerceIn(-28f, 28f)
        onDrag?.invoke(rawX, rawY)
    }

    fun release() {
        if (!held) return
        held = false
        view?.setDockHint(false)
        if (onDrop?.invoke(lastX, lastY) == true) return
        if (SystemClock.uptimeMillis() - lastT > 90) { vx = 0f; vy = 0f }
        view?.setMood(moodForStatus())
        if (Prefs.buddySnap.value == "1" && Prefs.buddyGravity.value != "1") snapToSide() else startPhysics()
    }

    // ---- touch: tap, long press, drag, fling -------------------------------------------------------

    private var downX = 0f; private var downY = 0f; private var startX = 0f; private var startY = 0f
    private var moved = false
    private var lastT = 0L; private var lastX = 0f; private var lastY = 0f
    private var longRun: Runnable? = null
    private var longFired = false

    private fun touch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; startX = px; startY = py; moved = false; longFired = false
                lastT = e.eventTime; lastX = e.rawX; lastY = e.rawY; vx = 0f; vy = 0f
                flying = false; stopPhysics()
                wake()
                longRun = Runnable { if (!moved) { longFired = true; tapAction(true) } }
                main.postDelayed(longRun!!, 550)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.rawX - downX, e.rawY - downY) > dp(8)) {
                    moved = true; held = true; main.removeCallbacks(longRun!!)
                    view?.setMood(Mood.HELD)
                }
                if (moved) {
                    px = startX + (e.rawX - downX); py = startY + (e.rawY - downY); clampPos(); apply()
                    val dt = (e.eventTime - lastT).coerceAtLeast(1) / 1000f
                    if (dt > 0.004f) {
                        vx = vx * 0.6f + 0.4f * (e.rawX - lastX) / dt
                        vy = vy * 0.6f + 0.4f * (e.rawY - lastY) / dt
                        lastT = e.eventTime; lastX = e.rawX; lastY = e.rawY
                    }
                    view?.lean = (vx / dp(40)).coerceIn(-28f, 28f)
                    onDrag?.invoke(e.rawX, e.rawY)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                main.removeCallbacks(longRun!!)
                if (moved) {
                    view?.setDockHint(false)
                    if (e.actionMasked == MotionEvent.ACTION_UP && onDrop?.invoke(e.rawX, e.rawY) == true) { held = false; return true }
                    held = false
                    if (e.eventTime - lastT > 90) { vx = 0f; vy = 0f } // held still before letting go: no fling
                    view?.setMood(moodForStatus())
                    if (Prefs.buddySnap.value == "1" && Prefs.buddyGravity.value != "1") snapToSide() else startPhysics()
                } else if (e.actionMasked == MotionEvent.ACTION_UP && !longFired) {
                    tapAction(false)
                }
            }
        }
        return true
    }

    private fun moodForStatus() = when {
        st.running -> if (shellNow) Mood.PC else Mood.WORK
        st == Status.Error || st == Status.Offline -> Mood.ERROR
        else -> Mood.IDLE
    }

    private val quips get() = listOf(tr("Hey!", "Hey!"), tr("Ow!", "Aaa!"), tr("Boop!", "Bop!"), tr("Hehe", "Hihi"), tr("That tickles", "Gıdıklanıyorum"), tr("Yes?", "Efendim?"))

    private fun poke() {
        view?.poke()
        if (!bubbleSticky && Prefs.buddyBubble.value == "1") say(quips.random(), sticky = false, type = false, holdMul = 0)
    }

    private fun wake() {
        if (view?.mood == Mood.SLEEP) view?.let { it.setMood(Mood.IDLE); it.poke() }
        if (!st.running) scheduleSleep()
    }

    // ---- physics -------------------------------------------------------------------------------------

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(ns: Long) {
            if (!flying || view == null) return
            val dt = if (lastFrame == 0L) 0.016f else ((ns - lastFrame) / 1e9f).coerceIn(0.004f, 0.033f)
            lastFrame = ns
            val gravity = Prefs.buddyGravity.value == "1"
            if (gravity) { vy += dp(1700) * dt; val d = exp(-0.25f * dt); vx *= d }
            else { val d = exp(-2.3f * dt); vx *= d; vy *= d }
            px += vx * dt; py += vy * dt
            val rest = pref(Prefs.buddyBounce, 65).coerceIn(0, 95) / 100f
            val maxX = (sw() - vw).coerceAtLeast(0).toFloat(); val maxY = (sh() - vh).coerceAtLeast(0).toFloat()
            var hit = 0f
            if (px < 0f) { px = 0f; hit = abs(vx); vx = -vx * rest }
            if (px > maxX) { px = maxX; hit = abs(vx); vx = -vx * rest }
            if (py < 0f) { py = 0f; hit = maxOf(hit, abs(vy)); vy = -vy * rest }
            if (py > maxY) {
                py = maxY; hit = maxOf(hit, abs(vy)); vy = -vy * rest
                if (gravity) { vx *= 0.92f; if (abs(vy) < dp(70)) vy = 0f }
            }
            if (hit > dp(500)) view?.thud(hit / dp(2400))
            view?.lean = (vx / dp(60)).coerceIn(-25f, 25f)
            apply()
            val onFloor = py >= maxY - 1f
            val still = hypot(vx, vy) < dp(18) && (!gravity || onFloor)
            if (still) { flying = false; vx = 0f; vy = 0f; view?.lean = 0f; savePos() }
            else Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startPhysics() {
        if (view == null) return
        val gravity = Prefs.buddyGravity.value == "1"
        if (!gravity && hypot(vx, vy) < dp(30)) { vx = 0f; vy = 0f; flying = false; view?.lean = 0f; savePos(); return }
        flying = true; lastFrame = 0L
        Choreographer.getInstance().removeFrameCallback(frame)
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stopPhysics() { flying = false; Choreographer.getInstance().removeFrameCallback(frame) }

    private fun snapToSide() {
        val target = if (px + vw / 2 < sw() / 2) 0f else (sw() - vw).toFloat()
        val from = px
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220; interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { px = from + (target - from) * (it.animatedValue as Float); clampPos(); apply() }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { view?.lean = 0f; savePos() } })
            start()
        }
    }

    // ---- sleep ---------------------------------------------------------------------------------------

    private fun scheduleSleep() {
        cancelSleep()
        val secs = pref(Prefs.buddySleepAfter, 60)
        if (secs <= 0) return
        sleepRun = Runnable { sleepRun = null; if (!st.running && !held && !Engine.buildRunning.value) { view?.setMood(Mood.SLEEP); hideBubbleNow(); if (Prefs.buddyHideAsleep.value == "1") { asleepHidden = true; view?.visibility = View.GONE } } }.also { main.postDelayed(it, secs * 1000L) }
    }

    private fun cancelSleep() { sleepRun?.let { main.removeCallbacks(it) }; sleepRun = null }

    // ---- speech bubble ------------------------------------------------------------------------------

    private fun clean(t: String) = t.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("[*`#>]+"), "").replace(Regex("\\s+"), " ").trim().take(600)

    private fun say(text: String, sticky: Boolean, type: Boolean, holdMul: Int = 1) {
        if (Prefs.buddyBubble.value != "1") return
        val b = ensureBubble() ?: return
        hideRun?.let { main.removeCallbacks(it) }; hideRun = null
        typeRun?.let { main.removeCallbacks(it) }; typeRun = null
        bubbleSticky = sticky
        val same = text == lastBubbleText && bubbleOn
        lastBubbleText = text
        if (!same) {
            b.style(pref(Prefs.buddyBubbleSize, 13).coerceIn(9, 24), dp(pref(Prefs.buddyBubbleWidth, 260).coerceIn(120, 360)),
                pref(Prefs.buddyBubbleLines, 8).coerceIn(1, 14), Prefs.buddyBubbleShape.value, Prefs.buddyBubbleTone.value,
                Prefs.buddyTail.value == "1", pref(Prefs.buddyBubbleOpacity, 96).coerceIn(30, 100))
            b.setText(text, TextView.BufferType.NORMAL)
        }
        bubbleOn = true
        b.visibility = if (quiet) View.GONE else View.VISIBLE
        b.alpha = 1f
        placeBubble()
        if (type && !same) {
            val sp = SpannableString(text)
            var n = 0
            val step = object : Runnable {
                override fun run() {
                    n += 2
                    if (n >= text.length) { b.text = text; typeRun = null; return }
                    val s2 = SpannableString(text); s2.setSpan(ForegroundColorSpan(Color.TRANSPARENT), n, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    b.text = s2; typeRun = this; main.postDelayed(this, 28)
                }
            }
            b.text = sp; step.run()
        }
        if (!sticky && holdMul > 0) {
            val hold = pref(Prefs.buddyBubbleHold, 12)
            if (hold > 0) hideRun = Runnable { hideRun = null; hideBubbleNow() }.also { main.postDelayed(it, hold * 1000L * holdMul) }
        } else if (!sticky) hideRun = Runnable { hideRun = null; hideBubbleNow() }.also { main.postDelayed(it, 1800) }
    }

    private fun hideBubbleSoon() { if (bubbleOn) { hideRun?.let { main.removeCallbacks(it) }; hideRun = Runnable { hideRun = null; hideBubbleNow() }.also { main.postDelayed(it, 1500) } } }

    private fun hideBubbleNow() {
        typeRun?.let { main.removeCallbacks(it) }; typeRun = null
        val b = bubble ?: return
        bubbleOn = false; bubbleSticky = false; lastBubbleText = ""
        b.animate().alpha(0f).setDuration(220).withEndAction { if (!bubbleOn) b.visibility = View.GONE }.start()
    }

    private fun placeBubble() {
        val b = bubble ?: return
        val lp = blp ?: return
        if (!bubbleOn || quiet) return
        b.measure(View.MeasureSpec.makeMeasureSpec(sw(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(sh(), View.MeasureSpec.AT_MOST))
        var bw = b.measuredWidth; var bh = b.measuredHeight
        val a = anchor
        val mascotTop = if (a != null) a.top.toFloat() else py + vh - size * 0.9f
        val cx = a?.exactCenterX() ?: (px + size / 2f)
        val above = if (a != null) false else (mascotTop - bh - dp(2) >= 0 || mascotTop > sh() / 2f)
        if (b.tailUp == above) { b.setTailSide(!above); b.measure(View.MeasureSpec.makeMeasureSpec(sw(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(sh(), View.MeasureSpec.AT_MOST)); bw = b.measuredWidth; bh = b.measuredHeight }
        val x = (cx - bw / 2f).coerceIn(dp(4).toFloat(), (sw() - bw - dp(4)).coerceAtLeast(dp(4)).toFloat())
        val y = if (above) mascotTop - bh + dp(3) else if (a != null) a.bottom + dp(2).toFloat() else py + vh - dp(4)
        b.tailX = cx - x
        lp.x = x.toInt(); lp.y = y.toInt().coerceIn(0, (sh() - bh).coerceAtLeast(0))
        b.invalidate()
        try { wm.updateViewLayout(b, lp) } catch (_: Exception) {}
    }

    companion object {
        private const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }
}

enum class Mood { IDLE, WORK, PC, HAPPY, ERROR, SLEEP, HELD, WOW }

/** Draws the mascot itself (faces, blanket, hat, little extras) and runs all of its small animations. */
private class BuddyView(c: Context, val s: Int, val w: Int, val h: Int) : View(c) {
    private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var mood = Mood.IDLE
        private set
    var lean = 0f                       // tilt while carried or flying
    private var leanNow = 0f
    private var since = SystemClock.uptimeMillis()
    private var wowUntil = 0L
    private var thudAt = 0L; private var thudPow = 0f
    private var blinkAt = since + 2500
    private var actAt = since + 9000; private var actKind = -1; private var actStart = 0L
    private var running = false
    private val rnd = java.util.Random()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var open: Drawable? = null; private var closed: Drawable? = null; private var x: Drawable? = null
    private var happy: Drawable? = null; private var wow: Drawable? = null; private var blanket: Drawable? = null; private var hat: Drawable? = null
    private var wear: List<Drawable> = emptyList()   // glasses, shoes, clothes: the same chosen pieces as the header and pill
    private var pc1: Drawable? = null; private var pc2: Drawable? = null
    private var sleepFace: Drawable? = null        // asleep: the normal body with closed eyes
    private val blanketOn get() = Prefs.buddyBlanket.value == "1"
    private val idleOn get() = Prefs.buddyIdle.value == "1"

    init {
        val skin = Outfit.skinMatrix()
        fun sleepFace0(id: Int) = ContextCompat.getDrawable(c, id)!!.mutate()   // asleep: default orange, no skin tint
        fun face(id: Int) = ContextCompat.getDrawable(c, id)!!.mutate().also { if (skin != null) it.colorFilter = android.graphics.ColorMatrixColorFilter(skin) }
        open = face(Mascots.r(R.drawable.cc_mascot18)); closed = face(Mascots.r(R.drawable.cc_mascot_sleep18)); x = face(R.drawable.cc_mascot_x18)
        happy = face(Mascots.r(R.drawable.cc_mascot_happy18)); wow = face(Mascots.r(R.drawable.cc_mascot_wow18))
        fun pcFace(alt: Boolean): Drawable = Mascots.pc(alt).map { face(it) }.let { l -> if (l.size == 1) l[0] else android.graphics.drawable.LayerDrawable(l.toTypedArray()) }
        pc1 = pcFace(false); pc2 = pcFace(true)
        sleepFace = sleepFace0(Mascots.r(R.drawable.cc_mascot_sleep18))
        blanket = ContextCompat.getDrawable(c, R.drawable.cc_blanket)!!.mutate()
        val hid = Outfit.hat(); hat = if (hid != 0) Mascots.hat(c, hid) else null
        wear = Wear.worn(false).map { ContextCompat.getDrawable(c, it)!!.mutate() }
        val dw = s; val dh = (s * 0.9f).toInt()
        for (d in listOf(open, closed, x, happy, wow, blanket, hat, pc1, pc2, sleepFace) + wear) d?.setBounds(-dw / 2, -dh / 2, dw / 2, dh / 2)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            if (mood == Mood.HAPPY && now - since > 2600) setMood(Mood.IDLE)
            if (mood == Mood.WOW && now > wowUntil) setMood(Mood.IDLE)
            invalidate()
            val busy = mood == Mood.WORK || mood == Mood.PC || mood == Mood.HAPPY || (mood == Mood.SLEEP && now - since < 1000) || mood == Mood.ERROR && now - since < 2500 || mood == Mood.HELD || mood == Mood.WOW ||
                actKind >= 0 || now < thudAt + 700 || abs(leanNow - lean) > 0.3f
            if (busy) postOnAnimation(this) else postDelayed(this, if (mood == Mood.SLEEP) 140 else 90)
        }
    }

    /** Dragged over the pill: shrinks a little and ticks, so it is clear it will go in when let go. */
    fun setDockHint(on: Boolean) {
        if (on == dockHint) return
        dockHint = on
        animate().scaleX(if (on) 0.8f else 1f).scaleY(if (on) 0.8f else 1f).setDuration(120).start()
        if (on) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    private var dockHint = false

    fun start() { running = true; post(tick) }
    fun stop() { running = false; removeCallbacks(tick) }

    fun setMood(m: Mood) { if (m != mood) { mood = m; since = SystemClock.uptimeMillis(); actKind = -1 } }
    fun poke() { wowUntil = SystemClock.uptimeMillis() + 900; val m = if (mood == Mood.WOW) Mood.IDLE else mood; if (m == Mood.IDLE || m == Mood.SLEEP) setMood(Mood.WOW) else { thudAt = SystemClock.uptimeMillis(); thudPow = 0.5f } }
    fun thud(power: Float) { thudAt = SystemClock.uptimeMillis(); thudPow = power.coerceIn(0.1f, 1f) }

    override fun onDraw(cv: Canvas) {
        // phone control is being used right now (not just switched on or the service enabled): a small green dot at the top right
        val now = SystemClock.uptimeMillis()
        val t = (now - since) / 1000f
        var rot = 0f; var dy = 0f; var dx = 0f; var sx = 1f; var sy = 1f
        var face = open
        when (mood) {
            Mood.IDLE -> {
                dy = sin(now / 900.0).toFloat() * 0.015f * s
                if (now > blinkAt) { face = closed; if (now > blinkAt + 140) blinkAt = now + 2200 + rnd.nextInt(3800) }
                if (idleOn && actKind < 0 && now > actAt) { actKind = rnd.nextInt(4); actStart = now; actAt = now + 9000 + rnd.nextInt(14000) }
                if (actKind >= 0) {
                    val dur = longArrayOf(800, 900, 1500, 800)[actKind]; val pr = (now - actStart).toFloat() / dur
                    if (pr >= 1f) actKind = -1 else when (actKind) {
                        0 -> { dy -= abs(sin(pr * PI * 2)).toFloat() * 0.13f * s; val g = abs(sin(pr * PI * 2)).toFloat(); sy = 1f + 0.06f * (1 - g); sx = 1f - 0.04f * (1 - g) }
                        1 -> rot = sin(pr * PI * 6).toFloat() * 8f * (1 - pr)
                        2 -> { dx = sin(pr * PI * 2).toFloat() * 0.07f * s; rot = dx / s * 40f; if (pr in 0.2f..0.35f || pr in 0.7f..0.85f) face = closed }
                        3 -> { sy = 1f + 0.09f * sin(pr * PI).toFloat(); sx = 1f - 0.05f * sin(pr * PI).toFloat() }
                    }
                }
            }
            Mood.WORK -> {
                val n = Dance.ROT.size; val i = ((now / Dance.FRAME_MS) % n).toInt()
                rot = Dance.ROT[i]; dy = -Dance.UP[i] * 0.07f * s
                if (now > blinkAt) { face = closed; if (now > blinkAt + 120) blinkAt = now + 1800 + rnd.nextInt(2500) }
            }
            Mood.PC -> { // at the laptop: two frames (typing arms, pulsing light) like the pill's, a tiny bob
                face = if ((now / 450) % 2 == 0L) pc1 else pc2
                dy = sin(now / 260.0).toFloat() * 0.012f * s
            }
            Mood.HAPPY -> {
                face = happy
                val k = exp(-t * 0.9).toFloat(); val g = abs(sin(t * 9.0)).toFloat()
                dy = -g * 0.2f * s * k; sy = 1f + 0.08f * (1 - g) * k; sx = 1f - 0.05f * (1 - g) * k; rot = sin(t * 9.0).toFloat() * 5f * k
            }
            Mood.ERROR -> {
                face = x
                val k = exp(-t * 1.4).toFloat(); rot = sin(t * 32.0).toFloat() * 7f * k; dx = sin(t * 32.0).toFloat() * 0.03f * s * k
            }
            Mood.SLEEP -> {
                face = sleepFace
                // breathing would stretch the blanket too: with the blanket on, the sleeper stays still
                val br = 0f; /* no breathing: the sleeper stays still */ sy = 1f + 0.025f * br; sx = 1f - 0.012f * br; dy = 0.01f * s * br
            }
            Mood.HELD -> { face = wow; sin(now / 120.0).let { rot = it.toFloat() * 3f } }
            Mood.WOW -> { face = wow; val k = (1f - (now - since) / 900f).coerceIn(0f, 1f); sx = 1f + 0.18f * k; sy = 1f + 0.18f * k; rot = sin(t * 40.0).toFloat() * 6f * k }
        }
        leanNow += (lean - leanNow) * 0.25f
        rot += leanNow
        if (now < thudAt + 450) { val k = 1f - (now - thudAt) / 450f; val sq = sin((now - thudAt) / 45.0).toFloat() * 0.16f * thudPow * k; sx *= 1f + sq; sy *= 1f - sq }
        val cx = s / 2f + dx; val cy = h - 0.45f * s + dy
        cv.save()
        cv.translate(cx, cy); cv.rotate(rot) // no grow or shrink in any mood
        // at the laptop the laptop goes in front of the clothes, so the bow tie sits behind it
        val gearOnTop = mood != Mood.PC
        if (!gearOnTop) wear.forEach { it.draw(cv) }
        // the glow picked in Customize, behind the buddy (a soft disc, no hard edge); not at the laptop, where the light only falls on the mascot
        val gl = glowArgb(Prefs.buddyGlow.value)
        if (gl != 0 && mood != Mood.PC) {
            glowPaint.shader = android.graphics.RadialGradient(0f, 0f, s * 0.6f, (gl and 0x00FFFFFF) or 0x66000000, 0, android.graphics.Shader.TileMode.CLAMP)
            cv.drawCircle(0f, 0f, s * 0.6f, glowPaint)
        }
        face?.draw(cv)
        if (mood == Mood.SLEEP && blanketOn) {
            // the blanket is pulled up over the body when it falls asleep (about a second), then stays
            val k = ((now - since) / 900f).coerceIn(0f, 1f)
            cv.save(); cv.translate(0f, (1f - k) * s * 0.9f * 0.45f); blanket?.alpha = (255 * k).toInt(); blanket?.draw(cv); cv.restore()
        }
        if (gearOnTop) wear.forEach { it.draw(cv) }
        hat?.draw(cv)
        cv.restore()
        when (mood) {
            Mood.SLEEP -> zzz(cv, now)
            Mood.ERROR -> sweat(cv, t)
            Mood.HAPPY -> sparkles(cv, t)
            else -> {}
        }
    }

    private fun zzz(cv: Canvas, now: Long) {
        p.style = Paint.Style.FILL; p.typeface = Typeface.DEFAULT_BOLD; p.color = Color.argb(220, 150, 170, 220)
        for (k in 0..1) {
            val ph = (((now + k * 1100L) % 2200L) / 2200f)
            p.alpha = (255 * sin(ph * PI)).toInt().coerceIn(0, 255)
            p.textSize = s * (0.17f + 0.07f * k + 0.05f * ph)
            cv.drawText(if (k == 0) "z" else "Z", s * 0.78f + ph * s * 0.2f, h - 0.62f * s - ph * s * 0.3f, p)
        }
    }

    private fun sweat(cv: Canvas, t: Float) {
        val ph = (t * 0.8f) % 1f
        val x0 = s * 0.82f; val y0 = h - 0.85f * s + ph * s * 0.25f
        p.style = Paint.Style.FILL; p.color = Color.argb((220 * (1 - ph)).toInt(), 120, 190, 255)
        val path = Path().apply { moveTo(x0, y0 - s * 0.07f); quadTo(x0 + s * 0.05f, y0 + s * 0.01f, x0, y0 + s * 0.04f); quadTo(x0 - s * 0.05f, y0 + s * 0.01f, x0, y0 - s * 0.07f); close() }
        cv.drawPath(path, p)
    }

    private fun sparkles(cv: Canvas, t: Float) {
        if (t > 2.2f) return
        p.style = Paint.Style.STROKE; p.strokeWidth = (s * 0.02f).coerceAtLeast(1.5f); p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 6) {
            val an = i / 6f * 2 * PI + 0.4; val r = s * (0.5f + 0.25f * t * (1 + i % 2))
            val qx = s / 2f + (kotlin.math.cos(an) * r).toFloat(); val qy = h - 0.45f * s + (sin(an) * r * 0.8f).toFloat() - s * 0.1f
            val a = ((1f - t / 2.2f) * 255).toInt().coerceIn(0, 255)
            p.color = Color.argb(a, 255, if (i % 2 == 0) 224 else 255, if (i % 2 == 0) 130 else 255)
            val q = s * 0.045f * (1f + 0.4f * sin(t * 12.0 + i).toFloat())
            cv.drawLine(qx - q, qy, qx + q, qy, p); cv.drawLine(qx, qy - q, qx, qy + q, p)
        }
    }
}

/** A speech bubble: pill / soft / sharp corners, optional tail towards the mascot. */
private class BubbleView(c: Context) : TextView(c) {
    var tailUp = false          // tail on top (bubble sits below the mascot)
        private set
    var tailX = 0f
    private var shape = "pill"; private var tone = "light"; private var tail = true; private var op = 96
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG); private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val d = c.resources.displayMetrics.density
    private val tailH get() = if (tail) 7 * d else 0f

    // the whole message is shown (up to the line limit, then it scrolls) instead of being cut to "..." after one line
    init { setWillNotDraw(false); typeface = Typeface.create("sans-serif", Typeface.NORMAL); ellipsize = null; movementMethod = android.text.method.ScrollingMovementMethod.getInstance() }

    fun style(sp: Int, maxW: Int, lines: Int, shape: String, tone: String, tail: Boolean, opacity: Int) {
        this.shape = shape; this.tone = tone; this.tail = tail; this.op = opacity
        textSize = sp.toFloat(); maxWidth = maxW; maxLines = lines
        setTextColor(when (tone) { "dark" -> 0xFFF1EEEA.toInt(); "orange" -> Color.WHITE; else -> Color.WHITE })
        pad()
    }

    fun setTailSide(up: Boolean) { tailUp = up; pad() }

    private fun pad() {
        val h = (12 * d).toInt(); val v = (8 * d).toInt(); val t = tailH.toInt()
        setPadding(h, v + if (tailUp) t else 0, h, v + if (tailUp) 0 else t)
    }

    override fun onDraw(cv: Canvas) {
        val body = RectF(1f, if (tailUp) tailH else 1f, width - 1f, height - (if (tailUp) 1f else tailH))
        val r = when (shape) { "sharp" -> 4 * d; "soft" -> 14 * d; else -> minOf(body.height() / 2f, 24 * d) }
        val col = when (tone) { "dark" -> 0xFF2B2724.toInt(); "orange" -> 0xFFD97757.toInt(); else -> 0xFF000000.toInt() }
        bg.color = col; bg.alpha = (255 * op / 100f).toInt()
        line.color = when (tone) { "dark" -> 0xFF4A443F.toInt(); "orange" -> 0xFFB85F42.toInt(); else -> 0xFF3A3A3A.toInt() }; line.strokeWidth = d; line.alpha = bg.alpha
        cv.drawRoundRect(body, r, r, bg); cv.drawRoundRect(body, r, r, line)
        if (tail) {
            val tx = tailX.coerceIn(r + 6 * d, width - r - 6 * d)
            val path = Path()
            if (tailUp) { path.moveTo(tx - 6 * d, body.top); path.lineTo(tx, 0f); path.lineTo(tx + 6 * d, body.top) }
            else { path.moveTo(tx - 6 * d, body.bottom); path.lineTo(tx, height - 0f); path.lineTo(tx + 6 * d, body.bottom) }
            path.close()
            cv.drawPath(path, bg)
            // keep the outline open where the tail joins
            val seam = Paint(bg).apply { strokeWidth = 2 * d; style = Paint.Style.STROKE }
            val y = if (tailUp) body.top else body.bottom
            cv.drawLine(tx - 5 * d, y, tx + 5 * d, y, seam)
            val l2 = Paint(line); val dy = if (tailUp) 0f else height.toFloat()
            cv.drawLine(tx - 6 * d, y, tx, dy, l2); cv.drawLine(tx + 6 * d, y, tx, dy, l2)
        }
        super.onDraw(cv)
    }
}
