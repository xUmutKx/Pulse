package com.umutk.pulse.extras.relay

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Cfg
import com.umutk.pulse.Look
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import com.vythera.relay.node.RelayDevice
import com.vythera.relay.node.SendResult
import com.vythera.relay.protocol.DeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Pulse · Relay" in the system's Share list: files, text and links shared from any app go to a paired Relay device (the desktop app or another phone) with one tap.
 * Devices are the paired ones; a device that is not on the network now is greyed out.
 */
class RelayShareActivity : ComponentActivity() {
    private var uris: List<Uri> = emptyList()
    private var text: String? = null
    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        val i = intent
        uris = when (i.action) {
            Intent.ACTION_SEND_MULTIPLE -> androidx.core.content.IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
        }
        text = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        if (!RelayHost.supported() || (uris.isEmpty() && text == null)) { finish(); return }
        RelayHost.open(this); opened = true
        setContent { LookHost(transparent = true) { Panel() } }
    }

    override fun onDestroy() { super.onDestroy(); if (opened) RelayHost.closeWhenIdle(this) }

    @Composable
    private fun Panel() {
        val node = RelayHost.node(this)
        val devices by node.devices.collectAsState()
        val paired = devices.filter { it.isTrusted }.sortedBy { !it.isReachable }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).clickable { finish() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.padding(horizontal = pageGutter).widthIn(max = 420.dp).fillMaxWidth().background(T.bg).border(1.dp, T.line).clickable(enabled = false) {}.padding(20.dp)) {
                Text("Relay", color = T.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(if (uris.isNotEmpty()) tr("${uris.size} file(s)", "${uris.size} dosya") else (text ?: "").take(80), color = T.sub, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (paired.isEmpty()) Text(tr("No paired device. Open Relay in Pulse and pair one first.", "Eşleşmiş cihaz yok. Önce Pulse'ta Relay'i aç ve bir cihazla eşleş."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    paired.forEach { d ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = d.isReachable) { send(d) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(if (d.type == DeviceType.PHONE || d.type == DeviceType.TABLET) "phone" else "cpu", if (d.isReachable) T.text else T.sub, 26.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(d.name, color = if (d.isReachable) T.text else T.sub, fontSize = 15.sp)
                                Text(if (d.isReachable) tr("Nearby", "Yakında") else tr("Not seen", "Görünmüyor"), color = T.sub, fontSize = 12.sp)
                            }
                            if (d.isReachable) PIcon("share", Look.accent, 22.dp)
                        }
                    }
                }
            }
        }
    }

    private fun send(d: RelayDevice) {
        val app = applicationContext
        val node = RelayHost.node(app)
        val t = text; val u = uris
        if (u.isNotEmpty()) {
            val files = RelayHost.filesOf(app, u)
            if (files.isEmpty()) { Toast.makeText(app, tr("Could not read the files", "Dosyalar okunamadı"), Toast.LENGTH_SHORT).show(); return }
            node.sendFiles(d.id, files)
            Toast.makeText(app, tr("Sending to ${d.name}", "${d.name} cihazına gönderiliyor"), Toast.LENGTH_SHORT).show()
        } else if (t != null) {
            CoroutineScope(Dispatchers.Main).launch {
                val r = node.sendText(d.id, t)
                Toast.makeText(app, if (r is SendResult.Sent) tr("Sent to ${d.name}", "${d.name} cihazına gönderildi") else tr("Could not send", "Gönderilemedi"), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
