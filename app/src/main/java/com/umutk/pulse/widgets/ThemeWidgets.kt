package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.umutk.pulse.MainActivity
import com.umutk.pulse.R
import com.umutk.pulse.mb
import com.umutk.pulse.rate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The numbers a themed widget shows: the sampler's saved values, the battery broadcast and the data partition. */
class SkinData(val cpu: Int, val ram: Int, val bat: Int, val charging: Boolean, val diskUsed: Int, val diskFree: Long, val now: Calendar,
               val ramUsedMb: Int = 0, val ramTotalMb: Int = 0, val down: Long = 0, val up: Long = 0, val temp: Float = 0f) {
    /** The same moment in another time zone, for the world clock. */
    fun at(zone: TimeZone) = SkinData(cpu, ram, bat, charging, diskUsed, diskFree, Calendar.getInstance(zone).apply { timeInMillis = now.timeInMillis })

    companion object {
        fun read(ctx: Context): SkinData {
            val p = WidgetData.prefs(ctx)
            val total = p.getInt("ramTotal", 0)
            val ram = if (total > 0) p.getInt("ramUsed", 0) * 100 / total else 0
            val (free, size) = try { StatFs(Environment.getDataDirectory().path).let { it.availableBytes to it.totalBytes } } catch (e: Exception) { 0L to 0L }
            val used = if (size > 0) ((size - free) * 100 / size).toInt() else 0
            // the battery from its sticky broadcast, so it is right even when the sampler has not run for a while
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val bat = if (level >= 0 && scale > 0) level * 100 / scale else p.getInt("bat", 0)
            val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            return SkinData(p.getInt("cpu", 0), ram, bat, plugged, used, free, Calendar.getInstance(),
                p.getInt("ramUsed", 0), total, p.getLong("down", 0), p.getLong("up", 0), p.getFloat("temp", 0f))
        }
    }
}

/** The look a set of widgets belongs to; the Widgets page groups them by it and puts the app's own look first. */
enum class SkinFamily(val label: String, val theme: String) {
    WIN7("Windows 7 gadgets", "win7"), WIN11("Windows 11", "win11"), XP("Windows XP", "xp"), WIN95("Windows 95", "win95"),
    MATERIAL("Material You", "material"), AMOLED("AMOLED", "amoled"), GLASS("Glass", "glass"), IOS("iOS", "ios"),
}

/** One themed widget: its family, name, bitmap size in pixels and the Pulse page a tap opens. */
enum class Skin(val family: SkinFamily, val label: String, val w: Int, val h: Int, val page: String = "") {
    W7_CLOCK_TRAD(SkinFamily.WIN7, "Clock · Traditional", 390, 390),
    W7_CLOCK_SYSTEM(SkinFamily.WIN7, "Clock · System", 390, 390),
    W7_CLOCK_CRONO(SkinFamily.WIN7, "Clock · Chronometer", 390, 390),
    W7_CLOCK_DINER(SkinFamily.WIN7, "Clock · Diner", 390, 390),
    W7_CLOCK_FLOWER(SkinFamily.WIN7, "Clock · Flower", 390, 390),
    W7_CLOCK_MODERN(SkinFamily.WIN7, "Clock · Modern", 390, 390),
    W7_CLOCK_SQUARE(SkinFamily.WIN7, "Clock · Square", 390, 390),
    W7_CLOCK_NOVELTY(SkinFamily.WIN7, "Clock · Novelty", 390, 390),
    W7_CLOCK_MONITOR(SkinFamily.WIN7, "Clock · Monitor", 390, 390),
    W7_CPU(SkinFamily.WIN7, "CPU Meter", 396, 318, "performance"),
    W7_CALENDAR(SkinFamily.WIN7, "Calendar", 390, 423),
    W7_BATTERY(SkinFamily.WIN7, "Battery", 405, 240, "device"),
    W7_DRIVE(SkinFamily.WIN7, "Drive", 468, 144, "files"),
    W11_CLOCK(SkinFamily.WIN11, "Clock", 360, 360),
    W11_DATE(SkinFamily.WIN11, "Date", 360, 360),
    W11_CPU(SkinFamily.WIN11, "CPU", 360, 360, "performance"),
    W11_MEMORY(SkinFamily.WIN11, "Memory", 360, 360, "performance"),
    W11_BATTERY(SkinFamily.WIN11, "Battery", 360, 360, "device"),
    W11_STORAGE(SkinFamily.WIN11, "Storage", 360, 360, "files"),
    W11_CALENDAR(SkinFamily.WIN11, "Calendar", 360, 360),
    W11_TIME(SkinFamily.WIN11, "Time", 360, 360),
    XP_CLOCK(SkinFamily.XP, "Clock", 360, 390),
    XP_CPU(SkinFamily.XP, "CPU Usage", 360, 390, "performance"),
    XP_BATTERY(SkinFamily.XP, "Power Meter", 480, 270, "device"),
    XP_DISK(SkinFamily.XP, "Local Disk", 480, 330, "files"),
    W95_TIME(SkinFamily.WIN95, "Time", 360, 360),
    W95_CPU(SkinFamily.WIN95, "CPU", 420, 216, "performance"),
    W95_BATTERY(SkinFamily.WIN95, "Battery", 420, 216, "device"),
    W95_MEMORY(SkinFamily.WIN95, "Memory", 420, 216, "performance"),
    W95_DATE(SkinFamily.WIN95, "Date", 420, 360),
    M3_BATTERY(SkinFamily.MATERIAL, "Battery", 360, 360, "device"),
    M3_CLOCK(SkinFamily.MATERIAL, "Clock", 360, 360),
    M3_DATE(SkinFamily.MATERIAL, "Date", 360, 360),
    M3_CPU(SkinFamily.MATERIAL, "CPU", 360, 360, "performance"),
    M3_STORAGE(SkinFamily.MATERIAL, "Storage", 480, 240, "files"),
    M3_MEMORY(SkinFamily.MATERIAL, "Memory", 360, 360, "performance"),
    M3_DATE_PILL(SkinFamily.MATERIAL, "Date pill", 480, 240),
    M3_CLOCK_DATE(SkinFamily.MATERIAL, "Clock and date", 480, 240),
    M3_BATTERY_RING(SkinFamily.MATERIAL, "Battery ring", 360, 360, "device"),
    M3_TEMP(SkinFamily.MATERIAL, "Temperature", 360, 360, "performance"),
    M3_RAM_BAR(SkinFamily.MATERIAL, "Memory bar", 480, 240, "performance"),
    M3_NETWORK(SkinFamily.MATERIAL, "Network", 480, 240, "performance"),
    M3_UPTIME(SkinFamily.MATERIAL, "Uptime", 360, 360, "device"),
    M3_OVERVIEW(SkinFamily.MATERIAL, "Overview", 480, 240, "performance"),
    AM_CLOCK(SkinFamily.AMOLED, "Clock", 480, 240),
    AM_BATTERY(SkinFamily.AMOLED, "Battery", 360, 360, "device"),
    AM_CPU(SkinFamily.AMOLED, "CPU", 360, 360, "performance"),
    AM_DATE(SkinFamily.AMOLED, "Date", 360, 360),
    GL_BATTERY(SkinFamily.GLASS, "Battery", 360, 360, "device"),
    GL_CLOCK(SkinFamily.GLASS, "Clock", 360, 360),
    GL_TIME(SkinFamily.GLASS, "Digital clock", 480, 240),
    GL_DATE(SkinFamily.GLASS, "Date", 360, 360),
    GL_BATTERY_RING(SkinFamily.GLASS, "Battery ring", 360, 360, "device"),
    GL_CPU(SkinFamily.GLASS, "CPU", 360, 360, "performance"),
    GL_MEMORY(SkinFamily.GLASS, "Memory", 360, 360, "performance"),
    GL_STORAGE(SkinFamily.GLASS, "Storage", 480, 240, "files"),
    IOS_CLOCK_DATE(SkinFamily.IOS, "Clock and date", 480, 240),
    IOS_RINGS(SkinFamily.IOS, "Battery rings", 360, 360, "device"),
    IOS_STORAGE(SkinFamily.IOS, "Storage", 360, 360, "files"),
    IOS_MEMORY(SkinFamily.IOS, "Memory", 360, 360, "performance"),
    IOS_UP_NEXT(SkinFamily.IOS, "Up next", 360, 360),
    IOS_WORLD(SkinFamily.IOS, "World clock", 480, 240),
}

