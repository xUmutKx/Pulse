package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.util.Calendar

/** Windows 95 look: flat grey faces, 2 px raised/sunken bevels drawn with lines, solid teal desktop. Opaque, no gradients. */
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
        CompositionLocalProvider(LocalContentColor provides W95.Black) {
            Row(
                modifier.fillMaxWidth().height(taskbarHeight).background(W95.Face).drawBehind {
                    // top edge: 1 px #DFDFDF then 1 px #FFFFFF
                    val p = 1.dp.toPx()
                    drawRect(W95.Light, Offset.Zero, Size(size.width, p))
                    drawRect(W95.Hi, Offset(0f, p), Size(size.width, p))
                }.padding(horizontal = edge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(75.dp).fillMaxHeight().w95Bevel(raised = true)
                        .clickable { LauncherSession.startOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Start", "Başlat"), color = W95.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                state.dock.forEach { app ->
                    Box(Modifier.size(30.dp).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 20.dp, W95.Face, W95.Black)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(75.dp).fillMaxHeight().w95Bevel(raised = false), contentAlignment = Alignment.Center) {
                    Text(w95Clock(), color = W95.Black, fontSize = 12.sp)
                }
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
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = edge, bottom = taskbarHeight)
                    .width(240.dp).heightIn(max = 420.dp)
                    .w95Bevel(raised = true).background(W95.Face).padding(3.dp)
                    .clickable(interactionSource = noRipple, indication = null) {},
            ) {
                // search: a sunken white field
                Box(
                    Modifier.fillMaxWidth().height(24.dp).w95Bevel(raised = false).background(Color.White).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = W95.Black, fontSize = 12.sp), cursorBrush = SolidColor(W95.Black),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(tr("Search", "Ara"), color = W95.Shadow, fontSize = 12.sp)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.height(3.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    if (pinned.isNotEmpty()) {
                        item { W95Heading(tr("Pinned", "Sabitlenenler")) }
                        items(pinned) { app -> W95Row(app, state, onClose) }
                    }
                    item { W95Heading(tr("Programs", "Programlar")) }
                    items(all) { app -> W95Row(app, state, onClose) }
                }
            }
        }
    }

    @Composable
    private fun W95Row(app: LaunchApp, state: LauncherState, onClose: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().height(37.dp).appTap(state, app) { state.open(app); onClose() }.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            LaunchAppPicture(app, 25.dp, W95.Face, W95.Black)
            Spacer(Modifier.width(12.dp))
            Text(app.label, color = W95.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun W95Heading(text: String) {
        Text(text, color = W95.Shadow, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Column(modifier.width(75.dp).padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(75.dp).height(50.dp), contentAlignment = Alignment.BottomCenter) {
                LaunchAppPicture(app, iconDp.dp, W95.Face, W95.Black)
            }
            Text(
                app.label,
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

private object W95 {
    val Face = Color(0xFFC0C0C0)
    val Light = Color(0xFFDFDFDF)
    val Hi = Color.White
    val Shadow = Color(0xFF808080)
    val Black = Color.Black
    val Desktop = Color(0xFF008080)
}

/** 2 px bevel drawn as lines: raised = light top-left, dark bottom-right; sunken = the reverse. */
private fun Modifier.w95Bevel(raised: Boolean): Modifier = drawBehind {
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
