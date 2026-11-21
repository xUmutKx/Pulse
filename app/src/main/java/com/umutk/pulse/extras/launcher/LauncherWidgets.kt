package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.gestures.detectDragGestures
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlin.math.min

/**
 * Widgets on the launcher home, hosted with the platform's AppWidgetHost. The drawing is the widget's own (its provider
 * draws it); the launcher only gives it a place and a size. One host per process, so the ids match everywhere.
 */
object LauncherWidgetHost {
    private const val HOST_ID = 0x50554C53 // "PULS"
    private var host: AppWidgetHost? = null
    fun get(c: Context): AppWidgetHost = host ?: AppWidgetHost(c.applicationContext, HOST_ID).also { host = it }
}

/** A placed widget read back from LauncherPrefs.widgets. */
data class PlacedWidget(val id: Int, val pkg: String, val cls: String)

fun placedWidgets(): List<PlacedWidget> = LauncherPrefs.widgets.mapNotNull { s ->
    val p = s.split('|')
    p[0].toIntOrNull()?.takeIf { p.size == 3 }?.let { PlacedWidget(it, p[1], p[2]) }
}

/** The size a placed widget is shown at, in dp: the person's (dragged with the corner when the widget was held), else the size its maker asked for. */
fun widgetSizeDp(id: Int, info: AppWidgetProviderInfo): Pair<Int, Int> {
    val own = LauncherPrefs.widgetSizes[id.toString()]?.split(',')?.mapNotNull { it.toIntOrNull() }
    return if (own != null && own.size == 2) own[0] to own[1] else info.minWidth.coerceAtLeast(40) to info.minHeight.coerceAtLeast(40)
}

/** Forgets a placed widget and frees its id. */
fun removeWidget(c: Context, id: Int) {
    LauncherWidgetHost.get(c).deleteAppWidgetId(id)
    LauncherPrefs.widgets = LauncherPrefs.widgets.filterNot { it.startsWith("$id|") }
    LauncherPrefs.widgetSizes = LauncherPrefs.widgetSizes - id.toString()
    LauncherPrefs.save(c)
}

