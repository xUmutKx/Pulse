package com.umutk.pulse.extras.palette.widget

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.core.icon.WallpaperStyle
import com.umutk.pulse.extras.palette.core.icon.WidgetStyle
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import java.io.File

/**
 * Single source of truth for what every placed Palette widget currently
 * shows. Providers only read from here; the studio only writes here — so
 * adding a new widget type just means reading these same prefs.
 */
object WidgetRefresher {
    private const val PREFS = "palette_widget"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentPalette(ctx: Context): ColorPalette {
        val p = prefs(ctx)
        val colors = p.getString("colors", null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(0xFF6E5A96.toInt())
        return ColorPalette(name = p.getString("name", "Palette") ?: "Palette", colors = colors)
    }

    fun currentStyle(ctx: Context): WidgetStyle =
        runCatching { WidgetStyle.valueOf(prefs(ctx).getString("style", WidgetStyle.GRADIENT.name)!!) }
            .getOrDefault(WidgetStyle.GRADIENT)

    fun labelVisible(ctx: Context): Boolean = prefs(ctx).getBoolean("label_visible", true)

    /** Live wallpaper reads this so it always animates the currently active theme. */
    fun currentWallpaperStyle(ctx: Context): WallpaperStyle =
        runCatching { WallpaperStyle.valueOf(prefs(ctx).getString("wallpaper_style", WallpaperStyle.GRADIENT_MESH.name)!!) }
            .getOrDefault(WallpaperStyle.GRADIENT_MESH)

    /** content:// URI of a user-picked video, played (muted, looping) by the live wallpaper instead of the procedural styles. */
    fun currentVideoUri(ctx: Context): Uri? =
        prefs(ctx).getString("video_uri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun useVideoWallpaper(ctx: Context): Boolean = prefs(ctx).getBoolean("use_video", false)

    /** Persists the video wallpaper choice; the caller must already hold a persistable read grant on [uri]. */
    fun setWallpaperVideo(ctx: Context, uri: Uri?, enabled: Boolean) {
        prefs(ctx).edit()
            .putString("video_uri", uri?.toString())
            .putBoolean("use_video", enabled && uri != null)
            .apply()
    }

    private fun photoFile(ctx: Context) = File(ctx.filesDir, "widget_photo.png")

    fun photoBitmap(ctx: Context): Bitmap? =
        photoFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    /** Saves the widget's background photo (or clears it) and redraws every placed widget. */
    fun setPhoto(ctx: Context, bmp: Bitmap?) {
        val f = photoFile(ctx)
        if (bmp == null) f.delete()
        else f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 92, it) }
        updateAll(ctx, PaletteWidgetProvider::class.java)
    }

    /** Called whenever the active theme changes; persists it and redraws every placed widget. */
    fun refresh(ctx: Context, spec: ThemeSpec) {
        prefs(ctx).edit()
            .putString("colors", spec.palette.colors.joinToString(",") { it.toString() })
            .putString("name", spec.palette.name)
            .putString("style", spec.widgetStyle.name)
            .putBoolean("label_visible", spec.widgetLabelVisible)
            .putString("wallpaper_style", spec.wallpaperStyle.name)
            .apply()
        updateAll(ctx, PaletteWidgetProvider::class.java)
        updateAll(ctx, PaletteSwatchWidgetProvider::class.java)
    }

    private fun updateAll(ctx: Context, provider: Class<*>) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, provider))
        if (ids.isNotEmpty()) ctx.sendBroadcast(
            android.content.Intent(ctx, provider).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
        )
    }
}
