package com.umutk.pulse.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.umutk.pulse.R
import com.umutk.pulse.extras.weather.Forecast
import com.umutk.pulse.extras.weather.Place
import com.umutk.pulse.extras.weather.WeatherApi
import com.umutk.pulse.extras.weather.WeatherFmt
import com.umutk.pulse.extras.weather.WeatherStore
import com.umutk.pulse.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weather and clock on the bare wallpaper: no background, white text with a soft shadow, the temperature big on the left, the sky on the right and the date and time
 * under it. A tap on the widget shows the next saved place (the phone's own place first, then the cities added in the weather app); each placed widget keeps its own.
 */
class WeatherClearWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val id = textId
        val places = placesOf(ctx)
        val pl = places[indexOf(ctx, id).coerceIn(0, places.lastIndex)]
        val f = cachedFor(ctx, pl)
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, id) ?: (250 to 130)
        val w = (sz.first * dens).toInt().coerceIn(200, 1400); val h = (sz.second * dens).toInt().coerceIn(100, 1400)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            setImageViewBitmap(R.id.tile, draw(ctx, f, pl, w, h))
            setViewVisibility(R.id.tile, View.VISIBLE)
            setContentDescription(R.id.tile, tr("Weather", "Hava durumu") + " " + (f?.let { WeatherFmt.temp(it.temp, WeatherFmt.imperial(ctx)) } ?: ""))
            setViewVisibility(R.id.icon, View.GONE); setViewVisibility(R.id.title, View.GONE); setViewVisibility(R.id.value, View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            val next = Intent(ctx, WeatherClearWidget::class.java).setAction(ACTION_NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            setOnClickPendingIntent(R.id.tile, PendingIntent.getBroadcast(ctx, 9300 + (id and 0xFF), next, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val open = Intent().setComponent(ComponentName(ctx, "com.umutk.pulse.ExtraToolActivity")).putExtra("route", "weather").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 9400 + (id and 0xFF), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_MINUTE) {
            // the clock moves with the minute: the widgets are drawn again from the saved forecast (no network)
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherClearWidget::class.java))
            if (ids.isEmpty()) ctx.getSystemService(AlarmManager::class.java)?.cancel(minutePending(ctx))
            else ids.forEach { mgr.updateAppWidget(it, contentFor(ctx, it, WidgetStyles.of(ctx, it))) }
            return
        }
        if (intent.action == ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            if (id != 0) {
                val n = placesOf(ctx).size
                prefs(ctx).edit().putInt("idx_$id", (indexOf(ctx, id) + 1) % n).apply()
                AppWidgetManager.getInstance(ctx).updateAppWidget(id, contentFor(ctx, id, WidgetStyles.of(ctx, id)))
                refresh(ctx, goAsync())
            }
            return
        }
        super.onReceive(ctx, intent)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        super.onUpdate(ctx, mgr, ids)
        refresh(ctx, goAsync())
        scheduleMinute(ctx)
    }

    private fun minutePending(ctx: Context) = PendingIntent.getBroadcast(ctx, 9500,
        Intent(ctx, WeatherClearWidget::class.java).setAction(ACTION_MINUTE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** A repeating alarm every minute redraws the clock; the system may group the alarms a little when the phone sleeps. */
    private fun scheduleMinute(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L
        am.setRepeating(AlarmManager.RTC, next, 60_000L, minutePending(ctx))
    }

    /** Fetches the places the placed widgets show when their saved copy is over 25 minutes old (the system asks every 30), then redraws them. */
    private fun refresh(ctx: Context, pending: android.content.BroadcastReceiver.PendingResult) {
        com.umutk.pulse.extras.weather.WeatherAuto.ensure(ctx)
        Thread { try { fetchAll(ctx) } finally { pending.finish() } }.start()
    }

    /** The same on the calling thread; the background refresh of the weather page calls it too, so the widgets stay fresh without the page being opened. */
    fun fetchAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherClearWidget::class.java))
        if (ids.isEmpty()) return
        val places = placesOf(ctx)
        ids.map { places[indexOf(ctx, it).coerceIn(0, places.lastIndex)] }.distinct().forEach { pl ->
            val at = prefs(ctx).getLong("at_" + key(pl), 0L)
            if (System.currentTimeMillis() - at < 25 * 60_000L) return@forEach
            runCatching {
                val spot = if (pl.device) kotlinx.coroutines.runBlocking { com.umutk.pulse.extras.weather.deviceLocation(ctx) }?.let { Place(pl.name, it.latitude, it.longitude, true) } ?: com.umutk.pulse.extras.weather.WeatherStore.savedPlace(ctx)?.takeIf { it.device } ?: return@runCatching else pl
                prefs(ctx).edit().putString("raw_" + key(pl), WeatherApi.forecast(spot.lat, spot.lon)).putLong("at_" + key(pl), System.currentTimeMillis()).apply()
            }
        }
        ids.forEach { mgr.updateAppWidget(it, contentFor(ctx, it, WidgetStyles.of(ctx, it))) }
    }

    private fun draw(ctx: Context, f: Forecast?, pl: Place, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val imp = WeatherFmt.imperial(ctx)
        fun paint(sizeF: Float, bold: Boolean = false, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; this.alpha = alpha; textSize = h * sizeF
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setShadowLayer(h * .02f, 0f, h * .006f, 0x99000000.toInt())
        }
        val padL = w * .04f; val padR = w * .04f
        // every line on the left starts at the same ink edge: a glyph's own left bearing is taken off, so the big number and the small text line up exactly
        fun left(p: Paint, t: String): Float { val r = android.graphics.Rect(); p.getTextBounds(t, 0, t.length, r); return padL - r.left }
        // left: temperature (a fifth smaller than before), place, condition, range
        val temp = paint(.24f, true)
        val tt = f?.let { WeatherFmt.temp(it.temp, imp) } ?: "--"
        c.drawText(tt, left(temp, tt), h * .34f, temp)
        val name = paint(.075f, false, 190)
        val nm = (f?.place?.ifBlank { null } ?: pl.name).take(18)
        val pinS = (h * .085f).toInt(); val pinTop = (h * .405f).toInt()
        ContextCompat.getDrawable(ctx, R.drawable.wi_pin)?.mutate()?.let { d ->
            d.setTint(android.graphics.Color.WHITE); d.alpha = 190
            // the pin's own artwork has a margin of one 24th around it
            val m = pinS / 12
            d.setBounds(padL.toInt() - m, pinTop, padL.toInt() - m + pinS, pinTop + pinS); d.draw(c)
        }
        c.drawText(nm, padL + pinS * .8f, h * .475f, name)
        val cond = paint(.075f)
        val ct = f?.let { WeatherApi.condition(it.code) } ?: tr("No data yet", "Henüz veri yok")
        c.drawText(ct, left(cond, ct), h * .78f, cond)
        val today = f?.days?.firstOrNull()
        if (today != null) { val rt = "↑${WeatherFmt.temp(today.max, imp)} / ↓${WeatherFmt.temp(today.min, imp)}"; val rp = paint(.062f); c.drawText(rt, left(rp, rt), h * .88f, rp) }
        // right: the sky, the date and the time
        val icon = f?.let { WeatherApi.drawable(it.code) } ?: 0
        if (icon != 0) ContextCompat.getDrawable(ctx, icon)?.let { d ->
            val s = (h * .34f).toInt(); val right = (w - padR).toInt(); val top = (h * .08f).toInt()
            d.setBounds(right - s, top, right, top + s); d.draw(c)
        }
        val now = Date()
        val dp = paint(.07f, true).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(SimpleDateFormat("EEE, MMMM d", Locale.getDefault()).format(now), w - padR, h * .64f, dp)
        val fmt = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
        c.drawText(SimpleDateFormat(fmt, Locale.getDefault()).format(now), w - padR, h * .86f, paint(.20f, true).apply { textAlign = Paint.Align.RIGHT })
        return bmp
    }

    private fun placesOf(c: Context): List<Place> =
        listOfNotNull(WeatherStore.savedPlace(c)?.takeIf { it.device }) .ifEmpty { listOf(Place(tr("My location", "Konumum"), 0.0, 0.0, true)) } + WeatherStore.cities(c)

    private fun cachedFor(c: Context, pl: Place): Forecast? {
        val s = prefs(c)
        val raw = s.getString("raw_" + key(pl), null)
        if (raw != null) runCatching { return WeatherApi.parse(pl.name, raw, s.getLong("at_" + key(pl), 0L)) }
        // the place the weather app shows, until the widget has fetched its own
        return WeatherStore.cached(c)?.takeIf { pl.device || it.place == pl.name }
    }

    private fun key(p: Place) = if (p.device) "device" else "%.2f_%.2f".format(Locale.US, p.lat, p.lon)
    private fun prefs(c: Context) = c.getSharedPreferences("weather_clear_widget", Context.MODE_PRIVATE)
    private fun indexOf(c: Context, id: Int) = prefs(c).getInt("idx_$id", 0)

    private companion object {
        const val ACTION_NEXT = "com.umutk.pulse.action.WEATHER_NEXT"
        const val ACTION_MINUTE = "com.umutk.pulse.action.WEATHER_MINUTE"
    }
}
