package com.umutk.pulse.extras.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The island itself, drawn by hand: a black pill that rests over the camera, widens for what is going on (a small picture on the left, a sign on the right)
 * and, on a tap, opens into a card with the song controls or the buttons of the alert. A swipe up closes it again. Its size is reported through [onSize]
 * every frame, so the window around it is never bigger than the island (touches outside it go to the app below).
 */
class IslandView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private var items: List<IslandItem> = emptyList()
    private var cur: IslandItem? = null
    private var expanded = false
    private var act = 0f       // 0 resting, 1 widened for an item
    private var exp = 0f       // 0 closed, 1 opened
    var calibrating = false
    var onSize: (Int, Int) -> Unit = { _, _ -> }
    var onTransport: (String) -> Unit = {}
    var onLong: () -> Unit = {}
    var onClosed: () -> Unit = {}
    private val hits = ArrayList<Pair<RectF, () -> Unit>>()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rect = RectF()
    private var actAnim: ValueAnimator? = null
    private var expAnim: ValueAnimator? = null
    private val tick = object : Runnable { override fun run() { if (cur?.playing == true && cur?.kind == "music") { invalidate(); main.postDelayed(this, 90) } } }
    private val autoClose = Runnable { collapse() }

    private fun dp(v: Float) = v * d
    private val restW get() = dp(IslandPrefs.w)
    private val restH get() = dp(IslandPrefs.h)
    private val sideW get() = dp(44f)
    private val maxW get() = min(dp(360f), resources.displayMetrics.widthPixels - dp(24f))
    private fun openH(k: String?) = dp(when (k) { "music" -> 158f; "volume", "charge" -> 104f; "shot" -> 132f; else -> 112f })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (expanded) {
                hits.firstOrNull { it.first.contains(e.x, e.y) }?.let { it.second(); return true }
                // a tap on the card itself opens what it is about (the song's app, the notification)
                cur?.onTap?.let { it(); collapse(); return true }
                collapse(); return true
            }
            if (cur != null) expand() else onLong()
            return true
        }
        override fun onLongPress(e: MotionEvent) { if (IslandPrefs.haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onLong() }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 != null && expanded && e2.y < e1.y - dp(24f)) { collapse(); return true }
            if (e1 != null && !expanded && cur != null && e2.y > e1.y + dp(24f)) { expand(); return true }
            return false
        }
    })

    /** Called with everything the island has to show now; the one in front is chosen here. */
    fun setItems(list: List<IslandItem>) {
        items = list
        val next = list.maxWithOrNull(compareBy<IslandItem> { it.prio }.thenBy { it.at })
        val changed = next?.id != cur?.id
        cur = next
        if (next == null && expanded) { expanded = false; animExp(0f) }
        animAct(if (next != null) 1f else 0f)
        if (changed && next != null && next.kind != "music" && !expanded && IslandPrefs.haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        main.removeCallbacks(tick); if (next?.kind == "music" && next.playing) main.post(tick)
        apply()
    }

    fun expand() { if (cur == null || expanded) return; expanded = true; animExp(1f); armClose() }
    fun collapse() { if (!expanded) return; expanded = false; animExp(0f); main.removeCallbacks(autoClose); onClosed() }
    private fun armClose() { main.removeCallbacks(autoClose); if (IslandPrefs.collapse > 0 && cur?.kind != "music") main.postDelayed(autoClose, IslandPrefs.collapse * 1000L) }

    private fun animAct(to: Float) {
        if (abs(act - to) < .001f && actAnim?.isRunning != true) return
        actAnim?.cancel()
        actAnim = ValueAnimator.ofFloat(act, to).apply { duration = 260; interpolator = OvershootInterpolator(.8f); addUpdateListener { act = it.animatedValue as Float; apply() }; start() }
    }
    private fun animExp(to: Float) {
        expAnim?.cancel()
        expAnim = ValueAnimator.ofFloat(exp, to).apply { duration = if (to > exp) 360 else 260; interpolator = if (to > exp) OvershootInterpolator(.9f) else DecelerateInterpolator(); addUpdateListener { exp = it.animatedValue as Float; apply() }; start() }
    }

    private fun curW() = lerp(lerp(restW, restW + 2 * sideW, act.coerceIn(0f, 1.2f)), maxW, exp)
    private fun curH() = lerp(restH, openH(cur?.kind), exp)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun apply() {
        val showRest = IslandPrefs.rest || calibrating
        val visible = cur != null || showRest || exp > 0.01f
        visibility = if (visible) VISIBLE else GONE
        onSize(max(1, curW().toInt()), max(1, curH().toInt()))
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN && expanded) main.removeCallbacks(autoClose)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) { if (expanded) armClose() }
        return gestures.onTouchEvent(e)
    }

    private fun bgColor(): Int {
        val it = cur
        return when (IslandPrefs.style) {
            "accent" -> 0xFF1B1B22.toInt()
            "art" -> if (it != null && it.color != 0) darken(it.color) else 0xFF000000.toInt()
            else -> 0xFF000000.toInt()
        }
    }
    private fun darken(c: Int): Int { val f = .45f; return (0xFF shl 24) or (((c shr 16 and 255) * f).toInt() shl 16) or (((c shr 8 and 255) * f).toInt() shl 8) or ((c and 255) * f).toInt() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = lerp(h / 2f, dp(30f), exp).coerceAtMost(h / 2f)
        bg.color = bgColor()
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, r, r, bg)
        if (calibrating) { bg.style = Paint.Style.STROKE; bg.strokeWidth = dp(1.5f); bg.color = 0xFF8C9EFF.toInt(); rect.inset(dp(.75f), dp(.75f)); c.drawRoundRect(rect, r, r, bg); bg.style = Paint.Style.FILL }
        val it = cur ?: return
        hits.clear()
        // clip to the pill so nothing is drawn outside the rounded shape while it grows
        val clip = Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        if (exp < .98f) drawCompact(c, it, w, h, (1f - exp * 1.4f).coerceIn(0f, 1f))
        if (exp > .05f) drawOpen(c, it, w, h, ((exp - .25f) / .75f).coerceIn(0f, 1f))
        c.restore()
    }

    private fun icon(c: Canvas, it: IslandItem, cx: Float, cy: Float, size: Float, alpha: Float) {
        val b = it.icon
        ink.alpha = (255 * alpha).toInt()
        if (b != null) {
            val s = size / 2f
            val p = Path().apply { addRoundRect(cx - s, cy - s, cx + s, cy + s, size * .22f, size * .22f, Path.Direction.CW) }
            c.save(); c.clipPath(p); c.drawBitmap(b, null, RectF(cx - s, cy - s, cx + s, cy + s), ink); c.restore()
        } else glyph(c, it.kind, cx, cy, size * .5f, alpha)
    }

    /** Small drawn signs for the alerts that come without a picture. */
    private fun glyph(c: Canvas, kind: String, cx: Float, cy: Float, s: Float, alpha: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = when (kind) { "charge" -> 0xFF4CD964.toInt(); "torch" -> 0xFFFFD60A.toInt(); "uninstall" -> 0xFFFF453A.toInt(); "bt", "headset" -> 0xFF0A84FF.toInt(); else -> 0xFFFFFFFF.toInt() }; this.alpha = (255 * alpha).toInt(); style = Paint.Style.FILL }
        val path = Path()
        when (kind) {
            "charge" -> { path.moveTo(cx + s * .15f, cy - s); path.lineTo(cx - s * .6f, cy + s * .12f); path.lineTo(cx - s * .05f, cy + s * .12f); path.lineTo(cx - s * .2f, cy + s); path.lineTo(cx + s * .6f, cy - s * .15f); path.lineTo(cx + s * .05f, cy - s * .15f); path.close() }
            "torch" -> { path.addRoundRect(cx - s * .5f, cy - s, cx + s * .5f, cy - s * .2f, s * .15f, s * .15f, Path.Direction.CW); path.addRect(cx - s * .3f, cy - s * .2f, cx + s * .3f, cy + s, Path.Direction.CW) }
            "volume" -> { path.moveTo(cx - s, cy - s * .35f); path.lineTo(cx - s * .4f, cy - s * .35f); path.lineTo(cx + s * .2f, cy - s * .85f); path.lineTo(cx + s * .2f, cy + s * .85f); path.lineTo(cx - s * .4f, cy + s * .35f); path.lineTo(cx - s, cy + s * .35f); path.close() }
            "bt", "headset" -> { p.style = Paint.Style.STROKE; p.strokeWidth = s * .28f; path.addArc(RectF(cx - s * .85f, cy - s * .9f, cx + s * .85f, cy + s * .6f), 180f, 180f); path.moveTo(cx - s * .85f, cy - s * .15f); path.lineTo(cx - s * .85f, cy + s * .7f); path.moveTo(cx + s * .85f, cy - s * .15f); path.lineTo(cx + s * .85f, cy + s * .7f) }
            "uninstall" -> { p.style = Paint.Style.STROKE; p.strokeWidth = s * .28f; path.moveTo(cx - s * .7f, cy - s * .7f); path.lineTo(cx + s * .7f, cy + s * .7f); path.moveTo(cx + s * .7f, cy - s * .7f); path.lineTo(cx - s * .7f, cy + s * .7f) }
            "music" -> { path.addCircle(cx - s * .35f, cy + s * .55f, s * .4f, Path.Direction.CW); path.addRect(cx - s * .0f, cy - s * .9f, cx + s * .3f, cy + s * .55f, Path.Direction.CW); path.addRect(cx, cy - s * .9f, cx + s * .9f, cy - s * .5f, Path.Direction.CW) }
            else -> path.addCircle(cx, cy, s * .7f, Path.Direction.CW)
        }
        c.drawPath(path, p)
    }

    // ---- the widened pill: a picture or sign at the left end, a live sign at the right end ----------------------------------------------------
    private fun drawCompact(c: Canvas, it: IslandItem, w: Float, h: Float, a: Float) {
        if (a <= 0f) return
        val pad = (h - dp(22f)) / 2f
        icon(c, it, pad + dp(11f), h / 2f, dp(22f), a)
        val rx = w - pad - dp(11f)
        ink.alpha = (255 * a).toInt()
        when {
            it.kind == "music" -> {
                // three bars that move with the song
                val t = System.currentTimeMillis() / 150.0
                for (i in 0..2) {
                    val hh = dp(if (it.playing) 5f + 7f * (0.5f + 0.5f * Math.sin(t + i * 1.7).toFloat()) else 5f)
                    val x = rx - dp(8f) + i * dp(6f)
                    ink.color = 0xFF30D158.toInt(); ink.alpha = (255 * a).toInt()
                    c.drawRoundRect(x - dp(2f), h / 2f - hh / 2f, x + dp(2f), h / 2f + hh / 2f, dp(2f), dp(2f), ink)
                }
                ink.color = 0xFFFFFFFF.toInt()
            }
            it.kind == "charge" -> { txt.textSize = dp(13f); txt.typeface = Typeface.DEFAULT_BOLD; txt.color = 0xFF4CD964.toInt(); txt.alpha = (255 * a).toInt(); txt.textAlign = Paint.Align.RIGHT; c.drawText(it.sub, w - pad - dp(2f), h / 2f + dp(4.5f), txt); txt.textAlign = Paint.Align.LEFT; txt.color = 0xFFFFFFFF.toInt() }
            it.level >= 0f -> { ink.color = 0x55FFFFFF; ink.alpha = (110 * a).toInt(); val bx = w - pad - dp(40f); c.drawRoundRect(bx, h / 2f - dp(2f), bx + dp(40f), h / 2f + dp(2f), dp(2f), dp(2f), ink); ink.color = 0xFFFFFFFF.toInt(); ink.alpha = (255 * a).toInt(); c.drawRoundRect(bx, h / 2f - dp(2f), bx + dp(40f) * it.level, h / 2f + dp(2f), dp(2f), dp(2f), ink) }
            else -> { ink.color = 0xFF0A84FF.toInt(); ink.alpha = (255 * a).toInt(); c.drawCircle(rx, h / 2f, dp(3.5f), ink); ink.color = 0xFFFFFFFF.toInt() }
        }
    }

    // ---- the opened card -----------------------------------------------------------------------------------------------------------------------
    private fun ellipsized(s: String, p: Paint, room: Float) = TextUtils.ellipsize(s, android.text.TextPaint(p), room, TextUtils.TruncateAt.END).toString()

    private fun drawOpen(c: Canvas, it: IslandItem, w: Float, h: Float, a: Float) {
        val pad = dp(18f); val top = dp(16f) + max(0f, restH - dp(34f)) * 0f
        val ia = a.coerceIn(0f, 1f)
        val artS = if (it.kind == "music") dp(64f) else dp(48f)
        icon(c, it, pad + artS / 2f, top + artS / 2f, artS, ia)
        val tx = pad + artS + dp(14f); val room = w - tx - pad
        txt.alpha = (255 * ia).toInt(); txt.typeface = Typeface.DEFAULT_BOLD; txt.textSize = dp(16f); txt.color = 0xFFFFFFFF.toInt()
        c.drawText(ellipsized(it.title, txt, room), tx, top + dp(if (it.kind == "music") 24f else 22f), txt)
        txt.typeface = Typeface.DEFAULT; txt.textSize = dp(13f); txt.color = 0xB3FFFFFF.toInt(); txt.alpha = (180 * ia).toInt()
        if (it.sub.isNotEmpty()) c.drawText(ellipsized(it.sub, txt, room), tx, top + dp(if (it.kind == "music") 44f else 42f), txt)
        txt.color = 0xFFFFFFFF.toInt()
        when {
            it.kind == "music" -> {
                // progress, then previous / play-pause / next
                val py = top + artS + dp(14f)
                ink.color = 0x44FFFFFF; ink.alpha = (70 * ia).toInt()
                c.drawRoundRect(pad, py, w - pad, py + dp(3.5f), dp(2f), dp(2f), ink)
                if (it.progress >= 0f) { ink.color = 0xFFFFFFFF.toInt(); ink.alpha = (255 * ia).toInt(); c.drawRoundRect(pad, py, pad + (w - 2 * pad) * it.progress.coerceIn(0f, 1f), py + dp(3.5f), dp(2f), dp(2f), ink) }
                val cy = py + dp(30f); val cx = w / 2f
                ink.color = 0xFFFFFFFF.toInt(); ink.alpha = (255 * ia).toInt()
                tri(c, cx - dp(70f), cy, dp(11f), false); tri(c, cx - dp(70f) + dp(9f), cy, dp(11f), false)
                hits.add(RectF(cx - dp(96f), cy - dp(24f), cx - dp(44f), cy + dp(24f)) to { onTransport("prev") })
                if (it.playing) { c.drawRoundRect(cx - dp(9f), cy - dp(11f), cx - dp(2.5f), cy + dp(11f), dp(2f), dp(2f), ink); c.drawRoundRect(cx + dp(2.5f), cy - dp(11f), cx + dp(9f), cy + dp(11f), dp(2f), dp(2f), ink) } else tri(c, cx - dp(7f), cy, dp(13f), true)
                hits.add(RectF(cx - dp(32f), cy - dp(26f), cx + dp(32f), cy + dp(26f)) to { onTransport("toggle") })
                tri(c, cx + dp(52f), cy, dp(11f), true); tri(c, cx + dp(61f), cy, dp(11f), true)
                hits.add(RectF(cx + dp(44f), cy - dp(24f), cx + dp(96f), cy + dp(24f)) to { onTransport("next") })
            }
            it.level >= 0f -> {
                val py = top + artS + dp(14f)
                ink.color = 0x44FFFFFF; ink.alpha = (70 * ia).toInt(); c.drawRoundRect(pad, py, w - pad, py + dp(8f), dp(4f), dp(4f), ink)
                ink.color = if (it.kind == "charge") 0xFF4CD964.toInt() else 0xFFFFFFFF.toInt(); ink.alpha = (255 * ia).toInt()
                c.drawRoundRect(pad, py, pad + (w - 2 * pad) * it.level.coerceIn(0f, 1f), py + dp(8f), dp(4f), dp(4f), ink); ink.color = 0xFFFFFFFF.toInt()
            }
            it.acts.isNotEmpty() -> {
                var x = w - pad
                txt.typeface = Typeface.DEFAULT_BOLD; txt.textSize = dp(14f)
                it.acts.reversed().forEach { ac ->
                    val tw = txt.measureText(ac.label); val bw = tw + dp(28f); val by = h - dp(18f) - dp(36f)
                    val r = RectF(x - bw, by, x, by + dp(36f))
                    ink.color = 0x33FFFFFF; ink.alpha = (60 * ia).toInt(); c.drawRoundRect(r, dp(18f), dp(18f), ink)
                    txt.color = 0xFFFFFFFF.toInt(); txt.alpha = (255 * ia).toInt(); c.drawText(ac.label, r.left + dp(14f), r.centerY() + dp(5f), txt)
                    hits.add(r to { ac.run(); collapse() })
                    x -= bw + dp(8f)
                }
                ink.color = 0xFFFFFFFF.toInt()
            }
        }
    }

    private fun tri(c: Canvas, cx: Float, cy: Float, s: Float, right: Boolean) {
        val p = Path()
        if (right) { p.moveTo(cx - s * .5f, cy - s); p.lineTo(cx + s * .7f, cy); p.lineTo(cx - s * .5f, cy + s) } else { p.moveTo(cx + s * .5f, cy - s); p.lineTo(cx - s * .7f, cy); p.lineTo(cx + s * .5f, cy + s) }
        p.close(); c.drawPath(p, ink)
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); main.removeCallbacksAndMessages(null); actAnim?.cancel(); expAnim?.cancel() }
}
