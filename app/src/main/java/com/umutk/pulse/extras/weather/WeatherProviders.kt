package com.umutk.pulse.extras.weather

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Other weather services than Open-Meteo, for a phone where that one does not answer. Each one reads its own answer and writes it again in the shape of an Open-Meteo
 * forecast, so everything that draws the weather (the page, the widgets, the alerts) works on it without knowing which service it came from.
 */
object WeatherProviders {
    private class Hr(val t: LocalDateTime, val temp: Double, val code: Int, val rain: Int)
    private class Now(val t: LocalDateTime, val temp: Double, val feels: Double, val hum: Int, val code: Int, val wind: Double, val dir: Int, val press: Double, val cloud: Int, val precip: Double)

    private fun fmt(t: LocalDateTime) = t.toString().take(16).let { if (it.length < 16) it + ":00" else it }

    /** Sunrise and sunset of [date] at the place, as local "yyyy-MM-ddTHH:mm" (the sunrise equation; both the same when the sun does not rise or set). */
    fun sunTimes(lat: Double, lon: Double, date: LocalDate, zone: ZoneId): Pair<String, String> {
        val rad = PI / 180.0
        val jd = date.toEpochDay() + 2440587.5 + 0.5
        val n = Math.ceil(jd - 2451545.0 + 0.0008)
        val jStar = n + lon / 360.0
        val m = (357.5291 + 0.98560028 * jStar) % 360.0
        val c = 1.9148 * sin(m * rad) + 0.02 * sin(2 * m * rad) + 0.0003 * sin(3 * m * rad)
        val lam = (m + c + 180.0 + 102.9372) % 360.0
        val transit = 2451545.0 + jStar + 0.0053 * sin(m * rad) - 0.0069 * sin(2 * lam * rad)
        val delta = asin(sin(lam * rad) * sin(23.4397 * rad))
        val cosW = ((sin(-0.833 * rad) - sin(lat * rad) * sin(delta)) / (cos(lat * rad) * cos(delta))).coerceIn(-1.0, 1.0)
        val w = acos(cosW) / rad
        fun at(j: Double) = fmt(Instant.ofEpochMilli(((j - 2440587.5) * 86_400_000.0).toLong()).atZone(zone).toLocalDateTime())
        return at(transit - w / 360.0) to at(transit + w / 360.0)
    }

    private fun build(now: Now, hours: List<Hr>, lat: Double, lon: Double, zone: ZoneId): String {
        val ht = JSONArray(); val hc = JSONArray(); val hT = JSONArray(); val hp = JSONArray()
        hours.forEach { ht.put(fmt(it.t)); hc.put(it.code); hT.put(it.temp); hp.put(it.rain) }
        // days: the extremes of the hours, the sky of the middle of the day, the highest chance of rain
        val days = hours.groupBy { it.t.toLocalDate() }.toSortedMap()
        val dt = JSONArray(); val dc = JSONArray(); val dmax = JSONArray(); val dmin = JSONArray(); val dr = JSONArray(); val du = JSONArray(); val dsr = JSONArray(); val dss = JSONArray()
        days.forEach { (d, hs) ->
            val mid = hs.minByOrNull { Math.abs(it.t.hour - 13) }!!
            val (sr, ss) = sunTimes(lat, lon, d, zone)
            dt.put(d.toString()); dc.put(mid.code); dmax.put(hs.maxOf { it.temp }); dmin.put(hs.minOf { it.temp }); dr.put(hs.maxOf { it.rain }); du.put(0.0); dsr.put(sr); dss.put(ss)
        }
        val (sr, ss) = sunTimes(lat, lon, now.t.toLocalDate(), zone)
        val day = fmt(now.t) >= sr && fmt(now.t) <= ss
        val cur = JSONObject().put("time", fmt(now.t)).put("temperature_2m", now.temp).put("apparent_temperature", now.feels).put("relative_humidity_2m", now.hum)
            .put("weather_code", now.code).put("wind_speed_10m", now.wind).put("wind_direction_10m", now.dir).put("surface_pressure", now.press).put("cloud_cover", now.cloud)
            .put("is_day", if (day) 1 else 0).put("precipitation", now.precip)
        return JSONObject().put("current", cur)
            .put("hourly", JSONObject().put("time", ht).put("weather_code", hc).put("temperature_2m", hT).put("precipitation_probability", hp))
            .put("daily", JSONObject().put("time", dt).put("weather_code", dc).put("temperature_2m_max", dmax).put("temperature_2m_min", dmin)
                .put("precipitation_probability_max", dr).put("uv_index_max", du).put("sunrise", dsr).put("sunset", dss)).toString()
    }

