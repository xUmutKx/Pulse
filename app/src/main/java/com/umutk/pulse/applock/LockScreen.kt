package com.umutk.pulse.applock

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.Look
import com.umutk.pulse.R
import com.umutk.pulse.ProfilePhoto
import com.umutk.pulse.features.AppLock
import com.umutk.pulse.tr

/**
 * The full-screen lock, drawn like the sign-in screen of the chosen look (plain views: no Compose in a service window).
 * [tryUnlock] checks a PIN, password or pattern and closes the screen when right; [bio] asks for the fingerprint, null when off.
 */
fun lockScreen(c: Context, pkg: String, tryUnlock: (String) -> Boolean, bio: (() -> Unit)?, cancel: () -> Unit, look: String = AppLock.look): FrameLayout =
    LockScreen(c, pkg, tryUnlock, bio, cancel, look).build()

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val W = Color.WHITE
private const val B = Color.BLACK
private fun k(v: Long) = v.toInt()

private class LockScreen(val c: Context, val pkg: String, val tryUnlock: (String) -> Boolean, val bio: (() -> Unit)?, val cancel: () -> Unit, val look: String) {
    val t = AppLock.lookTheme(look)
    val d = c.resources.displayMetrics.density
    val name = AppLock.label(c, pkg)
    // the user picture of the look: the photo from Settings, or the look's own placeholder figure
    val icon: Drawable = profileDrawable()
    val kind = if (AppLock.hasSecret) AppLock.secretKind else ""   // "": fingerprint only
    val wrong = tr("Wrong, try again", "Yanlış, tekrar deneyin")
    val msg = TextView(c).apply { textSize = 13f; maxLines = 2; gravity = Gravity.CENTER }
    lateinit var entry: EditText

