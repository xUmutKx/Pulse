package com.umutk.pulse.extras.weathermap

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.weather.Place
import com.umutk.pulse.extras.weather.WeatherStore
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/*
 * The weather map: a dark map (black sea edge, dark grey land) drawn in tiles that sharpen as you zoom in, with the weather
 * layers over it. It opens on the place chosen in the weather page, zoomed in, and the grid of values follows the screen, so
 * the seas and the coast get their own points. When a service fails the last values stay on the screen with their time.
 */

enum class MapLayer(val en: String, val trName: String) {
    WIND("Wind", "Rüzgar"), WAVE("Waves", "Dalga"), CLOUD("Clouds", "Bulut"), PLANE("Aircraft (live)", "Uçaklar (canlı)"),
    RADAR("Rain radar (live)", "Yağış radarı (canlı)"), TEMP("Temperature", "Sıcaklık"), RAIN("Precipitation", "Yağış"),
    SATELLITE("Satellite (yesterday)", "Uydu (dün)"),
}

/** The layers that are values on a grid of points (the others are pictures or live objects). */
private fun MapLayer.isGrid() = this == MapLayer.WIND || this == MapLayer.WAVE || this == MapLayer.CLOUD || this == MapLayer.TEMP || this == MapLayer.RAIN

/** A value of a grid layer at one point; [dir] is the wind or wave direction in degrees, when the layer has one. */
class Sample(val lat: Double, val lon: Double, val value: Double, val dir: Double?)

/** A live aircraft: its place, heading, speed, height (metres, NaN when unknown), callsign and the country it is registered in. */
class Plane(val lat: Double, val lon: Double, val track: Double, val kmh: Double, val metres: Double, val call: String, val country: String)

/** Where the map looks: the centre in world units (0..1, Mercator) and the zoom. */
private class MapPos(val cx: Double, val cy: Double, val zoom: Double)

private const val TILE = 256.0
private const val BASE_MAX_ZOOM = 18
private const val RADAR_MAX_ZOOM = 7
private const val SAT_MAX_ZOOM = 9

/** Pixels per world unit at a zoom, in physical pixels: a tile of 256 dp is one screen tile at that zoom. */
private fun pxPerWorld(zoom: Double, density: Float): Double = TILE * density * 2.0.pow(zoom)

private fun wx(lon: Double) = (lon + 180.0) / 360.0
private fun wy(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0
}
private fun lonOf(x: Double): Double = x * 360.0 - 180.0
private fun latOf(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))
private fun wrapLon(lon: Double): Double = ((lon + 540.0) % 360.0) - 180.0

private fun get(url: String): String? {
    val c = URL(url).openConnection() as HttpURLConnection
    return try {
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Pulse/1.0 (weather map)")
        if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) { null } finally { c.disconnect() }
}

