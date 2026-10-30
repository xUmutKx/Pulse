package com.umutk.pulse.extras.weather

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.umutk.pulse.R
import com.umutk.pulse.tr

/**
 * The weather in the notification shade: one line every six hours, and at once when rain is coming in the next two hours.
 * Called after every forecast refresh (WeatherAuto); on by default, off in the weather settings.
 */
object WeatherReport {
    private const val CHANNEL = "pulse_weather"
    private const val ID = 4120
    private const val SIX_HOURS = 6L * 3600_000L
    private const val RAIN_CHANCE = 60

    private fun p(c: Context) = c.getSharedPreferences("weather_report", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("on", true)
    fun setEnabled(c: Context, on: Boolean) { p(c).edit().putBoolean("on", on).apply() }

    /** Posts the report when six hours have passed since the last one, or when rain is likely soon and no rain warning went out in the last three hours. */
    fun maybePost(c: Context) {
        if (!enabled(c)) return
        val f = WeatherStore.cached(c) ?: return
        val sp = p(c)
        val now = System.currentTimeMillis()
        val rainSoon = f.hours.take(2).firstOrNull { it.rain >= RAIN_CHANCE }
        val rainDue = rainSoon != null && now - sp.getLong("rainAt", 0L) > 3 * 3600_000L
        val timeDue = now - sp.getLong("at", 0L) >= SIX_HOURS
        if (!rainDue && !timeDue) return
        val warn = if (rainDue) tr("Rain likely at ${clock(rainSoon!!.time)} (${rainSoon.rain}%)", "Yağmur bekleniyor, ${clock(rainSoon.time)} (%${rainSoon.rain})") else ""
        val line = "%.0f°  ·  %s".format(f.temp, WeatherApi.condition(f.code))
        val text = if (warn.isEmpty()) line else "$line\n$warn"
        post(c, f.place, text)
        sp.edit().putLong("at", now).apply()
        if (rainDue) sp.edit().putLong("rainAt", now).apply()
    }

    private fun clock(t: String) = t.substringAfter('T', "").take(5)

    private fun post(c: Context, place: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Weather", "Hava durumu"), NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_ad_weather_fg)
            .setContentTitle(place.ifEmpty { tr("Weather", "Hava durumu") })
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        nm.notify(ID, n)
    }
}
