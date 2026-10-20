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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr

// Solid colours only (no Aero gradients or glass): Win7 is opaque here.
private val W7_WALL = Color(0xFF3F6E9E)
private val W7_TASKBAR = Color(0xFF2B4A6B)
private val W7_TASKBAR_HI = Color(0xFF5C7FA3)
private val W7_TILE = Color(0xFF2B4A6B)
private val W7_ORB_EDGE = Color(0xFF1B3450)
private val W7_ORB = Color(0xFF3E7CB1)
private val W7_ORB_HI = Color(0xFF9CC4E8)
private val W7_MENU = Color(0xFF2B4A6B)
private val W7_BORDER = Color(0xFF555555)
private val W7_SEARCH = Color(0xFFF1F5FB)
private val W7_SHUT = Color(0xFF3F6E9E)
private val W7_LABEL = TextStyle(color = Color.White, fontSize = 12.sp, shadow = Shadow(Color.Black, Offset(1f, 1f), 1f))

/** Windows 7 look: solid taskbar and Start menu, round drawn orb, app icons from LaunchApp.icon. */
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
                Box(Modifier.width(54.dp).fillMaxHeight().clickable { LauncherSession.startOpen = true }, contentAlignment = Alignment.Center) {
                    StartOrb()
                }
                state.dock.forEach { app ->
                    Box(Modifier.width(44.dp).fillMaxHeight().appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 24.dp, W7_TILE, Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                TaskbarClock(Color.White, Modifier.padding(end = 12.dp))
            }
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        var query by remember { mutableStateOf("") }
        val (pinned, all) = startLists(state, query)
        val noRipple = remember { MutableInteractionSource() }
        // a tap outside the menu closes it; the menu swallows its own taps
        Box(modifier.clickable(interactionSource = noRipple, indication = null, onClick = onClose)) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = edge, bottom = taskbarHeight)
                    .fillMaxWidth(.94f).widthIn(max = 402.dp).fillMaxHeight(.75f)
                    .background(W7_MENU).border(1.dp, W7_BORDER).padding(6.dp)
                    .clickable(interactionSource = noRipple, indication = null) {},
            ) {
                Column(Modifier.weight(1f).fillMaxHeight().background(Color.White).border(1.dp, Color.Black)) {
                    Box(Modifier.fillMaxWidth().background(W7_SEARCH).padding(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(24.dp).background(Color.White).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = TextStyle(color = Color.Black, fontSize = 12.sp), cursorBrush = SolidColor(Color.Black),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) Text(tr("Search programs", "Program ara"), color = Color(0xFF777777), fontSize = 12.sp)
                                    inner()
                                },
                            )
                        }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(4.dp)) {
                        if (pinned.isNotEmpty()) {
                            item { W7Heading(tr("Pinned", "Sabitlenenler")) }
                            items(pinned) { app -> W7Row(app, state, onClose) }
                        }
                        item { W7Heading(tr("All programs", "Tüm programlar")) }
                        items(all) { app -> W7Row(app, state, onClose) }
                    }
                }
                Column(Modifier.width(120.dp).fillMaxHeight().padding(start = 8.dp, top = 40.dp, end = 0.dp, bottom = 8.dp)) {
                    Spacer(Modifier.weight(1f))
                    // Split button: the caret has no power menu in an app, so both halves close the menu.
                    Row(Modifier.fillMaxWidth().height(28.dp)) {
                        val left = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
                        val right = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(left).background(W7_SHUT).border(1.dp, W7_BORDER, left).clickable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(tr("Shut down", "Kapat"), color = Color.White, fontSize = 12.sp)
                        }
                        Box(
                            Modifier.width(24.dp).fillMaxHeight().clip(right).background(W7_SHUT).border(1.dp, W7_BORDER, right).clickable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(8.dp)) {
                                val p = Path().apply {
                                    moveTo(0f, size.height * .3f)
                                    lineTo(size.width, size.height * .3f)
                                    lineTo(size.width / 2f, size.height)
                                    close()
                                }
                                drawPath(p, Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun W7Row(app: LaunchApp, state: LauncherState, onClose: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).appTap(state, app) { state.open(app); onClose() }.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LaunchAppPicture(app, 24.dp, W7_TILE, Color.White)
            Spacer(Modifier.width(8.dp))
            Text(app.label, color = Color.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, W7_TILE, Color.White)
            Text(app.label, style = W7_LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }

    /** Round orb drawn with solid circles: no Aero sprite, no logo. */
    @Composable
    private fun StartOrb() {
        Canvas(Modifier.size(34.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(W7_ORB_EDGE, r, c)
            drawCircle(W7_ORB, r * .88f, c)
            drawCircle(W7_ORB_HI, r * .42f, Offset(c.x, c.y - r * .12f))
        }
    }

    @Composable
    private fun W7Heading(text: String) {
        Text(text, color = Color(0xFF1B3450), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
    }
}
