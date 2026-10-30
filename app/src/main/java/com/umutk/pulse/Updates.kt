package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Start-up and manual update check against the latest GitHub release. Any failure (no network, rate limit) is silent. */
object Updates {
    private const val PREFS = "pulse_updates"
    private const val ENDPOINT = "https://api.github.com/repos/xUmutKx/Pulse/releases/latest"
    private const val GAP = 12L * 3600_000L

    /** version is the tag without its "v"; url is the APK when the release has one, else the release page. */
    data class Release(val version: String, val notes: List<String>, val url: String)

    fun installed(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    /** True when remote is a higher dotted version than local ("0.10.117" > "0.10.116"). */
    fun newer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Reads the latest release now. Null when offline, on an error, or when the release has no tag. */
    suspend fun fetch(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "Pulse")
                if (c.responseCode != 200) return@runCatching null
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = j.optString("tag_name")
                if (tag.isEmpty()) return@runCatching null
                val apk = j.optJSONArray("assets")?.let { a ->
                    (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("name").endsWith(".apk") }
                        // the release holds two builds: Android 12+ and the "android5" one; each phone gets its own
                        .firstOrNull { (android.os.Build.VERSION.SDK_INT >= 31) != it.optString("name").contains("android5") }?.optString("browser_download_url")
                }
                val notes = j.optString("body").lines().map { it.trim().trimStart('#', '-', '*', ' ') }.filter { it.isNotEmpty() }.take(6)
                Release(tag.trimStart('v', 'V'), notes, if (apk.isNullOrEmpty()) j.optString("html_url") else apk)
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /** The start-up check: at most once per 12 hours, and not for a version the user already dismissed. */
    suspend fun due(ctx: Context): Release? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last", 0L) < GAP) return null
        p.edit().putLong("last", now).apply()
        val r = fetch() ?: return null
        return r.takeIf { newer(it.version, installed(ctx)) && it.version != p.getString("skip", null) }
    }

    fun skip(ctx: Context, version: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("skip", version).apply()
    }
}

/** Start-up popup: shown once the splash and welcome are done, only when a newer release exists. */
@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    var rel by remember { mutableStateOf<Updates.Release?>(null) }
    LaunchedEffect(Unit) { rel = Updates.due(ctx) }
    rel?.let { r -> UpdateDialog(r) { rel = null } }
}

/** The "New update available" window: the Pulse icon, the version, the first notes; Download opens the APK or the release page. */
@Composable
fun UpdateDialog(r: Updates.Release, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    fun close() { Updates.skip(ctx, r.version); onDone() }
    WinAlert(
        onDismissRequest = { close() },
        title = { Text(tr("New update available", "Yeni güncelleme var"), color = w.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(rememberLauncherLogo(), null, Modifier.size(64.dp))
                Text("Pulse ${r.version}", color = w.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                r.notes.forEach { Text("•  $it", color = w.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            PButton(tr("Download", "İndir")) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                close()
            }
        },
        dismissButton = { PButton(tr("Later", "Sonra")) { close() } },
    )
}