private fun fetchTile(url: String): ImageBitmap? {
    val c = URL(url).openConnection() as HttpURLConnection
    return try {
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Pulse/1.0 (weather map)")
        if (c.responseCode !in 200..299) null else c.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (e: Exception) { null } finally { c.disconnect() }
}

/** The tiles of the zoom level that cover the screen, as (zoom, column, row); the column is not wrapped, so the drawing stays in place. */
private fun visibleTiles(p: MapPos, w: Float, h: Float, density: Float, maxZoom: Int): List<Triple<Int, Int, Int>> {
    val z = ceil(p.zoom).toInt().coerceIn(0, maxZoom)
    val n = (1 shl z).toDouble()
    val ppu = pxPerWorld(p.zoom, density)
    val x0 = p.cx - w / 2 / ppu; val x1 = p.cx + w / 2 / ppu
    val y0 = p.cy - h / 2 / ppu; val y1 = p.cy + h / 2 / ppu
    val out = ArrayList<Triple<Int, Int, Int>>()
    val ty0 = floor(y0 * n).toInt().coerceAtLeast(0)
    val ty1 = floor(y1 * n).toInt().coerceAtMost(n.toInt() - 1)
    val tx0 = floor(x0 * n).toInt(); val tx1 = floor(x1 * n).toInt()
    for (ty in ty0..ty1) for (tx in tx0..tx1) out += Triple(z, tx, ty)
    return out
}

/** The key of a tile: zoom, column (wrapped around the world) and row. */
private fun tileKey(z: Int, x: Int, y: Int): String {
    val n = 1 shl z
    return "$z/${((x % n) + n) % n}/$y"
}

private fun yesterday(): String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString()

/** The URL of an overlay tile: RainViewer radar (its newest frame) or NASA GIBS satellite (yesterday's), both keyless. */
private fun overlayUrl(layer: MapLayer, radarBase: String?, zxy: String): String? {
    val parts = zxy.split('/')
    if (parts.size != 3) return null
    val (z, x, y) = parts
    return when (layer) {
        MapLayer.RADAR -> radarBase?.let { "$it/512/$z/$x/$y/2/1_1.png" }
        MapLayer.SATELLITE -> "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/MODIS_Terra_CorrectedReflectance_TrueColor/default/${yesterday()}/GoogleMapsCompatible_Level9/$z/$y/$x.jpg"
        else -> null
    }
}

private fun radarBase(): String? {
    val j = get("https://api.rainviewer.com/public/weather-maps.json")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val past = j.optJSONObject("radar")?.optJSONArray("past") ?: return null
    if (past.length() == 0) return null
    return j.optString("host") + past.optJSONObject(past.length() - 1)?.optString("path").orEmpty()
}

/** The points of a grid over the screen: a step that gives about a dozen points across, from the finest that fits. */
private class GridReq(val key: String, val points: List<Pair<Double, Double>>)

private fun gridFor(p: MapPos, w: Float, h: Float, density: Float): GridReq {
    val ppu = pxPerWorld(p.zoom, density)
    val latTop = latOf(p.cy - h / 2 / ppu).coerceIn(-85.0, 85.0)
    val latBottom = latOf(p.cy + h / 2 / ppu).coerceIn(-85.0, 85.0)
    val lonLeft = lonOf(p.cx - w / 2 / ppu)
    val lonRight = lonOf(p.cx + w / 2 / ppu)
    val span = lonRight - lonLeft
    val step = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0).firstOrNull { span / it <= 12 } ?: 30.0
    val latStart = floor(latBottom / step) * step
    val lonStart = floor(lonLeft / step) * step
    val pts = ArrayList<Pair<Double, Double>>()
    var la = latStart
    while (la <= latTop + 1e-9) {
        var lo = lonStart
        while (lo <= lonRight + 1e-9) { pts += la to wrapLon(lo); lo += step }
        la += step
    }
    return GridReq("$step|${"%.2f".format(Locale.US, latStart)}|${"%.2f".format(Locale.US, lonStart)}|${pts.size}", pts)
}

