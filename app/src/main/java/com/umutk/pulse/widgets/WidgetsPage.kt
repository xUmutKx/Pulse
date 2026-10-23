package com.umutk.pulse.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHome
import com.umutk.pulse.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.R
import com.umutk.pulse.T
import com.umutk.pulse.tr
import com.umutk.pulse.extras.music.skin.WINAMP_H
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.widgets.music.WinampWidget
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.umutk.pulse.widgets.music.MusicWidget
import com.umutk.pulse.widgets.music.MusicWidgetBig
import com.umutk.pulse.widgets.music.MusicWidgetSlim
import com.umutk.pulse.widgets.music.MusicWidgetSquare
import com.umutk.pulse.extras.music.skin.loadSkinPreview
import androidx.compose.ui.graphics.asAndroidBitmap
import com.umutk.pulse.extras.music.poweramp.NeutralAccent
import com.umutk.pulse.extras.music.poweramp.PowerampPrefs
import com.umutk.pulse.extras.music.poweramp.powerampSkin
import com.umutk.pulse.extras.music.skin.InstalledSkin
import com.umutk.pulse.extras.music.skin.installedSkins
import com.umutk.pulse.extras.music.skin.loadWinampSkin
import com.umutk.pulse.extras.music.widgets.PowerampWidget
import com.umutk.pulse.extras.music.widgets.WinampSkinPick
import com.umutk.pulse.extras.music.widgets.WinampVisWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One plain (title and value) widget: its name and the provider class that draws it. */
private class WidgetEntry(val label: String, val provider: String)

private val WIDGET_ENTRIES = listOf(
    WidgetEntry("Usage", "UsageWidget"),
    WidgetEntry("Battery", "BatteryWidget"),
    WidgetEntry("Battery health", "BatteryHealthWidget"),
    WidgetEntry("Storage", "StorageWidget"),
    WidgetEntry("Storage bar", "StorageBarWidget"),
    WidgetEntry("Downloads", "DownloadsWidget"),
    WidgetEntry("Last download", "LastDownloadWidget"),
    WidgetEntry("Memory", "MemoryWidget"),
    WidgetEntry("Free RAM", "RamFreeWidget"),
    WidgetEntry("Temperature", "TempWidget"),
    WidgetEntry("Cores", "CoresWidget"),
    WidgetEntry("Network", "NetworkWidget"),
    WidgetEntry("Wi-Fi", "WifiWidget"),
    WidgetEntry("Signal", "SignalWidget"),
    WidgetEntry("Brightness", "BrightnessWidget"),
    WidgetEntry("Clock", "ClockWidget"),
    WidgetEntry("Uptime", "UptimeWidget"),
    WidgetEntry("Next alarm", "NextAlarmWidget"),
    WidgetEntry("App count", "AppCountWidget"),
    WidgetEntry("Quick", "QuickWidget"),
    WidgetEntry("Notes", "com.umutk.pulse.extras.notes.NotesWidget"),
    WidgetEntry("Voice recorder", "VoiceWidget"),
    WidgetEntry("Steps", "StepsWidget"),
    WidgetEntry("Distance and calories", "DistanceWidget"),
    WidgetEntry("Heart rate", "HeartWidget"),
    WidgetEntry("Activity", "ActivityWidget"),
    WidgetEntry("Steps, 7 days", "WeekStepsWidget"),
    WidgetEntry("Weather", "WeatherWidget"),
    WidgetEntry("Weather, wide", "WeatherWideWidget"),
)

// Skins.draw caches art in a plain HashMap, so previews are drawn one at a time
@Suppress("OPT_IN_USAGE")
private val drawer = Dispatchers.Default.limitedParallelism(1)

/** Every design a widget belongs to (theme id to name): the skin families, plus Windows 10, which has only the plain widgets. */
private val DESIGNS: List<Pair<String, String>> = (SkinFamily.values().map { it.theme to it.label } + ("win10" to "Windows 10"))
    .sortedBy { listOf("win11", "win10", "win7", "xp", "win95", "material", "amoled", "glass", "ios").indexOf(it.first).let { i -> if (i < 0) 99 else i } }

/** The design the app's look belongs to; the wallpaper looks (Aurora, Frost and the rest) count as Glass. */
private fun designOf(themeId: String) = if (DESIGNS.any { it.first == themeId }) themeId else "glass"

