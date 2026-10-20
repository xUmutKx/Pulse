package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier.fillMaxSize().background(bg())) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val fg = fg()
        val edge = LocalLauncherEdge.current
        val shape = RoundedCornerShape(28.dp)
        // a floating dock: a rounded tonal surface over the page, inset from the rounded screen corners
        Row(modifier.padding(horizontal = maxOf(12.dp, edge), vertical = 12.dp).shadow(if (black) 0.dp else 4.dp, shape).clip(shape)
            .background(high()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Look.accent.copy(alpha = .16f)).clickable { LauncherSession.startOpen = true },
                contentAlignment = Alignment.Center) {
                PIcon("apps", Look.accent, 24.dp)
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
        val matches = state.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        // nothing typed: the apps in their store groups, one tonal card per group; typing: one flat list of matches
        val sections = if (query.isBlank()) groupedApps(state.apps).map { (g, apps) -> tr(g.en, g.tr) to apps }
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
                    cursorBrush = SolidColor(Look.accent),
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
            LazyColumn(Modifier.fillMaxSize()) {
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
            Text(app.label, color = fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
