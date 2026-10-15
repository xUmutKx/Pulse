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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PHead
import com.umutk.pulse.PSlider
import com.umutk.pulse.R
import com.umutk.pulse.T
import com.umutk.pulse.tr
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
    val providers = remember { WidgetData.all.associateBy { it.javaClass.simpleName } }
    val classOf = remember { SkinWidgets.all.associate { it.skin to it.javaClass.simpleName } }
    val current = designOf(Look.theme.id)
    val designs = DESIGNS.sortedBy { if (it.first == current) 0 else 1 }.filter { d -> only.let { it == null || it == d.first } }
    fun hit(name: String) = query.isBlank() || name.contains(query, ignoreCase = true)
    val oldShown = WIDGET_ENTRIES.filter { hit(it.label) && it.provider in providers }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PHead(tr("Widgets", "Widget'lar"), "${Skin.values().size + FaceWidgets.all.size + WIDGET_ENTRIES.size}") }
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
            if (plainStyle != null) oldShown.forEach { w -> cards += "t_${theme}_${w.provider}" to @Composable { TextCard(ctx, w, providers.getValue(w.provider), plainStyle) } }
            if (cards.isNotEmpty()) {
                item(key = "h_$theme") { FamilyHeader(theme, label) }
                items(cards.chunked(2), key = { it.first().first }) { pair -> PairRow(pair) { it.second() } }
            }
        }
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
    // laid out at a real widget size, then scaled down as a whole so the text keeps its proportions (some blank space is fine)
    WidgetCard(w.label, { addToHome(ctx, w.provider) }, aspect = 2.2f) {
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val k = (maxWidth / 264.dp) * 0.9f
        Box(Modifier.requiredSize(264.dp, 120.dp).graphicsLayer { scaleX = k; scaleY = k }) {
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

@Composable
private fun Placeholder() = Box(Modifier.size(40.dp).border(1.dp, T.line, CircleShape))

/** A theme's logo by theme id: the Windows logos, the Android robot for Material, a black disc for AMOLED, a clear disc for Glass, a dark rounded tile for iOS, a grid for All. */
@Composable
internal fun ThemeLogo(theme: String, size: Dp) {
    val res = when (theme) {
        "win95" -> R.drawable.logo_win95; "xp" -> R.drawable.logo_xp; "win7" -> R.drawable.logo_win7
        "win10" -> R.drawable.logo_win10; "win11" -> R.drawable.logo_win11; "material" -> R.drawable.ic_logo_android
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
}

internal fun styleShort(s: WidgetStyle) = when (s) {
    WidgetStyle.MATERIAL -> "Material"; WidgetStyle.AMOLED -> "AMOLED"
    WidgetStyle.WIN11 -> "11"; WidgetStyle.WIN11_DARK -> "11 dark"; WidgetStyle.WIN10 -> "10"
    WidgetStyle.WIN7 -> "7"; WidgetStyle.WIN95 -> "95"; WidgetStyle.XP -> "XP"
}

private fun addToHome(ctx: Context, provider: String) {
    val mgr = ctx.getSystemService(AppWidgetManager::class.java)
    if (mgr != null && mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(ComponentName(ctx, "com.umutk.pulse.widgets.$provider"), null, null)
    } else {
        Toast.makeText(ctx, "This launcher cannot place widgets from here: long-press the home screen and pick Pulse", Toast.LENGTH_LONG).show()
    }
}