/** The home row of placed widgets. Listens to the host only while it is on screen. */
@Composable
fun LauncherWidgetStrip(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val host = LauncherWidgetHost.get(ctx)
    val mgr = AppWidgetManager.getInstance(ctx)
    // reading the state here makes the row redraw when a widget is added or removed
    val placed = LauncherPrefs.widgets
    DisposableEffect(host) {
        host.startListening()
        onDispose { host.stopListening() }
    }
    if (placed.isEmpty()) return
    // a widget held for a moment gets its controls: take it off, move it left or right, done
    var editId by remember { mutableStateOf<Int?>(null) }
    fun move(id: Int, by: Int) {
        val l = LauncherPrefs.widgets.toMutableList()
        val i = l.indexOfFirst { it.startsWith("$id|") }
        val j = i + by
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        LauncherPrefs.widgets = l; LauncherPrefs.save(ctx)
    }
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
        placedWidgets().forEach { w ->
            val info = mgr.getAppWidgetInfo(w.id) ?: return@forEach
            key(w.id) {
                Box(Modifier.padding(end = 8.dp)
                    // the press is only watched (the widget still gets its touches); held without lifting it is the long press
                    .pointerInput(w.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(androidx.compose.ui.input.pointer.PointerEventPass.Initial) }
                            if (up == null) editId = w.id
                        }
                    }) {
                    val (sw, sh) = widgetSizeDp(w.id, info)
                    AndroidView(
                        factory = { c -> host.createView(c, w.id, info) },
                        // the widget is told its size, so it draws itself for it
                        update = { it.setAppWidget(w.id, info); it.updateAppWidgetSize(android.os.Bundle(), sw, sh, sw, sh) },
                        modifier = Modifier.width(sw.dp).height(sh.dp),
                    )
                    if (editId == w.id) Row(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .62f)).clickable { editId = null }, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        @Composable fun dot(icon: String, go: () -> Unit) = Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .18f)).clickable(onClick = go), contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon(icon, androidx.compose.ui.graphics.Color.White, 20.dp) }
                        dot("back") { move(w.id, -1) }
                        dot("close") { editId = null; removeWidget(ctx, w.id) }
                        dot("check") { editId = null }
                        dot("forward") { move(w.id, 1) }
                    }
                    // held: the corner at the bottom right is dragged to make the widget any size (freeform); the size is kept when the finger lifts
                    if (editId == w.id) Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).size(44.dp).pointerInput(w.id) {
                        detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, d ->
                            c.consume()
                            val (cw, ch) = widgetSizeDp(w.id, info)
                            val dens = density
                            val nw = (cw + d.x / dens).toInt().coerceIn(info.minResizeWidth.coerceAtLeast(40), 900)
                            val nh = (ch + d.y / dens).toInt().coerceIn(info.minResizeHeight.coerceAtLeast(40), 900)
                            LauncherPrefs.widgetSizes = LauncherPrefs.widgetSizes + (w.id.toString() to "$nw,$nh")
                        }
                    }, contentAlignment = androidx.compose.ui.Alignment.BottomEnd) {
                        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                            val c = androidx.compose.ui.graphics.Color.White
                            for (k in 1..3) drawLine(c, androidx.compose.ui.geometry.Offset(size.width, size.height * k / 4f), androidx.compose.ui.geometry.Offset(size.width * (4 - k) / 4f, size.height), 2.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

/**
 * A widget drawn at its real size, scaled down to fit the width and never cropped. A bound [id] gives the live view;
 * with no id (the provider could not be bound here) the provider's own preview picture is drawn instead.
 */
@Composable
fun WidgetPreview(info: AppWidgetProviderInfo, id: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val host = LauncherWidgetHost.get(ctx)
    val picture = remember(info.provider, id) {
        if (id >= 0) null else runCatching { info.loadPreviewImage(ctx, ctx.resources.displayMetrics.densityDpi)?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val w = info.minWidth.coerceAtLeast(40)
        val h = info.minHeight.coerceAtLeast(40)
        val byH = if (constraints.hasBoundedHeight) maxHeight.value / h else 1f
        val scale = min(1f, min(maxWidth.value / w, byH))
        Box(Modifier.size((w * scale).dp, (h * scale).dp), contentAlignment = Alignment.Center) {
            if (id >= 0) AndroidView(
                factory = { c -> host.createView(c, id, info).also { it.updateAppWidgetSize(null, w, h, w, h) } },
                modifier = Modifier.requiredSize(w.dp, h.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            )
            else if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else PIcon("gridView", T.sub, 28.dp)
        }
    }
}

/** About how many home cells a widget takes (the platform's own sum: 70 dp a cell, less 30 dp of margins). */
private fun cells(dp: Int): Int = ((dp + 30) / 70).coerceAtLeast(1)

/** The group of a widget, from its name: the board shows the widgets under these headings. */
private fun groupOf(name: String, cls: String): String {
    val n = "$name $cls".lowercase()
    fun has(vararg k: String) = k.any { n.contains(it) }
    return when {
        has("clock", "saat", "alarm", "flip", "timer", "zaman") -> "clock"
        has("music", "müzik", "player", "winamp", "poweramp", "media", "visual", "song", "radio", "ses", "voice", "record") -> "music"
        has("note", "not ", "sticky", "todo", "task", "list", "yapışkan") -> "notes"
        has("weather", "hava", "calendar", "takvim", "event", "agenda", "date") -> "calendar"
        has("battery", "pil", "storage", "depolama", "ram", "cpu", "network", "ağ", "system", "sistem", "monitor", "speed", "gauge", "temp", "disk", "device", "performance", "charging", "şarj") -> "system"
        has("photo", "gallery", "galeri", "picture", "image", "foto", "resim") -> "photos"
        else -> "other"
    }
}

private fun groupName(g: String): String = when (g) {
    "clock" -> tr("Clocks", "Saatler"); "music" -> tr("Music and sound", "Müzik ve ses"); "notes" -> tr("Notes and tasks", "Notlar ve görevler")
    "calendar" -> tr("Calendar and weather", "Takvim ve hava"); "system" -> tr("System", "Sistem"); "photos" -> tr("Photos", "Fotoğraflar"); else -> tr("More", "Diğer")
}

private val GROUP_ORDER = listOf("clock", "music", "notes", "calendar", "system", "photos", "other")

/**
 * The widget board, as the Windows 11 "Add gadgets" pane: Pulse's own widgets sorted into groups (clocks, music, notes, calendar, system, photos) with one chip per group,
 * and the other apps' widgets under their app. A card is as wide and as high as its widget really is (wide widgets take the whole row), so nothing is cropped or
 * stretched. A search finds a widget by its name or app. The board stays open after an add (the card gets a check), so several can be added in one go.
 */
@Composable
fun WidgetPickDialog(onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    val pm = ctx.packageManager
    val all = remember { AppWidgetManager.getInstance(ctx).installedProviders }
    val label = remember { HashMap<String, String>() }
    fun nameOf(i: AppWidgetProviderInfo): String = label.getOrPut(i.provider.flattenToString()) { runCatching { i.loadLabel(pm) }.getOrNull() ?: i.provider.className.substringAfterLast('.') }
    val appName = remember { HashMap<String, String>() }
    fun appOf(pkg: String): String = appName.getOrPut(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    val (own, others) = remember { all.partition { it.provider.packageName == ctx.packageName } }
    val ownBy = remember { own.groupBy { groupOf(nameOf(it), it.provider.className) } }
    val apps = remember { others.groupBy { it.provider.packageName }.toList().sortedBy { appOf(it.first).lowercase() } }
    var tab by remember { mutableStateOf(0) }          // 0 Pulse, 1 other apps
    var group by remember { mutableStateOf("") }       // "" every group
    var app by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(emptySet<String>()) }
    val q = query.trim()
    // what the grid shows: a flat list of widgets (search, one app) or the Pulse groups under headings
    val flat: List<AppWidgetProviderInfo>? = when {
        q.isNotEmpty() -> all.filter { nameOf(it).contains(q, true) || appOf(it.provider.packageName).contains(q, true) }
        tab == 1 && app != null -> apps.firstOrNull { it.first == app }?.second
        else -> null
    }
    WinAlert(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tab == 1 && app != null && q.isEmpty()) Box(Modifier.size(32.dp).clickable { app = null }, contentAlignment = Alignment.Center) { PIcon("back", w.text, 18.dp) }
                Text(if (tab == 1 && app != null && q.isEmpty()) appOf(app!!) else tr("Add gadgets", "Gadget ekle"), color = w.text, maxLines = 1)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                com.umutk.pulse.PField(query, { query = it }, tr("Search gadgets", "Gadget ara"), Modifier.fillMaxWidth())
                if (q.isEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.umutk.pulse.PChip(tab == 0, "Pulse") { tab = 0; app = null }
                        com.umutk.pulse.PChip(tab == 1, tr("Other apps", "Diğer uygulamalar")) { tab = 1 }
                    }
                    if (tab == 0 && ownBy.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.umutk.pulse.PChip(group == "", tr("All", "Tümü")) { group = "" }
                        GROUP_ORDER.filter { it in ownBy }.forEach { g -> com.umutk.pulse.PChip(group == g, groupName(g)) { group = g } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                @Composable fun Card(info: AppWidgetProviderInfo, span2: Boolean) {
                    val k = info.provider.flattenToString()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(w.panel).clickable { added = added + k; onPick(info) }.padding(8.dp)) {
                        // the picture is as high as the widget is, for the width of the card
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val wd = info.minWidth.coerceAtLeast(40); val hd = info.minHeight.coerceAtLeast(40)
                            val ph = (maxWidth.value * hd / wd).coerceIn(44f, if (span2) 170f else 150f).dp
                            Box(Modifier.fillMaxWidth().height(ph), contentAlignment = Alignment.Center) {
                                WidgetCardPicture(info) { added = added + k; onPick(info) }
                                if (k in added) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Look.accent), contentAlignment = Alignment.Center) { PIcon("check", Color.White, 14.dp) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(nameOf(info), color = w.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${cells(info.minWidth)} x ${cells(info.minHeight)}" + if (q.isNotEmpty()) "  ·  " + appOf(info.provider.packageName) else "", color = w.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                fun wide(i: AppWidgetProviderInfo) = cells(i.minWidth) >= 3 || i.minWidth >= 2.2f * i.minHeight
                if (flat == null && tab == 1 && app == null) LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    gridItems(apps, key = { it.first }) { (pkg, list) ->
                        val icon = remember(pkg) { runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(w.panel).clickable { app = pkg }.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (icon != null) Image(icon, null, Modifier.size(36.dp)) else PIcon("apps", w.text, 32.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(appOf(pkg), color = w.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text("${list.size}", color = w.sub, fontSize = 11.sp)
                        }
                    }
                }
                else if (flat != null && flat.isEmpty()) Text(tr("No gadgets", "Gadget yok"), color = w.sub, modifier = Modifier.padding(16.dp))
                else LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (flat != null) gridItems(flat, key = { it.provider.flattenToString() }, span = { GridItemSpan(if (wide(it)) 2 else 1) }) { Card(it, wide(it)) }
                    else GROUP_ORDER.filter { it in ownBy && (group == "" || group == it) }.forEach { g ->
                        if (group == "") item(key = "h_$g", span = { GridItemSpan(maxLineSpan) }) { Text(groupName(g), color = w.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
                        gridItems(ownBy.getValue(g), key = { it.provider.flattenToString() }, span = { GridItemSpan(if (wide(it)) 2 else 1) }) { Card(it, wide(it)) }
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Done", "Bitti"), onClick = onDismiss) },
    )
}

/**
 * The picture on a widget's card: the provider's own preview picture when it has one (cheap, nothing is bound), else the live widget, bound to a
 * temporary id only while the card is on screen, scaled to fit and covered so a tap adds it.
 */
@Composable
private fun WidgetCardPicture(info: AppWidgetProviderInfo, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val picture = remember(info.provider) {
        // the plain white note of the music widgets is no preview: those show the live widget instead
        if (info.previewImage == 0 || info.previewImage == com.umutk.pulse.R.drawable.widget_prev_white) null else runCatching { info.loadPreviewImage(ctx, ctx.resources.displayMetrics.densityDpi)?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    if (picture != null) { Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit); return }
    var id by remember(info.provider) { mutableStateOf(-1) }
    DisposableEffect(info.provider) {
        val host = LauncherWidgetHost.get(ctx)
        val n = host.allocateAppWidgetId()
        val ok = AppWidgetManager.getInstance(ctx).bindAppWidgetIdIfAllowed(n, info.provider)
        if (ok) id = n else host.deleteAppWidgetId(n)
        onDispose { if (ok) host.deleteAppWidgetId(n) }
    }
    Box(Modifier.fillMaxSize()) {
        WidgetPreview(info, id, Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onTap))
    }
}

/**
 * Returns a function that adds a provider to the home: binds it (the system asks once if it must), runs the widget's
 * own setup screen when it has one, and saves the placement. [onAdded] runs after it is saved.
 */
@Composable
fun rememberWidgetAdd(onAdded: () -> Unit): (AppWidgetProviderInfo) -> Unit {
    val ctx = LocalContext.current
    val host = LauncherWidgetHost.get(ctx)
    var pendingId by remember { mutableStateOf(-1) }
    var pendingInfo by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    fun save(id: Int, info: AppWidgetProviderInfo) {
        LauncherPrefs.widgets = LauncherPrefs.widgets + "$id|${info.provider.packageName}|${info.provider.className}"
        LauncherPrefs.save(ctx)
        onAdded()
    }

    val setup = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val info = pendingInfo
        if (r.resultCode == Activity.RESULT_OK && info != null) save(pendingId, info) else host.deleteAppWidgetId(pendingId)
    }
    // once bound, the widget's setup screen (if any) runs before the placement is kept
    val afterBind: (AppWidgetProviderInfo) -> Unit = { info ->
        val cfg = info.configure
        if (cfg != null) setup.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId))
        else save(pendingId, info)
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val info = pendingInfo
        if (r.resultCode == Activity.RESULT_OK && info != null) afterBind(info) else host.deleteAppWidgetId(pendingId)
    }

    return { info ->
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        val mgr = AppWidgetManager.getInstance(ctx)
        if (mgr.bindAppWidgetIdIfAllowed(id, info.provider)) afterBind(info)
        else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
    }
}

/** The sticky note widget of Pulse (the same one any launcher can place), so the desktop's note is a real widget: one note per widget, edited by a tap. */
fun stickyNoteProvider(c: Context): AppWidgetProviderInfo? =
    AppWidgetManager.getInstance(c).installedProviders.firstOrNull { it.provider.packageName == c.packageName && it.provider.className.endsWith("StickyNoteWidget") }

/**
 * The home with a page for each placed widget: swipe sideways from one to the next (round and round when Loop is on), a row of dots says where you are.
 * Widgets are drawn at their own size (never cut), at the top of the page; the rest of the page is the wallpaper.
 */
@Composable
fun LauncherWidgetPages(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val host = LauncherWidgetHost.get(ctx)
    val mgr = AppWidgetManager.getInstance(ctx)
    val placed = LauncherPrefs.widgets
    DisposableEffect(host) { host.startListening(); onDispose { host.stopListening() } }
    val list = remember(placed) { placedWidgets().filter { mgr.getAppWidgetInfo(it.id) != null } }
    if (list.isEmpty()) return
    val loop = LauncherPrefs.loopScroll && list.size > 1
    val start = if (loop) (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % list.size) else 0
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = start, pageCount = { if (loop) Int.MAX_VALUE else list.size })
    WallpaperScrollEffect(pager, list.size, loop)
    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth()) { i ->
            val w = list[i % list.size]
            val info = mgr.getAppWidgetInfo(w.id)
            if (info != null) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                val (_, sh) = widgetSizeDp(w.id, info)
                AndroidView(
                    factory = { c -> host.createView(c, w.id, info) },
                    update = { it.setAppWidget(w.id, info) },
                    modifier = Modifier.fillMaxWidth().height(sh.coerceAtLeast(80).dp),
                )
            }
        }
        if (list.size > 1) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
            repeat(list.size) { d ->
                androidx.compose.foundation.layout.Box(Modifier.padding(3.dp).size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (pager.currentPage % list.size == d) .95f else .35f)))
            }
        }
    }
}
