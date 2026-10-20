package com.umutk.pulse.extras.launcher

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Windows XP look, solid colours only (the real XP gradients are replaced by flat fills, per the no-gradient rule outside 95/2003). */
class XpLauncherLook : LauncherLook {
    override val id: String = "xp"
    override val nameEn: String = "Windows XP"
    override val nameTr: String = "Windows XP"
    override val taskbarHeight: Dp = 30.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    private val wallpaper = Color(0xFF3A6EA5)
    private val taskbar = Color(0xFF245EDC)
    private val startGreen = Color(0xFF3C9A3C)
    private val tray = Color(0xFF0997FF)
    private val menuRight = Color(0xFFD6DFF7)
    private val menuLeft = Color.White
    private val ink = Color.Black
    private val onBlue = Color.White

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        LauncherWallpaper(id, wallpaper, modifier) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        val clock by produceState(initialValue = "") {
            while (true) {
                value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(1_000)
            }
        }
        // the edge padding keeps the Start button and the tray out of the rounded screen corners
        Row(modifier.fillMaxWidth().height(taskbarHeight).background(taskbar).padding(horizontal = edge), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.fillMaxHeight().clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)).background(startGreen)
                    .clickable { LauncherSession.startOpen = true }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("Start", "Başlat"), color = onBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                state.dock.forEach { app ->
                    Box(Modifier.size(24.dp).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 18.dp, onBlue, taskbar)
                    }
                }
            }
            Box(Modifier.fillMaxHeight().background(tray).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text(clock, color = onBlue, fontSize = 12.sp)
            }
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        val ctx = LocalContext.current
        var query by remember { mutableStateOf("") }
        val (pinned, all) = startLists(state, query)
        val noRipple = remember { MutableInteractionSource() }
        // a tap outside the menu closes it; the menu swallows its own taps
        Box(modifier.clickable(interactionSource = noRipple, indication = null, onClick = onClose)) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = edge, bottom = taskbarHeight)
                    .widthIn(max = 320.dp).fillMaxWidth(.85f).fillMaxHeight(.6f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(menuLeft)
                    .clickable(interactionSource = noRipple, indication = null) {},
            ) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(3.dp)).border(1.dp, taskbar, RoundedCornerShape(3.dp))
                            .background(Color.White).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = ink, fontSize = 13.sp), cursorBrush = SolidColor(ink),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text(tr("Search", "Ara"), color = Color(0xFF888888), fontSize = 13.sp)
                                inner()
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        if (pinned.isNotEmpty()) {
                            item { XpHeading(tr("Pinned", "Sabitlenenler")) }
                            items(pinned) { app -> XpRow(app, state, onClose) }
                        }
                        item { XpHeading(tr("All programs", "Tüm programlar")) }
                        items(all) { app -> XpRow(app, state, onClose) }
                    }
                }
                Column(Modifier.width(120.dp).fillMaxHeight().background(menuRight).padding(6.dp)) {
                    Text(
                        tr("Settings", "Ayarlar"), color = ink, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java)); onClose() }.padding(6.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun XpRow(app: LaunchApp, state: LauncherState, onClose: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().appTap(state, app) { state.open(app); onClose() }.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LaunchAppPicture(app, 24.dp, menuRight, taskbar)
            Text(app.label, color = ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
    }

    @Composable
    private fun XpHeading(text: String) {
        Text(text, color = taskbar, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, onBlue, taskbar)
            Text(app.label, color = onBlue, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
