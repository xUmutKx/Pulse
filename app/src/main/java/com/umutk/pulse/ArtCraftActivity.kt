package com.umutk.pulse

import android.graphics.Color as AColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat


private fun artFont(): FontFamily = BundledFonts[0].second

/** "ArtCraft": standalone launcher app with its own task. The window is see-through, the look's background sits over it. */
class ArtCraftActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        ArtCraft.load(this)
        window.setBackgroundDrawable(ColorDrawable(AColor.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        setContent { LookHost(transparent = true, lookId = "material") { LookFade { ArtScreen() } } }
    }

    // back from an install or a download: read the states again
    override fun onResume() { super.onResume(); ArtCraft.tick++ }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
    override fun onDestroy() { super.onDestroy(); AppLookGuard.leave(this) }
}

/** The launcher: square tiles (default, 1 to 3 per row) or wide cards with a description (list). */
@Composable
fun ArtScreen() {
    val c = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ArtCraft.Item?>(null) }
    val tick = ArtCraft.tick
    LaunchedEffect(Unit) { if (ArtCraft.items.isEmpty()) ArtCraft.refresh(c) }
    Column(Modifier.fillMaxSize().background(if (ArtCraft.translucent) Color.Black.copy(alpha = .55f) else T.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ArtCraft", color = T.text, fontSize = 30.sp, fontFamily = artFont())
                Text(ArtCraft.tag.ifEmpty { tr("Loading…", "Yükleniyor…") }, color = T.sub, fontSize = 12.sp)
            }
            Box(Modifier.size(40.dp).clickable { ArtCraft.refresh(c) }, contentAlignment = Alignment.Center) { PIcon("refresh", T.text, 22.dp) }
            Box(Modifier.size(40.dp).clickable { settings = true }, contentAlignment = Alignment.Center) { PIcon("settings", T.text, 22.dp) }
        }
        if (ArtCraft.status.isNotEmpty()) Text(ArtCraft.status, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (ArtCraft.view == "list") {
            LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ArtCraft.items, key = { it.file }) { item -> ArtCard(item, tick) { detail = it } }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(ArtCraft.columns), Modifier.fillMaxSize().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                gridItems(ArtCraft.items, key = { it.file }) { item -> ArtTile(item, tick) { detail = it } }
            }
        }
    }
    if (settings) ArtSettings { settings = false }
    detail?.let { item -> ArtDetail(item) { detail = null } }
}

/** The app's colour from its icon, or the look's text colour before the icon is known. */
@Composable
private fun appColor(item: ArtCraft.Item): Color = ArtCraft.colors[item.app] ?: T.text

/** The icon from the APK; a letter tile until it has been read. */
@Composable
private fun appIcon(item: ArtCraft.Item, size: Dp) {
    val icon = ArtCraft.icons[item.app]
    val shape = RoundedCornerShape(size / 4)
    if (icon != null) Image(icon, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Fit)
    else Box(Modifier.size(size).clip(shape).background(T.panel), contentAlignment = Alignment.Center) {
        Text(item.app.take(1), color = T.sub, fontSize = (size.value / 2).sp, fontFamily = artFont())
    }
}

private fun stateLabel(s: ArtCraft.State): String = when (s) {
    ArtCraft.State.NONE -> tr("Not downloaded", "İndirilmedi")
    ArtCraft.State.INSTALL -> tr("Downloaded", "İndirildi")
    ArtCraft.State.UPDATE -> tr("Update available", "Güncelleme var")
    ArtCraft.State.INSTALLED -> tr("Installed", "Yüklü")
}

