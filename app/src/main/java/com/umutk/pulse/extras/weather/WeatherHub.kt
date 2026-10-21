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
        if (!searching && cities.isNotEmpty()) {
            items(cities, key = { it.lat.toString() + it.lon }) { c ->
                Row(Modifier.fillMaxWidth().clickable { load(c) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("globe", fg, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, color = fg, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clickable { WeatherStore.removeCity(ctx, c); cities = WeatherStore.cities(ctx) }, contentAlignment = Alignment.Center) {
                        PIcon("close", sub, 18.dp)
                    }
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
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(T.panel, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(vertical = 18.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(f.place, color = sub, fontSize = 14.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PIcon(WeatherApi.icon(f.code), fg, 64.dp)
                        Text(t(f.temp), color = fg, fontSize = 64.sp, fontWeight = FontWeight.Light)
                    }
                    Text(WeatherApi.condition(f.code), color = fg, fontSize = 18.sp)
                    Text(tr("Feels like %s", "Hissedilen %s").format(t(f.feels)), color = sub, fontSize = 13.sp)
                    if (today != null) {
                        Text(tr("High %s · Low %s", "En yüksek %s · En düşük %s").format(t(today.max), t(today.min)), color = sub, fontSize = 13.sp)
                    }
                    // a quick row under the hero: today's rain chance and the high / low, with icons so the card reads at a glance
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (today != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) { PIcon("cloud", sub, 18.dp); Spacer(Modifier.width(6.dp)); Text("${today.rain}%", color = fg, fontSize = 14.sp) }
                            Row(verticalAlignment = Alignment.CenterVertically) { PIcon("history", sub, 18.dp); Spacer(Modifier.width(6.dp)); Text("${t(today.max)} / ${t(today.min)}", color = fg, fontSize = 14.sp) }
                        }
                    }
                    Text(clock.format(Date(f.at)), color = sub, fontSize = 11.sp)
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(f.hours) { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(WeatherFmt.clock(h.time), color = sub, fontSize = 12.sp)
                            PIcon(WeatherApi.icon(h.code), fg, 24.dp)
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
                Column {
                    Detail(tr("Wind", "Rüzgar"), WeatherFmt.wind(f.wind, imp) + " " + WeatherFmt.compass(f.windDir), fg, sub)
                    Detail(tr("Gusts", "Ani rüzgar"), WeatherFmt.wind(f.gusts, imp), fg, sub)
                    Detail(tr("Humidity", "Nem"), "${f.humidity}%", fg, sub)
                    Detail(tr("Pressure", "Basınç"), "%.0f hPa".format(f.pressure), fg, sub)
                    Detail(tr("Cloud cover", "Bulutluluk"), "${f.cloud}%", fg, sub)
                    Detail(tr("Precipitation now", "Şu an yağış"), "%.1f mm".format(f.precip), fg, sub)
                    if (today != null) Detail(tr("Sunshine today", "Bugün güneşlenme"), "%.1f h".format(today.sun), fg, sub)
                    if (today != null) {
                        Detail(tr("UV index", "UV indeksi"), "%.0f · %s".format(today.uv, WeatherFmt.uv(today.uv)), fg, sub)
                        Detail(tr("Chance of rain today", "Bugün yağış olasılığı"), "${today.rain}%", fg, sub)
                        Detail(tr("Sunrise", "Gün doğumu"), WeatherFmt.clock(today.sunrise), fg, sub)
                        Detail(tr("Sunset", "Gün batımı"), WeatherFmt.clock(today.sunset), fg, sub)
                        Detail(tr("Day length", "Gündüz süresi"), WeatherFmt.dayLength(today.sunrise, today.sunset), fg, sub)
                    }
                    if (f.aqi >= 0) Detail(tr("Air quality", "Hava kalitesi"), "${f.aqi} · ${WeatherFmt.aqi(f.aqi)}", fg, sub)
                    Detail(tr("Moon", "Ay"), WeatherFmt.moon(), fg, sub)
                }
            }
            items(f.days, key = { it.date }) { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        runCatching { LocalDate.parse(d.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }.getOrDefault(d.date),
                        color = fg, fontSize = 15.sp, modifier = Modifier.width(64.dp),
                    )
                    PIcon(WeatherApi.icon(d.code), fg, 22.dp)
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
