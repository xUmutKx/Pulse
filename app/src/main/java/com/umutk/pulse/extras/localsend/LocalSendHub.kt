package com.umutk.pulse.extras.localsend

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The loading animation while devices are searched: the Windows 11 progress ring (an arc that grows, shrinks and turns) in Fluent, a plain spinner elsewhere. */
@Composable
internal fun ScanRing(modifier: Modifier = Modifier) {
    val accent = com.umutk.pulse.Look.accent
    val fluent = T.id == "win11" && !T.material
    if (!fluent) { androidx.compose.material3.CircularProgressIndicator(modifier.size(20.dp), color = accent, strokeWidth = 2.dp); return }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ring")
    val turn by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "turn")
    // the arc breathes between a short dash and most of the circle, the way the Windows 11 ring does
    val sweep by t.animateFloat(30f, 250f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "sweep")
    androidx.compose.foundation.Canvas(modifier.size(22.dp)) {
        val w = 2.5.dp.toPx()
        drawArc(accent.copy(alpha = .18f), 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawArc(accent, turn - 90f, sweep, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

private fun sizeText(b: Long) = when { b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0); b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0); b >= 1024 -> "%.0f KB".format(b / 1024.0); else -> "$b B" }

/** LocalSend inside Pulse: nearby devices, send files to one, accept files from another. The server runs while this page is open. */
@Composable
fun LocalSendHub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { back() }
    DisposableEffect(Unit) { LocalSendEngine.start(ctx); onDispose { LocalSendEngine.stop() } }
    // announce again now and then, so devices that joined later find this phone
    LaunchedEffect(Unit) { while (true) { delay(15_000); LocalSendEngine.announce() } }
    var target by remember { mutableStateOf<Peer?>(null) }
    var result by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var nameEdit by remember { mutableStateOf(LocalSendEngine.alias) }
    LaunchedEffect(LocalSendEngine.alias) { if (nameEdit.isEmpty()) nameEdit = LocalSendEngine.alias }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val p = target ?: return@rememberLauncherForActivityResult
        if (uris.isNotEmpty()) scope.launch { result = withContext(Dispatchers.IO) { LocalSendEngine.send(ctx, p, uris) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("LocalSend", color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            ExtraLookIcon()
        }
        PCard {
            Text(tr("This phone", "Bu telefon"), color = T.sub, fontSize = 12.sp)
            PField(nameEdit, { nameEdit = it; LocalSendEngine.setAlias(ctx, it) }, tr("Name", "Ad"), Modifier.fillMaxWidth())
            Text(tr("Visible on port ${LocalSendEngine.PORT} while this page is open", "Bu sayfa açıkken ${LocalSendEngine.PORT} portunda görünür"), color = T.sub, fontSize = 12.sp)
            Text(tr("Received files go to Download/LocalSend", "Gelen dosyalar Download/LocalSend içine gider"), color = T.sub, fontSize = 12.sp)
        }
        if (LocalSendEngine.busy.isNotEmpty()) PCard {
            Text(LocalSendEngine.busy, color = T.text, fontSize = 14.sp)
            androidx.compose.material3.LinearProgressIndicator(progress = { LocalSendEngine.progress }, modifier = Modifier.fillMaxWidth(), color = com.umutk.pulse.Look.accent)
        }
        if (result.isNotEmpty()) Text(result, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Nearby devices", "Yakındaki cihazlar"), color = com.umutk.pulse.Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (LocalSendEngine.scanning) ScanRing(Modifier.padding(end = 8.dp))
            Box(Modifier.size(40.dp).clickable { LocalSendEngine.announce(); LocalSendEngine.scan() }, contentAlignment = Alignment.Center) { PIcon("rotate", T.text, 22.dp) }
        }
        val list = LocalSendEngine.peers.values.sortedBy { it.alias.lowercase() }
        if (list.isEmpty()) Text(tr("Nobody found yet. Both devices need to be on the same Wi-Fi.", "Henüz kimse bulunamadı. İki cihaz da aynı Wi-Fi'da olmalı."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        list.forEach { p ->
            PCard(Modifier.clickable { target = p; pick.launch("*/*") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(if (p.type == "desktop" || p.type == "server") "phone" else "phone", T.text, 26.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.alias, color = T.text, fontSize = 15.sp)
                        Text(listOf(p.model, p.ip).filter { it.isNotEmpty() }.joinToString(" · "), color = T.sub, fontSize = 12.sp)
                    }
                    PIcon("share", com.umutk.pulse.Look.accent, 22.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PField(ip, { ip = it }, tr("Add by IP address", "IP adresiyle ekle"), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            PButton(tr("Add", "Ekle")) { val a = ip.trim(); if (a.isNotEmpty()) scope.launch { result = if (withContext(Dispatchers.IO) { LocalSendEngine.probe(a) }) "" else tr("No LocalSend device at $a", "$a adresinde LocalSend cihazı yok") } }
        }
        if (LocalSendEngine.received.isNotEmpty()) {
            Text(tr("Received", "Alınanlar"), color = com.umutk.pulse.Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            LocalSendEngine.received.take(20).forEach { p -> Text(p.substringAfterLast('/'), color = T.text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        if (LocalSendEngine.note.isNotEmpty()) Text(LocalSendEngine.note, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
    LocalSendEngine.incoming?.let { inc ->
        WinAlert(onDismissRequest = { inc.decision.complete(false) },
            title = { Text(tr("${inc.from} wants to send", "${inc.from} dosya göndermek istiyor")) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    inc.files.take(8).forEach { f -> Text(f.name + "  ·  " + sizeText(f.size), fontSize = 13.sp, maxLines = 1) }
                    if (inc.files.size > 8) Text("+${inc.files.size - 8}", fontSize = 13.sp)
                }
            },
            confirmButton = { PButton(tr("Accept", "Kabul et")) { inc.decision.complete(true) } },
            dismissButton = { PButton(tr("Decline", "Reddet")) { inc.decision.complete(false) } })
    }
}
