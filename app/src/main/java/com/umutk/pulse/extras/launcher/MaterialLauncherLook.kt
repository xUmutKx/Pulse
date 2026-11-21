package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** Default look. [black] = the AMOLED variant: pure #000000 background, light glyphs, grey dock. Otherwise Material surfaces from T. */
class MaterialLauncherLook(
    override val id: String,
    override val nameEn: String,
    override val nameTr: String,
    private val black: Boolean,
) : LauncherLook {
    override val taskbarHeight: Dp = 84.dp
    override val taskbarTop: Boolean = false
    // the drawer draws its own search pill, so the launcher does not put one above it
    override val ownSearch: Boolean = true

    private fun bg(): Color = if (black) Color.Black else T.bg
    private fun fg(): Color = if (black) Color.White else T.text
    private fun sub(): Color = if (black) Color(0xFFC4C4C4) else T.sub
    /** surfaceContainer: cards and icon tiles. */
    private fun pill(): Color = if (black) Color(0xFF1C1C1C) else T.panel
    /** surfaceContainerHigh: the dock and the search field. */
    private fun high(): Color = if (black) Color(0xFF2B2B2B) else lerp(T.panel, T.text, .06f)

    /** The accent: the phone's own colour when that is switched on (Look > Use the device's colours), else the look's accent. */
    @Composable
    private fun acc(): Color {
        val c = androidx.compose.ui.platform.LocalContext.current
        val dark = black || T.dark
        return remember(c, DeviceAccent.rev, dark) { DeviceAccent.color(c, dark) } ?: Look.accent
    }

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        // a picture of the person's own (Look > Custom wallpaper) is drawn; with none, the phone's own wallpaper shows through (the window is see-through) instead of a flat black
        val own = LauncherPrefs.wallFile.isNotEmpty() && java.io.File(LauncherPrefs.wallFile).isFile
        if (own) { LauncherWallpaper(id, bg(), modifier) { content() }; return }
        // the AMOLED look stays pure black
        if (black) { Box(modifier.fillMaxSize().background(bg())) { content() }; return }
        val act = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        androidx.compose.runtime.DisposableEffect(act) {
            act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            act?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            onDispose {
                act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                act?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
            }
        }
        // a light veil keeps the labels readable on a bright picture
        Box(modifier.fillMaxSize().background(bg().copy(alpha = .22f))) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val fg = fg()
        val edge = LocalLauncherEdge.current
        val shape = RoundedCornerShape(28.dp)
        // a floating dock: a rounded tonal surface over the page, inset from the rounded screen corners
        Row(modifier.padding(horizontal = maxOf(12.dp, edge), vertical = 12.dp).shadow(if (black) 0.dp else 4.dp, shape).clip(shape)
            .background(if (LauncherPrefs.dockGlass) androidx.compose.ui.graphics.Color.White.copy(alpha = .14f * LauncherPrefs.dockAlpha / 100f + .06f) else high().copy(alpha = LauncherPrefs.dockAlpha / 100f))
            .then(if (LauncherPrefs.dockGlass) Modifier.border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .28f), shape) else Modifier)
            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(acc().copy(alpha = .16f)).clickable { LauncherSession.startOpen = true },
                contentAlignment = Alignment.Center) {
                PIcon("apps", acc(), 24.dp)
            }
            state.dock.forEach { app ->
                Box(Modifier.weight(1f).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                    LaunchAppPicture(app, 46.dp, pill(), fg)
                }
            }
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val fg = fg()
        val edge = LocalLauncherEdge.current
        var query by remember { mutableStateOf("") }
        // the category bar (a tap keeps one group of apps in view)
        var catSel by remember { mutableStateOf<String?>(null) }
        val matches = state.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        // nothing typed: the apps in their store groups, one tonal card per group; typing: one flat list of matches
        val sections = if (query.isBlank()) groupedApps(state.apps).map { (g, apps) -> tr(g.en, g.tr) to apps }.filter { catSel == null || it.first == catSel }
            else listOf(tr("Results", "Sonuçlar") to matches)
        Column(modifier.background(bg()).statusBarsPadding().navigationBarsPadding().padding(horizontal = maxOf(16.dp, edge))) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).height(56.dp).clip(CircleShape).background(high()).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PIcon("search", sub(), 24.dp)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 16.sp),
                    cursorBrush = SolidColor(acc()),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text(tr("Search apps", "Uygulama ara"), color = sub(), fontSize = 16.sp)
                        inner()
                    },
                )
                if (query.isNotEmpty()) Box(Modifier.size(40.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                    PIcon("close", sub(), 22.dp)
                }
            }
            if (query.isBlank() && LauncherPrefs.drawerAz) {
                // one A-Z list, the letter strip at the right edge jumps to a letter
                val az = remember(state.apps) { lettered(state.apps) }
                val rows = remember(az, state.columns) { az.map { (l, apps) -> l to apps.chunked(state.columns) } }
                val starts = remember(rows) { var n = 0; rows.associate { (l, r) -> val at = n; n += 1 + r.size; l to at } }
                val lst = androidx.compose.foundation.lazy.rememberLazyListState()
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = lst) {
                        rows.forEach { (l, rs) ->
                            item(key = "h$l") { Text(l, color = acc(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp)) }
                            items(rs.size, key = { "r$l$it" }) { i ->
                                Row(Modifier.fillMaxWidth()) {
                                    rs[i].forEach { app -> Box(Modifier.weight(1f).appTap(state, app) { state.open(app); onClose() }) { IconCell(app, state.iconDp, Modifier.fillMaxWidth()) } }
                                    repeat(state.columns - rs[i].size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    LetterBar(az.map { it.first }, sub(), high(), { l -> starts[l]?.let { scope.launch { lst.scrollToItem(it) } } })
                }
                return@Column
            }
            if (query.isBlank()) {
                val names = remember(state.apps) { groupedApps(state.apps).map { (g, _) -> tr(g.en, g.tr) } }
                if (names.size > 1) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<String?>(null) + names).forEach { n ->
                        val on = catSel == n
                        Text(n ?: tr("All", "Tümü"), color = if (on) bg() else fg, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) acc() else high()).clickable { catSel = n }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                // the people of the contacts that match, with a call button (a setting, off by default)
                if (query.isNotBlank()) item(key = "contacts") { ContactResults(query, fg, sub(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                if (query.isNotBlank()) item(key = "events") { CalendarResults(query, fg, sub(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                items(sections, key = { it.first }) { (label, apps) ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(28.dp)).background(pill()).padding(horizontal = 8.dp, vertical = 12.dp)) {
                        Text(label, color = sub(), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                        apps.chunked(state.columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { app ->
                                    Box(Modifier.weight(1f).appTap(state, app) { state.open(app); onClose() }) { IconCell(app, state.iconDp, Modifier.fillMaxWidth()) }
                                }
                                repeat(state.columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        val fg = fg()
        Column(modifier.padding(vertical = 6.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, pill(), fg)
            Text(app.label, color = fg, fontSize = 12.sp, maxLines = if (LauncherPrefs.twoLineLabels) 2 else 1, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
