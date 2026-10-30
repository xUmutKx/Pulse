package com.umutk.pulse.extras.relay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import com.vythera.relay.node.NodeEvent
import com.vythera.relay.node.PairingStage
import com.vythera.relay.node.Presence
import com.vythera.relay.node.RelayDevice
import com.vythera.relay.node.SendResult
import com.vythera.relay.protocol.DeviceType
import com.vythera.relay.transfer.TransferDirection
import com.vythera.relay.transfer.TransferSnapshot
import com.vythera.relay.transfer.TransferStatus
import kotlinx.coroutines.launch

private class GotText(val from: String, val text: String)

/** Relay: devices on the same Wi-Fi (the Relay desktop app, or another phone), pairing, sending files and text, and what arrives. */
@Composable
fun RelayScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = back)
    if (!RelayHost.supported()) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            PHead("Relay")
            Text(tr("Relay needs Android 10 or newer.", "Relay için Android 10 veya yenisi gerekir."), color = T.sub, fontSize = 14.sp)
        }
        return
    }
    val node = remember { RelayHost.node(ctx) }
    DisposableEffect(Unit) { RelayHost.open(ctx); onDispose { RelayHost.close(ctx) } }
    val scope = rememberCoroutineScope()
    val devices by node.devices.collectAsState()
    val transfers by node.transfers.collectAsState()
    val pairings by node.pairingSessions.collectAsState()
    val running by node.isRunning.collectAsState()
    val texts = remember { mutableStateListOf<GotText>() }
    var bg by remember { mutableStateOf(RelayHost.background(ctx)) }
    var clip by remember { mutableStateOf(RelayHost.clipboardSync(ctx)) }
    var name by remember { mutableStateOf(RelayHost.deviceName(ctx)) }
    var renameDlg by remember { mutableStateOf(false) }
    var textTo by remember { mutableStateOf<RelayDevice?>(null) }
    var textVal by remember { mutableStateOf("") }
    var forgetDlg by remember { mutableStateOf<RelayDevice?>(null) }
    var address by remember { mutableStateOf("") }
    var sendTo by remember { mutableStateOf<RelayDevice?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val to = sendTo; sendTo = null
        if (to != null && uris.isNotEmpty()) {
            val files = RelayHost.filesOf(ctx, uris)
            if (files.isEmpty()) Toast.makeText(ctx, tr("Could not read the files", "Dosyalar okunamadı"), Toast.LENGTH_SHORT).show()
            else node.sendFiles(to.id, files)
        }
    }

    LaunchedEffect(node) {
        node.events.collect { e ->
            when (e) {
                is NodeEvent.TextReceived -> texts.add(0, GotText(devices.firstOrNull { it.id == e.from }?.name ?: "", e.text))
                is NodeEvent.ClipboardReceived -> {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Relay", e.clip.text))
                    node.markClipboardApplied(e.clip)
                }
                else -> Unit
            }
        }
    }

    // a pairing in progress is a window of its own
    pairings.firstOrNull { !it.stage.isFinished || it.stage is PairingStage.Failed || it.stage is PairingStage.Declined }?.let { s ->
        val st = s.stage
        WinAlert(onDismissRequest = { if (st.isFinished) node.dismissPairing(s.requestId) },
            title = { Text(tr("Pair with ${s.peerName}", "${s.peerName} ile eşleş"), color = T.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (st) {
                        is PairingStage.Confirm -> { Text(tr("Is this code the same on the other device?", "Bu kod diğer cihazda da aynı mı?"), color = T.sub, fontSize = 14.sp); Text(st.code.grouped, color = T.text, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
                        is PairingStage.WaitingForPeer -> { Text(tr("Check this code on the other device", "Bu kodu diğer cihazda kontrol et"), color = T.sub, fontSize = 14.sp); Text(st.code.grouped, color = T.text, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
                        is PairingStage.Completed -> Text(tr("Paired", "Eşleşti"), color = T.text, fontSize = 16.sp)
                        is PairingStage.Declined -> Text(tr("Declined", "Reddedildi"), color = T.text, fontSize = 16.sp)
                        is PairingStage.Failed -> Text(tr("Pairing failed (${st.reason.name.lowercase().replace('_', ' ')})", "Eşleşme olmadı (${st.reason.name.lowercase().replace('_', ' ')})"), color = T.text, fontSize = 14.sp)
                        else -> Text(tr("Connecting…", "Bağlanıyor…"), color = T.sub, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                when (st) {
                    is PairingStage.Confirm -> PButton(tr("Yes, pair", "Evet, eşleş")) { scope.launch { node.respondToPairing(s.requestId, true) } }
                    else -> if (st.isFinished) PButton("OK") { node.dismissPairing(s.requestId) } else PButton(tr("Cancel", "İptal")) { scope.launch { node.cancelPairing(s.requestId) } }
                }
            },
            dismissButton = if (st is PairingStage.Confirm) ({ PButton(tr("No", "Hayır")) { scope.launch { node.respondToPairing(s.requestId, false) } } }) else null)
    }

    if (renameDlg) WinAlert(onDismissRequest = { renameDlg = false }, title = { Text(tr("Device name", "Cihaz adı"), color = T.text) },
        text = { PField(name, { name = it.take(64) }, tr("Name", "Ad"), Modifier.fillMaxWidth()) },
        confirmButton = { PButton("OK") { RelayHost.setDeviceName(ctx, name); name = RelayHost.deviceName(ctx); renameDlg = false } },
        dismissButton = { PButton(tr("Cancel", "İptal")) { renameDlg = false } })

    textTo?.let { to ->
        WinAlert(onDismissRequest = { textTo = null }, title = { Text(tr("Send text to ${to.name}", "${to.name} cihazına metin gönder"), color = T.text) },
            text = { PField(textVal, { textVal = it }, tr("Text or link", "Metin veya bağlantı"), Modifier.fillMaxWidth()) },
            confirmButton = { PButton(tr("Send", "Gönder")) {
                val t = textVal; textVal = ""; textTo = null
                scope.launch { val r = node.sendText(to.id, t); if (r !is SendResult.Sent) Toast.makeText(ctx, tr("Could not send", "Gönderilemedi"), Toast.LENGTH_SHORT).show() }
            } },
            dismissButton = { PButton(tr("Clipboard", "Pano")) {
                val c = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                if (c.isNotEmpty()) textVal = c
            } })
    }

    forgetDlg?.let { d ->
        WinAlert(onDismissRequest = { forgetDlg = null }, title = { Text(tr("Forget ${d.name}?", "${d.name} unutulsun mu?"), color = T.text) },
            text = { Text(tr("It will have to be paired again before it can send or receive.", "Göndermek veya almak için yeniden eşleşmesi gerekir."), color = T.sub, fontSize = 14.sp) },
            confirmButton = { PButton(tr("Forget", "Unut"), danger = true) { scope.launch { node.forget(d.id) }; forgetDlg = null } },
            dismissButton = { PButton(tr("Cancel", "İptal")) { forgetDlg = null } })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PHead("Relay", if (running) tr("On the network", "Ağda") else tr("Starting…", "Başlıyor…"))
        PCard {
            Row(Modifier.fillMaxWidth().clickable { renameDlg = true }, verticalAlignment = Alignment.CenterVertically) {
                PIcon("phone", Look.accent, 24.dp)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(RelayHost.deviceName(ctx), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("This device, seen by others under this name", "Bu cihaz, diğerlerine bu adla görünür"), color = T.sub, fontSize = 12.sp)
                }
                PIcon("edit", T.sub, 18.dp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Stay available when the app is closed", "Uygulama kapalıyken de açık kal"), Modifier.weight(1f), color = T.text, fontSize = 14.sp)
                PSwitch(bg, { bg = it; RelayHost.setBackground(ctx, it) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Take the clipboard of paired devices", "Eşleşen cihazların panosunu al"), Modifier.weight(1f), color = T.text, fontSize = 14.sp)
                PSwitch(clip, { clip = it; RelayHost.setClipboardSync(ctx, it) })
            }
            Text(tr("Received files: ${RelayHost.inbox(ctx).path}", "Alınan dosyalar: ${RelayHost.inbox(ctx).path}"), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Text(tr("Devices", "Cihazlar"), color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        if (devices.isEmpty()) PCard { Text(tr("No device yet. Open Relay on the other device, on the same Wi-Fi.", "Henüz cihaz yok. Aynı Wi-Fi'da diğer cihazda Relay'i aç."), color = T.sub, fontSize = 13.sp) }
        devices.forEach { d ->
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(if (d.type == DeviceType.PHONE || d.type == DeviceType.TABLET) "phone" else "cpu", if (d.isReachable) Look.accent else T.sub, 26.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(d.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(when (d.presence) { Presence.CONNECTED -> tr("Connected", "Bağlı"); Presence.NEARBY -> tr("Nearby", "Yakında"); Presence.OFFLINE -> tr("Not seen", "Görünmüyor") },
                            if (d.isTrusted) tr("Paired", "Eşleşti") else tr("Not paired", "Eşleşmedi"), d.platform.wireName).joinToString(" · "), color = T.sub, fontSize = 12.sp)
                    }
                }
                if (!d.isCompatible) Text(tr("Update Relay on that device", "O cihazda Relay'i güncelle"), color = T.sub, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!d.isTrusted) PButton(tr("Pair", "Eşleş"), compact = true) { scope.launch { runCatching { node.pair(d.id) } } }
                    else {
                        PButton(tr("Send files", "Dosya gönder"), compact = true) { sendTo = d; pick.launch("*/*") }
                        PButton(tr("Send text", "Metin gönder"), compact = true) { textTo = d }
                        PButton(tr("Forget", "Unut"), compact = true, danger = true) { forgetDlg = d }
                    }
                }
                if (d.isTrusted) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Always accept its files", "Dosyalarını hep kabul et"), Modifier.weight(1f), color = T.text, fontSize = 13.sp)
                    PSwitch(d.autoAcceptTransfers, { on -> scope.launch { node.setAutoAcceptTransfers(d.id, on) } })
                }
            }
        }
        PCard {
            Text(tr("Connect by address", "Adresle bağlan"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(tr("When the device is not found by itself: its address and port, like 192.168.1.20:47800, or the invite text of the other app.", "Cihaz kendiliğinden bulunmazsa: adresi ve portu, 192.168.1.20:47800 gibi, ya da diğer uygulamanın davet metni."), color = T.sub, fontSize = 12.sp)
            PField(address, { address = it }, "192.168.1.20:47800", Modifier.fillMaxWidth().padding(top = 4.dp))
            PButton(tr("Connect", "Bağlan"), Modifier.padding(top = 4.dp), compact = true) {
                val a = address.trim(); if (a.isEmpty()) return@PButton
                scope.launch {
                    val ok = runCatching {
                        if (a.contains(':') && a.substringAfterLast(':').toIntOrNull() != null && !a.contains(' ')) node.connectAddress(a.substringBeforeLast(':'), a.substringAfterLast(':').toInt())
                        else node.connectInvite(a)
                    }.isSuccess
                    Toast.makeText(ctx, if (ok) tr("Connected", "Bağlandı") else tr("Could not connect", "Bağlanılamadı"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (transfers.isNotEmpty()) Text(tr("Transfers", "Aktarımlar"), color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        transfers.sortedByDescending { it.createdAtMillis }.forEach { t -> TransferCard(t, devices) { act -> scope.launch { act() } } }

        if (texts.isNotEmpty()) Text(tr("Received text", "Alınan metin"), color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        texts.forEach { g ->
            PCard {
                if (g.from.isNotEmpty()) Text(g.from, color = T.sub, fontSize = 12.sp)
                Text(g.text, color = T.text, fontSize = 14.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton(tr("Copy", "Kopyala"), compact = true) { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Relay", g.text)) }
                    if (g.text.startsWith("http://") || g.text.startsWith("https://")) PButton(tr("Open", "Aç"), compact = true) {
                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(g.text)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    PButton(tr("Remove", "Kaldır"), compact = true) { texts.remove(g) }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun TransferCard(t: TransferSnapshot, devices: List<RelayDevice>, run: (suspend () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val node = remember { RelayHost.node(ctx) }
    val peer = devices.firstOrNull { it.id == t.peerId }?.name ?: tr("Device", "Cihaz")
    val out = t.direction == TransferDirection.OUTGOING
    val st = t.status
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PIcon(if (out) "up" else "down", Look.accent, 22.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text((if (out) tr("To ", "Alıcı: ") else tr("From ", "Gönderen: ")) + peer, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(t.items.take(3).joinToString(", ") { it.name } + if (t.items.size > 3) " +${t.items.size - 3}" else "", color = T.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val label = when (st) {
            TransferStatus.AwaitingDecision -> if (out) tr("Waiting for the other device", "Diğer cihaz bekleniyor") else tr("Wants to send you ${t.items.size} file(s), ${com.umutk.pulse.features.exSize(t.totalBytes)}", "${t.items.size} dosya göndermek istiyor, ${com.umutk.pulse.features.exSize(t.totalBytes)}")
            TransferStatus.Queued -> tr("Queued", "Sırada")
            TransferStatus.Running -> "${(t.progress.fraction * 100).toInt()}%  ·  ${com.umutk.pulse.features.exSize(t.progress.bytesPerSecond)}/s"
            is TransferStatus.Paused -> tr("Paused", "Duraklatıldı")
            TransferStatus.Completed -> tr("Done", "Bitti")
            TransferStatus.Declined -> tr("Declined", "Reddedildi")
            is TransferStatus.Cancelled -> tr("Cancelled", "İptal edildi")
            is TransferStatus.Failed -> tr("Failed (${st.reason.name.lowercase().replace('_', ' ')})", "Olmadı (${st.reason.name.lowercase().replace('_', ' ')})")
        }
        Text(label, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (st.isActive) LinearProgressIndicator(progress = { t.progress.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Look.accent)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                st == TransferStatus.AwaitingDecision && !out -> { PButton(tr("Accept", "Kabul et"), compact = true) { run { node.acceptTransfer(t.id) } }; PButton(tr("Decline", "Reddet"), compact = true) { run { node.declineTransfer(t.id) } } }
                st == TransferStatus.Running -> { PButton(tr("Pause", "Duraklat"), compact = true) { run { node.pauseTransfer(t.id) } }; PButton(tr("Cancel", "İptal"), compact = true) { run { node.cancelTransfer(t.id) } } }
                st is TransferStatus.Paused -> { PButton(tr("Resume", "Sürdür"), compact = true) { run { node.resumeTransfer(t.id) } }; PButton(tr("Cancel", "İptal"), compact = true) { run { node.cancelTransfer(t.id) } } }
                st == TransferStatus.AwaitingDecision || st == TransferStatus.Queued -> PButton(tr("Cancel", "İptal"), compact = true) { run { node.cancelTransfer(t.id) } }
                st.isFinished -> {
                    if (st is TransferStatus.Failed && out) PButton(tr("Retry", "Yeniden dene"), compact = true) { node.retryTransfer(t.id) }
                    if (st == TransferStatus.Completed && !out) t.storedFiles.firstOrNull()?.let { f -> PButton(tr("Open", "Aç"), compact = true) { com.umutk.pulse.features.exHandOver(ctx, java.io.File(f.location), false) } }
                    PButton(tr("Dismiss", "Kaldır"), compact = true) { node.dismissTransfer(t.id) }
                }
            }
        }
    }
}
