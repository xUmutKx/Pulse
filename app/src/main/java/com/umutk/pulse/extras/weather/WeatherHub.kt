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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    var showList by remember { mutableStateOf(false) }
    var allDays by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(WeatherStore.source(ctx)) }
    var skin by remember { mutableStateOf(WeatherStore.skin(ctx)) }
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
                        val ex = WeatherApi.extras(spot.lat, spot.lon, source == "mgm", WeatherStore.combine(ctx))
                        WeatherStore.saveCache(ctx, spot.name, raw, aqi, ex)
                        WeatherApi.parse(spot.name, raw, System.currentTimeMillis(), aqi, ex)
                    }
                }
                res.onSuccess {
                    fc = it; place = spot; WeatherStore.setPlace(ctx, spot)
                    WeatherWidgets.refreshAll(ctx)
                }.onFailure { error = tr("Could not load the weather.", "Hava durumu alınamadı.") + WeatherApi.lastError.let { if (it.isBlank()) "" else "\n$it" } }
            }
            busy = false
        }
    }
    // fine location too, not only coarse: it lets deviceLocation() fall back to GPS when the network provider has nothing (Wi-Fi scanning off, no
    // Play Services, a cold start) — that gap used to be the usual way this ended up saying it could not find a place
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) load(devicePlace) else error = tr("Location is off. Search a city.", "Konum kapalı. Bir şehir arayın.")
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun useDevice() { if (hasLocation(ctx)) load(devicePlace) else ask.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
    fun search() {
        if (query.isBlank()) return
        scope.launch {
            busy = true; error = ""
            hits = withContext(Dispatchers.IO) { WeatherApi.lastError = ""; runCatching { WeatherApi.search(query, ctx) }.getOrDefault(emptyList()) }
            if (hits.isEmpty()) error = tr("No city found.", "Şehir bulunamadı.") + WeatherApi.lastError.let { if (it.isBlank()) "" else "\n$it" }
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

    LaunchedEffect(Unit) { WeatherAuto.ensure(ctx) }
    BackHandler { if (showSettings) showSettings = false else if (searching) searching = false else if (showList) showList = false else onBack() }
    // first paint from the cache, then one refresh for a saved city or the phone's place when it is allowed
    LaunchedEffect(Unit) {
        val p = place
        if (p != null && (!p.device || hasLocation(ctx))) load(p)
    }

    val sky = skin != "app"
    val fg = if (sky) Color.White else T.text
    val sub = if (sky) Color.White.copy(alpha = .72f) else T.sub
    WxTheme.panel = when (skin) { "sky" -> Color.White.copy(alpha = .16f); "amoled" -> Color(0xFF141414); else -> T.panel }
    val bgBrush: Brush = when (skin) {
        "sky" -> Brush.verticalGradient(skyColors(fc?.code ?: 0, fc?.isDay ?: true))
        "amoled" -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        else -> Brush.verticalGradient(listOf(com.umutk.pulse.pageBg, com.umutk.pulse.pageBg))
    }
    val order = listOf(devicePlace) + cities
    fun go(delta: Int) {
        val cur = order.indexOfFirst { p -> place?.let { q -> if (q.device) p.device else (!p.device && p.lat == q.lat && p.lon == q.lon) } == true }.coerceAtLeast(0)
        val to = (cur + delta).coerceIn(0, order.lastIndex)
        if (to != cur) { val p = order[to]; if (p.device) useDevice() else load(p) }
    }
    if (showSettings) {
        WeatherSettings(fg, sub, bgBrush, skin, imp, source, alerts, WeatherStore.combine(ctx), { WeatherStore.setCombine(ctx, it); place?.let { p -> load(p) } },
            onSkin = { skin = it; WeatherStore.setSkin(ctx, it) },
            onUnits = { imp = it; WeatherStore.setUnits(ctx, if (it) "f" else "c") },
            onSource = { source = it; WeatherStore.setSource(ctx, it); place?.let { p -> load(p) } },
            onAlerts = { setAlerts(it) },
            onBack = { showSettings = false })
        return
    }
    if (showList) {
        PlacesPage(order, place, fg, sub, bgBrush, imp, hasLocation(ctx),
            onPick = { p -> showList = false; if (p.device) useDevice() else load(p) },
            onRemove = { c -> WeatherStore.removeCity(ctx, c); cities = WeatherStore.cities(ctx) },
            onAdd = { showList = false; searching = true; error = "" },
            onBack = { showList = false })
        return
    }
    // adding a place has a page of its own: the field is ready for typing, the cities are looked up while typing, a tap adds one and goes back
    if (searching) {
        AddPlacePage(fg, sub, bgBrush, { searching = false },
            onPick = { h -> searching = false; WeatherStore.addCity(ctx, h); cities = WeatherStore.cities(ctx); load(h) })
        return
    }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    fun t(v: Double) = WeatherFmt.temp(v, imp)
    // a swipe to the side goes to the next place of the list
    var dragSum by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize().background(bgBrush).pointerInput(place, cities) {
        detectHorizontalDragGestures(onDragStart = { dragSum = 0f }, onDragEnd = { if (dragSum < -160f) go(1) else if (dragSum > 160f) go(-1); dragSum = 0f }) { _, dx -> dragSum += dx }
    }) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PIcon("back", fg, 22.dp, Modifier.clickable { if (searching) searching = false else onBack() })
                Text(tr("Weather", "Hava durumu"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                com.umutk.pulse.ExtraLookIcon(fg)
                Box(Modifier.size(40.dp).clickable { showList = true }, contentAlignment = Alignment.Center) { PIcon("details", fg, 22.dp) }
                Box(Modifier.size(40.dp).clickable { place?.let { load(it) } }, contentAlignment = Alignment.Center) { PIcon("refresh", fg, 22.dp) }
                Box(Modifier.size(40.dp).clickable { showSettings = true }, contentAlignment = Alignment.Center) { PIcon("settings", fg, 22.dp) }
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
                    Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(if (on) T.accent.copy(alpha = .22f) else WxTheme.panel).clickable { useDevice() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("compass", if (on) T.accent else fg, 16.dp); Spacer(Modifier.width(6.dp)); Text(tr("My location", "Konumum"), color = if (on) T.accent else fg, fontSize = 14.sp)
                    }
                }
                items(cities, key = { it.lat.toString() + it.lon }) { c ->
                    val on = place?.let { !it.device && it.lat == c.lat && it.lon == c.lon } == true
                    Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(if (on) T.accent.copy(alpha = .22f) else WxTheme.panel).clickable { load(c) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name.substringBefore(','), color = if (on) T.accent else fg, fontSize = 14.sp, maxLines = 1)
                        Box(Modifier.size(28.dp).clickable { WeatherStore.removeCity(ctx, c); cities = WeatherStore.cities(ctx) }, contentAlignment = Alignment.Center) { PIcon("close", sub, 14.dp) }
                    }
                }
                item {
                    Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(WxTheme.panel).clickable { searching = true; error = "" }, contentAlignment = Alignment.Center) { PIcon("add", fg, 18.dp) }
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
                WeatherScene(f.code, Modifier.matchParentSize(), day = f.isDay)
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val wh = androidx.compose.ui.graphics.Color.White
                    Text(f.place, color = wh.copy(alpha = .85f), fontSize = 15.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        WeatherGlyph(f.code, 76.dp, day = f.isDay)
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
            // the hours: 96 of them, the row runs on to the right as it is swiped; a day name stands at each midnight
            item { Text(tr("Next hours", "Sonraki saatler"), color = sub, fontSize = 13.sp) }
            item {
                LazyRow(Modifier.fillMaxWidth().background(WxTheme.panel, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(vertical = 12.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // the first one is the hour it is now: it is lit
                    itemsIndexed(if (f.hours48.isNotEmpty()) f.hours48 else f.hours) { hi, h ->
                        Column(Modifier.then(if (hi == 0) Modifier.background(Color.White.copy(alpha = .20f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 6.dp) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                            val midnight = h.time.length >= 16 && h.time.substring(11, 16) == "00:00"
                            Text(if (midnight) runCatching { LocalDate.parse(h.time.take(10)).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }.getOrDefault("") else WeatherFmt.clock(h.time), color = if (midnight) fg else sub, fontSize = 12.sp, fontWeight = if (midnight) FontWeight.Medium else FontWeight.Normal)
                            WeatherGlyph(h.code, 30.dp, day = h.time.length < 16 || h.time.substring(11, 13).toIntOrNull()?.let { it in 6..19 } != false)
                            Text(t(h.temp), color = fg, fontSize = 15.sp)
                            Text(if (h.rain > 0) "${h.rain}%" else "", color = sub, fontSize = 10.sp)
                        }
                    }
                }
            }
            item { Text(tr("Next days", "Sonraki günler"), color = sub, fontSize = 13.sp) }
            // five days at first; the rest of the 16 open with one tap
            items(if (allDays) f.days else f.days.take(5), key = { it.date }) { d ->
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
            if (f.days.size > 5) item { Text(if (allDays) tr("Show 5 days", "5 günü göster") else tr("Show ${f.days.size} days", "${f.days.size} günü göster"), color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { allDays = !allDays }.padding(vertical = 8.dp)) }
            // the sun's path today and the moon now, drawn rather than written
            item {
                Column(Modifier.fillMaxWidth().background(WxTheme.panel, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(14.dp)) {
                    val now = System.currentTimeMillis()
                    if (today != null) SunArc(today.sunrise, today.sunset, now, fg, sub)
                    Spacer(Modifier.height(12.dp))
                    MoonPhase(now, fg, sub)
                }
            }
            // today against yesterday, and the facts of the moment as cards: pollen, UV, humidity, wind, dew point, pressure, visibility
            if (!f.yMax.isNaN() && today != null) item { VsYesterday(today.max - f.yMax, imp, fg, sub) }
            if (f.pollen.isNotEmpty()) item { PollenCard(f.pollen, fg, sub) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UvCard(today?.uv ?: 0.0, fg, sub, Modifier.weight(1f).fillMaxHeight()); HumidityCard(f.humidity, tr("Humidity", "Nem"), fg, sub, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WindCard(f.wind, f.windDir, imp, fg, sub, Modifier.weight(1f).fillMaxHeight())
                        ValueCard(tr("Dew point", "Çiy noktası"), t(f.dew), if (f.dew > 18) tr("Noticeable humidity", "Nem hissediliyor") else tr("Comfortable", "Rahat"), fg, sub, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressureCard(f.pressure, fg, sub, Modifier.weight(1f).fillMaxHeight())
                        ValueCard(tr("Visibility", "Görüş"), if (f.visKm >= 0) "%.1f km".format(f.visKm) else "—", if (f.visKm >= 10) tr("Good right now", "Şu an iyi") else if (f.visKm >= 0) tr("Reduced", "Azalmış") else "", fg, sub, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            if (f.hours.size >= 2) {
                item {
                    Text(tr("Temperature", "Sıcaklık"), color = sub, fontSize = 13.sp)
                    Sparkline(f.hours.map { it.temp }, fg, label = { t(it) })
                    // the numbers under the chart: now, the highest and the lowest of the hours with the time they happen
                    val hiH = f.hours.maxByOrNull { it.temp }!!; val loH = f.hours.minByOrNull { it.temp }!!
                    Text(tr("Now ${t(f.hours.first().temp)} · High ${t(hiH.temp)} at ${WeatherFmt.clock(hiH.time)} · Low ${t(loH.temp)} at ${WeatherFmt.clock(loH.time)}",
                        "Şimdi ${t(f.hours.first().temp)} · En yüksek ${t(hiH.temp)} (${WeatherFmt.clock(hiH.time)}) · En düşük ${t(loH.temp)} (${WeatherFmt.clock(loH.time)})"), color = fg, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(WeatherFmt.clock(f.hours.first().time), color = sub, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(WeatherFmt.clock(f.hours.last().time), color = sub, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Chance of rain", "Yağış olasılığı"), color = sub, fontSize = 13.sp)
                    Sparkline(f.hours.map { it.rain.toDouble() }, fg, bars = true, top = 100.0, label = { "${it.toInt()}%" })
                    val rainH = f.hours.maxByOrNull { it.rain }!!
                    Text(tr("Highest chance ${rainH.rain}% at ${WeatherFmt.clock(rainH.time)} · average ${f.hours.map { it.rain }.average().toInt()}%",
                        "En yüksek olasılık %${rainH.rain} (${WeatherFmt.clock(rainH.time)}) · ortalama %${f.hours.map { it.rain }.average().toInt()}"), color = fg, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            item {
                val d = mutableListOf(
                    tr("Gusts", "Ani rüzgar") to WeatherFmt.wind(f.gusts, imp),
                    tr("Cloud cover", "Bulutluluk") to "${f.cloud}%",
                    tr("Precipitation", "Yağış") to "%.1f mm".format(f.precip),
                )
                if (today != null) {
                    d += tr("Sunshine", "Güneşlenme") to "%.1f h".format(today.sun)
                }
                if (f.aqi >= 0) d += tr("Air quality", "Hava kalitesi") to "${f.aqi} · ${WeatherFmt.aqi(f.aqi)}"
                // an odd count leaves one tile alone on its row: the dew point fills the gap, so the grid is even
                if (d.size % 2 == 1) d += tr("Dew point", "Çiy noktası") to t(f.dew)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.chunked(2).forEach { r ->
                        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            r.forEach { (l, v) -> Tile(l, v, fg, sub, Modifier.weight(1f).fillMaxHeight()) }
                            if (r.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        fc?.let { f -> if (f.viaMgm) item { Text(tr("Now and the first hours: MGM. Longer ranges: Open-Meteo.", "Şu an ve ilk saatler: MGM. Uzun vadeli: Open-Meteo."), color = sub, fontSize = 11.sp) } }
        fc?.let { f -> if (f.sources >= 2) item { Text(tr("Next 24 hours: ${f.sources} forecasts combined", "Sonraki 24 saat: ${f.sources} tahmin birleştirildi"), color = sub, fontSize = 11.sp) } }
        if (busy) item { Text(tr("Loading...", "Yükleniyor..."), color = sub, fontSize = 13.sp) }
        if (error.isNotEmpty()) item { Text(error, color = sub, fontSize = 13.sp) }
    }
    }
}

/** The colours of the sky page: a gradient that follows the weather and whether it is day. */
private fun skyColors(code: Int, day: Boolean): List<Color> = when {
    !day && code <= 2 -> listOf(Color(0xFF0B1030), Color(0xFF1B2A5C))
    !day -> listOf(Color(0xFF1A2236), Color(0xFF2F3B58))
    code == 0 || code == 1 -> listOf(Color(0xFF2F80ED), Color(0xFF56CCF2))
    code == 2 -> listOf(Color(0xFF3A7BD5), Color(0xFF7FB6E8))
    code == 3 -> listOf(Color(0xFF4A6FA5), Color(0xFF8FA8C8))
    code == 45 || code == 48 -> listOf(Color(0xFF7B8A99), Color(0xFFA5B1BD))
    code in 71..77 || code == 85 || code == 86 -> listOf(Color(0xFF6C85A8), Color(0xFFB8C6DB))
    code >= 95 -> listOf(Color(0xFF1F2638), Color(0xFF3B4560))
    else -> listOf(Color(0xFF2E3A59), Color(0xFF52607D))
}

/** The panel colour of the weather page, set by the chosen look of the page (a see-through white on the sky, dark grey on black). */
internal object WxTheme { var panel by mutableStateOf(Color.Unspecified) }

/** The list of places: a card for the phone's place and for each saved city, with the weather now and the day's range; a tap opens it. */
@Composable
private fun PlacesPage(order: List<Place>, current: Place?, fg: Color, sub: Color, bg: Brush, imp: Boolean, hasLoc: Boolean,
    onPick: (Place) -> Unit, onRemove: (Place) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clock = remember { SimpleDateFormat("EEE, MMM d 'at' HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().background(bg).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", fg, 22.dp, Modifier.clickable { onBack() })
            Text(tr("Weather", "Hava durumu"), color = fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Box(Modifier.size(40.dp).clickable { onAdd() }, contentAlignment = Alignment.Center) { PIcon("add", fg, 22.dp) }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(order, key = { if (it.device) "device" else it.lat.toString() + it.lon }) { p ->
                val f by produceState<Forecast?>(null, p.lat, p.lon, p.device, hasLoc) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            val s = if (p.device) (if (hasLoc) deviceLocation(ctx)?.let { Place(p.name, it.latitude, it.longitude, true) } else null) else p
                            s?.let { WeatherApi.parse(p.name, WeatherApi.forecast(it.lat, it.lon), System.currentTimeMillis()) }
                        }.getOrNull()
                    }
                }
                val d = f?.days?.firstOrNull()
                Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(WxTheme.panel).clickable { onPick(p) }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (p.device) { PIcon("compass", fg, 16.dp); Spacer(Modifier.width(6.dp)) }
                            Text(if (p.device) (f?.place ?: p.name) else p.name.substringBefore(','), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Text(if (p.device) tr("My location", "Konumum") else p.name.substringAfter(',', "").trim(), color = sub, fontSize = 13.sp, maxLines = 1)
                        f?.let { Text(clock.format(Date(it.at)), color = sub, fontSize = 12.sp) }
                    }
                    f?.let { x ->
                        WeatherGlyph(x.code, 44.dp)
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                            Text(WeatherFmt.temp(x.temp, imp), color = fg, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                            if (d != null) Text(WeatherFmt.temp(d.max, imp) + " / " + WeatherFmt.temp(d.min, imp), color = sub, fontSize = 12.sp)
                        }
                    } ?: Text(if (p.device && !hasLoc) tr("Location off", "Konum kapalı") else "…", color = sub, fontSize = 13.sp)
                    if (!p.device) Box(Modifier.size(34.dp).clickable { onRemove(p) }, contentAlignment = Alignment.Center) { PIcon("close", sub, 16.dp) }
                }
            }
            item { Text(tr("The place at the top of the list is the one the widgets and alerts use.", "Listenin en üstündeki yer, widget ve uyarılarda kullanılır."), color = sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp)) }
        }
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
    Column(modifier.background(WxTheme.panel, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = sub, fontSize = 12.sp)
        Text(value, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The weather settings: how the page looks, the unit, where the data comes from, the alerts. Full rows that open a list, like the other settings screens. */
@Composable
private fun WeatherSettings(fg: Color, sub: Color, bg: Brush, skin: String, imp: Boolean, source: String, alerts: Boolean, combine: Boolean, onCombine: (Boolean) -> Unit,
                            onSkin: (String) -> Unit, onUnits: (Boolean) -> Unit, onSource: (String) -> Unit, onAlerts: (Boolean) -> Unit, onBack: () -> Unit) {
    var ask by remember { mutableStateOf("") }
    val skins = listOf("sky" to tr("Sky (follows the weather)", "Gökyüzü (havaya göre)"), "amoled" to "AMOLED", "app" to tr("App look", "Uygulama teması"))
    val sources = listOf("om" to "Open-Meteo", "mgm" to tr("MGM + Open-Meteo (Turkey)", "MGM + Open-Meteo (Türkiye)"), "metno" to tr("MET Norway (no key)", "MET Norway (anahtarsız)"), "owm" to tr("OpenWeatherMap (your key)", "OpenWeatherMap (kendi anahtarınız)"))
    val wctx = LocalContext.current
    @Composable fun Row2(title: String, value: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = fg, fontSize = 16.sp, modifier = Modifier.weight(1f)); Text(value, color = sub, fontSize = 14.sp)
        }
    }
    Column(Modifier.fillMaxSize().background(bg).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", fg, 22.dp, Modifier.clickable(onClick = onBack))
            Text(tr("Weather settings", "Hava durumu ayarları"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
        Row2(tr("Colours", "Renkler"), skins.first { it.first == skin }.second) { ask = "skin" }
        Row2(tr("Temperature unit", "Sıcaklık birimi"), if (imp) "°F" else "°C") { ask = "units" }
        Row2(tr("Data source", "Veri kaynağı"), sources.first { it.first == source }.second) { ask = "source" }
        if (source == "owm") {
            var key by remember { mutableStateOf(WeatherStore.owmKey(wctx)) }
            PField(key, { key = it; WeatherStore.setOwmKey(wctx, it) }, tr("OpenWeatherMap key", "OpenWeatherMap anahtarı"), Modifier.fillMaxWidth())
        }
        var comb by remember { mutableStateOf(combine) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Combine several forecasts", "Birden fazla tahmini birleştir"), color = fg, fontSize = 16.sp)
                Text(tr("The next 24 hours: four models through Open-Meteo (best match, ECMWF, GFS, ICON) and met.no, plus MGM for places in Turkey when it is the source; the middle temperature and the sky most of them agree on", "Sonraki 24 saat: Open-Meteo üzerinden dört model (en iyi eşleşme, ECMWF, GFS, ICON) ve met.no, kaynak MGM ise Türkiye için MGM de; orta sıcaklık ve çoğunun uyuştuğu gökyüzü"), color = sub, fontSize = 12.sp)
            }
            PSwitch(comb, { comb = it; onCombine(it) })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Severe weather alerts", "Sert hava uyarıları"), color = fg, fontSize = 16.sp, modifier = Modifier.weight(1f))
            PSwitch(alerts, onAlerts)
        }
        // the report in the notification shade: every six hours, and at once when rain is coming
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var report by remember { mutableStateOf(WeatherReport.enabled(ctx)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Weather notification", "Hava durumu bildirimi"), color = fg, fontSize = 16.sp)
                Text(tr("Every six hours, and when rain is coming", "Altı saatte bir ve yağmur geleceğinde"), color = sub, fontSize = 12.sp)
            }
            PSwitch(report, { report = it; WeatherReport.setEnabled(ctx, it) })
        }
        Text(tr("MGM gives the station values of now and the first hours for places in Turkey; the 96 hours and 16 days come from Open-Meteo.", "MGM, Türkiye'deki yerler için şu anın ve ilk saatlerin istasyon değerlerini verir; 96 saat ve 16 gün Open-Meteo'dan gelir."), color = sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
    when (ask) {
        "skin" -> com.umutk.pulse.extras.video.PickDialog(tr("Colours", "Renkler"), skins.map { it.second to (it.first == skin) }, { ask = "" }) { onSkin(skins[it].first); ask = "" }
        "units" -> com.umutk.pulse.extras.video.PickDialog(tr("Temperature unit", "Sıcaklık birimi"), listOf("°C" to !imp, "°F" to imp), { ask = "" }) { onUnits(it == 1); ask = "" }
        "source" -> com.umutk.pulse.extras.video.PickDialog(tr("Data source", "Veri kaynağı"), sources.map { it.second to (it.first == source) }, { ask = "" }) { onSource(sources[it].first); ask = "" }
    }
}


/** The page that adds a place: a city field with a search as you type, and the matches to tap. */
@Composable
private fun AddPlacePage(fg: Color, sub: Color, bg: Brush, onBack: () -> Unit, onPick: (Place) -> Unit) {
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<Place>()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val searchCtx = LocalContext.current
    // a short pause after the last key, then the lookup
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); note = ""; return@LaunchedEffect }
        kotlinx.coroutines.delay(450)
        busy = true
        val r = withContext(Dispatchers.IO) { WeatherApi.lastError = ""; runCatching { WeatherApi.search(q, searchCtx) } }
        hits = r.getOrDefault(emptyList())
        val why = WeatherApi.lastError.let { if (it.isBlank()) "" else "\n$it" }
        note = if (r.isFailure || (hits.isEmpty() && why.isNotEmpty())) tr("No connection.", "Bağlantı yok.") + why else if (hits.isEmpty()) tr("No city found.", "Şehir bulunamadı.") else ""
        busy = false
    }
    Box(Modifier.fillMaxSize().background(bg)) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PIcon("back", fg, 22.dp, Modifier.clickable { onBack() })
                    Text(tr("Add a place", "Yer ekle"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }
            }
            item { PField(q, { q = it }, tr("City", "Şehir"), Modifier.fillMaxWidth().focusRequester(focus)) }
            if (busy) item { Text("…", color = sub, fontSize = 14.sp) }
            if (note.isNotEmpty() && !busy) item { Text(note, color = sub, fontSize = 14.sp) }
            items(hits, key = { it.lat.toString() + it.lon }) { h ->
                Row(Modifier.fillMaxWidth().clickable { onPick(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("globe", fg, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(h.name, color = fg, fontSize = 15.sp, maxLines = 2)
                }
            }
        }
    }
}
