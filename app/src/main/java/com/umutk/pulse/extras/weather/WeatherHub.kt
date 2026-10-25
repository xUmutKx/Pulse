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
    var showList by remember { mutableStateOf(false) }
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
                        val ex = WeatherApi.extras(spot.lat, spot.lon)
                        WeatherStore.saveCache(ctx, spot.name, raw, aqi, ex)
                        WeatherApi.parse(spot.name, raw, System.currentTimeMillis(), aqi, ex)
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

    LaunchedEffect(Unit) { WeatherAuto.ensure(ctx) }
    BackHandler { if (searching) searching = false else if (showList) showList = false else onBack() }
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
    if (showList) {
        PlacesPage(order, place, fg, sub, bgBrush, imp, hasLocation(ctx),
            onPick = { p -> showList = false; if (p.device) useDevice() else load(p) },
            onRemove = { c -> WeatherStore.removeCity(ctx, c); cities = WeatherStore.cities(ctx) },
            onAdd = { showList = false; searching = true; error = "" },
            onBack = { showList = false })
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
                Box(Modifier.clickable { imp = !imp; WeatherStore.setUnits(ctx, if (imp) "f" else "c") }.padding(horizontal = 6.dp, vertical = 8.dp)) {
                    Text(if (imp) "°F" else "°C", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                com.umutk.pulse.ExtraLookIcon(fg)
                Box(Modifier.size(40.dp).clickable { showList = true }, contentAlignment = Alignment.Center) { PIcon("details", fg, 22.dp) }
                Box(Modifier.size(40.dp).clickable { skin = when (skin) { "sky" -> "amoled"; "amoled" -> "app"; else -> "sky" }; WeatherStore.setSkin(ctx, skin) }, contentAlignment = Alignment.Center) { Text("◐", color = fg, fontSize = 20.sp) }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UvCard(today?.uv ?: 0.0, fg, sub, Modifier.weight(1f)); HumidityCard(f.humidity, tr("Humidity", "Nem"), fg, sub, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WindCard(f.wind, f.windDir, imp, fg, sub, Modifier.weight(1f))
                        ValueCard(tr("Dew point", "Çiy noktası"), t(f.dew), if (f.dew > 18) tr("Noticeable humidity", "Nem hissediliyor") else tr("Comfortable", "Rahat"), fg, sub, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressureCard(f.pressure, fg, sub, Modifier.weight(1f))
                        ValueCard(tr("Visibility", "Görüş"), if (f.visKm >= 0) "%.1f km".format(f.visKm) else "—", if (f.visKm >= 10) tr("Good right now", "Şu an iyi") else if (f.visKm >= 0) tr("Reduced", "Azalmış") else "", fg, sub, Modifier.weight(1f))
                    }
                }
            }
            var hours48 by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hours48) tr("Next 48 hours", "Sonraki 48 saat") else tr("Next 24 hours", "Sonraki 24 saat"), color = sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(if (hours48) "24" else "48", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(WxTheme.panel).clickable { hours48 = !hours48 }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            item {
                LazyRow(Modifier.fillMaxWidth().background(WxTheme.panel, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(vertical = 12.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(if (hours48 && f.hours48.isNotEmpty()) f.hours48 else f.hours) { h ->
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
                    tr("Gusts", "Ani rüzgar") to WeatherFmt.wind(f.gusts, imp),
                    tr("Cloud cover", "Bulutluluk") to "${f.cloud}%",
                    tr("Precipitation", "Yağış") to "%.1f mm".format(f.precip),
                )
                if (today != null) {
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
