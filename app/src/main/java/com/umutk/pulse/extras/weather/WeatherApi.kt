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

/** Now, the next 24 hours (hours48 holds the next 96) and the next 16 days. Values are metric (C, km/h, hPa); the screens convert. */
data class Forecast(
    val place: String, val at: Long, val time: String, val temp: Double, val feels: Double, val code: Int,
    val wind: Double, val gusts: Double, val windDir: Int, val humidity: Int, val pressure: Double, val cloud: Int, val isDay: Boolean,
    val days: List<Day>, val hours: List<Hour>, val aqi: Int = -1, val precip: Double = 0.0,
    val dew: Double = 0.0, val visKm: Double = -1.0, val yMax: Double = Double.NaN, val yMin: Double = Double.NaN,
    val pollen: Map<String, Int> = emptyMap(), val hours48: List<Hour> = emptyList(), val viaMgm: Boolean = false, val sources: Int = 1,
)

/** Open-Meteo: forecast, air quality and geocoding, no key. Same HTTPS helper as the store. */
object WeatherApi {
    private fun lang() = tr("en", "tr")

    /** The context the stores were last opened with, so the plain calls below know which provider is chosen. */
    @Volatile var appCtx: android.content.Context? = null
    /** "om" and "mgm" read Open-Meteo; "metno" is MET Norway and "owm" OpenWeatherMap (with the user's key). */
    private fun provider(): String = appCtx?.let { WeatherStore.source(it) } ?: "om"
    private fun openMeteo() = provider().let { it == "om" || it == "mgm" }

    fun forecast(lat: Double, lon: Double): String = when (provider()) {
        "metno" -> WeatherProviders.metno(lat, lon)
        "owm" -> WeatherProviders.owm(lat, lon, appCtx?.let { WeatherStore.owmKey(it) }.orEmpty())
        else -> forecastOm(lat, lon)
    }

    private fun forecastOm(lat: Double, lon: Double): String {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(java.util.Locale.US, lat, lon) +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m,surface_pressure,cloud_cover,is_day,precipitation,dew_point_2m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,visibility&forecast_hours=96" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,sunrise,sunset,sunshine_duration" +
            "&forecast_days=16&timezone=auto&wind_speed_unit=kmh"
        // one more try after a pause (a busy or rate-limited service), then a smaller request without the optional readings, in case one of them is the one refused
        return runCatching { get(url) }.recoverCatching { Thread.sleep(800); get(url) }.recoverCatching {
            get("https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(java.util.Locale.US, lat, lon) +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure,cloud_cover,is_day,precipitation" +
                "&hourly=temperature_2m,weather_code,precipitation_probability&forecast_hours=96" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,sunrise,sunset&forecast_days=16&timezone=auto&wind_speed_unit=kmh")
        }.getOrThrow()
    }

    /** European air quality index for now, -1 when the service has no value for the place. */
    fun airQuality(lat: Double, lon: Double): Int = runCatching {
        if (!openMeteo()) return@runCatching -1
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=european_aqi".format(java.util.Locale.US, lat, lon)
        JSONObject(get(url)).getJSONObject("current").optInt("european_aqi", -1)
    }.getOrDefault(-1)

    /**
     * What the main forecast does not hold: yesterday's high and low, and the pollen counts (grains per cubic metre, Europe only) of now. A small JSON text for the cache;
     * "" when the service has nothing.
     */
    fun extras(lat: Double, lon: Double, mgm: Boolean = false, combine: Boolean = false): String = runCatching {
        if (!openMeteo()) return@runCatching ""
        val out = JSONObject()
        // MGM first: its hours are one more voice in the combined hours, not something the combined hours then paint over
        val m = if (mgm) runCatching { mgmOverlay(lat, lon) }.getOrNull() else null
        m?.let { out.put("mgm", it) }
        if (combine) runCatching { ensemble(lat, lon, m)?.let { out.put("ens", it) } }
        runCatching {
            val d = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=temperature_2m_max,temperature_2m_min&past_days=1&forecast_days=1&timezone=auto".format(java.util.Locale.US, lat, lon))).getJSONObject("daily")
            out.put("ymax", d.getJSONArray("temperature_2m_max").getDouble(0)).put("ymin", d.getJSONArray("temperature_2m_min").getDouble(0))
        }
        runCatching {
            val c = JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen&timezone=auto".format(java.util.Locale.US, lat, lon))).getJSONObject("current")
            val p = JSONObject()
            listOf("alder", "birch", "grass", "mugwort", "olive", "ragweed").forEach { k -> if (!c.isNull(k + "_pollen") && c.has(k + "_pollen")) p.put(k, c.getDouble(k + "_pollen").toInt()) }
            if (p.length() > 0) out.put("pollen", p)
        }
        out.toString()
    }.getOrDefault("")

    /** City search; the label is "Name, region, country". Empty when nothing matches. */
    fun search(query: String, ctx: android.content.Context? = null): List<Place> {
        val net = runCatching { searchNet(query) }.getOrDefault(emptyList())
        if (net.isNotEmpty() || ctx == null) return net
        // the web service did not answer: the phone's own geocoder, when it has one
        return runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(ctx, java.util.Locale.getDefault()).getFromLocationName(query.trim(), 5).orEmpty().map { a ->
                Place(listOf(a.locality ?: a.featureName, a.adminArea, a.countryName).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString(", "), a.latitude, a.longitude)
            }
        }.getOrDefault(emptyList())
    }

