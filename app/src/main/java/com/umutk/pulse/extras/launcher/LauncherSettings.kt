package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
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
    var cat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    BackHandler { if (cat.isNotEmpty()) cat = "" else back() }
    var pickLook by remember { mutableStateOf(false) }
    var pickDock by remember { mutableStateOf(false) }
    var pickPack by remember { mutableStateOf(false) }
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
    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { if (cat.isNotEmpty()) cat = "" else back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(if (cat.isEmpty()) tr("Launcher", "Ana ekran") else LAUNCHER_CATS.first { it.key == cat }.let { tr(it.en, it.trName) }, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        // the first page is the list of categories; each one opens as a page of its own
        if (cat.isEmpty()) LAUNCHER_CATS.forEach { k ->
            Row(Modifier.fillMaxWidth().clickable { cat = k.key }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { PIcon(k.icon, T.accent, 26.dp) }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(tr(k.en, k.trName), color = T.text, fontSize = 16.sp)
                    Text(tr(k.enSub, k.trSub), color = T.sub, fontSize = 12.sp)
                }
                PIcon("chevron", T.sub, 16.dp)
            }
        }
        if (cat == "look") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        if (cat == "widgets") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        // the Windows desktops: which shortcuts sit on the desktop (system icons and Pulse's own apps)
        if (cat == "desktop" && cur.id in setOf("win11", "win10", "win10black", "win7", "win95", "xp")) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            desktopItems().forEach { it ->
                SwitchLine(it.iconKey ?: "apps", it.label, it.pkg in LauncherPrefs.desktopOn) { on ->
                    LauncherPrefs.desktopOn = if (on) LauncherPrefs.desktopOn + it.pkg else LauncherPrefs.desktopOn - it.pkg; save()
                }
            }
        }
        if (cat == "home") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cur.id.startsWith("winphone")) SwitchLine("gridView", tr("Live tiles", "Canlı kutucuklar"), LauncherPrefs.phoneTiles) { LauncherPrefs.phoneTiles = it; save() }
            if (cur.id.startsWith("winphone") && LauncherPrefs.phoneTiles) {
                Text(tr("Tile layout", "Kutucuk düzeni"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to tr("Mixed", "Karışık"), "medium" to tr("All medium", "Hepsi orta"), "small" to tr("All small", "Hepsi küçük")).forEach { (k, n) ->
                        com.umutk.pulse.PChip(LauncherPrefs.tileMode == k, n) { LauncherPrefs.tileMode = k; LauncherPrefs.tileSizes = emptyMap(); save() }
                    }
                }
            }
            SwitchLine("settings", tr("Buttons at the top right", "Sağ üstteki düğmeler"), LauncherPrefs.topChips) { LauncherPrefs.topChips = it; save() }
            if (cur.id in setOf("win11", "win10", "win7", "xp", "win95")) SwitchLine("clock", tr("Desktop clock widget", "Masaüstü saat widget'ı"), LauncherPrefs.desktopClock) { LauncherPrefs.desktopClock = it; save() }
            if (cur.id in setOf("win11", "win10", "win7", "xp", "win95")) ValueRow("notes", tr("Add a sticky note", "Yapışkan not ekle"), "") { stickyNoteProvider(ctx)?.let(addWidget) }
            SwitchLine("clock", tr("Clock and date", "Saat ve tarih"), LauncherPrefs.clock) { LauncherPrefs.clock = it; save() }
            SwitchLine("apps", tr("Apps on the home screen", "Ana ekranda uygulamalar"), LauncherPrefs.homeApps) { LauncherPrefs.homeApps = it; save() }
            SwitchLine("gridView", tr("Open apps in windows", "Uygulamaları pencerede aç"), LauncherPrefs.windowed) { LauncherPrefs.windowed = it; save() }
            SwitchLine("gridView", tr("Vertical scrolling", "Dikey kaydırma"), LauncherPrefs.vertical) { LauncherPrefs.vertical = it; save() }
            SwitchLine("rotate", tr("Lock to portrait", "Dikey ekranda kilitle"), LauncherPrefs.lockPortrait) { LauncherPrefs.lockPortrait = it; save() }
            SwitchLine("fullscreen", tr("Hide status bar", "Durum çubuğunu gizle"), LauncherPrefs.fullScreen) { LauncherPrefs.fullScreen = it; save() }
            SliderRow("${tr("Columns", "Sütun")}: ${LauncherPrefs.columns}", LauncherPrefs.columns.toFloat(), 2f..8f) {
                LauncherPrefs.columns = it.roundToInt(); save()
            }
            SliderRow("${tr("Rows", "Satır")}: ${LauncherPrefs.rows}", LauncherPrefs.rows.toFloat(), 3f..8f) {
                LauncherPrefs.rows = it.roundToInt(); save()
            }
            SliderRow("${tr("Icon size", "Simge boyutu")}: ${LauncherPrefs.iconDp}", LauncherPrefs.iconDp.toFloat(), 24f..64f) {
                LauncherPrefs.iconDp = it.roundToInt(); save()
            }
            SwitchLine("notes", tr("Names under the icons", "Simgelerin altında adlar"), LauncherPrefs.labels) { LauncherPrefs.labels = it; save() }
            SwitchLine("gridView", tr("Page dots", "Sayfa noktaları"), LauncherPrefs.pageDots) { LauncherPrefs.pageDots = it; save() }
            Text(tr("Page effect", "Sayfa efekti"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("simple" to tr("Simple", "Basit"), "cube" to tr("Cube", "Küp"), "zoom" to tr("Zoom fade", "Yakınlaş"), "stack" to tr("Card stack", "Kart yığını"), "flip" to tr("Flip", "Çevir")).forEach { (k, n) ->
                    com.umutk.pulse.PChip(LauncherPrefs.pageEffect == k, n) { LauncherPrefs.pageEffect = k; save() }
                }
            }
        }
        if (cat == "dock") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SwitchLine("apps", tr("Show dock", "Dok görünsün"), LauncherPrefs.dockShown) { LauncherPrefs.dockShown = it; save() }
            ValueRow("pin", tr("Pinned apps", "Sabitli uygulamalar"),
                if (LauncherPrefs.dock.isEmpty()) tr("Automatic", "Otomatik") else "${LauncherPrefs.dock.size}/${LauncherPrefs.dockCount}") { pickDock = true }
            SliderRow("${tr("Icons in the dock", "Dok'taki simge")}: ${LauncherPrefs.dockCount}", LauncherPrefs.dockCount.toFloat(), 3f..7f) {
                LauncherPrefs.dockCount = it.roundToInt(); save()
            }
        }
        if (cat == "apps") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ValueRow("sort", tr("Sort", "Sıralama"), sortLabel(LauncherPrefs.sort)) { pickSort = true }
            SliderRow("${tr("Drawer columns", "Çekmece sütunu")}: ${LauncherPrefs.drawerColumns}", LauncherPrefs.drawerColumns.toFloat(), 2f..8f) {
                LauncherPrefs.drawerColumns = it.roundToInt(); save()
            }
            ValueRow("eyeOff", tr("Hidden apps", "Gizli uygulamalar"), "${LauncherPrefs.hidden.size}") { pickHidden = true }
            ValueRow("palette", tr("Icon pack", "Simge paketi"), IconPacks.installed(ctx).firstOrNull { it.pkg == LauncherPrefs.iconPack }?.label ?: tr("Default", "Varsayılan")) { pickPack = true }
        }
        if (cat == "gestures") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // gestures
            SwitchLine("lock", tr("Double tap to lock the screen", "Çift dokunuşla ekranı kilitle"), LauncherPrefs.gestureLock) { LauncherPrefs.gestureLock = it; save() }
            SwitchLine("chevron", tr("Swipe down: notifications", "Aşağı kaydır: bildirimler"), LauncherPrefs.swipeDownShade) { LauncherPrefs.swipeDownShade = it; save() }
            SwitchLine("search", tr("Swipe down: quick search", "Aşağı kaydır: hızlı arama"), LauncherPrefs.swipeDownSearch) { LauncherPrefs.swipeDownSearch = it; save() }
            SwitchLine("apps", tr("Swipe up: apps list", "Yukarı kaydır: uygulama listesi"), LauncherPrefs.swipeUpApps) { LauncherPrefs.swipeUpApps = it; save() }
            Text(tr("The lock and notification gestures use the \"Pulse · Screen cover layer\" accessibility service.", "Kilit ve bildirim hareketleri \"Pulse · Screen cover layer\" erişilebilirlik hizmetini kullanır."), color = T.sub, fontSize = 12.sp)
            SwitchLine("sensors", tr("Tick on long press", "Uzun basınca titreşim"), LauncherPrefs.haptic) { LauncherPrefs.haptic = it; save() }
        }
        if (cat == "feel") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cur.id in com.umutk.pulse.loginLooks) {
                SwitchLine("power", tr("Boot screen when the launcher starts", "Başlatıcı açılırken önyükleme ekranı"), LauncherPrefs.bootScreen) { LauncherPrefs.bootScreen = it; save() }
                SwitchLine("users", tr("Sign-in screen when the launcher starts", "Başlatıcı açılırken giriş ekranı"), LauncherPrefs.loginScreen) { LauncherPrefs.loginScreen = it; save() }
            }
            if (cur.id in setOf("win95", "xp")) SwitchLine("help", tr("Office Assistant (Clippy) on the desktop", "Masaüstünde Office Yardımcısı (Clippy)"), LauncherPrefs.clippy) { LauncherPrefs.clippy = it; save() }
            // icons
            Text(tr("App picture shape", "Uygulama resmi şekli"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("squircle" to tr("Squircle", "Yumuşak kare"), "circle" to tr("Circle", "Daire"), "rounded" to tr("Rounded", "Yuvarlak köşe"), "square" to tr("Square", "Kare")).forEach { (k, n) ->
                    com.umutk.pulse.PChip(LauncherPrefs.iconShape == k, n) { LauncherPrefs.iconShape = k; save() }
                }
            }
            // wallpaper
            SwitchLine("image", tr("A different wallpaper every day", "Her gün farklı duvar kağıdı"), LauncherPrefs.wallDaily) { LauncherPrefs.wallDaily = it; save() }
            SwitchLine("volume", tr("Sound effects (start-up chime, clicks)", "Ses efektleri (açılış sesi, tıklama)"), LauncherPrefs.sounds) { LauncherPrefs.sounds = it; save() }
            SliderRow("${tr("Darken the wallpaper", "Duvar kağıdını karart")}: ${LauncherPrefs.wallDim}%", LauncherPrefs.wallDim.toFloat(), 0f..70f) { LauncherPrefs.wallDim = it.roundToInt(); save() }
            SliderRow("${tr("Blur the wallpaper", "Duvar kağıdını bulanıklaştır")}: ${LauncherPrefs.wallBlur}", LauncherPrefs.wallBlur.toFloat(), 0f..24f) { LauncherPrefs.wallBlur = it.roundToInt(); save() }
        }
        if (cat == "screen") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // clock and screen
            SwitchLine("clock", tr("24-hour clock", "24 saatlik saat"), LauncherPrefs.clock24) { LauncherPrefs.clock24 = it; save() }
            SwitchLine("clock", tr("Seconds on the desktop clock", "Masaüstü saatinde saniye"), LauncherPrefs.clockSeconds) { LauncherPrefs.clockSeconds = it; save() }
            Text(tr("Date in the taskbar", "Görev çubuğunda tarih"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dmy" to "31/12/26", "mdy" to "12/31/26", "ymd" to "2026-12-31").forEach { (k, n) -> com.umutk.pulse.PChip(LauncherPrefs.dateFmt == k, n) { LauncherPrefs.dateFmt = k; save() } }
            }
            SwitchLine("light", tr("Keep the screen on at home", "Ana ekranda ekran açık kalsın"), LauncherPrefs.keepOn) { LauncherPrefs.keepOn = it; save() }
            SwitchLine("search", tr("Keyboard opens in the apps search", "Uygulama aramasında klavye açılsın"), LauncherPrefs.searchFocus) { LauncherPrefs.searchFocus = it; save() }
            SwitchLine("lock", tr("Lock the layout (gadgets stay where they are)", "Düzeni kilitle (gadget'lar yerinde kalır)"), LauncherPrefs.lockLayout) { LauncherPrefs.lockLayout = it; save() }
        }
        if (cat == "folders") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Folder picture", "Klasör resmi"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("grid" to tr("Grid", "Izgara"), "line" to tr("Line", "Satır"), "stack" to tr("Stack", "Yığın")).forEach { (k, n) ->
                    com.umutk.pulse.PChip(LauncherPrefs.folderStyle == k, n) { LauncherPrefs.folderStyle = k; save() }
                }
            }
        }
        if (cat == "badges") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SwitchLine("bell", tr("Notification badges on the app pictures", "Uygulama resimlerinde bildirim sayısı"), LauncherPrefs.badges) { LauncherPrefs.badges = it; save() }
            if (LauncherPrefs.badges) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("number" to tr("Numbers", "Sayı"), "dot" to tr("Dots", "Nokta")).forEach { (k, n) -> com.umutk.pulse.PChip(LauncherPrefs.badgeStyle == k, n) { LauncherPrefs.badgeStyle = k; save() } }
            }
            Text(tr("Badges need notification access for one of Pulse's listeners (Notification history).", "Rozetler için Pulse'un bildirim erişimi (Bildirim geçmişi) açık olmalı."), color = T.sub, fontSize = 12.sp)
        }
        if (cat == "system") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ValueRow("home", tr("Default home app", "Varsayılan ana ekran"), if (isHome) tr("Yes", "Evet") else tr("No", "Hayır")) {
                ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        }
        if (cat == "backup") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ValueRow("save", tr("Export launcher settings", "Ana ekran ayarlarını dışa aktar"), "Download/PulseLauncher.properties") {
                android.widget.Toast.makeText(ctx, if (LauncherPrefs.exportSettings(ctx)) tr("Saved to Download", "Download'a kaydedildi") else tr("Could not save", "Kaydedilemedi"), android.widget.Toast.LENGTH_SHORT).show()
            }
            ValueRow("download", tr("Import launcher settings", "Ana ekran ayarlarını içe aktar"), "Download/PulseLauncher.properties") {
                android.widget.Toast.makeText(ctx, if (LauncherPrefs.importSettings(ctx)) tr("Settings loaded", "Ayarlar yüklendi") else tr("No file found in Download", "Download'da dosya yok"), android.widget.Toast.LENGTH_SHORT).show()
            }
            ValueRow("restore", tr("Reset launcher", "Ana ekranı sıfırla"), "") { askReset = true }
        }
    }
    if (pickLook) com.umutk.pulse.WinAlert(onDismissRequest = { pickLook = false },
        title = { Text(tr("Look", "Görünüm")) },
        text = {
            // every look as a small picture of its home screen, three to a row
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LauncherLooks.all.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { l ->
                            val sel = l.id == LauncherPrefs.look
                            Column(Modifier.weight(1f).clickable { LauncherPrefs.look = l.id; save(); pickLook = false }
                                .border(if (sel) 2.dp else 1.dp, if (sel) com.umutk.pulse.Look.accent else T.line, RoundedCornerShape(8.dp)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                LookPicture(l.id, Modifier.fillMaxWidth().aspectRatio(.62f))
                                Text(tr(l.nameEn, l.nameTr), color = T.text, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { com.umutk.pulse.PButton(tr("Close", "Kapat")) { pickLook = false } })
    if (pickPack) {
        val packs = remember { IconPacks.installed(ctx) }
        PickDialog(tr("Icon pack", "Simge paketi"), listOf(tr("Default", "Varsayılan") to LauncherPrefs.iconPack.isBlank()) + packs.map { it.label to (it.pkg == LauncherPrefs.iconPack) }, onDismiss = { pickPack = false },
            extra = { if (packs.isEmpty()) Text(tr("No icon pack is installed (Nova / ADW packs from the Play Store work).", "Yüklü simge paketi yok (Play Store'daki Nova / ADW paketleri çalışır)."), color = T.sub, fontSize = 12.sp) }) { i ->
            LauncherPrefs.iconPack = if (i == 0) "" else packs[i - 1].pkg; save(); pickPack = false
        }
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
    if (pickDock) AppCheckDialog(tr("Dock", "Dok"), LauncherPrefs.dock, LauncherPrefs.dockCount, onDismiss = { pickDock = false }) {
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

/** One category of the launcher settings: its colour, icon, name and what is inside. */
private class LCat(val key: String, val color: Long, val icon: String, val en: String, val trName: String, val enSub: String, val trSub: String)

private val LAUNCHER_CATS = listOf(
    LCat("look", 0xFF7E57C2, "palette", "Look", "Görünüm", "Look, wallpaper", "Görünüm, duvar kağıdı"),
    LCat("home", 0xFF43A047, "apps", "Home screen", "Ana ekran", "Grid, icons, pages, windows, orientation", "Izgara, simgeler, sayfalar, pencereler, yön"),
    LCat("dock", 0xFFEF6C00, "pin", "Dock", "Dok", "Pinned apps", "Sabitli uygulamalar"),
    LCat("apps", 0xFF3949AB, "sort", "App drawer", "Uygulama çekmecesi", "Sort, columns, hidden apps, icon pack", "Sıralama, sütunlar, gizli uygulamalar, simge paketi"),
    LCat("folders", 0xFFF9A825, "folder", "Folders", "Klasörler", "How a folder looks", "Klasörün görünümü"),
    LCat("widgets", 0xFF1E88E5, "gridView", "Widgets", "Widget'lar", "Add and remove widgets", "Widget ekle ve kaldır"),
    LCat("desktop", 0xFF26A69A, "folder", "Desktop icons", "Masaüstü simgeleri", "Which shortcuts sit on the Windows desktops", "Windows masaüstlerindeki kısayollar"),
    LCat("feel", 0xFF8E24AA, "palette", "Look and feel", "Görünüm ve his", "Icon shape, wallpaper effects, sounds, boot screens", "Simge şekli, duvar kağıdı efektleri, sesler, açılış ekranları"),
    LCat("gestures", 0xFFD81B60, "sensors", "Gestures and inputs", "Hareketler ve girişler", "Double tap, swipes, vibration", "Çift dokunuş, kaydırmalar, titreşim"),
    LCat("badges", 0xFFE53935, "bell", "Notification badges", "Bildirim rozetleri", "Numbers or dots on the apps", "Uygulamalarda sayı ya da nokta"),
    LCat("screen", 0xFF00897B, "clock", "Clock and screen", "Saat ve ekran", "Clock formats, screen on, layout lock", "Saat biçimi, ekran açık kalsın, düzen kilidi"),
    LCat("backup", 0xFF546E7A, "save", "Backup and reset", "Yedek ve sıfırlama", "Export, import, reset", "Dışa aktar, içe aktar, sıfırla"),
    LCat("system", 0xFF6D4C41, "settings", "System", "Sistem", "Default home app", "Varsayılan ana ekran"),
)

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
        // the title keeps the room it needs; a long value (a file path) is cut short instead of squeezing the title into one letter per line
        if (value.isNotEmpty()) Text(value, color = T.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 140.dp))
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


/** A little picture of a launcher look's home screen: its wallpaper colours, its taskbar or dock, a few icons. */
@Composable
internal fun LookPicture(id: String, modifier: Modifier) {
    val bg: Brush; val bar: Color?; val tiles: List<Color>
    when {
        id == "win11" -> { bg = Brush.verticalGradient(listOf(Color(0xFF1B2A6B), Color(0xFFB0306E))); bar = Color(0xCC202020); tiles = listOf(Color(0xFF0078D4), Color(0xFF8764B8)) }
        id.startsWith("win10") -> { bg = Brush.verticalGradient(listOf(Color(0xFF0A5FB4), Color(0xFF53B0F0))); bar = Color(0xFF101010); tiles = listOf(Color(0xFF0078D7), Color(0xFF00B294)) }
        id == "win7" -> { bg = Brush.verticalGradient(listOf(Color(0xFF6E9CCB), Color(0xFF2F5C8C))); bar = Color(0xFF3A6592); tiles = listOf(Color(0xFF9BC3E8), Color(0xFFFFFFFF)) }
        id == "xp" -> { bg = Brush.verticalGradient(listOf(Color(0xFF3A93FF), Color(0xFF4CA32C))); bar = Color(0xFF0831D9); tiles = listOf(Color(0xFFFFFFFF), Color(0xFFD6E6FF)) }
        id == "win95" -> { bg = SolidColor(Color(0xFF008080)); bar = Color(0xFFC0C0C0); tiles = listOf(Color(0xFFC0C0C0), Color(0xFFFFFF00)) }
        id.startsWith("winphone") -> { bg = SolidColor(Color.Black); bar = null; tiles = listOf(Color(0xFF0078D7), Color(0xFFE51400), Color(0xFF60A917), Color(0xFFF0A30A)) }
        id == "amoled" -> { bg = SolidColor(Color.Black); bar = Color(0xFF2B2B2B); tiles = listOf(Color.White, Color(0xFF777777)) }
        else -> { bg = Brush.verticalGradient(listOf(Color(0xFFF3EDF7), Color(0xFFD0BCFF))); bar = Color(0xFFE8DEF8); tiles = listOf(Color(0xFF6750A4), Color(0xFFB3A0E0)) }
    }
    Canvas(modifier.clip(RoundedCornerShape(4.dp))) {
        drawRect(bg)
        val phone = id.startsWith("winphone")
        if (phone) {
            val s = size.width / 2.3f; val g = size.width * .04f
            tiles.forEachIndexed { i, c -> drawRect(c, Offset(g + (i % 2) * (s + g), g + (i / 2) * (s + g)), Size(s, s)) }
        } else {
            tiles.forEachIndexed { i, c -> drawRect(c.copy(alpha = .9f), Offset(size.width * .1f, size.height * (.08f + i * .14f)), Size(size.width * .16f, size.width * .16f)) }
        }
        bar?.let { drawRect(it, Offset(0f, size.height * .9f), Size(size.width, size.height * .1f)) }
    }
}
