package com.umutk.pulse.features

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.PIcon
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.shownArtist
import com.umutk.pulse.extras.weather.WeatherApi
import com.umutk.pulse.extras.weather.WeatherFmt
import com.umutk.pulse.extras.weather.WeatherStore
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A dashboard of tiles, the way a car's home screen shows them: the clock, the weather, what plays and the battery. In colour (rounded coloured tiles), or with
 * [lines] a black screen with the tiles drawn as white outlines only, for an AMOLED screen.
 */
@Composable
internal fun DashFace(ctx: Context, ink: Color, tick: Long, lines: Boolean) {
    val land = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val gap = 12.dp
    val h24 = StandByPrefs.h24(ctx)
    val now = Date()
    val time = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    val ampm = if (h24) "" else SimpleDateFormat("a", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
    val (level, charging) = batteryInfo(ctx)
    val fc = remember(tick / 60) { WeatherStore.cached(ctx) }
    val imp = WeatherFmt.imperial(ctx)
    val engine = remember { MusicPlayerEngine.get(ctx) }
    val st by engine.state.collectAsState()
    val cur = st.current

    val clock: @Composable (Modifier) -> Unit = { m ->
        DashTile(m, Color(0xFF6D4AE0), lines, ink) { fg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(time, color = fg, fontSize = 54.sp, fontWeight = FontWeight.Light, maxLines = 1)
                        if (ampm.isNotEmpty()) Text(ampm, color = fg.copy(alpha = .8f), fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                    }
                    Text(date, color = fg.copy(alpha = .8f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Dial(fg, Calendar.getInstance(), Modifier.size(84.dp))
            }
        }
    }
    val weather: @Composable (Modifier) -> Unit = { m ->
        DashTile(m, Color(0xFF2F7DE1), lines, ink) { fg ->
            if (fc == null) {
                Text(tr("Weather", "Hava durumu"), color = fg, fontSize = 18.sp)
                Text("—", color = fg.copy(alpha = .7f), fontSize = 40.sp, fontWeight = FontWeight.Light)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(WeatherApi.icon(fc.code), fg, 30.dp)
                    Text(WeatherFmt.temp(fc.temp, imp), color = fg, fontSize = 44.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 10.dp))
                }
                Text(WeatherApi.condition(fc.code), color = fg.copy(alpha = .85f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                fc.days.firstOrNull()?.let { d -> Text("${WeatherFmt.temp(d.max, imp)}  /  ${WeatherFmt.temp(d.min, imp)}", color = fg.copy(alpha = .7f), fontSize = 14.sp) }
            }
        }
    }
    val media: @Composable (Modifier) -> Unit = { m ->
        DashTile(m, Color(0xFF26262B), lines, ink) { fg ->
            Text(cur?.let { it.title ?: it.path.substringAfterLast('/') } ?: tr("Nothing playing", "Çalan bir şey yok"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cur?.shownArtist().orEmpty(), color = fg.copy(alpha = .7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { engine.previous() }, contentAlignment = Alignment.Center) { PIcon("back", fg, 24.dp) }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(fg.copy(alpha = .18f)).clickable { engine.togglePlayPause() }, contentAlignment = Alignment.Center) { PIcon(if (st.isPlaying) "pause" else "play", fg, 26.dp) }
                Box(Modifier.size(40.dp).clickable { engine.next() }, contentAlignment = Alignment.Center) { PIcon("chevron", fg, 24.dp) }
            }
        }
    }
    val battery: @Composable (Modifier) -> Unit = { m ->
        DashTile(m, Color(0xFF1F6F54), lines, ink) { fg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 8.dp.toPx(); val r = size.minDimension / 2f - w
                        drawCircle(fg.copy(alpha = .2f), r, center, style = Stroke(w))
                        drawArc(fg, -90f, 360f * level / 100f, false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    if (charging) Text("⚡", color = fg, fontSize = 20.sp)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("$level%", color = fg, fontSize = 40.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    Text(if (charging) tr("Charging", "Şarj oluyor") else tr("Battery", "Pil"), color = fg.copy(alpha = .75f), fontSize = 14.sp)
                }
            }
        }
    }

    if (land) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            clock(Modifier.weight(1f).fillMaxWidth()); media(Modifier.weight(1f).fillMaxWidth())
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            weather(Modifier.weight(1f).fillMaxWidth()); battery(Modifier.weight(1f).fillMaxWidth())
        }
    } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            clock(Modifier.weight(1.4f).fillMaxHeight()); weather(Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            media(Modifier.weight(1.4f).fillMaxHeight()); battery(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** One tile: a coloured rounded block, or with [lines] only its outline in the cover's colour. [content] gets the colour its text is drawn in. */
@Composable
private fun DashTile(modifier: Modifier, fill: Color, lines: Boolean, ink: Color, content: @Composable ColumnScope.(Color) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val fg = if (lines) ink else Color.White
    Column(
        modifier.clip(shape).then(if (lines) Modifier.border(1.5.dp, ink, shape) else Modifier.background(fill, shape)).padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) { content(fg) }
}

/** A small round clock face: twelve dots and two hands. */
@Composable
private fun Dial(color: Color, cal: Calendar, modifier: Modifier) {
    Canvas(modifier) {
        val c = center; val r = size.minDimension / 2f
        for (i in 0 until 12) {
            val a = i * 30.0 * PI / 180.0
            drawCircle(color.copy(alpha = if (i % 3 == 0) .9f else .45f), r * .05f, Offset(c.x + (r * .88f * sin(a)).toFloat(), c.y - (r * .88f * cos(a)).toFloat()))
        }
        val h = (cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f) * 30f
        val m = cal.get(Calendar.MINUTE) * 6f
        fun hand(deg: Float, len: Float, w: Float) {
            val a = deg * PI / 180.0
            drawLine(color, c, Offset(c.x + (r * len * sin(a)).toFloat(), c.y - (r * len * cos(a)).toFloat()), strokeWidth = w, cap = StrokeCap.Round)
        }
        hand(h, .5f, r * .09f); hand(m, .78f, r * .06f)
    }
}

/**
 * The always-on look: a small, plain digital clock at the top and the notifications that are on the phone under it (the icons of their apps in a row, then
 * their titles). The notifications need the notification access; without it the clock stands alone with a note.
 */
@Composable
internal fun AodFace(ctx: Context, ink: Color, tick: Long) {
    val h24 = StandByPrefs.h24(ctx)
    val now = Date()
    val time = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
    val granted = remember(tick / 10) { NotifLog.isGranted(ctx) }
    val items = remember(tick / 3) { NotifLog.active().take(6) }
    Column(Modifier.fillMaxSize().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = ink, fontSize = 64.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(date, color = ink.copy(alpha = .7f), fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        if (!granted) {
            Text(tr("Allow notification access to see them here", "Bildirimleri burada görmek için bildirim erişimine izin ver"), color = ink.copy(alpha = .5f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items.map { it.pkg }.distinct().take(8).forEach { pkg -> AppDot(ctx, pkg, ink) } }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.take(4).forEach { n -> Text(n.title, color = ink.copy(alpha = .75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** The icon of an app as a plain one-colour shape in the cover's colour, the way an always-on screen draws them. */
@Composable
private fun AppDot(ctx: Context, pkg: String, ink: Color) {
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(28.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(ink)) } }
}
