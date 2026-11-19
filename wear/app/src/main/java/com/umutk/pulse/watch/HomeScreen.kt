package com.umutk.pulse.watch

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.umutk.pulse.watch.apps.Apps
import com.umutk.pulse.watch.ui.Chip
import com.umutk.pulse.watch.ui.Header
import com.umutk.pulse.watch.ui.Page
import com.umutk.pulse.watch.ui.RoundButton
import com.umutk.pulse.watch.ui.T
import com.umutk.pulse.watch.ui.W
import com.umutk.pulse.watch.ui.edge
import kotlinx.coroutines.delay
import java.util.Date

/** The three pages of the home: quick controls (left), the time (middle, where it opens), every app (right). Dots at the bottom show the page. */
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val pager = rememberPagerState(initialPage = 1) { 3 }
    Box(Modifier.fillMaxSize().background(W.bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> QuickPage(onOpen)
                1 -> FacePage(onOpen)
                else -> AppsPage(onOpen)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i -> Box(Modifier.size(if (pager.currentPage == i) 7.dp else 5.dp).clip(CircleShape).background(if (pager.currentPage == i) W.text else W.sub.copy(alpha = .5f))) }
        }
    }
}

/** The time large in the middle, the date under it and the battery as a ring round the edge of the screen. */
@Composable
private fun FacePage(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000L) } }
    val battery by produceState(-1, now / 60_000L) {
        value = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i ->
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) l * 100 / s else -1
        } ?: -1
    }
    // a tap on the face changes it: digital, analog, analog with the digital time under the hands
    var face by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(com.umutk.pulse.watch.apps.Prefs.int("face", 0)) }
    Page(showTime = false) {
        Box(Modifier.fillMaxSize().clickable { face = (face + 1) % 3; com.umutk.pulse.watch.apps.Prefs.putInt("face", face) })
        if (face > 0) Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val c = center; val r = size.minDimension / 2f
            // the hour marks: long at 12, 3, 6 and 9
            for (k in 0 until 60) {
                val a = Math.toRadians(k * 6.0 - 90.0); val long = k % 15 == 0; val hour = k % 5 == 0
                val r1 = r * (if (long) .82f else if (hour) .88f else .94f)
                drawLine(if (hour) W.text else W.sub.copy(alpha = .5f), Offset(c.x + r1 * Math.cos(a).toFloat(), c.y + r1 * Math.sin(a).toFloat()), Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat()), if (hour) 3.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
            }
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val sec = cal.get(java.util.Calendar.SECOND); val min = cal.get(java.util.Calendar.MINUTE) + sec / 60f; val hr = cal.get(java.util.Calendar.HOUR) + min / 60f
            fun hand(deg: Float, len: Float, wd: Float, col: androidx.compose.ui.graphics.Color) {
                val a = Math.toRadians(deg - 90.0)
                drawLine(col, Offset(c.x - r * .1f * Math.cos(a).toFloat(), c.y - r * .1f * Math.sin(a).toFloat()), Offset(c.x + r * len * Math.cos(a).toFloat(), c.y + r * len * Math.sin(a).toFloat()), wd, StrokeCap.Round)
            }
            hand(hr * 30f, .5f, 6.dp.toPx(), W.text)
            hand(min * 6f, .74f, 4.dp.toPx(), W.text)
            hand(sec * 6f, .84f, 1.5.dp.toPx(), W.accent)
            drawCircle(W.accent, 4.dp.toPx(), c)
        }
        // the battery ring: the part that is left, in the accent, round the edge (red under 20 %)
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val w = 5.dp.toPx()
            drawArc(W.card, -90f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
            if (battery >= 0) drawArc(if (battery < 20) W.red else W.accent, -90f, 360f * battery / 100f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        if (face == 1) Column(Modifier.align(Alignment.Center).padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(DateFormat.format("EEE d", now).toString(), color = W.sub, size = 13.sp, align = TextAlign.Center)
        } else Column(Modifier.align(if (face == 2) Alignment.BottomCenter else Alignment.Center).padding(bottom = if (face == 2) 34.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (face == 0) T(DateFormat.getTimeFormat(ctx).format(Date(now)), size = 46.sp, weight = FontWeight.Light, align = TextAlign.Center)
            else T(DateFormat.getTimeFormat(ctx).format(Date(now)), size = 15.sp, weight = FontWeight.Medium, align = TextAlign.Center)
            T(DateFormat.format("EEE d MMM", now).toString(), color = W.sub, size = 15.sp, align = TextAlign.Center)
            if (battery >= 0) T("$battery %", color = if (battery < 20) W.red else W.sub, size = 13.sp, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The quick controls: the apps used most, as big round buttons. */
@Composable
private fun QuickPage(onOpen: (String) -> Unit) {
    Page {
        Column(Modifier.align(Alignment.Center).padding(horizontal = edge()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Quick", color = W.accent, size = 14.sp, weight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton("🔦", size = 54.dp) { onOpen("light") }
                RoundButton("⏱", size = 54.dp) { onOpen("stopwatch") }
                RoundButton("⏲", size = 54.dp) { onOpen("timer") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton("❤", size = 54.dp) { onOpen("heart") }
                RoundButton("🌬", size = 54.dp) { onOpen("breathe") }
                RoundButton("⚙", size = 54.dp) { onOpen("settings") }
            }
        }
    }
}

/** Every app of the watch in a list that bends to the round screen; the crown scrolls it. */
@Composable
private fun AppsPage(onOpen: (String) -> Unit) {
    val state = rememberScalingLazyListState()
    Page {
        // a tap on the title switches between the round icons (three in a row) and the list
        var grid by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.umutk.pulse.watch.apps.Prefs.int("appsGrid", 1) == 1) }
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = state) {
            item { Box(Modifier.clickable { grid = !grid; com.umutk.pulse.watch.apps.Prefs.putInt("appsGrid", if (grid) 1 else 0) }) { Header("Apps") } }
            if (grid) items(Apps.all.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { app ->
                        Column(Modifier.size(width = 50.dp, height = 66.dp).clickable { onOpen(app.route) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(W.accent.copy(alpha = .22f)), contentAlignment = Alignment.Center) { T(app.icon, size = 20.sp) }
                            T(app.name, color = W.sub, size = 9.sp, align = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            } else items(Apps.all) { app -> Chip(app.name, icon = app.icon, secondary = app.about) { onOpen(app.route) } }
        }
    }
}
