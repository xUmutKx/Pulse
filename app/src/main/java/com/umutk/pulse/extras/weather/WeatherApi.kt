package com.umutk.pulse.extras.weather

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.umutk.pulse.extras.store.openHttps
import com.umutk.pulse.tr
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

/** A place to show weather for. [device] means "wherever the phone is now". */
data class Place(val name: String, val lat: Double, val lon: Double, val device: Boolean = false)

/** One day: range, rain chance, UV, sunrise and sunset ("yyyy-MM-ddTHH:mm"), sunshine in hours. */
data class Day(val date: String, val code: Int, val max: Double, val min: Double, val rain: Int, val uv: Double, val sunrise: String, val sunset: String, val sun: Double = 0.0)

/** One hour: local time "yyyy-MM-ddTHH:00", condition, temperature (C), chance of rain (%). */
data class Hour(val time: String, val code: Int, val temp: Double, val rain: Int)

/** Now, the next 24 hours and the next 7 days. Values are metric (C, km/h, hPa); the screens convert. */
data class Forecast(
    val place: String, val at: Long, val time: String, val temp: Double, val feels: Double, val code: Int,
    val wind: Double, val gusts: Double, val windDir: Int, val humidity: Int, val pressure: Double, val cloud: Int, val isDay: Boolean,
    val days: List<Day>, val hours: List<Hour>, val aqi: Int = -1, val precip: Double = 0.0,
)

/** Open-Meteo: forecast, air quality and geocoding, no key. Same HTTPS helper as the store. */
object WeatherApi {
    private fun lang() = tr("en", "tr")

    fun forecast(lat: Double, lon: Double): String {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(java.util.Locale.US, lat, lon) +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m,surface_pressure,cloud_cover,is_day,precipitation" +
            "&hourly=temperature_2m,weather_code,precipitation_probability&forecast_hours=48" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,sunrise,sunset,sunshine_duration" +
            "&forecast_days=7&timezone=auto&wind_speed_unit=kmh"
        return get(url)
    }

    /** European air quality index for now, -1 when the service has no value for the place. */
    fun airQuality(lat: Double, lon: Double): Int = runCatching {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=european_aqi".format(java.util.Locale.US, lat, lon)
        JSONObject(get(url)).getJSONObject("current").optInt("european_aqi", -1)
    }.getOrDefault(-1)