    private fun searchNet(query: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=5&format=json&language=${lang()}"
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val label = listOf(o.optString("name"), o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            Place(label, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    /** The last network failure in words (HTTP code or the exception), shown beside "could not load" so a failing phone says why. */
    @Volatile var lastError: String = ""

    internal fun get(url: String): String {
        val cn = openHttps(url)
        cn.setRequestProperty("User-Agent", "Pulse/1.0 (github.com/xUmutKx/Pulse)"); cn.setRequestProperty("Accept", "application/json")
        try {
            val code = try { cn.responseCode } catch (e: Exception) { lastError = e.javaClass.simpleName + ": " + (e.message ?: ""); throw e }
            if (code != 200) lastError = "HTTP $code " + url.substringAfter("://").substringBefore('/')
            check(code == 200) { "HTTP $code" }
            return cn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            cn.disconnect()
        }
    }

    /** Reads the forecast JSON from the cache or a fresh response. Throws on a malformed body. */
    fun parse(place: String, raw: String, at: Long, aqi: Int = -1, extras: String = ""): Forecast {
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
        val hv = h.optJSONArray("visibility")
        val hours48 = (start until minOf(start + 96, ht.length())).map { Hour(ht.getString(it), hc.getInt(it), hT.optDouble(it, 0.0), hp.optInt(it, 0)) }
        val ex = runCatching { JSONObject(extras) }.getOrNull()
        val pol = ex?.optJSONObject("pollen")?.let { po -> po.keys().asSequence().associateWith { k -> po.optInt(k, 0) } }.orEmpty()
        val base = Forecast(
            place, at, cur.getString("time"), cur.getDouble("temperature_2m"), cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            cur.getInt("weather_code"), cur.getDouble("wind_speed_10m"), cur.optDouble("wind_gusts_10m", 0.0), cur.optInt("wind_direction_10m", 0),
            cur.getInt("relative_humidity_2m"), cur.optDouble("surface_pressure", 0.0), cur.optInt("cloud_cover", 0), cur.optInt("is_day", 1) == 1,
            days, hours, aqi, cur.optDouble("precipitation", 0.0),
            cur.optDouble("dew_point_2m", 0.0), if (hv != null && start < hv.length() && !hv.isNull(start)) hv.optDouble(start, 0.0) / 1000.0 else -1.0,
            ex?.optDouble("ymax", Double.NaN) ?: Double.NaN, ex?.optDouble("ymin", Double.NaN) ?: Double.NaN, pol, hours48,
        )
        val withMgm = ex?.optJSONObject("mgm")?.let { runCatching { applyMgm(base, it) }.getOrNull() } ?: base
        return ex?.optJSONObject("ens")?.let { runCatching { applyEnsemble(withMgm, it) }.getOrNull() } ?: withMgm
    }

    // ---- Several forecasts for the next 24 hours -------------------------------------------------------------------------------------------------
    // Open-Meteo's own best guess, three more weather models (ECMWF, GFS, ICON, asked in one request) and met.no each give the hours. Per hour the temperature is the median
    // (a trimmed mean when there are four or more: one model that is far off cannot drag it), the sky is the one most sources agree on (the worse one when they tie, so a
    // storm is not dropped), the chance of rain is the average. The result replaces the hours; it is kept with the forecast as "ens".

    private fun noaaSky(sym: String): Int = when {
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
        sym.startsWith("clearsky") -> 0
        else -> -1
    }

    /** {hours: [{t, temp, code, rain, n}], sources}, or null when fewer than two sources answered. */
    fun ensemble(lat: Double, lon: Double, mgm: JSONObject? = null): JSONObject? {
        // time -> temps, codes, rain
        class Slot { val temps = ArrayList<Double>(); val codes = ArrayList<Int>(); val rain = ArrayList<Int>() }
        val slots = java.util.TreeMap<String, Slot>()
        var sources = 0
        runCatching {
            val j = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=temperature_2m,weather_code,precipitation_probability&models=best_match,ecmwf_ifs025,gfs_seamless,icon_seamless&forecast_hours=30&timezone=auto".format(java.util.Locale.US, lat, lon))).getJSONObject("hourly")
            val times = j.getJSONArray("time")
            val keys = j.keys().asSequence().toList()
            val tk = keys.filter { it.startsWith("temperature_2m") }
            sources += tk.size
            for (i in 0 until times.length()) {
                val sl = slots.getOrPut(times.getString(i)) { Slot() }
                for (k in tk) j.optJSONArray(k)?.takeIf { !it.isNull(i) }?.let { sl.temps += it.getDouble(i) }
                for (k in keys.filter { it.startsWith("weather_code") }) j.optJSONArray(k)?.takeIf { !it.isNull(i) }?.let { sl.codes += it.getInt(i) }
                for (k in keys.filter { it.startsWith("precipitation_probability") }) j.optJSONArray(k)?.takeIf { !it.isNull(i) }?.let { sl.rain += it.getInt(i) }
            }
        }
        runCatching {
            val cn = openHttps("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f".format(java.util.Locale.US, lat, lon))
            cn.setRequestProperty("User-Agent", "Pulse/1.0 github.com/xUmutKx/Pulse")
            val body = try { check(cn.responseCode == 200) { "HTTP ${cn.responseCode}" }; cn.inputStream.bufferedReader().use { it.readText() } } finally { cn.disconnect() }
            val ts = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
            var got = false
            for (i in 0 until ts.length()) {
                val e = ts.getJSONObject(i)
                val local = java.time.Instant.parse(e.getString("time")).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                if (local.minute != 0) continue
                val key = local.toString().take(13) + ":00"
                val sl = slots[key] ?: continue
                val d = e.getJSONObject("data")
                d.getJSONObject("instant").getJSONObject("details").optDouble("air_temperature", Double.NaN).takeIf { !it.isNaN() }?.let { sl.temps += it; got = true }
                d.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")?.let { noaaSky(it) }?.takeIf { it >= 0 }?.let { sl.codes += it }
            }
            if (got) sources++
        }
        // MGM's own hours, for a place in Turkey when the MGM source is chosen
        mgm?.optJSONArray("hours")?.let { a ->
            var got = false
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                val sl = slots[e.optString("t")] ?: continue
                if (e.has("temp") && !e.isNull("temp")) { sl.temps += e.getDouble("temp"); got = true }
                e.optInt("code", -1).takeIf { it >= 0 }?.let { sl.codes += it }
            }
            if (got) sources++
        }
        if (sources < 2) return null
        val out = JSONArray()
        for ((t, sl) in slots) {
            if (sl.temps.isEmpty()) continue
            val sorted = sl.temps.sorted()
            val temp = if (sorted.size >= 4) sorted.drop(1).dropLast(1).average() else if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            // the sky: the group most sources agree on; on a tie the heavier group wins
            val groups = sl.codes.groupBy { skyRank(it) }
            val top = groups.maxWithOrNull(compareBy<Map.Entry<Int, List<Int>>> { it.value.size }.thenBy { it.key })
            var code = top?.value?.let { v -> v.groupBy { it }.maxByOrNull { it.value.size }?.key } ?: -1
            val rain = if (sl.rain.isEmpty()) -1 else sl.rain.average().toInt()
            // the sky and the chance of rain must tell the same story: rain in the sky with hardly any chance of it is shown as cloud, and a high chance under a dry sky as light rain
            if (code >= 0 && rain >= 0) {
                if (skyRank(code) in 3..4 && rain < 20) code = 3
                else if (skyRank(code) < 3 && rain >= 75) code = 61
            }
            out.put(JSONObject().put("t", t).put("temp", temp).put("code", code).put("rain", rain).put("n", sl.temps.size))
        }
        return if (out.length() > 0) JSONObject().put("hours", out).put("sources", sources) else null
    }

    /** The groups of the sky, mildest to worst: clear, cloud, fog, rain, snow, storm. */
    private fun skyRank(code: Int) = when (code) { 0, 1 -> 0; 2, 3 -> 1; 45, 48 -> 2; 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> 3; 71, 73, 75, 77, 85, 86 -> 4; 95, 96, 99 -> 5; else -> 1 }

    /** The model forecast with the combined hours laid over it. */
    private fun applyEnsemble(f: Forecast, o: JSONObject): Forecast {
        if (o.optJSONArray("hours") == null) return f
        val hm = HashMap<String, JSONObject>()
        o.getJSONArray("hours").let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { hm[it.getString("t")] = it } }
        // the first hours lean on the reading of now, so the hour list never jumps away from the temperature shown above it
        fun over(i: Int, h: Hour): Hour {
            val e = hm[h.time.take(16)] ?: return h
            if (e.optInt("n", 1) < 2) return h
            val w = (i / 3f).coerceIn(0f, 1f)
            val t = e.optDouble("temp", h.temp)
            val temp = if (i < 3) f.temp * (1 - w) + t * w else t
            return h.copy(temp = temp, code = e.optInt("code", -1).takeIf { it >= 0 } ?: h.code, rain = e.optInt("rain", -1).takeIf { it >= 0 } ?: h.rain)
        }
        return f.copy(hours = f.hours.mapIndexed(::over), hours48 = f.hours48.mapIndexed(::over), sources = o.optInt("sources", 1))
    }

    // ---- MGM (Turkish State Meteorological Service) ---------------------------------------------------------------------------------------------
    // The web service of mgm.gov.tr that its own site uses; it wants the site as Origin. For a place in Turkey its station values replace the model values for now and the
    // first hours; the longer ranges (days 3 to 16, hours past the station forecast) stay with Open-Meteo, which is the only one of the two with a long range.

    private fun mgmGet(url: String): String {
        val cn = openHttps(url)
        try {
            cn.setRequestProperty("Origin", "https://www.mgm.gov.tr"); cn.setRequestProperty("Referer", "https://www.mgm.gov.tr/")
            check(cn.responseCode == 200) { "HTTP ${cn.responseCode}" }
            return cn.inputStream.bufferedReader().use { it.readText() }
        } finally { cn.disconnect() }
    }

    /** MGM's phenomenon letters to the WMO codes the page draws. */
    private fun mgmCode(h: String): Int = when (h.uppercase()) {
        "A" -> 0; "AB" -> 1; "PB" -> 2; "CB" -> 3
        "HY", "HSY" -> 61; "Y" -> 63; "KY", "KSY", "SY" -> 65; "MSY" -> 80
        "KK" -> 67; "HKY" -> 71; "K" -> 73; "YKY" -> 75; "DS" -> 99; "GSY" -> 95
        "SIS", "S" -> 45; "R", "KF" -> 3; "T" -> 45
        else -> -1
    }

    /** Station values near the place as a small JSON object (cur, hours), or null when the place has no MGM station or the service does not answer. */
    fun mgmOverlay(lat: Double, lon: Double): JSONObject? {
        if (lat !in 35.5..42.5 || lon !in 25.5..45.0) return null
        val near = JSONArray(mgmGet("https://servis.mgm.gov.tr/web/merkezler/yakin?lat=%.4f&lon=%.4f".format(java.util.Locale.US, lat, lon))).optJSONObject(0) ?: return null
        val out = JSONObject()
        val obsNo = near.optString("merkezId").ifBlank { near.optString("sondurumIstNo") }
        if (obsNo.isNotBlank()) runCatching {
            val o = JSONArray(mgmGet("https://servis.mgm.gov.tr/web/sondurumlar?merkezid=$obsNo")).optJSONObject(0)
            if (o != null) {
                fun v(k: String): Double? = o.optDouble(k, -9999.0).takeIf { it > -9000.0 }
                val cur = JSONObject()
                v("sicaklik")?.let { cur.put("temp", it) }; v("hissedilenSicaklik")?.let { cur.put("feels", it) }; v("nem")?.let { cur.put("hum", it) }
                v("ruzgarHiz")?.let { cur.put("wind", it) }; (v("ruzgarYon") ?: v("ruzgarYön"))?.let { cur.put("dir", it) }
                (v("denizeIndirgenmisBasinc") ?: v("aktuelBasinc"))?.let { if (it > 800) cur.put("press", it) }
                v("gorus")?.let { cur.put("vis", it / 1000.0) }
                mgmCode(o.optString("hadiseKodu")).takeIf { it >= 0 }?.let { cur.put("code", it) }
                if (cur.length() > 0) out.put("cur", cur)
            }
        }
        val hourNo = near.optString("saatlikTahminIstNo")
        if (hourNo.isNotBlank()) runCatching {
            val arr = JSONArray(mgmGet("https://servis.mgm.gov.tr/web/tahminler/saatlik?istno=$hourNo")).optJSONObject(0)?.optJSONArray("tahmin")
            if (arr != null) {
                val hs = JSONArray()
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    val t = java.time.Instant.parse(e.getString("tarih")).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().withMinute(0).withSecond(0).withNano(0)
                    val o = JSONObject().put("t", t.toString().take(16)).put("code", mgmCode(e.optString("hadise")))
                    e.optDouble("sicaklik", Double.NaN).takeIf { !it.isNaN() && it > -90 }?.let { o.put("temp", it) }
                    hs.put(o)
                }
                out.put("hours", hs)
            }
        }
        return if (out.length() > 0) out else null
    }

