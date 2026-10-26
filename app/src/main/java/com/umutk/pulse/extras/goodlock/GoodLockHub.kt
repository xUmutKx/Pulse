package com.umutk.pulse.extras.goodlock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.umutk.pulse.ExtraKeys
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A manager for Samsung's Good Lock modules: which of them are installed and in what version, a check for newer versions in APKMirror's feed, and a way to the
 * app info screen and to the module's page. Written from the description of the idea (a lighter client for the modules); nothing of another program's code is in it.
 */
private class Module(val label: String, val pkg: String, val version: String, val installedAt: Long)

/** The names of the modules; the installed apps are matched to them by their label, so the package names need not be known. */
private val NAMES = listOf("Good Lock", "QuickStar", "NavStar", "Keys Cafe", "LockStar", "ClockFace", "Home Up", "MultiStar", "NiceCatch", "Nice Shot", "One Hand Operation",
    "Theme Park", "Wonderland", "SoundAssistant", "RegiStar", "Routines+", "Edge lighting", "Pentastic", "Camera Assistant", "Display Assistant", "Battery Assistant",
    "Good Guardians", "Good Lock Plugin", "Edge Touch", "Wallpaper Classic", "Nice Lock", "Gallery Assistant", "Keyboard Assistant", "Dex Assistant", "Pattern Lock")

private fun installed(c: Context): List<Module> {
    val pm = c.packageManager
    return pm.getInstalledApplications(0)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.startsWith("com.samsung") }
        .mapNotNull { ai ->
            val label = ai.loadLabel(pm).toString()
            if (!ai.packageName.startsWith("com.samsung") && !ai.packageName.startsWith("com.android.systemui.plugin")) return@mapNotNull null
            if (NAMES.none { label.contains(it, true) }) return@mapNotNull null
            val pi = runCatching { pm.getPackageInfo(ai.packageName, 0) }.getOrNull()
            Module(label, ai.packageName, pi?.versionName.orEmpty(), pi?.lastUpdateTime ?: 0L)
        }.sortedBy { it.label.lowercase() }
}

/** What APKMirror's feed of Samsung's apps lists: title -> newest version number found in it. */
private fun feedVersions(): Map<String, String> = runCatching {
    val cn = URL("https://www.apkmirror.com/apk/samsung-electronics-co-ltd/feed/").openConnection() as HttpURLConnection
    cn.connectTimeout = 10_000; cn.readTimeout = 15_000; cn.setRequestProperty("User-Agent", "Pulse/1.0 (github.com/xUmutKx/Pulse)")
    val xml = try { cn.inputStream.bufferedReader().use { it.readText() } } finally { cn.disconnect() }
    Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1].replace("<![CDATA[", "").replace("]]>", "").trim() }
        .mapNotNull { t -> Regex("\\d+(?:\\.\\d+)+").find(t)?.value?.let { v -> t to v } }.toMap()
}.getOrDefault(emptyMap())

private fun newer(a: String, b: String): Boolean {
    val x = a.split('.').mapNotNull { it.toIntOrNull() }; val y = b.split('.').mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
    return false
}

@Composable
fun GoodLockHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mods by remember { mutableStateOf<List<Module>?>(null) }
    var feed by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var checking by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    BackHandler { onBack() }
    // volume down = the tab on the left, volume up = the tab on the right
    DisposableEffect(Unit) {
        ExtraKeys.volume = { up -> tab = if (up) 1 else 0; true }
        onDispose { ExtraKeys.volume = null }
    }
    fun reload() { scope.launch { mods = withContext(Dispatchers.IO) { installed(ctx) } } }
    fun check() { scope.launch { checking = true; feed = withContext(Dispatchers.IO) { feedVersions() }; checking = false } }
    LaunchedEffect(Unit) { reload(); check() }
    // a module is outdated when the feed lists its name with a higher version
    fun latest(m: Module): String? = feed.entries.filter { it.key.contains(m.label, true) }.map { it.value }.maxWithOrNull(Comparator { a, b -> if (newer(a, b)) 1 else if (newer(b, a)) -1 else 0 })
    fun hasUpdate(m: Module): Boolean = latest(m)?.let { newer(it, m.version) } == true
    val list = mods.orEmpty()
    val shown = if (tab == 1) list.filter { hasUpdate(it) } else list
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("Good Lock", color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable { reload(); check() }, contentAlignment = Alignment.Center) { PIcon("refresh", T.text, 22.dp) }
            ExtraLookIcon()
        }
        // two tabs: every module, and the ones with a newer version (with their number)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tr("Modules", "Modüller") to 0, tr("Updates", "Güncellemeler") + (list.count { hasUpdate(it) }.takeIf { it > 0 }?.let { " ($it)" } ?: "") to 1).forEach { (label, i) ->
                Text(label, color = if (tab == i) Color.White else T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background2(if (tab == i) Look.accent else T.panel).clickable { tab = i }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (checking) Text("…", color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
        if (mods != null && list.isEmpty()) Text(tr("No Good Lock module found on this phone.", "Bu telefonda Good Lock modülü yok."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.pkg }) { m ->
                val upd = hasUpdate(m)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background2(T.panel).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModIcon(m.pkg)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.label, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.version + (latest(m)?.takeIf { upd }?.let { "  →  $it" } ?: ""), color = if (upd) Look.accent else T.sub, fontSize = 13.sp)
                        }
                        if (upd) Text(tr("Update", "Güncelle"), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { openMirror(ctx, m.label) }.padding(6.dp))
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Action("info", tr("App info", "Uygulama bilgisi")) { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", m.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        Action("play", tr("Open", "Aç")) { ctx.packageManager.getLaunchIntentForPackage(m.pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        Action("globe", "APKMirror") {
                            openMirror(ctx, m.label)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Action(icon: String, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, Look.accent, 18.dp)
        Spacer(Modifier.width(5.dp))
        Text(label, color = Look.accent, fontSize = 12.sp)
    }
}

private fun Modifier.background2(c: Color): Modifier = this.then(Modifier.background(c))

private fun openMirror(ctx: Context, label: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apkmirror.com/?s=" + Uri.encode(label))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The module's own launcher picture. */
@Composable
private fun ModIcon(pkg: String) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background2(T.panel), contentAlignment = Alignment.Center) { PIcon("apps", T.sub, 22.dp) }
}
