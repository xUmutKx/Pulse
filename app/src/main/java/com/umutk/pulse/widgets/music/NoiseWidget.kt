package com.umutk.pulse.widgets.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.ambient.AmbientLast
import com.umutk.pulse.extras.ambient.AmbientMixer
import com.umutk.pulse.extras.whitenoise.AmbientPreset
import com.umutk.pulse.extras.whitenoise.BuiltInPresets
import com.umutk.pulse.extras.whitenoise.NoiseLook
import com.umutk.pulse.extras.whitenoise.NoiseThemes
import com.umutk.pulse.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** White noise widget: last mix name, big play/pause, four one-tap presets; drawn in the look chosen in the White Noise page (72 looks incl. Winamp). */
class NoiseWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { watch(c); ids.forEach { m.updateAppWidget(it, build(c)) } }

    override fun onEnabled(c: Context) = watch(c)

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            A_TOGGLE -> AmbientLast.toggle(c)
            A_PRESET -> presets.getOrNull(i.getIntExtra("n", -1))?.let { p ->
                AmbientLast.save(c, p.levels)
                if (AmbientMixer.isPlaying()) AmbientMixer.applyMix(p.levels) else AmbientMixer.load(c) { AmbientMixer.applyMix(p.levels) }
            }
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_TOGGLE = "com.umutk.pulse.noisew.TOGGLE"
        const val A_PRESET = "com.umutk.pulse.noisew.PRESET"
        /** Calm, Rain, Sea, Fire = Calm, Gentle rain, Seaside, Fireplace of BuiltInPresets (matched by English name). */
        private val presets: List<AmbientPreset> by lazy {
            listOf("Calm", "Gentle rain", "Seaside", "Fireplace").map { n -> BuiltInPresets.first { it.name == n } }
        }
        private val short = listOf("Calm" to "Sakin", "Rain" to "Yağmur", "Sea" to "Deniz", "Fire" to "Ateş")
        private var job: Job? = null

        private fun watch(c: Context) {
            if (job?.isActive == true) return
            val app = c.applicationContext
            job = AmbientMixer.running.drop(1).onEach { refresh(app) }.launchIn(CoroutineScope(Dispatchers.Main))
        }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val v = build(c)
            m.getAppWidgetIds(ComponentName(c, NoiseWidget::class.java)).forEach { m.updateAppWidget(it, v) }
        }

        /** The chosen noise look, or one drawn from the widget palette (light/dark) when "app look" is chosen. */
        private fun look(c: Context): NoiseLook = NoiseThemes.current(c)
            ?: NoiseLook("app", "App", Color.parseColor(if (WidgetUi.dark(c)) "#2B2930" else "#F3EDF7"), Color.parseColor(if (WidgetUi.dark(c)) "#211F26" else "#E8DEF8"),
                WidgetUi.accent(c), WidgetUi.text(c), 0)

        private fun bmp(w: Int, h: Int, draw: Canvas.(Paint) -> Unit): Bitmap =
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).draw(Paint(Paint.ANTI_ALIAS_FLAG)) }

        private fun stroked(p: Paint, col: Int, w: Float) { p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = w; p.color = col }

        /** Windows 95 face: grey fill, white/grey 2 px bevel, black outer edge; [raised] false gives the sunken one. */
        private fun Canvas.bevel95(p: Paint, w: Float, h: Float, raised: Boolean = true) {
            p.shader = null; p.style = Paint.Style.FILL
            p.color = 0xFFC0C0C0.toInt(); drawRect(0f, 0f, w, h, p)
            val hi = if (raised) Color.WHITE else 0xFF808080.toInt(); val lo = if (raised) 0xFF808080.toInt() else Color.WHITE
            val k = if (raised) Color.BLACK else 0xFF404040.toInt()
            p.color = k; drawRect(0f, h - 2f, w, h, p); drawRect(w - 2f, 0f, w, h, p)
            p.color = hi; drawRect(0f, 0f, w - 2f, 2f, p); drawRect(0f, 0f, 2f, h - 2f, p)
            p.color = lo; drawRect(2f, h - 4f, w - 2f, h - 2f, p); drawRect(w - 4f, 2f, w - 2f, h - 2f, p)
        }

        private fun bg(l: NoiseLook) = bmp(400, 200) { p ->
            if (l.style == 6) { bevel95(p, 400f, 200f); return@bmp }
            val r = floatArrayOf(36f, 60f, 0f, 28f, 48f, 0f, 0f)[l.style]
            val box = RectF(2f, 2f, 398f, 198f)
            p.shader = LinearGradient(0f, 0f, 0f, 200f, l.bg1, l.bg2, Shader.TileMode.CLAMP)
            drawRoundRect(box, r, r, p)
            if (l.style == 3 || l.style == 5 || l.style == 4) { stroked(p, if (l.style == 4) (l.text and 0xFFFFFF) or 0x55000000 else l.accent, 3f); drawRoundRect(box, r, r, p) }
            if (l.style == 5) { stroked(p, l.accent, 2f); for (i in 0 until 3) drawLine(14f, 10f + i * 5f, 386f, 10f + i * 5f, p) } // titlebar stripes
        }

        private fun chip(l: NoiseLook) = bmp(160, 72) { p ->
            if (l.style == 6) { bevel95(p, 160f, 72f); return@bmp }
            val r = floatArrayOf(18f, 36f, 0f, 14f, 24f, 0f, 0f)[l.style]
            val box = RectF(2f, 2f, 158f, 70f)
            p.style = Paint.Style.FILL
            p.color = if (l.style == 5) Color.BLACK else if (l.style == 3) Color.TRANSPARENT else (l.accent and 0xFFFFFF) or 0x38000000
            drawRoundRect(box, r, r, p)
            if (l.style == 3 || l.style == 5) { stroked(p, l.accent, 3f); drawRoundRect(box, r, r, p) }
        }

        private fun play(l: NoiseLook, playing: Boolean) = bmp(112, 112) { p ->
            if (l.style == 6) {
                bevel95(p, 112f, 112f); p.color = Color.BLACK
                if (playing) { drawRect(34f, 30f, 49f, 82f, p); drawRect(63f, 30f, 78f, 82f, p) }
                else for (i in 0 until 5) drawRect(38f + i * 8f, 28f + i * 6f, 46f + i * 8f, 84f - i * 6f, p)
                return@bmp
            }
            val outline = l.style == 3
            val lightAccent = androidx.core.graphics.ColorUtils.calculateLuminance(l.accent) > .5f
            val ink = if (outline) l.accent else if (lightAccent) Color.BLACK else Color.WHITE
            p.style = Paint.Style.FILL; p.color = if (outline) Color.TRANSPARENT else l.accent
            val box = RectF(3f, 3f, 109f, 109f)
            when (l.style) { 2, 5 -> drawRect(box, p); 1, 0, 4, 3 -> drawRoundRect(box, if (l.style == 3) 20f else 56f, if (l.style == 3) 20f else 56f, p) }
            if (outline) { stroked(p, l.accent, 4f); drawRoundRect(box, 20f, 20f, p) }
            p.style = Paint.Style.FILL; p.color = ink
            if (playing) { drawRect(36f, 32f, 50f, 80f, p); drawRect(62f, 32f, 76f, 80f, p) }
            else drawPath(Path().apply { moveTo(40f, 30f); lineTo(84f, 56f); lineTo(40f, 82f); close() }, p)
        }

        fun build(c: Context): RemoteViews {
            val l = look(c)
            val mix = AmbientLast.load(c)
            val hit = presets.firstOrNull { it.levels == mix } ?: BuiltInPresets.firstOrNull { it.levels == mix }
            val name = when {
                mix.isEmpty() -> tr("White noise", "Beyaz gürültü")
                hit != null -> tr(hit.en, hit.tr)
                else -> tr("Last mix", "Son karışım")
            }
            val chipBmp = chip(l)
            return RemoteViews(c.packageName, R.layout.widget_noise_main).apply {
                setImageViewBitmap(R.id.nw_bg, bg(l))
                setTextViewText(R.id.nw_name, name); setTextColor(R.id.nw_name, l.text)
                setImageViewBitmap(R.id.nw_play, play(l, AmbientLast.isPlaying()))
                setOnClickPendingIntent(R.id.nw_play, WidgetUi.broadcast(c, NoiseWidget::class.java, A_TOGGLE))
                intArrayOf(R.id.nw_p0, R.id.nw_p1, R.id.nw_p2, R.id.nw_p3).forEachIndexed { i, id ->
                    setImageViewBitmap(intArrayOf(R.id.nw_c0, R.id.nw_c1, R.id.nw_c2, R.id.nw_c3)[i], chipBmp)
                    setTextViewText(id, tr(short[i].first, short[i].second)); setTextColor(id, l.text)
                    setOnClickPendingIntent(id, WidgetUi.broadcast(c, NoiseWidget::class.java, A_PRESET, 90 + i) { putExtra("n", i) })
                }
            }
        }
    }
}
