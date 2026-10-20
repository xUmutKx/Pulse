package com.umutk.pulse.extras.launcher

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Windows 10 look, opaque and flat: solid taskbar and start menu, square tiles, no gradients, no blur.
 * Two variants: "win10" (light, or dark with the app theme; blue desktop) and the black variant, a second
 * instance with its own id, for the registry: Win10LauncherLook("win10black", "Windows 10 Black", "Windows 10 Siyah", black = true).
 * The black variant has no stock wallpaper, so its desktop is plain black.
 */
class Win10LauncherLook(
    override val id: String = "win10",
    override val nameEn: String = "Windows 10",
    override val nameTr: String = "Windows 10",
    private val black: Boolean = false,
) : LauncherLook {
    override val taskbarHeight: Dp = 40.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    private fun dark(): Boolean = black || T.dark
    private fun desk(): Color = if (dark()) Color.Black else Color(0xFF0078D7)
    private fun bar(): Color = if (black) Color(0xFF0A0A0A) else if (T.dark) Color(0xFF1F1F1F) else Color(0xFFF0F0F0)
    private fun panel(): Color = if (black) Color(0xFF141414) else if (T.dark) Color(0xFF1F1F1F) else Color(0xFFF2F2F2)
    private fun field(): Color = if (black) Color(0xFF262626) else if (T.dark) Color(0xFF2B2B2B) else Color.White
    private fun hover(): Color = if (dark()) Color(0xFF3A3A3A) else Color(0xFFD5D5D5)
    private fun border(): Color = if (dark()) Color(0xFF2B2B2B) else Color(0xFFCCCCCC)
    private fun fg(): Color = if (dark()) Color.White else Color.Black

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
            BarButton(46.dp, hover(), onClick = { LauncherSession.startOpen = true }) {
                Canvas(Modifier.size(16.dp)) { startLogo(fg) }
            }
            BarButton(46.dp, hover(), onClick = { LauncherSession.startOpen = true }) {
                Canvas(Modifier.size(16.dp)) { searchIcon(fg) }
            }
            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                state.dock.forEach { app ->
                    BarButton(46.dp, hover(), onClick = { state.open(app) }) {
                        LaunchAppPicture(app, 24.dp, Color(0xFF0078D7), Color.White)
                    }
                }
            }
            Box(Modifier.width(46.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { Canvas(Modifier.size(16.dp)) { wifiIcon(fg) } }
            Box(Modifier.width(46.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { Canvas(Modifier.size(16.dp)) { speakerIcon(fg) } }
            Box(Modifier.fillMaxHeight().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text("$time\n$date", color = fg, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.End)
            }
            // show-desktop strip: a thin divider at the far end, as on Windows
            Box(Modifier.fillMaxHeight().width(6.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxHeight().width(1.dp).background(fg.copy(alpha = .3f)))
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
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = edge, bottom = taskbarHeight)
                    .fillMaxWidth(.92f).widthIn(max = 420.dp).fillMaxHeight(.8f)
                    .background(panel()).border(0.5.dp, border())
                    .clickable(interactionSource = noRipple, indication = null) {}
            ) {
                Rail(fg, panel(), onClose, Modifier.width(48.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        // no search: the pinned tiles, then the apps in their tile groups; searching gives one flat list of rows
                        if (query.isBlank()) {
                            if (pinned.isNotEmpty()) items(pinned.chunked(3)) { row -> TileRow(row, state, onClose) }
                            groupedApps(all).forEach { (g, apps) ->
                                item { WinHeading(tr(g.en, g.tr), fg) }
                                items(apps.chunked(3)) { row -> TileRow(row, state, onClose) }
                            }
                        } else items(all, key = { it.pkg }) { app -> ResultRow(app, state, fg, onClose) }
                    }
                    SearchField(query) { query = it }
                }
            }
        }
    }

    /** One row of three square tiles; the colour follows the package, so an app keeps its tile in every list. */
    @Composable
    private fun TileRow(row: List<LaunchApp>, state: LauncherState, onClose: () -> Unit) {
        Row(Modifier.fillMaxWidth()) {
            row.forEach { app ->
                val c = WIN10_ACCENTS[(app.pkg.hashCode() and 0x7fffffff) % WIN10_ACCENTS.size]
                Box(Modifier.weight(1f).padding(2.dp).height(88.dp).background(c).appTap(state, app) { state.open(app); onClose() }) {
                    LaunchAppPicture(app, 32.dp, c, textOn(c), Modifier.align(Alignment.TopStart).padding(10.dp))
                    Text(
                        app.label, color = textOn(c), fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    /** A search result: icon and name in one row, as the Windows search list. */
    @Composable
    private fun ResultRow(app: LaunchApp, state: LauncherState, fg: Color, onClose: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).appTap(state, app) { state.open(app); onClose() }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LaunchAppPicture(app, 24.dp, Color(0xFF0078D7), Color.White)
            Spacer(Modifier.width(12.dp))
            Text(app.label, color = fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    /** The search box at the foot of the menu, where the Windows search sits next to the start button. */
    @Composable
    private fun SearchField(query: String, onQuery: (String) -> Unit) {
        val fg = fg()
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp).background(field()).border(0.5.dp, border()).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(16.dp)) { searchIcon(fg) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(tr("Search", "Ara"), color = fg.copy(alpha = .6f), fontSize = 13.sp)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 13.sp), cursorBrush = SolidColor(fg),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    /** The narrow left column of the menu: the menu icon and the settings shortcut at the foot. */
    @Composable
    private fun Rail(fg: Color, bg: Color, onClose: () -> Unit, modifier: Modifier) {
        val ctx = LocalContext.current
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.size(16.dp)) { hamburgerIcon(fg) } }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clickable { ctx.startActivity(Intent(AndroidSettings.ACTION_SETTINGS)); onClose() },
                contentAlignment = Alignment.Center
            ) { Canvas(Modifier.size(16.dp)) { gearIcon(fg, bg) } }
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        // desktop labels are fixed white with a black shadow, on any background
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, Color(0xFF0078D7), Color.White)
            Text(
                app.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, shadow = Shadow(Color.Black, Offset(1f, 1f), 2f)),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    private fun textOn(c: Color): Color = if (c.luminance() > .5f) Color.Black else Color.White
}

/** Taskbar button: a flat 46dp cell that shows the hover colour while pressed. */
@Composable
private fun BarButton(width: Dp, hover: Color, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.width(width).fillMaxHeight().background(if (pressed) hover else Color.Transparent)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center, content = content
    )
}

/** Section title inside a Windows start menu list. */
@Composable
internal fun WinHeading(text: String, color: Color) {
    Text(text, color = color.copy(alpha = .8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
}

// icons, drawn as paths on a square canvas; sizes are fractions of the canvas width

/** Four panes, the Windows start mark. */
private fun DrawScope.startLogo(c: Color) {
    val g = size.width * .08f
    val w = (size.width - g) / 2f
    val p = Path()
    for (x in listOf(0f, w + g)) for (y in listOf(0f, w + g)) p.addRect(Rect(x, y, x + w, y + w))
    drawPath(p, c)
}

private fun DrawScope.searchIcon(c: Color) {
    val s = size.width
    val sw = s * .09f
    drawCircle(c, radius = s * .32f, center = Offset(s * .42f, s * .42f), style = Stroke(sw))
    drawLine(c, Offset(s * .66f, s * .66f), Offset(s * .9f, s * .9f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
}

private fun DrawScope.hamburgerIcon(c: Color) {
    val s = size.width
    for (y in listOf(.2f, .5f, .8f)) drawLine(c, Offset(0f, s * y), Offset(s, s * y), strokeWidth = s * .09f)
}

/** Wi-Fi: three arcs fanning up from a dot. */
private fun DrawScope.wifiIcon(c: Color) {
    val s = size.width
    val sw = s * .09f
    val cx = s * .5f
    val cy = s * .9f
    for (r in listOf(.42f, .66f, .9f)) {
        val rr = s * r
        drawArc(c, 225f, 90f, false, topLeft = Offset(cx - rr, cy - rr), size = Size(2 * rr, 2 * rr), style = Stroke(sw, cap = StrokeCap.Round))
    }
    drawCircle(c, radius = sw * .9f, center = Offset(cx, cy))
}

/** Speaker: a cone with two sound arcs. */
private fun DrawScope.speakerIcon(c: Color) {
    val s = size.width
    val sw = s * .09f
    val body = Path().apply {
        moveTo(s * .08f, s * .36f); lineTo(s * .28f, s * .36f); lineTo(s * .5f, s * .16f)
        lineTo(s * .5f, s * .84f); lineTo(s * .28f, s * .64f); lineTo(s * .08f, s * .64f); close()
    }
    drawPath(body, c)
    for (r in listOf(.26f, .42f)) {
        val rr = s * r
        drawArc(c, -45f, 90f, false, topLeft = Offset(s * .5f - rr, s * .5f - rr), size = Size(2 * rr, 2 * rr), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Gear: eight teeth on a ring, with a hole in the panel colour. */
private fun DrawScope.gearIcon(c: Color, hole: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rO = size.width * .46f
    val rI = size.width * .34f
    val step = 2 * PI / 8
    fun at(r: Float, a: Double) = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    val p = Path()
    var first = true
    for (i in 0 until 8) {
        val a = i * step
        for (pt in listOf(at(rI, a - .3 * step), at(rO, a - .15 * step), at(rO, a + .15 * step), at(rI, a + .3 * step))) {
            if (first) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
            first = false
        }
    }
    p.close()
    drawPath(p, c)
    drawCircle(hole, radius = size.width * .16f, center = Offset(cx, cy))
}

/** Tile palette: the Windows 10 accent colours (Settings > Colours), one per package. */
private val WIN10_ACCENTS = listOf(
    Color(0xFF0078D7), Color(0xFF0063B1), Color(0xFF8764B8), Color(0xFF744DA9),
    Color(0xFFB146C2), Color(0xFF00B7C3), Color(0xFF018574), Color(0xFF10893E),
    Color(0xFF107C10), Color(0xFFFF8C00), Color(0xFFE81123), Color(0xFFBF0077),
)
