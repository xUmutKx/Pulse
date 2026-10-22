package com.umutk.pulse.extras.launcher

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private class FlyLook(val bg: Color, val ink: Color, val sub: Color, val edge: Color, val hi: Color, val corner: Dp, val tiles: Boolean)

private fun flyLook(id: String) = when (id) {
    "win7" -> FlyLook(Color(0xF02B4A6B), Color.White, Color(0xFFC8D8EA), Color(0xFF7FA3CC), Color(0xFF3E78B3), 6.dp, false)
    "xp" -> FlyLook(Color(0xFFECE9D8), Color.Black, Color(0xFF404040), Color(0xFF0831D9), Color(0xFF316AC5), 3.dp, false)
    "win95" -> FlyLook(Color(0xFFC0C0C0), Color.Black, Color(0xFF404040), Color(0xFF808080), Color(0xFF000080), 0.dp, false)
    "win10", "win10black" -> FlyLook(Color(0xFF1F1F1F), Color.White, Color(0xFFB0B0B0), Color(0xFF3A3A3A), Color(0xFF0078D7), 0.dp, true)
    else -> FlyLook(Color(0xF22B2B2B), Color.White, Color(0xFFC8C8C8), Color(0xFF454545), Color(0xFF4CC2FF), 10.dp, true)
}

/**
 * What the taskbar clock opens: the time and date, a month calendar and, in Windows 10 and 11, the quick tiles and a volume slider (the notification
 * area is empty: Pulse's launcher has no notifications of its own). A tap outside closes it.
 */
@Composable
fun ClockFlyout(lookId: String, taskbar: Dp) {
    if (!LauncherSession.clockOpen) return
    val ctx = LocalContext.current
    val look = remember(lookId) { flyLook(lookId) }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val none = remember { MutableInteractionSource() }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { LauncherSession.clockOpen = false }) {
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 6.dp, bottom = taskbar + 6.dp).widthIn(max = 330.dp).fillMaxWidth(.92f)
                .clip(RoundedCornerShape(look.corner)).background(look.bg).border(1.dp, look.edge, RoundedCornerShape(look.corner))
                .clickable(interactionSource = none, indication = null) {}.padding(14.dp),
        ) {
            if (look.tiles) {
                // quick tiles: the first eight, each opens its system page
                QUICK.take(8).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { t ->
                            Column(Modifier.weight(1f).aspectRatio(1.15f).clip(RoundedCornerShape(look.corner.coerceAtMost(6.dp))).background(Color(0x33FFFFFF)).clickable { t.run(ctx) }.padding(6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(t.icon, null, tint = look.ink, modifier = Modifier.size(20.dp))
                                Text(tr(t.en, t.trName), color = look.ink, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                VolumeRow(ctx, look)
                Spacer(Modifier.height(10.dp))
                Text(tr("No new notifications", "Yeni bildirim yok"), color = look.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now)), color = look.ink, fontSize = 34.sp, fontWeight = FontWeight.Light)
            Text(SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(now)), color = look.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            MonthGrid(cal, look)
        }
    }
}

@Composable
private fun VolumeRow(ctx: Context, look: FlyLook) {
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    Slider(v, { v = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0) }, colors = SliderDefaults.colors(thumbColor = look.hi, activeTrackColor = look.hi, inactiveTrackColor = Color(0x44FFFFFF)))
}

/** The month of [cal] as a grid, the week starting on the locale's first day, today marked. */
@Composable
private fun MonthGrid(cal: Calendar, look: FlyLook) {
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val start = (first.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val head = (0 until 7).map { i -> SimpleDateFormat("EEEEE", Locale.getDefault()).format((first.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, (cal.firstDayOfWeek - 1 + i) % 7 + 1) }.time) }
    Text(SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(cal.time), color = look.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
    Row(Modifier.fillMaxWidth()) { head.forEach { Text(it, color = look.sub, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
    val cells = List(start) { 0 } + (1..days).toList()
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            week.forEach { d ->
                Box(Modifier.weight(1f).aspectRatio(1.3f), contentAlignment = Alignment.Center) {
                    if (d != 0) {
                        val me = d == today
                        Box(Modifier.size(26.dp).then(if (me) Modifier.clip(CircleShape).background(look.hi) else Modifier), contentAlignment = Alignment.Center) {
                            Text("$d", color = if (me) Color.White else look.ink, fontSize = 12.sp)
                        }
                    }
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
