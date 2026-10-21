package com.umutk.pulse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.umutk.pulse.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The chrome families of the photo viewer / editor. */
internal enum class ImgKind { W11, CLASSIC, MATERIAL, ONEUI, COLOROS }

internal fun imgKind(): ImgKind = when (T.id) {
    "win11", "win10" -> ImgKind.W11
    "win95", "xp", "win7" -> ImgKind.CLASSIC
    "oneui" -> ImgKind.MATERIAL  // Samsung Gallery layout is the Material overlay chrome
    "coloros" -> ImgKind.COLOROS
    else -> if (T.material) ImgKind.MATERIAL else ImgKind.W11
}

/** Everything the viewer's bars can trigger. [onGrid] is null when the picture has no folder to browse. */
internal class ImgActs(
    val onBack: () -> Unit, val onShare: () -> Unit, val onEdit: () -> Unit, val onInfo: () -> Unit, val onDelete: () -> Unit,
    val onGrid: (() -> Unit)?, val onZoom: () -> Unit, val onSlide: () -> Unit, val slideOn: Boolean, val onSetAs: () -> Unit,
    val onLive: (() -> Unit)? = null, val fav: Boolean = false, val onFav: () -> Unit = {},
)

private val ImgMatKeys = setOf("back", "delete", "info", "close", "play")

/** The theme's icon [key]; the Material pack lacks many keys, so there the [fallback] vector is drawn. */
@Composable
internal fun ImgIc(key: String, fallback: ImageVector, tint: Color, size: Dp = 22.dp) {
    val plainMaterial = T.material && T.id != "oneui" && T.id != "coloros" && key !in ImgMatKeys
    if (plainMaterial) Icon(fallback, null, Modifier.size(size), tint) else PIcon(key, tint, size)
}

@Composable
internal fun ImgIcBtn(onClick: () -> Unit, size: Dp = 40.dp, bg: Color = Color.Transparent, shape: Shape = CircleShape, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(shape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ImgMoreMenu(open: Boolean, onDismiss: () -> Unit, a: ImgActs) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        a.onGrid?.let { g -> DropdownMenuItem(text = { Text(tr("Gallery", "Galeri")) }, onClick = { onDismiss(); g() }) }
        DropdownMenuItem(text = { Text(tr("Slideshow", "Slayt gösterisi")) }, onClick = { onDismiss(); a.onSlide() })
        DropdownMenuItem(text = { Text(tr("Set as", "Şu olarak ayarla")) }, onClick = { onDismiss(); a.onSetAs() })
        DropdownMenuItem(text = { Text(tr("Details", "Ayrıntılar")) }, onClick = { onDismiss(); a.onInfo() })
    }
}

/** Shows / hides a bar with an animation: collapsing in the Windows column layouts, sliding when it floats over the photo. */
@Composable
private fun ImgBar(visible: Boolean, top: Boolean, overlay: Boolean, content: @Composable () -> Unit) {
    val enter = if (overlay) fadeIn() + slideInVertically { if (top) -it / 2 else it / 2 } else fadeIn() + expandVertically(expandFrom = if (top) Alignment.Top else Alignment.Bottom)
    val exit = if (overlay) fadeOut() + slideOutVertically { if (top) -it / 2 else it / 2 } else fadeOut() + shrinkVertically(shrinkTowards = if (top) Alignment.Top else Alignment.Bottom)
    AnimatedVisibility(visible, enter = enter, exit = exit) { content() }
}

