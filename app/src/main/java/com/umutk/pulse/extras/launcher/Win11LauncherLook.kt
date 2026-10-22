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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.FluentPaths
import com.umutk.pulse.Look
import com.umutk.pulse.T
import com.umutk.pulse.backdrop
import com.umutk.pulse.layer
import com.umutk.pulse.tr

/** Windows 11 look: centred translucent taskbar with Fluent glyphs, a rounded Start flyout with pinned, recommended and all-apps pages on a blue wallpaper. Translucency is allowed for this look only. */
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
        val tileC = tile()
        val accent = Look.accent.copy(alpha = 1f)
        val open = LauncherSession.startOpen
        val edge = LocalLauncherEdge.current
        val shape = RoundedCornerShape(8.dp)
        // the edge padding keeps the clock out of the rounded screen corners
        Box(glass(modifier.fillMaxWidth().height(taskbarHeight), taskbarFill()).padding(horizontal = edge), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(40.dp).clip(shape).clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) {
                    StartGlyph(fg)
                    if (open) Indicator(accent, Modifier.align(Alignment.BottomCenter))
                }
                Box(Modifier.size(40.dp).clip(shape).clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) {
                    Glyph("search", fg, 20.dp)
                }
                state.dock.forEach { app ->
                    Box(Modifier.size(40.dp).clip(shape).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 24.dp, tileC, fg)
                    }
                }
            }
            TaskbarClock(fg, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val fg = fg()
        val tileC = tile()
        val accent = Look.accent.copy(alpha = 1f)
        val edge = LocalLauncherEdge.current
        val shape = RoundedCornerShape(8.dp)
        var query by remember { mutableStateOf("") }
        var allPage by remember { mutableStateOf(false) }
        val (pinned, all) = startLists(state, query)
        // Recommended: the newest installs (the launcher keeps no usage data)
        val recent = if (query.isBlank()) state.apps.filter { it.installed > 0L }.sortedByDescending { it.installed }.take(4) else emptyList<LaunchApp>()
        val launch: (LaunchApp) -> Unit = { state.open(it); onClose() }
        Box(modifier.fillMaxSize().clickable(onClick = onClose)) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = maxOf(8.dp, edge), end = maxOf(8.dp, edge), bottom = taskbarHeight + 8.dp)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .fillMaxHeight(.78f)
                    .shadow(24.dp, shape, clip = false)
                    .clip(shape)
                    .let { glass(it, flyoutFill(), shape) }
                    .clickable(enabled = false) {}
                    .padding(16.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(shape).background(field())
                        .drawBehind { drawRect(accent, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph("search", fg.copy(alpha = .7f), 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = fg, fontSize = 14.sp),
                                cursorBrush = SolidColor(fg),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) Text(tr("Search", "Ara"), color = fg.copy(alpha = .6f), fontSize = 14.sp)
                                    inner()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (query.isNotBlank() || allPage) {
                        // searching gives one flat grid; the all-apps page has its group headings
                        Column(Modifier.fillMaxSize()) {
                            if (query.isBlank()) Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(shape).clickable { allPage = false }, contentAlignment = Alignment.Center) { Glyph("back", fg, 16.dp) }
                                Text(tr("All apps", "Tüm uygulamalar"), color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                            }
                            LazyVerticalGrid(GridCells.Fixed(state.columns), Modifier.fillMaxWidth().weight(1f)) {
                                if (query.isBlank()) groupedApps(all).forEach { (g, apps) ->
                                    item(span = { GridItemSpan(state.columns) }) { Win11Heading(tr(g.en, g.tr), fg) }
                                    items(apps, key = { it.pkg }) { app -> StartCell(app, state.iconDp, fg, tileC, Modifier.fillMaxWidth().clip(shape).appTap(state, app) { launch(app) }) }
                                } else items(all, key = { it.pkg }) { app ->
                                    StartCell(app, state.iconDp, fg, tileC, Modifier.fillMaxWidth().clip(shape).appTap(state, app) { launch(app) })
                                }
                            }
                        }
                    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Win11Heading(if (pinned.isNotEmpty()) tr("Pinned", "Sabitlenenler") else null, fg) {
                            Row(Modifier.clip(shape).clickable { allPage = true }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("All apps", "Tüm uygulamalar"), color = fg, fontSize = 12.sp)
                                Spacer(Modifier.width(4.dp))
                                Glyph("chevron", fg, 12.dp, Modifier.rotate(-90f))
                            }
                        }
                        pinned.chunked(state.columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { app -> StartCell(app, state.iconDp, fg, tileC, Modifier.weight(1f).clip(shape).appTap(state, app) { launch(app) }) }
                                repeat(state.columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (recent.isNotEmpty()) {
                            Win11Heading(tr("Recommended", "Önerilen"), fg)
                            recent.forEach { app ->
                                Row(Modifier.fillMaxWidth().clip(shape).appTap(state, app) { launch(app) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LaunchAppPicture(app, 32.dp, tileC, fg)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(app.label, color = fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(tr("Recently installed", "Yakın zamanda yüklendi"), color = fg.copy(alpha = .6f), fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                        // the foot of the menu: the way out of the desktop into the rest of Pulse
                        val logCtx = androidx.compose.ui.platform.LocalContext.current
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            Row(Modifier.clip(shape).clickable { onClose(); logOff(logCtx) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                com.umutk.pulse.PIcon("power", fg, 18.dp); Spacer(Modifier.width(8.dp)); Text(tr("Log off", "Oturumu kapat"), color = fg, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // the desktop sits on the blue wallpaper, so its labels stay white with a soft shadow
    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, tile(), Color.White)
            Text(
                app.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 4f)), modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** A start-menu tile: icon over a one-line label. */
@Composable
private fun StartCell(app: LaunchApp, iconDp: Int, fg: Color, tile: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LaunchAppPicture(app, iconDp.dp, tile, fg)
        Text(app.label, color = fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Section title of the start menu, with an optional control on the right (default none). */
@Composable
private fun Win11Heading(text: String?, color: Color, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        if (text != null) Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Small accent bar under a taskbar button that is open. */
@Composable
private fun Indicator(color: Color, modifier: Modifier) {
    Box(modifier.padding(bottom = 2.dp).size(16.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(color))
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

/** A Fluent UI System outline from FluentPaths, drawn in [tint] (24 px grid scaled to [size]). */
@Composable
private fun Glyph(key: String, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val paths = remember(key) { (FluentPaths.map[key] ?: FluentPaths.map["info"].orEmpty()).map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } }
    }
}
