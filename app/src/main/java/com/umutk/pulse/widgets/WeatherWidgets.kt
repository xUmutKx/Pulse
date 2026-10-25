package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.weather.WeatherApi
import com.umutk.pulse.extras.weather.WeatherFmt
import com.umutk.pulse.extras.weather.WeatherStore
import com.umutk.pulse.tr

/** Redraws the weather widgets from the saved forecast; a fetch goes through [WeatherWidgetBase.onUpdate]. */
object WeatherWidgets {
    fun refreshAll(c: Context) {
        val mgr = AppWidgetManager.getInstance(c)
        listOf(WeatherWidget(), WeatherWideWidget(), WeatherClearWidget()).forEach { w ->
            mgr.getAppWidgetIds(ComponentName(c, w.javaClass)).forEach { id ->
                mgr.updateAppWidget(id, w.contentFor(c, id, WidgetStyles.of(c, id)))
            }
        }
    }
}

/**
 * Home-screen weather in two sizes: small (2x2) and wide (4x2, with the next 12 hours as a line).
 * Draws the saved forecast at once; the network is only used when the saved copy is over an hour old.
 */
abstract class WeatherWidgetBase(private val wide: Boolean) : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val id = textId
        val f = WeatherStore.cached(ctx)
        val imp = WeatherFmt.imperial(ctx)
        val title = tr("Weather", "Hava durumu")
        val tile = if (f == null) {
            Tile(big = "--", sub = listOf(tr("No data yet", "Henüz veri yok")))
        } else {
            val today = f.days.firstOrNull()
            val range = today?.let { "${WeatherFmt.temp(it.max, imp)} / ${WeatherFmt.temp(it.min, imp)}" }
            val sub = listOfNotNull(f.place.ifBlank { null }, WeatherApi.condition(f.code) + (range?.let { " · $it" } ?: ""))
            val line = if (wide) f.hours.take(12).map { (if (imp) it.temp * 9 / 5 + 32 else it.temp).toFloat() } else emptyList()
            Tile(big = WeatherFmt.temp(f.temp, imp), sub = sub, series = line)
        }
        val icon = f?.let { WeatherApi.drawable(it.code) } ?: 0
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, id) ?: (if (wide) 250 to 110 else 110 to 110)
        val tw = (sz.first * dens).toInt().coerceIn(100, 1400)
        val th = (sz.second * dens).toInt().coerceIn(60, 1400)
        val prev = Win11Material.forId.get(); Win11Material.forId.set(id)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            try {
                setImageViewBitmap(R.id.tile, TextTile.fitBinder(TextTile.draw(ctx, s, icon, title, tile, tw, th, id)))
            } finally { Win11Material.forId.set(prev) }
            setViewVisibility(R.id.tile, View.VISIBLE)
            setContentDescription(R.id.tile, "$title ${tile.big}")
            setViewVisibility(R.id.icon, View.GONE)
            setViewVisibility(R.id.title, View.GONE)
            setViewVisibility(R.id.value, View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            setOnClickPendingIntent(R.id.root, openWeather(ctx, id))
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        super.onUpdate(ctx, mgr, ids)
        com.umutk.pulse.extras.weather.WeatherAuto.ensure(ctx)
        val place = WeatherStore.savedPlace(ctx) ?: return
        val old = WeatherStore.cached(ctx)
        if (old != null && System.currentTimeMillis() - old.at < MAX_AGE_MS) return
        val pending = goAsync()
        Thread {
            try {
                val raw = WeatherApi.forecast(place.lat, place.lon)
                WeatherStore.saveCache(ctx, place.name, raw, old?.aqi ?: -1, WeatherApi.extras(place.lat, place.lon, WeatherStore.source(ctx) == "mgm", WeatherStore.combine(ctx)))
                WeatherWidgets.refreshAll(ctx)
            } catch (e: Exception) {
                // keep the saved copy on screen; the next update tries again
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun openWeather(c: Context, id: Int): PendingIntent {
        val i = Intent().setComponent(ComponentName(c, "com.umutk.pulse.ExtraToolActivity")).putExtra("route", "weather").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(c, 9100 + (id and 0xFF), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val MAX_AGE_MS = 60 * 60_000L
    }
}

/** Weather, small size (2x2). */
class WeatherWidget : WeatherWidgetBase(wide = false)

/** Weather, wide size (4x2), with the next 12 hours. */
class WeatherWideWidget : WeatherWidgetBase(wide = true)
