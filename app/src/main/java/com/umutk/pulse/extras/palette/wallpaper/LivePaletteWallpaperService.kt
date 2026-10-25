package com.umutk.pulse.extras.palette.wallpaper

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.umutk.pulse.extras.palette.core.color.withAlpha
import com.umutk.pulse.extras.palette.core.icon.EffectPainter
import com.umutk.pulse.extras.palette.core.icon.WallpaperStyle
import com.umutk.pulse.extras.palette.widget.WidgetRefresher
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated counterpart to [com.umutk.pulse.extras.palette.core.wallpaper.WallpaperGenerator]. A
 * static wallpaper needs an external trigger (a tap, a Tasker broadcast) to ever
 * change; this reads the same [WidgetRefresher]-persisted palette every frame, so
 * it both animates on its own and picks up theme changes made in-app immediately
 * — no adb, no automation setup required.
 */
class LivePaletteWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PaletteEngine()

    private companion object {
        /** ~12.5fps — smooth enough for slow drift, gentle on battery. */
        const val FRAME_MS = 80L
    }

    private inner class PaletteEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val startTime = SystemClock.elapsedRealtime()
        private var visible = false
        private val drawRunnable = Runnable { draw() }

        /** Plain video playback, muted + looping, used instead of the procedural styles when the user picks one. */
        private var mediaPlayer: MediaPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (isVideoMode()) startVideo(holder)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                if (isVideoMode()) {
                    handler.removeCallbacks(drawRunnable)
                    if (mediaPlayer == null) startVideo(surfaceHolder) else mediaPlayer?.start()
                } else {
                    releaseVideo()
                    draw()
                }
            } else {
                handler.removeCallbacks(drawRunnable)
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (isVideoMode()) startVideo(holder) else draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
            releaseVideo()
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            releaseVideo()
        }

        private fun isVideoMode(): Boolean {
            val ctx = this@LivePaletteWallpaperService
            return WidgetRefresher.useVideoWallpaper(ctx) && WidgetRefresher.currentVideoUri(ctx) != null
        }

        /** MediaPlayer streams straight into the wallpaper surface — no scaling/crop, the video is stretched to fill it. */
        private fun startVideo(holder: SurfaceHolder) {
            val ctx = this@LivePaletteWallpaperService
            val uri = WidgetRefresher.currentVideoUri(ctx) ?: return
            releaseVideo()
            mediaPlayer = MediaPlayer()
            runCatching {
                mediaPlayer?.apply {
                    setDataSource(ctx, uri)
                    setSurface(holder.surface)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnPreparedListener { if (visible) it.start() }
                    prepareAsync()
                }
            }.onFailure { releaseVideo() }
        }

        private fun releaseVideo() {
            mediaPlayer?.let { runCatching { it.release() } }
            mediaPlayer = null
        }

        private fun draw() {
            if (isVideoMode()) { handler.removeCallbacks(drawRunnable); return }
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let { render(it, it.width.toFloat(), it.height.toFloat()) }
            } catch (_: Exception) {
                // Surface can vanish mid-lock during rotation/screen-off; skip this frame.
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            handler.removeCallbacks(drawRunnable)
            if (visible) handler.postDelayed(drawRunnable, FRAME_MS)
        }

        private fun render(c: Canvas, w: Float, h: Float) {
            val ctx = this@LivePaletteWallpaperService
            val palette = WidgetRefresher.currentPalette(ctx)
            val style = WidgetRefresher.currentWallpaperStyle(ctx)
            val t = (SystemClock.elapsedRealtime() - startTime) / 1000f
            val r = RectF(0f, 0f, w, h)
            val full = Path().apply { addRect(r, Path.Direction.CW) }
            val ramp = palette.ramp.ifEmpty { listOf(palette.accent) }

            when (style) {
                WallpaperStyle.GRADIENT_MESH -> {
                    val angle = (t * 6f) % 360f
                    EffectPainter.gradient(c, full, r, ramp.first(), ramp.last(), angle)
                    if (ramp.size >= 3) {
                        EffectPainter.gradient(
                            c, full, r, palette.accent, ramp.first(), (angle + 200f) % 360f, alpha = 0.28f
                        )
                    }
                }

                WallpaperStyle.ANGLED_BANDS -> {
                    val n = ramp.size.coerceAtLeast(1)
                    val bandWidth = (w + h) / n
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    c.save()
                    c.rotate(-18f, w / 2f, h / 2f)
                    val span = w + h * 2f
                    val scroll = (t * bandWidth * 0.12f) % bandWidth
                    var x = -h - scroll
                    var i = 0
                    while (x < span) {
                        paint.color = ramp[i % n]
                        c.drawRect(x, -h, x + bandWidth, h * 2f, paint)
                        x += bandWidth; i++
                    }
                    c.restore()
                }

                WallpaperStyle.SOFT_BLOBS -> {
                    EffectPainter.fill(c, full, palette.darkest)
                    val n = ramp.size.coerceIn(3, 7)
                    repeat(n) { i ->
                        val color = ramp[i % ramp.size]
                        val phase = i * 1.7f
                        val cx = (0.5f + 0.35f * sin(t * 0.18f + phase)) * w
                        val cy = (0.5f + 0.35f * cos(t * 0.14f + phase * 1.3f)) * h
                        val radius = (0.30f + 0.12f * sin(t * 0.2f + phase)) * max(w, h)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            shader = RadialGradient(
                                cx, cy, radius.coerceAtLeast(1f),
                                color.withAlpha(190), color.withAlpha(0), Shader.TileMode.CLAMP
                            )
                        }
                        c.drawCircle(cx, cy, radius.coerceAtLeast(1f), paint)
                    }
                }

                WallpaperStyle.MINIMAL_FADE -> {
                    val breathe = sin(t * 0.6f)
                    EffectPainter.gradient(c, full, r, palette.darkest, palette.lightest, 90f + breathe * 20f)
                }

                WallpaperStyle.AURORA_WAVES -> {
                    EffectPainter.gradient(c, full, r, palette.darkest, palette.sample(0.32f), 100f)
                    val ramp = palette.ramp.ifEmpty { listOf(palette.accent) }
                    val bands = ramp.size.coerceIn(3, 6)
                    val wavelength = w * 0.8f
                    val phase = t * 0.6f
                    repeat(bands) { i ->
                        val color = ramp[i % ramp.size]
                        val yCenter = h * (0.15f + 0.7f * i / bands.toFloat())
                        val amplitude = h * 0.06f
                        val thickness = h * 0.14f
                        val steps = 40
                        val path = Path()
                        for (s in 0..steps) {
                            val x = w * s / steps
                            val y = yCenter - thickness / 2f + amplitude * sin(2f * Math.PI.toFloat() * x / wavelength + phase + i)
                            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        for (s in steps downTo 0) {
                            val x = w * s / steps
                            val y = yCenter + thickness / 2f + amplitude * sin(2f * Math.PI.toFloat() * x / wavelength + phase + i)
                            path.lineTo(x, y)
                        }
                        path.close()
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.withAlpha(150) }
                        c.drawPath(path, paint)
                    }
                }

                WallpaperStyle.CONFETTI_DOTS -> {
                    EffectPainter.fill(c, full, palette.lightest)
                    val rnd = Random(7L)
                    val ramp = palette.ramp.ifEmpty { listOf(palette.accent) }
                    repeat(60) { i ->
                        val color = ramp[rnd.nextInt(ramp.size)]
                        val cx = rnd.nextFloat() * w
                        val cy = rnd.nextFloat() * h
                        val baseRadius = (0.015f + rnd.nextFloat() * 0.035f) * max(w, h)
                        val pulse = 0.85f + 0.15f * sin(t * 0.8f + i)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.color = color.withAlpha((210 * pulse).toInt().coerceIn(0, 255))
                        }
                        c.drawCircle(cx, cy, baseRadius * pulse, paint)
                    }
                }
            }
        }
    }
}