    // ---- MET Norway: no key -----------------------------------------------------------------------------------------------------------------
    fun metno(lat: Double, lon: Double): String {
        val body = WeatherApi.get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f".format(java.util.Locale.US, lat, lon))
        val ts = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
        val zone = ZoneId.systemDefault()
        val hours = ArrayList<Hr>(); var now: Now? = null
        for (i in 0 until ts.length()) {
            val e = ts.getJSONObject(i)
            val t = Instant.parse(e.getString("time")).atZone(zone).toLocalDateTime()
            val data = e.getJSONObject("data"); val det = data.getJSONObject("instant").getJSONObject("details")
            val temp = det.optDouble("air_temperature", Double.NaN)
            if (temp.isNaN()) continue
            // an hour, else the six hours that follow: the sky and the rain of the slot
            val nx = data.optJSONObject("next_1_hours") ?: data.optJSONObject("next_6_hours")
            val code = nx?.optJSONObject("summary")?.optString("symbol_code")?.let { noaa(it) } ?: 3
            val mm = nx?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0) ?: 0.0
            val rain = if (mm <= 0.0) (if (code >= 51) 40 else 0) else (30 + mm * 35).toInt().coerceAtMost(100)
            if (now == null) now = Now(t, temp, temp, det.optDouble("relative_humidity", 50.0).toInt(), code, det.optDouble("wind_speed", 0.0) * 3.6,
                det.optDouble("wind_from_direction", 0.0).toInt(), det.optDouble("air_pressure_at_sea_level", 0.0), det.optDouble("cloud_area_fraction", 0.0).toInt(), mm)
            hours += Hr(t, temp, code, rain)
        }
        val n = now ?: error("MET Norway sent no readings")
        return build(n, hours.take(120), lat, lon, zone)
    }

    private fun noaa(sym: String): Int = when {
        sym.contains("thunder") -> 95
        sym.contains("heavysnow") -> 75
        sym.contains("snow") -> 73
        sym.contains("sleet") -> 67
        sym.contains("heavyrain") -> 65
        sym.contains("rain") -> 63
        sym.contains("fog") -> 45
        sym.startsWith("cloudy") -> 3
        sym.startsWith("partlycloudy") -> 2
        sym.startsWith("fair") -> 1
        else -> 0
    }

    // ---- OpenWeatherMap: the user's key (the free plan) -------------------------------------------------------------------------------------
    fun owm(lat: Double, lon: Double, key: String): String {
        require(key.isNotBlank()) { "Enter the OpenWeatherMap key in the weather settings" }
        val q = "lat=%.4f&lon=%.4f&units=metric&appid=%s".format(java.util.Locale.US, lat, lon, key)
        val cur = JSONObject(WeatherApi.get("https://api.openweathermap.org/data/2.5/weather?$q"))
        val fc = JSONObject(WeatherApi.get("https://api.openweathermap.org/data/2.5/forecast?$q"))
        val off = cur.optInt("timezone", 0)
        val zone = ZoneOffset.ofTotalSeconds(off)
        fun local(sec: Long) = Instant.ofEpochSecond(sec).atZone(zone).toLocalDateTime()
        val main = cur.getJSONObject("main"); val wind = cur.optJSONObject("wind")
        val now = Now(local(cur.getLong("dt")), main.getDouble("temp"), main.optDouble("feels_like", main.getDouble("temp")), main.optInt("humidity", 50),
            owmCode(cur.getJSONArray("weather").getJSONObject(0).getInt("id")), (wind?.optDouble("speed", 0.0) ?: 0.0) * 3.6, wind?.optInt("deg", 0) ?: 0,
            main.optDouble("pressure", 0.0), cur.optJSONObject("clouds")?.optInt("all", 0) ?: 0, cur.optJSONObject("rain")?.optDouble("1h", 0.0) ?: 0.0)
        val list = fc.getJSONArray("list")
        val hours = ArrayList<Hr>()
        for (i in 0 until list.length()) {
            val e = list.getJSONObject(i)
            hours += Hr(local(e.getLong("dt")), e.getJSONObject("main").getDouble("temp"), owmCode(e.getJSONArray("weather").getJSONObject(0).getInt("id")), (e.optDouble("pop", 0.0) * 100).toInt())
        }
        return build(now, hours, lat, lon, zone)
    }

    /** OpenWeatherMap's condition number as the WMO code the page draws. */
    private fun owmCode(id: Int): Int = when (id) {
        in 200..232 -> 95
        in 300..321 -> 53
        500, 501 -> 61
        in 502..504 -> 65
        511 -> 67
        in 520..531 -> 81
        600 -> 71; 601 -> 73; in 602..622 -> 75
        in 701..781 -> 45
        800 -> 0; 801 -> 1; 802 -> 2; in 803..804 -> 3
        else -> 3
    }
}
