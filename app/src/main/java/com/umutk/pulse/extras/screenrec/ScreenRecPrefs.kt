package com.umutk.pulse.extras.screenrec

import android.content.Context
import kotlin.math.min

enum class RecState { IDLE, RUNNING, PAUSED }

/** Recorder settings in the recorder's own prefs file; Pulse's Cfg/Opt are never written from here. */
object ScreenRecPrefs {
    const val QUALITY_LOW = 0
    const val QUALITY_MEDIUM = 1
    const val QUALITY_HIGH = 2
    val FPS_CHOICES = listOf(30, 60)
    val LIMIT_CHOICES = listOf(0, 5, 10, 30) // minutes, 0 = no limit
    const val SORT_NEWEST = 0
    const val SORT_NAME = 1
    const val SORT_SIZE = 2
    const val SORT_LENGTH = 3

    fun prefs(ctx: Context) = ctx.getSharedPreferences("screenrec", Context.MODE_PRIVATE)

    fun mic(ctx: Context) = prefs(ctx).getBoolean("mic", false)
    fun setMic(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("mic", on).apply()
    fun quality(ctx: Context) = prefs(ctx).getInt("quality", QUALITY_MEDIUM)
    fun setQuality(ctx: Context, q: Int) = prefs(ctx).edit().putInt("quality", q).apply()
    fun fps(ctx: Context) = prefs(ctx).getInt("fps", 30)
    fun setFps(ctx: Context, f: Int) = prefs(ctx).edit().putInt("fps", f).apply()
    fun limit(ctx: Context) = prefs(ctx).getInt("limit", 0)
    fun setLimit(ctx: Context, m: Int) = prefs(ctx).edit().putInt("limit", m).apply()
    fun sort(ctx: Context) = prefs(ctx).getInt("sort", SORT_NEWEST)
    fun setSort(ctx: Context, s: Int) = prefs(ctx).edit().putInt("sort", s).apply()
    fun countdown(ctx: Context) = prefs(ctx).getBoolean("countdown", true)
    fun setCountdown(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("countdown", on).apply()

    fun touches(ctx: Context) = prefs(ctx).getBoolean("touches", false)
    fun setTouches(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("touches", on).apply()
    fun floating(ctx: Context) = prefs(ctx).getBoolean("floating", true)
    fun setFloating(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("floating", on).apply()

    fun config(ctx: Context) = RecConfig(mic(ctx), quality(ctx), fps(ctx), limit(ctx))
}

/** What one recording uses; built from the prefs when the user starts it. */
data class RecConfig(val mic: Boolean, val quality: Int, val fps: Int, val limitMin: Int) {
    /** Short side capped by quality (720 / 1080 / native); both sides even for H.264. */
    fun size(screenW: Int, screenH: Int): Pair<Int, Int> {
        val cap = when (quality) { ScreenRecPrefs.QUALITY_LOW -> 720; ScreenRecPrefs.QUALITY_MEDIUM -> 1080; else -> Int.MAX_VALUE }
        val short = min(screenW, screenH)
        val k = if (short <= cap) 1f else cap.toFloat() / short
        return even(screenW * k) to even(screenH * k)
    }

    fun bitrate(w: Int, h: Int): Int {
        val bpp = when (quality) { ScreenRecPrefs.QUALITY_LOW -> .05; ScreenRecPrefs.QUALITY_MEDIUM -> .08; else -> .12 }
        return (w.toLong() * h * fps * bpp).toInt().coerceIn(1_500_000, 40_000_000)
    }

    private fun even(v: Float): Int = (v.toInt() / 2 * 2).coerceAtLeast(2)
}
