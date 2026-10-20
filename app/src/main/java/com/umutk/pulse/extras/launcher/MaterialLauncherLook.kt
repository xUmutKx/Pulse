package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    private fun bg(): Color = if (black) Color.Black else T.bg
    private fun fg(): Color = if (black) Color.White else T.text
    private fun pill(): Color = if (black) Color(0xFF1C1C1C) else T.panel

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier.fillMaxSize().background(bg())) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val fg = fg()
        val labelColor = if (black) Color.White else T.panel
        val edge = LocalLauncherEdge.current
        // opaque bottom bar: Material surface, pure black on AMOLED; the clock sits at the right end, clear of the rounded corners
        Row(modifier.fillMaxWidth().background(if (black) Color.Black else T.panel).padding(horizontal = maxOf(8.dp, edge), vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) {
                PIcon("apps", fg, 26.dp)
            }
            state.dock.forEach { app ->
                Column(Modifier.weight(1f).appTap(state, app) { state.open(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    LaunchAppPicture(app, 48.dp, T.panel, fg)
                    Text(app.label, color = labelColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TaskbarClock(fg, Modifier.padding(start = 6.dp))
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val fg = fg()
        Column(modifier.background(bg()).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) { PIcon("back", fg, 22.dp) }
                Text(tr("Apps", "Uygulamalar"), color = fg, fontSize = 18.sp)
            }
            LazyVerticalGrid(GridCells.Fixed(state.columns), Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(state.apps, key = { it.pkg }) { app ->
                    IconCell(app, state.iconDp, Modifier.fillMaxWidth().appTap(state, app) { state.open(app); onClose() })
                }
            }
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        val fg = fg()
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, pill(), fg)
            Text(app.label, color = fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
