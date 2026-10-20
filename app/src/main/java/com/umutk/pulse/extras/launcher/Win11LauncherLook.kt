package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import androidx.compose.ui.graphics.RectangleShape
import com.umutk.pulse.T
import com.umutk.pulse.backdrop
import com.umutk.pulse.layer
import com.umutk.pulse.tr

/** Windows 11 look: centred translucent taskbar, rounded 8 dp start flyout, blue wallpaper gradient. Translucency is allowed for this look only. */
class Win11LauncherLook : LauncherLook {
    override val id = "win11"
    override val nameEn = "Windows 11"
    override val nameTr = "Windows 11"
    override val taskbarHeight: Dp = 48.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    // Follows the Pulse theme (T.bg is light or dark); the wallpaper is fixed.
    private fun dark(): Boolean = T.bg.luminance() < .5f
    private fun taskbarFill(): Color = if (dark()) Color(0xCC1C1C1C) else Color(0xCCF3F3F3)
    private fun flyoutFill(): Color = if (dark()) Color(0xE6202020) else Color(0xE6F3F3F3)
    private fun fg(): Color = if (dark()) Color.White else Color(0xFF1B1B1B)
    private fun tile(): Color = if (dark()) Color(0xFF3A3A3A) else Color(0xFFE0E0E0)
    private fun field(): Color = if (dark()) Color(0x26FFFFFF) else Color(0x14000000)

    // the window material from Settings > Appearance (Mica, Mica Alt, Acrylic, Glass); "none" keeps the flat fills above
    @Composable
    private fun glass(modifier: Modifier, flat: Color, shape: androidx.compose.ui.graphics.Shape? = null): Modifier =
        if (Look.effect == "none") modifier.background(flat, shape ?: RectangleShape)
        else modifier.backdrop(Look.effect).let { if (shape != null) it.layer(Look.effect, shape) else it }

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        val wallpaper = Brush.verticalGradient(listOf(Color(0xFF0B3D91), Color(0xFF1E7BD0)))
        LauncherWallpaper(id, wallpaper, modifier) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val fg = fg()
        val edge = LocalLauncherEdge.current
        // the edge padding keeps the clock out of the rounded screen corners
        Box(glass(modifier.fillMaxWidth().height(taskbarHeight), taskbarFill()).padding(horizontal = edge), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) {
                    StartGlyph(fg)
                }
                state.dock.forEach { app ->
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 28.dp, tile(), fg)
                    }
                }
            }
            TaskbarClock(fg, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val fg = fg()
        val edge = LocalLauncherEdge.current
        var query by remember { mutableStateOf("") }
        val (pinned, all) = startLists(state, query)
        Box(modifier.fillMaxSize().clickable(onClick = onClose)) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = maxOf(8.dp, edge), end = maxOf(8.dp, edge), bottom = taskbarHeight + 8.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .let { glass(it, flyoutFill(), RoundedCornerShape(8.dp)) }
                    .clickable(enabled = false) {}
                    .padding(16.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(4.dp)).background(field()).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = fg, fontSize = 14.sp),
                        cursorBrush = SolidColor(fg),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(tr("Search", "Ara"), color = fg.copy(alpha = .6f), fontSize = 14.sp)
                            inner()
                        },
                    )
                }
                if (pinned.isNotEmpty()) {
                    WinHeading(tr("Pinned", "Sabitlenenler"), fg)
                    pinned.chunked(state.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                IconCell(app, state.iconDp, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).appTap(state, app) { state.open(app); onClose() })
                            }
                            repeat(state.columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                WinHeading(tr("All apps", "Tüm uygulamalar"), fg)
                LazyVerticalGrid(GridCells.Fixed(state.columns), Modifier.fillMaxWidth().weight(1f)) {
                    items(all, key = { it.pkg }) { app ->
                        IconCell(app, state.iconDp, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).appTap(state, app) { state.open(app); onClose() })
                    }
                }
            }
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        val fg = fg()
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, tile(), fg)
            Text(app.label, color = fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Start button: four plain squares in a 2x2 Path (no Microsoft mark). */
@Composable
private fun StartGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val gap = size.minDimension * .08f
        val q = (size.minDimension - gap) / 2f
        val path = Path()
        for (x in listOf(0f, q + gap)) for (y in listOf(0f, q + gap)) path.addRect(Rect(x, y, x + q, y + q))
        drawPath(path, color)
    }
}
