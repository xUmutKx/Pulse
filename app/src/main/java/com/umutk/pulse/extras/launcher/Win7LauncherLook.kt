package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr

internal val W7_WINDOW = WinSkin("7", Brush.verticalGradient(listOf(Color(0xFFC4D8EE), Color(0xFF9DBBE0))), Color.Black, Color(0xFF7B9EC8), 5.dp, 7.dp,
    Color.White, Color(0xFFEEF3FA), Color.White, Color.Black, Color(0xFF6D6D6D), Color(0xFFCDE5FA), Color.Black, Color(0xFF7DA2CE))


// Aero glass is approximated with fixed vertical gradients: no blur, no transparency over the wallpaper.
private val W7_WALL = Brush.verticalGradient(listOf(Color(0xFF6E9CCB), Color(0xFF2F5C8C)))
private val W7_TASKBAR = Brush.verticalGradient(listOf(Color(0xFF6A93C0), Color(0xFF3A6592), Color(0xFF2A4D76)))
private val W7_TASKBAR_HI = Color(0xFFB9D3EE)
private val W7_TRAY_LINE = Color(0x663C6A96)
private val W7_SHOW_STRIP = Color(0x33FFFFFF)
private val W7_HOT = Color(0x553E6E9E)
private val W7_HOT_EDGE = Color(0x88A9CBEB)
private val W7_TILE = Color(0xFF2B4A6B)
private val W7_ORB_RIM = Color(0xFF0B2A4F)
private val W7_ORB_EDGE = Color(0xFF0E3A6E)
private val W7_ORB = Color(0xFF2A6FB8)
private val W7_ORB_HI = Color(0xFFA6D4F5)
private val W7_FRAME = Brush.verticalGradient(listOf(Color(0xFF4A73A0), Color(0xFF243F63)))
private val W7_FRAME_EDGE = Color(0xFF13263F)
private val W7_RIGHT = Brush.verticalGradient(listOf(Color(0xFF5E86B2), Color(0xFF2E4F78)))
private val W7_SEARCH_BG = Color(0xFFD9E4F1)
private val W7_SEARCH_EDGE = Color(0xFF7A96B0)
private val W7_SEP = Color(0xFFCBD8E6)
private val W7_SHUT = Brush.verticalGradient(listOf(Color(0xFFF4F8FC), Color(0xFFD5E2F0), Color(0xFFB4C8E0)))
private val W7_SHUT_EDGE = Color(0xFF5D7FA8)
private val W7_FOLDER = Color(0xFFE8B84A)
private val W7_FOLDER_FRONT = Color(0xFFFBDF7C)
private val W7_LABEL = TextStyle(color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, shadow = Shadow(Color.Black, Offset(1f, 1f), 1f))