/** A home-screen widget drawn as one bitmap in its theme's look; one provider per [Skin] so each is listed on its own. */
abstract class SkinWidget(val skin: Skin) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { push(ctx, mgr, ids); if (ticking && ids.isNotEmpty()) scheduleTick(ctx) }

    // the world clock and Up next are drawn into one bitmap, so a per-minute alarm chain (inexact, may lag in Doze) and the time, zone and date broadcasts redraw them
    private val ticking get() = skin == Skin.IOS_WORLD || skin == Skin.IOS_UP_NEXT
    private fun alarm(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    private fun tickIntent(ctx: Context) = PendingIntent.getBroadcast(ctx, 2000 + skin.ordinal, Intent(ctx, javaClass).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun scheduleTick(ctx: Context) = if (android.os.Build.VERSION.SDK_INT >= 23) alarm(ctx).setAndAllowWhileIdle(android.app.AlarmManager.RTC, (System.currentTimeMillis() / 60_000 + 1) * 60_000, tickIntent(ctx)) else alarm(ctx).set(android.app.AlarmManager.RTC, (System.currentTimeMillis() / 60_000 + 1) * 60_000, tickIntent(ctx))

    override fun onDisabled(ctx: Context) { if (ticking) alarm(ctx).cancel(tickIntent(ctx)) }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (!ticking) return
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, TICK -> {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, javaClass))
                if (ids.isNotEmpty()) { push(ctx, mgr, ids); scheduleTick(ctx) }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: android.os.Bundle?) = push(ctx, mgr, intArrayOf(id))

    override fun onDeleted(ctx: Context, ids: IntArray) = WidgetAccent.remove(ctx, ids)

    /** Draws once per accent in use, so instances sharing an accent share one bitmap. */
    fun push(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        ids.forEach { mgr.updateAppWidget(it, views(ctx, WidgetAccent.of(ctx, it), it)) }

    fun views(ctx: Context, accent: Int, id: Int = AppWidgetManager.INVALID_APPWIDGET_ID): RemoteViews {
        // clocks and dates tick on their own: analog skins through AnalogClock with the clk_<skin>_* art, the rest with TextClocks over a bare background
        fun art(part: String) = ctx.resources.getIdentifier("clk_${skin.name.lowercase(Locale.ROOT)}_$part", "drawable", ctx.packageName)
        val digital = liveLayouts[skin]
        val dens = ctx.resources.displayMetrics.density
        val px = WidgetSize.dp(ctx, id)?.let { (it.first * dens).toInt() to (it.second * dens).toInt() } ?: (0 to 0)
        val v = when {
            art("dial") != 0 && android.os.Build.VERSION.SDK_INT >= 23 -> RemoteViews(ctx.packageName, if (skin == Skin.GL_CLOCK) R.layout.widget_skin_gl_clock else R.layout.widget_skin_analog).apply {
                // AnalogClock scales the dial and the hands together only when they are the same size, so the drawn dials take the hands' size
                val hs = Skins.sizeOf(ctx, art("hour"))
                when (skin) {
                    Skin.W11_CLOCK -> setIcon(R.id.clock, "setDial", Icon.createWithBitmap(Skins.win11Dial(ctx, id, hs.first)))
                    Skin.XP_CLOCK, Skin.W95_TIME, Skin.GL_CLOCK -> setIcon(R.id.clock, "setDial", Icon.createWithBitmap(Skins.liveDial(ctx, skin, id, accent, hs.first, hs.second)))
                    else -> setIcon(R.id.clock, "setDial", Icon.createWithResource(ctx, art("dial")))
                }
                setIcon(R.id.clock, "setHourHand", Icon.createWithResource(ctx, art("hour")).apply { if (skin == Skin.GL_CLOCK) setTint(Skins.readable(accent)) })
                setIcon(R.id.clock, "setMinuteHand", Icon.createWithResource(ctx, art("minute")))
                art("second").let { if (it != 0) setIcon(R.id.clock, "setSecondHand", Icon.createWithResource(ctx, it)) }
                setImageViewResource(R.id.cap, art("cap"))
                if (skin == Skin.W11_CLOCK) {
                    // the Windows 11 hands take the tile accent like the preview
                    val prev = Win11Material.forId.get(); Win11Material.forId.set(id)
                    val a = Win11Material.accent(ctx)
                    Win11Material.forId.set(prev)
                    setIcon(R.id.clock, "setHourHand", Icon.createWithResource(ctx, art("hour")).setTint(a))
                    setIcon(R.id.clock, "setMinuteHand", Icon.createWithResource(ctx, art("minute")).setTint(a))
                }
                // the art is opaque: fade the whole live clock like Skins.draw fades the bitmap (Windows 11 always follows its opacity)
                // (the drawn dials are faded in liveDial; only the Windows 7 gadget art, which is a resource, fades as a whole here)
                if (skin.family == SkinFamily.WIN7) Skins.opacity(ctx, skin, id).let { if (it < 1f) setFloat(R.id.root, "setAlpha", it) }
            }
            digital != null -> RemoteViews(ctx.packageName, digital).apply {
                val d = SkinData.read(ctx)
                setImageViewBitmap(R.id.face, Skins.drawBackground(ctx, skin, d, accent, id, px.first, px.second))
                Skins.clockColors(ctx, skin, accent).forEachIndexed { i, col -> setTextColor(clockIds[i], col) }
                // shortcut: the Sunday red is set at each refresh, so it can lag the midnight change until the next one
                if (skin.family == SkinFamily.WIN11) {
                    // keep the TextClocks inside the fit-centred square the bitmap occupies (and its 90% content area), whatever the cell shape
                    val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
                    val dm = ctx.resources.displayMetrics.density
                    val wd = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 110
                    val hd = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 110
                    val sq = minOf(wd, hd)
                    val padX = (((wd - sq) / 2f + sq * (if (skin == Skin.W11_TIME) .25f else .1f)) * dm).toInt()
                    val padY = (((hd - sq) / 2f + sq * .05f) * dm).toInt()
                    setViewPadding(R.id.w11_box, padX, padY, padX, padY)
                    val prev = Win11Material.forId.get(); Win11Material.forId.set(id)
                    val a = Win11Material.accent(ctx)
                    Win11Material.forId.set(prev)
                    setTextColor(R.id.w11_text1, a); if (skin == Skin.W11_DATE) { setTextColor(R.id.w11_text2, a); setTextColor(R.id.w11_text3, a) }
                }
                if (skin == Skin.W7_CALENDAR) setTextColor(R.id.day, if (d.now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) Color.rgb(214, 66, 32) else Color.rgb(52, 52, 52))
            }
            else -> RemoteViews(ctx.packageName, R.layout.widget_face).apply { setImageViewBitmap(R.id.face, Skins.draw(ctx, skin, SkinData.read(ctx), accent, id, px.first, px.second)) }
        }
        v.setOnClickPendingIntent(R.id.root, WidgetTap.skin(ctx, skin, id))
        return v
    }

    companion object {
        private val liveLayouts = mapOf(
            Skin.M3_CLOCK to R.layout.widget_skin_m3_clock, Skin.AM_CLOCK to R.layout.widget_skin_am_clock,
            Skin.W11_TIME to R.layout.widget_skin_w11_time, Skin.W11_DATE to R.layout.widget_skin_w11_date,
            Skin.M3_DATE to R.layout.widget_skin_m3_date, Skin.AM_DATE to R.layout.widget_skin_am_date,
            Skin.W7_CALENDAR to R.layout.widget_skin_w7_calendar,
            Skin.M3_CLOCK_DATE to R.layout.widget_skin_time_date, Skin.IOS_CLOCK_DATE to R.layout.widget_skin_time_date,
            Skin.GL_TIME to R.layout.widget_skin_am_clock, Skin.GL_DATE to R.layout.widget_skin_m3_date,
        )
        private val clockIds = intArrayOf(R.id.skin_t1, R.id.skin_t2, R.id.skin_t3)
        private const val TICK = "com.umutk.pulse.SKIN_TICK"

        /** Redraws every placed themed widget; called with the other widget refreshes. */
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            SkinWidgets.all.forEach { w ->
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, w.javaClass))
                if (ids.isNotEmpty()) w.push(ctx, mgr, ids)
            }
        }
    }
}

/** Draws each [Skin]. The Windows 7 gadgets use the real gadget art; the others are drawn to match their look. */
object Skins {
    private val art = HashMap<Int, Bitmap>()

    private fun img(ctx: Context, id: Int): Bitmap = art.getOrPut(id) {
        BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }

    /** The whole widget with the time drawn in; the in-app previews use this. [id] picks that widget's own look overrides, [pw] x [ph] px the cell it is drawn for (aspect kept). */
    fun draw(ctx: Context, s: Skin, d: SkinData, accent: Int = WidgetAccent.app(ctx), id: Int = 0, pw: Int = 0, ph: Int = 0): Bitmap = draw(ctx, s, d, true, accent, id, pw, ph)

    fun win11Accent(ctx: Context, id: Int): Int {
        val prev = Win11Material.forId.get(); Win11Material.forId.set(id)
        try { return Win11Material.accent(ctx) } finally { Win11Material.forId.set(prev) }
    }

    /** The Windows 11 clock dial for the live AnalogClock: the same Win11Material tile as the date widget (material, tint, opacity, accent), ticks and numerals in the accent, no hands. */
    fun win11Dial(ctx: Context, id: Int, px: Int): Bitmap {
        val s = px.toFloat()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val prev = Win11Material.forId.get(); Win11Material.forId.set(id)
        try {
            Win11Material.draw(ctx, c, s, s, s * .16f)
            val acc = Win11Material.accent(ctx)
            c.drawCircle(s / 2f, s / 2f, s * .4f, paint(Color.argb(24, 255, 255, 255)))
            analog(c, s / 2f, s / 2f, s * .4f, SkinData.read(ctx), acc, acc, numerals = acc, face = medium, hands = false)
        } finally { Win11Material.forId.set(prev) }
        return b
    }

    /** The widget without its time and date text, for the live widgets that lay TextClocks over it. */
    fun drawBackground(ctx: Context, s: Skin, d: SkinData, accent: Int, id: Int = 0, pw: Int = 0, ph: Int = 0): Bitmap = draw(ctx, s, d, false, accent, id, pw, ph)

    private fun draw(ctx: Context, s: Skin, d: SkinData, withText: Boolean, accent: Int, id: Int, pw: Int, ph: Int): Bitmap {
        // the bitmap is the skin's own aspect scaled to fill the cell, so the launcher shows it at its real pixel size
        val k = if (pw > 0 && ph > 0) minOf(pw / s.w.toFloat(), ph / s.h.toFloat()).coerceIn(.5f, 4f).coerceAtMost(1400f / maxOf(s.w, s.h)) else 1f
        var bmp = Bitmap.createBitmap((s.w * k).toInt(), (s.h * k).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(k, k)
        val prev = Win11Material.forId.get(); Win11Material.forId.set(id)
        try {
            when (s.family) {
                SkinFamily.WIN7 -> win7(ctx, c, s, d, withText)
                SkinFamily.WIN11 -> win11(ctx, c, s, d, withText)
                SkinFamily.XP -> xp(c, s, d)
                SkinFamily.WIN95 -> win95(c, s, d)
                SkinFamily.MATERIAL -> material(ctx, c, s, d, withText, accent)
                SkinFamily.AMOLED -> amoled(c, s, d, withText)
                SkinFamily.GLASS -> glass(c, s, d, withText, accent)
                SkinFamily.IOS -> ios(c, s, d, withText, accent)
            }
        } finally { Win11Material.forId.set(prev) }
        // the other families are opaque art: a per-widget opacity fades the whole bitmap (Windows 11 applies it to its tile only)
        val op = opacity(ctx, s, id)
        if (op < 1f) bmp = fade(bmp, op)
        return bmp
    }

    /** The tile opacity of a skin: the widget's own, else its design's default (XP, 95 and AMOLED opaque, Windows 7 .85, Glass .6, iOS .9, Material .95); Windows 11 fades its tile itself. */
    fun opacity(ctx: Context, s: Skin, id: Int): Float = if (s.family == SkinFamily.WIN11) 1f else WidgetOver.opacity(ctx, id, s.family.theme).coerceIn(0f, 1f)

    private fun fade(b: Bitmap, a: Float): Bitmap {
        val faded = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(faded).drawBitmap(b, 0f, 0f, Paint().apply { alpha = (a * 255).toInt() })
        return faded
    }

    /** True while a live dial is drawn: the window or panel only, the face centred, no hands and no digits (AnalogClock and TextClocks add those). */
    private val dialOnly = ThreadLocal.withInitial { false }

    /** The pixel size of a drawable resource without loading it; AnalogClock needs the dial exactly as big as its hands. */
    fun sizeOf(ctx: Context, res: Int): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeResource(ctx.resources, res, o)
        return o.outWidth.coerceAtLeast(1) to o.outHeight.coerceAtLeast(1)
    }

    /** The dial of a live analog skin (Windows XP, Windows 95, Glass) drawn by the same function as the skin's other widgets, at [w] x [h] px (the hands' size), faded to the skin's opacity. */
    fun liveDial(ctx: Context, s: Skin, id: Int, accent: Int, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(w / s.w.toFloat(), h / s.h.toFloat())
        val prev = Win11Material.forId.get(); Win11Material.forId.set(id); dialOnly.set(true)
        try {
            val d = SkinData.read(ctx)
            when (s.family) {
                SkinFamily.XP -> xp(c, s, d)
                SkinFamily.WIN95 -> win95(c, s, d)
                SkinFamily.GLASS -> glass(c, s, d, false, accent)
                else -> {}
            }
        } finally { dialOnly.set(false); Win11Material.forId.set(prev) }
        val op = opacity(ctx, s, id)
        return if (op < 1f) fade(b, op) else b
    }

    // ---------------------------------------------------------------- shared helpers

