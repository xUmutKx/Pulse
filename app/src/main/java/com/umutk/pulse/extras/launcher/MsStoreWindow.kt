package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.store.ExtraModule
import com.umutk.pulse.extras.store.PackButton
import com.umutk.pulse.extras.store.formatMb
import com.umutk.pulse.extras.store.packIcon
import com.umutk.pulse.extras.store.packKind
import com.umutk.pulse.extras.store.packState
import com.umutk.pulse.extras.store.rememberPacks
import com.umutk.pulse.tr

/**
 * Windows 11's Microsoft Store, drawn the way the real one looks: a see-through window, a search box at the top, a narrow rail of places at the left
 * (Home, Looks, Pictures, Widgets, Library), a big banner on Home and the packs as small rounded cards with a Get button. What is in it is this phone's own:
 * the launcher looks, their pictures, the widgets, and the downloadable packs (wallpapers in 4K, icon packs, skins, …).
 */
@Composable
fun MsStoreWindow(onPictures: () -> Unit, onWidgets: () -> Unit, win10: Boolean = false) {
    val ctx = LocalContext.current
    // the body of the window is see-through (the desktop shows through it, as in the real Store); the panels are tinted
    val deskWin = LocalDesktopWin.current
    // Windows 11 is see-through; Windows 10 is opaque and flat (square tiles, no rounding)
    DisposableEffect(deskWin) { if (!win10) deskWin?.glass = true; onDispose { if (!win10) deskWin?.glass = false } }
    var nav by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val packs = rememberPacks().filter { !it.isApk && it.id !in com.umutk.pulse.extras.store.SmallPack.MEMBERS }
    val pane = if (win10) T.panel else T.panel.copy(alpha = .55f)
    val line = T.sub.copy(alpha = .22f)
    val r = if (win10) 0.dp else 8.dp
    val items = listOf("home" to tr("Home", "Ana sayfa"), "palette" to tr("Looks", "Görünümler"), "image" to tr("Pictures", "Resimler"),
        "apps" to tr("Widgets", "Widget'lar"), "download" to tr("Library", "Kitaplık"))
    Column(Modifier.fillMaxSize().background(if (win10) T.bg else T.bg.copy(alpha = .35f))) {
        // the search box: a rounded field in the middle of the top, as in the real Store
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(if (win10) 0.dp else 17.dp)).background(T.bg.copy(alpha = .5f)).border(1.dp, line, RoundedCornerShape(if (win10) 0.dp else 17.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon("search", T.sub, 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(tr("Search packs, looks, widgets and more", "Paket, görünüm, widget ve daha fazlasını ara"), color = T.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicTextField(query, { query = it; if (it.isNotBlank()) nav = 4 }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 13.sp),
                            cursorBrush = SolidColor(T.accent), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // Windows 10: the places are a row of words at the top (the open one underlined in the accent), as in the Store of Windows 10
        if (win10) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items.forEachIndexed { i, (_, label) ->
                Column(Modifier.clickable { nav = i }.padding(vertical = 6.dp)) {
                    Text(label, color = if (nav == i) T.text else T.sub, fontSize = 15.sp, fontWeight = if (nav == i) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    Box(Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth().background(if (nav == i) T.accent else Color.Transparent))
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // the rail of places (Windows 11): the open one has the accent bar at its left edge
            if (!win10) Column(Modifier.width(64.dp).fillMaxHeight().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { i, (icon, label) ->
                    val on = nav == i
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(6.dp)).then(if (on) Modifier.background(pane) else Modifier).clickable { nav = i }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(if (on) 22.dp else 0.dp).clip(RoundedCornerShape(2.dp)).background(T.accent))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            PIcon(icon, if (on) T.accent else T.text, 20.dp)
                            Text(label, color = if (on) T.text else T.sub, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = if (win10) 12.dp else 0.dp, end = 12.dp, bottom = 12.dp, top = if (win10) 8.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (nav) {
                    0 -> {
                        // the banner: a wide rounded tile with a big title, like the featured one of the real Store
                        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(r)).background(Brush.linearGradient(listOf(T.accent, T.accent.copy(alpha = .55f), Color(0xFF5B2A86))))) {
                            Column(Modifier.padding(18.dp).align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tr("Make it yours", "Senin olsun"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                                Text(tr("Wallpapers in 4K, icon packs, skins and more", "4K duvar kağıtları, simge paketleri, kaplamalar ve daha fazlası"), color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .2f)).border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(5.dp)).clickable { nav = 4 }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(tr("See the packs", "Paketlere bak"), color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        // three tiles that open the other places
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(Triple("palette", tr("Looks", "Görünümler"), 1), Triple("image", tr("Pictures", "Resimler"), 2), Triple("apps", tr("Widgets", "Widget'lar"), 3)).forEach { (ic, t3, dest) ->
                                Row(Modifier.weight(1f).clip(RoundedCornerShape(r)).background(pane).border(1.dp, line, RoundedCornerShape(r)).clickable { nav = dest }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PIcon(ic, T.accent, 22.dp)
                                    Text(t3, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Text(tr("Popular packs", "Popüler paketler") + "  ›", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        PackCards(packs.take(6), pane, line, r)
                    }
                    1 -> {
                        Text(tr("Looks", "Görünümler"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(r)).background(pane).border(1.dp, line, RoundedCornerShape(r)).padding(horizontal = 12.dp)) {
                            LauncherLooks.all.forEach { l ->
                                val on = LauncherPrefs.look == l.id
                                Row(Modifier.fillMaxWidth().clickable { LauncherPrefs.look = l.id; LauncherPrefs.save(ctx) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LookPicture(l.id, Modifier.width(72.dp).height(44.dp).clip(RoundedCornerShape(6.dp)))
                                    Text(tr(l.nameEn, l.nameTr), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    if (on) PIcon("check", T.accent, 22.dp)
                                }
                            }
                        }
                    }
                    2 -> {
                        Text(tr("Pictures", "Resimler"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr("Pictures of ${LauncherLooks.byId(LauncherPrefs.look).nameEn}", "${LauncherLooks.byId(LauncherPrefs.look).nameTr} resimleri"), color = T.sub, fontSize = 13.sp)
                        PButton(tr("Browse pictures", "Resimlere göz at"), onClick = onPictures)
                    }
                    3 -> {
                        Text(tr("Widgets", "Widget'lar"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr("Widgets on this phone", "Bu telefondaki widget'lar"), color = T.sub, fontSize = 13.sp)
                        PButton(tr("Browse widgets", "Widget'lara göz at"), onClick = onWidgets)
                    }
                    else -> {
                        Text(tr("Library", "Kitaplık"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        val shown = packs.filter { query.isBlank() || it.name().contains(query, ignoreCase = true) }
                        if (shown.isEmpty()) Text(tr("No packs to show (offline?)", "Gösterilecek paket yok (çevrimdışı mı?)"), color = T.sub, fontSize = 13.sp)
                        PackCards(shown, pane, line, r)
                    }
                }
            }
        }
    }
}

/** The packs as small rounded cards in a grid (as many columns as fit): the kind's icon on a tinted tile, the name, the kind and size, and the Get / Remove button. */
@Composable
private fun PackCards(list: List<ExtraModule>, pane: Color, line: Color, r: androidx.compose.ui.unit.Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 250.dp).toInt().coerceIn(1, 4)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { m -> Box(Modifier.weight(1f)) { PackCard(m, pane, line, r) } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PackCard(m: ExtraModule, pane: Color, line: Color, r: androidx.compose.ui.unit.Dp) {
    val (state, pct) = packState(m)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(r)).background(pane).border(1.dp, line, RoundedCornerShape(r)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp).height(48.dp).clip(RoundedCornerShape(r)).background(T.accent.copy(alpha = .22f)), contentAlignment = Alignment.Center) { PIcon(packIcon(m.kategori), T.text, 24.dp) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(m.name(), color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(packKind(m.kategori) + "  ·  " + formatMb(m.boyut), color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PackButton(m, state, pct)
    }
}
