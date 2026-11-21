package com.umutk.pulse.extras.launcher

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.extras.blackout.Privilege
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The three hives of the phone's settings database, like the hives of a registry. */
private val HIVES = listOf("system" to "HKEY_SYSTEM", "global" to "HKEY_GLOBAL", "secure" to "HKEY_SECURE")

/** Tweaks worth a place at the top: key, hive, and what it does. */
private val TWEAKS = listOf(
    Triple("window_animation_scale", "global", "Window animation scale (0 off, 1 normal)"),
    Triple("transition_animation_scale", "global", "Transition animation scale"),
    Triple("animator_duration_scale", "global", "Animator duration scale"),
    Triple("peak_refresh_rate", "system", "Peak refresh rate (Hz)"),
    Triple("min_refresh_rate", "system", "Lowest refresh rate (Hz)"),
    Triple("screen_off_timeout", "system", "Screen off timeout (ms)"),
    Triple("screen_brightness_mode", "system", "Brightness mode (0 manual, 1 auto)"),
    Triple("accelerometer_rotation", "system", "Auto-rotate (0 / 1)"),
    Triple("clock_seconds", "secure", "Seconds in the status bar clock (0 / 1)"),
    Triple("show_touches", "system", "Show touches (0 / 1)"),
    Triple("pointer_location", "system", "Pointer location (0 / 1)"),
    Triple("adb_enabled", "global", "USB debugging (0 / 1)"),
    Triple("stay_on_while_plugged_in", "global", "Stay awake while charging (0, 1 AC, 2 USB, 3 both, 7 all)"),
    Triple("font_scale", "system", "Font scale (1.0 normal)"),
)

private fun uriOf(h: String) = when (h) { "system" -> Settings.System.CONTENT_URI; "global" -> Settings.Global.CONTENT_URI; else -> Settings.Secure.CONTENT_URI }

/** Every name and value of a hive, sorted (read through the settings provider). */
private fun readHive(c: Context, h: String): List<Pair<String, String>> = runCatching {
    val out = ArrayList<Pair<String, String>>()
    c.contentResolver.query(uriOf(h), arrayOf("name", "value"), null, null, "name ASC")?.use { cur ->
        while (cur.moveToNext()) out += (cur.getString(0).orEmpty() to cur.getString(1).orEmpty())
    }
    out
}.getOrDefault(emptyList())

private fun readOne(c: Context, h: String, k: String): String? = runCatching { when (h) { "system" -> Settings.System.getString(c.contentResolver, k); "global" -> Settings.Global.getString(c.contentResolver, k); else -> Settings.Secure.getString(c.contentResolver, k) } }.getOrNull()

/** Writes a value: through the app itself when the phone allows it (Write settings for the system hive, WRITE_SECURE_SETTINGS given by adb for the others), else through root or Shizuku. */
private fun writeOne(c: Context, h: String, k: String, v: String?): Boolean {
    val own = runCatching {
        when (h) {
            "system" -> if (Settings.System.canWrite(c)) { if (v == null) false else Settings.System.putString(c.contentResolver, k, v) } else false
            "global" -> if (v == null) false else Settings.Global.putString(c.contentResolver, k, v)
            else -> if (v == null) false else Settings.Secure.putString(c.contentResolver, k, v)
        }
    }.getOrDefault(false)
    if (own) return true
    val q = { x: String -> "'" + x.replace("'", "'\\''") + "'" }
    val cmd = if (v == null) "settings delete $h ${q(k)}" else "settings put $h ${q(k)} ${q(v)}"
    return Privilege.run(cmd).code == 0
}

/**
 * The mini registry: the phone's settings as a registry (three hives: System, Global, Secure) that reads every key and changes them, like SetEdit. A write goes through the
 * app when the phone allows it (adb grant of WRITE_SECURE_SETTINGS, or Write settings), else through root or Shizuku. A tweaks list with the usual keys comes first.
 */
@Composable
fun MiniRegistry() {
    val ctx = LocalContext.current
    var hive by remember { mutableStateOf("global") }
    var q by remember { mutableStateOf("") }
    var rev by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var edit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(hive, rev) { rows = withContext(Dispatchers.IO) { readHive(ctx, hive) } }
    val shown = remember(rows, q) { if (q.isBlank()) rows else rows.filter { it.first.contains(q.trim(), true) || it.second.contains(q.trim(), true) } }
    val tweaks = remember(hive, rows) { TWEAKS.filter { it.second == hive } }
    WinGlass()
    Column(Modifier.fillMaxSize().background(winPageBg()).padding(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            HIVES.forEach { (id, name) ->
                Text(name, color = if (hive == id) T.accent else T.text, fontSize = 12.sp, fontWeight = if (hive == id) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.background(if (hive == id) T.accent.copy(alpha = .15f) else T.panel).clickable { hive = id; q = "" }.padding(horizontal = 8.dp, vertical = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("+", color = T.accent, fontSize = 20.sp, modifier = Modifier.clickable { edit = "" to "" }.padding(horizontal = 8.dp))
        }
        PField(q, { q = it }, tr("Find a key or a value", "Anahtar ya da değer ara"), Modifier.fillMaxWidth())
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (q.isBlank() && tweaks.isNotEmpty()) {
                item { Text(tr("Tweaks", "İnce ayarlar"), color = T.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                items(tweaks) { (k, _, d) ->
                    Column(Modifier.fillMaxWidth().clickable { edit = k to (readOne(ctx, hive, k).orEmpty()) }.padding(vertical = 6.dp)) {
                        Row { Text(k, color = T.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(readOne(ctx, hive, k) ?: "—", color = T.accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
                        Text(d, color = T.sub, fontSize = 11.sp)
                    }
                }
                item { Text(tr("All keys", "Tüm anahtarlar") + " · ${rows.size}", color = T.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) }
            }
            items(shown, key = { it.first }) { (k, v) ->
                Row(Modifier.fillMaxWidth().clickable { edit = k to v }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(v.take(40), color = T.sub, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(T.line))
            }
        }
    }
    edit?.let { (k0, v0) ->
        var k by remember(k0) { mutableStateOf(k0) }
        var v by remember(k0) { mutableStateOf(v0) }
        WinPopup(tr("Edit value", "Değeri düzenle"), { edit = null }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(HIVES.first { it.first == hive }.second, color = T.sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                PField(k, { k = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
                PField(v, { v = it }, tr("Value", "Değer"), Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton(tr("Save", "Kaydet"), compact = true) {
                        val name = k.trim()
                        if (name.isNotEmpty()) Thread {
                            val ok = writeOne(ctx, hive, name, v)
                            android.os.Handler(android.os.Looper.getMainLooper()).post { note = if (ok) "$name = $v" else tr("Not written: the phone needs Write settings, adb's WRITE_SECURE_SETTINGS, root or Shizuku", "Yazılamadı: telefon için Ayar yazma izni, adb'nin WRITE_SECURE_SETTINGS'i, root ya da Shizuku gerekir"); rev++ }
                        }.start()
                        edit = null
                    }
                    if (k0.isNotEmpty()) PButton(tr("Delete", "Sil"), danger = true, compact = true) {
                        Thread { val ok = writeOne(ctx, hive, k0, null); android.os.Handler(android.os.Looper.getMainLooper()).post { note = if (ok) tr("Deleted", "Silindi") else tr("Not deleted (root or Shizuku needed)", "Silinemedi (root ya da Shizuku gerekir)"); rev++ } }.start()
                        edit = null
                    }
                    PButton(tr("Cancel", "Vazgeç"), compact = true) { edit = null }
                }
            }
        }
    }
}