/** The one action button: outlined in the app's colour; while it downloads it is filled with that colour and shows the percent. */
@Composable
private fun ArtButton(item: ArtCraft.Item) {
    val c = LocalContext.current
    val pct = ArtCraft.progress[item.app]
    val state = ArtCraft.stateOf(c, item)
    val color = appColor(item)
    val label = when (state) {
        ArtCraft.State.NONE -> tr("Download", "İndir")
        ArtCraft.State.INSTALL -> tr("Install", "Kur")
        ArtCraft.State.UPDATE -> tr("Update", "Güncelle")
        ArtCraft.State.INSTALLED -> tr("Open", "Aç")
    }
    val shape = RoundedCornerShape(20.dp)
    val busy = pct != null
    val fill = if (busy) color else Color.Transparent
    val ink = if (busy) com.umutk.pulse.onAccent(color) else color
    Box(Modifier.height(36.dp).clip(shape).background(fill).border(1.dp, color, shape)
        .clickable(enabled = !busy) { ArtCraft.tap(c, item) }
        .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(if (busy) "$pct%" else label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** List view: a wide card, icon on the left, name and description, the button on the right. */
@Composable
private fun ArtCard(item: ArtCraft.Item, tick: Int, onOpen: (ArtCraft.Item) -> Unit) {
    val c = LocalContext.current
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp).clip(RoundedCornerShape(20.dp)).background(T.panel).clickable { onOpen(item) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        appIcon(item, 96.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.app, color = T.text, fontSize = 20.sp, fontFamily = artFont(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ArtCraft.details) {
                Text("%.1f MB · %s".format(item.size / 1_000_000.0, stateLabel(ArtCraft.stateOf(c, item))), color = T.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ArtCraft.pkgOf(item), color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        ArtButton(item)
    }
}

/** Square view: an icon tile, name, state and the button under it. */
@Composable
private fun ArtTile(item: ArtCraft.Item, tick: Int, onOpen: (ArtCraft.Item) -> Unit) {
    val c = LocalContext.current
    Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(T.panel).clickable { onOpen(item) }.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { appIcon(item, 84.dp) }
        Text(item.app, color = T.text, fontSize = 15.sp, fontFamily = artFont(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (ArtCraft.details) Text(stateLabel(ArtCraft.stateOf(c, item)), color = T.sub, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        ArtButton(item)
    }
}

/** The detail page of one app: big icon, state, version, size, the release notes, and the trash button. Opened by tapping a tile or a card. */
@Composable
private fun ArtDetail(item: ArtCraft.Item, onClose: () -> Unit) {
    val c = LocalContext.current
    BackHandler(onBack = onClose)
    val state = ArtCraft.stateOf(c, item)
    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable { ArtCraft.trash(c, item) }, contentAlignment = Alignment.Center) { PIcon("delete", T.text, 22.dp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            appIcon(item, 120.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.app, color = T.text, fontSize = 30.sp, fontFamily = artFont())
                Text(stateLabel(state), color = appColor(item), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        ArtButton(item)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Package", "Paket") + ": " + ArtCraft.pkgOf(item), color = T.sub, fontSize = 12.sp)
            Text(tr("Version", "Sürüm") + ": " + (ArtCraft.versionOf(c, item) ?: "—"), color = T.sub, fontSize = 12.sp)
            Text(tr("Size", "Boyut") + ": " + "%.1f MB".format(item.size / 1_000_000.0), color = T.sub, fontSize = 12.sp)
            Text(tr("Release", "Sürüm grubu") + ": " + ArtCraft.tag, color = T.sub, fontSize = 12.sp)
        }
        if (ArtCraft.notes.isNotBlank()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(T.panel).padding(14.dp)) {
            Text(tr("Release notes", "Yayın notları"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(ArtCraft.notes, color = T.sub, fontSize = 13.sp)
        }
    }
}

/** Layout settings: square or list, columns for square, details, download mode and the background. */
@Composable
private fun ArtSettings(close: () -> Unit) {
    val c = LocalContext.current
    com.umutk.pulse.WinAlert(onDismissRequest = close,
        title = { Text(tr("Layout", "Görünüm")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("View", "Görünüm"), color = T.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(ArtCraft.view == "square", tr("Square", "Kare")) { ArtCraft.view = "square"; ArtCraft.saveLayout(c) }
                PChip(ArtCraft.view == "list", tr("List", "Liste")) { ArtCraft.view = "list"; ArtCraft.saveLayout(c) }
            }
            Text(tr("Squares per row", "Satır başı kare"), color = T.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { n -> PChip(ArtCraft.columns == n, n.toString()) { ArtCraft.columns = n; ArtCraft.saveLayout(c) } }
            }
            Text(tr("Details", "Detaylar"), color = T.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(ArtCraft.details, tr("On", "Açık")) { ArtCraft.details = true; ArtCraft.saveLayout(c) }
                PChip(!ArtCraft.details, tr("Off", "Kapalı")) { ArtCraft.details = false; ArtCraft.saveLayout(c) }
            }
            Text(tr("Download", "İndirme"), color = T.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(ArtCraft.mode == "apk", tr("Separate APK", "Ayrı APK")) { ArtCraft.mode = "apk"; ArtCraft.saveLayout(c) }
                PChip(ArtCraft.mode == "pulse", tr("In Pulse", "Pulse içinde")) { ArtCraft.mode = "pulse"; ArtCraft.saveLayout(c) }
            }
            Text(tr("Background", "Arka plan"), color = T.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(!ArtCraft.translucent, tr("Solid", "Düz")) { ArtCraft.translucent = false; ArtCraft.saveLayout(c) }
                PChip(ArtCraft.translucent, tr("Translucent", "Yarı saydam")) { ArtCraft.translucent = true; ArtCraft.saveLayout(c) }
            }
        } },
        confirmButton = { TextButton(close) { Text(tr("Close", "Kapat")) } })
}
