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

/**
 * Every installed widget as its live preview; a tap adds it. Each row binds a temporary id while it is on screen, so the
 * list stays lazy. The caller keeps the host listening.
 */
@Composable
fun WidgetPickDialog(onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    // Pulse's own widgets come first: they are drawn in the look of the launcher (Windows 7 gadgets, XP, 95, 10, 11, Material, AMOLED)
    val (own, others) = remember { AppWidgetManager.getInstance(ctx).installedProviders.partition { it.provider.packageName == ctx.packageName } }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Widgets", "Widget'lar"), color = w.text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (own.isNotEmpty()) item(key = "h_own") { Text(tr("Pulse widgets", "Pulse widget'ları"), color = w.sub, modifier = Modifier.padding(vertical = 6.dp)) }
                items(own, key = { it.provider.flattenToString() }) { info -> WidgetPickRow(info) { onPick(info) } }
                if (others.isNotEmpty()) item(key = "h_other") { Text(tr("Other apps", "Diğer uygulamalar"), color = w.sub, modifier = Modifier.padding(vertical = 6.dp)) }
                items(others, key = { it.provider.flattenToString() }) { info -> WidgetPickRow(info) { onPick(info) } }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat"), onClick = onDismiss) },
    )
}

@Composable
private fun WidgetPickRow(info: AppWidgetProviderInfo, onPick: () -> Unit) {
    val ctx = LocalContext.current
    var id by remember(info.provider) { mutableStateOf(-1) }
    DisposableEffect(info.provider) {
        val host = LauncherWidgetHost.get(ctx)
        val n = host.allocateAppWidgetId()
        val ok = AppWidgetManager.getInstance(ctx).bindAppWidgetIdIfAllowed(n, info.provider)
        if (ok) id = n else host.deleteAppWidgetId(n)
        onDispose { if (ok) host.deleteAppWidgetId(n) }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        WidgetPreview(info, id)
        // over the live view, so a tap adds the widget instead of reaching the widget's own buttons
        Box(Modifier.matchParentSize().clickable(onClick = onPick))
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
