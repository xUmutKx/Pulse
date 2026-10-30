package com.umutk.pulse.extras.weathermap

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The world map: OpenStreetMap tiles as the ground (Mercator, zoom 2), with the layers drawn over it. */
private const val WORLD = 1024.0          // zoom 2: four 256 px tiles across, the same in both directions
private const val STEP = 10               // degrees between sample points of the weather layers

enum class MapLayer(val en: String, val trName: String) {
    TEMP("Temperature", "Sıcaklık"), RAIN("Precipitation", "Yağış"), WIND("Wind", "Rüzgar"),
    CLOUD("Clouds", "Bulut"), WAVE("Waves", "Dalga"), PLANE("Aircraft (live)", "Uçaklar (canlı)"),
}

/** One sample point of a weather layer: where it is and its value (null when the service has none there). */
class Sample(val lat: Double, val lon: Double, val value: Double?, val dir: Double? = null)

/** Mercator position on the zoom-2 world square. */
private fun px(lon: Double) = (lon + 180.0) / 360.0 * WORLD
private fun py(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * WORLD
}

private fun get(url: String): String? {
    val c = URL(url).openConnection() as HttpURLConnection
    return try {
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Pulse/1.0 (weather map)")
        if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) { null } finally { c.disconnect() }
}

/** Open-Meteo at the sample grid: the current values of one layer, one request per 200 points. */
private object Grid {
    private fun points(): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var lat = -80
        while (lat <= 80) { var lon = -180; while (lon < 180) { out += lat.toDouble() to lon.toDouble(); lon += STEP }; lat += STEP }
        return out
    }

    fun sample(layer: MapLayer): List<Sample> {
        val pts = points()
        val result = ArrayList<Sample>()
        pts.chunked(200).forEach { chunk ->
            val lats = chunk.joinToString(",") { it.first.toInt().toString() }
            val lons = chunk.joinToString(",") { it.second.toInt().toString() }
            val url = if (layer == MapLayer.WAVE) {
                "https://marine-api.open-meteo.com/v1/marine?latitude=$lats&longitude=$lons&current=wave_height,wave_direction"
            } else {
                "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons&current=temperature_2m,precipitation,cloud_cover,wind_speed_10m,wind_direction_10m"
            }
            val body = get(url) ?: return@forEach
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: runCatching { JSONArray().put(JSONObject(body)) }.getOrNull() ?: return@forEach
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cur = o.optJSONObject("current") ?: continue
                val (lat, lon) = chunk[i]
                val v: Double? = when (layer) {
                    MapLayer.TEMP -> cur.optDouble("temperature_2m", Double.NaN).takeIf { !it.isNaN() }
                    MapLayer.RAIN -> cur.optDouble("precipitation", Double.NaN).takeIf { !it.isNaN() }
                    MapLayer.CLOUD -> cur.optDouble("cloud_cover", Double.NaN).takeIf { !it.isNaN() }
                    MapLayer.WIND -> cur.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() }
                    MapLayer.WAVE -> cur.optDouble("wave_height", Double.NaN).takeIf { !it.isNaN() }
                    MapLayer.PLANE -> null
                }
                if (v == null) continue
                val dir = when (layer) {
                    MapLayer.WIND -> cur.optDouble("wind_direction_10m", Double.NaN).takeIf { !it.isNaN() }
                    MapLayer.WAVE -> cur.optDouble("wave_direction", Double.NaN).takeIf { !it.isNaN() }
                    else -> null
                }
                result += Sample(lat, lon, v, dir)
            }
        }
        return result
    }
}

/** Live aircraft from OpenSky (no key, rate limited): position, heading and speed. */
class Plane(val lat: Double, val lon: Double, val track: Double, val kmh: Double, val name: String)

private fun planes(): List<Plane> {
    val body = get("https://opensky-network.org/api/states/all") ?: return emptyList()
    val states = runCatching { JSONObject(body).optJSONArray("states") }.getOrNull() ?: return emptyList()
    val out = ArrayList<Plane>()
    for (i in 0 until states.length()) {
        val s = states.optJSONArray(i) ?: continue
        val lon = s.optDouble(5, Double.NaN); val lat = s.optDouble(6, Double.NaN)
        if (lon.isNaN() || lat.isNaN() || s.optBoolean(8, false)) continue
        out += Plane(lat, lon, s.optDouble(10, 0.0), s.optDouble(9, 0.0) * 3.6, s.optString(1).trim())
    }
    return out
}

/** One tile of the zoom-2 world: its column and row (0..3) and the picture. */
class Tile(val x: Int, val y: Int, val img: ImageBitmap)

