package com.umutk.pulse.features

import android.Manifest
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.ToolOpenActivity
import com.umutk.pulse.pageBg
import com.umutk.pulse.tr
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** What the QR reader read, newest first (kept on the phone, up to 100). */
object QrHistory {
    private fun p(c: Context) = c.getSharedPreferences("qr_reader", Context.MODE_PRIVATE)
    class Entry(val text: String, val time: Long)

    fun list(c: Context): List<Entry> = runCatching {
        val a = JSONArray(p(c).getString("hist", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Entry(o.getString("t"), o.optLong("time")) } }
    }.getOrDefault(emptyList())

    fun add(c: Context, text: String) {
        val l = (listOf(Entry(text, System.currentTimeMillis())) + list(c).filter { it.text != text }).take(100)
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("t", it.text).put("time", it.time)) }
        p(c).edit().putString("hist", a.toString()).apply()
    }

    fun clear(c: Context) { p(c).edit().remove("hist").apply() }
    /** A link is opened at once when it is read (on at first). */
    fun autoOpen(c: Context) = p(c).getBoolean("autoOpen", true)
    fun setAutoOpen(c: Context, v: Boolean) { p(c).edit().putBoolean("autoOpen", v).apply() }

    /** The address to open for [t] when it is a link (a web address, or a tel:, mailto:, geo: ... one); null for plain text. */
    fun link(t: String): String? {
        val s = t.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        if (Regex("^(https?|ftp|tel|mailto|sms|smsto|geo|market|intent|spotify|youtube|tg|whatsapp)(:|://)\\S+$", RegexOption.IGNORE_CASE).matches(s)) return s
        if (s.startsWith("www.", true)) return "https://$s"
        if (Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/\\S*)?$").matches(s) && s.substringBefore('/').substringAfterLast('.').length in 2..6) return "https://$s"
        return null
    }

    fun open(c: Context, address: String): Boolean = runCatching {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}

/** Starts the QR reader page at once (a Toolkit tile and the Quick Settings tile both use it). */
fun startQrReader(c: Context) {
    c.startActivity(Intent(c, ToolOpenActivity::class.java).putExtra("route", "qr").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * The QR reader: the camera is open the moment the page is; the first code read is kept in the history, and a link is opened at once (a setting, on at first),
 * any other text is shown with Copy and Share. Closing the camera leaves the page when nothing was read; otherwise the result and the history show.
 */
@Composable
fun QrPage(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var scanning by remember { mutableStateOf(true) }
    var last by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(QrHistory.list(ctx)) }
    var auto by remember { mutableStateOf(QrHistory.autoOpen(ctx)) }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    fun got(t: String) {
        QrHistory.add(ctx, t); history = QrHistory.list(ctx); last = t
        if (auto) QrHistory.link(t)?.let { QrHistory.open(ctx, it) }
    }
    if (scanning && granted) { QrScanDialog({ got(it) }) { scanning = false; if (last == null) back() } }
    Column(Modifier.fillMaxSize().background(pageBg).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead(tr("QR reader", "QR okuyucu"))
        if (!granted) PCard {
            Text(tr("The camera is needed to read a code", "Kod okumak için kamera gerekir"), color = T.text, fontSize = 14.sp)
            PButton(tr("Allow the camera", "Kameraya izin ver")) { ask.launch(Manifest.permission.CAMERA) }
        }
        last?.let { t ->
            val link = QrHistory.link(t)
            PCard {
                Text(t, color = T.text, fontSize = 15.sp, fontFamily = if (link == null) FontFamily.Monospace else FontFamily.Default)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    if (link != null) PButton(tr("Open", "Aç"), compact = true) { QrHistory.open(ctx, link) }
                    PButton(tr("Copy", "Kopyala"), compact = true) {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("QR", t))
                        android.widget.Toast.makeText(ctx, tr("Copied", "Kopyalandı"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    PButton(tr("Share", "Paylaş"), compact = true) {
                        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "QR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
        }
        if (granted) PButton(tr("Scan", "Tara"), Modifier.fillMaxWidth()) { scanning = true }
        PCard {
            Row(Modifier.fillMaxWidth().clickable { auto = !auto; QrHistory.setAutoOpen(ctx, auto) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Open links at once", "Bağlantıları hemen aç"), color = T.text, fontSize = 15.sp)
                    Text(tr("A link read from a code opens by itself; other text is shown here", "Koddan okunan bağlantı kendiliğinden açılır; başka metin burada gösterilir"), color = T.sub, fontSize = 12.sp)
                }
                PSwitch(auto, { auto = it; QrHistory.setAutoOpen(ctx, it) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("History", "Geçmiş") + " · ${history.size}", color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) Text(tr("Clear", "Temizle"), color = T.sub, fontSize = 13.sp, modifier = Modifier.clickable { QrHistory.clear(ctx); history = emptyList() }.padding(6.dp))
        }
        val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
        history.forEach { e ->
            PCard(Modifier.fillMaxWidth().clickable { last = e.text }) {
                Text(e.text, color = T.text, fontSize = 14.sp, maxLines = 2)
                Text(df.format(Date(e.time)), color = T.sub, fontSize = 11.sp)
            }
        }
    }
}

/** Quick Settings tile: the QR reader, open at once with the camera running. */
@androidx.annotation.RequiresApi(24)
class QrTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { label = tr("QR reader", "QR okuyucu"); state = Tile.STATE_INACTIVE; updateTile() }
    }
    override fun onClick() {
        val i = Intent(this, ToolOpenActivity::class.java).putExtra("route", "qr").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 7301, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
        collapsePanel()
    }
}