    /** The model forecast with MGM's station values laid over now and the hours MGM has. */
    private fun applyMgm(f: Forecast, m: JSONObject): Forecast {
        val c = m.optJSONObject("cur")
        val hm = HashMap<String, JSONObject>()
        m.optJSONArray("hours")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { hm[it.getString("t")] = it } }
        fun over(h: Hour): Hour { val e = hm[h.time.take(16)] ?: return h; return h.copy(temp = if (e.has("temp") && !e.isNull("temp")) e.optDouble("temp", h.temp) else h.temp, code = e.optInt("code", -1).takeIf { it >= 0 } ?: h.code) }
        return f.copy(
            temp = c?.optDouble("temp", f.temp) ?: f.temp, feels = c?.optDouble("feels", f.feels) ?: f.feels, code = c?.optInt("code", f.code) ?: f.code,
            wind = c?.optDouble("wind", f.wind) ?: f.wind, windDir = c?.optInt("dir", f.windDir) ?: f.windDir, humidity = c?.optInt("hum", f.humidity) ?: f.humidity,
            pressure = c?.optDouble("press", f.pressure) ?: f.pressure, visKm = c?.optDouble("vis", f.visKm) ?: f.visKm,
            hours = f.hours.map(::over), hours48 = f.hours48.map(::over), viaMgm = true,
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
    private fun p(c: Context): android.content.SharedPreferences { WeatherApi.appCtx = c.applicationContext; return c.getSharedPreferences("weather", Context.MODE_PRIVATE) }

    /** The OpenWeatherMap key the user typed (the free one is enough). */
    fun owmKey(c: Context): String = p(c).getString("owm_key", "") ?: ""
    fun setOwmKey(c: Context, v: String) = p(c).edit().putString("owm_key", v.trim()).apply()

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

    fun saveCache(c: Context, place: String, raw: String, aqi: Int, extras: String? = null) {
        p(c).edit().putString("raw", raw).putString("cache_place", place).putInt("aqi", aqi).putLong("at", System.currentTimeMillis()).apply { if (extras != null) putString("extras", extras) }.apply()
    }

    /** The last forecast shown, or null when none was saved or the saved copy is unreadable. */
    fun cached(c: Context): Forecast? {
        val s = p(c)
        val raw = s.getString("raw", null) ?: return null
        return runCatching { WeatherApi.parse(s.getString("cache_place", "").orEmpty(), raw, s.getLong("at", 0L), s.getInt("aqi", -1), s.getString("extras", "").orEmpty()) }.getOrNull()
    }

    /** The data source: "om" Open-Meteo alone, "mgm" MGM station values for places in Turkey on top of Open-Meteo. */
    fun source(c: Context): String = p(c).getString("source", "om") ?: "om"
    fun setSource(c: Context, v: String) = p(c).edit().putString("source", v).apply()

    /** Combine several forecasts (models and met.no) for the next 24 hours; on by default. */
    fun combine(c: Context): Boolean = p(c).getBoolean("combine", true)
    fun setCombine(c: Context, v: Boolean) = p(c).edit().putBoolean("combine", v).apply()

    /** Hour of the last severe-weather notice, so one storm is announced once. */
    fun lastAlert(c: Context): String = p(c).getString("alert_hour", "") ?: ""

    fun setLastAlert(c: Context, hour: String) = p(c).edit().putString("alert_hour", hour).apply()

    fun alertsOn(c: Context): Boolean = p(c).getBoolean("alerts", false)

    /** Off by default: alerts need a background refresh, which only runs when the user turns them on. */
    fun setAlertsOn(c: Context, on: Boolean) = p(c).edit().putBoolean("alerts", on).apply()
}

/** Where the phone is: a fresh fix from each enabled provider in turn (network, then GPS with fine permission), else the last known one of either.
 *  Network alone often has nothing (Wi-Fi scanning off, no Play Services, a cold start) and used to leave Weather saying it could not find a place;
 *  GPS is slower to answer but works on far more phones. Needs ACCESS_COARSE_LOCATION, or ACCESS_FINE_LOCATION for the GPS provider too. */
@SuppressLint("MissingPermission")
suspend fun deviceLocation(c: Context): Location? {
    val coarse = ContextCompat.checkSelfPermission(c, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val fine = ContextCompat.checkSelfPermission(c, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!coarse && !fine) return null
    val lm = c.getSystemService(LocationManager::class.java)
    val providers = listOfNotNull(
        LocationManager.NETWORK_PROVIDER.takeIf { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) },
        LocationManager.GPS_PROVIDER.takeIf { fine && runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) },
    )
    val fresh = if (android.os.Build.VERSION.SDK_INT < 30) null else providers.firstNotNullOfOrNull { provider ->
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { k ->
                runCatching { lm.getCurrentLocation(provider, null, c.mainExecutor) { k.resume(it) } }.onFailure { if (k.isActive) k.resume(null) }
            }
        }
    }
    return fresh ?: (providers + LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}