    private fun paint(color: Int, aa: Boolean = true) = Paint(if (aa) Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG else Paint.FILTER_BITMAP_FLAG).apply { this.color = color }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface = Typeface.DEFAULT,
                     align: Paint.Align = Paint.Align.CENTER, shadow: Int = 0, aa: Boolean = true) {
        val p = paint(color, aa).apply { textSize = size; typeface = face; textAlign = align }
        if (shadow != 0) p.setShadowLayer(size * .08f, 0f, size * .05f, shadow)
        c.drawText(s, x, y, p)
    }

    private fun fitText(s: String, max: Float, size: Float, face: Typeface): Float {
        val p = Paint().apply { textSize = size; typeface = face }
        val w = p.measureText(s)
        return if (w <= max) size else size * max / w
    }

    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansBold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val thin = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

    private fun hh(d: SkinData) = String.format(Locale.getDefault(), "%02d", d.now.get(Calendar.HOUR_OF_DAY))
    private fun mm(d: SkinData) = String.format(Locale.getDefault(), "%02d", d.now.get(Calendar.MINUTE))
    private fun fmt(d: SkinData, p: String) = SimpleDateFormat(p, Locale.getDefault()).format(d.now.time)

    /** Hour and minute hand angles in degrees, 0 at twelve. */
    private fun angles(d: SkinData): Pair<Float, Float> {
        val h = d.now.get(Calendar.HOUR) ; val m = d.now.get(Calendar.MINUTE)
        return (h * 30f + m / 2f) to (m * 6f)
    }

    private fun gb(b: Long) = String.format(Locale.getDefault(), "%.1f GB", b / 1073741824.0)

    /** A filled arc gauge: the track, then [pct] of the sweep in [color]. */
    private fun gauge(c: Canvas, r: RectF, pct: Int, stroke: Float, track: Int, color: Int, start: Float = 135f, sweep: Float = 270f) {
        val p = paint(track).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        c.drawArc(r, start, sweep, false, p)
        p.color = color
        if (pct > 0) c.drawArc(r, start, sweep * pct.coerceIn(0, 100) / 100f, false, p)
    }

    /** A month grid: weekday initials, then the days with today marked. */
    private fun monthGrid(c: Canvas, d: SkinData, area: RectF, head: Int, day: Int, mark: Int, markText: Int, dim: Int, face: Typeface, round: Boolean) {
        val cal = d.now.clone() as Calendar
        val today = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Monday first
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cw = area.width() / 7f; val rows = (first + days + 6) / 7 + 1; val ch = area.height() / rows
        val size = min(cw, ch) * .55f
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, s -> text(c, s, area.left + cw * (i + .5f), area.top + ch * .7f, size, if (i >= 5) dim else head, face) }
        for (n in 1..days) {
            val idx = first + n - 1; val col = idx % 7; val row = idx / 7 + 1
            val x = area.left + cw * (col + .5f); val y = area.top + ch * row
            if (n == today) {
                val p = paint(mark)
                if (round) c.drawCircle(x, y + ch * .45f, min(cw, ch) * .46f, p) else c.drawRect(x - cw / 2f, y + 1f, x + cw / 2f, y + ch - 1f, p)
            }
            text(c, "$n", x, y + ch * .68f, size, if (n == today) markText else if (col >= 5) dim else day, face)
        }
    }

    /** An analog face drawn from scratch: ticks or numerals, then the hands. */
    private fun analog(c: Canvas, cx: Float, cy: Float, r: Float, d: SkinData, tick: Int, hand: Int, numerals: Int = 0, face: Typeface = sans, minuteW: Float = r * .06f, hands: Boolean = true) {
        val p = paint(tick).apply { strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val big = i % 5 == 0
            if (numerals != 0 && big) continue
            p.strokeWidth = if (big) r * .035f else r * .014f
            val r0 = if (big) r * .84f else r * .9f
            c.drawLine(cx + (r0 * sin(a)).toFloat(), cy - (r0 * cos(a)).toFloat(), cx + (r * .96f * sin(a)).toFloat(), cy - (r * .96f * cos(a)).toFloat(), p)
        }
        if (numerals != 0) for (n in 1..12) {
            val a = Math.toRadians(n * 30.0)
            text(c, "$n", cx + (r * .76f * sin(a)).toFloat(), cy - (r * .76f * cos(a)).toFloat() + r * .085f, r * .23f, numerals, face)
        }
        if (!hands) return
        val (ha, ma) = angles(d)
        p.color = hand
        fun handLine(deg: Float, len: Float, w: Float) {
            val a = Math.toRadians(deg.toDouble()); p.strokeWidth = w
            c.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), p)
        }
        handLine(ha, r * .5f, minuteW * 1.4f)
        handLine(ma, r * .75f, minuteW)
        c.drawCircle(cx, cy, minuteW * 1.4f, paint(hand))
    }

    // ---------------------------------------------------------------- Windows 7: the real gadget art

    private class ClockArt(val face: Int, val top: Int, val h: Int, val m: Int, val dot: Int, val left: Float, val y: Float, val pw: Float, val ph: Float)

    private val clocks = mapOf(
        Skin.W7_CLOCK_TRAD to ClockArt(R.drawable.gadget_clk_trad, R.drawable.gadget_clk_trad_top, R.drawable.gadget_clk_trad_h, R.drawable.gadget_clk_trad_m, R.drawable.gadget_clk_trad_dot, 57f, -1f, 13f, 129f),
        Skin.W7_CLOCK_SYSTEM to ClockArt(R.drawable.gadget_clk_system, R.drawable.gadget_clk_system_top, R.drawable.gadget_clk_system_h, R.drawable.gadget_clk_system_m, R.drawable.gadget_clk_system_dot, 58f, 0f, 13f, 129f),
        Skin.W7_CLOCK_CRONO to ClockArt(R.drawable.gadget_clk_cronometer, R.drawable.gadget_clk_cronometer_top, R.drawable.gadget_clk_cronometer_h, R.drawable.gadget_clk_cronometer_m, R.drawable.gadget_clk_cronometer_dot, 57f, -1f, 13f, 129f),
        Skin.W7_CLOCK_DINER to ClockArt(R.drawable.gadget_clk_diner, R.drawable.gadget_clk_diner_top, R.drawable.gadget_clk_diner_h, R.drawable.gadget_clk_diner_m, R.drawable.gadget_clk_diner_dot, 58f, -1f, 13f, 129f),
        Skin.W7_CLOCK_FLOWER to ClockArt(R.drawable.gadget_clk_flower, R.drawable.gadget_clk_flower_top, R.drawable.gadget_clk_flower_h, R.drawable.gadget_clk_flower_m, R.drawable.gadget_clk_flower_dot, 59f, 0f, 13f, 129f),
        Skin.W7_CLOCK_MODERN to ClockArt(R.drawable.gadget_clk_modern, R.drawable.gadget_clk_modern_top, R.drawable.gadget_clk_modern_h, R.drawable.gadget_clk_modern_m, R.drawable.gadget_clk_modern_dot, 58f, -1f, 13f, 129f),
        Skin.W7_CLOCK_SQUARE to ClockArt(R.drawable.gadget_clk_square, R.drawable.gadget_clk_square_top, R.drawable.gadget_clk_square_h, R.drawable.gadget_clk_square_m, R.drawable.gadget_clk_square_dot, 57f, -1f, 13f, 129f),
        Skin.W7_CLOCK_NOVELTY to ClockArt(R.drawable.gadget_clk_novelty, R.drawable.gadget_clk_novelty_top, R.drawable.gadget_clk_novelty_h, R.drawable.gadget_clk_novelty_m, R.drawable.gadget_clk_novelty_dot, 59f, 46f, 7f, 81f),
        Skin.W7_CLOCK_MONITOR to ClockArt(R.drawable.gadget_clk_monitor, R.drawable.gadget_clk_monitor_top, R.drawable.gadget_clk_monitor_h, R.drawable.gadget_clk_monitor_m, R.drawable.gadget_clk_monitor_dot, 57f, -1f, 13f, 129f),
    )

    private fun win7(ctx: Context, c: Canvas, s: Skin, d: SkinData, withText: Boolean) {
        val p = paint(Color.WHITE)
        clocks[s]?.let { a ->
            // the gadget's own 130 px layout, scaled up: face, hour and minute hands, the centre cap, the glass highlight
            c.scale(s.w / 130f, s.h / 130f)
            c.drawBitmap(img(ctx, a.face), null, RectF(0f, 0f, 130f, 130f), p)
            val (ha, ma) = angles(d)
            fun hand(id: Int, deg: Float) {
                c.save(); c.rotate(deg, a.left + a.pw / 2f, a.y + a.ph / 2f)
                c.drawBitmap(img(ctx, id), null, RectF(a.left, a.y, a.left + a.pw, a.y + a.ph), p)
                c.restore()
            }
            hand(a.h, ha); hand(a.m, ma); hand(a.dot, 0f)
            c.drawBitmap(img(ctx, R.drawable.gadget_clk_highlights), null, RectF(3f, 3f, 127f, 127f), p)
            return
        }
        when (s) {
            Skin.W7_CPU -> {
                // the gadget's large layout (198 x 159): CPU dial on the left, memory on the right, needles turn 2.5 degrees per percent from -125
                c.scale(s.w / 198f, s.h / 159f)
                c.drawBitmap(img(ctx, R.drawable.gadget_cpu_back), null, RectF(0f, 0f, 198f, 159f), p)
                c.save(); c.rotate(d.cpu * 2.5f - 125f, 68f, 83f)
                c.drawBitmap(img(ctx, R.drawable.gadget_cpu_dial), null, RectF(63f, 34f, 73f, 132f), p); c.restore()
                c.save(); c.rotate(d.ram * 2.5f - 125f, 142f, 51f)
                c.drawBitmap(img(ctx, R.drawable.gadget_cpu_dial_small), null, RectF(137f, 16f, 147f, 86f), p); c.restore()
                c.drawBitmap(img(ctx, R.drawable.gadget_cpu_dots), null, RectF(0f, 0f, 198f, 150f), p)
                text(c, "${d.cpu}%", 68f, 119f, 12f, Color.WHITE, sansBold, shadow = Color.BLACK)
                text(c, "${d.ram}%", 142f, 77f, 11f, Color.WHITE, sansBold, shadow = Color.BLACK)
                c.drawBitmap(img(ctx, R.drawable.gadget_cpu_glass), null, RectF(0f, 0f, 198f, 159f), p)
            }
            Skin.W7_CALENDAR -> {
                // the day page: month on top, the big date, the weekday under it
                c.scale(s.w / 130f, s.h / 141f)
                c.drawBitmap(img(ctx, R.drawable.gadget_cal_page), null, RectF(0f, 0f, 130f, 141f), p)
                if (!withText) return
                val sunday = d.now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                val mo = fmt(d, "MMMM yyyy")
                text(c, mo, 65f, 36f, fitText(mo, 110f, 14f, sansBold), Color.rgb(64, 64, 64), sansBold)
                text(c, "${d.now.get(Calendar.DAY_OF_MONTH)}", 65f, 106f, 70f, if (sunday) Color.rgb(214, 66, 32) else Color.rgb(52, 52, 52), light)
                val wd = fmt(d, "EEEE")
                text(c, wd, 65f, 132f, fitText(wd, 110f, 14f, sansBold), Color.rgb(80, 80, 80), sansBold)
            }
            Skin.W7_BATTERY -> {
                // the gadget's 135 x 75 body: its battery picture with the charge filled in eighths, the percentage over it
                c.scale(s.w / 135f, s.h / 80f)
                c.drawBitmap(img(ctx, R.drawable.gadget_bat_back), null, RectF(0f, 0f, 135f, 80f), p)
                val icons = listOf(R.drawable.gadget_bat_0, R.drawable.gadget_bat_1, R.drawable.gadget_bat_2, R.drawable.gadget_bat_3, R.drawable.gadget_bat_4,
                    R.drawable.gadget_bat_5, R.drawable.gadget_bat_6, R.drawable.gadget_bat_7, R.drawable.gadget_bat_8)
                c.drawBitmap(img(ctx, icons[((d.bat + 6) / 12.5f).toInt().coerceIn(0, 8)]), null, RectF(2f, 3f, 132f, 78f), p)
                text(c, "${d.bat}%", 62f, 49f, 22f, Color.WHITE, sansBold, shadow = Color.BLACK)
                if (d.charging) c.drawBitmap(img(ctx, R.drawable.gadget_bat_charging), null, RectF(104f, 21f, 117f, 43f), p)
            }
            Skin.W7_DRIVE -> {
                // the Drive Info strip: drive icon, its name and free space, the blue meter (orange from 75 %, red from 90 %)
                c.scale(s.w / 156f, s.h / 48f)
                c.drawBitmap(img(ctx, R.drawable.gadget_drive_back), null, RectF(0f, 0f, 156f, 48f), p)
                c.drawBitmap(img(ctx, R.drawable.gadget_drive_icon), null, RectF(4f, 6f, 44f, 40f), p)
                text(c, "Internal (C:)", 48f, 17f, 10f, Color.WHITE, sansBold, Paint.Align.LEFT, Color.BLACK)
                text(c, "${gb(d.diskFree)} free", 48f, 29f, 9f, Color.rgb(220, 232, 248), sans, Paint.Align.LEFT, Color.BLACK)
                val meter = when { d.diskUsed >= 90 -> R.drawable.gadget_drive_meter_red; d.diskUsed >= 75 -> R.drawable.gadget_drive_meter_orange; else -> R.drawable.gadget_drive_meter_blue }
                c.drawRect(48f, 35f, 150f, 41f, paint(Color.argb(110, 0, 0, 0)))
                c.save(); c.clipRect(48f, 35f, 48f + 102f * d.diskUsed / 100f, 41f)
                c.drawBitmap(img(ctx, meter), null, RectF(48f, 35f, 150f, 41f), p); c.restore()
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- Windows 11: dark Mica/Acrylic tiles (Win11Material) with the light accent

    private val W11_TRACK = Color.argb(56, 255, 255, 255)

    private fun win11(ctx: Context, c: Canvas, s: Skin, d: SkinData, withText: Boolean) {
        val w = s.w.toFloat(); val h = s.h.toFloat()
        Win11Material.draw(ctx, c, w, h, w * .16f)
        val acc = Win11Material.accent(ctx)
        val cx = w / 2f
        // the content sits at 90% inside the tile so nothing touches the rounded edge; the live overlays use the same factor
        c.save(); c.scale(.9f, .9f, cx, h / 2f)
        when (s) {
            Skin.W11_CLOCK -> {
                c.drawCircle(cx, h / 2f, w * .4f, paint(Color.argb(24, 255, 255, 255)))
                analog(c, cx, h / 2f, w * .4f, d, acc, acc, numerals = acc, face = medium, minuteW = w * .016f)
            }
            Skin.W11_DATE -> if (withText) {
                val dm = fmt(d, "EEE MMM")
                text(c, dm, cx, h * .2f, fitText(dm, w * .8f, w * .16f, sansBold), acc, sansBold)
                text(c, "${d.now.get(Calendar.DAY_OF_MONTH)}", cx, h * .76f, w * .55f, acc, medium)
                text(c, fmt(d, "yyyy"), cx, h * .93f, w * .11f, acc, sans)
            }
            Skin.W11_CPU -> {
                // a chip in a ring at the top left, the load in big type under it
                val r = RectF(w * .12f, h * .1f, w * .44f, h * .42f)
                gauge(c, r, d.cpu, w * .025f, W11_TRACK, acc, -90f, 360f)
                chip(c, r.centerX(), r.centerY(), w * .085f, acc)
                text(c, "${d.cpu}%", w * .1f, h * .85f, w * .3f, acc, light, Paint.Align.LEFT)
            }
            Skin.W11_MEMORY, Skin.W11_BATTERY, Skin.W11_STORAGE -> {
                val v = when (s) { Skin.W11_MEMORY -> d.ram; Skin.W11_BATTERY -> d.bat; else -> d.diskUsed }
                gauge(c, RectF(w * .14f, h * .14f, w * .86f, h * .86f), v, w * .05f, W11_TRACK, acc)
                text(c, "$v", cx, h * .56f, w * .24f, acc, medium)
                when (s) {
                    Skin.W11_MEMORY -> chip(c, cx, h * .74f, w * .05f, acc)
                    Skin.W11_BATTERY -> bolt(c, cx, h * .74f, w * .055f, acc)
                    else -> text(c, "C:", cx, h * .79f, w * .09f, acc, medium)
                }
            }
            Skin.W11_CALENDAR -> {
                val head = fmt(d, "MMMM yyyy").uppercase(Locale.getDefault())
                text(c, head, w * .08f, h * .17f, fitText(head, w * .84f, w * .09f, sansBold), Color.WHITE, sansBold, Paint.Align.LEFT)
                monthGrid(c, d, RectF(w * .06f, h * .2f, w * .94f, h * .95f), Color.WHITE, Color.WHITE, acc, Color.BLACK, Color.rgb(150, 150, 150), medium, true)
            }
            Skin.W11_TIME -> {
                c.drawCircle(cx, h / 2f, w * .4f, paint(Color.argb(32, 255, 255, 255)))
                sun(c, cx, h * .36f, w * .06f, acc)
                if (withText) text(c, "${hh(d)}:${mm(d)}", cx, h * .64f, w * .16f, acc, sansBold)
            }
            else -> {}
        }
        c.restore()
    }

    /** A processor chip: a square with pins on each side. */
    private fun chip(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = paint(color).apply { style = Paint.Style.STROKE; strokeWidth = r * .18f }
        c.drawRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), r * .25f, r * .25f, p)
        c.drawRect(RectF(cx - r * .45f, cy - r * .45f, cx + r * .45f, cy + r * .45f), p)
        for (i in -1..1) {
            val o = i * r * .5f
            c.drawLine(cx + o, cy - r, cx + o, cy - r * 1.4f, p); c.drawLine(cx + o, cy + r, cx + o, cy + r * 1.4f, p)
            c.drawLine(cx - r, cy + o, cx - r * 1.4f, cy + o, p); c.drawLine(cx + r, cy + o, cx + r * 1.4f, cy + o, p)
        }
    }

    /** A lightning bolt. */
    private fun bolt(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path().apply {
            moveTo(cx + r * .2f, cy - r); lineTo(cx - r * .6f, cy + r * .15f); lineTo(cx - r * .05f, cy + r * .15f)
            lineTo(cx - r * .2f, cy + r); lineTo(cx + r * .6f, cy - r * .15f); lineTo(cx + r * .05f, cy - r * .15f); close()
        }
        c.drawPath(path, paint(color))
    }

    /** A sun rising over a line. */
    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = paint(color).apply { strokeWidth = r * .22f; strokeCap = Paint.Cap.ROUND }
        c.drawArc(RectF(cx - r * .6f, cy - r * .6f, cx + r * .6f, cy + r * .6f), 180f, 180f, true, paint(color))
        c.drawLine(cx - r * 1.4f, cy + r * .2f, cx + r * 1.4f, cy + r * .2f, p)
        for (i in 0..4) {
            val a = Math.toRadians(180.0 + 45.0 * i)
            c.drawLine(cx + (r * .85f * cos(a)).toFloat(), cy + (r * .85f * sin(a)).toFloat(), cx + (r * 1.2f * cos(a)).toFloat(), cy + (r * 1.2f * sin(a)).toFloat(), p)
        }
    }

    // ---------------------------------------------------------------- Windows XP: Luna windows

    /** A Luna window: blue frame, the glossy title bar with its red close button, and the beige body. Returns the body. */
    private fun lunaWindow(c: Canvas, w: Float, h: Float, title: String): RectF {
        val u = w / 160f
        val frame = RectF(0f, 0f, w, h)
        c.drawRoundRect(frame, 8 * u, 8 * u, paint(Color.rgb(0, 61, 220)))
        c.drawRect(0f, 10 * u, w, h, paint(Color.rgb(0, 61, 220)))
        val bar = RectF(0f, 0f, w, 24 * u)
        val g = paint(Color.WHITE).apply {
            shader = LinearGradient(0f, 0f, 0f, bar.bottom, intArrayOf(Color.rgb(9, 151, 255), Color.rgb(0, 83, 238), Color.rgb(0, 80, 238), Color.rgb(6, 111, 252)),
                floatArrayOf(0f, .12f, .6f, 1f), Shader.TileMode.CLAMP)
        }
        c.save(); c.clipRect(bar); c.drawRoundRect(RectF(0f, 0f, w, bar.bottom + 10 * u), 8 * u, 8 * u, g); c.restore()
        text(c, title, 7 * u, 16.5f * u, 9.5f * u, Color.WHITE, sansBold, Paint.Align.LEFT, Color.rgb(10, 24, 131))
        // the red close button
        val x = RectF(w - 21 * u, 3 * u, w - 3 * u, 21 * u)
        c.drawRoundRect(x, 3 * u, 3 * u, paint(Color.WHITE))
        c.drawRoundRect(RectF(x.left + u, x.top + u, x.right - u, x.bottom - u), 2.5f * u, 2.5f * u, paint(Color.WHITE).apply {
            shader = LinearGradient(0f, x.top, 0f, x.bottom, Color.rgb(231, 131, 98), Color.rgb(208, 59, 22), Shader.TileMode.CLAMP)
        })
        val xp = paint(Color.WHITE).apply { strokeWidth = 2 * u; strokeCap = Paint.Cap.ROUND }
        c.drawLine(x.left + 5 * u, x.top + 5 * u, x.right - 5 * u, x.bottom - 5 * u, xp); c.drawLine(x.right - 5 * u, x.top + 5 * u, x.left + 5 * u, x.bottom - 5 * u, xp)
        val body = RectF(3 * u, 24 * u, w - 3 * u, h - 3 * u)
        c.drawRect(body, paint(Color.rgb(236, 233, 216)))
        return body
    }

    /** The Luna progress bar: a rounded white trough with green blocks. */
    private fun lunaBar(c: Canvas, r: RectF, pct: Int) {
        val u = r.height() / 14f
        c.drawRoundRect(r, 3 * u, 3 * u, paint(Color.rgb(104, 104, 104)))
        val inner = RectF(r.left + u, r.top + u, r.right - u, r.bottom - u)
        c.drawRoundRect(inner, 2 * u, 2 * u, paint(Color.WHITE))
        val bw = 7 * u; val gap = 2 * u
        val green = paint(Color.WHITE).apply { shader = LinearGradient(0f, inner.top, 0f, inner.bottom, intArrayOf(Color.rgb(172, 237, 173), Color.rgb(45, 210, 44), Color.rgb(36, 174, 36)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP) }
        var x = inner.left + 2 * u
        val end = inner.left + 2 * u + (inner.width() - 4 * u) * pct.coerceIn(0, 100) / 100f
        while (x + bw <= end) { c.drawRect(x, inner.top + 2 * u, x + bw, inner.bottom - 2 * u, green); x += bw + gap }
    }

    private fun xp(c: Canvas, s: Skin, d: SkinData) {
        val w = s.w.toFloat(); val h = s.h.toFloat(); val u = w / 160f
        when (s) {
            Skin.XP_CLOCK -> {
                val b = lunaWindow(c, w, h, "Clock")
                val r = min(b.width(), b.height() - 22 * u) * .42f
                val cy = if (dialOnly.get()) b.centerY() else b.top + 8 * u + r   // live: the face sits at the window centre, where AnalogClock turns the hands
                c.drawCircle(b.centerX(), cy, r * 1.04f, paint(Color.rgb(127, 157, 185)))
                c.drawCircle(b.centerX(), cy, r, paint(Color.WHITE))
                analog(c, b.centerX(), cy, r, d, Color.rgb(0, 51, 153), Color.BLACK, numerals = Color.rgb(0, 51, 153), face = sansBold, minuteW = r * .05f, hands = !dialOnly.get())
                if (!dialOnly.get()) text(c, "${hh(d)}:${mm(d)}", b.centerX(), b.bottom - 6 * u, 11 * u, Color.BLACK, sans)
            }
            Skin.XP_CPU -> {
                // the Task Manager CPU Usage box: green LED meter on black, the percentage under it
                val b = lunaWindow(c, w, h, "CPU Usage")
                val box = RectF(b.left + 12 * u, b.top + 10 * u, b.right - 12 * u, b.bottom - 10 * u)
                c.drawRect(box, paint(Color.BLACK))
                val rows = 16; val meter = RectF(box.centerX() - 16 * u, box.top + 8 * u, box.centerX() + 16 * u, box.bottom - 22 * u)
                val rh = meter.height() / rows
                val lit = d.cpu * rows / 100
                for (i in 0 until rows) {
                    val on = rows - i <= lit
                    val y = meter.top + i * rh
                    val col = paint(if (on) Color.rgb(0, 255, 0) else Color.rgb(0, 64, 0))
                    c.drawRect(meter.left, y + rh * .2f, meter.centerX() - u, y + rh * .85f, col)
                    c.drawRect(meter.centerX() + u, y + rh * .2f, meter.right, y + rh * .85f, col)
                }
                text(c, "${d.cpu} %", box.centerX(), box.bottom - 7 * u, 11 * u, Color.rgb(0, 255, 0), sans)
            }
            Skin.XP_BATTERY -> {
                val b = lunaWindow(c, w, h, "Power Meter")
                val iy = b.top + 14 * u
                c.drawRoundRect(RectF(b.left + 10 * u, iy, b.left + 40 * u, iy + 16 * u), 2 * u, 2 * u, paint(Color.rgb(64, 64, 64)))
                c.drawRect(b.left + 40 * u, iy + 5 * u, b.left + 43 * u, iy + 11 * u, paint(Color.rgb(64, 64, 64)))
                c.drawRect(b.left + 12 * u, iy + 2 * u, b.left + 12 * u + 26 * u * d.bat / 100f, iy + 14 * u, paint(if (d.bat > 20) Color.rgb(45, 190, 44) else Color.rgb(220, 50, 30)))
                text(c, "${d.bat}% remaining", b.left + 50 * u, iy + 8 * u, 10 * u, Color.BLACK, sans, Paint.Align.LEFT)
                text(c, if (d.charging) "Charging" else "On batteries", b.left + 50 * u, iy + 19 * u, 8.5f * u, Color.rgb(80, 80, 80), sans, Paint.Align.LEFT)
                lunaBar(c, RectF(b.left + 10 * u, b.bottom - 26 * u, b.right - 10 * u, b.bottom - 12 * u), d.bat)
            }
            Skin.XP_DISK -> {
                // the disk Properties pie: blue used space, magenta free space, with its darker rim
                val b = lunaWindow(c, w, h, "Local Disk (C:)")
                val pie = RectF(b.left + 10 * u, b.top + 14 * u, b.left + 72 * u, b.top + 44 * u)
                val usedSweep = 360f * d.diskUsed / 100f
                for (k in 8 downTo 1) {
                    val r = RectF(pie.left, pie.top + k * u, pie.right, pie.bottom + k * u)
                    c.drawArc(r, -90f, usedSweep, true, paint(Color.rgb(0, 0, 160)))
                    c.drawArc(r, -90f + usedSweep, 360f - usedSweep, true, paint(Color.rgb(160, 0, 160)))
                }
                c.drawArc(pie, -90f, usedSweep, true, paint(Color.rgb(0, 0, 255)))
                c.drawArc(pie, -90f + usedSweep, 360f - usedSweep, true, paint(Color.rgb(255, 0, 255)))
                val lx = b.left + 82 * u
                c.drawRect(lx, b.top + 16 * u, lx + 8 * u, b.top + 24 * u, paint(Color.rgb(0, 0, 255)))
                text(c, "Used space  ${d.diskUsed}%", lx + 12 * u, b.top + 23.5f * u, 8.5f * u, Color.BLACK, sans, Paint.Align.LEFT)
                c.drawRect(lx, b.top + 30 * u, lx + 8 * u, b.top + 38 * u, paint(Color.rgb(255, 0, 255)))
                text(c, "Free  ${gb(d.diskFree)}", lx + 12 * u, b.top + 37.5f * u, 8.5f * u, Color.BLACK, sans, Paint.Align.LEFT)
                text(c, "Drive C:", b.centerX(), b.bottom - 8 * u, 9 * u, Color.BLACK, sans)
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- Windows 95: grey windows and group boxes, drawn on a pixel grid

    private val W95_FACE = Color.rgb(192, 192, 192)
    private val W95_DARK = Color.rgb(128, 128, 128)
    private val W95_NAVY = Color.rgb(0, 0, 128)
    private val W95_TEAL = Color.rgb(0, 128, 128)

    private fun bevel(c: Canvas, r: RectF, raised: Boolean, fill: Int) {
        val pf = paint(fill, false)
        c.drawRect(r, pf)
        val (o1, o2) = if (raised) Color.WHITE to Color.BLACK else W95_DARK to Color.WHITE
        val (i1, i2) = if (raised) Color.rgb(223, 223, 223) to W95_DARK else Color.BLACK to Color.rgb(223, 223, 223)
        val p = paint(o1, false)
        c.drawRect(r.left, r.top, r.right, r.top + 1, p); c.drawRect(r.left, r.top, r.left + 1, r.bottom, p)
        p.color = o2; c.drawRect(r.left, r.bottom - 1, r.right, r.bottom, p); c.drawRect(r.right - 1, r.top, r.right, r.bottom, p)
        p.color = i1; c.drawRect(r.left + 1, r.top + 1, r.right - 1, r.top + 2, p); c.drawRect(r.left + 1, r.top + 1, r.left + 2, r.bottom - 1, p)
        p.color = i2; c.drawRect(r.left + 1, r.bottom - 2, r.right - 1, r.bottom - 1, p); c.drawRect(r.right - 2, r.top + 1, r.right - 1, r.bottom - 1, p)
    }

    /** The etched group box with its label sitting on the top line; the first letter underlined like a keyboard shortcut. */
    private fun groupBox(c: Canvas, r: RectF, label: String) {
        val p = paint(W95_DARK, false).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
        c.drawRect(r.left, r.top, r.right - 1, r.bottom - 1, p)
        p.color = Color.WHITE; c.drawRect(r.left + 1, r.top + 1, r.right, r.bottom, p)
        val tp = Paint().apply { textSize = 9f; typeface = sans }
        val tw = tp.measureText(label)
        c.drawRect(r.left + 6, r.top - 5, r.left + 10 + tw, r.top + 5, paint(W95_FACE, false))
        text(c, label, r.left + 8, r.top + 3.5f, 9f, Color.BLACK, sans, Paint.Align.LEFT)
        c.drawRect(r.left + 8, r.top + 5f, r.left + 8 + tp.measureText(label.take(1)), r.top + 5.6f, paint(Color.BLACK, false))
    }

    private fun win95(c: Canvas, s: Skin, d: SkinData) {
        // a 1 px Windows 95 grid scaled up, so the bevels stay crisp
        val lw = 140f
        val k = s.w / lw
        val lh = s.h / k
        c.scale(k, k)
        bevel(c, RectF(0f, 0f, lw, lh), true, W95_FACE)
        when (s) {
            Skin.W95_TIME -> {
                groupBox(c, RectF(6f, 9f, lw - 6, lh - 6), "Time")
                val cx = lw / 2f; val cy = if (dialOnly.get()) lh / 2f else 9f + (lh - 15f) / 2f + 2f; val r = (lh - 26f) / 2f
                // twelve teal dots with a black shadow, as on the Windows 95 clock
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    val x = cx + (r * sin(a)).toFloat(); val y = cy - (r * cos(a)).toFloat()
                    val z = if (i % 3 == 0) 2.2f else 1.6f
                    c.drawRect(x - z + .8f, y - z + .8f, x + z + .8f, y + z + .8f, paint(Color.BLACK, false))
                    c.drawRect(x - z, y - z, x + z, y + z, paint(W95_TEAL, false))
                }
                val (ha, ma) = angles(d)
                fun hand(deg: Float, len: Float, width: Float) {
                    val a = Math.toRadians(deg.toDouble())
                    val tx = cx + (len * sin(a)).toFloat(); val ty = cy - (len * cos(a)).toFloat()
                    val nx = (width * cos(a)).toFloat(); val ny = (width * sin(a)).toFloat()
                    val path = Path().apply { moveTo(cx + nx, cy + ny); lineTo(tx, ty); lineTo(cx - nx, cy - ny); lineTo(cx - (len * .12f * sin(a)).toFloat(), cy + (len * .12f * cos(a)).toFloat()); close() }
                    c.drawPath(path, paint(W95_TEAL)); c.drawPath(path, paint(Color.BLACK).apply { style = Paint.Style.STROKE; strokeWidth = .7f })
                }
                if (!dialOnly.get()) { hand(ha, r * .55f, 3f); hand(ma, r * .85f, 2.4f) }
            }
            Skin.W95_CPU, Skin.W95_BATTERY, Skin.W95_MEMORY -> {
                val (label, v) = when (s) { Skin.W95_CPU -> "CPU" to d.cpu; Skin.W95_BATTERY -> "Battery" to d.bat; else -> "Memory" to d.ram }
                groupBox(c, RectF(6f, 9f, lw - 6, lh - 6), label)
                // a sunken field with the number, the spin buttons beside it, and the blocky navy progress bar under it
                val f = RectF(14f, 20f, lw - 34f, 36f)
                bevel(c, f, false, Color.WHITE)
                text(c, String.format(Locale.US, "%03d %%", v), f.right - 4, f.bottom - 4.5f, 10f, Color.BLACK, mono, Paint.Align.RIGHT, aa = false)
                val sp = RectF(lw - 32f, 20f, lw - 18f, 36f)
                bevel(c, RectF(sp.left, sp.top, sp.right, sp.centerY()), true, W95_FACE)
                bevel(c, RectF(sp.left, sp.centerY(), sp.right, sp.bottom), true, W95_FACE)
                tri(c, sp.centerX(), sp.top + 4f, true); tri(c, sp.centerX(), sp.centerY() + 4.5f, false)
                val bar = RectF(14f, lh - 22f, lw - 18f, lh - 12f)
                bevel(c, bar, false, W95_FACE)
                var x = bar.left + 2
                val end = bar.left + 2 + (bar.width() - 4) * v.coerceIn(0, 100) / 100f
                while (x + 6 <= end) { c.drawRect(x, bar.top + 2, x + 6, bar.bottom - 2, paint(W95_NAVY, false)); x += 8 }
            }
            Skin.W95_DATE -> {
                // the Date/Time Properties calendar: month and year boxes on top, the white grid with today in navy
                groupBox(c, RectF(6f, 9f, lw - 6, lh - 6), "Date")
                val m = RectF(12f, 17f, 74f, 30f); bevel(c, m, false, Color.WHITE)
                text(c, fmt(d, "MMMM"), m.left + 3, m.bottom - 3.5f, 8.5f, Color.BLACK, sans, Paint.Align.LEFT, aa = false)
                val y = RectF(78f, 17f, lw - 12, 30f); bevel(c, y, false, Color.WHITE)
                text(c, fmt(d, "yyyy"), y.left + 3, y.bottom - 3.5f, 8.5f, Color.BLACK, sans, Paint.Align.LEFT, aa = false)
                val g = RectF(12f, 34f, lw - 12, lh - 12); bevel(c, g, false, Color.WHITE)
                monthGrid(c, d, RectF(g.left + 3, g.top + 2, g.right - 3, g.bottom - 2), Color.BLACK, Color.BLACK, W95_NAVY, Color.WHITE, W95_DARK, sans, false)
            }
            else -> {}
        }
    }

    private fun tri(c: Canvas, cx: Float, top: Float, up: Boolean) {
        val path = Path().apply {
            if (up) { moveTo(cx - 3, top + 3); lineTo(cx + 3, top + 3); lineTo(cx, top) } else { moveTo(cx - 3, top - 3); lineTo(cx + 3, top - 3); lineTo(cx, top) }
            close()
        }
        c.drawPath(path, paint(Color.BLACK, false))
    }

    // ---------------------------------------------------------------- Material You: tonal cards in the wallpaper's colours

    private class Tones(val card: Int, val fill: Int, val accent: Int, val onAccent: Int, val text: Int, val sub: Int)

    /** The wallpaper's tonal palette when [accent] is the wallpaper accent, else tones mixed from the accent itself. */
    private fun tones(ctx: Context, accent: Int): Tones {
        if (android.os.Build.VERSION.SDK_INT >= 31 && accent == ctx.getColor(android.R.color.system_accent1_200)) return Tones(ctx.getColor(android.R.color.system_neutral2_900), ctx.getColor(android.R.color.system_accent2_800),
            accent, ctx.getColor(android.R.color.system_accent1_800), ctx.getColor(android.R.color.system_accent1_100), ctx.getColor(android.R.color.system_neutral2_300))
        val a = readable(accent)
        fun mix(to: Int, f: Float) = ColorUtils.blendARGB(a, to, f)
        return Tones(mix(Color.rgb(20, 20, 22), .86f), mix(Color.BLACK, .6f), a, mix(Color.BLACK, .72f), mix(Color.WHITE, .78f), mix(Color.rgb(170, 170, 170), .75f))
    }

    /** Dark accents lifted toward white so they read on the dark cards. */
    fun readable(c: Int): Int = if (ColorUtils.calculateLuminance(c) < .25) ColorUtils.blendARGB(c, Color.WHITE, .45f) else c

    /** The colours of a live layout's TextClocks (ids skin_t1..t3, top to bottom); empty keeps the layout's own. */
    fun clockColors(ctx: Context, s: Skin, accent: Int): IntArray {
        val a = readable(accent)
        return when (s) {
            Skin.M3_CLOCK, Skin.M3_CLOCK_DATE -> tones(ctx, accent).let { intArrayOf(it.accent, it.text) }
            Skin.M3_DATE -> tones(ctx, accent).let { intArrayOf(it.sub, it.accent, it.text) }
            Skin.GL_TIME, Skin.IOS_CLOCK_DATE -> intArrayOf(Color.WHITE, a)
            Skin.GL_DATE -> intArrayOf(Color.argb(220, 255, 255, 255), a, Color.WHITE)
            else -> IntArray(0)
        }
    }

    /** An arrow for the network rates: down or up. */
    private fun arrow(c: Canvas, cx: Float, cy: Float, r: Float, down: Boolean, color: Int) {
        val p = paint(color).apply { style = Paint.Style.STROKE; strokeWidth = r * .32f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val k = if (down) 1f else -1f
        c.drawLine(cx, cy - r * k, cx, cy + r * k, p)
        c.drawPath(Path().apply { moveTo(cx - r * .7f, cy + r * .3f * k); lineTo(cx, cy + r * k); lineTo(cx + r * .7f, cy + r * .3f * k) }, p)
    }

    /** A bar with rounded ends filled to [pct]. */
    private fun bar(c: Canvas, r: RectF, pct: Int, track: Int, color: Int) {
        val k = r.height() / 2f
        c.drawRoundRect(r, k, k, paint(track))
        if (pct > 0) c.drawRoundRect(RectF(r.left, r.top, r.left + (r.width() * pct.coerceIn(0, 100) / 100f).coerceAtLeast(r.height()), r.bottom), k, k, paint(color))
    }

    private fun material(ctx: Context, c: Canvas, s: Skin, d: SkinData, withText: Boolean, accent: Int) {
        val t = tones(ctx, accent)
        val w = s.w.toFloat(); val h = s.h.toFloat()
        val corner = min(w, h) * .24f
        val card = RectF(0f, 0f, w, h)
        when (s) {
            Skin.M3_BATTERY -> {
                // as in Material You widgets: the icon in a circle, the device name, and the level filling the card from the bottom
                c.drawRoundRect(card, corner, corner, paint(t.card))
                c.save(); c.clipPath(Path().apply { addRoundRect(card, corner, corner, Path.Direction.CW) })
                c.drawRect(0f, h * (1f - d.bat / 100f), w, h, paint(t.fill)); c.restore()
                c.drawCircle(w * .22f, h * .22f, w * .12f, paint(t.accent))
                c.drawRoundRect(RectF(w * .19f, h * .15f, w * .25f, h * .29f), w * .012f, w * .012f, paint(t.onAccent))
                val name = Build.MODEL.take(14)
                text(c, name, w * .1f, h * .53f, fitText(name, w * .8f, w * .1f, medium), t.text, medium, Paint.Align.LEFT)
                text(c, "${d.bat}%", w * .1f, h * .87f, w * .22f, t.accent, Typeface.create("sans-serif", Typeface.NORMAL), Paint.Align.LEFT)
                if (d.charging) bolt(c, w * .84f, h * .2f, w * .07f, t.accent)
            }
            Skin.M3_CLOCK -> {
                // the hour over the minute in big rounded digits, as on Pixel
                c.drawRoundRect(card, w / 2f, w / 2f, paint(t.card))
                if (!withText) return
                text(c, hh(d), w / 2f, h * .47f, w * .36f, t.accent, medium)
                text(c, mm(d), w / 2f, h * .84f, w * .36f, t.text, medium)
            }
            Skin.M3_DATE -> {
                c.drawCircle(w / 2f, h / 2f, w / 2f, paint(t.card))
                if (!withText) return
                text(c, fmt(d, "EEE").uppercase(Locale.getDefault()), w / 2f, h * .27f, w * .13f, t.sub, medium)
                text(c, "${d.now.get(Calendar.DAY_OF_MONTH)}", w / 2f, h * .7f, w * .42f, t.accent, medium)
                text(c, fmt(d, "MMM"), w / 2f, h * .86f, w * .1f, t.text, medium)
            }
            Skin.M3_CPU, Skin.M3_MEMORY -> {
                val v = if (s == Skin.M3_CPU) d.cpu else d.ram
                c.drawRoundRect(card, corner, corner, paint(t.card))
                gauge(c, RectF(w * .16f, h * .16f, w * .84f, h * .84f), v, w * .07f, t.fill, t.accent, -90f, 360f)
                text(c, "$v%", w / 2f, h * .55f, w * .17f, t.text, medium)
                text(c, if (s == Skin.M3_CPU) "CPU" else "RAM", w / 2f, h * .68f, w * .07f, t.sub, medium)
            }
            Skin.M3_STORAGE, Skin.M3_RAM_BAR -> {
                val ram = s == Skin.M3_RAM_BAR
                c.drawRoundRect(card, h * .3f, h * .3f, paint(t.card))
                text(c, if (ram) "Memory" else "Storage", w * .08f, h * .3f, h * .13f, t.sub, medium, Paint.Align.LEFT)
                text(c, if (ram) "${mb(d.ramUsedMb)} / ${mb(d.ramTotalMb)}" else "${gb(d.diskFree)} free", w * .08f, h * .52f, h * .17f, t.text, medium, Paint.Align.LEFT)
                bar(c, RectF(w * .08f, h * .68f, w * .92f, h * .82f), if (ram) d.ram else d.diskUsed, t.fill, t.accent)
            }
            Skin.M3_DATE_PILL -> {
                // the day in an accent disc, weekday and month beside it
                c.drawRoundRect(card, h / 2f, h / 2f, paint(t.card))
                c.drawCircle(h / 2f, h / 2f, h * .38f, paint(t.accent))
                text(c, "${d.now.get(Calendar.DAY_OF_MONTH)}", h / 2f, h * .64f, h * .4f, t.onAccent, medium)
                val wd = fmt(d, "EEEE")
                text(c, wd, h * 1.04f, h * .48f, fitText(wd, w - h * 1.14f, h * .2f, medium), t.text, medium, Paint.Align.LEFT)
                text(c, fmt(d, "MMMM yyyy"), h * 1.04f, h * .7f, h * .12f, t.sub, medium, Paint.Align.LEFT)
            }
            Skin.M3_CLOCK_DATE -> {
                c.drawRoundRect(card, h * .3f, h * .3f, paint(t.card))
                if (!withText) return
                text(c, "${hh(d)}:${mm(d)}", w / 2f, h * .62f, h * .46f, t.accent, medium)
                text(c, fmt(d, "EEEE, d MMMM"), w / 2f, h * .84f, h * .1f, t.text, medium)
            }
            Skin.M3_BATTERY_RING -> {
                c.drawCircle(w / 2f, h / 2f, w / 2f, paint(t.card))
                gauge(c, RectF(w * .15f, h * .15f, w * .85f, h * .85f), d.bat, w * .09f, t.fill, t.accent, -90f, 360f)
                text(c, "${d.bat}%", w / 2f, h * .58f, w * .19f, t.text, medium)
                if (d.charging) bolt(c, w / 2f, h * .32f, w * .055f, t.accent)
            }
            Skin.M3_TEMP -> {
                // a thermometer filled over 20..80 °C
                c.drawRoundRect(card, corner, corner, paint(t.card))
                val pct = ((d.temp - 20f) / 60f * 100f).toInt().coerceIn(0, 100)
                val tube = RectF(w * .16f, h * .14f, w * .3f, h * .72f)
                c.drawRoundRect(tube, tube.width() / 2f, tube.width() / 2f, paint(t.fill))
                c.drawRoundRect(RectF(tube.left + w * .025f, tube.bottom - tube.height() * pct / 100f, tube.right - w * .025f, tube.bottom + w * .04f), w * .05f, w * .05f, paint(t.accent))
                c.drawCircle(tube.centerX(), h * .76f, w * .1f, paint(t.accent))
                text(c, if (d.temp > 0f) "${d.temp.toInt()}°" else "–", w * .86f, h * .52f, w * .26f, t.text, medium, Paint.Align.RIGHT)
                text(c, "CPU", w * .86f, h * .68f, w * .08f, t.sub, medium, Paint.Align.RIGHT)
            }
            Skin.M3_NETWORK -> {
                c.drawRoundRect(card, h * .3f, h * .3f, paint(t.card))
                listOf(d.down, d.up).forEachIndexed { i, v ->
                    val cx = w * (.27f + .46f * i); val down = i == 0
                    c.drawCircle(cx, h * .33f, h * .17f, paint(if (down) t.accent else t.fill))
                    arrow(c, cx, h * .33f, h * .085f, down, if (down) t.onAccent else t.accent)
                    val r = rate(v.toFloat())
                    text(c, r, cx, h * .7f, fitText(r, w * .4f, h * .15f, medium), t.text, medium)
                    text(c, if (down) "Down" else "Up", cx, h * .86f, h * .09f, t.sub, medium)
                }
            }
            Skin.M3_UPTIME -> {
                c.drawRoundRect(card, corner, corner, paint(t.card))
                val mins = SystemClock.elapsedRealtime() / 60_000
                val days = mins / 1440; val hours = mins / 60 % 24
                text(c, "Uptime", w * .12f, h * .24f, w * .08f, t.sub, medium, Paint.Align.LEFT)
                text(c, if (days > 0) "${days}d" else "${hours}h", w * .12f, h * .62f, w * .3f, t.accent, medium, Paint.Align.LEFT)
                text(c, if (days > 0) "${hours}h ${mins % 60}m" else "${mins % 60}m", w * .12f, h * .84f, w * .12f, t.text, medium, Paint.Align.LEFT)
            }
            Skin.M3_OVERVIEW -> {
                // three small rings: CPU, RAM, battery
                c.drawRoundRect(card, h * .3f, h * .3f, paint(t.card))
                listOf("CPU" to d.cpu, "RAM" to d.ram, "BAT" to d.bat).forEachIndexed { i, (n, v) ->
                    val cx = w * (.2f + .3f * i); val cy = h * .42f; val r = h * .25f
                    gauge(c, RectF(cx - r, cy - r, cx + r, cy + r), v, h * .07f, t.fill, t.accent, -90f, 360f)
                    text(c, "$v", cx, cy + h * .06f, h * .16f, t.text, medium)
                    text(c, n, cx, h * .88f, h * .09f, t.sub, medium)
                }
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- AMOLED: black with white lines only

    private fun amoled(c: Canvas, s: Skin, d: SkinData, withText: Boolean) {
        val w = s.w.toFloat(); val h = s.h.toFloat()
        val r = min(w, h) * .14f
        c.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint(Color.BLACK))
        c.drawRoundRect(RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f), r, r, paint(Color.rgb(40, 40, 40)).apply { style = Paint.Style.STROKE; strokeWidth = 3f })
        when (s) {
            Skin.AM_CLOCK -> if (withText) {
                text(c, "${hh(d)}:${mm(d)}", w / 2f, h * .62f, h * .46f, Color.WHITE, thin)
                text(c, fmt(d, "EEEE, d MMMM"), w / 2f, h * .84f, h * .1f, Color.rgb(158, 158, 158), light)
            }
            Skin.AM_BATTERY -> {
                val b = RectF(w * .2f, h * .3f, w * .76f, h * .58f)
                val p = paint(Color.WHITE).apply { style = Paint.Style.STROKE; strokeWidth = w * .012f }
                c.drawRoundRect(b, w * .04f, w * .04f, p)
                c.drawRoundRect(RectF(b.right + w * .015f, b.top + b.height() * .3f, b.right + w * .04f, b.bottom - b.height() * .3f), w * .01f, w * .01f, paint(Color.WHITE))
                val inner = RectF(b.left + w * .03f, b.top + w * .03f, b.right - w * .03f, b.bottom - w * .03f)
                c.drawRoundRect(RectF(inner.left, inner.top, inner.left + inner.width() * d.bat / 100f, inner.bottom), w * .015f, w * .015f, paint(Color.WHITE))
                text(c, "${d.bat}%", w / 2f, h * .82f, w * .15f, Color.WHITE, light)
            }
            Skin.AM_CPU -> {
                gauge(c, RectF(w * .16f, h * .16f, w * .84f, h * .84f), d.cpu, w * .02f, Color.rgb(48, 48, 48), Color.WHITE, -90f, 360f)
                text(c, "${d.cpu}", w / 2f, h * .56f, w * .2f, Color.WHITE, thin)
                text(c, "CPU %", w / 2f, h * .68f, w * .06f, Color.rgb(158, 158, 158), light)
            }
            Skin.AM_DATE -> if (withText) {
                text(c, fmt(d, "EEEE").uppercase(Locale.getDefault()), w / 2f, h * .17f, w * .1f, Color.rgb(190, 190, 190), light)
                text(c, "${d.now.get(Calendar.DAY_OF_MONTH)}", w / 2f, h * .76f, w * .6f, Color.WHITE, thin)
                text(c, fmt(d, "MMMM"), w / 2f, h * .93f, w * .1f, Color.WHITE, light)
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- Glass: a frosted panel with a bright edge

    private fun glass(c: Canvas, s: Skin, d: SkinData, withText: Boolean, accent: Int) {
        val w = s.w.toFloat(); val h = s.h.toFloat()
        val r = min(w, h) * .14f
        val a = readable(accent)
        val dim = Color.argb(70, 255, 255, 255); val soft = Color.argb(220, 255, 255, 255); val shade = Color.argb(90, 0, 0, 0)
        val card = RectF(2f, 2f, w - 2f, h - 2f)
        c.drawRoundRect(card, r, r, paint(Color.WHITE).apply {
            shader = LinearGradient(0f, 0f, w, h, Color.argb(80, 255, 255, 255), Color.argb(30, 255, 255, 255), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(card, r, r, paint(Color.argb(140, 255, 255, 255)).apply { style = Paint.Style.STROKE; strokeWidth = 3f })
        when (s) {
            Skin.GL_BATTERY -> {
                text(c, "${d.bat}%", w / 2f, h * .3f, w * .17f, Color.WHITE, sansBold, shadow = Color.argb(90, 0, 0, 0))
                // ten bars, lit up to the charge
                val n = 10; val bw = w * .05f; val gap = w * .022f
                val total = n * bw + (n - 1) * gap; var x = (w - total) / 2f
                for (i in 0 until n) {
                    val on = i < (d.bat + 5) / 10
                    c.drawRoundRect(RectF(x, h * .4f, x + bw, h * .68f), bw / 2f, bw / 2f, paint(if (on) a else dim))
                    x += bw + gap
                }
                text(c, if (d.charging) "Charging" else "Battery Stable", w / 2f, h * .85f, w * .07f, Color.argb(220, 255, 255, 255), medium)
            }
            Skin.GL_CLOCK -> {
                // big digital time with thin analog hands over it
                if (dialOnly.get()) return
                text(c, hh(d), w / 2f, h * .46f, w * .34f, Color.argb(170, 255, 255, 255), thin)
                text(c, mm(d), w / 2f, h * .86f, w * .34f, Color.argb(170, 255, 255, 255), thin)
                val (ha, ma) = angles(d)
                val p = paint(Color.WHITE).apply { strokeCap = Paint.Cap.ROUND }
                fun hand(deg: Float, len: Float, width: Float) {
                    val a = Math.toRadians(deg.toDouble()); p.strokeWidth = width
                    c.drawLine(w / 2f, h / 2f, w / 2f + (len * sin(a)).toFloat(), h / 2f - (len * cos(a)).toFloat(), p)
                }
                p.color = a; hand(ha, w * .22f, w * .025f)
                p.color = Color.WHITE; hand(ma, w * .36f, w * .016f)
                c.drawCircle(w / 2f, h / 2f, w * .025f, paint(Color.WHITE))
            }
            Skin.GL_TIME -> if (withText) {
                text(c, "${hh(d)}:${mm(d)}", w / 2f, h * .62f, h * .46f, Color.WHITE, thin)
                text(c, fmt(d, "EEEE, d MMMM"), w / 2f, h * .84f, h * .1f, a, light)
            }
            Skin.GL_DATE -> if (withText) {
                text(c, fmt(d, "EEE").uppercase(Locale.getDefault()), w / 2f, h * .27f, w * .13f, soft, medium)
                text(c, "${d.now.get(Calendar.DAY_OF_MONTH)}", w / 2f, h * .7f, w * .42f, a, medium)
                text(c, fmt(d, "MMM"), w / 2f, h * .86f, w * .1f, Color.WHITE, medium)
            }
            Skin.GL_BATTERY_RING, Skin.GL_CPU, Skin.GL_MEMORY -> {
                val v = when (s) { Skin.GL_CPU -> d.cpu; Skin.GL_MEMORY -> d.ram; else -> d.bat }
                val box = RectF(w * .17f, h * .17f, w * .83f, h * .83f)
                // the battery is a full ring, the meters an open dial
                if (s == Skin.GL_BATTERY_RING) gauge(c, box, v, w * .075f, dim, a, -90f, 360f) else gauge(c, box, v, w * .075f, dim, a)
                text(c, "$v%", w / 2f, h * .56f, w * .17f, Color.WHITE, sansBold, shadow = shade)
                val n = when (s) { Skin.GL_CPU -> "CPU"; Skin.GL_MEMORY -> "Memory"; else -> if (d.charging) "Charging" else "Battery" }
                text(c, n, w / 2f, h * .69f, w * .07f, soft, medium)
            }
            Skin.GL_STORAGE -> {
                text(c, "Storage", w * .08f, h * .28f, h * .12f, soft, medium, Paint.Align.LEFT)
                text(c, "${d.diskUsed}%", w * .92f, h * .28f, h * .12f, a, sansBold, Paint.Align.RIGHT)
                text(c, "${gb(d.diskFree)} free", w * .08f, h * .56f, h * .2f, Color.WHITE, sansBold, Paint.Align.LEFT, shade)
                bar(c, RectF(w * .08f, h * .68f, w * .92f, h * .8f), d.diskUsed, dim, a)
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- iOS: dark rounded tiles, bold type, the accent for the values

    private fun ios(c: Canvas, s: Skin, d: SkinData, withText: Boolean, accent: Int) {
        val w = s.w.toFloat(); val h = s.h.toFloat()
        val a = readable(accent)
        val grey = Color.rgb(142, 142, 147); val track = Color.rgb(58, 58, 62)
        val r = min(w, h) * .22f
        c.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint(Color.rgb(28, 28, 30)))
        when (s) {
            Skin.IOS_CLOCK_DATE -> if (withText) {
                text(c, "${hh(d)}:${mm(d)}", w / 2f, h * .62f, h * .46f, Color.WHITE, medium)
                text(c, fmt(d, "EEEE, d MMMM"), w / 2f, h * .84f, h * .1f, a, medium)
            }
            Skin.IOS_RINGS -> {
                // rings inside each other, like the activity rings: battery, CPU, memory
                listOf(d.bat, d.cpu, d.ram).forEachIndexed { i, v ->
                    val inset = w * (.1f + .1f * i)
                    val col = ColorUtils.blendARGB(a, Color.WHITE, .3f * i)
                    gauge(c, RectF(inset, inset, w - inset, h - inset), v, w * .08f, ColorUtils.setAlphaComponent(col, 60), col, -90f, 360f)
                }
                text(c, "${d.bat}%", w / 2f, h * .54f, w * .1f, Color.WHITE, sansBold)
            }
            Skin.IOS_STORAGE -> {
                text(c, "Storage", w * .1f, h * .2f, w * .075f, grey, sansBold, Paint.Align.LEFT)
                text(c, gb(d.diskFree), w * .1f, h * .43f, w * .15f, Color.WHITE, sansBold, Paint.Align.LEFT)
                text(c, "available", w * .1f, h * .54f, w * .07f, grey, sans, Paint.Align.LEFT)
                bar(c, RectF(w * .1f, h * .66f, w * .9f, h * .78f), d.diskUsed, track, a)
                text(c, "${d.diskUsed}% used", w * .1f, h * .9f, w * .065f, grey, sans, Paint.Align.LEFT)
            }
            Skin.IOS_MEMORY -> {
                text(c, "Memory", w * .1f, h * .2f, w * .075f, grey, sansBold, Paint.Align.LEFT)
                gauge(c, RectF(w * .22f, h * .3f, w * .78f, h * .86f), d.ram, w * .07f, track, a)
                text(c, "${d.ram}%", w / 2f, h * .62f, w * .13f, Color.WHITE, sansBold)
                text(c, mb(d.ramUsedMb), w / 2f, h * .73f, w * .06f, grey, sans)
            }
            Skin.IOS_UP_NEXT -> {
                // today, then the next three days
                text(c, fmt(d, "EEEE").uppercase(Locale.getDefault()), w * .08f, h * .15f, w * .085f, a, sansBold, Paint.Align.LEFT)
                text(c, "${d.now.get(Calendar.DAY_OF_MONTH)}", w * .08f, h * .45f, w * .3f, Color.WHITE, sans, Paint.Align.LEFT)
                val cal = d.now.clone() as Calendar
                val day = SimpleDateFormat("EEEE", Locale.getDefault())
                for (i in 1..3) {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                    val y = h * (.5f + .14f * i)
                    c.drawRoundRect(RectF(w * .1f, y - w * .06f, w * .115f, y + w * .012f), w * .008f, w * .008f, paint(a))
                    text(c, if (i == 1) "Tomorrow" else day.format(cal.time), w * .15f, y, w * .065f, Color.WHITE, sansBold, Paint.Align.LEFT)
                    text(c, "${cal.get(Calendar.DAY_OF_MONTH)}", w * .9f, y, w * .065f, grey, sans, Paint.Align.RIGHT)
                }
            }
            Skin.IOS_WORLD -> {
                // here and three other cities; white faces by day, dark ones by night, as on iOS
                val here = TimeZone.getDefault()
                val zones = listOf(here to here.id.substringAfterLast('/').replace('_', ' '), TimeZone.getTimeZone("Europe/London") to "London",
                    TimeZone.getTimeZone("America/New_York") to "New York", TimeZone.getTimeZone("Asia/Tokyo") to "Tokyo")
                val ms = d.now.timeInMillis
                zones.forEachIndexed { i, (z, name) ->
                    val cx = w * (.14f + .24f * i); val cy = h * .4f; val cr = w * .095f
                    val local = d.at(z)
                    val dayTime = local.now.get(Calendar.HOUR_OF_DAY) in 6..17
                    c.drawCircle(cx, cy, cr, paint(if (dayTime) Color.WHITE else Color.rgb(60, 60, 64)))
                    analog(c, cx, cy, cr, local, if (dayTime) grey else Color.rgb(120, 120, 126), if (dayTime) Color.BLACK else Color.WHITE, minuteW = cr * .07f)
                    c.drawCircle(cx, cy, cr * .06f, paint(a))
                    text(c, name, cx, h * .78f, fitText(name, w * .2f, h * .085f, sansBold), Color.WHITE, sansBold)
                    val diff = (z.getOffset(ms) - here.getOffset(ms)) / 3_600_000
                    text(c, if (i == 0) "Local" else String.format(Locale.getDefault(), "%+dh", diff), cx, h * .91f, h * .07f, grey, sans)
                }
            }
            else -> {}
        }
    }
}

/** Every themed widget provider, for refreshing. */
object SkinWidgets {
    val all: List<SkinWidget> by lazy {
        listOf(
            Win7Clock1Widget(), Win7Clock2Widget(), Win7Clock3Widget(), Win7Clock4Widget(), Win7Clock5Widget(), Win7Clock6Widget(), Win7Clock7Widget(), Win7Clock8Widget(), Win7Clock9Widget(),
            W7CpuWidget(), W7CalendarWidget(), W7BatteryWidget(), W7DriveWidget(),
            W11ClockWidget(), W11DateWidget(), W11CpuWidget(), W11MemoryWidget(), W11BatteryWidget(), W11StorageWidget(), W11CalendarWidget(), W11TimeWidget(),
            XpClockWidget(), XpCpuWidget(), XpBatteryWidget(), XpDiskWidget(),
            W95TimeWidget(), W95CpuWidget(), W95BatteryWidget(), W95MemoryWidget(), W95DateWidget(),
            M3BatteryWidget(), M3ClockWidget(), M3DateWidget(), M3CpuWidget(), M3StorageWidget(), M3MemoryWidget(),
            AmClockWidget(), AmBatteryWidget(), AmCpuWidget(), AmDateWidget(),
            GlBatteryWidget(), GlClockWidget(),
            M3DatePillWidget(), M3ClockDateWidget(), M3BatteryRingWidget(), M3TempWidget(), M3RamBarWidget(), M3NetworkWidget(), M3UptimeWidget(), M3OverviewWidget(),
            GlTimeWidget(), GlDateWidget(), GlBatteryRingWidget(), GlCpuWidget(), GlMemoryWidget(), GlStorageWidget(),
            IosClockDateWidget(), IosRingsWidget(), IosStorageWidget(), IosMemoryWidget(), IosUpNextWidget(), IosWorldWidget(),
        )
    }
}

// one provider class per widget, so the launcher lists each on its own; the Windows 7 clocks keep their old class names so placed ones carry on
class Win7Clock1Widget : SkinWidget(Skin.W7_CLOCK_TRAD)
class Win7Clock2Widget : SkinWidget(Skin.W7_CLOCK_SYSTEM)
class Win7Clock3Widget : SkinWidget(Skin.W7_CLOCK_CRONO)
class Win7Clock4Widget : SkinWidget(Skin.W7_CLOCK_DINER)
class Win7Clock5Widget : SkinWidget(Skin.W7_CLOCK_FLOWER)
class Win7Clock6Widget : SkinWidget(Skin.W7_CLOCK_MODERN)
class Win7Clock7Widget : SkinWidget(Skin.W7_CLOCK_SQUARE)
class Win7Clock8Widget : SkinWidget(Skin.W7_CLOCK_NOVELTY)
class Win7Clock9Widget : SkinWidget(Skin.W7_CLOCK_MONITOR)
class W7CpuWidget : SkinWidget(Skin.W7_CPU)
class W7CalendarWidget : SkinWidget(Skin.W7_CALENDAR)
class W7BatteryWidget : SkinWidget(Skin.W7_BATTERY)
class W7DriveWidget : SkinWidget(Skin.W7_DRIVE)
class W11ClockWidget : SkinWidget(Skin.W11_CLOCK)
class W11DateWidget : SkinWidget(Skin.W11_DATE)
class W11CpuWidget : SkinWidget(Skin.W11_CPU)
class W11MemoryWidget : SkinWidget(Skin.W11_MEMORY)
class W11BatteryWidget : SkinWidget(Skin.W11_BATTERY)
class W11StorageWidget : SkinWidget(Skin.W11_STORAGE)
class W11CalendarWidget : SkinWidget(Skin.W11_CALENDAR)
class W11TimeWidget : SkinWidget(Skin.W11_TIME)
class XpClockWidget : SkinWidget(Skin.XP_CLOCK)
class XpCpuWidget : SkinWidget(Skin.XP_CPU)
class XpBatteryWidget : SkinWidget(Skin.XP_BATTERY)
class XpDiskWidget : SkinWidget(Skin.XP_DISK)
class W95TimeWidget : SkinWidget(Skin.W95_TIME)
class W95CpuWidget : SkinWidget(Skin.W95_CPU)
class W95BatteryWidget : SkinWidget(Skin.W95_BATTERY)
class W95MemoryWidget : SkinWidget(Skin.W95_MEMORY)
class W95DateWidget : SkinWidget(Skin.W95_DATE)
class M3BatteryWidget : SkinWidget(Skin.M3_BATTERY)
class M3ClockWidget : SkinWidget(Skin.M3_CLOCK)
class M3DateWidget : SkinWidget(Skin.M3_DATE)
class M3CpuWidget : SkinWidget(Skin.M3_CPU)
class M3StorageWidget : SkinWidget(Skin.M3_STORAGE)
class M3MemoryWidget : SkinWidget(Skin.M3_MEMORY)
class AmClockWidget : SkinWidget(Skin.AM_CLOCK)
class AmBatteryWidget : SkinWidget(Skin.AM_BATTERY)
class AmCpuWidget : SkinWidget(Skin.AM_CPU)
class AmDateWidget : SkinWidget(Skin.AM_DATE)
class GlBatteryWidget : SkinWidget(Skin.GL_BATTERY)
class GlClockWidget : SkinWidget(Skin.GL_CLOCK)
class M3DatePillWidget : SkinWidget(Skin.M3_DATE_PILL)
class M3ClockDateWidget : SkinWidget(Skin.M3_CLOCK_DATE)
class M3BatteryRingWidget : SkinWidget(Skin.M3_BATTERY_RING)
class M3TempWidget : SkinWidget(Skin.M3_TEMP)
class M3RamBarWidget : SkinWidget(Skin.M3_RAM_BAR)
class M3NetworkWidget : SkinWidget(Skin.M3_NETWORK)
class M3UptimeWidget : SkinWidget(Skin.M3_UPTIME)
class M3OverviewWidget : SkinWidget(Skin.M3_OVERVIEW)
class GlTimeWidget : SkinWidget(Skin.GL_TIME)
class GlDateWidget : SkinWidget(Skin.GL_DATE)
class GlBatteryRingWidget : SkinWidget(Skin.GL_BATTERY_RING)
class GlCpuWidget : SkinWidget(Skin.GL_CPU)
class GlMemoryWidget : SkinWidget(Skin.GL_MEMORY)
class GlStorageWidget : SkinWidget(Skin.GL_STORAGE)
class IosClockDateWidget : SkinWidget(Skin.IOS_CLOCK_DATE)
class IosRingsWidget : SkinWidget(Skin.IOS_RINGS)
class IosStorageWidget : SkinWidget(Skin.IOS_STORAGE)
class IosMemoryWidget : SkinWidget(Skin.IOS_MEMORY)
class IosUpNextWidget : SkinWidget(Skin.IOS_UP_NEXT)
class IosWorldWidget : SkinWidget(Skin.IOS_WORLD)
