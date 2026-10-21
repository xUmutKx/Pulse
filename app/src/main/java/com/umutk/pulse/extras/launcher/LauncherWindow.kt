package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.bevel
import com.umutk.pulse.tr

/** How one Windows look paints the apps window: the title bar, the frame and the panes. [kind] picks the caption buttons: "7", "xp" or "95". */
class WinSkin(
    val kind: String, val title: Brush, val titleInk: Color, val frame: Color, val frameWidth: Dp, val corner: Dp,
    val body: Color, val nav: Color, val address: Color, val ink: Color, val sub: Color, val sel: Color, val selInk: Color, val selEdge: Color,
)

/**
 * The apps of the launcher as a real Explorer window, the way the look draws one: a title bar with caption buttons, an address bar with the
 * path and a search box, a navigation pane with the app groups, the apps as icons, and a status line with the count. Any caption button closes it.
 */
@Composable
fun LauncherWindow(skin: WinSkin, state: LauncherState, onClose: () -> Unit, taskbarHeight: Dp, modifier: Modifier = Modifier) {
    val edge = LocalLauncherEdge.current
    val none = remember { MutableInteractionSource() }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<StartGroup?>(null) }
    val groups = remember(state.apps) { groupedApps(state.apps) }
    val shown = state.apps.filter { (group == null || startGroup(it) == group) && it.label.contains(query.trim(), ignoreCase = true) }
    val shape = RoundedCornerShape(skin.corner)
    // a tap on the desktop around the window closes it; the window swallows its own taps
    Box(modifier.clickable(interactionSource = none, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.Center).statusBarsPadding().padding(start = edge + 6.dp, end = edge + 6.dp, bottom = taskbarHeight + 6.dp, top = 6.dp)
                .fillMaxWidth().fillMaxHeight().clip(shape).background(skin.frame).then(if (skin.kind == "95") Modifier.bevel(true) else Modifier)
                .padding(skin.frameWidth).clickable(interactionSource = none, indication = null) {},
        ) {
            // title bar
            Row(Modifier.fillMaxWidth().height(if (skin.kind == "95") 20.dp else 30.dp).background(skin.title).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(16.dp)) { // a little folder
                    drawRect(Color(0xFFE8B84A), Offset(0f, size.height * .2f), androidx.compose.ui.geometry.Size(size.width, size.height * .7f))
                    drawRect(Color(0xFFFBDF7C), Offset(0f, size.height * .4f), androidx.compose.ui.geometry.Size(size.width, size.height * .5f))
                }
                Spacer(Modifier.width(6.dp))
                Text(tr("Programs", "Programlar") + (group?.let { " › " + tr(it.en, it.tr) } ?: ""), color = skin.titleInk, fontSize = 13.sp,
                    fontWeight = if (skin.kind == "7") FontWeight.Normal else FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                Caption(skin, 0, onClose); Caption(skin, 1, onClose); Caption(skin, 2, onClose)
            }
            // address bar: the path and the search
            Row(Modifier.fillMaxWidth().background(skin.nav).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(24.dp).background(skin.address).border(1.dp, skin.selEdge).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    Text(tr("Computer", "Bilgisayar") + " › " + tr("Programs", "Programlar") + (group?.let { " › " + tr(it.en, it.tr) } ?: ""), color = skin.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(120.dp).height(24.dp).background(skin.address).border(1.dp, skin.selEdge).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = skin.ink, fontSize = 12.sp), cursorBrush = SolidColor(skin.ink), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> if (query.isEmpty()) Text(tr("Search", "Ara"), color = skin.sub, fontSize = 12.sp); inner() })
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // navigation pane
                LazyColumn(Modifier.width(118.dp).fillMaxHeight().background(skin.nav).padding(vertical = 4.dp)) {
                    item { NavRow(skin, tr("All programs", "Tüm programlar"), state.apps.size, group == null) { group = null } }
                    items(groups) { (g, apps) -> NavRow(skin, tr(g.en, g.tr), apps.size, group == g) { group = g } }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(skin.selEdge))
                // the apps
                LazyVerticalGrid(GridCells.Adaptive(82.dp), Modifier.weight(1f).fillMaxHeight().background(skin.body).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.pkg + it.label }) { app ->
                        Column(Modifier.fillMaxWidth().padding(2.dp).appTap(state, app) { state.open(app); onClose() }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            LaunchAppPicture(app, state.iconDp.dp, skin.nav, skin.ink)
                            Text(app.label, color = skin.ink, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
            // status line
            Row(Modifier.fillMaxWidth().background(skin.nav).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${shown.size} " + tr("items", "öğe"), color = skin.ink, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NavRow(skin: WinSkin, label: String, n: Int, on: Boolean, click: () -> Unit) {
    val shape = RoundedCornerShape(if (skin.kind == "7") 3.dp else 0.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp).clip(shape).then(if (on) Modifier.background(skin.sel).border(1.dp, skin.selEdge, shape) else Modifier)
        .clickable(onClick = click).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (on) skin.selInk else skin.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$n", color = if (on) skin.selInk else skin.sub, fontSize = 11.sp)
    }
}

/** Caption button: 0 minimise, 1 maximise, 2 close, drawn as the look draws them. */
@Composable
private fun Caption(skin: WinSkin, i: Int, onClick: () -> Unit) {
    val xp = skin.kind == "xp"; val w95 = skin.kind == "95"
    val w = if (w95) 16.dp else if (xp) 22.dp else if (i == 2) 42.dp else 26.dp
    val h = if (w95) 14.dp else if (xp) 22.dp else 20.dp
    val shape = RoundedCornerShape(if (w95) 0.dp else if (xp) 3.dp else 3.dp)
    val fill: Brush = when {
        w95 -> SolidColor(Color(0xFFC0C0C0))
        xp -> if (i == 2) Brush.verticalGradient(listOf(Color(0xFFE08A70), Color(0xFFC1432A))) else Brush.verticalGradient(listOf(Color(0xFF5B8DF0), Color(0xFF2A58C8)))
        else -> if (i == 2) Brush.verticalGradient(listOf(Color(0xFFE8A99A), Color(0xFFC4412B), Color(0xFFA32A15))) else Brush.verticalGradient(listOf(Color(0xFFE6EEF8), Color(0xFFB4C8E0)))
    }
    val ink = if (w95) Color.Black else if (xp || i == 2) Color.White else Color(0xFF33445C)
    Box(Modifier.padding(end = if (w95) 2.dp else 3.dp).size(w, h).clip(shape).background(fill)
        .then(if (w95) Modifier.bevel(true) else Modifier.border(1.dp, if (xp) Color.White else Color(0xFF6C86A8), shape)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (w95) 8.dp else 9.dp)) {
            val s = size.width; val t = s * .16f
            when (i) {
                0 -> drawLine(ink, Offset(0f, s * .85f), Offset(s * .7f, s * .85f), t * 1.6f)
                1 -> drawRect(ink, Offset(0f, 0f), androidx.compose.ui.geometry.Size(s, s), style = androidx.compose.ui.graphics.drawscope.Stroke(t))
                else -> { drawLine(ink, Offset(0f, 0f), Offset(s, s), t * 1.3f); drawLine(ink, Offset(s, 0f), Offset(0f, s), t * 1.3f) }
            }
        }
    }
}