    private fun profileDrawable(): Drawable {
        val f = ProfilePhoto.file(c)
        runCatching { if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path)?.let { return android.graphics.drawable.BitmapDrawable(c.resources, it) } }
        val n = 256
        val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = when (t.id) { "win95" -> k(0xFF000080); "xp" -> k(0xFF2B6FD6); "win7" -> k(0xFF2C6FB3); else -> W }
        }
        val cv = android.graphics.Canvas(bmp)
        cv.drawCircle(n / 2f, n * .3f, n * .22f, p)
        cv.drawOval(android.graphics.RectF(n * .12f, n * .58f, n * .88f, n * 1.18f), p)
        return android.graphics.drawable.BitmapDrawable(c.resources, bmp)
    }

    fun build(): FrameLayout = when (t.id) {
        "win10", "win11" -> win10()
        "win7" -> win7()
        "xp" -> xp()
        "win95" -> win95()
        else -> material()
    }

    // ---- shared pieces

    fun px(v: Number) = (v.toFloat() * d).toInt()

    fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(c).apply {
        text = s; textSize = size; setTextColor(color); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun box(color: Int, radiusDp: Number = 0, strokeDp: Number = 0, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radiusDp.toFloat() * d
        if (strokeDp.toFloat() > 0f) setStroke(px(strokeDp), stroke)
    }

    fun gradient(o: GradientDrawable.Orientation, vararg colors: Int, radiusDp: Number = 0) =
        GradientDrawable(o, colors).apply { cornerRadius = radiusDp.toFloat() * d }

    fun column(g: Int = Gravity.CENTER_HORIZONTAL) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = g }
    fun row() = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun LinearLayout.put(v: View, top: Number = 0, w: Int = WRAP, h: Int = WRAP, weight: Float = 0f, start: Number = 0): View {
        addView(v, LinearLayout.LayoutParams(w, h, weight).apply { topMargin = px(top); marginStart = px(start) })
        return v
    }

    fun input(textColor: Int, hintColor: Int) = EditText(c).apply {
        hint = if (kind == "pin") tr("Enter PIN", "PIN girin") else tr("Password", "Şifre")
        isSingleLine = true; textSize = 16f; background = null
        setTextColor(textColor); setHintTextColor(hintColor)
        setPadding(px(10), 0, px(10), 0)
        inputType = if (kind == "pin") InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        imeOptions = EditorInfo.IME_ACTION_DONE
        setOnEditorActionListener { _, _, _ -> submit(); true }
        entry = this
    }

    fun submit() {
        val s = entry.text.toString()
        if (s.isEmpty()) return
        attempt(s)
        entry.text.clear()
    }

    /** Wrong tries are counted across screens (AppLock.fails): every fifth one waits 30 s. */
    fun attempt(s: String) {
        val now = System.currentTimeMillis()
        if (now < AppLock.lockedUntil) { msg.text = tr("Too many tries, wait ${(AppLock.lockedUntil - now) / 1000 + 1} s", "Çok fazla deneme, ${(AppLock.lockedUntil - now) / 1000 + 1} sn bekleyin"); return }
        if (tryUnlock(s)) { AppLock.fails = 0; return }
        AppLock.fails++
        if (AppLock.fails % 5 == 0) AppLock.lockedUntil = now + 30_000
        msg.text = wrong
    }

    fun pad(dot: Int, line: Int) = PatternView(c, dot, line) { attempt(it) }

    fun button(text: String, fg: Int, bg: Drawable?, onClick: () -> Unit) = label(text, 14f, fg).apply {
        background = bg; gravity = Gravity.CENTER; setPadding(px(16), px(8), px(16), px(8)); setOnClickListener { onClick() }
    }

    fun finger(tint: Int, bg: Drawable?) = ImageView(c).apply {
        setImageResource(R.drawable.applock_fingerprint)
        imageTintList = ColorStateList.valueOf(tint)
        background = bg
        setPadding(px(9), px(9), px(9), px(9))
        contentDescription = tr("Fingerprint", "Parmak izi")
        setOnClickListener { bio?.invoke() }
    }

    fun circle(bg: Int, padDp: Int) = ImageView(c).apply {
        setImageDrawable(icon)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
        outlineProvider = ViewOutlineProvider.BACKGROUND; clipToOutline = true
        setPadding(px(padDp), px(padDp), px(padDp), px(padDp))
    }

    /** Full-screen root: a colour or picture behind, [content] centred. It eats every tap, so nothing reaches the app behind. */
    fun screen(content: View, bg: Int = B, picture: Int = 0, blur: Boolean = false, scrim: Int = 0,
               scale: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP, boxW: Int = WRAP, bitmap: android.graphics.Bitmap? = null): FrameLayout = object : FrameLayout(c) {
        // Back leaves the app like Cancel does (the overlay is a window of its own and would otherwise swallow it)
        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (e.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e)
            if (e.action == KeyEvent.ACTION_UP) cancel()
            return true
        }
    }.apply {
        setBackgroundColor(bg); isClickable = true
        // a screen taken from the ready list or shown again starts clean: no old error text, no typed secret
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { msg.text = ""; if (::entry.isInitialized) entry.text.clear() }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        if (picture != 0) addView(ImageView(c).apply {
            setImageResource(picture); scaleType = scale
            if (blur && android.os.Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(70f, 70f, Shader.TileMode.CLAMP))
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        if (bitmap != null) addView(ImageView(c).apply { setImageBitmap(bitmap); scaleType = scale }, FrameLayout.LayoutParams(MATCH, MATCH))
        if (scrim != 0) addView(View(c).apply { setBackgroundColor(scrim) }, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(content, FrameLayout.LayoutParams(boxW, WRAP, Gravity.CENTER))
    }

    // ---- looks

    /** Windows 10 / 11 sign-in: blurred wallpaper, round picture, name, PIN box, sign-in options. */
    fun win10(): FrameLayout {
        val r = if (t.id == "win11") 4 else 0
        val col = column()
        col.put(circle(k(0x33FFFFFF), 34), 0, px(150), px(150))
        col.put(label(name, 28f, W), 18)
        msg.setTextColor(W)
        col.put(msg, 6)
        when (kind) {
            "pattern" -> col.put(pad(W, t.accent.toArgb()), 8)
            "pin", "pass" -> {
                val bar = row().apply { background = box(k(0x66000000), r, 1.5f, k(0xB3FFFFFF)) }
                bar.put(input(W, k(0xB3FFFFFF)), 0, 0, px(40), 1f)
                bar.put(button("→", W, null) { submit() }, 0, px(44), px(40))   // a number keyboard may have no Done key
                col.put(bar, 10, px(290))
            }
        }
        if (bio != null) {
            col.put(label(tr("Sign-in options", "Oturum açma seçenekleri"), 13f, k(0xCCFFFFFF)), 24)
            col.put(finger(W, box(k(0x33FFFFFF), r)), 8, px(44), px(44))
        }
        col.put(button(tr("Cancel", "İptal"), W, box(k(0x33FFFFFF), r)) { cancel() }, 24)
        // Windows 10 and the "Windows 11 blur" look show the blurred wallpaper (rendered once, cached), plain Windows 11 the sharp one
        val w = com.umutk.pulse.features.LockWall
        val blur = t.id == "win10" || look == "win11blur"
        val bmp = if (!blur) w.get(c, t.id) else if (t.id == "win10") w.blurred(c, t.id, 20f, 0x55 shl 24) else w.blurred(c, t.id, AppLock.blurDp.toFloat(), AppLock.tintA shl 24)
        return screen(col, bg = k(0xFF0B2A4A), bitmap = bmp, scrim = if (blur) 0 else k(0x55000000))
    }

    /** Windows 7 logon: Aero wallpaper, framed user tile, password box with the round blue arrow. */
    fun win7(): FrameLayout {
        val col = column()
        val tile = FrameLayout(c).apply {
            background = gradient(GradientDrawable.Orientation.TOP_BOTTOM, k(0xF2FFFFFF), k(0x99C9DDF2), radiusDp = 12).apply { setStroke(px(1), W) }
            setPadding(px(10), px(10), px(10), px(10))
            addView(ImageView(c).apply {
                setImageDrawable(icon); setPadding(px(16), px(16), px(16), px(16))
                background = gradient(GradientDrawable.Orientation.TOP_BOTTOM, k(0xFFEAF3FC), k(0xFFB7D3EE), radiusDp = 4)
            }, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        col.put(tile, 0, px(128), px(128))
        col.put(label(name, 22f, W).apply { setShadowLayer(6f, 0f, 1f, k(0xCC000000)) }, 14)
        msg.setTextColor(W); msg.setShadowLayer(4f, 0f, 1f, k(0xCC000000))
        col.put(msg, 6)
        when (kind) {
            "pattern" -> col.put(pad(W, k(0xFF3399FF)), 8)
            "pin", "pass" -> {
                val r = row()
                r.put(input(B, k(0xFF7A7A7A)).apply { background = box(W, 3, 1, k(0xFF5A7FA8)); textSize = 14f }, 0, px(210), px(34))
                val go = gradient(GradientDrawable.Orientation.TOP_BOTTOM, k(0xFF7CC0F5), k(0xFF1E64B4)).apply {
                    shape = GradientDrawable.OVAL; setStroke(px(1.5f), k(0xFFE6F2FF))
                }
                r.put(button("→", W, go) { submit() }.apply { textSize = 18f; setPadding(0, 0, 0, 0) }, 0, px(34), px(34), start = 6)
                col.put(r, 10)
            }
        }
        val glass = gradient(GradientDrawable.Orientation.TOP_BOTTOM, k(0x66FFFFFF), k(0x22FFFFFF)).apply {
            shape = GradientDrawable.OVAL; setStroke(px(1), k(0x99FFFFFF))
        }
        if (bio != null) col.put(finger(W, glass), 14, px(42), px(42))
        col.put(button(tr("Cancel", "İptal"), W, box(k(0x33FFFFFF), 3, 1, k(0x99FFFFFF))) { cancel() }, 22)
        return screen(col, bg = k(0xFF1E5AA8), bitmap = com.umutk.pulse.features.LockWall.get(c, "win7")).apply {
            addView(ImageView(c).apply { setImageResource(R.drawable.logo_win7); adjustViewBounds = true },
                FrameLayout.LayoutParams(WRAP, px(44), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(28) })
        }
    }

    /** Windows XP welcome screen: blue logon backdrop, one user tile with the green arrow, "Turn off" at the bottom left. */
    fun xp(): FrameLayout {
        val tile = row().apply {
            background = gradient(GradientDrawable.Orientation.LEFT_RIGHT, k(0xFF2A55C8), k(0x005A7EDC), radiusDp = 8)
            setPadding(px(10), px(10), px(16), px(10))
        }
        tile.put(ImageView(c).apply { setImageDrawable(icon); background = box(W, 4, 3, k(0xFFF9C24E)); setPadding(px(8), px(8), px(8), px(8)) },
            0, px(64), px(64))
        val right = column(Gravity.START)
        right.put(label(name, 18f, W, bold = true))
        right.put(label(if (kind == "pattern") tr("Draw your pattern", "Deseninizi çizin") else tr("Type your password", "Şifrenizi yazın"), 11f, W), 2)
        msg.setTextColor(k(0xFFFFE08A)); msg.gravity = Gravity.START
        right.put(msg, 2)
        if (kind == "pin" || kind == "pass") {
            val r = row()
            r.put(input(B, k(0xFF7A7A7A)).apply { background = box(W, 3, 1, k(0xFF7F9DB9)); textSize = 13f }, 0, px(150), px(30))
            val go = gradient(GradientDrawable.Orientation.TOP_BOTTOM, k(0xFF6BCB5B), k(0xFF2E8B22), radiusDp = 4).apply { setStroke(px(1), W) }
            r.put(button("→", W, go) { submit() }.apply { textSize = 16f; setPadding(0, 0, 0, 0) }, 0, px(30), px(30), start = 6)
            right.put(r, 4)
        }
        if (bio != null) right.put(finger(W, box(k(0x33FFFFFF), 4, 1, k(0x99FFFFFF))), 8, px(36), px(36))
        tile.put(right, 0, start = 12)
        val col = column()
        col.put(tile)
        if (kind == "pattern") col.put(pad(W, k(0xFFF9C24E)), 8)
        // the red "Turn off computer" button: here it leaves the app
        val off = row().apply { setOnClickListener { cancel() } }
        off.put(label("✕", 15f, W, bold = true).apply { gravity = Gravity.CENTER; background = box(k(0xFFD8572A), 4, 1, W) }, 0, px(30), px(30))
        off.put(label(tr("Cancel", "İptal"), 14f, W), 0, start = 8)
        return screen(col, bg = k(0xFF5A7EDC), picture = R.drawable.xp_logon_bg, scale = ImageView.ScaleType.FIT_XY).apply {
            addView(off, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { setMargins(px(20), 0, 0, px(22)) })
        }
    }

    /** Windows 95 "Enter Network Password" dialog over the teal desktop. */
    fun win95(): FrameLayout {
        val grey = k(0xFFC0C0C0)
        val dlg = column(Gravity.START).apply { background = Bevel95(true, grey, d); setPadding(px(3), px(3), px(3), px(3)) }
        dlg.put(label(tr("Enter Network Password", "Ağ Şifresini Girin"), 12f, W, bold = true).apply {
            background = gradient(GradientDrawable.Orientation.LEFT_RIGHT, k(0xFF000080), k(0xFF1084D0)); setPadding(px(4), px(3), px(4), px(3))
        }, 0, MATCH)
        val body = row().apply { gravity = Gravity.TOP; setPadding(px(10), px(10), px(10), px(6)) }
        body.put(ImageView(c).apply { setImageDrawable(icon) }, 0, px(32), px(32))
        val f = column(Gravity.START)
        f.put(label(tr("Type your password to open $name.", "$name için şifrenizi yazın."), 12f, B).apply { maxLines = 3 }, 0, MATCH)
        fun line(text: String, v: View) = row().apply { put(label(text, 12f, B), 0, px(72)); put(v, 0, 0, px(24), 1f) }
        f.put(line(tr("Resource:", "Kaynak:"), label(name, 12f, B)), 8, MATCH)
        when (kind) {
            "pin", "pass" -> f.put(line(tr("Password:", "Şifre:"), input(B, k(0xFF808080)).apply {
                background = Bevel95(false, W, d); textSize = 12f; setPadding(px(4), 0, px(4), 0)
            }), 6, MATCH)
            "pattern" -> f.put(pad(B, k(0xFF000080)).apply { background = Bevel95(false, W, d) }, 6)
        }
        msg.setTextColor(k(0xFF800000)); msg.gravity = Gravity.START
        f.put(msg, 4)
        body.put(f, 0, 0, WRAP, 1f, start = 10)
        dlg.put(body, 0, MATCH)
        val buttons = row().apply { gravity = Gravity.END; setPadding(px(10), 0, px(10), px(10)) }
        fun b95(text: String, onClick: () -> Unit) = button(text, B, Bevel95(true, grey, d), onClick).apply { textSize = 12f }
        if (kind == "pin" || kind == "pass") buttons.put(b95("OK") { submit() }, 0, px(72), px(30))
        buttons.put(b95(tr("Cancel", "İptal")) { cancel() }, 0, px(72), px(30), start = 6)
        if (bio != null) buttons.put(finger(B, Bevel95(true, grey, d)).apply { setPadding(px(5), px(5), px(5), px(5)) }, 0, px(30), px(30), start = 6)
        dlg.put(buttons, 0, MATCH)
        return screen(dlg, bg = k(0xFF008080), boxW = px(316), bitmap = com.umutk.pulse.features.LockWall.get(c, "win95"))
    }

    /** Material / AMOLED and the other looks: app icon, name, PIN or pattern, Unlock, fingerprint. */
    fun material(): FrameLayout {
        val night = Look.mode == "dark" || (Look.mode == "system" &&
            (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        // Material You takes the wallpaper colours; AMOLED and the colour looks keep their own
        // shortcut: ignores Look.dynamic off and a picked Material accent, upgrade if the lock must match those exactly
        val dyn = t.id == "material"
        fun sys(dark: Int, light: Int) = androidx.core.content.ContextCompat.getColor(c, if (night) dark else light)
        val coN = t.id == "coloros" && night   // ColorOS: its own dark greys
        val bg = if (dyn) sys(android.R.color.system_neutral1_900, android.R.color.system_neutral1_10) else if (coN) k(0xFF0B1210) else t.bg.toArgb()
        val panel = if (dyn) sys(android.R.color.system_accent2_700, android.R.color.system_accent2_100) else if (coN) k(0xFF16211D) else t.panel.toArgb()
        val text = if (dyn) sys(android.R.color.system_neutral1_100, android.R.color.system_neutral1_900) else if (coN) k(0xFFF2F2F2) else t.text.toArgb()
        val sub = if (dyn) sys(android.R.color.system_neutral2_200, android.R.color.system_neutral2_700) else if (coN) k(0xFF9AAAA2) else t.sub.toArgb()
        val accent = if (dyn) sys(android.R.color.system_accent1_200, android.R.color.system_accent1_600) else t.accent.toArgb()
        val onAcc = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > .45) B else W
        val r = minOf(t.radius.value, 16f)

        val col = column()
        // the locked app's own icon: no user photo in the Material, One UI and ColorOS looks
        col.put(ImageView(c).apply {
            setImageDrawable(runCatching { c.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: icon)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(panel) }
            outlineProvider = ViewOutlineProvider.BACKGROUND; clipToOutline = true
            setPadding(px(14), px(14), px(14), px(14))
        }, 0, px(84), px(84))
        col.put(label(name, 24f, text), 16)
        col.put(label(when (kind) {
            "pin" -> tr("Enter PIN", "PIN girin"); "pass" -> tr("Enter password", "Şifre girin")
            "pattern" -> tr("Draw your pattern", "Deseninizi çizin"); else -> tr("Use your fingerprint", "Parmak izinizi kullanın")
        }, 14f, sub), 4)
        msg.setTextColor(if (t.dark || night) k(0xFFF2B8B5) else k(0xFFB3261E))
        col.put(msg, 6)
        when (kind) {
            "pattern" -> col.put(pad(text, accent), 8)
            "pin", "pass" -> {
                col.put(input(text, sub).apply { background = if (t.id == "coloros") box(panel, 26) else box(Color.TRANSPARENT, r, 1.5f, sub); hint = null }, 10, px(280), px(52))
                col.put(button(tr("Unlock", "Aç"), onAcc, box(accent, 24)) { submit() }, 12, px(280), px(48))
            }
        }
        if (bio != null) col.put(finger(accent, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(panel) }), 18, px(56), px(56))
        col.put(button(tr("Cancel", "İptal"), accent, null) { cancel() }, 12)
        return screen(col, bg = bg, boxW = px(300))
    }
}

/** Windows 95 raised or sunken two-pixel edge around a flat fill. */
private class Bevel95(private val raised: Boolean, private val fill: Int, private val d: Float) : Drawable() {
    private val p = Paint()

    override fun draw(cv: Canvas) {
        val b = bounds
        p.color = fill; cv.drawRect(b, p)
        val l = b.left.toFloat(); val tp = b.top.toFloat(); val r = b.right.toFloat(); val bt = b.bottom.toFloat()
        fun edge(i: Int, tl: Int, br: Int) {
            val o = i * d
            p.color = tl; cv.drawRect(l + o, tp + o, r - o, tp + o + d, p); cv.drawRect(l + o, tp + o, l + o + d, bt - o, p)
            p.color = br; cv.drawRect(l + o, bt - o - d, r - o, bt - o, p); cv.drawRect(r - o - d, tp + o, r - o, bt - o, p)
        }
        if (raised) { edge(0, k(0xFFDFDFDF), B); edge(1, W, k(0xFF808080)) }
        else { edge(0, k(0xFF808080), W); edge(1, B, k(0xFFDFDFDF)) }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
