package com.umutk.pulse.widgets.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.skin.SPRITE_H
import com.umutk.pulse.extras.music.skin.WINAMP_H
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampButton
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.extras.music.skin.installedSkins
import com.umutk.pulse.extras.music.skin.loadWinampSkin
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Winamp main window as a home-screen widget: skin bitmap, track title, seek bar, working transport buttons. */
class WinampWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { watch(c); ids.forEach { m.updateAppWidget(it, build(c, it)) } }

    override fun onEnabled(c: Context) = watch(c)

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            A_PREV -> MusicPlayerEngine.get(c).previous()
            A_PLAY -> MusicPlayerEngine.get(c).play()
            A_PAUSE -> MusicPlayerEngine.get(c).pause()
            A_STOP -> MusicPlayerEngine.get(c).player.run { pause(); seekTo(0) }
            A_NEXT -> MusicPlayerEngine.get(c).next()
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_PREV = "com.umutk.pulse.winamp.PREV"
        const val A_PLAY = "com.umutk.pulse.winamp.PLAY"
        const val A_PAUSE = "com.umutk.pulse.winamp.PAUSE"
        const val A_STOP = "com.umutk.pulse.winamp.STOP"
        const val A_NEXT = "com.umutk.pulse.winamp.NEXT"
        /** SharedPreferences key "winamp_widget": "skin_<appWidgetId>" = file name in filesDir/extras/skins, absent = drawn default. */
        fun prefs(c: Context) = c.getSharedPreferences("winamp_widget", Context.MODE_PRIVATE)
        fun skinDir(c: Context) = File(c.filesDir, "extras/skins")

        private var job: Job? = null

        /** Redraws on every playback state change while the process lives (the engine is a process singleton). */
        private fun watch(c: Context) {
            if (job?.isActive == true) return
            val app = c.applicationContext
            job = MusicPlayerEngine.get(app).state.drop(1).onEach { refresh(app) }.launchIn(CoroutineScope(Dispatchers.Main))
        }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, WinampWidget::class.java)).forEach { m.updateAppWidget(it, build(c, it)) }
        }

        private val skinCache = HashMap<String, Pair<Long, WinampSkin?>>()

        private fun skinFor(c: Context, id: Int): WinampSkin? {
            val name = prefs(c).getString("skin_$id", null) ?: return null
            // skins come from the store's "skins" pack (either install location), matched by file name
            val f = installedSkins(c).firstOrNull { it.file.name == name }?.file ?: return null
            val hit = skinCache[name]
            if (hit != null && hit.first == f.lastModified()) return hit.second
            val s = loadWinampSkin(f)
            skinCache[name] = f.lastModified() to s
            return s
        }

        private fun pi(c: Context, a: String) =
            PendingIntent.getBroadcast(c, a.hashCode(), Intent(a).setComponent(ComponentName(c, WinampWidget::class.java)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun build(c: Context, id: Int): RemoteViews {
            val e = MusicPlayerEngine.get(c)
            val st = e.state.value
            val dur = e.player.duration.takeIf { it > 0 } ?: 0L
            val frac = if (dur > 0) (e.player.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val title = st.current?.let { it.title ?: it.path.substringAfterLast('/') } ?: "Winamp"
            val bmp = render(skinFor(c, id), title, frac, st.isPlaying, e.player.currentPosition / 1000)
            return RemoteViews(c.packageName, R.layout.widget_winamp).apply {
                setImageViewBitmap(R.id.wa_img, bmp)
                setOnClickPendingIntent(R.id.wa_prev, pi(c, A_PREV)); setOnClickPendingIntent(R.id.wa_play, pi(c, A_PLAY))
                setOnClickPendingIntent(R.id.wa_pause, pi(c, A_PAUSE)); setOnClickPendingIntent(R.id.wa_stop, pi(c, A_STOP))
                setOnClickPendingIntent(R.id.wa_next, pi(c, A_NEXT))
            }
        }

        /** 275x116 main window drawn at 2x. A skin without main.bmp gets the drawn default; button cells come from cbuttons.bmp when present. */
        fun render(skin: WinampSkin?, title: String, frac: Float, playing: Boolean, posSec: Long): Bitmap {
            val k = 2
            val out = Bitmap.createBitmap(WINAMP_W * k, WINAMP_H * k, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out); cv.scale(k.toFloat(), k.toFloat())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val main = skin?.main?.asAndroidBitmap()
            if (main != null) cv.drawBitmap(main, null, Rect(0, 0, WINAMP_W, WINAMP_H), p) else drawDefaultBg(cv, p)
            val btn = skin?.buttons?.asAndroidBitmap()
            WinampButton.entries.forEach { b ->
                if (btn != null) {
                    val down = (b == WinampButton.PLAY && playing) || (b == WinampButton.PAUSE && !playing && posSec > 0)
                    cv.drawBitmap(btn, Rect(b.sx, if (down) SPRITE_H else 0, b.sx + b.w, if (down) 2 * SPRITE_H else SPRITE_H), Rect(b.x, b.y, b.x + b.w, b.y + SPRITE_H), p)
                } else drawDefaultButton(cv, p, b, playing)
            }
            val vis = skin?.vis
            val fg = vis?.getOrNull(1)?.toArgb() ?: Color.rgb(0, 230, 0)
            // title display (main.bmp's LCD area) and time
            p.color = Color.BLACK; cv.drawRect(110f, 24f, 262f, 38f, p)
            p.color = fg; p.typeface = Typeface.MONOSPACE; p.textSize = 9f
            val t = "${(posSec / 60).toString().padStart(2, '0')}:${(posSec % 60).toString().padStart(2, '0')}"
            cv.drawText(t, 112f, 33f, p)
            cv.save(); cv.clipRect(110f, 40f, 262f, 56f); cv.drawText(title, 112f, 50f, p); cv.restore()
            // seek bar (posbar slot)
            p.color = Color.rgb(24, 24, 32); cv.drawRect(16f, 72f, 264f, 82f, p)
            p.color = fg; cv.drawRect(16f, 74f, 16f + 248f * frac, 80f, p)
            return out
        }

        private fun drawDefaultBg(cv: Canvas, p: Paint) {
            p.color = Color.rgb(58, 58, 74); cv.drawRect(0f, 0f, WINAMP_W.toFloat(), WINAMP_H.toFloat(), p)
            p.color = Color.rgb(34, 34, 48); cv.drawRect(0f, 0f, WINAMP_W.toFloat(), 14f, p)
            p.color = Color.rgb(160, 160, 190); p.textSize = 9f; p.typeface = Typeface.DEFAULT_BOLD; cv.drawText("WINAMP", 108f, 10f, p)
            p.color = Color.BLACK; cv.drawRect(10f, 20f, 100f, 62f, p)
        }

        private fun drawDefaultButton(cv: Canvas, p: Paint, b: WinampButton, playing: Boolean) {
            val on = (b == WinampButton.PLAY && playing)
            p.color = if (on) Color.rgb(90, 90, 120) else Color.rgb(78, 78, 98)
            cv.drawRect(b.x.toFloat(), b.y.toFloat(), (b.x + b.w - 1).toFloat(), (b.y + SPRITE_H - 1).toFloat(), p)
            p.color = Color.rgb(220, 220, 235)
            val cx = b.x + b.w / 2f; val cy = b.y + SPRITE_H / 2f
            when (b) {
                WinampButton.PLAY -> cv.drawPath(android.graphics.Path().apply { moveTo(cx - 3, cy - 4); lineTo(cx + 4, cy); lineTo(cx - 3, cy + 4); close() }, p)
                WinampButton.PAUSE -> { cv.drawRect(cx - 3, cy - 4, cx - 1, cy + 4, p); cv.drawRect(cx + 1, cy - 4, cx + 3, cy + 4, p) }
                WinampButton.STOP -> cv.drawRect(cx - 3, cy - 3, cx + 3, cy + 3, p)
                WinampButton.PREV -> { cv.drawRect(cx - 4, cy - 4, cx - 2, cy + 4, p); cv.drawPath(android.graphics.Path().apply { moveTo(cx + 4, cy - 4); lineTo(cx - 2, cy); lineTo(cx + 4, cy + 4); close() }, p) }
                WinampButton.NEXT -> { cv.drawRect(cx + 2, cy - 4, cx + 4, cy + 4, p); cv.drawPath(android.graphics.Path().apply { moveTo(cx - 4, cy - 4); lineTo(cx + 2, cy); lineTo(cx - 4, cy + 4); close() }, p) }
                WinampButton.EJECT -> cv.drawPath(android.graphics.Path().apply { moveTo(cx - 4, cy + 1); lineTo(cx, cy - 4); lineTo(cx + 4, cy + 1); close() }, p)
            }
        }
    }
}
