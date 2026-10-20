package com.umutk.pulse.extras.launcher

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.util.Calendar

/** Windows 95 look: grey #C0C0C0 faces, 1 px bevel rings (2 px per frame), navy title bars and selection, the vertical Start banner. Opaque, no gradients. */
class Win95LauncherLook : LauncherLook {
    override val id: String = "win95"
    override val nameEn: String = "Windows 95"
    override val nameTr: String = "Windows 95"
    override val taskbarHeight: Dp = 30.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalContentColor provides W95.Black) {
            LauncherWallpaper(id, W95.Desktop, modifier) { content() }
        }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        val menuOpen = LauncherSession.startOpen
        CompositionLocalProvider(LocalContentColor provides W95.Black) {
            Row(
                modifier.fillMaxWidth().height(taskbarHeight).background(W95.Face).drawBehind {
                    // raised top edge: white line, then the light face line
                    val p = 1.dp.toPx()
                    drawRect(W95.Hi, Offset.Zero, Size(size.width, p))
                    drawRect(W95.Light, Offset(0f, p), Size(size.width, p))
                }.padding(start = edge + 2.dp, end = edge + 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Start button: pressed (sunken, content shifted down 1 px) while the menu is open
                Box(
                    Modifier.width(72.dp).fillMaxHeight().w95Bevel(raised = !menuOpen).w95Click { LauncherSession.startOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(Modifier.padding(top = if (menuOpen) 1.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        W95Logo()
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Start", "Başlat"), color = W95.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(6.dp))
                // grip in front of the quick launch icons
                Spacer(Modifier.width(4.dp).fillMaxHeight().drawBehind {
                    val p = 1.dp.toPx()
                    drawRect(W95.Shadow, Offset(p, 0f), Size(p, size.height))
                    drawRect(W95.Hi, Offset(2 * p, 0f), Size(p, size.height))
                })
                Spacer(Modifier.width(3.dp))
                state.dock.forEach { app ->
                    Box(Modifier.size(26.dp).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 18.dp, W95.Face, W95.Black)
                    }
                }
                Spacer(Modifier.weight(1f))
                // sunken tray with a speaker and the clock
                Row(
                    Modifier.fillMaxHeight().w95Bevel(raised = false).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(12.dp)) { drawSpeaker() }
                    Spacer(Modifier.width(5.dp))
                    Text(w95Clock(), color = W95.Black, fontSize = 11.sp)
                }
            }
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        val ctx = LocalContext.current
        var query by remember { mutableStateOf("") }
        var folder by remember { mutableStateOf<StartGroup?>(null) }
        val (pinned, all) = startLists(state, query)
        val searching = query.isNotBlank()
        val groups: List<Pair<StartGroup, List<LaunchApp>>> = if (searching) emptyList() else groupedApps(all)
        val noRipple = remember { MutableInteractionSource() }
        // a tap outside the menu closes it; the menu swallows its own taps
        BoxWithConstraints(modifier.clickable(interactionSource = noRipple, indication = null, onClick = onClose)) {
            val menuH = (maxHeight - taskbarHeight - 2.dp).coerceAtMost(440.dp)
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = edge, bottom = taskbarHeight)
                    .width(240.dp).height(menuH)
                    .clickable(interactionSource = noRipple, indication = null) {}
                    .w95Bevel(raised = true)
                    .padding(3.dp),
            ) {
                W95Banner(Modifier.width(24.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (searching) W95TitleBar(
                        title = tr("Find", "Bul"),
                        icon = { Canvas(Modifier.size(12.dp)) { drawMagnifier(Color.White) } },
                        onClose = { query = "" },
                    )
                    // search: a sunken white field with a magnifier
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp).height(22.dp).w95Bevel(raised = false, fill = Color.White),
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(12.dp)) { drawMagnifier(W95.Black) }
                            Spacer(Modifier.width(5.dp))
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = TextStyle(color = W95.Black, fontSize = 11.sp), cursorBrush = SolidColor(W95.Black),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        if (searching) {
                            items(all, key = { it.pkg }) { app -> W95App(app, state, onClose) }
                        } else {
                            if (pinned.isNotEmpty()) {
                                items(pinned, key = { "pin-" + it.pkg }) { app -> W95App(app, state, onClose) }
                                item(key = "pin-sep") { W95Sep() }
                            }
                            // the apps by store group: a folder opens inline, one at a time
                            groups.forEach { (g, apps) ->
                                item(key = "group-" + g.name) {
                                    W95Item(
                                        label = tr(g.en, g.tr),
                                        tap = Modifier.w95Click { folder = if (folder == g) null else g },
                                        selected = folder == g,
                                        arrow = true,
                                        icon = { W95Folder() },
                                    )
                                }
                                if (folder == g) items(apps, key = { it.pkg }) { app -> W95App(app, state, onClose, indent = 16.dp) }
                            }
                            item(key = "exit-sep") { W95Sep() }
                            item(key = "settings") {
                                W95Item(
                                    label = tr("Settings", "Ayarlar"),
                                    tap = Modifier.w95Click { ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java)); onClose() },
                                    icon = { Canvas(Modifier.size(20.dp)) { drawGear(W95.Black, W95.Face) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun W95App(app: LaunchApp, state: LauncherState, onClose: () -> Unit, indent: Dp = 0.dp) {
        W95Item(
            label = app.label,
            tap = Modifier.appTap(state, app) { state.open(app); onClose() },
            indent = indent,
            icon = { LaunchAppPicture(app, 24.dp, W95.Face, W95.Black) },
        )
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Column(modifier.width(75.dp).padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(75.dp).height(iconDp.dp + 2.dp), contentAlignment = Alignment.BottomCenter) {
                LaunchAppPicture(app, iconDp.dp, W95.Face, W95.Black)
            }
            Text(
                app.label,
                color = Color.White,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** One menu row: navy and white when selected, else black on the face. [tap] is the row's click (app tap or folder toggle). */
@Composable
private fun W95Item(
    label: String,
    tap: Modifier,
    selected: Boolean = false,
    indent: Dp = 0.dp,
    arrow: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val fg = if (selected) Color.White else W95.Black
    Row(
        Modifier.fillMaxWidth().height(34.dp).background(if (selected) W95.Navy else W95.Face).then(tap)
            .padding(start = 4.dp + indent, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (arrow) Canvas(Modifier.width(4.dp).height(8.dp)) { drawArrow(fg) }
    }
}

/** Navy title bar with a bevelled close button; used by the Find window. */
@Composable
private fun W95TitleBar(title: String, icon: @Composable () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(20.dp).background(W95.Navy).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(4.dp))
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        Box(Modifier.width(16.dp).height(14.dp).w95Bevel(raised = true).w95Click(onClose), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(7.dp)) { drawCross(W95.Black) }
        }
    }
}

/** The vertical banner down the left edge of the Start menu, its text reading upwards from the bottom. */
@Composable
private fun W95Banner(modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = W95.Face, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier.background(W95.Navy).drawBehind {
        translate(left = 4.dp.toPx(), top = size.height - 8.dp.toPx()) {
            rotate(-90f, pivot = Offset.Zero) { drawText(measurer, "Windows 95", style = style) }
        }
    })
}

/** Etched horizontal separator: shadow line over a white line. */
@Composable
private fun W95Sep() {
    Spacer(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp).height(2.dp).drawBehind {
        val p = 1.dp.toPx()
        drawRect(W95.Shadow, Offset.Zero, Size(size.width, p))
        drawRect(W95.Hi, Offset(0f, p), Size(size.width, p))
    })
}

/** A click without the Material ripple: the 95 controls have no press glow. */
@Composable
private fun Modifier.w95Click(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
private fun W95Folder() {
    Canvas(Modifier.size(20.dp)) { drawFolder() }
}

@Composable
private fun W95Logo() {
    Canvas(Modifier.size(14.dp)) {
        val g = size.width * .08f
        val cw = (size.width - g) / 2
        val ch = (size.height - g) / 2
        drawRect(W95.Red, Offset.Zero, Size(cw, ch))
        drawRect(W95.Green, Offset(cw + g, 0f), Size(cw, ch))
        drawRect(W95.Blue, Offset(0f, ch + g), Size(cw, ch))
        drawRect(W95.Gold, Offset(cw + g, ch + g), Size(cw, ch))
    }
}

private object W95 {
    val Face = Color(0xFFC0C0C0)
    val Light = Color(0xFFDFDFDF)
    val Hi = Color.White
    val Shadow = Color(0xFF808080)
    val Black = Color.Black
    val Navy = Color(0xFF000080)
    val Desktop = Color(0xFF008080)
    val Folder = Color(0xFFFFE15A)
    val Red = Color(0xFFFF0000)
    val Green = Color(0xFF00A000)
    val Blue = Color(0xFF0000FF)
    val Gold = Color(0xFFFFD700)
}

/** Win95 frame: the fill, then a 2 px bevel ring. Raised = white and light on top-left, black and shadow on bottom-right; sunken = the reverse. */
private fun Modifier.w95Bevel(raised: Boolean, fill: Color = W95.Face): Modifier =
    background(fill).drawBehind { drawW95Bevel(raised) }

private fun DrawScope.drawW95Bevel(raised: Boolean) {
    val p = 1.dp.toPx()
    val w = size.width
    val h = size.height
    val outTL = if (raised) W95.Hi else W95.Shadow
    val inTL = if (raised) W95.Light else W95.Black
    val outBR = if (raised) W95.Black else W95.Hi
    val inBR = if (raised) W95.Shadow else W95.Light
    drawRect(outTL, Offset.Zero, Size(w, p))
    drawRect(outTL, Offset.Zero, Size(p, h))
    drawRect(outBR, Offset(0f, h - p), Size(w, p))
    drawRect(outBR, Offset(w - p, 0f), Size(p, h))
    drawRect(inTL, Offset(p, p), Size(w - 2 * p, p))
    drawRect(inTL, Offset(p, p), Size(p, h - 2 * p))
    drawRect(inBR, Offset(p, h - 2 * p), Size(w - 2 * p, p))
    drawRect(inBR, Offset(w - 2 * p, p), Size(p, h - 2 * p))
}

private fun DrawScope.drawSpeaker() {
    val w = size.width
    val h = size.height
    drawPath(Path().apply {
        moveTo(0f, h * .35f); lineTo(w * .3f, h * .35f); lineTo(w * .6f, 0f)
        lineTo(w * .6f, h); lineTo(w * .3f, h * .65f); lineTo(0f, h * .65f); close()
    }, W95.Black)
    drawArc(W95.Black, -60f, 120f, useCenter = false, topLeft = Offset(w * .55f, h * .25f), size = Size(w * .4f, h * .5f), style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawArrow(color: Color) {
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h); close() }, color)
}

private fun DrawScope.drawCross(color: Color) {
    val w = size.width
    val h = size.height
    val t = 1.5f.dp.toPx()
    drawLine(color, Offset.Zero, Offset(w, h), strokeWidth = t)
    drawLine(color, Offset(w, 0f), Offset(0f, h), strokeWidth = t)
}

private fun DrawScope.drawMagnifier(color: Color) {
    val w = size.width
    val h = size.height
    val t = 1.5f.dp.toPx()
    drawCircle(color, radius = w * .3f, center = Offset(w * .6f, h * .4f), style = Stroke(width = t))
    drawLine(color, Offset(w * .4f, h * .6f), Offset(w * .05f, h * .95f), strokeWidth = t * 2)
}

/** Yellow folder with a tab, black outline. */
private fun DrawScope.drawFolder() {
    val w = size.width
    val h = size.height
    val p = 1.dp.toPx()
    drawRect(W95.Black, Offset(0f, h * .3f), Size(w, h * .7f))
    drawRect(W95.Folder, Offset(p, h * .3f + p), Size(w - 2 * p, h * .7f - 2 * p))
    drawRect(W95.Black, Offset(0f, h * .15f), Size(w * .45f, h * .3f))
    drawRect(W95.Folder, Offset(p, h * .15f + p), Size(w * .45f - 2 * p, h * .3f - p))
}

/** Cog: eight teeth around a disc with a centre hole. */
private fun DrawScope.drawGear(color: Color, hole: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension / 2
    repeat(8) { i ->
        rotate(i * 45f, c) { drawRect(color, Offset(c.x - r * .22f, 0f), Size(r * .44f, r * .45f)) }
    }
    drawCircle(color, radius = r * .7f, center = c)
    drawCircle(hole, radius = r * .28f, center = c)
}

/** Clock text "HH:mm", refreshed every second. */
@Composable
private fun w95Clock(): String {
    val now by produceState(initialValue = "") {
        while (true) {
            val c = Calendar.getInstance()
            value = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            delay(1000)
        }
    }
    return now
}