/** Open-Meteo at the points: the current values of one layer, one request per 100 points. Null when the service fails. */
private fun fetchGrid(layer: MapLayer, pts: List<Pair<Double, Double>>): List<Sample>? {
    val out = ArrayList<Sample>()
    for (chunk in pts.chunked(100)) {
        val la = chunk.joinToString(",") { String.format(Locale.US, "%.2f", it.first) }
        val lo = chunk.joinToString(",") { String.format(Locale.US, "%.2f", it.second) }
        val url = if (layer == MapLayer.WAVE) {
            "https://marine-api.open-meteo.com/v1/marine?latitude=$la&longitude=$lo&current=wave_height,wave_direction"
        } else {
            "https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo&current=temperature_2m,precipitation,cloud_cover,wind_speed_10m,wind_direction_10m"
        }
        val body = get(url) ?: return null
        if (body.contains("\"error\"")) return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: runCatching { JSONArray().put(JSONObject(body)) }.getOrNull() ?: return null
        for (i in 0 until arr.length()) {
            val cur = arr.optJSONObject(i)?.optJSONObject("current") ?: continue
            val v = when (layer) {
                MapLayer.TEMP -> cur.optDouble("temperature_2m", Double.NaN)
                MapLayer.RAIN -> cur.optDouble("precipitation", Double.NaN)
                MapLayer.CLOUD -> cur.optDouble("cloud_cover", Double.NaN)
                MapLayer.WIND -> cur.optDouble("wind_speed_10m", Double.NaN)
                MapLayer.WAVE -> cur.optDouble("wave_height", Double.NaN)
                else -> Double.NaN
            }
            if (v.isNaN() || i >= chunk.size) continue
            val d = when (layer) {
                MapLayer.WIND -> cur.optDouble("wind_direction_10m", Double.NaN)
                MapLayer.WAVE -> cur.optDouble("wave_direction", Double.NaN)
                else -> Double.NaN
            }
            out += Sample(chunk[i].first, chunk[i].second, v, d.takeIf { !it.isNaN() })
        }
    }
    return out
}

/** Live aircraft from OpenSky (no key, rate limited). Null when the service fails. */
private fun fetchPlanes(): List<Plane>? {
    val body = get("https://opensky-network.org/api/states/all") ?: return null
    val states = runCatching { JSONObject(body).optJSONArray("states") }.getOrNull() ?: return null
    val out = ArrayList<Plane>(states.length())
    for (i in 0 until states.length()) {
        val s = states.optJSONArray(i) ?: continue
        val lon = s.optDouble(5, Double.NaN); val lat = s.optDouble(6, Double.NaN)
        if (lon.isNaN() || lat.isNaN() || s.optBoolean(8, false)) continue
        out += Plane(lat, lon, s.optDouble(10, 0.0), s.optDouble(9, 0.0) * 3.6, s.optDouble(7, Double.NaN), s.optString(1).trim(), s.optString(2).trim())
    }
    return out
}

/** The lowest and the highest value of each grid layer's colour scale. */
private fun rangeOf(layer: MapLayer): Pair<Double, Double> = when (layer) {
    MapLayer.TEMP -> -20.0 to 40.0
    MapLayer.RAIN -> 0.0 to 5.0
    MapLayer.WIND -> 0.0 to 100.0
    MapLayer.CLOUD -> 0.0 to 100.0
    MapLayer.WAVE -> 0.0 to 6.0
    else -> 0.0 to 1.0
}

/** The rainbow of the scales, from the lowest value (violet) to the highest (red). */
private val SCALE = listOf(Color(0xFF3B0E9A), Color(0xFF1E5BD8), Color(0xFF1FB5E0), Color(0xFF27C080), Color(0xFFB8E03A), Color(0xFFF5D120), Color(0xFFF08A1C), Color(0xFFE0302A))

private fun scaleAt(t: Float): Color {
    val x = t.coerceIn(0f, 1f) * (SCALE.size - 1)
    val i = floor(x).toInt().coerceAtMost(SCALE.size - 2)
    return lerp(SCALE[i], SCALE[i + 1], x - i)
}

private fun valueColor(layer: MapLayer, v: Double): Color {
    val (lo, hi) = rangeOf(layer)
    val t = ((v - lo) / (hi - lo)).toFloat()
    // the cloud cover is a grey veil: the more cloud, the whiter
    if (layer == MapLayer.CLOUD) return Color.White.copy(alpha = 0.1f + 0.7f * t.coerceIn(0f, 1f))
    return scaleAt(t).copy(alpha = 0.85f)
}