/** The plain widgets' style for a design: the picked default when it is one of the design's, else its first; null for Glass and iOS. */
private fun styleFor(design: String, picked: WidgetStyle) =
    if (styleLogo(picked) == design) picked else WidgetStyle.values().firstOrNull { styleLogo(it) == design }

private fun designShort(theme: String) = when (theme) {
    "win95" -> "95"; "xp" -> "XP"; "win7" -> "7"; "win10" -> "10"; "win11" -> "11"
    "material" -> "Material"; "amoled" -> "AMOLED"; "glass" -> "Glass"; "ios" -> "iOS"; else -> theme
}

/** Every Pulse widget as the home screen draws it, filed under its design; the logo row shows one design (the app's look first) or all. */
@Composable
fun WidgetsPage() {
    val ctx = LocalContext.current
    var style by remember { mutableStateOf(WidgetStyles.default(ctx)) }
    // the design shown; null shows them all
    var only by remember { mutableStateOf<String?>(designOf(Look.theme.id)) }
    // the design shown first is the app's look: new widgets get its style, not the last one picked in an earlier visit
    LaunchedEffect(Unit) { only?.let { d -> styleFor(d, style)?.let { style = it; WidgetStyles.saveDefault(ctx, it) } }; WidgetPreviews.publish(ctx) }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    // the Winamp group is closed until its arrow is tapped; a search always shows its matches
    var winampOpen by remember { mutableStateOf(false) }
    // the plain widgets of a design: one row is shown, the rest fold under "More" (a search opens them)
    var textOpen by remember { mutableStateOf(setOf<String>()) }
    val providers = remember { WidgetData.all.associateBy { it.javaClass.simpleName } }
    val classOf = remember { SkinWidgets.all.associate { it.skin to it.javaClass.simpleName } }
    val current = designOf(Look.theme.id)
    val designs = DESIGNS.sortedBy { if (it.first == current) 0 else 1 }.filter { d -> only.let { it == null || it == d.first } }
    fun hit(name: String) = query.isBlank() || name.contains(query, ignoreCase = true)
    val oldShown = WIDGET_ENTRIES.filter { hit(it.label) && it.provider in providers }
    // every installed Winamp skin, read off the main thread; each one is a card under Music
    val winampSkins by produceState<List<InstalledSkin>>(emptyList(), ctx) {
        value = withContext(drawer) { runCatching { installedSkins(ctx) }.getOrDefault(emptyList()) }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PHead(tr("Widgets", "Widget'lar"), "${Skin.values().size + FaceWidgets.all.size + WIDGET_ENTRIES.size + 2}") }
        item(key = "tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(tab == 0, tr("Widgets", "Widget'lar")) { tab = 0 }
                PChip(tab == 1, tr("Settings", "Ayarlar")) { tab = 1 }
            }
        }
        if (tab == 1) {
            item(key = "look") { LookSettings(ctx, win11 = only == "win11") }
            return@LazyColumn
        }
        item {
            Row(Modifier.fillMaxWidth().border(1.dp, T.line, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr("Search widgets", "Widget ara"), color = T.sub, fontSize = 14.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "styles") {
            // one design at a time; a second tap on it (or All) shows every design. Picking a design also gives the plain widgets its style.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (DESIGNS.map { it.first } + "all").chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { d ->
                            val all = d == "all"
                            LogoChip(d, if (all) tr("All", "Tümü") else designShort(d), if (all) only == null else only == d, Modifier.weight(1f)) {
                                only = if (all || only == d) null else d
                                if (!all) styleFor(d, style)?.let { style = it; WidgetStyles.saveDefault(ctx, it) }
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        designs.forEach { (theme, label) ->
            val cards = mutableListOf<Pair<String, @Composable () -> Unit>>()
            Skin.values().filter { it.family.theme == theme && hit(it.label) }.forEach { s -> cards += "s_${s.name}" to @Composable { SkinCard(ctx, s, classOf[s]) } }
            FaceWidgets.all.filter { FaceWidgets.theme(it.look) == theme && hit(FaceWidgets.labels[it.look].orEmpty()) }
                .forEach { f -> cards += "f_${f.look}" to @Composable { FaceCard(ctx, f) } }
            // the plain widgets in this design's style; with all designs shown, once, in the picked style, after the first
            val plainStyle = if (only == null) style.takeIf { theme == designs.first().first } else styleFor(theme, style)
            val plain = mutableListOf<Pair<String, @Composable () -> Unit>>()
            if (plainStyle != null) oldShown.forEach { w -> plain += "t_${theme}_${w.provider}" to @Composable { TextCard(ctx, w, providers.getValue(w.provider), plainStyle) } }
            if (cards.isNotEmpty() || plain.isNotEmpty()) {
                item(key = "h_$theme") { FamilyHeader(theme, label) }
                items(cards.chunked(2), key = { it.first().first }) { pair -> PairRow(pair) { it.second() } }
                // one row of the plain widgets stays in view, the others are folded
                items(plain.take(2).chunked(2), key = { it.first().first }) { pair -> PairRow(pair) { it.second() } }
                if (plain.size > 2) {
                    val more = theme in textOpen || query.isNotBlank()
                    item(key = "pm_$theme") { WinampHeader(plain.size - 2, more, tr("More widgets", "Diğer widget'lar")) { textOpen = if (more) textOpen - theme else textOpen + theme } }
                    if (more) items(plain.drop(2).chunked(2), key = { it.first().first }) { pair -> PairRow(pair) { it.second() } }
                }
            }
        }
        // the Winamp widgets belong to no design: one Music header, shown while the search matches a card
        val music = mutableListOf<Pair<String, @Composable () -> Unit>>()
        val winamp = mutableListOf<Pair<String, @Composable () -> Unit>>()
        if (hit("Winamp")) winamp += "m_winamp" to @Composable { WinampCard(ctx, false) }
        if (hit("Winamp 4x1")) winamp += "m_winamp4" to @Composable { WinampCard(ctx, true) }
        if (hit("Winamp visualizer") || hit("Winamp")) winamp += "m_winvis" to @Composable { WinampVisCard(ctx) }
        if (hit("Poweramp")) music += "m_poweramp" to @Composable { PowerampCard(ctx) }
        if (hit("Windows media player CD Player Aero Luna XP Windows 7 Windows 95 Windows 10 Windows 11 WMP medya oynatıcı")) music += "m_winplayer" to @Composable { WinPlayerCard(ctx) }
        if (hit("Music") || hit("Lyrics")) {
            music += "m_music" to @Composable { MusicCard(ctx, "Music", "music.MusicWidget", 2f, compact = false) }
            music += "m_music_sq" to @Composable { MusicCard(ctx, "Music 2x2", "music.MusicWidgetSquare", 1f, compact = false) }
            music += "m_music_big" to @Composable { MusicCard(ctx, "Music 4x3", "music.MusicWidgetBig", 4f / 3f, compact = false) }
            music += "m_music_slim" to @Composable { MusicCard(ctx, "Music bar", "music.MusicWidgetSlim", 4f, compact = true) }
            music += "m_lyrics" to @Composable { LyricsCard(ctx) }
        }
        // the skins are kept apart from the three Winamp widgets: a search shows the skins that match, opened
        val skinCards = mutableListOf<Pair<String, @Composable () -> Unit>>()
        // the Windows Media player skins (pictures of real player windows), each its own widget
        com.umutk.pulse.extras.music.imgskin.ImgSkins.all.forEachIndexed { i, sk ->
            if (hit("Windows Media skin") || hit(sk.name)) skinCards += "m_wms_$i" to @Composable { WmsCard(ctx, i) }
        }
        winampSkins.filter { hit("Winamp") || hit(it.name) }.forEach { s -> skinCards += "m_skin_${s.file.name}" to @Composable { WinampSkinCard(ctx, s) } }
        if (music.isNotEmpty() || winamp.isNotEmpty() || skinCards.isNotEmpty()) {
            item(key = "h_music") { Text(tr("Music", "Müzik"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
        }
        if (winamp.isNotEmpty() || skinCards.isNotEmpty()) {
            // the three Winamp widgets (window, 4x1 strip, visualizer) are always shown; the skins fold under the arrow, and a search opens them
            val open = winampOpen || query.isNotBlank()
            item(key = "h_winamp") { Text("Winamp", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) }
            if (winamp.isNotEmpty()) items(winamp.chunked(2), key = { "w_" + it.first().first }) { pair -> PairRow(pair) { it.second() } }
            if (skinCards.isNotEmpty()) {
                // three skins stay in view, the rest fold under the arrow
                items(skinCards.take(3).chunked(2), key = { it.first().first }) { pair -> PairRow(pair) { it.second() } }
                if (skinCards.size > 3) {
                    item(key = "h_winamp_skins") { WinampHeader(skinCards.size - 3, open) { winampOpen = !winampOpen } }
                    if (open) items(skinCards.drop(3).chunked(2), key = { it.first().first }) { pair -> PairRow(pair) { it.second() } }
                }
            }
        }
        if (music.isNotEmpty()) items(music.chunked(2), key = { it.first().first }) { pair -> PairRow(pair) { it.second() } }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

/** The Settings tab: the accent every widget follows, and the Windows 11 tile's opacity and blur, each shown on the real widgets. */
@Composable
private fun LookSettings(ctx: Context, win11: Boolean) {
    remember { WidgetLook.load(ctx) }
    val previews = listOf(Skin.W11_CPU, Skin.M3_BATTERY, Skin.IOS_RINGS)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            previews.forEach { sk ->
                val bmp by produceState<Bitmap?>(null, WidgetLook.opacity, WidgetLook.blur, WidgetLook.accentColor, WidgetLook.material, WidgetLook.tint, WidgetLook.blurBg) {
                    value = withContext(drawer) { runCatching { Skins.draw(ctx, sk, SkinData.read(ctx)) }.getOrNull() }
                }
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    val b = bmp
                    if (b != null) Image(b.asImageBitmap(), sk.label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else Placeholder()
                }
            }
        }
        PCard {
            Text(tr("Accent colour", "Vurgu rengi"), color = T.text, fontSize = 14.sp)
            (listOf(0) + WidgetAccent.swatches).chunked(8).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { col -> Swatch(col, WidgetAccent.app(ctx), WidgetLook.accentColor == col, T.text, Modifier.weight(1f)) { WidgetLook.accentColor = col; WidgetLook.save(ctx) } }
                    repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(tr("Opacity", "Opaklık"), color = T.text, fontSize = 14.sp)
            var opDesign by remember { mutableStateOf("win11") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("win11" to "Windows 11", "win10" to "Windows 10", "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95", "material" to "Material", "glass" to "Glass", "ios" to "iOS").forEach { (id, n) -> PChip(opDesign == id, n) { opDesign = id } }
            }
            PSlider(WidgetLook.opacityOf(opDesign), { WidgetLook.picked[opDesign] = it }, 0f..1f) { WidgetLook.save(ctx) }
            Text(tr("Blur", "Bulanıklık"), color = T.text, fontSize = 14.sp)
            PSlider(WidgetLook.blur, { WidgetLook.blur = it }, 0f..1f) { WidgetLook.save(ctx) }
            if (win11) {
            Text(tr("Windows 11 material", "Windows 11 malzemesi"), color = T.text, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("acrylic" to "Acrylic", "mica" to "Mica", "blur" to "Blur").forEach { (id, n) -> PChip(WidgetLook.material == id, n) { WidgetLook.material = id; WidgetLook.save(ctx) } }
            }
            if (WidgetLook.material == "blur") {
                Text(tr("Tint (darkness)", "Koyuluk"), color = T.text, fontSize = 14.sp)
                PSlider(WidgetLook.tint, { WidgetLook.tint = it }, 0f..1f) { WidgetLook.save(ctx) }
                Text(tr("Picture behind the blur", "Bulanıklığın arkasındaki resim"), color = T.text, fontSize = 14.sp)
                val pickBg = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
                    if (uri != null) {
                        val ok = runCatching { ctx.contentResolver.openInputStream(uri)?.use { i -> java.io.File(ctx.filesDir, "widget_blur_bg.jpg").outputStream().use { i.copyTo(it) } } }.isSuccess
                        if (ok) { WidgetLook.blurBg = "file"; WidgetLook.save(ctx) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PChip(WidgetLook.blurBg == "stock", tr("Default", "Varsayılan")) { WidgetLook.blurBg = "stock"; WidgetLook.save(ctx) }
                    PChip(WidgetLook.blurBg == "phone", tr("Phone wallpaper", "Telefon duvar kağıdı")) { WidgetLook.blurBg = "phone"; WidgetLook.save(ctx) }
                    PChip(WidgetLook.blurBg == "file", tr("My picture", "Resmim")) { pickBg.launch(arrayOf("image/*")) }
                    PChip(WidgetLook.blurBg == "none", tr("None", "Yok")) { WidgetLook.blurBg = "none"; WidgetLook.save(ctx) }
                }
            }
            }
            Text(tr("Window look", "Pencere görünümü"), color = T.text, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("material" to "Material You", "own" to "Widget's own design", "blur" to "Windows 11 blur").forEach { (id, n) -> PChip(WidgetLook.windowLook == id, n) { WidgetLook.windowLook = id; WidgetLook.save(ctx) } }
            }
        }
    }
}

@Composable
private fun <E> PairRow(pair: List<E>, card: @Composable (E) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pair.forEach { Box(Modifier.weight(1f)) { card(it) } }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FamilyHeader(theme: String, label: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeLogo(theme, 26.dp)
        Text(label, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Collapsible group header: title, how many widgets it holds, and a chevron that turns when open. */
@Composable
private fun WinampHeader(count: Int, open: Boolean, title: String = tr("Skins", "Skinler"), onClick: () -> Unit) {
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "chev")
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("$count", color = T.sub, fontSize = 14.sp)
        Spacer(Modifier.size(8.dp))
        PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = rot })
    }
}

/** A see-through card: the preview square on top, the name and add button below. */
@Composable
private fun WidgetCard(label: String, onAdd: () -> Unit, aspect: Float = 1f, preview: @Composable () -> Unit) {
    // a tap on the card opens its settings as a floating window over the page
    var open by remember { mutableStateOf(false) }
    if (open) {
        val c = LocalContext.current
        com.umutk.pulse.WinAlert(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { Box(Modifier.aspectRatio(aspect)) { preview() } }
                    LookSettings(c, win11 = true)
                }
            },
            confirmButton = { com.umutk.pulse.PButton(tr("Add", "Ekle")) { open = false; onAdd() } },
            dismissButton = { com.umutk.pulse.PButton(tr("Close", "Kapat")) { open = false } },
        )
    }
    Column(Modifier.fillMaxWidth().border(1.dp, T.line, RoundedCornerShape(16.dp)).clickable { open = true }.padding(10.dp)) {
        // the card keeps a square cell; the preview inside takes the widget's own aspect, so wide and tall ones are shown whole and centred
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { Box(Modifier.aspectRatio(aspect)) { preview() } }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AddHome, "Add to home", tint = T.text, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun SkinCard(ctx: Context, skin: Skin, cls: String?) {
    // drawn off the main thread and redrawn every 30 s so clocks and meters stay current
    val bmp by produceState<Bitmap?>(null, skin) {
        while (true) {
            value = withContext(drawer) { runCatching { Skins.draw(ctx, skin, SkinData.read(ctx)) }.getOrNull() }
            delay(30_000)
        }
    }
    WidgetCard(skin.label, { if (cls != null) addToHome(ctx, cls) }, skin.w.toFloat() / skin.h) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), skin.label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Placeholder()
    }
}

@Composable
private fun FaceCard(ctx: Context, f: FaceWidget) {
    // the home-screen views themselves (the frosted clock's panel is a layout background), applied again every 30 s
    val tick by produceState(0) { while (true) { delay(30_000); value++ } }
    val label = FaceWidgets.labels[f.look].orEmpty()
    WidgetCard(label, { addToHome(ctx, f.javaClass.simpleName) }) {
        AndroidView(
            factory = { c -> FrameLayout(c) },
            update = { frame ->
                frame.tag = tick   // reading the tick redraws it
                frame.removeAllViews()
                val v = try { f.views(ctx, f.look, WidgetAccent.app(ctx)).apply(ctx, frame) } catch (e: Exception) { null }
                if (v != null) frame.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                frame.addView(View(ctx).apply { setOnClickListener {} }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TextCard(ctx: Context, w: WidgetEntry, provider: PulseWidget, style: WidgetStyle) {
    // the size the widget really has on the home screen: the plain weather one is a square, the wide one a strip, the rest 264x120
    val real = when (w.provider) {
        "WeatherWidget" -> DpSize(110.dp, 110.dp)
        "WeatherWideWidget" -> DpSize(250.dp, 110.dp)
        else -> DpSize(264.dp, 120.dp)
    }
    // laid out at a real widget size, then scaled down as a whole so the text keeps its proportions (some blank space is fine)
    WidgetCard(w.label, { addToHome(ctx, provider.javaClass.name) }, aspect = real.width / real.height) {
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val k = (maxWidth / real.width) * 0.9f
        Box(Modifier.requiredSize(real.width, real.height).graphicsLayer { scaleX = k; scaleY = k }) {
        AndroidView(
            factory = { c -> FrameLayout(c) },
            update = { frame ->
                frame.removeAllViews()
                // one broken RemoteViews must not take the page down: show a dash instead
                val v = try { provider.content(ctx, style).apply(ctx, frame) } catch (e: Exception) {
                    TextView(ctx).apply { text = "–"; textSize = 24f; gravity = android.view.Gravity.CENTER }
                }
                frame.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
                // the overlay stops a tap from opening Pulse
                frame.addView(View(ctx).apply { setOnClickListener {} }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            },
            modifier = Modifier.fillMaxSize(),
        )
        }
      }
    }
}

/** One Windows Media player skin as its widget draws it: the picture with the title, cover and seek bar on it. */
@Composable
private fun WmsCard(ctx: Context, index: Int) {
    val skin = com.umutk.pulse.extras.music.imgskin.ImgSkins.all[index]
    val bmp by produceState<Bitmap?>(null, index) {
        value = withContext(drawer) {
            runCatching {
                val b = Bitmap.createBitmap(skin.w * 2, skin.h * 2, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(b); c.scale(2f, 2f)
                com.umutk.pulse.extras.music.imgskin.ImgSkins.paint(c, ctx, skin, com.umutk.pulse.extras.music.imgskin.SkinState("Track name", "Artist name", true, 83_000L, 240_000L, null))
                b
            }.getOrNull()
        }
    }
    val cls = listOf(com.umutk.pulse.extras.music.imgskin.WmsBlobWidget::class.java, com.umutk.pulse.extras.music.imgskin.WmsWin7Widget::class.java, com.umutk.pulse.extras.music.imgskin.WmsCandyWidget::class.java,
        com.umutk.pulse.extras.music.imgskin.WmsSilverWidget::class.java, com.umutk.pulse.extras.music.imgskin.WmsLunaWidget::class.java,
        com.umutk.pulse.extras.music.imgskin.WmsBubbleWidget::class.java, com.umutk.pulse.extras.music.imgskin.WmsAeroWidget::class.java, com.umutk.pulse.extras.music.imgskin.WmsNexusWidget::class.java,
        com.umutk.pulse.extras.music.imgskin.WmsSymphoraWidget::class.java, com.umutk.pulse.extras.music.imgskin.WmsCdWidget::class.java)[index]
    WidgetCard("WMP · " + skin.name, { WinampSkinPick.pinWidget(ctx, cls) }, skin.w.toFloat() / skin.h) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Placeholder()
    }
}

/** The Winamp widget or its 4x1 strip, drawn by the widget's own render with the classic skin; the card keeps the widget's shape. */
@Composable
private fun WinampCard(ctx: Context, compact: Boolean) {
    val h = if (compact) WinampWidget.STRIP_H else WINAMP_H
    val bmp by produceState<Bitmap?>(null, compact) {
        value = withContext(drawer) { runCatching { WinampWidget.render(null, "Pulse", 0.4f, true, 83, boxH = 2 * h, strip = compact) }.getOrNull() }
    }
    WidgetCard(if (compact) "Winamp 4x1" else "Winamp", { addToHome(ctx, if (compact) "music.WinampWidgetCompact" else "music.WinampWidget") }, WINAMP_W.toFloat() / h) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Placeholder()
    }
}

/** One installed Winamp skin: a tap pins the Winamp widget set to that skin (see WinampSkinPick). */
@Composable
private fun WinampSkinCard(ctx: Context, s: InstalledSkin) {
    val bmp by produceState<Bitmap?>(null, s.file.path) {
        // the widget's own drawing first; if that fails, the skin's main.bmp so the card never stays empty
        value = withContext(drawer) {
            runCatching { WinampWidget.render(loadWinampSkin(s.file), "Pulse", 0.4f, true, 83, boxH = 2 * WINAMP_H) }.getOrNull()
                ?: loadSkinPreview(s.file)?.asAndroidBitmap()
        }
    }
    WidgetCard(s.name, { if (!WinampSkinPick.pin(ctx, s.file.name, compact = false)) Toast.makeText(ctx, tr("This launcher cannot place widgets from here", "Bu başlatıcı buradan widget yerleştiremiyor"), Toast.LENGTH_LONG).show() }, WINAMP_W.toFloat() / WINAMP_H) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Placeholder()
    }
}

/** The lyrics widget card: one lyric line under the track title, in the app's look. */
@Composable
private fun LyricsCard(ctx: Context) {
    WidgetCard(tr("Lyrics", "Şarkı sözleri"), { addToHome(ctx, "music.LyricsWidget") }, 4f) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
            Text(tr("Track title", "Parça adı"), color = T.sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("♪ " + tr("The lyric line now", "Şu anki söz satırı"), color = T.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A normal music player widget card: a mock of its layout (cover, title, artist, transport), added by its provider class. */
@Composable
private fun MusicCard(ctx: Context, label: String, provider: String, aspect: Float, compact: Boolean) {
    WidgetCard(label, { addToHome(ctx, provider) }, aspect) {
        Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f).background(T.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = T.sub, modifier = Modifier.size(if (compact) 14.dp else 22.dp))
            }
            if (!compact) Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text("Pulse", color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr("Artist", "Sanatçı"), color = T.sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.SkipPrevious, null, tint = T.text, modifier = Modifier.size(if (compact) 14.dp else 20.dp))
            Icon(Icons.Filled.PlayArrow, null, tint = T.text, modifier = Modifier.size(if (compact) 16.dp else 24.dp))
            Icon(Icons.Filled.SkipNext, null, tint = T.text, modifier = Modifier.size(if (compact) 14.dp else 20.dp))
        }
    }
}

/** The Winamp visualizer widget, drawn with sample bars. */
@Composable
private fun WinampVisCard(ctx: Context) {
    val bmp by produceState<Bitmap?>(null) {
        value = withContext(drawer) { runCatching { WinampVisWidget.render(List(24) { i -> .5f + .45f * kotlin.math.sin(i * .7f) }, "Pulse", 600, 260) }.getOrNull() }
    }
    WidgetCard("Winamp visualizer", { WinampSkinPick.pinWidget(ctx, WinampVisWidget::class.java) }, WinampVisWidget.W.toFloat() / WinampVisWidget.H) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Placeholder()
    }
}

/** The Poweramp widget in the app's Poweramp skin, drawn as the widget draws it (sample title, no cover). */
@Composable
private fun PowerampCard(ctx: Context) {
    val style = remember { powerampSkin(PowerampPrefs.skin(ctx)).build(NeutralAccent, Color.Black) }
    val bmp by produceState<Bitmap?>(null, style) {
        value = withContext(drawer) { runCatching { PowerampWidget.render(style, "Pulse", "Poweramp", "Underground Years", 0.4f, true, 83_000L, 240_000L, null, 4, 2322, true, 2, 640, 400) }.getOrNull() }
    }
    WidgetCard("Poweramp", { WinampSkinPick.pinWidget(ctx, PowerampWidget::class.java) }, PowerampWidget.W.toFloat() / PowerampWidget.H) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Placeholder()
    }
}

/** The Windows media player widget in the look the Widgets page uses (95 CD Player, XP, 7 Aero, flat), drawn as the widget draws it. */
@Composable
private fun WinPlayerCard(ctx: Context) {
    val style = remember { com.umutk.pulse.widgets.WidgetStyles.default(ctx) }
    val bmp by produceState<Bitmap?>(null, style) {
        value = withContext(drawer) { runCatching { com.umutk.pulse.extras.music.widgets.WindowsPlayerWidget.render(style, "Track name", "Artist name", 0.4f, true, 83_000L, 240_000L, null, 640, 300) }.getOrNull() }
    }
    WidgetCard(tr("Windows media player", "Windows medya oynatıcı"), { WinampSkinPick.pinWidget(ctx, com.umutk.pulse.extras.music.widgets.WindowsPlayerWidget::class.java) }, com.umutk.pulse.extras.music.widgets.WindowsPlayerWidget.W.toFloat() / com.umutk.pulse.extras.music.widgets.WindowsPlayerWidget.H) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Placeholder()
    }
}

@Composable
private fun Placeholder() =Box(Modifier.size(40.dp).border(1.dp, T.line, CircleShape))

/** A theme's logo by theme id: the Windows logos, the Android robot for Material, a black disc for AMOLED, a clear disc for Glass, a dark rounded tile for iOS, a grid for All. */
@Composable
internal fun ThemeLogo(theme: String, size: Dp) {
    val res = when (theme) {
        "win95" -> R.drawable.logo_win95; "xp" -> R.drawable.logo_xp; "win7" -> R.drawable.logo_win7
        "win10" -> R.drawable.logo_win10; "win11" -> R.drawable.logo_win11; "material" -> R.drawable.ic_logo_android
        "oneui" -> R.drawable.widget_oneui
        else -> 0
    }
    if (res != 0) Image(painterResource(res), null, Modifier.size(size), contentScale = ContentScale.Fit)
    else if (theme == "ios") Box(Modifier.size(size).padding(size / 8).background(Color(0xFF1C1C1E), RoundedCornerShape(size / 4)).border(1.dp, Color.Gray, RoundedCornerShape(size / 4)))
    else if (theme == "all") Column(Modifier.size(size).padding(size / 6), verticalArrangement = Arrangement.spacedBy(size / 12)) {
        repeat(2) { Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(size / 12)) { repeat(2) { Box(Modifier.weight(1f).fillMaxSize().background(Color.Gray, RoundedCornerShape(size / 12))) } } }
    }
    else Box(
        Modifier.size(size).padding(size / 8)
            .background(if (theme == "amoled") Color.Black else Color.White.copy(alpha = .25f), CircleShape)
            .border(1.dp, Color.Gray, CircleShape)
    )
}

/** Logo with a one-line label under it; the picked one gets an accent ring. */
@Composable
internal fun LogoChip(theme: String, label: String, picked: Boolean, modifier: Modifier = Modifier, ring: Color = Look.accent, text: Color = T.text, onClick: () -> Unit) {
    Column(
        modifier.then(if (picked) Modifier.border(2.dp, ring, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // a light disc behind every logo so the black 95 and XP logos read on dark themes
        Box(Modifier.size(36.dp).background(Color.White.copy(alpha = .85f), CircleShape), contentAlignment = Alignment.Center) { ThemeLogo(theme, 28.dp) }
        Text(label, color = text, fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.fillMaxWidth().padding(top = 3.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

internal fun styleLogo(s: WidgetStyle) = when (s) {
    WidgetStyle.MATERIAL -> "material"
    WidgetStyle.AMOLED -> "amoled"
    WidgetStyle.WIN11, WidgetStyle.WIN11_DARK -> "win11"
    WidgetStyle.WIN10 -> "win10"
    WidgetStyle.WIN7 -> "win7"
    WidgetStyle.WIN95 -> "win95"
    WidgetStyle.XP -> "xp"
    WidgetStyle.ONEUI -> "oneui"
}

internal fun styleShort(s: WidgetStyle) = when (s) {
    WidgetStyle.MATERIAL -> "Material"; WidgetStyle.AMOLED -> "AMOLED"
    WidgetStyle.WIN11 -> "11"; WidgetStyle.WIN11_DARK -> "11 dark"; WidgetStyle.WIN10 -> "10"
    WidgetStyle.WIN7 -> "7"; WidgetStyle.WIN95 -> "95"; WidgetStyle.XP -> "XP"; WidgetStyle.ONEUI -> "One UI"
}

private fun addToHome(ctx: Context, provider: String) {
    val mgr = ctx.getSystemService(AppWidgetManager::class.java)
    if (mgr != null && mgr.isRequestPinAppWidgetSupported) {
        // a full class name (Notes lives in extras/notes), else a class in widgets/
        mgr.requestPinAppWidget(ComponentName(ctx, if (provider.startsWith("com.")) provider else "com.umutk.pulse.widgets.$provider"), null, null)
    } else {
        Toast.makeText(ctx, "This launcher cannot place widgets from here: long-press the home screen and pick Pulse", Toast.LENGTH_LONG).show()
    }
}
