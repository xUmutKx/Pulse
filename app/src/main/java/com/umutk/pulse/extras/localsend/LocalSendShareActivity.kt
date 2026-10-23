package com.umutk.pulse.extras.localsend

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/**
 * "Pulse · LocalSend" in the system's Share list, an alternative to Quick Share: the devices of the network are listed right here (found while the sheet is
 * open, over Wi-Fi, a hotspot or USB tethering) and a tap sends the shared files to one of them. It keeps the look of the LocalSend app.
 */
class LocalSendShareActivity : ComponentActivity() {
    private fun uris(): List<Uri> {
        val i = intent
        return when (i.action) {
            Intent.ACTION_SEND_MULTIPLE -> androidx.core.content.IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
        }
    }

    override fun onResume() { LocalSendLook.apply(this); super.onResume() }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        LocalSendLook.apply(this)
        val list = uris()
        if (list.isEmpty()) { finish(); return }
        LocalSendEngine.start(this)
        setContent { LookHost(transparent = true) { Panel(list) } }
    }

    override fun onDestroy() { super.onDestroy(); if (LocalSendEngine.busy.isEmpty()) LocalSendEngine.stop(); AppLookGuard.leave(this) }

    @Composable
    private fun Panel(list: List<Uri>) {
        LaunchedEffect(Unit) { while (true) { LocalSendEngine.announce(); delay(4000) } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).clickable { finish() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.padding(horizontal = pageGutter).widthIn(max = 420.dp).fillMaxWidth().background(T.bg).border(1.dp, T.line).clickable(enabled = false) {}.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LocalSend", color = T.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (LocalSendEngine.scanning || LocalSendEngine.peers.isEmpty()) ScanRing()
                }
                Text(tr("${list.size} file(s)", "${list.size} dosya"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    val peers = LocalSendEngine.peers.values.sortedBy { it.alias.lowercase() }
                    if (peers.isEmpty()) Text(tr("Looking for devices…", "Cihazlar aranıyor…"), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    peers.forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable { send(p, list) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon("phone", T.text, 26.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(p.alias, color = T.text, fontSize = 15.sp)
                                Text(listOf(p.model, p.ip).filter { it.isNotEmpty() }.joinToString(" · "), color = T.sub, fontSize = 12.sp)
                            }
                            PIcon("share", Look.accent, 22.dp)
                        }
                    }
                }
            }
        }
    }

    private fun send(p: Peer, list: List<Uri>) {
        val app = applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            val r = LocalSendEngine.send(app, p, list)
            main.post { android.widget.Toast.makeText(app, r.ifBlank { tr("Sent", "Gönderildi") }, android.widget.Toast.LENGTH_LONG).show() }
        }.start()
        // with the bubble the sheet can go at once and the transfer stays in sight; without it the sheet waits for the end
        if (LocalSendBubble.show(app)) finish() else Thread { var n = 0; while (LocalSendEngine.busy.isEmpty() && n++ < 100) Thread.sleep(50); while (LocalSendEngine.busy.isNotEmpty()) Thread.sleep(200); runOnUiThread { finish() } }.start()
    }
}