/** A wind or wave arrow: the reports give the direction the air or water comes from, so the arrow points the other way. */
private fun DrawScope.drawArrow(c: Offset, fromDeg: Double, len: Float, color: Color) {
    val r = Math.toRadians(fromDeg + 180.0)
    val dx = sin(r).toFloat()
    val dy = -cos(r).toFloat()
    val tip = Offset(c.x + dx * len, c.y + dy * len)
    drawLine(color, c, tip, strokeWidth = 1.5.dp.toPx())
    val baseX = tip.x - dx * len * 0.4f
    val baseY = tip.y - dy * len * 0.4f
    val side = len * 0.25f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX - dy * side, baseY + dx * side)
        lineTo(baseX + dy * side, baseY - dx * side)
        close()
    }
    drawPath(head, color)
}

/** The colour scale of a grid layer: its highest value on top, its lowest at the bottom, with the unit. */
@Composable
private fun ScaleLegend(layer: MapLayer, modifier: Modifier) {
    val (lo, hi) = rangeOf(layer)
    val colors = if (layer == MapLayer.CLOUD) listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f)) else SCALE.reversed()
    Column(modifier.padding(end = 10.dp), horizontalAlignment = Alignment.End) {
        Text("${hi.roundToInt()} ${unitOf(layer)}", color = Color.White, fontSize = 11.sp)
        Box(Modifier.padding(vertical = 4.dp).size(12.dp, 170.dp).clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(colors)))
        Text("${lo.roundToInt()} ${unitOf(layer)}", color = Color.White, fontSize = 11.sp)
    }
}

private fun unitOf(layer: MapLayer): String = when (layer) {
    MapLayer.TEMP -> "°C"; MapLayer.RAIN -> "mm"; MapLayer.WIND -> "km/h"; MapLayer.CLOUD -> "%"; MapLayer.WAVE -> "m"
    else -> ""
}

/** The icon of a layer on its chip. */
private fun layerIcon(l: MapLayer): ImageVector = when (l) {
    MapLayer.WIND -> Icons.Outlined.Air
    MapLayer.WAVE -> Icons.Outlined.Waves
    MapLayer.CLOUD -> Icons.Outlined.Cloud
    MapLayer.PLANE -> Icons.Outlined.Flight
    MapLayer.RADAR -> Icons.Outlined.Thunderstorm
    MapLayer.TEMP -> Icons.Outlined.Thermostat
    MapLayer.RAIN -> Icons.Outlined.WaterDrop
    MapLayer.SATELLITE -> Icons.Outlined.SatelliteAlt
}

/** A round chip with an icon: black glass, the accent colour when it is the layer shown. */
@Composable
private fun LayerChip(icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).background(if (on) T.accent else Color.Black.copy(alpha = .6f), CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(22.dp))
    }
}

/** The outline of an aircraft pointing north, in units of its half length (x to the right, y down). */
private val PLANE_SHAPE = listOf(
    0f to -1f, 0.12f to -0.3f, 1f to 0.15f, 1f to 0.35f, 0.12f to 0.2f, 0.1f to 0.8f, 0.35f to 1f,
    0f to 0.9f, -0.35f to 1f, -0.1f to 0.8f, -0.12f to 0.2f, -1f to 0.35f, -1f to 0.15f, -0.12f to -0.3f,
)