/** The ground: the 16 OpenStreetMap tiles of the zoom-2 world. */
private fun worldTiles(): List<Tile> {
    val out = ArrayList<Tile>()
    for (y in 0..3) for (x in 0..3) {
        val c = URL("https://tile.openstreetmap.org/2/$x/$y.png").openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("User-Agent", "Pulse/1.0 (weather map)")
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) } ?: continue
            out += Tile(x, y, bmp.asImageBitmap())
        } catch (e: Exception) { } finally { c.disconnect() }
    }
    return out
}

private fun heat(layer: MapLayer, v: Double): Color {
    val t = when (layer) {
        MapLayer.TEMP -> ((v + 20) / 60).coerceIn(0.0, 1.0)
        MapLayer.RAIN -> (v / 5).coerceIn(0.0, 1.0)
        MapLayer.WIND -> (v / 80).coerceIn(0.0, 1.0)
        MapLayer.CLOUD -> (v / 100).coerceIn(0.0, 1.0)
        MapLayer.WAVE -> (v / 6).coerceIn(0.0, 1.0)
        MapLayer.PLANE -> 0.0
    }
    // cold / calm is blue, warm / strong is red
    return Color(red = t.toFloat(), green = 0.35f, blue = (1 - t).toFloat(), alpha = 0.6f)
}

@Composable
fun WeatherMapHub(onBack: () -> Unit) {
    var layer by remember { mutableStateOf(MapLayer.TEMP) }
    val tiles by produceState<List<Tile>?>(null) { value = withContext(Dispatchers.IO) { worldTiles() } }
    val samples by produceState<List<Sample>?>(null, layer) {
        value = null
        value = withContext(Dispatchers.IO) { if (layer == MapLayer.PLANE) emptyList() else Grid.sample(layer) }
    }
    val aircraft by produceState<List<Plane>?>(null, layer) {
        value = null
        value = if (layer == MapLayer.PLANE) withContext(Dispatchers.IO) { planes() } else emptyList()
    }

    Column(Modifier.fillMaxSize().background(T.panel).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 24.dp) }
            Text(tr("Weather map", "Hava haritası"), color = T.text, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapLayer.entries.forEach { l ->
                val on = l == layer
                Text(tr(l.en, l.trName), color = if (on) Color.Black else T.text, fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.background(if (on) T.accent else T.key, RoundedCornerShape(50))
                        .clickable { layer = l }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) {
                val k = (size.width / WORLD).toFloat()
                // the ground first: the four columns and rows of 256 px tiles
                tiles?.forEach { t ->
                    drawImage(t.img, dstOffset = IntOffset((t.x * 256 * k).toInt(), (t.y * 256 * k).toInt()), dstSize = IntSize((256 * k).toInt(), (256 * k).toInt()))
                }
                // the weather values as soft dots (wind and waves get a short tick for the direction)
                samples?.forEach { sm ->
                    val c = Offset((px(sm.lon) * k).toFloat(), (py(sm.lat) * k).toFloat())
                    val v = sm.value ?: return@forEach
                    drawCircle(heat(layer, v), radius = 9f * k, center = c)
                    sm.dir?.let { d ->
                        val r = Math.toRadians(d)
                        val end = Offset(c.x + (kotlin.math.sin(r) * 14 * k).toFloat(), c.y - (kotlin.math.cos(r) * 14 * k).toFloat())
                        drawLine(Color.White, c, end, strokeWidth = 1.5f * k)
                    }
                }
                // live aircraft: a dot with a short heading line
                aircraft?.forEach { p ->
                    val c = Offset((px(p.lon) * k).toFloat(), (py(p.lat) * k).toFloat())
                    drawCircle(Color(0xFFFFC107), radius = 2.5f * k, center = c)
                    val r = Math.toRadians(p.track)
                    drawLine(Color(0xFFFFC107), c, Offset(c.x + (kotlin.math.sin(r) * 6 * k).toFloat(), c.y - (kotlin.math.cos(r) * 6 * k).toFloat()), strokeWidth = 1f * k)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                layer == MapLayer.PLANE -> aircraft?.let { tr("${it.size} aircraft in the air (OpenSky)", "${it.size} uçak havada (OpenSky)") } ?: tr("Loading…", "Yükleniyor…")
                samples == null -> tr("Loading…", "Yükleniyor…")
                samples!!.isEmpty() -> tr("No data (no network?)", "Veri yok (ağ yok mu?)")
                else -> tr("${samples!!.size} points", "${samples!!.size} nokta")
            },
            color = T.sub, fontSize = 12.sp,
        )
    }
}
