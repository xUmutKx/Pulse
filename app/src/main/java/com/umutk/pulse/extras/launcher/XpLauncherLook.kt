package com.umutk.pulse.extras.launcher

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.umutk.pulse.produceStateVisible

internal val XP_WINDOW = WinSkin("xp", Brush.horizontalGradient(listOf(Color(0xFF0058EE), Color(0xFF3A93FF), Color(0xFF0058EE))), Color.White, Color(0xFF0831D9), 3.dp, 7.dp,
    Color.White, Color(0xFFD6DFF7), Color.White, Color(0xFF1B1B1B), Color(0xFF5A6A9A), Color(0xFF316AC5), Color.White, Color(0xFF7F9DB9))


/** Windows XP (Luna) look: Luna gradients as Compose brushes, the Start button, flag, tray and icons drawn with Path and Canvas. */
class XpLauncherLook : LauncherLook {
    override val id: String = "xp"
    override val nameEn: String = "Windows XP"
    override val nameTr: String = "Windows XP"
    override val taskbarHeight: Dp = 30.dp
    override val taskbarTop: Boolean = false
    override val ownSearch: Boolean = true

    // Bliss-style sky and grass, used only when the look has no stock picture
    private val sky = Brush.verticalGradient(
        0f to Color(0xFF3D7BD9), 0.55f to Color(0xFFA9CFF5), 0.56f to Color(0xFF5FA53C), 1f to Color(0xFF2E7324),
    )
    private val taskbar = Color(0xFF245EDC)
    private val taskbarBrush = Brush.verticalGradient(
        0f to Color(0xFF3F8CF3), 0.1f to Color(0xFF245EDC), 0.88f to Color(0xFF2056CF), 1f to Color(0xFF1941A5),
    )
    private val startBrush = Brush.verticalGradient(0f to Color(0xFF4DB848), 0.5f to Color(0xFF3A9C38), 1f to Color(0xFF2B7E2A))
    private val trayBrush = Brush.verticalGradient(0f to Color(0xFF1BA1FF), 1f to Color(0xFF0A7BE0))
    private val trayEdge = Color(0xFF0C59C1)
    private val headerBrush = Brush.horizontalGradient(0f to Color(0xFF3B7DE6), 1f to Color(0xFF1A4FBF))
    private val frame = Color(0xFF1E56C8)
    private val avatarBg = Color(0xFF7DB0F5)
    private val menuLeft = Color.White
    private val menuRight = Color(0xFFD3E5FA)
    private val linkBlue = Color(0xFF215DC6)
    private val headingBlue = Color(0xFF215DC6)
    private val separator = Color(0xFFB8CFF0)
    private val searchBorder = Color(0xFF7F9DB9)
    private val folderFill = Color(0xFFF9D65C)
    private val folderEdge = Color(0xFFD29A2A)
    private val gripColor = Color(0xFF9CC3FA)
    private val ink = Color.Black
    private val onBlue = Color.White
    private val userName = "Pulse"

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        LauncherWallpaper(id, sky, modifier) { content() }
    }

    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        val clock by produceStateVisible(initialValue = "") {
            while (true) {
                value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(60_000 - System.currentTimeMillis() % 60_000)   // the clock shows minutes: it is redrawn when the minute changes
            }
        }
        // the edge padding keeps the Start button and the tray out of the rounded screen corners
        Row(modifier.fillMaxWidth().height(taskbarHeight).background(taskbarBrush).padding(horizontal = edge), verticalAlignment = Alignment.CenterVertically) {
            XpStartButton { LauncherSession.startOpen = true }
            XpGrip(Modifier.padding(horizontal = 5.dp).size(3.dp, 20.dp))
            Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                state.dock.forEach { app ->
                    Box(Modifier.size(22.dp).appTap(state, app) { state.open(app) }, contentAlignment = Alignment.Center) {
                        LaunchAppPicture(app, 16.dp, onBlue, taskbar)
                    }
                }
                TaskbarWindows("xp", onBlue, 24.dp)
            }
            Spacer(Modifier.weight(1f).fillMaxHeight())
            Row(
                Modifier.fillMaxHeight().background(trayBrush)
                    .drawBehind { drawLine(color = trayEdge, start = Offset(0f, 0f), end = Offset(0f, size.height)) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                XpSpeaker(Modifier.size(14.dp))
                Text(clock, style = TextStyle(color = onBlue, fontSize = 11.sp))
            }
        }
    }

    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        XpStartMenu(state, onClose, modifier)
    }

    /**
     * The Windows XP start menu as XP drew it: a blue head with the user's picture and name, a white column of the programs
     * the person uses (only these, not every app on the phone: the rest are in the app drawer), a light blue column of links,
     * and the foot with Log Off and Turn Off Computer.
     */
    @Composable
    private fun XpStartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val ctx = LocalContext.current
        val left = listOf("pulse:x:notes", "pulse:x:calculator", "pulse:x:messages", "pulse:x:music", "pulse:x:video", "pulse:x:weather", "pulse:x:voice", "pulse:x:win:notepad")
            .mapNotNull { p -> state.apps.firstOrNull { it.pkg == p } }
        val links = listOf("pulse:x:toolkit", "pulse:x:notifhist", "pulse:x:screenrec")
            .mapNotNull { p -> state.apps.firstOrNull { it.pkg == p } }
        val rightWhite = Color(0xFFD6E4F8)
        Column(
            modifier.widthIn(max = 420.dp).fillMaxWidth(.94f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).border(2.dp, Color(0xFF0831D9), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
        ) {
            // the head: the blue band with the user's picture and name
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF3A93FF), Color(0xFF0058EE)))).padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).border(2.dp, Color.White).background(Color(0xFF2A4F9E)), contentAlignment = Alignment.Center) {
                    XpFlag(Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(userName, style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, shadow = Shadow(Color(0x66000000), Offset(1f, 1f), 2f)))
            }
            Row(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                // the programs the person uses, in white
                Column(Modifier.weight(1f).background(Color.White).padding(vertical = 6.dp, horizontal = 4.dp)) {
                    left.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().appTap(state, app) { state.open(app); onClose() }.padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LaunchAppPicture(app, 30.dp, Color.Transparent, linkBlue)
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // the links, in light blue
                Column(Modifier.weight(1f).background(rightWhite).padding(vertical = 6.dp, horizontal = 8.dp)) {
                    links.forEach { app ->
                        Row(Modifier.fillMaxWidth().appTap(state, app) { state.open(app); onClose() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            LaunchAppPicture(app, 22.dp, Color.Transparent, linkBlue)
                            Spacer(Modifier.width(8.dp))
                            Text(app.label, style = TextStyle(color = linkBlue, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    XpSeparator()
                    Row(Modifier.fillMaxWidth().clickable { runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }; onClose() }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        XpGear(Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Settings", "Ayarlar"), style = TextStyle(color = linkBlue, fontSize = 13.sp))
                    }
                }
            }
            // the foot: the grey-blue band with Log Off and Turn Off Computer
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF4A8DEA), Color(0xFF2357D1)))).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Log Off", "Oturumu kapat"), style = TextStyle(color = Color.White, fontSize = 13.sp), modifier = Modifier.clickable { onClose() }.padding(6.dp))
                Spacer(Modifier.width(12.dp))
                Text(tr("Turn Off Computer", "Bilgisayarı kapat"), style = TextStyle(color = Color.White, fontSize = 13.sp), modifier = Modifier.clickable { onClose() }.padding(6.dp))
            }
        }
    }

    @Composable
    private fun XpRow(app: LaunchApp, state: LauncherState, onClose: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().appTap(state, app) { state.open(app); onClose() }.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LaunchAppPicture(app, 28.dp, menuRight, linkBlue)
            Text(app.label, style = TextStyle(color = ink, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
    }

    @Composable
    private fun XpHeading(text: String) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            XpFolder(Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = TextStyle(color = headingBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun XpSeparator() {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(separator))
    }

    @Composable
    private fun XpSearch(query: String, onChange: (String) -> Unit) {
        Box(Modifier.fillMaxWidth().height(26.dp).border(1.dp, searchBorder).background(Color.White).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XpMagnifier(Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = query, onValueChange = onChange, singleLine = true,
                    textStyle = TextStyle(color = ink, fontSize = 13.sp), cursorBrush = SolidColor(ink),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Composable
    private fun XpLink(label: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            XpGear(Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = TextStyle(color = linkBlue, fontSize = 12.sp))
        }
    }

    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LaunchAppPicture(app, iconDp.dp, Color.Transparent, taskbar)
            Text(
                app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(color = onBlue, fontSize = 12.sp, textAlign = TextAlign.Center, shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 2f)),
            )
        }
    }

    // the green Start button: a curved right edge, the flag and an italic "start"
    @Composable
    private fun XpStartButton(onClick: () -> Unit) {
        Box(Modifier.fillMaxHeight().width(78.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawPath(startShape(size.width, size.height), startBrush) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                XpFlag(Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    tr("start", "başlat"),
                    style = TextStyle(color = onBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, shadow = Shadow(Color(0x99000000), Offset(1f, 1f), 1f)),
                )
            }
        }
    }

    @Composable
    private fun XpFlag(modifier: Modifier) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cw = w * .46f
        val ch = h * .46f
        val skew = cw * .12f
        fun quad(x: Float, y: Float, c: Color) {
            drawPath(Path().apply { moveTo(x + skew, y); lineTo(x + cw, y); lineTo(x + cw - skew, y + ch); lineTo(x, y + ch); close() }, c)
        }
        quad(0f, 0f, Color(0xFFF25022))
        quad(w * .54f, 0f, Color(0xFF7FBA00))
        quad(0f, h * .54f, Color(0xFF00A4EF))
        quad(w * .54f, h * .54f, Color(0xFFFFB900))
    }

    @Composable
    private fun XpGrip(modifier: Modifier) = Canvas(modifier) {
        repeat(3) { i -> drawRect(gripColor, Offset(0f, i * 6.dp.toPx()), Size(2.dp.toPx(), 2.dp.toPx())) }
    }

    @Composable
    private fun XpSpeaker(modifier: Modifier) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawPath(Path().apply { moveTo(0f, h * .35f); lineTo(w * .3f, h * .35f); lineTo(w * .6f, h * .05f); lineTo(w * .6f, h * .95f); lineTo(w * .3f, h * .65f); lineTo(0f, h * .65f); close() }, onBlue)
        drawArc(color = onBlue, startAngle = -50f, sweepAngle = 100f, useCenter = false, topLeft = Offset(w * .5f, h * .2f), size = Size(w * .45f, h * .6f), style = Stroke(1.dp.toPx()))
    }

    @Composable
    private fun XpFolder(modifier: Modifier) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply { moveTo(0f, h * .18f); lineTo(w * .38f, h * .18f); lineTo(w * .48f, h * .3f); lineTo(w, h * .3f); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(p, folderFill)
        drawPath(p, folderEdge, style = Stroke(1.dp.toPx()))
    }

    @Composable
    private fun XpMagnifier(modifier: Modifier) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color = ink, radius = w * .3f, center = Offset(w * .4f, h * .4f), style = Stroke(1.5.dp.toPx()))
        drawLine(color = Color(0xFF8B5A2B), start = Offset(w * .62f, h * .62f), end = Offset(w * .95f, h * .95f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }

    @Composable
    private fun XpGear(modifier: Modifier) = Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        repeat(8) { i ->
            rotate(i * 45f, c) { drawRect(linkBlue, Offset(c.x - r * .14f, c.y - r), Size(r * .28f, r * .4f)) }
        }
        drawCircle(linkBlue, r * .7f, c)
        drawCircle(menuRight, r * .3f, c)
    }

    @Composable
    private fun XpAvatar(modifier: Modifier) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(avatarBg)
        drawCircle(color = Color.White, radius = w * .18f, center = Offset(w * .5f, h * .36f))
        drawPath(Path().apply { moveTo(w * .14f, h); cubicTo(w * .14f, h * .62f, w * .86f, h * .62f, w * .86f, h); close() }, Color.White)
        drawRect(Color.White, style = Stroke(2.dp.toPx()))
    }
}

/** Start button outline: flat left, a rounded right end. */
private fun startShape(w: Float, h: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(w * .7f, 0f)
    cubicTo(w * .95f, 0f, w, h * .25f, w, h * .5f)
    cubicTo(w, h * .75f, w * .95f, h, w * .7f, h)
    lineTo(0f, h)
    close()
}
