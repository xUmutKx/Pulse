package com.umutk.pulse.extras.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.AppLookGuard
import com.umutk.pulse.Cfg
import com.umutk.pulse.Look
import com.umutk.pulse.LocalSendLook
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.localsend.LocalSendBubble
import com.umutk.pulse.extras.localsend.LocalSendEngine
import com.umutk.pulse.extras.localsend.ScanRing
import com.umutk.pulse.extras.relay.RelayHost
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/**
 * "Pulse · Share" in the system's Share list: the devices that LocalSend and Relay find are one list here, and a tap sends what was shared (files, a text or a link) to one of them;
 * Quick Share (and AirDrop with it) is the last row. It keeps the look of the Share app.
 */
class ShareTargetActivity : ComponentActivity() {
    private var payload: Payload? = null
    private var relayOpened = false

    override fun onResume() { LocalSendLook.apply(this); super.onResume() }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        LocalSendLook.apply(this)
        val i = intent
        val uris = when (i.action) {
            Intent.ACTION_SEND_MULTIPLE -> androidx.core.content.IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
        }
        val text = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        if (uris.isEmpty() && text == null) { finish(); return }
        payload = if (uris.isNotEmpty()) Payload(uris, null) else Payload(emptyList(), text)
        LocalSendEngine.start(this)
        if (RelayHost.supported()) { RelayHost.open(this); relayOpened = true }
        setContent { LookHost(transparent = true) { Panel() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (LocalSendEngine.busy.isEmpty()) LocalSendEngine.stop()
        if (relayOpened) RelayHost.closeWhenIdle(this)
        AppLookGuard.leave(this)
    }

    @Composable
    private fun Panel() {
        val p = payload ?: return
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { while (true) { LocalSendEngine.announce(); delay(4000) } }
        val relay = if (RelayHost.supported()) RelayHost.node(this).devices.collectAsState().value.filter { it.isTrusted }.sortedBy { !it.isReachable } else emptyList()
        val list = LocalSendEngine.peers.values.map { q -> Dev("ls:" + q.fingerprint + q.ip, q.alias, listOf("LocalSend", q.model, q.ip).filter { it.isNotEmpty() }.joinToString(" · "), q, null) } +
            relay.filter { it.isReachable }.map { d -> Dev("rl:" + d.id, d.name, "Relay · " + tr("Nearby", "Yakında"), null, d) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).clickable { finish() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.padding(horizontal = pageGutter).widthIn(max = 420.dp).fillMaxWidth().background(T.bg).border(1.dp, T.line).clickable(enabled = false) {}.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Share", "Paylaş"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (LocalSendEngine.scanning) ScanRing()
                }
                Text(p.label + (p.text?.let { " · " + it.take(60) } ?: ""), color = T.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text(
                        if (LocalSendEngine.scanning) tr("Looking for devices…", "Cihazlar aranıyor…") else tr("No device found on this Wi-Fi", "Bu Wi-Fi'da cihaz bulunamadı"),
                        color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    list.sortedBy { it.name.lowercase() }.forEach { d ->
                        Row(Modifier.fillMaxWidth().clickable { send(d, p) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(d.icon, T.text, 26.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(d.name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(d.sub, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            PIcon("share", Look.accent, 22.dp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { QuickShare.send(applicationContext, p.uris, p.text); finish() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("wifi", T.text, 26.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Quick Share · AirDrop", color = T.text, fontSize = 15.sp)
                            Text(tr("Android phones and iPhones nearby", "Yakındaki Android telefonlar ve iPhone'lar"), color = T.sub, fontSize = 12.sp)
                        }
                        PIcon("share", Look.accent, 22.dp)
                    }
                }
            }
        }
    }

    private fun send(d: Dev, p: Payload) {
        val app = applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        ShareSend.send(app, d, p, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)) { msg -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
        // with the bubble the sheet can go at once and the transfer stays in sight; without it the sheet waits a moment so the transfer can start
        if (d.ls != null && !LocalSendBubble.show(app)) main.postDelayed({ finish() }, 1500L) else finish()
    }
}