/** An aircraft icon at its place, turned to its heading (degrees clockwise from north). */
private fun DrawScope.drawPlane(c: Offset, headingDeg: Double, half: Float, color: Color) {
    val r = Math.toRadians(headingDeg)
    val sn = sin(r).toFloat()
    val cs = cos(r).toFloat()
    val path = Path()
    PLANE_SHAPE.forEachIndexed { i, (x, y) ->
        val px = c.x + (x * cs - y * sn) * half
        val py = c.y + (x * sn + y * cs) * half
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}

/**
 * The dark look of the ground: the OpenStreetMap tiles are turned to grey, darkened, with the sea a little bluer than the land.
 */
private val DARK_MAP: ColorFilter = run {
    val k = 0.46f
    fun row(w: Float) = floatArrayOf(k * w * 0.2126f, k * w * 0.7152f, k * w * 0.0722f)
    val r = row(0.9f); val g = row(0.95f); val b = row(1.15f)
    ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(r[0], r[1], r[2], 0f, 0f, g[0], g[1], g[2], 0f, 0f, b[0], b[1], b[2], 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
}

/** One tile of a picture layer, drawn at its place on the screen. */
/** A tile that is not loaded yet: the part of the nearest loaded tile of a lower zoom that covers it, so the map never goes black. */
private fun DrawScope.drawOrParent(tiles: Map<String, ImageBitmap>, z: Int, tx: Int, ty: Int, p: MapPos, ppu: Double, filter: ColorFilter?) {
    tiles[tileKey(z, tx, ty)]?.let { drawTile(it, z, tx, ty, p, ppu, 1f, filter); return }
    for (k in 1..4) {
        val pz = z - k
        if (pz < 0) return
        val img = tiles[tileKey(pz, tx shr k, ty shr k)] ?: continue
        val n = 1 shl k
        val sw = img.width / n
        val sh = img.height / n
        val src = IntOffset((tx and (n - 1)) * sw, (ty and (n - 1)) * sh)
        val d = 1.0 * (1 shl z)
        val side = ppu / d
        val left = size.width / 2 + (tx / d - p.cx) * ppu
        val top = size.height / 2 + (ty / d - p.cy) * ppu
        val l = floor(left).toInt(); val t = floor(top).toInt()
        val r = ceil(left + side).toInt(); val b = ceil(top + side).toInt()
        drawImage(img, srcOffset = src, srcSize = IntSize(sw, sh), dstOffset = IntOffset(l, t), dstSize = IntSize(r - l, b - t), colorFilter = filter)
        return
    }
}

private fun DrawScope.drawTile(img: ImageBitmap, z: Int, tx: Int, ty: Int, p: MapPos, ppu: Double, alpha: Float, filter: ColorFilter? = null) {
    val n = (1 shl z).toDouble()
    val side = ppu / n
    val left = size.width / 2 + (tx / n - p.cx) * ppu
    val top = size.height / 2 + (ty / n - p.cy) * ppu
    val l = floor(left).toInt(); val t = floor(top).toInt()
    val r = kotlin.math.ceil(left + side).toInt(); val b = kotlin.math.ceil(top + side).toInt()
    drawImage(img, dstOffset = IntOffset(l, t), dstSize = IntSize(r - l, b - t), alpha = alpha, colorFilter = filter)
}

@Composable
fun WeatherMapHub(onBack: () -> Unit, at: Place? = null) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val prefs = remember { ctx.getSharedPreferences("weathermap", Context.MODE_PRIVATE) }
    var layer by remember { mutableStateOf(MapLayer.entries.firstOrNull { it.name == prefs.getString("layer", "") } ?: MapLayer.WIND) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var pos by remember { mutableStateOf(MapPos(0.5, 0.5, 1.0)) }
    var placed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<List<String>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val grids = remember { mutableStateMapOf<MapLayer, List<Sample>>() }
    val stamps = remember { mutableStateMapOf<MapLayer, Long>() }
    val baseTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    val overTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var planes by remember { mutableStateOf<List<Plane>?>(null) }
    var planeStamp by remember { mutableStateOf(0L) }
    var radar by remember { mutableStateOf<String?>(null) }
    var radarStamp by remember { mutableStateOf(0L) }

    // the last values of each layer are kept on the phone: they show at once and stay when a request fails
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { MapLayer.entries.filter { it.isGrid() }.mapNotNull { l -> loadSaved(ctx, l)?.let { Triple(l, it.first, it.second) } } }
        saved.forEach { (l, t, s) -> grids[l] = s; stamps[l] = t }
    }

    // the map opens flat on the whole world, the width of the screen; the place chip zooms to the chosen place
    fun place(sz: Size) {
        if (placed || sz.width <= 0f) return
        placed = true
        pos = MapPos(0.5, 0.5, (ln(sz.width / (TILE * density)) / ln(2.0)).coerceAtLeast(1.0))
    }
    val homeView = remember(at) {
        (at ?: WeatherStore.savedPlace(ctx))?.takeIf { !(it.device && it.lat == 0.0 && it.lon == 0.0) }?.let { MapPos(wx(it.lon), wy(it.lat), 5.0) }
    }

    // the live layers: aircraft every minute, the radar frame every five minutes
    LaunchedEffect(layer, refresh) {
        if (layer != MapLayer.PLANE) return@LaunchedEffect
        while (true) {
            val p = withContext(Dispatchers.IO) { fetchPlanes() }
            if (p != null) { planes = p; planeStamp = System.currentTimeMillis() }
            delay(60_000)
        }
    }
    LaunchedEffect(layer, refresh) {
        if (layer != MapLayer.RADAR) return@LaunchedEffect
        while (true) {
            val b = withContext(Dispatchers.IO) { radarBase() }
            if (b != null) { radar = b; radarStamp = System.currentTimeMillis(); overTiles.clear() }
            delay(300_000)
        }
    }

    // the values of the grid follow the screen: asked again a moment after the map stops moving
    val grid = if (canvasSize.width > 0f && layer.isGrid()) gridFor(pos, canvasSize.width, canvasSize.height, density) else null
    LaunchedEffect(layer, grid?.key, refresh) {
        val g = grid ?: return@LaunchedEffect
        delay(600)
        val res = withContext(Dispatchers.IO) { fetchGrid(layer, g.points) }
        if (res != null) {
            val now = System.currentTimeMillis()
            grids[layer] = res; stamps[layer] = now
            withContext(Dispatchers.IO) { saveGrid(ctx, layer, now, res) }
        }
    }

    // the tiles of the screen: the dark map, and the radar or satellite picture over it
    val base = if (canvasSize.width > 0f) visibleTiles(pos, canvasSize.width, canvasSize.height, density, BASE_MAX_ZOOM) else emptyList()
    val baseKeys = base.map { (z, x, y) -> tileKey(z, x, y) }
    LaunchedEffect(baseKeys) {
        val missing = baseKeys.filter { it !in baseTiles }
        if (missing.isNotEmpty()) {
            missing.map { k -> async(Dispatchers.IO) { k to fetchTile("https://tile.openstreetmap.org/$k.png") } }
                .awaitAll().forEach { (k, img) -> if (img != null) baseTiles[k] = img }
        }
        if (baseTiles.size > 160) baseTiles.keys.filter { it !in baseKeys }.forEach { baseTiles.remove(it) }
    }
    val overMax = when (layer) { MapLayer.RADAR -> RADAR_MAX_ZOOM; MapLayer.SATELLITE -> SAT_MAX_ZOOM; else -> -1 }
    val overKeys = if (overMax < 0 || canvasSize.width <= 0f) emptyList()
        else visibleTiles(pos, canvasSize.width, canvasSize.height, density, overMax).map { (z, x, y) -> "${layer.name}/${tileKey(z, x, y)}" }
    LaunchedEffect(layer, radar, overKeys) {
        if (overKeys.isEmpty()) return@LaunchedEffect
        val missing = overKeys.filter { it !in overTiles }
        if (missing.isNotEmpty()) {
            missing.map { k -> async(Dispatchers.IO) { k to overlayUrl(layer, radar, k.substringAfter('/'))?.let { fetchTile(it) } } }
                .awaitAll().forEach { (k, img) -> if (img != null) overTiles[k] = img }
        }
        if (overTiles.size > 160) overTiles.keys.filter { it !in overKeys }.forEach { overTiles.remove(it) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { sz ->
                    canvasSize = Size(sz.width.toFloat(), sz.height.toFloat())
                    place(canvasSize)
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val s = canvasSize
                        if (s.width <= 0f) return@detectTransformGestures
                        val p = pos
                        val ppu = pxPerWorld(p.zoom, density)
                        // the point under the fingers stays under them while zooming; then the drag moves the map
                        val wxAt = p.cx + (centroid.x - s.width / 2) / ppu
                        val wyAt = p.cy + (centroid.y - s.height / 2) / ppu
                        val nz = (p.zoom + ln(zoomChange.toDouble()) / ln(2.0)).coerceIn(1.0, BASE_MAX_ZOOM.toDouble())
                        val np = pxPerWorld(nz, density)
                        val ncx = wxAt - (centroid.x + pan.x - s.width / 2) / np
                        val ncy = wyAt - (centroid.y + pan.y - s.height / 2) / np
                        pos = MapPos(ncx, ncy.coerceIn(0.0, 1.0), nz)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { tap ->
                        val s = canvasSize
                        val p = pos
                        val ppu = pxPerWorld(p.zoom, density)
                        fun screen(lat: Double, lon: Double) = Offset(((wx(lon) - p.cx) * ppu + s.width / 2).toFloat(), ((wy(lat) - p.cy) * ppu + s.height / 2).toFloat())
                        val reach = 28.dp.toPx()
                        info = if (layer == MapLayer.PLANE) {
                            planes?.filter { (screen(it.lat, it.lon) - tap).getDistance() < reach }
                                ?.minByOrNull { (screen(it.lat, it.lon) - tap).getDistance() }
                                ?.let { pl ->
                                    listOf(
                                        pl.call.ifEmpty { "—" } + if (pl.country.isNotEmpty()) " · ${pl.country}" else "",
                                        tr("Height ", "Yükseklik ") + if (pl.metres.isNaN()) "—" else "${(pl.metres * 3.28084).roundToInt()} ft (${pl.metres.roundToInt()} m)",
                                        tr("Speed ", "Hız ") + "${pl.kmh.roundToInt()} km/h",
                                        tr("Heading ", "Yön ") + "${pl.track.roundToInt()}°",
                                    )
                                }
                        } else {
                            grids[layer]?.filter { (screen(it.lat, it.lon) - tap).getDistance() < reach }
                                ?.minByOrNull { (screen(it.lat, it.lon) - tap).getDistance() }
                                ?.let { sm ->
                                    listOfNotNull(
                                        tr(layer.en, layer.trName) + ": " + String.format(Locale.US, "%.1f", sm.value) + " " + unitOf(layer),
                                        sm.dir?.let { tr("Direction ", "Yön ") + "${it.roundToInt()}°" },
                                        String.format(Locale.US, "%.2f, %.2f", sm.lat, sm.lon),
                                    )
                                }
                        }
                    })
                }
        ) {
            val p = pos
            val ppu = pxPerWorld(p.zoom, density)
            base.forEach { (z, tx, ty) -> drawOrParent(baseTiles, z, tx, ty, p, ppu, DARK_MAP) }
            if (overMax >= 0) visibleTiles(p, size.width, size.height, density, overMax).forEach { (z, tx, ty) ->
                overTiles["${layer.name}/${tileKey(z, tx, ty)}"]?.let { drawTile(it, z, tx, ty, p, ppu, if (layer == MapLayer.RADAR) 0.7f else 1f) }
            }
            fun screenOf(lat: Double, lon: Double) = Offset(((wx(lon) - p.cx) * ppu + size.width / 2).toFloat(), ((wy(lat) - p.cy) * ppu + size.height / 2).toFloat())
            if (layer.isGrid()) grids[layer]?.forEach { sm ->
                val c = screenOf(sm.lat, sm.lon)
                if (c.x < -20f || c.x > size.width + 20f || c.y < -20f || c.y > size.height + 20f) return@forEach
                // a soft blob per value: the field reads as a heatmap, the cloud cover as soft white clouds
                val reach = (if (layer == MapLayer.CLOUD) 34 else 22).dp.toPx()
                val col = valueColor(layer, sm.value)
                drawCircle(Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = c, radius = reach), radius = reach, center = c)
                if (layer == MapLayer.WIND || layer == MapLayer.WAVE) sm.dir?.let { drawArrow(c, it, 14.dp.toPx(), Color.White) }
            }
            if (layer == MapLayer.PLANE) planes?.forEach { pl ->
                val c = screenOf(pl.lat, pl.lon)
                if (c.x < -10f || c.x > size.width + 10f || c.y < -10f || c.y > size.height + 10f) return@forEach
                drawPlane(c, pl.track, 5.dp.toPx(), Color(0xFFFFC107))
            }
        }

        if (layer.isGrid()) ScaleLegend(layer, Modifier.align(Alignment.CenterEnd))

        // the controls float over the map: back and the layers at the top
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(Color.Black.copy(alpha = .6f), CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", Color.White, 22.dp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                homeView?.let { home ->
                    LayerChip(Icons.Outlined.MyLocation, false) { pos = home; info = null }
                }
                MapLayer.entries.forEach { l ->
                    LayerChip(layerIcon(l), l == layer) { layer = l; info = null; prefs.edit().putString("layer", l.name).apply() }
                }
            }
        }

        // the picked value or aircraft, above the status line; a tap on the map picks another
        info?.let { lines ->
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp)
                .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                lines.forEachIndexed { i, line -> Text(line, color = Color.White, fontSize = if (i == 0) 15.sp else 13.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }

        // the state: what is shown and when its data came in; a tap asks again
        val stamp = when (layer) {
            MapLayer.PLANE -> planeStamp
            MapLayer.RADAR -> radarStamp
            MapLayer.SATELLITE -> 0L
            else -> stamps[layer] ?: 0L
        }
        val when_ = if (stamp > 0L) " · " + tr("data ", "veri ") + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stamp)) else ""
        val status = when {
            layer == MapLayer.PLANE -> planes?.let { tr("${it.size} aircraft · OpenSky", "${it.size} uçak · OpenSky") + when_ } ?: tr("Loading…", "Yükleniyor…")
            layer == MapLayer.RADAR -> if (radar == null) tr("Loading…", "Yükleniyor…") else tr("Rain radar", "Yağış radarı") + when_
            layer == MapLayer.SATELLITE -> tr("Satellite picture of ${yesterday()}", "${yesterday()} uydu görüntüsü")
            else -> grids[layer]?.let { g ->
                if (g.isEmpty()) tr("No data here", "Burada veri yok") + when_ else tr("${g.size} points", "${g.size} nokta") + when_
            } ?: tr("Loading…", "Yükleniyor…")
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, color = Color.White, fontSize = 12.sp, maxLines = 1,
                modifier = Modifier.background(Color.Black.copy(alpha = .6f), RoundedCornerShape(50)).clickable { refresh++ }.padding(horizontal = 12.dp, vertical = 4.dp))
            Text("© OpenStreetMap contributors", color = Color.White.copy(alpha = .5f), fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun savedFile(c: Context, layer: MapLayer) = File(c.filesDir, "weathermap_${layer.name}.txt")

/** The grid of a layer as it was last loaded: the time on the first line, then "lat lon value direction" lines. */
private fun saveGrid(c: Context, layer: MapLayer, time: Long, list: List<Sample>) {
    runCatching {
        savedFile(c, layer).writeText(buildString {
            append(time).append('\n')
            list.forEach { append(it.lat).append(' ').append(it.lon).append(' ').append(it.value).append(' ').append(it.dir ?: "").append('\n') }
        })
    }
}

private fun loadSaved(c: Context, layer: MapLayer): Pair<Long, List<Sample>>? = runCatching {
    val lines = savedFile(c, layer).readLines()
    val time = lines[0].toLong()
    time to lines.drop(1).map { l ->
        val f = l.split(' ')
        Sample(f[0].toDouble(), f[1].toDouble(), f[2].toDouble(), f.getOrNull(3)?.toDoubleOrNull())
    }
}.getOrNull()