/** Strip with the other pictures of the viewer; the current one is bigger and ringed. */
@Composable
private fun ImgFilmstrip(items: List<ImgSrc>, index: Int, onPick: (Int) -> Unit, corner: Dp, modifier: Modifier = Modifier) {
    if (items.size < 2 || !PhotosPrefs.filmstrip) return
    val ls = rememberLazyListState()
    LaunchedEffect(index, items.size) { if (index in items.indices) runCatching { ls.animateScrollToItem(index) } }
    val sh = RoundedCornerShape(corner)
    LazyRow(state = ls, modifier = modifier.fillMaxWidth().height(62.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(items) { i, s ->
            val on = i == index
            val sz by animateDpAsState(if (on) 52.dp else 42.dp, label = "film")
            ImgThumb(s, Modifier.size(sz).clip(sh).border(if (on) 2.dp else 0.dp, if (on) Look.accent else Color.Transparent, sh).clickable { onPick(i) }, 120)
        }
    }
}

/** The viewer's frame: bars and buttons in the current look around [content] (the pager). */
@Composable
internal fun ImgChrome(title: String, pos: String, bars: Boolean, a: ImgActs, items: List<ImgSrc>, index: Int, onPick: (Int) -> Unit, content: @Composable () -> Unit) {
    when (imgKind()) {
        ImgKind.W11 -> ImgW11(title, pos, bars, a, items, index, onPick, content)
        ImgKind.CLASSIC -> ImgClassic(title, pos, bars, a, items, index, onPick, content)
        ImgKind.MATERIAL -> ImgMaterial(title, pos, bars, a, items, index, onPick, content)
        ImgKind.ONEUI -> ImgOneUi(title, pos, bars, a, items, index, onPick, content)
        ImgKind.COLOROS -> ImgColorOs(title, pos, bars, a, items, index, onPick, content)
    }
}

// ---------------------------------------------------------------------------------------------------------------- Windows 11 / 10 (Photos)

@Composable
private fun ImgW11(title: String, pos: String, bars: Boolean, a: ImgActs, items: List<ImgSrc>, index: Int, onPick: (Int) -> Unit, content: @Composable () -> Unit) {
    val t = T
    val r = if (t.id == "win11") 8.dp else 0.dp
    val s = RoundedCornerShape(r)
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(t.bg)) {
        ImgBar(bars, true, false) {
            Row(Modifier.fillMaxWidth().background(t.panel.copy(alpha = .96f)).statusBarsPadding().height(48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ImgIcBtn(a.onBack, 38.dp, shape = s) { ImgIc("back", Icons.Outlined.ArrowBack, t.text, 20.dp) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(title, color = t.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pos, color = t.sub, fontSize = 10.sp, maxLines = 1)
                }
                ImgIcBtn(a.onZoom, 38.dp, shape = s) { ImgIc("zoomIn", Icons.Outlined.ZoomIn, t.text, 20.dp) }
                ImgIcBtn(a.onEdit, 38.dp, shape = s) { ImgIc("edit", Icons.Outlined.Edit, t.text, 20.dp) }
                ImgIcBtn(a.onShare, 38.dp, shape = s) { ImgIc("share", Icons.Outlined.Share, t.text, 20.dp) }
                ImgIcBtn(a.onDelete, 38.dp, shape = s) { ImgIc("delete", Icons.Outlined.Delete, t.text, 20.dp) }
                Box {
                    ImgIcBtn({ more = true }, 38.dp, shape = s) { ImgIc("more", Icons.Outlined.MoreHoriz, t.text, 20.dp) }
                    ImgMoreMenu(more, { more = false }, a)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        ImgBar(bars, false, false) {
            ImgFilmstrip(items, index, onPick, if (r > 0.dp) 4.dp else 0.dp, Modifier.background(t.panel.copy(alpha = .8f)).navigationBarsPadding())
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- Windows 95 / XP / 7 (Picture and Fax Viewer, Photo Viewer)

@Composable
private fun ImgBevel(onClick: () -> Unit, selected: Boolean = false, content: @Composable () -> Unit) {
    val t = T
    val deco = if (t.id == "win95") Modifier.drawBehind {
        val w = size.width; val h = size.height
        drawRect(Color(0xFFC0C0C0))
        val hi = if (selected) Color(0xFF404040) else Color.White
        val lo = if (selected) Color.White else Color(0xFF404040)
        drawLine(hi, Offset(0f, 1f), Offset(w, 1f), 2f); drawLine(hi, Offset(1f, 0f), Offset(1f, h), 2f)
        drawLine(lo, Offset(0f, h - 1f), Offset(w, h - 1f), 3f); drawLine(lo, Offset(w - 1f, 0f), Offset(w - 1f, h), 3f)
    } else Modifier.clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(if (selected) listOf(Color(0xFFD5E3F5), Color(0xFFB9D1EA)) else listOf(Color.White, Color(0xFFE3E8F0)))).border(1.dp, t.line, RoundedCornerShape(3.dp))
    Box(Modifier.size(34.dp).then(deco).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ImgClassic(title: String, pos: String, bars: Boolean, a: ImgActs, items: List<ImgSrc>, index: Int, onPick: (Int) -> Unit, content: @Composable () -> Unit) {
    val t = T
    Column(Modifier.fillMaxSize().background(t.bg)) {
        ImgBar(bars, true, false) {
            Column {
                Row(Modifier.fillMaxWidth().then(t.title?.let { Modifier.background(it) } ?: Modifier.background(t.panel)).statusBarsPadding().height(30.dp).padding(start = 8.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = t.titleText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(if (t.id == "win95") 0.dp else 3.dp)).background(if (t.id == "win95") Color(0xFFC0C0C0) else Color(0xFFE0523E)).clickable(onClick = a.onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(14.dp), if (t.id == "win95") Color.Black else Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    a.onGrid?.let { g -> ImgBevel(g) { ImgIc("gridView", Icons.Outlined.GridView, t.text, 20.dp) } }
                    ImgBevel(a.onZoom) { ImgIc("zoomIn", Icons.Outlined.ZoomIn, t.text, 20.dp) }
                    ImgBevel(a.onSlide, a.slideOn) { ImgIc(if (a.slideOn) "pause" else "play", if (a.slideOn) Icons.Outlined.Pause else Icons.Outlined.Slideshow, t.text, 20.dp) }
                    Spacer(Modifier.width(6.dp))
                    ImgBevel(a.onEdit) { ImgIc("edit", Icons.Outlined.Edit, t.text, 20.dp) }
                    ImgBevel(a.onShare) { ImgIc("share", Icons.Outlined.Share, t.text, 20.dp) }
                    ImgBevel(a.onInfo) { ImgIc("info", Icons.Outlined.Info, t.text, 20.dp) }
                    ImgBevel(a.onSetAs) { Icon(Icons.Outlined.Wallpaper, null, Modifier.size(20.dp), t.text) }
                    Spacer(Modifier.width(6.dp))
                    ImgBevel(a.onDelete) { ImgIc("delete", Icons.Outlined.Delete, t.text, 20.dp) }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (bars) 4.dp else 0.dp).background(Color.Black).then(if (bars) Modifier.border(2.dp, t.line) else Modifier)) { content() }
        ImgBar(bars, false, false) {
            Column(Modifier.navigationBarsPadding()) {
                ImgFilmstrip(items, index, onPick, 0.dp)
                Text(pos, color = t.text, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- Material You / AMOLED (Samsung Gallery layout)

@Composable
private fun ImgMaterial(title: String, pos: String, bars: Boolean, a: ImgActs, items: List<ImgSrc>, index: Int, onPick: (Int) -> Unit, content: @Composable () -> Unit) {
    val t = T
    var more by remember { mutableStateOf(false) }
    val glass = Color(0xFF1C1C1E).copy(alpha = .78f)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        content()
        Box(Modifier.align(Alignment.TopCenter)) {
            ImgBar(bars, true, true) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ImgIcBtn(a.onBack, 52.dp, glass) { ImgIc("back", Icons.Outlined.ArrowBack, Color.White, 24.dp) }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(glass).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        a.onLive?.let { l -> ImgIcBtn(l, 44.dp) { Icon(Icons.Outlined.SlowMotionVideo, null, Modifier.size(22.dp), Color.White) } }
                        Box {
                            ImgIcBtn({ more = true }, 44.dp) { ImgIc("more", Icons.Outlined.MoreVert, Color.White, 22.dp) }
                            ImgMoreMenu(more, { more = false }, a)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            ImgBar(bars, false, true) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ImgFilmstrip(items, index, onPick, 6.dp)
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(glass).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ImgIcBtn(a.onFav, 48.dp) { Icon(if (a.fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, Modifier.size(24.dp), if (a.fav) Color(0xFFFF5A6E) else Color.White) }
                        ImgIcBtn(a.onEdit, 48.dp) { ImgIc("edit", Icons.Outlined.Edit, Color.White) }
                        ImgIcBtn(a.onShare, 48.dp) { ImgIc("share", Icons.Outlined.Share, Color.White) }
                        ImgIcBtn(a.onDelete, 48.dp) { ImgIc("delete", Icons.Outlined.Delete, Color.White) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- One UI (Samsung Gallery)

@Composable
private fun ImgOneUi(title: String, pos: String, bars: Boolean, a: ImgActs, items: List<ImgSrc>, index: Int, onPick: (Int) -> Unit, content: @Composable () -> Unit) {
    val t = T
    var more by remember { mutableStateOf(false) }
    val soft = t.text.copy(alpha = .07f)
    Column(Modifier.fillMaxSize().background(t.bg)) {
        ImgBar(bars, true, false) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ImgIcBtn(a.onBack, 40.dp) { ImgIc("back", Icons.Outlined.ArrowBack, t.text) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(pos, color = t.sub, fontSize = 11.sp)
                }
                Row(Modifier.clip(CircleShape).background(soft)) {
                    a.onGrid?.let { g -> ImgIcBtn(g, 38.dp) { ImgIc("gridView", Icons.Outlined.GridView, t.text, 20.dp) } }
                    ImgIcBtn(a.onInfo, 38.dp) { ImgIc("info", Icons.Outlined.Info, t.text, 20.dp) }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        ImgBar(bars, false, false) {
            Column(Modifier.navigationBarsPadding()) {
                ImgFilmstrip(items, index, onPick, 10.dp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    @Composable fun pill(onClick: () -> Unit, c: @Composable () -> Unit) {
                        Box(Modifier.clip(CircleShape).background(soft).clickable(onClick = onClick).padding(horizontal = 26.dp, vertical = 11.dp), contentAlignment = Alignment.Center) { c() }
                    }
                    pill(a.onEdit) { ImgIc("edit", Icons.Outlined.Edit, t.text) }
                    pill(a.onShare) { ImgIc("share", Icons.Outlined.Share, t.text) }
                    pill(a.onDelete) { ImgIc("delete", Icons.Outlined.Delete, t.text) }
                    Box {
                        pill({ more = true }) { ImgIc("more", Icons.Outlined.MoreVert, t.text) }
                        ImgMoreMenu(more, { more = false }, a)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- ColorOS (OPPO / OnePlus Photos)

@Composable
private fun ImgColorOs(title: String, pos: String, bars: Boolean, a: ImgActs, items: List<ImgSrc>, index: Int, onPick: (Int) -> Unit, content: @Composable () -> Unit) {
    val t = T
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(t.bg)) {
        ImgBar(bars, true, false) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ImgIcBtn(a.onBack, 40.dp, t.panel) { ImgIc("back", Icons.Outlined.ArrowBack, t.text) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, color = t.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pos, color = t.sub, fontSize = 11.sp)
                }
                a.onGrid?.let { g -> ImgIcBtn(g, 40.dp, t.panel) { ImgIc("gridView", Icons.Outlined.GridView, t.text, 20.dp) } }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (bars) 8.dp else 0.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (bars) 18.dp else 0.dp))) { content() }
        }
        ImgBar(bars, false, false) {
            Column(Modifier.navigationBarsPadding()) {
                ImgFilmstrip(items, index, onPick, 10.dp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(RoundedCornerShape(24.dp)).background(t.panel).border(1.dp, t.line, RoundedCornerShape(24.dp)).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ImgIcBtn(a.onShare, 48.dp) { ImgIc("share", Icons.Outlined.Share, t.text) }
                    ImgIcBtn(a.onEdit, 48.dp, Look.accent.copy(alpha = .16f)) { ImgIc("edit", Icons.Outlined.Edit, Look.accent) }
                    ImgIcBtn(a.onDelete, 48.dp) { ImgIc("delete", Icons.Outlined.Delete, t.text) }
                    Box {
                        ImgIcBtn({ more = true }, 48.dp) { ImgIc("more", Icons.Outlined.MoreVert, t.text) }
                        ImgMoreMenu(more, { more = false }, a)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- details

/** The details of a picture: a Windows dialog in the Windows looks, a rounded bottom card in the others. */
@Composable
internal fun ImgInfoSheet(rows: List<Pair<String, String>>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val t = T
    val k = imgKind()
    val body: @Composable ColumnScope.() -> Unit = {
        rows.forEach { (l, v) ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(l, color = t.sub, fontSize = 11.sp)
                Text(v, color = t.text, fontSize = 14.sp)
            }
        }
    }
    if (k == ImgKind.W11 || k == ImgKind.CLASSIC) {
        WinAlert(onDismissRequest = onClose, title = { Text(tr("Details", "Ayrıntılar")) }, text = { Column(content = body) },
            confirmButton = { TextButton(onClose) { Text(tr("OK", "Tamam")) } })
        return
    }
    val shape = if (k == ImgKind.MATERIAL) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(28.dp)
    Column(modifier.fillMaxWidth().then(if (k == ImgKind.MATERIAL) Modifier else Modifier.padding(12.dp)).navigationBarsPadding()
        .clip(shape).background(t.panel).clickable(onClick = {}).padding(horizontal = 22.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { if (k == ImgKind.MATERIAL) Box(Modifier.align(Alignment.Center).width(32.dp).height(4.dp).clip(CircleShape).background(t.line)) }
            ImgIcBtn(onClose, 32.dp) { ImgIc("close", Icons.Outlined.Close, t.sub, 18.dp) }
        }
        body()
    }
}

// ---------------------------------------------------------------------------------------------------------------- editor chrome

/** Top row of the editor: close, undo, reset on the left, save on the right. */
@Composable
internal fun ImgEditTop(onClose: () -> Unit, canUndo: Boolean, onUndo: () -> Unit, onReset: () -> Unit, onSave: () -> Unit) {
    val t = T
    val k = imgKind()
    val saveShape = if (k == ImgKind.CLASSIC) RoundedCornerShape(3.dp) else if (k == ImgKind.W11) RoundedCornerShape(if (t.id == "win11") 6.dp else 0.dp) else CircleShape
    val acc = Look.accent.copy(alpha = 1f)
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ImgIcBtn(onClose) { ImgIc("close", Icons.Outlined.Close, t.text) }
        ImgIcBtn(onUndo) { ImgIc("undo", Icons.Outlined.Undo, if (canUndo) t.text else t.sub.copy(alpha = .4f)) }
        ImgIcBtn(onReset) { Icon(Icons.Outlined.Restore, null, Modifier.size(22.dp), t.text) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.clip(saveShape).background(acc).clickable(onClick = onSave).padding(horizontal = 22.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, null, Modifier.size(22.dp), onAccent(acc))
        }
    }
}

/** The bottom tool strip: Transform, Tone, Effects, Draw (labelled in One UI, like Samsung Gallery). */
@Composable
internal fun ImgToolStrip(sel: Int, onSel: (Int) -> Unit) {
    val t = T
    val k = imgKind()
    val tabs = listOf(Icons.Outlined.Crop to tr("Transform", "Dönüştür"), Icons.Outlined.Tune to tr("Tone", "Ton"), Icons.Outlined.AutoFixHigh to tr("Effects", "Efektler"), Icons.Outlined.Brush to tr("Draw", "Çiz"))
    Row(Modifier.fillMaxWidth().background(if (k == ImgKind.W11 || k == ImgKind.CLASSIC) t.bg else t.panel)) {
        tabs.forEachIndexed { i, (vec, label) ->
            val tint = if (i == sel) Look.accent.copy(alpha = 1f) else t.text
            Column(Modifier.weight(1f).clickable { onSel(i) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(vec, null, Modifier.size(24.dp), tint)
                if (k == ImgKind.ONEUI) Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
                else Box(Modifier.padding(top = 4.dp).width(18.dp).height(2.dp).background(if (i == sel) tint else Color.Transparent))
            }
        }
    }
}
