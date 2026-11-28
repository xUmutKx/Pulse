package com.umutk.pulse.extras.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Pulse Core's "Extra apps" page (Pulse Core has no extra apps inside): each app of Pulse, what it is, what it does, its screenshots, and a Download button that fetches
 * its APK from Pulse's releases and hands it to the system installer. An app that is installed shows Open (or Update when a newer one is out).
 */
object PulseAppsRemote {
    /** What a release holds for one app: its version and its download address and size. */
    class Build(val version: String, val url: String, val size: Long)

    private const val API = "https://api.github.com/repos/xUmutKx/Pulse/releases?per_page=12"
    private const val RAW = "https://raw.githubusercontent.com/xUmutKx/Pulse/main/docs/apps/"
    @Volatile var builds: Map<String, Build>? = null

    fun packageOf(id: String) = "com.umutk.pulse.$id"

    /** The newest build of each app, read from the latest releases that hold one (blocking; null when the net is down). */
    fun fetch(): Map<String, Build>? = runCatching {
        val c = java.net.URL(API).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000; c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode !in 200..299) return null
        val rel = JSONArray(c.inputStream.use { String(it.readBytes()) })
        val out = HashMap<String, Build>()
        for (i in 0 until rel.length()) {
            val r = rel.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val assets = r.optJSONArray("assets") ?: continue
            for (k in 0 until assets.length()) {
                val a = assets.getJSONObject(k)
                val m = Regex("^Pulse-([a-z0-9]+)-(\\d+(?:\\.\\d+)+)\\.apk$").matchEntire(a.optString("name")) ?: continue
                out.putIfAbsent(m.groupValues[1], Build(m.groupValues[2], a.optString("browser_download_url"), a.optLong("size")))
            }
        }
        out.also { builds = it }
    }.getOrNull()

    /** The screenshots of [id]: the names listed in docs/apps/<id>/shots.txt of the repository, as picture addresses (none when the file is not there). */
    fun shots(id: String): List<String> = runCatching {
        val c = java.net.URL(RAW + id + "/shots.txt").openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        if (c.responseCode !in 200..299) return emptyList()
        c.inputStream.use { String(it.readBytes()) }.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { RAW + id + "/" + it }
    }.getOrDefault(emptyList())

    fun installedVersion(c: Context, id: String): String? = runCatching { c.packageManager.getPackageInfo(packageOf(id), 0).versionName }.getOrNull()

    fun newer(remote: String, local: String): Boolean {
        fun p(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }
        val a = p(remote); val b = p(local)
        for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }
        return false
    }

    /** Downloads the APK of [id] (progress 0..1 to [onProgress]) and returns its file, or null when it failed. Blocking. */
    fun download(c: Context, id: String, b: Build, onProgress: (Float) -> Unit): File? = runCatching {
        val dir = File(c.filesDir, "apps").apply { mkdirs(); listFiles()?.filter { it.name.startsWith("Pulse-$id-") }?.forEach { it.delete() } }
        val f = File(dir, "Pulse-$id-${b.version}.apk")
        val tmp = File(dir, f.name + ".part")
        val conn = java.net.URL(b.url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000; conn.readTimeout = 30_000; conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) return null
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: b.size
        conn.inputStream.use { i -> tmp.outputStream().use { o ->
            val buf = ByteArray(64 * 1024); var done = 0L
            while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
        } }
        if (total > 0 && tmp.length() != total) { tmp.delete(); return null }
        tmp.renameTo(f); f
    }.getOrNull()

    /** Hands the APK to the system installer (asks first for the right to install apps when it is not given). */
    fun install(c: Context, f: File) {
        if (!c.packageManager.canRequestPackageInstalls()) {
            runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", f)
        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
fun PulseAppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var builds by remember { mutableStateOf(PulseAppsRemote.builds) }
    var failed by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    val progress = remember { mutableStateMapOf<String, Float>() }
    val shots = remember { mutableStateMapOf<String, List<String>>() }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (builds == null) { builds = withContext(Dispatchers.IO) { PulseAppsRemote.fetch() }; failed = builds == null } }
    // back from the installer: the rows are read again
    val lc = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lc) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) rev++ }
        lc.addObserver(o); onDispose { lc.removeObserver(o) }
    }
    BackHandler(enabled = open != null) { open = null }
    fun get(app: PulseApp) {
        val b = builds?.get(app.id) ?: return
        if (progress.containsKey(app.id)) return
        scope.launch {
            progress[app.id] = 0f
            val f = withContext(Dispatchers.IO) { PulseAppsRemote.download(ctx, app.id, b) { p -> progress[app.id] = p } }
            progress.remove(app.id)
            if (f != null) PulseAppsRemote.install(ctx, f) else android.widget.Toast.makeText(ctx, tr("The download failed", "İndirme başarısız"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val all = PulseAppsCatalog.all
    val shown = remember(query) { val q = query.trim(); if (q.isEmpty()) all else all.filter { it.name.contains(q, true) || it.about.contains(q, true) } }
    val detail = all.firstOrNull { it.id == open }
    LaunchedEffect(open) { open?.let { id -> if (!shots.containsKey(id)) shots[id] = withContext(Dispatchers.IO) { PulseAppsRemote.shots(id) } } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pageGutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { if (open != null) open = null else onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(detail?.name ?: tr("Extra apps", "Ekstra uygulamalar"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (failed) Text(tr("Could not reach the list of apps. Check the connection.", "Uygulama listesine ulaşılamadı. Bağlantıyı kontrol et."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pageGutter, vertical = 6.dp))
        else if (builds == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = pageGutter, vertical = 6.dp).height(3.dp), color = Look.accent)
        LazyColumn(Modifier.weight(1f).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (detail != null) item {
                AppCard(detail, builds?.get(detail.id), rev, progress[detail.id], expanded = true, shots = shots[detail.id].orEmpty(), onOpen = {}, onGet = { get(detail) })
            } else {
                item { Text(tr("Every app of Pulse as an app of its own. Download the ones you want; each installs beside Pulse.", "Pulse'un her uygulaması ayrı bir uygulama olarak. İstediğini indir; her biri Pulse'un yanına kurulur."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                items(shown, key = { it.id }) { app -> AppCard(app, builds?.get(app.id), rev, progress[app.id], expanded = false, shots = emptyList(), onOpen = { open = app.id }, onGet = { get(app) }) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppCard(app: PulseApp, build: PulseAppsRemote.Build?, rev: Int, progress: Float?, expanded: Boolean, shots: List<String>, onOpen: () -> Unit, onGet: () -> Unit) {
    val ctx = LocalContext.current
    val have = remember(rev, app.id) { PulseAppsRemote.installedVersion(ctx, app.id) }
    val update = have != null && build != null && PulseAppsRemote.newer(build.version, have)
    PCard(Modifier.fillMaxWidth().then(if (expanded) Modifier else Modifier.clickable { onOpen() })) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Look.accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) { PIcon(iconOf(app.id), Look.accent, 24.dp) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(app.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr(app.about, app.aboutTr), color = T.sub, fontSize = 12.sp, maxLines = if (expanded) 6 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (expanded) {
            app.features.forEach { (en, trk) -> Text("•  " + tr(en, trk), color = T.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (shots.isNotEmpty()) LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shots) { u -> coil.compose.AsyncImage(u, null, Modifier.height(320.dp).width(150.dp).clip(RoundedCornerShape(12.dp)).background(T.panel), contentScale = ContentScale.Crop) }
            }
            Text(tr("Its own page: github.com/xUmutKx/${app.repo}", "Kendi sayfası: github.com/xUmutKx/${app.repo}"), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOfNotNull(have?.let { tr("Installed $it", "Kurulu $it") } ?: build?.let { "v${it.version} · ${(it.size / 1048576).coerceAtLeast(1)} MB" }).firstOrNull().orEmpty(), color = T.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            when {
                progress != null -> Column(Modifier.width(120.dp)) { LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(4.dp), color = Look.accent) }
                have != null && !update -> PButton(tr("Open", "Aç"), compact = true) {
                    runCatching { ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(PulseAppsRemote.packageOf(app.id))!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                else -> PButton(if (update) tr("Update", "Güncelle") else tr("Download", "İndir"), compact = true) { if (build != null) onGet() }
            }
        }
    }
}

private fun iconOf(id: String): String = when (id) {
    "files" -> "folder"; "photos" -> "image"; "calendar" -> "calendar"; "contacts" -> "person"; "music", "ytx" -> "music"; "video" -> "video"; "clock" -> "clock"
    "notes", "tempmail" -> "notes"; "keyboard" -> "keyboard"; "weather" -> "weather"; "voice" -> "voice"; "messages" -> "chat"; "code", "terminal" -> "terminal"
    "ebook" -> "book"; "office" -> "document"; "browser" -> "globe"; "health" -> "heart"; "calculator" -> "calc"; "launcher", "island", "gamehub" -> "phone"; else -> "apps"
}
