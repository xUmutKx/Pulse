package com.umutk.pulse.extras.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Accent colours of the Windows Phone start screen; an app keeps its colour in every list. */
private val WP_TILES = listOf(
    Color(0xFF0078D7), Color(0xFF107C10), Color(0xFF5C2D91), Color(0xFFE81123),
    Color(0xFFD83B01), Color(0xFF008272), Color(0xFFB4009E), Color(0xFF00A300),
)

private fun wpTile(app: LaunchApp): Color = WP_TILES[(app.pkg.hashCode() and 0x7fffffff) % WP_TILES.size]

/** The Windows logo mark: four squares in a 2 by 2 grid. */
@Composable
private fun StartGlyph(color: Color, side: Dp) {
    Canvas(Modifier.size(side)) {
        val gap = size.width * .08f
        val q = (size.width - gap) / 2f
        drawRect(color, Offset.Zero, Size(q, q))
        drawRect(color, Offset(q + gap, 0f), Size(q, q))
        drawRect(color, Offset(0f, q + gap), Size(q, q))
        drawRect(color, Offset(q + gap, q + gap), Size(q, q))
    }
}

/** The magnifier of the apps search line. */
@Composable
private fun SearchGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val r = w * .32f
        val c = Offset(w * .42f, w * .42f)
        val stroke = w * .09f
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        drawLine(color, Offset(c.x + r * .72f, c.y + r * .72f), Offset(w * .92f, w * .92f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * Windows Phone (Windows 10 Mobile). No taskbar: a dock runs down the left side with the start button on top, the home is a
 * grid of coloured tiles, and the apps screen is one alphabetical list. [black] gives the AMOLED variant on pure black.
 */
class WinPhoneLauncherLook(
    override val id: String,
    override val nameEn: String,
    override val nameTr: String,
    private val black: Boolean,
) : LauncherLook {
    override val taskbarHeight: Dp = 0.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    private fun bg(): Color = if (black) Color.Black else Color(0xFF1A1A1A)

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier.fillMaxSize().background(bg())) { content() }
    }

    // no taskbar: the dock is drawn by SideDock
    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {}

    @Composable
    override fun SideDock(state: LauncherState, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        Column(
            modifier.width(60.dp + edge).padding(start = edge, top = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // the Windows button opens the glance page (time, weather, battery ...), and from there the list of every app
            Box(Modifier.size(52.dp).clickable { LauncherSession.feedOpen = true }, contentAlignment = Alignment.Center) {
                StartGlyph(Color.White, 24.dp)
            }
            state.dock.forEach { app ->
                Box(Modifier.size(52.dp).background(wpTile(app)).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                    LaunchAppPicture(app, 34.dp, Color.Transparent, Color.White)
                }
            }
            // the rest of the dock: search, every app, and the settings at the foot
            Spacer(Modifier.weight(1f))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Box(Modifier.size(52.dp).clickable { LauncherSession.searchOpen = true }, contentAlignment = Alignment.Center) { SearchGlyph(Color.White) }
            Box(Modifier.size(52.dp).clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("apps", Color.White, 24.dp) }
            Box(Modifier.size(52.dp).clickable { ctx.startActivity(android.content.Intent(ctx, LauncherSettingsActivity::class.java)) }, contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("settings", Color.White, 24.dp) }
        }
    }

    /**
     * A live tile: a square in the app's colour with the picture top left and the name bottom left.
     * Everything scales with the tile width, so the glyph is about a third of the tile and the name a tenth.
     */
    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        // plain icons with the name under them, unless the live tiles are switched on in the launcher settings
        if (!LauncherPrefs.phoneTiles) {
            Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LaunchAppPicture(app, iconDp.dp, wpTile(app), Color.White)
                Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            return
        }
        Box(modifier.aspectRatio(1f).padding(3.dp).background(wpTile(app))) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
                // read the width here, in the box's own scope, and pass it down
                val side = maxWidth
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    LaunchAppPicture(app, side * .3f, Color.Transparent, Color.White)
                    Text(
                        app.label, color = Color.White, fontSize = (side.value * .1f).sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    /** The apps pivot: a title, a search line, every app in one alphabetical list under its letter, and an A to Z row. */
    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        var query by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val fg = Color.White
        val groups = state.apps
            .filter { it.label.contains(query.trim(), ignoreCase = true) }
            .groupBy { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
            .toSortedMap()
        // list index of each letter's heading, for the A to Z row
        val headAt = mutableMapOf<String, Int>()
        var at = 0
        for ((letter, apps) in groups) { headAt[letter] = at; at += 1 + apps.size }

        Column(modifier.fillMaxSize().background(bg()).statusBarsPadding().padding(horizontal = 20.dp)) {
            Text(tr("apps", "uygulamalar"), color = fg, fontSize = 44.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchGlyph(fg.copy(alpha = .8f))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.Light), cursorBrush = SolidColor(fg),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text(tr("Search", "Ara"), color = fg.copy(alpha = .6f), fontSize = 18.sp, fontWeight = FontWeight.Light)
                        inner()
                    },
                )
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState) {
                    groups.forEach { (letter, apps) ->
                        item(key = "h$letter") {
                            Text(letter, color = fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        }
                        items(apps, key = { it.pkg }) { app ->
                            Row(
                                Modifier.fillMaxWidth().appTap(state, app) { state.open(app); onClose() }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LaunchAppPicture(app, 40.dp, wpTile(app), Color.White)
                                Spacer(Modifier.width(18.dp))
                                Text(app.label, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxHeight().padding(start = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ('A'..'Z').forEach { ch ->
                        val letter = ch.toString()
                        val has = headAt.containsKey(letter)
                        Box(
                            Modifier.weight(1f).width(20.dp).clickable(enabled = has) {
                                scope.launch { listState.scrollToItem(headAt.getValue(letter)) }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(letter, color = fg.copy(alpha = if (has) 1f else .3f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}


/**
 * A live tile face: when the app has notifications waiting, the tile turns over every few seconds, from its picture to the count of new items
 * (the tiles start one after another, so the screen does not turn in step). Without notifications it stays on the picture.
 */
@Composable
private fun FlipFace(count: Int, index: Int, front: @Composable () -> Unit) {
    var back by remember { mutableStateOf(false) }
    LaunchedEffect(count > 0) {
        back = false
        if (count > 0) { delay(300L + index * 450L); while (true) { back = !back; delay(3500L) } }
    }
    Crossfade(targetState = back && count > 0, animationSpec = tween(450), label = "flip") { showBack ->
        if (showBack) Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
            Text("$count", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(tr("new", "yeni"), color = Color.White, fontSize = 13.sp)
        } else front()
    }
}

/**
 * The Windows Phone start screen: square tiles (small 1x1, medium 2x2) and wide ones (4x2), four units across. They are laid out by themselves, a long press
 * on a tile gives it another size.
 */
@Composable
fun WinPhoneTiles(state: LauncherState, apps: List<LaunchApp>, edge: Dp, modifier: Modifier = Modifier) {
    class Placed(val app: LaunchApp, val x: Int, val y: Int, val w: Int, val h: Int)
    val sizes = LauncherPrefs.tileSizes; val mode = LauncherPrefs.tileMode
    val placed = remember(apps, sizes, mode) {
        // sizes: s = 1x1, m = 2x2, w = 4x2 units; an app has the size it was given, else the layout's (auto mixes them)
        val auto = listOf("m", "s", "s", "s", "s", "w", "m", "m", "s", "s", "s", "s")
        val grid = ArrayList<BooleanArray>()
        fun free(x: Int, y: Int, w: Int, h: Int): Boolean { for (r in y until y + h) for (c in x until x + w) if (r < grid.size && grid[r][c]) return false; return true }
        val out = ArrayList<Placed>()
        apps.forEachIndexed { i, app ->
            val size = sizes[app.pkg] ?: when (mode) { "medium" -> "m"; "small" -> "s"; else -> auto[i % auto.size] }
            val (w, h) = when (size) { "w" -> 4 to 2; "m" -> 2 to 2; else -> 1 to 1 }
            // the first place from the top left where it fits: small tiles fill the holes the bigger ones leave
            var y = 0; var spot: Pair<Int, Int>? = null
            while (spot == null) {
                for (x in 0..(4 - w)) if (free(x, y, w, h)) { spot = x to y; break }
                if (spot == null) y++
            }
            val (px, py) = spot!!
            while (grid.size < py + h) grid += BooleanArray(4)
            for (r in py until py + h) for (c in px until px + w) grid[r][c] = true
            out += Placed(app, px, py, w, h)
        }
        out
    }
    val rows = (placed.maxOfOrNull { it.y + it.h } ?: 0)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = maxOf(8.dp, edge), vertical = 8.dp)) {
        val gap = 4.dp
        val unit = (maxWidth - gap * 3) / 4
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(unit * rows + gap * (rows - 1).coerceAtLeast(0))) {
                placed.forEach { t ->
                    val w = unit * t.w + gap * (t.w - 1); val h = unit * t.h + gap * (t.h - 1)
                    Box(Modifier.offset((unit + gap) * t.x, (unit + gap) * t.y).size(w, h).background(wpTile(t.app)).appTap(state, t.app) { state.open(t.app) }) {
                        if (t.w == 1) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LaunchAppPicture(t.app, unit * .62f, Color.Transparent, Color.White) }
                        else if (t.w == 4) Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            LaunchAppPicture(t.app, h * .5f, Color.Transparent, Color.White)
                            Spacer(Modifier.width(16.dp))
                            Text(t.app.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else FlipFace(if (LauncherPrefs.badges) LauncherBadges.counts[t.app.pkg] ?: 0 else 0, placed.indexOf(t)) {
                            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                LaunchAppPicture(t.app, w * .4f, Color.Transparent, Color.White)
                                Text(t.app.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * The page behind the Windows button: the time and date large, then cards with the weather (the last forecast the weather app saved), the battery, the notifications
 * waiting and today in the calendar, and a row that opens the list of every app. A back arrow at the top goes back to the home screen.
 */
@Composable
fun WinPhoneFeed(onApps: () -> Unit, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler { onClose() }
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) { while (true) { kotlinx.coroutines.delay(1000); value = System.currentTimeMillis() } }
    val d = java.util.Date(now)
    val forecast = remember { runCatching { com.umutk.pulse.extras.weather.WeatherStore.cached(ctx) }.getOrNull() }
    val battery = remember(now / 20_000L) {
        val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val lv = i?.let { it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
        lv to ((i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0) ?: 0).let { it == android.os.BatteryManager.BATTERY_STATUS_CHARGING || it == android.os.BatteryManager.BATTERY_STATUS_FULL })
    }
    val notes = LauncherBadges.counts.values.sum()
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("back", Color.White, 24.dp) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(java.text.SimpleDateFormat(if (LauncherPrefs.clock24) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(d), color = Color.White, fontSize = 80.sp, fontWeight = FontWeight.ExtraLight)
            Text(java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(d), color = Color.White.copy(alpha = .8f), fontSize = 20.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(20.dp))
            @Composable fun card(c: Color, content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(c).padding(16.dp)) { content() }
            val imp = com.umutk.pulse.extras.weather.WeatherFmt.imperial(ctx)
            card(Color(0xFF0078D7)) {
                if (forecast != null) Column {
                    Text(forecast.place, color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                    Text(com.umutk.pulse.extras.weather.WeatherFmt.temp(forecast.temp, imp), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
                    forecast.days.firstOrNull()?.let { Text("↑ " + com.umutk.pulse.extras.weather.WeatherFmt.temp(it.max, imp) + "   ↓ " + com.umutk.pulse.extras.weather.WeatherFmt.temp(it.min, imp), color = Color.White.copy(alpha = .85f), fontSize = 14.sp) }
                } else Text(tr("Open the weather app once to see the weather here", "Burada hava durumu için hava durumu uygulamasını bir kez aç"), color = Color.White, fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f).padding(end = 3.dp)) { card(Color(0xFF5C2D91)) { Column { Text("${battery.first}%", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light); Text(if (battery.second) tr("Charging", "Şarjda") else tr("Battery", "Pil"), color = Color.White.copy(alpha = .85f), fontSize = 13.sp) } } }
                Box(Modifier.weight(1f).padding(start = 3.dp)) { card(Color(0xFFD83B01)) { Column { Text("$notes", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light); Text(tr("Notifications", "Bildirim"), color = Color.White.copy(alpha = .85f), fontSize = 13.sp) } } }
            }
            card(Color(0xFF107C10)) {
                Column {
                    Text(java.text.SimpleDateFormat("d", java.util.Locale.getDefault()).format(d), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
                    Text(java.text.SimpleDateFormat("EEEE, MMMM yyyy", java.util.Locale.getDefault()).format(d), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { onApps() }.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("all apps", "tüm uygulamalar"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
                Text("→", color = Color.White, fontSize = 24.sp)
            }
        }
    }
}