/** Windows 7 look: taskbar with a glossy orb, Start menu with the search at its foot, desktop labels with shadows. App icons come from LaunchApp.icon. */
class Win7LauncherLook : LauncherLook {
    override val id: String = "win7"
    override val nameEn: String = "Windows 7"
    override val nameTr: String = "Windows 7"
    override val taskbarHeight: Dp = 40.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        LauncherWallpaper(id, W7_WALL, modifier) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        Column(modifier.fillMaxWidth().height(taskbarHeight).background(W7_TASKBAR)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(W7_TASKBAR_HI))
            // the edge padding keeps the orb and the clock out of the rounded screen corners
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = edge), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(46.dp).fillMaxHeight().clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) {
                    StartOrb()
                }
                state.dock.forEach { app ->
                    Box(Modifier.width(44.dp).fillMaxHeight().appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        val hot = RoundedCornerShape(3.dp)
                        Box(Modifier.width(38.dp).height(34.dp).clip(hot).background(W7_HOT).border(1.dp, W7_HOT_EDGE, hot), contentAlignment = Alignment.Center) {
                            LaunchAppPicture(app, 26.dp, W7_TILE, Color.White)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight(.6f).background(W7_TRAY_LINE))
                TaskbarClock(Color.White, Modifier.padding(horizontal = 8.dp))
                // the show-desktop strip at the far right end, as on Windows 7
                Box(Modifier.width(1.dp).fillMaxHeight().background(W7_TRAY_LINE))
                Box(Modifier.width(5.dp).fillMaxHeight().background(W7_SHOW_STRIP))
            }
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val edge = LocalLauncherEdge.current
        val none = remember { MutableInteractionSource() }
        var query by remember { mutableStateOf("") }
        var all by remember { mutableStateOf(false) }
        var power by remember { mutableStateOf(false) }
        val desk = remember { desktopItems() }
        fun go(pkg: String) { desk.firstOrNull { it.pkg == pkg }?.let { state.open(it); onClose() } }
        val frame = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        // a tap on the desktop around the menu closes it; the menu swallows its own taps
        Box(modifier.clickable(interactionSource = none, indication = null, onClick = onClose)) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = edge + 4.dp, bottom = taskbarHeight).width(390.dp).height(470.dp)
                    .clip(frame).background(W7_FRAME).border(1.dp, W7_FRAME_EDGE, frame).padding(5.dp)
                    .clickable(interactionSource = none, indication = null) { power = false },
            ) {
                // left pane: the programs, All programs as a folder tree, the search at the foot
                Column(Modifier.weight(1.25f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color.White)) {
                    val pinned = remember(state.dock, state.apps) { (state.dock + state.apps).distinctBy { it.pkg }.take(9) }
                    val found = remember(query, state.apps) { state.apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
                        when {
                            query.isNotBlank() -> items(found, key = { it.pkg + it.label }) { W7Row(it, state, onClose) }
                            all -> groupedApps(state.apps).forEach { (g, apps) ->
                                item { W7Folder(tr(g.en, g.tr)) }
                                items(apps, key = { it.pkg + it.label }) { Box(Modifier.padding(start = 14.dp)) { W7Row(it, state, onClose) } }
                            }
                            else -> items(pinned, key = { it.pkg + it.label }) { W7Row(it, state, onClose) }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(W7_SEP))
                    Row(Modifier.fillMaxWidth().height(34.dp).clickable { all = !all; query = "" }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (all) "◄  " + tr("Back", "Geri") else "►  " + tr("All programs", "Tüm programlar"), color = Color.Black, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                    W7Search(query) { query = it; if (it.isNotEmpty()) all = false }
                }
                Spacer(Modifier.width(5.dp))
                // right pane: the places of the computer and the way out
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(W7_RIGHT).padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFDDE9F6)).border(1.dp, Color.White, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                            com.umutk.pulse.PIcon("person", Color(0xFF3A6592), 28.dp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Pulse", color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    W7Place("folder", tr("Documents", "Belgeler")) { go("pulse:files") }
                    W7Place("image", tr("Pictures", "Resimler")) { go("pulse:files") }
                    W7Place("music", tr("Music", "Müzik")) { go("pulse:x:music") }
                    W7Place("gridView", tr("Games", "Oyunlar")) { go("pulse:games") }
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(Color(0x44FFFFFF)))
                    W7Place("phone", tr("Computer", "Bilgisayar")) { go("pulse:pc") }
                    W7Place("settings", tr("Control Panel", "Denetim Masası")) { go("pulse:settings") }
                    W7Place("performance", tr("Devices and Printers", "Aygıtlar ve Yazıcılar")) { go("pulse:control") }
                    W7Place("apps", tr("Default Programs", "Varsayılan Programlar")) { go("pulse:apps") }
                    W7Place("processes", tr("Task Manager", "Görev Yöneticisi")) { go("pulse:taskmgr") }
                    Spacer(Modifier.weight(1f))
                    // the shut-down button sits at the bottom right of the right pane, with its arrow for the other ways out
                    Box(Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                        val shut = RoundedCornerShape(4.dp)
                        Row(Modifier.height(26.dp).clip(shut).background(W7_SHUT).border(1.dp, W7_SHUT_EDGE, shut), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Log off", "Oturumu kapat"), color = Color(0xFF1B2B3F), fontSize = 12.sp, modifier = Modifier.clickable { onClose(); logOff(ctx) }.padding(horizontal = 10.dp).padding(vertical = 4.dp))
                            Box(Modifier.width(1.dp).fillMaxHeight().background(W7_SHUT_EDGE))
                            Box(Modifier.width(22.dp).fillMaxHeight().clickable { power = !power }, contentAlignment = Alignment.Center) { Text("▸", color = Color(0xFF1B2B3F), fontSize = 12.sp) }
                        }
                        if (power) Column(Modifier.align(Alignment.BottomEnd).padding(bottom = 30.dp).width(130.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF1F5FA)).border(1.dp, W7_SHUT_EDGE, RoundedCornerShape(4.dp))) {
                            listOf(tr("Lock", "Kilitle") to { globalAction(ctx, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); onClose() },
                                tr("Log off", "Oturumu kapat") to { onClose(); logOff(ctx) }).forEach { (t, a) ->
                                Text(t, color = Color.Black, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = a).padding(horizontal = 10.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    /** One place of the right pane: the classic Windows 7 icon and a white label. */
    @Composable
    private fun W7Place(icon: String, text: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().height(32.dp).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.umutk.pulse.PIcon(icon, Color.White, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    /** Program row: 28 dp icon, 12 sp black label, 36 dp tall, as in the Windows 7 programs list. */
    @Composable
    private fun W7Row(app: LaunchApp, state: LauncherState, onClose: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).appTap(state, app) { state.open(app); onClose() }.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LaunchAppPicture(app, 28.dp, W7_TILE, Color.White)
            Spacer(Modifier.width(8.dp))
            Text(app.label, color = Color.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    /** Group heading as a folder row with a drawn folder, as the Windows 7 All Programs tree shows its folders. */
    @Composable
    private fun W7Folder(text: String) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val back = Path().apply {
                    moveTo(0f, h * .18f)
                    lineTo(w * .38f, h * .18f)
                    lineTo(w * .47f, h * .3f)
                    lineTo(w, h * .3f)
                    lineTo(w, h * .9f)
                    lineTo(0f, h * .9f)
                    close()
                }
                drawPath(back, W7_FOLDER)
                drawRect(W7_FOLDER_FRONT, topLeft = Offset(0f, h * .42f), size = Size(w, h * .48f))
            }
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    /** Search box at the foot of the programs pane, with a drawn magnifier. */
    @Composable
    private fun W7Search(query: String, onQuery: (String) -> Unit) {
        Box(Modifier.fillMaxWidth().background(W7_SEARCH_BG).padding(6.dp)) {
            val shape = RoundedCornerShape(3.dp)
            Box(Modifier.fillMaxWidth().height(24.dp).clip(shape).background(Color.White).border(1.dp, W7_SEARCH_EDGE, shape)) {
                Box(Modifier.fillMaxSize().padding(start = 6.dp, end = 22.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query, onValueChange = onQuery, singleLine = true,
                        textStyle = TextStyle(color = Color.Black, fontSize = 12.sp), cursorBrush = SolidColor(Color.Black),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(tr("Search programs", "Program ara"), color = Color(0xFF6D6D6D), fontSize = 12.sp)
                            inner()
                        },
                    )
                }
                Canvas(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(12.dp)) {
                    val w = size.width
                    drawCircle(Color(0xFF555555), radius = w * .34f, center = Offset(w * .6f, w * .4f), style = Stroke(width = w * .12f))
                    drawLine(Color(0xFF555555), Offset(w * .38f, w * .62f), Offset(w * .06f, w * .94f), strokeWidth = w * .16f)
                }
            }
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, W7_TILE, Color.White)
            Text(app.label, style = W7_LABEL, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }

    /** Glossy round orb: radial body, a rim, the Windows flag in four panes and a top gloss. */
    @Composable
    private fun StartOrb() {
        Canvas(Modifier.size(36.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(listOf(W7_ORB_HI, W7_ORB, W7_ORB_EDGE), center = Offset(c.x - r * .25f, c.y - r * .35f), radius = r * 1.3f),
                radius = r, center = c,
            )
            drawCircle(W7_ORB_RIM, radius = r - .5f, center = c, style = Stroke(width = 1f))
            // the flag: four waving panes in red, green, blue and yellow
            val u = r * .17f; val g = r * .05f; val x0 = c.x - u * 2 - g / 2; val y0 = c.y - u * 2 - g / 2
            fun pane(i: Int, j: Int, col: Color) = drawRect(col, Offset(x0 + i * (u * 2 + g) + (if (j == 1) u * .0f else 0f), y0 + j * (u * 2 + g) + i * u * .12f), Size(u * 2, u * 2))
            pane(0, 0, Color(0xFFF25022)); pane(1, 0, Color(0xFF7FBA00)); pane(0, 1, Color(0xFF00A4EF)); pane(1, 1, Color(0xFFFFB900))
            drawOval(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = .55f), Color.White.copy(alpha = 0f)), startY = c.y - r * .85f, endY = c.y),
                topLeft = Offset(c.x - r * .7f, c.y - r * .85f), size = Size(r * 1.4f, r * .9f),
            )
        }
    }
}
