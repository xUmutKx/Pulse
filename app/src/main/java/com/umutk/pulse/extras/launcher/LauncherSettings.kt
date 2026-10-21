package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PCheck
import com.umutk.pulse.PDialog
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.features.wallsFor
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.ui.SettingsSection
import com.umutk.pulse.extras.ui.SliderRow
import com.umutk.pulse.extras.video.PickDialog
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Settings of the Pulse Launcher. Drawn in the look passed in by its host; writes only LauncherPrefs. */
@Composable
fun LauncherSettings(back: () -> Unit) {
    val ctx = LocalContext.current
    // Pulse's process never runs LauncherActivity, so read the stored values before showing or saving them
    remember(ctx) { LauncherPrefs.load(ctx) }
    BackHandler { back() }
    var pickLook by remember { mutableStateOf(false) }
    var pickDock by remember { mutableStateOf(false) }
    var pickSort by remember { mutableStateOf(false) }
    var pickHidden by remember { mutableStateOf(false) }
    var pickWall by remember { mutableStateOf(false) }
    var pickWidget by remember { mutableStateOf(false) }
    val addWidget = rememberWidgetAdd { }
    // the live previews of the placed widgets update only while the host listens
    val widgetHost = LauncherWidgetHost.get(ctx)
    DisposableEffect(widgetHost) { widgetHost.startListening(); onDispose { widgetHost.stopListening() } }
    var askReset by remember { mutableStateOf(false) }
    fun save() = LauncherPrefs.save(ctx)
    val cur = LauncherLooks.byId(LauncherPrefs.look)
    val isHome = isDefaultHome(ctx)
    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(tr("Launcher", "Ana ekran"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        SettingsSection(tr("Look", "Görünüm")) {
            ValueRow("palette", tr("Look", "Görünüm"), tr(cur.nameEn, cur.nameTr)) { pickLook = true }
            // the same stock pictures as Pulse's settings for this look; the choice is kept in the launcher
            val walls = wallsFor(cur.id)
            ValueRow("image", tr("Wallpaper", "Duvar kağıdı"), if (walls.isEmpty()) "-" else "${(LauncherPrefs.wallIndex(cur.id) + 1).coerceAtMost(walls.size)}/${walls.size}") {
                if (walls.isNotEmpty()) pickWall = true
            }
            ValueRow("image", tr("Set as phone wallpaper", "Telefon duvar kağıdı yap"), "") {
                val id = cur.id
                // full-size decode is slow: off the main thread, the result is reported on it
                Thread {
                    val ok = setPhoneWallpaper(ctx, id)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(ctx, if (ok) tr("Wallpaper set", "Duvar kağıdı ayarlandı") else tr("Could not set the wallpaper", "Duvar kağıdı ayarlanamadı"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        }
        SettingsSection(tr("Widgets", "Widget'lar")) {
            ValueRow("add", tr("Add widget", "Widget ekle"), "") { pickWidget = true }
            val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
            placedWidgets().forEach { w ->
                val info = mgr.getAppWidgetInfo(w.id) ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    WidgetPreview(info, w.id, Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clickable { removeWidget(ctx, w.id) }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 22.dp) }
                }
            }
        }
        SettingsSection(tr("Home screen", "Ana ekran")) {
            SwitchLine("clock", tr("Clock and date", "Saat ve tarih"), LauncherPrefs.clock) { LauncherPrefs.clock = it; save() }
            SwitchLine("apps", tr("Apps on the home screen", "Ana ekranda uygulamalar"), LauncherPrefs.homeApps) { LauncherPrefs.homeApps = it; save() }
            SwitchLine("gridView", tr("Vertical scrolling", "Dikey kaydırma"), LauncherPrefs.vertical) { LauncherPrefs.vertical = it; save() }
            SwitchLine("rotate", tr("Lock to portrait", "Dikey ekranda kilitle"), LauncherPrefs.lockPortrait) { LauncherPrefs.lockPortrait = it; save() }
            SwitchLine("fullscreen", tr("Hide status bar", "Durum çubuğunu gizle"), LauncherPrefs.fullScreen) { LauncherPrefs.fullScreen = it; save() }
            SliderRow("${tr("Columns", "Sütun")}: ${LauncherPrefs.columns}", LauncherPrefs.columns.toFloat(), 2f..8f) {
                LauncherPrefs.columns = it.roundToInt(); save()
            }
            SliderRow("${tr("Icon size", "Simge boyutu")}: ${LauncherPrefs.iconDp}", LauncherPrefs.iconDp.toFloat(), 24f..64f) {
                LauncherPrefs.iconDp = it.roundToInt(); save()
            }
        }
        SettingsSection(tr("Dock", "Dok")) {
            SwitchLine("apps", tr("Show dock", "Dok görünsün"), LauncherPrefs.dockShown) { LauncherPrefs.dockShown = it; save() }
            ValueRow("pin", tr("Pinned apps", "Sabitli uygulamalar"),
                if (LauncherPrefs.dock.isEmpty()) tr("Automatic", "Otomatik") else "${LauncherPrefs.dock.size}/5") { pickDock = true }
        }
        SettingsSection(tr("Apps", "Uygulamalar")) {
            ValueRow("sort", tr("Sort", "Sıralama"), sortLabel(LauncherPrefs.sort)) { pickSort = true }
            SliderRow("${tr("Drawer columns", "Çekmece sütunu")}: ${LauncherPrefs.drawerColumns}", LauncherPrefs.drawerColumns.toFloat(), 2f..8f) {
                LauncherPrefs.drawerColumns = it.roundToInt(); save()
            }
            ValueRow("eyeOff", tr("Hidden apps", "Gizli uygulamalar"), "${LauncherPrefs.hidden.size}") { pickHidden = true }
        }
        SettingsSection(tr("System", "Sistem")) {
            ValueRow("home", tr("Default home app", "Varsayılan ana ekran"), if (isHome) tr("Yes", "Evet") else tr("No", "Hayır")) {
                ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
            ValueRow("restore", tr("Reset launcher", "Ana ekranı sıfırla"), "") { askReset = true }
        }
    }
    if (pickLook) PickDialog(tr("Look", "Görünüm"),
        LauncherLooks.all.map { tr(it.nameEn, it.nameTr) to (it.id == LauncherPrefs.look) },
        onDismiss = { pickLook = false }) { i ->
        LauncherPrefs.look = LauncherLooks.all[i].id; save(); pickLook = false
    }
    if (pickWidget) WidgetPickDialog(onPick = { pickWidget = false; addWidget(it) }, onDismiss = { pickWidget = false })
    if (pickWall) com.umutk.pulse.features.WallPickDialog(cur.id, LauncherPrefs.wallIndex(cur.id), onDismiss = { pickWall = false }) { i ->
        LauncherPrefs.setWallIndex(cur.id, i); save(); pickWall = false
    }
    if (pickSort) PickDialog(tr("Sort", "Sıralama"),
        SORTS.map { sortLabel(it) to (it == LauncherPrefs.sort) },
        onDismiss = { pickSort = false },
        extra = { Text(tr("Last opened records each launch on this phone only", "Son açılan, açılışları yalnız bu telefonda kaydeder"), color = T.sub, fontSize = 12.sp) }) { i ->
        LauncherPrefs.sort = SORTS[i]; save(); pickSort = false
    }
    if (pickDock) AppCheckDialog(tr("Dock", "Dok"), LauncherPrefs.dock, 5, onDismiss = { pickDock = false }) {
        LauncherPrefs.dock = it; save(); pickDock = false
    }
    if (pickHidden) AppCheckDialog(tr("Hidden apps", "Gizli uygulamalar"), LauncherPrefs.hidden.toList(), Int.MAX_VALUE, onDismiss = { pickHidden = false }) {
        LauncherPrefs.hidden = it.toSet(); save(); pickHidden = false
    }
    if (askReset) PDialog(tr("Reset launcher", "Ana ekranı sıfırla"),
        tr("Every launcher setting goes back to the default.", "Ana ekran ayarlarının tümü varsayılana döner."),
        tr("Reset", "Sıfırla"), onConfirm = { LauncherPrefs.reset(ctx); askReset = false }, onDismiss = { askReset = false })
}

private val SORTS = listOf("name", "installed", "opened")

private fun sortLabel(s: String): String = when (s) {
    "installed" -> tr("Newest installed", "En son yüklenen")
    "opened" -> tr("Last opened", "Son açılan")
    else -> tr("A to Z", "A'dan Z'ye")
}

/** Whether this app is the phone's home app right now. */
private fun isDefaultHome(ctx: Context): Boolean = runCatching {
    ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == ctx.packageName
}.getOrDefault(false)

@Composable
private fun ValueRow(icon: String, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, T.text, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = T.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwitchLine(icon: String, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, T.text, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(checked, onChange)
    }
}

/** Checklist of launchable apps; at most [max] can be ticked. [onOk] gets the ticked package names. */
@Composable
private fun AppCheckDialog(title: String, initial: List<String>, max: Int, onDismiss: () -> Unit, onOk: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    val apps by produceState(emptyList<LaunchApp>()) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    val picked = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    fun toggle(pkg: String) { if (pkg in picked) picked.remove(pkg) else if (picked.size < max) picked.add(pkg) }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = w.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                apps.forEach { app ->
                    val on = app.pkg in picked
                    Row(Modifier.fillMaxWidth().clickable { toggle(app.pkg) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PCheck(on, { toggle(app.pkg) }, enabled = on || picked.size < max)
                        Spacer(Modifier.width(8.dp))
                        Text(app.label, color = w.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam")) { onOk(picked.toList()) } },
        dismissButton = { PButton(tr("Cancel", "İptal"), onClick = onDismiss) },
    )
}
