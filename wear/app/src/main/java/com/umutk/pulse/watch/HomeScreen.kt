package com.umutk.pulse.watch

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    Page(showTime = false) {
        // the battery ring: the part that is left, in the accent, round the edge (red under 20 %)
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val w = 5.dp.toPx()
            drawArc(W.card, -90f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
            if (battery >= 0) drawArc(if (battery < 20) W.red else W.accent, -90f, 360f * battery / 100f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            T(DateFormat.getTimeFormat(ctx).format(Date(now)), size = 46.sp, weight = FontWeight.Light, align = TextAlign.Center)
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
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = state) {
            item { Header("Apps") }
            items(Apps.all) { app -> Chip(app.name, icon = app.icon, secondary = app.about) { onOpen(app.route) } }
        }
    }
}
