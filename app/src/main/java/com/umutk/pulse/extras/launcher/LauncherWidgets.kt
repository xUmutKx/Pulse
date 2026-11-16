package com.umutk.pulse.extras.launcher

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
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

/** Forgets a placed widget and frees its id. */
fun removeWidget(c: Context, id: Int) {
    LauncherWidgetHost.get(c).deleteAppWidgetId(id)
    LauncherPrefs.widgets = LauncherPrefs.widgets.filterNot { it.startsWith("$id|") }
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
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
        placedWidgets().forEach { w ->
            val info = mgr.getAppWidgetInfo(w.id) ?: return@forEach
            key(w.id) {
                AndroidView(
                    factory = { c -> host.createView(c, w.id, info) },
                    update = { it.setAppWidget(w.id, info) },
                    modifier = Modifier.width(info.minWidth.coerceAtLeast(40).dp).height(info.minHeight.coerceAtLeast(40).dp).padding(end = 8.dp),
                )
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
        val scale = min(1f, maxWidth.value / w)
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

/**
 * The widget picker, as the Windows 11 "Add widgets" board: the apps that have widgets in a grid (Pulse first), and an app's widgets as cards in two columns with their size.
 * A search box finds a widget by its name or its app. The board stays open after a widget is added (it gets a check mark), so several can be added in one go.
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
    // the apps with widgets: Pulse first, then by name
    val apps = remember { all.groupBy { it.provider.packageName }.toList().sortedWith(compareBy<Pair<String, List<AppWidgetProviderInfo>>> { it.first != ctx.packageName }.thenBy { appOf(it.first).lowercase() }) }
    var app by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(emptySet<String>()) }
    val q = query.trim()
    val shown: List<AppWidgetProviderInfo>? = when {
        q.isNotEmpty() -> all.filter { nameOf(it).contains(q, true) || appOf(it.provider.packageName).contains(q, true) }
        app != null -> apps.firstOrNull { it.first == app }?.second
        else -> null
    }
    WinAlert(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app != null && q.isEmpty()) Box(Modifier.size(32.dp).clickable { app = null }, contentAlignment = Alignment.Center) { PIcon("back", w.text, 18.dp) }
                Text(if (app != null && q.isEmpty()) appOf(app!!) else tr("Add widgets", "Widget ekle"), color = w.text, maxLines = 1)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                com.umutk.pulse.PField(query, { query = it }, tr("Search widgets", "Widget ara"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (shown == null) LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                else if (shown.isEmpty()) Text(tr("No widgets", "Widget yok"), color = w.sub, modifier = Modifier.padding(16.dp))
                else LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gridItems(shown, key = { it.provider.flattenToString() }) { info ->
                        val k = info.provider.flattenToString()
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(w.panel).clickable { added = added + k; onPick(info) }.padding(8.dp)) {
                            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                                WidgetCardPicture(info) { added = added + k; onPick(info) }
                                if (k in added) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Look.accent), contentAlignment = Alignment.Center) { PIcon("check", Color.White, 14.dp) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(nameOf(info), color = w.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${cells(info.minWidth)} x ${cells(info.minHeight)}" + if (q.isNotEmpty()) "  ·  " + appOf(info.provider.packageName) else "", color = w.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
        if (info.previewImage == 0) null else runCatching { info.loadPreviewImage(ctx, ctx.resources.displayMetrics.densityDpi)?.toBitmap()?.asImageBitmap() }.getOrNull()
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
