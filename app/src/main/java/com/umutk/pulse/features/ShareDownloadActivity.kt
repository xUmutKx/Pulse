package com.umutk.pulse.features

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Cfg
import com.umutk.pulse.ExtraLookControl
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.LocalExtraLook
import com.umutk.pulse.LookHost
import com.umutk.pulse.MainActivity
import com.umutk.pulse.Opt
import com.umutk.pulse.PButton
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr

/** The look of the share window, kept apart from Pulse's own: an outside app that shares a link gets a window like an extra app, Material until another is picked. */
object ShareLook {
    private fun p(c: Context) = c.getSharedPreferences("share_download", Context.MODE_PRIVATE)
    fun get(c: Context): String = p(c).getString("look", "material") ?: "material"
    fun set(c: Context, id: String) { p(c).edit().putString("look", id).apply() }
}

/**
 * A link shared to Pulse from a browser or any app: a small window over that app (a bottom sheet in Material / AMOLED, a Windows dialog box in the
 * Windows looks) instead of opening Pulse. A file link downloads at once on "Download"; a web page is read for its download links first.
 */
class ShareDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        val url = Regex("https?://\\S+").find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value
        if (url == null) { finish(); return }
        setContent {
            val ctx = LocalContext.current
            var look by remember { mutableStateOf(ShareLook.get(ctx)) }
            val control = remember(look) { ExtraLookControl(look) { look = it; ShareLook.set(ctx, it) } }
            CompositionLocalProvider(LocalExtraLook provides control) {
                LookHost(transparent = true, lookId = look) { _ -> ShareUi(url, look) { finish() } }
            }
        }
    }
}

@Composable
private fun ShareUi(url: String, look: String, close: () -> Unit) {
    val ctx = LocalContext.current
    val found by produceState<LinkFinder.Result?>(null, url) { value = LinkFinder.find(url) }
    var chosen by remember { mutableStateOf<String?>(null) }
    val target = chosen ?: if (found is LinkFinder.Result.Direct || found is LinkFinder.Result.Failed) url else null
    fun download() {
        val t = target ?: return
        Downloads.add(ctx, t)
        Toast.makeText(ctx, tr("Download started", "İndirme başladı"), Toast.LENGTH_SHORT).show()
        close()
    }
    fun openPulse() { Downloads.wanted = true; ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); close() }
    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Uri.parse(url).host.orEmpty(), color = T.sub, fontSize = 12.sp, maxLines = 1)
            when (val r = found) {
                null -> Text(tr("Looking for the download…", "İndirme aranıyor…"), color = T.text, fontSize = 14.sp)
                is LinkFinder.Result.Direct, is LinkFinder.Result.Failed ->
                    Text(Uri.decode(url.substringBefore('?').substringAfterLast('/')).ifBlank { url }, color = T.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                is LinkFinder.Result.None -> Text(tr("No download link found on that page", "Bu sayfada indirme bağlantısı bulunamadı"), color = T.text, fontSize = 14.sp)
                is LinkFinder.Result.Found -> LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(r.links, key = { it }) { l ->
                        val on = chosen == l
                        Column(Modifier.fillMaxWidth().background(if (on) T.accent.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent).clickable { chosen = l }.padding(vertical = 7.dp, horizontal = 4.dp)) {
                            Text(Uri.decode(l.substringBefore('?').substringAfterLast('/')).ifBlank { l }, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Uri.parse(l).host.orEmpty(), color = T.sub, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    if (look.startsWith("win")) {
        // the Windows looks get a Windows dialog box
        WinAlert(onDismissRequest = close, title = { Text(tr("Download", "İndir")) }, text = { body() },
            confirmButton = { PButton(tr("Download", "İndir")) { download() } },
            dismissButton = { Row { PButton(tr("Open Pulse", "Pulse'u aç")) { openPulse() }; Spacer(Modifier.padding(4.dp)); PButton(tr("Cancel", "İptal")) { close() } } })
    } else {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .35f)).clickable(onClick = close), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().clip26().background(T.bg).clickable(enabled = false) {}.navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Download", "İndir"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    ExtraLookIcon(T.text)
                }
                body()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PButton(tr("Download", "İndir"), Modifier.weight(1f)) { download() }
                    PButton(tr("Open Pulse", "Pulse'u aç"), Modifier.weight(1f)) { openPulse() }
                }
            }
        }
    }
}

private fun Modifier.clip26() = this.then(Modifier.clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)))
