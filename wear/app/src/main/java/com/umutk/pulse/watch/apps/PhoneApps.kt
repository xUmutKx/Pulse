package com.umutk.pulse.watch.apps

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import com.umutk.pulse.watch.link.PhoneLink
import com.umutk.pulse.watch.ui.Chip
import com.umutk.pulse.watch.ui.Header
import com.umutk.pulse.watch.ui.Page
import com.umutk.pulse.watch.ui.RoundButton
import com.umutk.pulse.watch.ui.T
import com.umutk.pulse.watch.ui.W
import com.umutk.pulse.watch.ui.crown
import com.umutk.pulse.watch.ui.tick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Asks for the Bluetooth permission once, then [content]; without it a page that says so. */
@Composable
private fun NeedsLink(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(granted()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    LaunchedEffect(Unit) { if (!ok && Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }
    if (ok) content() else Page {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            T("Bluetooth permission", size = 14.sp, align = TextAlign.Center)
            Chip("Allow", "🔓") { if (Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }
        }
    }
}

/** Shown while the link is not up: what is going on, and Retry. */
@Composable
private fun LinkState(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        when (PhoneLink.state) {
            PhoneLink.State.CONNECTING, PhoneLink.State.OFF -> T("Connecting…", color = W.sub, size = 14.sp)
            else -> {
                T(PhoneLink.error.ifEmpty { "No phone" }, size = 13.sp, align = TextAlign.Center)
                T("Pulse > Device manager > Watch > Pulse Watch link", color = W.sub, size = 11.sp, align = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                RoundButton("↻", size = 44.dp) { onRetry() }
            }
        }
    }
}

private fun gb(b: Long): String = if (b >= 1L shl 30) "%.1f GB".format(b / 1073741824.0) else if (b >= 1L shl 20) "%.0f MB".format(b / 1048576.0) else "${b / 1024} KB"

/** The phone: its battery and storage, Find my phone, the torch and the ringer, and the ways into Now playing and the phone's files. */
@Composable
fun PhoneScreen(onOpen: (String) -> Unit) = NeedsLink {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry) {
        while (true) { PhoneLink.tryAsk(ctx, "status")?.let { st = it }; delay(10_000L) }
    }
    fun send(t: String, b: JSONObject) = scope.launch { tick(ctx); PhoneLink.tryAsk(ctx, t, b)?.let { if (it.has("bat")) st = it }; PhoneLink.tryAsk(ctx, "status")?.let { st = it } }
    Page {
        val s = st
        if (s == null) LinkState { retry++ } else ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header(s.optString("name", PhoneLink.phoneName)) }
            item {
                val bat = s.optInt("bat", -1)
                T((if (bat >= 0) "$bat%" else "–") + (if (s.optBoolean("chg")) " ⚡" else "") + "   " + gb(s.optLong("free")) + " free", color = W.sub, size = 13.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            item {
                val on = s.optBoolean("find")
                Chip(if (on) "Stop ringing" else "Find my phone", "🔔", if (on) "Ringing" else "Rings even when silent", color = if (on) W.red.copy(alpha = .35f) else W.card) { send("find", JSONObject().put("on", !on)) }
            }
            item { Chip("Now playing", "🎵", "Music on the phone") { onOpen("nowplaying") } }
            item { Chip("Phone files", "📁", "Browse and copy to the watch") { onOpen("phonefiles") } }
            item { val on = s.optBoolean("torch"); Chip("Flashlight", "🔦", if (on) "On" else "Off") { send("torch", JSONObject().put("on", !on)) } }
            item {
                val m = s.optInt("ringer", 2)
                val name = when (m) { 0 -> "Silent"; 1 -> "Vibrate"; else -> "Sound" }
                Chip("Ringer", "🔊", name) { send("ringer", JSONObject().put("m", (m + 2) % 3)) }
            }
        }
    }
}

/** Now playing on the phone (any app): the cover, the title, a ring with the position, previous / play / next; the crown turns the phone's volume. */
@Composable
fun NowPlayingScreen() = NeedsLink {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf<JSONObject?>(null) }
    var art by remember { mutableStateOf<ImageBitmap?>(null) }
    var artKey by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }
    var vol by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(retry) {
        while (true) {
            val key = m?.let { it.optString("title") + it.optString("artist") } ?: ""
            val r = PhoneLink.tryAsk(ctx, "media", JSONObject().put("art", key != artKey || art == null))
            if (r != null) {
                m = r
                vol = r.optInt("vol").toFloat() / r.optInt("max", 1).coerceAtLeast(1)
                r.optString("art").takeIf { it.isNotEmpty() }?.let { b64 ->
                    art = withContext(Dispatchers.Default) { runCatching { Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
                }
                artKey = r.optString("title") + r.optString("artist")
            }
            delay(if (r?.optBoolean("playing") == true) 1500L else 4000L)
        }
    }
    fun cmd(c: String) = scope.launch { tick(ctx); PhoneLink.tryAsk(ctx, "mcmd", JSONObject().put("c", c)); delay(300L); PhoneLink.tryAsk(ctx, "media")?.let { m = it } }
    Page(showTime = false) {
        val x = m
        if (x == null) { LinkState { retry++ }; return@Page }
        Box(Modifier.fillMaxSize().crown { d -> cmd(if (d > 0) "volup" else "voldn") }) {
            art?.let { Image(it, null, Modifier.fillMaxSize().alpha(.35f), contentScale = ContentScale.Crop) }
            // the position as a ring round the edge, the volume as a thin inner ring
            val dur = x.optLong("dur"); val pos = x.optLong("pos")
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val sw = 5.dp.toPx()
                drawArc(W.card, -90f, 360f, false, style = Stroke(sw))
                if (dur > 0) drawArc(W.accent, -90f, 360f * (pos.toFloat() / dur).coerceIn(0f, 1f), false, style = Stroke(sw, cap = StrokeCap.Round))
                if (vol >= 0f) drawArc(W.sub.copy(alpha = .6f), 135f, 90f * vol, false, topLeft = androidx.compose.ui.geometry.Offset(sw * 2, sw * 2),
                    size = androidx.compose.ui.geometry.Size(size.width - sw * 4, size.height - sw * 4), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (x.optBoolean("none")) { T("Nothing playing", color = W.sub, size = 14.sp); return@Column }
                T(x.optString("app"), color = W.accent, size = 11.sp, maxLines = 1)
                T(x.optString("title"), size = 15.sp, weight = FontWeight.SemiBold, maxLines = 2, align = TextAlign.Center)
                T(x.optString("artist"), color = W.sub, size = 12.sp, maxLines = 1)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton("⏮", size = 40.dp, textSize = 15.sp) { cmd("prev") }
                    RoundButton(if (x.optBoolean("playing")) "⏸" else "▶", size = 54.dp, color = W.accent, textColor = W.bg) { cmd("toggle") }
                    RoundButton("⏭", size = 40.dp, textSize = 15.sp) { cmd("next") }
                }
            }
        }
    }
}

