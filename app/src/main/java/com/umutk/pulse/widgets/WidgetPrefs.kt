package com.umutk.pulse.widgets

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.MaterialAccents
import com.umutk.pulse.Themes
import com.umutk.pulse.WinAccents
import com.umutk.pulse.accentsFor

/**
 * Widget look settings, edited on the Widgets page Settings tab and read by every widget provider.
 * Kept in SharedPreferences "widget_look" so the providers can read them while Pulse is closed.
 */
object WidgetLook {
    /** How solid each design's tile is by default, 0 = clear, 1 = opaque: only Windows 11 and glass are see-through. */
    val defaults = mapOf("win11" to .62f, "win7" to .85f, "win10" to .92f, "xp" to 1f, "win95" to 1f, "material" to .95f, "glass" to .6f, "ios" to .9f, "amoled" to 1f)
    /** The opacity the user picked per design; a design missing here uses its [defaults] value. */
    val picked = mutableStateMapOf<String, Float>()
    fun opacityOf(design: String) = picked[design] ?: defaults[design] ?: 1f
    /** The Windows 11 tile's opacity (the Settings tab slider). */
    var opacity: Float
        get() = opacityOf("win11")
        set(v) { picked["win11"] = v }
    /** Blur strength of the wallpaper wash behind Windows 11 tiles, 0..1. */
    var blur by mutableFloatStateOf(.3f)
    /** The accent every widget without a pick of its own draws with (Windows 11 lightens it); 0 follows the app. */
    var accentColor by mutableStateOf(0)
    /** The Windows 11 tile material: acrylic / mica / blur (the lock-screen wallpaper blur; its amount is [blur], its darkness [tint]). */
    var material by mutableStateOf("acrylic")
    var tint by mutableFloatStateOf(.25f)
    /** What the Windows 11 "blur" material shows behind the tile: stock (the Windows 11 wallpaper) / phone (the phone wallpaper) / file (own picture) / none (just the dark tint). */
    var blurBg by mutableStateOf("stock")
    /** The widget configuration window: material (Material You, default) / own (the widget's design) / blur (Windows 11 over a blurred wallpaper). */
    var windowLook by mutableStateOf("material")
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("widget_look", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        p.all.forEach { (k, v) -> if (k.startsWith("op_") && v is Float) picked[k.removePrefix("op_")] = v }
        if (p.contains("opacity")) picked["win11"] = p.getFloat("opacity", .62f)
        blur = p.getFloat("blur", .3f)
        accentColor = p.getInt("accentColor", 0)
        material = p.getString("material", "acrylic") ?: "acrylic"; tint = p.getFloat("tint", .25f); windowLook = p.getString("windowLook", "material") ?: "material"
        blurBg = p.getString("blurBg", "stock") ?: "stock"
    }

    /** Saves and redraws the placed widgets. */
    fun save(c: Context) {
        prefs(c).edit().apply { picked.forEach { (k, v) -> putFloat("op_$k", v) } }.putFloat("opacity", opacity).putFloat("blur", blur).putInt("accentColor", accentColor).putString("blurBg", blurBg).putString("material", material).putFloat("tint", tint).putString("windowLook", windowLook).apply()
        WidgetData.refreshNow(c)
        WidgetPreviews.publish(c)
    }
}

/** The accent the Material, Glass and iOS widgets draw with: a pick of their own per appWidgetId, else the app's accent. */
object WidgetAccent {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)

    /** The swatches a widget can pick from: the Material pastels, then the Windows colours. */
    val swatches: List<Int> by lazy { (MaterialAccents + WinAccents).map { it.toArgb() }.distinct() }

    /** The app accent as the app shows it: the wallpaper's on Material You, else the picked swatch or the theme's own. */
    fun app(c: Context): Int {
        WidgetLook.load(c)
        if (WidgetLook.accentColor != 0) return WidgetLook.accentColor
        val p = com.umutk.pulse.ownPrefs(c, "pulse_settings")
        val t = Themes.firstOrNull { it.id == p.getString("theme", "win11") } ?: Themes[0]
        if (t.id == "material" && p.getBoolean("dynamic", true)) return c.getColor(android.R.color.system_accent1_200)
        // same rule as Look.accent: only Material, tab-less and the two Windows 10/11 themes take the picked swatch
        val swatch = t.material || !t.tabs || t.id == "win10" || t.id == "win11"
        return (if (swatch) accentsFor(t.material).getOrNull(p.getInt("accent2", -1)) ?: t.accent else t.accent).toArgb()
    }

    /** The widget's own accent, or 0 when it follows the app. */
    fun own(c: Context, id: Int): Int = prefs(c).getInt("accent_$id", 0)

    fun of(c: Context, id: Int): Int = own(c, id).takeIf { it != 0 } ?: app(c)

    /** Saves [color] for the widget; 0 follows the app again. */
    fun save(c: Context, id: Int, color: Int) = prefs(c).edit().apply { if (color == 0) remove("accent_$id") else putInt("accent_$id", color) }.apply()

    fun remove(c: Context, ids: IntArray) {
        val edit = prefs(c).edit()
        ids.forEach { edit.remove("accent_$it") }
        edit.apply()
        WidgetOver.remove(c, ids)
    }
}

/** Per-appWidgetId overrides of the look (opacity, blur, material, tint); an id with none of its own, or id <= 0, follows the global [WidgetLook]. */
object WidgetOver {
    private fun p(c: Context) = c.getSharedPreferences("widget_over", Context.MODE_PRIVATE)
    private fun g(c: Context) = WidgetLook.also { it.load(c) }

    fun has(c: Context, id: Int) = id > 0 && p(c).contains("op_$id")
    /** The widget's own opacity, else the global one picked for [design], else that design's default (Windows XP and 95 opaque, Windows 11 glassy). */
    fun opacity(c: Context, id: Int, design: String = "win11") = if (has(c, id)) p(c).getFloat("op_$id", 1f) else g(c).opacityOf(design)
    fun blur(c: Context, id: Int) = if (id > 0) p(c).getFloat("blur_$id", g(c).blur) else g(c).blur
    fun tint(c: Context, id: Int) = if (id > 0) p(c).getFloat("tint_$id", g(c).tint) else g(c).tint
    fun material(c: Context, id: Int) = (if (id > 0) p(c).getString("mat_$id", null) else null) ?: g(c).material
    /** True when this widget has a material of its own (picked in its settings), not the global one. */
    fun materialSet(c: Context, id: Int) = id > 0 && p(c).contains("mat_$id")

    /** [opacity] null = not touched: the widget keeps following the design default. */
    fun save(c: Context, id: Int, opacity: Float?, blur: Float, material: String, tint: Float) =
        p(c).edit().apply { if (opacity == null) remove("op_$id") else putFloat("op_$id", opacity) }.putFloat("blur_$id", blur).putString("mat_$id", material).putFloat("tint_$id", tint).apply()

    fun remove(c: Context, ids: IntArray) = p(c).edit().apply { ids.forEach { remove("op_$it"); remove("blur_$it"); remove("mat_$it"); remove("tint_$it") } }.apply()
}

/** The size of a placed widget in dp from its options (portrait width, height); null until the launcher has reported one. */
object WidgetSize {
    fun dp(c: Context, id: Int): Pair<Int, Int>? {
        if (id <= 0) return null
        val o = android.appwidget.AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
        val w = o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return if (w > 0 && h > 0) w to h else null
    }
}
