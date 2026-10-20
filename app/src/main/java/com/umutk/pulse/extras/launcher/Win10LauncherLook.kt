package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Windows 10 look. Opaque: solid taskbar and panels (#1F1F1F dark / #F0F0F0 light), flat tiles, no gradients. Dark follows T.dark. */
class Win10LauncherLook : LauncherLook {
    override val id = "win10"
    override val nameEn = "Windows 10"
    override val nameTr = "Windows 10"
    override val taskbarHeight: Dp = 45.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    private fun desk(): Color = if (T.dark) Color.Black else Color(0xFF0078D7)
    private fun bar(): Color = if (T.dark) Color(0xFF1F1F1F) else Color(0xFFF0F0F0)
    private fun panel(): Color = if (T.dark) Color(0xFF1F1F1F) else Color(0xFFE6E6E9)
    private fun fg(): Color = if (T.dark) Color.White else Color.Black

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        LauncherWallpaper(id, desk(), modifier) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val fg = fg()
        val edge = LocalLauncherEdge.current
        val now by produceState(System.currentTimeMillis()) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000 - value % 60_000)
            }
        }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        val date = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(now))
        // the edge padding keeps the start button and the clock out of the rounded screen corners
        Row(modifier.background(bar()).padding(horizontal = edge), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(20.dp)) {
                    // four panes drawn as one Path; plain squares, no Microsoft artwork
                    val g = size.width * .1f
                    val w = (size.width - g) / 2f
                    val p = Path()
                    for (x in listOf(0f, w + g)) for (y in listOf(0f, w + g)) p.addRect(Rect(x, y, x + w, y + w))
                    drawPath(p, fg)
                }
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                state.dock.forEach { app ->
                    Box(Modifier.size(45.dp).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 35.dp, Color(0xFF0078D7), Color.White)
                    }
                }
            }
            Column(Modifier.padding(end = 10.dp), horizontalAlignment = Alignment.End) {
                Text("$time\n$date", color = fg, fontSize = 13.sp, lineHeight = 15.sp, textAlign = TextAlign.End)
            }
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val fg = fg()
        val edge = LocalLauncherEdge.current
        var query by remember { mutableStateOf("") }
        val (pinned, all) = startLists(state, query)
        val noRipple = remember { MutableInteractionSource() }
        // full-screen box closes the menu on a tap outside; the panel swallows its own taps
        Box(modifier.clickable(interactionSource = noRipple, indication = null, onClick = onClose)) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = edge, bottom = taskbarHeight)
                    .fillMaxWidth(.92f).widthIn(max = 420.dp).fillMaxHeight(.8f)
                    .background(panel()).border(0.5.dp, if (T.dark) Color(0xFF212324) else Color(0xFFAAAAAA))
                    .clickable(interactionSource = noRipple, indication = null) {}
                    .padding(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(36.dp)
                        .background(if (T.dark) Color.Black else Color.White)
                        .border(0.5.dp, fg, RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        drawCircle(fg, radius = size.width * .35f, center = Offset(size.width * .42f, size.height * .42f), style = Stroke(size.width * .11f))
                        drawLine(fg, Offset(size.width * .66f, size.height * .66f), Offset(size.width * .92f, size.height * .92f), strokeWidth = size.width * .11f, cap = StrokeCap.Round)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(tr("Search", "Ara"), color = fg.copy(alpha = .6f), fontSize = 14.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = fg, fontSize = 14.sp), cursorBrush = SolidColor(fg),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    if (pinned.isNotEmpty()) {
                        item { WinHeading(tr("Pinned", "Sabitlenenler"), fg) }
                        items(pinned.chunked(3)) { row -> TileRow(row, state, onClose) }
                    }
                    item { WinHeading(tr("All apps", "Tüm uygulamalar"), fg) }
                    items(all.chunked(3)) { row -> TileRow(row, state, onClose) }
                }
            }
        }
    }

    /** One row of three flat tiles; the colour follows the package, so an app keeps its tile in every list. */
    @Composable
    private fun TileRow(row: List<LaunchApp>, state: LauncherState, onClose: () -> Unit) {
        Row(Modifier.fillMaxWidth()) {
            row.forEach { app ->
                val c = WIN10_TILES[(app.pkg.hashCode() and 0x7fffffff) % WIN10_TILES.size]
                Box(Modifier.weight(1f).padding(2.dp).height(80.dp).background(c).appTap(state, app) { state.open(app); onClose() }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LaunchAppPicture(app, 35.dp, c, textOn(c)) }
                    Text(
                        app.label, color = textOn(c), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        // desktop labels are fixed white with a black shadow, on any background
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, Color(0xFF0078D7), Color.White)
            Text(
                app.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, shadow = Shadow(Color.Black, Offset(1f, 1f), 2f)),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    private fun textOn(c: Color): Color = if (c.luminance() > .5f) Color.Black else Color.White
}

/** Section title inside a Windows start menu list. */
@Composable
internal fun WinHeading(text: String, color: Color) {
    Text(text, color = color.copy(alpha = .8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
}

/** Tile palette from the Win10 resources: item 1 teal, items 2-8 screen_2..8. */
private val WIN10_TILES = listOf(
    Color(0xFF359F07), Color(0xFF899937), Color(0xFFF6FC07), Color(0xFFFCC419),
    Color(0xFFFA5252), Color(0xFFB7DBF3), Color(0xFF008BFF), Color(0xFFCC5DE8),
)
