package com.umutk.pulse.extras.notifhist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.umutk.pulse.AppIcon
import com.umutk.pulse.Center
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PDialog
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Notification history: saved notifications, newest first, grouped by day. [back] is called by the back arrow. */
@Composable
fun NotifHistoryScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    BackHandler { back() }
    var granted by remember { mutableStateOf(NotifHist.hasAccess(ctx)) }
    var on by remember { mutableStateOf(NotifHist.enabled(ctx)) }
    var query by rememberSaveable { mutableStateOf("") }
    var askClear by remember { mutableStateOf(false) }

    // the user grants access in system settings, so look again when the screen comes back
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = NotifHist.hasAccess(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { NotifHist.load(ctx) }

    val all by NotifHist.entries.collectAsState()
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.app.contains(q, true) || it.title.contains(q, true) || it.text.contains(q, true) }
    }
    val days = remember(shown) { shown.withIndex().groupBy { dayOf(it.value.time) } }

    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(
                tr("Notifications", "Bildirimler"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            if (granted && all.isNotEmpty()) {
                Box(Modifier.size(40.dp).clickable { askClear = true }, contentAlignment = Alignment.Center) { PIcon("delete", T.text, 22.dp) }
            }
        }

        // saving switch: off by default, and nothing is saved while it is off
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PIcon("info", T.text, 22.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Save notifications", "Bildirimleri kaydet"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                PSwitch(on, { on = it; NotifHist.setEnabled(ctx, it) })
            }
        }

        // access: Android 13+ blocks the toggle for sideloaded apps until "Allow restricted settings" is set in App info
        if (!granted) {
            PCard {
                Text(
                    tr("Pulse needs notification access to save the notifications this phone receives.",
                        "Telefonun aldığı bildirimleri kaydetmek için Pulse'a bildirim erişimi gerekir."),
                    color = T.text, fontSize = 14.sp
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    tr("If the switch in notification access is greyed out, allow restricted settings in App info first.",
                        "Bildirim erişimi anahtarı soluksa önce Uygulama bilgisi'nden kısıtlı ayarlara izin ver."),
                    color = T.sub, fontSize = 13.sp
                )
                Spacer(Modifier.size(12.dp))
                PButton(tr("Allow notification access", "Bildirim erişimine izin ver")) {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Spacer(Modifier.size(8.dp))
                PButton(tr("Open App info", "Uygulama bilgisini aç")) { openAppInfo(ctx) }
            }
        }

        PField(query, { query = it }, tr("Search", "Ara"), Modifier.fillMaxWidth().padding(vertical = 8.dp))

        if (all.isEmpty()) Center(tr("No notifications yet", "Henüz bildirim yok"), Modifier.padding(top = 24.dp))
        else if (shown.isEmpty()) Center(tr("Nothing found", "Sonuç yok"), Modifier.padding(top = 24.dp))
        else LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            days.forEach { (day, items) -> dayGroup(ctx, day, items) }
        }
    }

    if (askClear) PDialog(
        title = tr("Clear notification history?", "Bildirim geçmişi silinsin mi?"),
        text = tr("All saved notifications will be removed.", "Kayıtlı tüm bildirimler silinecek."),
        confirm = tr("Clear", "Sil"),
        onConfirm = { NotifHist.clear(ctx); askClear = false },
        onDismiss = { askClear = false },
    )
}

private fun openAppInfo(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun dayOf(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LazyListScope.dayGroup(ctx: Context, day: LocalDate, rows: List<IndexedValue<NotifHistEntry>>) {
    item(key = "day:$day") {
        Text(
            dayLabel(day), color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)
        )
    }
    items(rows, key = { it.index }) { (_, e) ->
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { openApp(ctx, e.pkg) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                AppIcon(e.pkg, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.app, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(TIME.format(Instant.ofEpochMilli(e.time)), color = T.sub, fontSize = 12.sp)
                    }
                    if (e.title.isNotEmpty()) Text(e.title, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.text.isNotEmpty()) Text(e.text, color = T.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = T.line)
        }
    }
}

// tapping a row opens the source app; the notification's own contentIntent is not stored
private fun openApp(ctx: Context, pkg: String) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> tr("Today", "Bugün")
        today.minusDays(1) -> tr("Yesterday", "Dün")
        else -> DATE.format(day)
    }
}
