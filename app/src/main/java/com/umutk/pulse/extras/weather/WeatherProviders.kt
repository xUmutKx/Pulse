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

    /**
     * Every service gives its points at its own spacing: wttr, OpenWeatherMap and 7Timer every three hours, MET Norway every hour and then every six. The page, the widgets
     * and the alerts take "the next 24 (or 12) hours" as that many list entries, so a three-hourly service showed three times the span. The list is made hourly here
     * (the temperature in a straight line between two points, the sky of the nearer point, the chance of rain of the earlier one), whichever service answered.
     */
    private fun hourly(list: List<Hr>): List<Hr> {
        val s = list.sortedBy { it.t }.distinctBy { it.t }
        val out = ArrayList<Hr>()
        for (i in s.indices) {
            val a = s[i]
            out += a
            val b = s.getOrNull(i + 1) ?: break
            val gap = java.time.Duration.between(a.t, b.t).toHours().toInt()
            if (gap in 2..12) for (k in 1 until gap) out += Hr(a.t.plusHours(k.toLong()), a.temp + (b.temp - a.temp) * k / gap, if (k * 2 > gap) b.code else a.code, a.rain)
        }
        return out
    }

    private fun build(now: Now, hoursIn: List<Hr>, lat: Double, lon: Double, zone: ZoneId): String {
        val hours = hourly(hoursIn)
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

    // ---- wttr.in: no key, worldwide (the j1 report: the current conditions and three days of hours) -------------------------------------
    /** The wttr.in weather codes (World Weather Online) as the forecast codes the app shows. */
    private fun wttrCode(c: Int): Int = when (c) {
        113 -> 0
        116 -> 2
        119, 122 -> 3
        143, 248, 260 -> 45
        200, 386, 389, 392, 395 -> 95
        176, 263, 266, 293, 296, 353 -> 61
        299, 302, 305, 308, 356, 359 -> 63
        179, 182, 227, 230, 317, 320, 323, 326, 329, 332, 335, 338, 350, 368, 371, 374, 377 -> 73
        else -> 3
    }

    fun wttr(lat: Double, lon: Double): String {
        val j = JSONObject(WeatherApi.get("https://wttr.in/%.4f,%.4f?format=j1".format(java.util.Locale.US, lat, lon)))
        val zone = ZoneId.systemDefault()
        val c = j.getJSONArray("current_condition").getJSONObject(0)
        val now = Now(LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0),
            c.getString("temp_C").toDouble(), c.getString("FeelsLikeC").toDouble(), c.getString("humidity").toInt(), wttrCode(c.getString("weatherCode").toInt()),
            c.getString("windspeedKmph").toDouble(), c.getString("winddirDegree").toInt(), c.getString("pressure").toDouble(),
            c.getString("cloudcover").toInt(), c.getString("precipMM").toDouble())
        val hours = ArrayList<Hr>()
        val days = j.getJSONArray("weather")
        for (d in 0 until days.length()) {
            val day = days.getJSONObject(d)
            val date = LocalDate.parse(day.getString("date"))
            val hs = day.getJSONArray("hourly")
            for (h in 0 until hs.length()) {
                val o = hs.getJSONObject(h)
                // the time is HHMM as a number: "0", "300", "1500"
                val hour = o.getString("time").toInt() / 100
                hours += Hr(date.atTime(hour, 0), o.getString("tempC").toDouble(), wttrCode(o.getString("weatherCode").toInt()), o.getString("chanceofrain").toInt())
            }
        }
        return build(now, hours.take(120), lat, lon, zone)
    }

    // ---- Bright Sky (the German weather service DWD): no key. Stations and the ICON model; hourly for about ten days ----
    /** The sky of a "icon" word (Bright Sky, Pirate Weather) as the WMO code the page draws; without a word the cloud cover decides. */
    private fun skyIcon(icon: String, cloud: Int, mm: Double): Int = when {
        icon.startsWith("thunder") -> 95
        icon == "hail" -> 77
        icon == "snow" -> 73
        icon == "sleet" -> 67
        icon == "rain" -> if (mm >= 4.0) 65 else if (mm >= 1.0) 63 else 61
        icon == "fog" -> 45
        icon.startsWith("partly") -> 2
        icon == "cloudy" -> 3
        icon.startsWith("clear") -> 0
        cloud <= 20 -> 0
        cloud <= 60 -> 2
        else -> 3
    }

    fun brightsky(lat: Double, lon: Double): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val arr = JSONObject(WeatherApi.get("https://api.brightsky.dev/weather?lat=%.4f&lon=%.4f&date=%s&last_date=%s&tz=%s".format(java.util.Locale.US, lat, lon, today, today.plusDays(9), zone.id))).getJSONArray("weather")
        val nowT = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
        val hours = ArrayList<Hr>(); var now: Now? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = java.time.OffsetDateTime.parse(o.getString("timestamp")).atZoneSameInstant(zone).toLocalDateTime()
            val temp = o.optDouble("temperature", Double.NaN)
            if (temp.isNaN()) continue
            val cloud = o.optInt("cloud_cover", 50); val mm = o.optDouble("precipitation", 0.0)
            val code = skyIcon(if (o.isNull("icon")) "" else o.optString("icon"), cloud, mm)
            val rain = if (!o.isNull("precipitation_probability")) o.optInt("precipitation_probability", 0) else if (mm > 0.0) 60 else 0
            if (t.isAfter(nowT)) { hours += Hr(t, temp, code, rain); continue }
            // the latest record that is not in the future is "now"; it also starts the list
            now = Now(nowT, temp, temp, o.optInt("relative_humidity", 60), code, o.optDouble("wind_speed", 0.0), o.optInt("wind_direction", 0), o.optDouble("pressure_msl", 0.0), cloud, mm)
            hours.clear(); hours += Hr(nowT, temp, code, rain)
        }
        val n = now ?: hours.firstOrNull()?.let { h -> Now(nowT, h.temp, h.temp, 60, h.code, 0.0, 0, 0.0, 50, 0.0) } ?: error("Bright Sky sent no readings")
        return build(n, hours, lat, lon, zone)
    }

    // ---- WeatherAPI.com: the user's key (the free plan: three days) ----
    private fun wapiCode(c: Int): Int = when (c) {
        1000 -> 0; 1003 -> 2; 1006, 1009 -> 3; 1030, 1135, 1147 -> 45
        1063, 1150, 1153, 1180, 1183, 1240 -> 61; 1186, 1189, 1243 -> 63; 1192, 1195, 1246 -> 65
        1066, 1210, 1213, 1216, 1219, 1255 -> 73; 1114, 1117, 1222, 1225, 1258 -> 75; 1237, 1261, 1264 -> 77
        1069, 1072, 1168, 1171, 1198, 1201, 1204, 1207, 1249, 1252 -> 67
        1087, 1273, 1276, 1279, 1282 -> 95
        else -> 3
    }

    fun wapi(lat: Double, lon: Double, key: String): String {
        require(key.isNotBlank()) { "Enter the WeatherAPI.com key in the weather settings" }
        val j = JSONObject(WeatherApi.get("https://api.weatherapi.com/v1/forecast.json?key=$key&q=%.4f,%.4f&days=10&aqi=no&alerts=no".format(java.util.Locale.US, lat, lon)))
        val zone = runCatching { ZoneId.of(j.getJSONObject("location").getString("tz_id")) }.getOrDefault(ZoneId.systemDefault())
        val f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val cur = j.getJSONObject("current")
        val nowT = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
        val now = Now(nowT, cur.getDouble("temp_c"), cur.optDouble("feelslike_c", cur.getDouble("temp_c")), cur.optInt("humidity", 50), wapiCode(cur.getJSONObject("condition").optInt("code", 1009)),
            cur.optDouble("wind_kph", 0.0), cur.optInt("wind_degree", 0), cur.optDouble("pressure_mb", 0.0), cur.optInt("cloud", 0), cur.optDouble("precip_mm", 0.0))
        val hours = ArrayList<Hr>()
        val days = j.getJSONObject("forecast").getJSONArray("forecastday")
        for (d in 0 until days.length()) {
            val hs = days.getJSONObject(d).getJSONArray("hour")
            for (h in 0 until hs.length()) {
                val o = hs.getJSONObject(h)
                val t = LocalDateTime.parse(o.getString("time"), f)
                if (t.isBefore(nowT)) continue
                hours += Hr(t, o.getDouble("temp_c"), wapiCode(o.getJSONObject("condition").optInt("code", 1009)), o.optInt("chance_of_rain", 0))
            }
        }
        return build(now, hours, lat, lon, zone)
    }

    // ---- Pirate Weather: the user's key (the free plan: ten thousand calls a month) ----
    fun pirate(lat: Double, lon: Double, key: String): String {
        require(key.isNotBlank()) { "Enter the Pirate Weather key in the weather settings" }
        val j = JSONObject(WeatherApi.get("https://api.pirateweather.net/forecast/$key/%.4f,%.4f?units=si&extend=hourly&exclude=minutely,alerts".format(java.util.Locale.US, lat, lon)))
        val zone = runCatching { ZoneId.of(j.getString("timezone")) }.getOrDefault(ZoneId.systemDefault())
        fun local(sec: Long) = Instant.ofEpochSecond(sec).atZone(zone).toLocalDateTime()
        val cur = j.getJSONObject("currently")
        val mm0 = cur.optDouble("precipIntensity", 0.0)
        val cloud0 = (cur.optDouble("cloudCover", 0.5) * 100).toInt()
        val nowT = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
        val now = Now(nowT, cur.getDouble("temperature"), cur.optDouble("apparentTemperature", cur.getDouble("temperature")), (cur.optDouble("humidity", 0.5) * 100).toInt(),
            skyIcon(cur.optString("icon"), cloud0, mm0), cur.optDouble("windSpeed", 0.0) * 3.6, cur.optInt("windBearing", 0), cur.optDouble("pressure", 0.0), cloud0, mm0)
        val hs = j.getJSONObject("hourly").getJSONArray("data")
        val hours = ArrayList<Hr>()
        for (i in 0 until hs.length()) {
            val o = hs.getJSONObject(i)
            val t = local(o.getLong("time"))
            if (t.isBefore(nowT)) continue
            hours += Hr(t, o.getDouble("temperature"), skyIcon(o.optString("icon"), (o.optDouble("cloudCover", 0.5) * 100).toInt(), o.optDouble("precipIntensity", 0.0)), (o.optDouble("precipProbability", 0.0) * 100).toInt())
        }
        return build(now, hours, lat, lon, zone)
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