/** The phone's shared storage: folders first; a tap on a file copies it to the watch (Download/Phone), with its progress. */
@Composable
fun PhoneFilesScreen() = NeedsLink {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    var root by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var retry by remember { mutableStateOf(0) }
    var copying by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(path, retry) {
        list = null
        val r = PhoneLink.tryAsk(ctx, "list", JSONObject().put("p", path)) ?: return@LaunchedEffect
        root = r.optString("root"); if (path.isEmpty()) path = r.optString("p")
        val a = r.optJSONArray("items"); list = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
    }
    fun copy(name: String, size: Long) {
        if (copying != null) return
        scope.launch {
            val dir = File(ctx.getExternalFilesDir(null), "Phone").apply { mkdirs() }
            val out = File(dir, name)
            copying = name to 0f; note = ""
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // resumes where an earlier copy stopped (the watch may have slept)
                    RandomAccessFile(out, "rw").use { w ->
                        var off = if (out.length() in 1 until size) out.length() else 0L
                        w.setLength(off)
                        while (off < size) {
                            val r = PhoneLink.tryAsk(ctx, "get", JSONObject().put("p", "$path/$name").put("off", off)) ?: return@runCatching false
                            val n = r.optInt("n"); if (n <= 0) return@runCatching false
                            w.seek(off); w.write(Base64.decode(r.optString("data"), Base64.DEFAULT)); off += n
                            copying = name to off.toFloat() / size
                        }
                    }
                    true
                }.getOrDefault(false)
            }
            note = if (ok) "Copied to the watch" else "Copy stopped"
            copying = null
            tick(ctx, strong = true)
        }
    }
    Page(showTime = false) {
        val l = list
        if (l == null) { if (PhoneLink.state == PhoneLink.State.FAILED) LinkState { retry++ } else T("…", color = W.sub, modifier = Modifier.align(Alignment.Center)); return@Page }
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header(if (path == root) "Phone" else path.substringAfterLast('/')) }
            copying?.let { (n, f) -> item { T("$n  ${(f * 100).toInt()}%", color = W.accent, size = 12.sp, maxLines = 1) } }
            if (note.isNotEmpty()) item { T(note, color = W.sub, size = 12.sp) }
            if (path != root && path.isNotEmpty()) item { Chip("Up", "↰") { path = path.substringBeforeLast('/') } }
            items(l) { f ->
                val dirF = f.optBoolean("d")
                Chip(f.optString("n"), if (dirF) "📁" else "📄", if (dirF) null else gb(f.optLong("s"))) {
                    if (dirF) path = path + "/" + f.optString("n") else copy(f.optString("n"), f.optLong("s"))
                }
            }
        }
    }
}