    /** City search; the label is "Name, region, country". Empty when nothing matches. */
    fun search(query: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=5&format=json&language=${lang()}"
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val label = listOf(o.optString("name"), o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            Place(label, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    private fun get(url: String): String {
        val cn = openHttps(url)
        try {
            val code = cn.responseCode
            check(code == 200) { "HTTP $code" }
            return cn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            cn.disconnect()
        }
    }

    /** Reads the forecast JSON from the cache or a fresh response. Throws on a malformed body. */
    fun parse(place: String, raw: String, at: Long, aqi: Int = -1): Forecast {
        val j = JSONObject(raw)
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val dc = d.getJSONArray("weather_code"); val dmax = d.getJSONArray("temperature_2m_max"); val dmin = d.getJSONArray("temperature_2m_min")
        val drain = d.getJSONArray("precipitation_probability_max"); val duv = d.getJSONArray("uv_index_max")
        val dsr = d.getJSONArray("sunrise"); val dss = d.getJSONArray("sunset")
        val dsun = d.optJSONArray("sunshine_duration")
        val days = (0 until dt.length()).map {
            Day(dt.getString(it), dc.getInt(it), dmax.optDouble(it, 0.0), dmin.optDouble(it, 0.0), drain.optInt(it, 0), duv.optDouble(it, 0.0),
                dsr.optString(it), dss.optString(it), (dsun?.optDouble(it, 0.0) ?: 0.0) / 3600.0)
        }
        // the hourly list starts at midnight, so begin at the current hour and keep 24 hours
        val h = j.getJSONObject("hourly")
        val ht = h.getJSONArray("time"); val hc = h.getJSONArray("weather_code"); val hT = h.getJSONArray("temperature_2m"); val hp = h.getJSONArray("precipitation_probability")
        val nowHour = cur.getString("time").take(13) + ":00"
        var start = 0
        while (start < ht.length() && ht.getString(start) < nowHour) start++
        val hours = (start until minOf(start + 24, ht.length())).map { Hour(ht.getString(it), hc.getInt(it), hT.optDouble(it, 0.0), hp.optInt(it, 0)) }
        return Forecast(
            place, at, cur.getString("time"), cur.getDouble("temperature_2m"), cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            cur.getInt("weather_code"), cur.getDouble("wind_speed_10m"), cur.optDouble("wind_gusts_10m", 0.0), cur.optInt("wind_direction_10m", 0),
            cur.getInt("relative_humidity_2m"), cur.optDouble("surface_pressure", 0.0), cur.optInt("cloud_cover", 0), cur.optInt("is_day", 1) == 1,
            days, hours, aqi, cur.optDouble("precipitation", 0.0),
        )
    }

    /** WMO weather code to a short English / Turkish text. */
    fun condition(code: Int): String = when (code) {
        0 -> tr("Clear", "Açık")
        1 -> tr("Mainly clear", "Çoğunlukla açık")
        2 -> tr("Partly cloudy", "Parçalı bulutlu")
        3 -> tr("Overcast", "Kapalı")
        45, 48 -> tr("Fog", "Sis")
        51, 53, 55, 56, 57 -> tr("Drizzle", "Çisenti")
        61, 63, 65, 66, 67, 80, 81, 82 -> tr("Rain", "Yağmur")
        71, 73, 75, 77, 85, 86 -> tr("Snow", "Kar")
        95, 96, 99 -> tr("Thunderstorm", "Gök gürültülü fırtına")
        else -> tr("Unknown", "Bilinmiyor")
    }

    /** Icon key for PIcon: sun, cloud or bolt. */
    fun icon(code: Int): String = when (code) {
        0, 1 -> "light"
        95, 96, 99 -> "bolt"
        else -> "cloud"
    }

    /** Vector drawable for the home-screen widgets, same three looks as [icon]. */
    fun drawable(code: Int): Int = when (code) {
        0, 1 -> com.umutk.pulse.R.drawable.wi_weather_sun
        95, 96, 99 -> com.umutk.pulse.R.drawable.wi_weather_bolt
        else -> com.umutk.pulse.R.drawable.wi_weather_cloud
    }

    /** Codes that count as severe for the alert: thunderstorm, heavy snow or heavy rain. */
    fun severe(code: Int) = code in setOf(65, 75, 82, 86, 95, 96, 99)
}

/** Units, labels and small calculations the screens and widgets share. */
object WeatherFmt {
    fun imperial(c: Context) = WeatherStore.units(c) == "f"

    /** Temperature in the chosen unit, rounded, with the degree sign. */
    fun temp(v: Double, f: Boolean): String = "%.0f°".format(if (f) v * 9 / 5 + 32 else v)

    fun wind(v: Double, f: Boolean): String = if (f) "%.0f mph".format(v * 0.621371) else "%.0f km/h".format(v)

    /** 16-point compass name of a wind direction in degrees. */
    fun compass(deg: Int): String {
        val en = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val trk = listOf("K", "KKD", "KD", "DKD", "D", "DGD", "GD", "GGD", "G", "GGB", "GB", "BGB", "B", "BKB", "KB", "KKB")
        val i = ((deg % 360) / 22.5 + 0.5).toInt() % 16
        return tr(en[i], trk[i])
    }

    fun aqi(v: Int): String = when {
        v < 0 -> tr("No data", "Veri yok")
        v <= 20 -> tr("Good", "İyi")
        v <= 40 -> tr("Fair", "Orta")
        v <= 60 -> tr("Moderate", "Hassas")
        v <= 80 -> tr("Poor", "Kötü")
        v <= 100 -> tr("Very poor", "Çok kötü")
        else -> tr("Extremely poor", "Aşırı kötü")
    }

    fun uv(v: Double): String = when {
        v < 3 -> tr("Low", "Düşük")
        v < 6 -> tr("Moderate", "Orta")
        v < 8 -> tr("High", "Yüksek")
        v < 11 -> tr("Very high", "Çok yüksek")
        else -> tr("Extreme", "Aşırı")
    }

    /** "HH:mm" part of an Open-Meteo local time. */
    fun clock(t: String): String = if (t.length >= 16) t.substring(11, 16) else t

    /** Hours and minutes between two Open-Meteo local times, e.g. "10h 24m". */
    fun dayLength(sunrise: String, sunset: String): String = runCatching {
        val m = java.time.Duration.between(java.time.LocalDateTime.parse(sunrise), java.time.LocalDateTime.parse(sunset)).toMinutes()
        "%dh %02dm".format(m / 60, m % 60)
    }.getOrDefault("")

    const val SYNODIC = 29.530588853

    /** Days into the synodic month, counted from a known new moon (6 Jan 2000, 18:14 UTC). */
    fun moonAge(nowMs: Long = System.currentTimeMillis()): Double {
        val days = (nowMs - 947182440000L) / 86_400_000.0
        return ((days % SYNODIC) + SYNODIC) % SYNODIC
    }

    /** Moon phase name for [nowMs]. */
    fun moon(nowMs: Long = System.currentTimeMillis()): String {
        val age = moonAge(nowMs)
        return when (((age / SYNODIC) * 8 + 0.5).toInt() % 8) {
            0 -> tr("New moon", "Yeni ay")
            1 -> tr("Waxing crescent", "Büyüyen hilal")
            2 -> tr("First quarter", "İlk dördün")
            3 -> tr("Waxing gibbous", "Büyüyen şişkin ay")
            4 -> tr("Full moon", "Dolunay")
            5 -> tr("Waning gibbous", "Küçülen şişkin ay")
            6 -> tr("Last quarter", "Son dördün")
            else -> tr("Waning crescent", "Küçülen hilal")
        }
    }
}

/** Last place, cities, units and forecast; SharedPreferences, read and written only by this feature. */
object WeatherStore {
    private fun p(c: Context) = c.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun savedPlace(c: Context): Place? {
        val s = p(c)
        if (!s.contains("lat")) return null
        return Place(s.getString("name", "").orEmpty(), s.getFloat("lat", 0f).toDouble(), s.getFloat("lon", 0f).toDouble(), s.getBoolean("device", false))
    }

    fun setPlace(c: Context, pl: Place) {
        p(c).edit().putString("name", pl.name).putFloat("lat", pl.lat.toFloat()).putFloat("lon", pl.lon.toFloat()).putBoolean("device", pl.device).apply()
    }

    /** "c" or "f". */
    fun units(c: Context): String = p(c).getString("units", "c") ?: "c"
    /** How the weather page looks: "sky" (a sky that follows the weather), "amoled" (black) or "app" (the app look). */
    fun skin(c: Context): String = p(c).getString("skin", "sky") ?: "sky"
    fun setSkin(c: Context, v: String) = p(c).edit().putString("skin", v).apply()

    fun setUnits(c: Context, u: String) = p(c).edit().putString("units", u).apply()

    /** Saved cities (not the phone's place), as a JSON array in SharedPreferences. */
    fun cities(c: Context): List<Place> = runCatching {
        val arr = JSONArray(p(c).getString("cities", "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Place(it.getString("name"), it.getDouble("lat"), it.getDouble("lon")) } }
    }.getOrDefault(emptyList())

    private fun saveCities(c: Context, list: List<Place>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon)) }
        p(c).edit().putString("cities", arr.toString()).apply()
    }

    fun addCity(c: Context, pl: Place) {
        val all = cities(c).filterNot { it.lat == pl.lat && it.lon == pl.lon }
        saveCities(c, all + Place(pl.name, pl.lat, pl.lon))
    }

    fun removeCity(c: Context, pl: Place) = saveCities(c, cities(c).filterNot { it.lat == pl.lat && it.lon == pl.lon })

    fun saveCache(c: Context, place: String, raw: String, aqi: Int) {
        p(c).edit().putString("raw", raw).putString("cache_place", place).putInt("aqi", aqi).putLong("at", System.currentTimeMillis()).apply()
    }

    /** The last forecast shown, or null when none was saved or the saved copy is unreadable. */
    fun cached(c: Context): Forecast? {
        val s = p(c)
        val raw = s.getString("raw", null) ?: return null
        return runCatching { WeatherApi.parse(s.getString("cache_place", "").orEmpty(), raw, s.getLong("at", 0L), s.getInt("aqi", -1)) }.getOrNull()
    }

    /** Hour of the last severe-weather notice, so one storm is announced once. */
    fun lastAlert(c: Context): String = p(c).getString("alert_hour", "") ?: ""

    fun setLastAlert(c: Context, hour: String) = p(c).edit().putString("alert_hour", hour).apply()

    fun alertsOn(c: Context): Boolean = p(c).getBoolean("alerts", false)

    /** Off by default: alerts need a background refresh, which only runs when the user turns them on. */
    fun setAlertsOn(c: Context, on: Boolean) = p(c).edit().putBoolean("alerts", on).apply()
}

/** Where the phone is: a fresh network fix (30 s at most), else the last known one. Needs ACCESS_COARSE_LOCATION. */
@SuppressLint("MissingPermission")
suspend fun deviceLocation(c: Context): Location? {
    if (ContextCompat.checkSelfPermission(c, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val lm = c.getSystemService(LocationManager::class.java)
    val fresh = if (android.os.Build.VERSION.SDK_INT < 30) null else withTimeoutOrNull(30_000) {
        suspendCancellableCoroutine<Location?> { k ->
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, c.mainExecutor) { k.resume(it) }
        }
    }
    return fresh ?: listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}
