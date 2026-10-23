package com.umutk.pulse.extras.launcher

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Live tiles for the Windows 10 and 11 start menus: the time and date, a calendar tile that turns between the day and the month, the battery, the
 * notifications waiting and the day of the year. They refresh while the menu is open.
 */
@Composable
fun LiveTiles(corner: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val battery by produceState(0 to false) {
        while (true) {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
            val charging = (i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
            value = level to charging
            delay(20_000)
        }
    }
    val d = Date(now)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    // the calendar tile turns every few seconds
    val turn = (now / 4000L) % 2L == 0L
    val shape = RoundedCornerShape(corner)
    val notes = LauncherBadges.counts.values.sum()
    @Composable fun tile(w: Float, c: Color, content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth().padding(2.dp).height(88.dp).clip(shape).background(c).padding(10.dp), contentAlignment = Alignment.BottomStart) { content() }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(2f)) { tile(2f, Color(0xFF0078D7)) {
                Column {
                    Text(SimpleDateFormat(if (LauncherPrefs.clock24) "HH:mm" else "h:mm a", Locale.getDefault()).format(d), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(d), color = Color.White.copy(alpha = .85f), fontSize = 12.sp, maxLines = 1)
                }
            } }
            Box(Modifier.weight(1f)) { tile(1f, Color(0xFF107C10)) {
                Crossfade(turn, animationSpec = tween(600), label = "cal") { t ->
                    if (t) Column {
                        Text(cal.get(Calendar.DAY_OF_MONTH).toString(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
                        Text(SimpleDateFormat("EEE", Locale.getDefault()).format(d), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    } else Column {
                        Text(SimpleDateFormat("MMM", Locale.getDefault()).format(d), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                        Text(cal.get(Calendar.YEAR).toString(), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    }
                }
            } }
        }
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { tile(1f, Color(0xFF5C2D91)) {
                Column {
                    Text("${battery.first}%", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    Text(if (battery.second) tr("Charging", "Şarjda") else tr("Battery", "Pil"), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }
            } }
            Box(Modifier.weight(1f)) { tile(1f, Color(0xFFD83B01)) {
                Column {
                    Text(notes.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    Text(tr("Notifications", "Bildirim"), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }
            } }
            Box(Modifier.weight(1f)) { tile(1f, Color(0xFF008272)) {
                Column {
                    Text(cal.get(Calendar.DAY_OF_YEAR).toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    Text(tr("Day of the year", "Yılın günü"), color = Color.White.copy(alpha = .85f), fontSize = 12.sp, maxLines = 1)
                }
            } }
        }
    }
}
