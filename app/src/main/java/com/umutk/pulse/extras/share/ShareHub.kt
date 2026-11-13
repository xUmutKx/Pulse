package com.umutk.pulse.extras.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.localsend.LocalSendBubble
import com.umutk.pulse.extras.localsend.LocalSendEngine
import com.umutk.pulse.extras.localsend.Peer
import com.umutk.pulse.extras.localsend.ScanRing
import com.umutk.pulse.extras.relay.RelayHost
import com.umutk.pulse.extras.relay.TransferCard
import com.umutk.pulse.tr
import com.vythera.relay.node.NodeEvent
import com.vythera.relay.node.PairingStage
import com.vythera.relay.node.Presence
import com.vythera.relay.node.RelayDevice
import com.vythera.relay.node.SendResult
import com.vythera.relay.protocol.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What is going to be sent: files (a list of uris), or a text. */
internal class Payload(val uris: List<Uri>, val text: String?) {
    val label: String get() = if (text != null) tr("A text", "Bir metin") else tr("${uris.size} file(s)", "${uris.size} dosya")
}

/** One device of either kind in the one list: a LocalSend peer, or a Relay device. */
internal class Dev(val key: String, val name: String, val sub: String, val ls: Peer?, val rl: RelayDevice?) {
    val ready get() = ls != null || (rl != null && rl.isTrusted)
    val icon: String get() = if (rl != null && (rl.type == DeviceType.PHONE || rl.type == DeviceType.TABLET)) "phone" else if (rl != null) "cpu" else "phone"
}

/**
 * Share: one page for sending to devices near you. LocalSend devices (any phone or computer with LocalSend) and Relay devices (paired ones) are one list; pick what to send
 * (files, photos, a text, the clipboard) and tap a device. Quick Share, and AirDrop through it, open from here too. Everything that arrives and everything that goes is on this page.
 */
