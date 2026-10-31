package com.umutk.pulse.extras.weather

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.R
import com.umutk.pulse.tr
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Locale

/**
 * The weather as a pop-up notification (heads-up), with the sky icons and bold figures:
 * - every six hours, or at once when rain is coming in the next two hours: now, then the next three hours
 * - every twelve hours: the five days ahead, one line each with its icon, the high, the low and the chance of rain
 * Called after every forecast refresh (WeatherAuto); on by default, off in the weather settings.
 */
object WeatherReport {
    // a channel of its own: the one before was quiet, so its notifications never popped up
    private const val CHANNEL = "pulse_weather_pop"
    private const val ID = 4120
    private const val ID_DAYS = 4121
    private const val SIX_HOURS = 6L * 3600_000L
    private const val TWELVE_HOURS = 12L * 3600_000L
    private const val RAIN_CHANCE = 60
    private const val HOURS_SHOWN = 3
    private const val DAYS_SHOWN = 5

    private fun p(c: Context) = c.getSharedPreferences("weather_report", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("on", true)
    fun setEnabled(c: Context, on: Boolean) { p(c).edit().putBoolean("on", on).apply() }

    /** Posts the hour report and/or the five-day report when they are due. */
    fun maybePost(c: Context) {
        if (!enabled(c)) return
        val f = WeatherStore.cached(c) ?: return
        val sp = p(c)
        val now = System.currentTimeMillis()
        val rainSoon = f.hours.take(2).firstOrNull { it.rain >= RAIN_CHANCE }
        val rainDue = rainSoon != null && now - sp.getLong("rainAt", 0L) > 3 * 3600_000L
        val timeDue = now - sp.getLong("at", 0L) >= SIX_HOURS
        if (rainDue || timeDue) {
            val warn = if (rainDue) tr("Rain likely at ${clock(rainSoon!!.time)} (${rainSoon.rain}%)", "Yağmur bekleniyor, ${clock(rainSoon.time)} (%${rainSoon.rain})") else ""
            val body = SpannableStringBuilder()
            // now: the big figure in bold, the sky's word after it
            bold(body, "%.0f°".format(f.temp))
            body.append("  ").append(WeatherApi.condition(f.code))
            // the next hours, one under the other: time, icon, bold temperature, chance of rain
            f.hours.drop(1).take(HOURS_SHOWN).forEach { h ->
                body.append("\n").append("${clock(h.time)}  ${sign(h.code, h.time)}  ")
                bold(body, "%.0f°".format(h.temp))
                body.append("   ${h.rain}%")
            }
            if (warn.isNotEmpty()) body.append("\n").append(warn)
            post(c, ID, f.place, body, WeatherApi.drawable(f.code))
            sp.edit().putLong("at", now).apply()
            if (rainDue) sp.edit().putLong("rainAt", now).apply()
        }
        if (now - sp.getLong("days", 0L) >= TWELVE_HOURS && f.days.isNotEmpty()) {
            val body = SpannableStringBuilder()
            f.days.take(DAYS_SHOWN).forEachIndexed { i, d ->
                if (i > 0) body.append("\n")
                body.append("${dayName(d.date)}  ${sign(d.code, "")}  ")
                bold(body, "%.0f°".format(d.max))
                body.append(" / %.0f°   %d%%".format(d.min, d.rain))
            }
            post(c, ID_DAYS, tr("5-day forecast", "5 günlük tahmin"), body, WeatherApi.drawable(f.days.first().code))
            sp.edit().putLong("days", now).apply()
        }
    }

    private fun bold(sb: SpannableStringBuilder, s: String) {
        val start = sb.length
        sb.append(s)
        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun clock(t: String) = t.substringAfter('T', "").take(5)

    /**
     * A sky sign for a forecast code, as text symbols (U+FE0E asks for the plain text form, not the coloured emoji);
     * a clear hour at night gets the moon. [time] is an Open-Meteo local time, "" for a day.
     */
    private fun sign(code: Int, time: String): String {
        val hour = time.substringAfter('T', "").take(2).toIntOrNull() ?: 12
        val night = hour < 6 || hour >= 20
        return when (skyOf(code)) {
            Sky.CLEAR -> if (night) "☾" else "☀︎"
            Sky.PARTLY -> if (night) "☾☁︎" else "☀︎☁︎"
            Sky.CLOUD -> "☁︎"
            Sky.FOG -> "≋"
            Sky.DRIZZLE -> "˙☂︎"
            Sky.RAIN -> "☂︎"
            Sky.SNOW -> "❄︎"
            Sky.STORM -> "⚡︎"
        }
    }

    private fun dayName(date: String): String = runCatching {
        LocalDate.parse(date.take(10)).dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault())
    }.getOrDefault(date.take(10))

    private fun post(c: Context, id: Int, title: String, text: SpannableStringBuilder, iconRes: Int) {
        val nm = c.getSystemService(NotificationManager::class.java)
        // high importance so the notification pops up; no sound or vibration, and only the first post alerts
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Weather", "Hava durumu"), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) })
        val large = ContextCompat.getDrawable(c, iconRes)?.toBitmap(160, 160)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_ad_weather_fg)
            .setLargeIcon(large)
            .setContentTitle(title.ifEmpty { tr("Weather", "Hava durumu") })
            .setContentText(text.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
