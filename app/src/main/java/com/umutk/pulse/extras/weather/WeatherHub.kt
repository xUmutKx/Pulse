package com.umutk.pulse.extras.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import com.umutk.pulse.widgets.WeatherWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private fun hasLocation(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Weather for the phone's place or saved cities. The last forecast is drawn at once, then refreshed. */
@Composable
fun WeatherHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var place by remember { mutableStateOf(WeatherStore.savedPlace(ctx)) }
    var fc by remember { mutableStateOf(WeatherStore.cached(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<Place>()) }
    var cities by remember { mutableStateOf(WeatherStore.cities(ctx)) }
    var imp by remember { mutableStateOf(WeatherFmt.imperial(ctx)) }
    var alerts by remember { mutableStateOf(WeatherStore.alertsOn(ctx)) }
    val devicePlace = Place(tr("My location", "Konumum"), 0.0, 0.0, device = true)

    fun load(p: Place) {
        scope.launch {
            busy = true; error = ""
            val spot = if (p.device) deviceLocation(ctx)?.let { Place(p.name, it.latitude, it.longitude, true) } else p
            if (spot == null) {
                error = tr("No location fix yet. Search a city.", "Konum henüz alınamadı. Bir şehir arayın.")
            } else {
                val res = runCatching {
                    withContext(Dispatchers.IO) {
                        val raw = WeatherApi.forecast(spot.lat, spot.lon)
                        val aqi = WeatherApi.airQuality(spot.lat, spot.lon)
                        WeatherStore.saveCache(ctx, spot.name, raw, aqi)
                        WeatherApi.parse(spot.name, raw, System.currentTimeMillis(), aqi)
                    }
                }
                res.onSuccess {
                    fc = it; place = spot; WeatherStore.setPlace(ctx, spot)
                    WeatherWidgets.refreshAll(ctx)
                }.onFailure { error = tr("Could not load the weather.", "Hava durumu alınamadı.") }
            }
            busy = false
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) load(devicePlace) else error = tr("Location is off. Search a city.", "Konum kapalı. Bir şehir arayın.")
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun useDevice() { if (hasLocation(ctx)) load(devicePlace) else ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    fun search() {
        if (query.isBlank()) return
        scope.launch {
            busy = true; error = ""
            hits = withContext(Dispatchers.IO) { runCatching { WeatherApi.search(query) }.getOrDefault(emptyList()) }
            if (hits.isEmpty()) error = tr("No city found.", "Şehir bulunamadı.")
            busy = false
        }
    }
    fun setAlerts(on: Boolean) {
        alerts = on
        if (on && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        WeatherAlerts.set(ctx, on)
    }

    BackHandler { if (searching) searching = false else onBack() }
    // first paint from the cache, then one refresh for a saved city or the phone's place when it is allowed
    LaunchedEffect(Unit) {
        val p = place
        if (p != null && (!p.device || hasLocation(ctx))) load(p)
    }

    val fg = T.text
    val sub = T.sub
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    fun t(v: Double) = WeatherFmt.temp(v, imp)
    LazyColumn(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PIcon("back", fg, 22.dp, Modifier.clickable { if (searching) searching = false else onBack() })
                Text(tr("Weather", "Hava durumu"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Box(Modifier.clickable { imp = !imp; WeatherStore.setUnits(ctx, if (imp) "f" else "c") }.padding(horizontal = 6.dp, vertical = 8.dp)) {
                    Text(if (imp) "°F" else "°C", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                com.umutk.pulse.ExtraLookIcon(fg)
                Box(Modifier.size(40.dp).clickable { searching = !searching; error = "" }, contentAlignment = Alignment.Center) { PIcon("search", fg, 22.dp) }
                Box(Modifier.size(40.dp).clickable { useDevice() }, contentAlignment = Alignment.Center) { PIcon("compass", fg, 22.dp) }
                Box(Modifier.size(40.dp).clickable { place?.let { load(it) } }, contentAlignment = Alignment.Center) { PIcon("refresh", fg, 22.dp) }
            }
        }
        if (searching) {
            item { PField(query, { query = it }, tr("City", "Şehir"), Modifier.fillMaxWidth(), onDone = { search() }) }
            items(hits, key = { it.lat.toString() + it.lon }) { h ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        searching = false; hits = emptyList()
                        WeatherStore.addCity(ctx, h); cities = WeatherStore.cities(ctx)
                        load(h)
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PIcon("globe", fg, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(h.name, color = fg, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        // the places as a row of chips: the phone's own place, each saved city (x removes it) and a + that opens the city search
        if (!searching) item {
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item {
                    val on = place?.device == true
                    Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(if (on) T.accent.copy(alpha = .22f) else T.panel).clickable { useDevice() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("compass", if (on) T.accent else fg, 16.dp); Spacer(Modifier.width(6.dp)); Text(tr("My location", "Konumum"), color = if (on) T.accent else fg, fontSize = 14.sp)
                    }
                }
                items(cities, key = { it.lat.toString() + it.lon }) { c ->
                    val on = place?.let { !it.device && it.lat == c.lat && it.lon == c.lon } == true
                    Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(if (on) T.accent.copy(alpha = .22f) else T.panel).clickable { load(c) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name.substringBefore(','), color = if (on) T.accent else fg, fontSize = 14.sp, maxLines = 1)
                        Box(Modifier.size(28.dp).clickable { WeatherStore.removeCity(ctx, c); cities = WeatherStore.cities(ctx) }, contentAlignment = Alignment.Center) { PIcon("close", sub, 14.dp) }
                    }
                }
                item {
                    Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(T.panel).clickable { searching = true; error = "" }, contentAlignment = Alignment.Center) { PIcon("add", fg, 18.dp) }
                }
            }
        }
        if (place == null || (place?.device == true && !hasLocation(ctx))) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(tr("No place yet. Use the phone's location or search a city.", "Henüz konum yok. Telefonun konumunu kullanın ya da bir şehir arayın."), color = sub, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    PButton(tr("Use my location", "Konumumu kullan"), compact = true) { useDevice() }
                }
            }
        }
        fc?.let { f ->
            val today = f.days.firstOrNull()
            val rainSoon = f.hours.take(6).maxOfOrNull { it.rain } ?: 0
            item {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))) {
                WeatherScene(f.code, Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val wh = androidx.compose.ui.graphics.Color.White
                    Text(f.place, color = wh.copy(alpha = .85f), fontSize = 15.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        WeatherGlyph(f.code, 76.dp)
                        Text(t(f.temp), color = wh, fontSize = 68.sp, fontWeight = FontWeight.Light)
                    }
                    Text(WeatherApi.condition(f.code), color = wh, fontSize = 19.sp)
                    Text(tr("Feels like %s", "Hissedilen %s").format(t(f.feels)), color = wh.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    if (today != null) Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .22f)).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Hl(tr("High", "Yüksek"), t(today.max), wh); Hl(tr("Low", "Düşük"), t(today.min), wh); Hl(tr("Rain", "Yağış"), "${today.rain}%", wh)
                    }
                    Text(clock.format(Date(f.at)), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .7f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                }
            }
            if (rainSoon >= 50) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PIcon("cloud", fg, 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Rain likely in the next 6 hours", "Önümüzdeki 6 saatte yağmur bekleniyor"), color = fg, fontSize = 14.sp)
                    }
                }
            }
            // the sun's path today and the moon now, drawn rather than written
            item {
                Column(Modifier.fillMaxWidth().background(T.panel, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(14.dp)) {
                    val now = System.currentTimeMillis()
                    if (today != null) SunArc(today.sunrise, today.sunset, now, fg, sub)
                    Spacer(Modifier.height(12.dp))
                    MoonPhase(now, fg, sub)
                }
            }
            item { Text(tr("Next 24 hours", "Sonraki 24 saat"), color = sub, fontSize = 13.sp) }
            item {
                LazyRow(Modifier.fillMaxWidth().background(T.panel, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(vertical = 12.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(f.hours) { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(WeatherFmt.clock(h.time), color = sub, fontSize = 12.sp)
                            WeatherGlyph(h.code, 30.dp)
                            Text(t(h.temp), color = fg, fontSize = 15.sp)
                        }
                    }
                }
            }
            if (f.hours.size >= 2) {
                item {
                    Text(tr("Temperature", "Sıcaklık"), color = sub, fontSize = 13.sp)
                    Sparkline(f.hours.map { it.temp }, fg)
                    Row(Modifier.fillMaxWidth()) {
                        Text(WeatherFmt.clock(f.hours.first().time), color = sub, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(WeatherFmt.clock(f.hours.last().time), color = sub, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Chance of rain", "Yağış olasılığı"), color = sub, fontSize = 13.sp)
                    Sparkline(f.hours.map { it.rain.toDouble() }, fg, bars = true, top = 100.0)
                }
            }
            item {
                val d = mutableListOf(
                    tr("Wind", "Rüzgar") to WeatherFmt.wind(f.wind, imp) + " " + WeatherFmt.compass(f.windDir),
                    tr("Gusts", "Ani rüzgar") to WeatherFmt.wind(f.gusts, imp),
                    tr("Humidity", "Nem") to "${f.humidity}%",
                    tr("Pressure", "Basınç") to "%.0f hPa".format(f.pressure),
                    tr("Cloud cover", "Bulutluluk") to "${f.cloud}%",
                    tr("Precipitation", "Yağış") to "%.1f mm".format(f.precip),
                )
                if (today != null) {
                    d += tr("UV index", "UV indeksi") to "%.0f · %s".format(today.uv, WeatherFmt.uv(today.uv))
                    d += tr("Sunshine", "Güneşlenme") to "%.1f h".format(today.sun)
                    d += tr("Sunrise", "Gün doğumu") to WeatherFmt.clock(today.sunrise)
                    d += tr("Sunset", "Gün batımı") to WeatherFmt.clock(today.sunset)
                    d += tr("Day length", "Gündüz süresi") to WeatherFmt.dayLength(today.sunrise, today.sunset)
                }
                if (f.aqi >= 0) d += tr("Air quality", "Hava kalitesi") to "${f.aqi} · ${WeatherFmt.aqi(f.aqi)}"
                d += tr("Moon", "Ay") to WeatherFmt.moon()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.chunked(2).forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            r.forEach { (l, v) -> Tile(l, v, fg, sub, Modifier.weight(1f)) }
                            if (r.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            items(f.days, key = { it.date }) { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        runCatching { LocalDate.parse(d.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }.getOrDefault(d.date),
                        color = fg, fontSize = 15.sp, modifier = Modifier.width(64.dp),
                    )
                    WeatherGlyph(d.code, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (d.rain > 0) "${d.rain}%" else "", color = sub, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    Spacer(Modifier.weight(1f))
                    Text("${t(d.min)} / ${t(d.max)}", color = fg, fontSize = 15.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Severe weather alerts", "Sert hava uyarıları"), color = fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                PSwitch(alerts, { setAlerts(it) })
            }
        }
        if (busy) item { Text(tr("Loading...", "Yükleniyor..."), color = sub, fontSize = 13.sp) }
        if (error.isNotEmpty()) item { Text(error, color = sub, fontSize = 13.sp) }
    }
}

/** One label and value row of the details list. */
@Composable
private fun Detail(label: String, value: String, fg: androidx.compose.ui.graphics.Color, sub: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = fg, fontSize = 14.sp)
    }
}

@Composable
private fun Hl(label: String, value: String, c: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = c.copy(alpha = .75f), fontSize = 12.sp)
        Text(value, color = c, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

/** One fact of the weather in its own rounded tile: the name small, the value big. */
@Composable
private fun Tile(label: String, value: String, fg: androidx.compose.ui.graphics.Color, sub: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.background(T.panel, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = sub, fontSize = 12.sp)
        Text(value, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}