@Composable
fun ShareHub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = back)
    val relayOk = remember { RelayHost.supported() }
    var visible by remember { mutableStateOf(true) }
    // both engines run while the page is open and "visible" is on; leaving with a transfer under way keeps LocalSend going behind its bubble
    DisposableEffect(visible) {
        if (visible) { LocalSendEngine.start(ctx); if (relayOk) RelayHost.open(ctx) }
        onDispose { if (!(LocalSendEngine.busy.isNotEmpty() && LocalSendBubble.show(ctx))) LocalSendEngine.stop(); if (relayOk) RelayHost.close(ctx) }
    }
    LaunchedEffect(visible) { while (visible) { LocalSendEngine.announce(); delay(15_000) } }
    val node = remember { if (relayOk) RelayHost.node(ctx) else null }
    val devices: List<RelayDevice> = if (node != null) node.devices.collectAsState().value else emptyList()
    val transfers = if (node != null) node.transfers.collectAsState().value else emptyList()
    val pairings = if (node != null) node.pairingSessions.collectAsState().value else emptyList()
    val running = if (node != null) node.isRunning.collectAsState().value else false
    val texts = remember { mutableStateListOf<Pair<String, String>>() }
    var name by remember { mutableStateOf(RelayHost.deviceName(ctx)) }
    var bg by remember { mutableStateOf(RelayHost.background(ctx)) }
    var clip by remember { mutableStateOf(RelayHost.clipboardSync(ctx)) }
    var payload by remember { mutableStateOf<Payload?>(null) }
    var status by remember { mutableStateOf("") }
    var textDlg by remember { mutableStateOf(false) }
    var textVal by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var forget by remember { mutableStateOf<RelayDevice?>(null) }
    var target by remember { mutableStateOf<Dev?>(null) }
    var more by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { LocalSendEngine.setAlias(ctx, name) }

    fun sendTo(d: Dev, p: Payload) {
        status = ""
        ShareSend.send(ctx, d, p, scope) { status = it }
        payload = null
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) { val t = target; target = null; if (t != null) sendTo(t, Payload(uris, null)) else payload = Payload(uris, null) }
        else target = null
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) { val t = target; target = null; if (t != null) sendTo(t, Payload(uris, null)) else payload = Payload(uris, null) }
        else target = null
    }

    LaunchedEffect(node) {
        val n = node ?: return@LaunchedEffect
        n.events.collect { e ->
            when (e) {
                is NodeEvent.TextReceived -> texts.add(0, (n.devices.value.firstOrNull { it.id == e.from }?.name ?: "") to e.text)
                is NodeEvent.ClipboardReceived -> {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Share", e.clip.text))
                    n.markClipboardApplied(e.clip)
                }
                else -> Unit
            }
        }
    }

    // the devices: LocalSend peers and Relay devices together, by name
    val list = (LocalSendEngine.peers.values.map { p -> Dev("ls:" + p.fingerprint + p.ip, p.alias, listOf("LocalSend", p.model, p.ip).filter { it.isNotEmpty() }.joinToString(" · "), p, null) } +
        devices.map { d ->
            val st = when (d.presence) { Presence.CONNECTED -> tr("Connected", "Bağlı"); Presence.NEARBY -> tr("Nearby", "Yakında"); Presence.OFFLINE -> tr("Not seen", "Görünmüyor") }
            Dev("rl:" + d.id, d.name, listOf("Relay", st, if (d.isTrusted) tr("Paired", "Eşleşti") else tr("Not paired", "Eşleşmedi")).joinToString(" · "), null, d)
        }).sortedBy { it.name.lowercase() }

    // a pairing in progress is a window of its own
    pairings.firstOrNull { !it.stage.isFinished || it.stage is PairingStage.Failed || it.stage is PairingStage.Declined }?.let { s ->
        val st = s.stage
        WinAlert(onDismissRequest = { if (st.isFinished) node?.dismissPairing(s.requestId) },
            title = { Text(tr("Pair with ${s.peerName}", "${s.peerName} ile eşleş"), color = T.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (st) {
                        is PairingStage.Confirm -> { Text(tr("Is this code the same on the other device?", "Bu kod diğer cihazda da aynı mı?"), color = T.sub, fontSize = 14.sp); Text(st.code.grouped, color = T.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
                        is PairingStage.WaitingForPeer -> { Text(tr("Check this code on the other device", "Bu kodu diğer cihazda kontrol et"), color = T.sub, fontSize = 14.sp); Text(st.code.grouped, color = T.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
                        is PairingStage.Completed -> Text(tr("Paired", "Eşleşti"), color = T.text, fontSize = 16.sp)
                        is PairingStage.Declined -> Text(tr("Declined", "Reddedildi"), color = T.text, fontSize = 16.sp)
                        is PairingStage.Failed -> Text(tr("Pairing failed (${st.reason.name.lowercase().replace('_', ' ')})", "Eşleşme olmadı (${st.reason.name.lowercase().replace('_', ' ')})"), color = T.text, fontSize = 16.sp)
                        else -> Text(tr("Connecting…", "Bağlanıyor…"), color = T.sub, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                when (st) {
                    is PairingStage.Confirm -> PButton(tr("Yes, pair", "Evet, eşleş")) { scope.launch { node?.respondToPairing(s.requestId, true) } }
                    else -> if (st.isFinished) PButton("OK") { node?.dismissPairing(s.requestId) } else PButton(tr("Cancel", "İptal")) { scope.launch { node?.cancelPairing(s.requestId) } }
                }
            },
            dismissButton = if (st is PairingStage.Confirm) ({ PButton(tr("No", "Hayır")) { scope.launch { node?.respondToPairing(s.requestId, false) } } }) else null)
    }

    if (textDlg) WinAlert(onDismissRequest = { textDlg = false }, title = { Text(tr("Text or link to send", "Gönderilecek metin veya bağlantı"), color = T.text) },
        text = { PField(textVal, { textVal = it }, tr("Text or link", "Metin veya bağlantı"), Modifier.fillMaxWidth()) },
        confirmButton = { PButton(tr("Next", "İleri")) { if (textVal.isNotBlank()) { payload = Payload(emptyList(), textVal.trim()); textVal = ""; textDlg = false } } },
        dismissButton = { PButton(tr("Clipboard", "Pano")) {
            val c = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            if (c.isNotEmpty()) textVal = c
        } })

    forget?.let { d ->
        WinAlert(onDismissRequest = { forget = null }, title = { Text(tr("Forget ${d.name}?", "${d.name} unutulsun mu?"), color = T.text) },
            text = { Text(tr("It will have to be paired again before it can send or receive.", "Göndermek veya almak için yeniden eşleşmesi gerekir."), color = T.sub, fontSize = 14.sp) },
            confirmButton = { PButton(tr("Forget", "Unut"), danger = true) { scope.launch { node?.forget(d.id) }; forget = null } },
            dismissButton = { PButton(tr("Cancel", "İptal")) { forget = null } })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(tr("Share", "Paylaş"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            ExtraLookIcon()
        }

        // this phone: its name (the same on both networks) and whether others can see it
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Look.accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) { PIcon("phone", Look.accent, 28.dp) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    PField(name, { name = it.take(64); RelayHost.setDeviceName(ctx, name); LocalSendEngine.setAlias(ctx, name) }, tr("Name", "Ad"), Modifier.fillMaxWidth())
                    Text(if (visible) tr("Visible to nearby devices", "Yakındaki cihazlara görünür") else tr("Hidden", "Gizli"), color = if (visible) Look.accent else T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                PSwitch(visible, { visible = it })
            }
        }

        // what to send
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("folder", tr("Files", "Dosya"), { pickFiles.launch("*/*") }),
                Triple("image", tr("Photos", "Fotoğraf"), { pickMedia.launch(arrayOf("image/*", "video/*")) }),
                Triple("document", tr("Text", "Metin"), { textDlg = true }),
                Triple("clipboard", tr("Clipboard", "Pano"), {
                    val c = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    if (c.isNotBlank()) payload = Payload(emptyList(), c) else status = tr("The clipboard is empty", "Pano boş")
                }),
            ).forEach { (icon, label, go) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(T.radius)).background(T.panel).clickable { go() }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PIcon(icon, Look.accent, 26.dp)
                    Text(label, color = T.text, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        payload?.let { p ->
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon("share", Look.accent, 22.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(p.label, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr("Tap a device to send it", "Göndermek için bir cihaza dokun"), color = T.sub, fontSize = 12.sp)
                    }
                    Box(Modifier.size(40.dp).clickable { payload = null }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 20.dp) }
                }
            }
        }
        if (status.isNotEmpty()) Text(status, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))

        // the devices, both kinds
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Nearby devices", "Yakındaki cihazlar"), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (LocalSendEngine.scanning) ScanRing(Modifier.padding(end = 8.dp))
            Box(Modifier.size(40.dp).clickable { LocalSendEngine.announce(); LocalSendEngine.scan() }, contentAlignment = Alignment.Center) { PIcon("rotate", T.text, 22.dp) }
        }
        if (list.isEmpty()) PCard { Text(tr("Nobody found yet. The other device needs LocalSend or Relay open, on the same Wi-Fi.", "Henüz kimse bulunamadı. Diğer cihazda LocalSend veya Relay açık ve aynı Wi-Fi'da olmalı."), color = T.sub, fontSize = 13.sp) }
        list.forEach { d ->
            PCard(Modifier.clickable {
                val p = payload
                when {
                    !d.ready -> { val r = d.rl; if (r != null && node != null) scope.launch { runCatching { node.pair(r.id) } } }
                    p != null -> sendTo(d, p)
                    else -> { target = d; pickFiles.launch("*/*") }
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(T.text.copy(alpha = .08f)), contentAlignment = Alignment.Center) { PIcon(d.icon, if (d.ready) Look.accent else T.sub, 24.dp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(d.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.sub, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (d.ready) PIcon("share", Look.accent, 22.dp)
                    else Text(tr("Pair", "Eşleş"), color = Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                val r = d.rl
                if (r != null && r.isTrusted) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Always accept its files", "Dosyalarını hep kabul et"), Modifier.weight(1f), color = T.text, fontSize = 13.sp)
                    PSwitch(r.autoAcceptTransfers, { on -> scope.launch { node?.setAutoAcceptTransfers(r.id, on) } })
                    Text(tr("Forget", "Unut"), color = Look.accent, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp).clickable { forget = r })
                }
            }
        }

        // Quick Share, and AirDrop with it: the phone's own ways, opened from here
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(T.text.copy(alpha = .08f)), contentAlignment = Alignment.Center) { PIcon("wifi", Look.accent, 24.dp) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Quick Share · AirDrop", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("Android phones, and iPhones through AirDrop (on the iPhone: Everyone for 10 minutes)", "Android telefonlar ve AirDrop ile iPhone'lar (iPhone'da: 10 dakika için herkes)"), color = T.sub, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Receive", "Al"), compact = true) { if (!QuickShare.openReceive(ctx)) Toast.makeText(ctx, tr("Quick Share was not found on this phone", "Bu telefonda Quick Share bulunamadı"), Toast.LENGTH_LONG).show() }
                PButton(tr("Send", "Gönder"), compact = true) {
                    val p = payload
                    if (p == null) pickFiles.launch("*/*").also { target = null; status = tr("Pick files, then tap Send again", "Dosyaları seç, sonra yeniden Gönder'e dokun") }
                    else { QuickShare.send(ctx, p.uris, p.text); payload = null }
                }
            }
        }

        // a device that is not found by itself
        PCard {
            Text(tr("Add a device by address", "Cihazı adresle ekle"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(tr("An IP address (LocalSend), or an address and port like 192.168.1.20:47800, or the invite text of Relay.", "Bir IP adresi (LocalSend), 192.168.1.20:47800 gibi adres ve port ya da Relay'in davet metni."), color = T.sub, fontSize = 12.sp)
            PField(address, { address = it }, "192.168.1.20", Modifier.fillMaxWidth().padding(top = 4.dp))
            PButton(tr("Add", "Ekle"), Modifier.padding(top = 4.dp), compact = true) {
                val a = address.trim(); if (a.isEmpty()) return@PButton
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        val relayPort = a.contains(':') && a.substringAfterLast(':').toIntOrNull() != null && !a.contains(' ')
                        when {
                            relayPort && node != null -> runCatching { node.connectAddress(a.substringBeforeLast(':'), a.substringAfterLast(':').toInt()) }.isSuccess
                            a.startsWith("relay://") && node != null -> runCatching { node.connectInvite(a) }.isSuccess
                            else -> LocalSendEngine.probe(a)
                        }
                    }
                    Toast.makeText(ctx, if (ok) tr("Added", "Eklendi") else tr("Nobody there", "Orada kimse yok"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // what goes and what comes
        if (LocalSendEngine.busy.isNotEmpty()) PCard {
            Text(LocalSendEngine.busy, color = T.text, fontSize = 14.sp)
            LinearProgressIndicator(progress = { LocalSendEngine.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Look.accent)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${(LocalSendEngine.progress * 100).toInt()}%", color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clickable { LocalSendEngine.paused = !LocalSendEngine.paused }, contentAlignment = Alignment.Center) { PIcon(if (LocalSendEngine.paused) "play" else "pause", T.text, 22.dp) }
                Box(Modifier.size(40.dp).clickable { LocalSendEngine.cancel() }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 22.dp) }
                Box(Modifier.size(40.dp).clickable { if (!LocalSendBubble.show(ctx)) status = tr("Allow \"display over other apps\" for Pulse to float the transfer", "Aktarımı yüzdürmek için Pulse'a \"diğer uygulamaların üzerinde göster\" izni ver") }, contentAlignment = Alignment.Center) { PIcon("scale", T.text, 22.dp) }
            }
        }
        if (transfers.isNotEmpty()) Text(tr("Transfers", "Aktarımlar"), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        transfers.sortedByDescending { it.createdAtMillis }.forEach { t -> TransferCard(t, devices) { act -> scope.launch { act() } } }
        if (LocalSendEngine.received.isNotEmpty()) {
            Text(tr("Received", "Alınanlar"), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            LocalSendEngine.received.take(20).forEach { p -> Text(p.substringAfterLast('/'), color = T.text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        if (texts.isNotEmpty()) Text(tr("Received text", "Alınan metin"), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        texts.toList().forEach { g ->
            PCard {
                if (g.first.isNotEmpty()) Text(g.first, color = T.sub, fontSize = 12.sp)
                Text(g.second, color = T.text, fontSize = 14.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton(tr("Copy", "Kopyala"), compact = true) { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Share", g.second)) }
                    if (g.second.startsWith("http://") || g.second.startsWith("https://")) PButton(tr("Open", "Aç"), compact = true) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(g.second)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    PButton(tr("Remove", "Kaldır"), compact = true) { texts.remove(g) }
                }
            }
        }

        // settings
        Row(Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Settings", "Ayarlar"), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 18.dp)
        }
        if (more) PCard {
            if (relayOk) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Stay available when the app is closed", "Uygulama kapalıyken de açık kal"), Modifier.weight(1f), color = T.text, fontSize = 14.sp)
                    PSwitch(bg, { bg = it; RelayHost.setBackground(ctx, it) })
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Take the clipboard of paired devices", "Eşleşen cihazların panosunu al"), Modifier.weight(1f), color = T.text, fontSize = 14.sp)
                    PSwitch(clip, { clip = it; RelayHost.setClipboardSync(ctx, it) })
                }
            }
            Text(tr("Received files: Download/LocalSend and ${if (relayOk) RelayHost.inbox(ctx).path else "-"}", "Alınan dosyalar: Download/LocalSend ve ${if (relayOk) RelayHost.inbox(ctx).path else "-"}"), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            if (LocalSendEngine.note.isNotEmpty()) Text(LocalSendEngine.note, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            if (relayOk) Text(if (running) tr("Relay is on the network", "Relay ağda") else tr("Relay is starting…", "Relay başlıyor…"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    // a LocalSend offer: the files are listed, accept or decline
    LocalSendEngine.incoming?.let { inc ->
        WinAlert(onDismissRequest = { inc.decision.complete(false) },
            title = { Text(tr("${inc.from} wants to send", "${inc.from} dosya göndermek istiyor")) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    inc.files.take(8).forEach { f -> Text(f.name, fontSize = 13.sp, maxLines = 1) }
                    if (inc.files.size > 8) Text("+${inc.files.size - 8}", fontSize = 13.sp)
                }
            },
            confirmButton = { PButton(tr("Accept", "Kabul et")) { inc.decision.complete(true) } },
            dismissButton = { PButton(tr("Decline", "Reddet")) { inc.decision.complete(false) } })
    }
}

/** The phone's own Quick Share (Google's, or Samsung's): opened to receive, or handed the files to send. AirDrop from an iPhone reaches a phone that has it through Quick Share. */
internal object QuickShare {
    private val GMS = "com.google.android.gms"

    /** Opens Quick Share's receive screen. False when the phone has none of the known ones. */
    fun openReceive(c: Context): Boolean {
        val tries = listOf(
            Intent().setClassName(GMS, "com.google.android.gms.nearby.sharing.ReceiveSurfaceActivity"),
            Intent("com.google.android.gms.RECEIVE_NEARBY"),
            Intent().setClassName("com.samsung.android.app.sharelive", "com.samsung.android.app.sharelive.presentation.main.MainActivity"),
            Intent().setClassName(GMS, "com.google.android.gms.nearby.sharing.SettingsActivity"),
        )
        for (i in tries) if (runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return true
        return false
    }

    /** Hands the files (or the text) to Quick Share's share sheet; the system's own share sheet when that is not there. */
    fun send(c: Context, uris: List<Uri>, text: String?) {
        val base = if (text != null) Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        else if (uris.size == 1) Intent(Intent.ACTION_SEND).setType("*/*").putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val direct = Intent(base).setClassName(GMS, "com.google.android.gms.nearby.sharing.ShareSheetActivity")
        if (!runCatching { c.startActivity(direct); true }.getOrDefault(false)) runCatching { c.startActivity(Intent.createChooser(base, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Sends a payload to one device of either kind (shared by the page and the system's share sheet). */
internal object ShareSend {
    fun send(ctx: Context, d: Dev, p: Payload, scope: kotlinx.coroutines.CoroutineScope, report: (String) -> Unit) {
        if (d.ls != null) {
            // a text goes to a LocalSend device as a small text file
            val uris = if (p.text != null) {
                val f = File(ctx.cacheDir, "share/message.txt").apply { parentFile?.mkdirs(); writeText(p.text) }
                listOf(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f))
            } else p.uris
            scope.launch { report(withContext(Dispatchers.IO) { LocalSendEngine.send(ctx, d.ls, uris) }.ifBlank { tr("Sent to ${d.name}", "${d.name} cihazına gönderildi") }) }
        } else if (d.rl != null && RelayHost.supported()) {
            val node = RelayHost.node(ctx)
            if (p.text != null) scope.launch { val r = node.sendText(d.rl.id, p.text); report(if (r is SendResult.Sent) tr("Sent to ${d.name}", "${d.name} cihazına gönderildi") else tr("Could not send", "Gönderilemedi")) }
            else {
                val files = RelayHost.filesOf(ctx, p.uris)
                if (files.isEmpty()) report(tr("Could not read the files", "Dosyalar okunamadı")) else { node.sendFiles(d.rl.id, files); report(tr("Sending to ${d.name}", "${d.name} cihazına gönderiliyor")) }
            }
        }
    }
}
