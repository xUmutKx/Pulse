package com.umutk.pulse.extras.goodlock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A manager for Samsung's Good Lock modules: which of them are installed and in what version, a check for newer versions in Samsung's own store, and a way to the
 * app info screen and to the module's page. Written from the description of the idea (a lighter client for the modules); nothing of another program's code is in it.
 */
private class Module(val label: String, val pkg: String, val version: String, val installedAt: Long, val code: Long = 0L)

/** The names of the modules; the installed apps are matched to them by their label, so the package names need not be known. */
private val NAMES = listOf("Good Lock", "QuickStar", "NavStar", "Keys Cafe", "LockStar", "ClockFace", "Home Up", "MultiStar", "NiceCatch", "Nice Shot", "One Hand Operation",
    "Theme Park", "Wonderland", "SoundAssistant", "RegiStar", "Routines+", "Edge lighting", "Pentastic", "Camera Assistant", "Display Assistant", "Battery Assistant",
    "Good Guardians", "Good Lock Plugin", "Edge Touch", "Wallpaper Classic", "Nice Lock", "Gallery Assistant", "Keyboard Assistant", "Dex Assistant", "Pattern Lock")

/** The modules grouped by what they change, shown as the headings of the "Not installed" list. */
private val CATEGORIES = listOf(
    tr("Lock screen", "Kilit ekranı") to listOf("LockStar", "Nice Lock", "Pattern Lock", "Keys Cafe", "ClockFace"),
    tr("Home and edge", "Ana ekran ve kenar") to listOf("Home Up", "Edge lighting", "Edge Touch", "NavStar", "Pentastic"),
    tr("Quick panel and routines", "Hızlı panel ve rutinler") to listOf("QuickStar", "Routines+", "Good Guardians"),
    tr("Camera and gallery", "Kamera ve galeri") to listOf("NiceCatch", "Nice Shot", "Camera Assistant", "Gallery Assistant"),
    tr("Sound, display and battery", "Ses, ekran ve pil") to listOf("SoundAssistant", "Display Assistant", "Battery Assistant"),
    tr("Look and theme", "Görünüm ve tema") to listOf("Theme Park", "Wonderland", "Wallpaper Classic"),
    tr("Input and one hand", "Giriş ve tek el") to listOf("One Hand Operation", "Keyboard Assistant"),
    tr("Other", "Diğer") to listOf("MultiStar", "RegiStar", "Dex Assistant", "Good Lock Plugin", "Good Lock"),
)

private fun installed(c: Context): List<Module> {
    val pm = c.packageManager
    return pm.getInstalledApplications(0)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.startsWith("com.samsung") }
        .mapNotNull { ai ->
            val label = ai.loadLabel(pm).toString()
            if (!ai.packageName.startsWith("com.samsung") && !ai.packageName.startsWith("com.android.systemui.plugin")) return@mapNotNull null
            if (NAMES.none { label.contains(it, true) }) return@mapNotNull null
            val pi = runCatching { pm.getPackageInfo(ai.packageName, 0) }.getOrNull()
            Module(label, ai.packageName, pi?.versionName.orEmpty(), pi?.lastUpdateTime ?: 0L, pi?.let { com.umutk.pulse.Compat.longVersion(it) } ?: 0L)
        }.sortedBy { it.label.lowercase() }
}

/** What Samsung's store says about an app: the newest version code and name. */
private class StoreInfo(val code: Long, val name: String)

/** Packages of the modules that are not installed, for the version their store page shows (a wrong name is simply not answered). */
private val KNOWN = mapOf("Good Lock" to "com.samsung.android.goodlock", "QuickStar" to "com.samsung.android.qstuner", "NavStar" to "com.samsung.systemui.navillera",
    "Keys Cafe" to "com.samsung.android.keyscafe", "LockStar" to "com.samsung.systemui.lockstar", "Home Up" to "com.samsung.android.app.homestar", "MultiStar" to "com.samsung.android.multistar",
    "SoundAssistant" to "com.samsung.android.soundassistant", "Theme Park" to "com.samsung.android.themedesigner")

/**
 * The newest version of an app in Samsung's own store (the answer of the store's download service, the one the Galaxy Store itself asks): no download happens,
 * only the version code and name are read. Null when the store does not know the package or does not answer. (APKMirror's feed, used before, refuses apps with HTTP 403.)
 */
private fun storeInfo(pkg: String): StoreInfo? = runCatching {
    val model = if (android.os.Build.MANUFACTURER.equals("samsung", true)) android.os.Build.MODEL else "SM-S918B"
    val url = "https://vas.samsungapps.com/stub/stubDownload.as?appId=$pkg&deviceId=$model&mcc=450&mnc=11&csc=TUR&sdkVer=${android.os.Build.VERSION.SDK_INT}&pd=0&systemId=1&callerId=com.sec.android.app.samsungapps&abiType=64&extuk=0191d6627f38685f"
    val cn = URL(url).openConnection() as HttpURLConnection
    cn.connectTimeout = 10_000; cn.readTimeout = 15_000
    val xml = try { if (cn.responseCode != 200) return@runCatching null; cn.inputStream.bufferedReader().use { it.readText() } } finally { cn.disconnect() }
    if (Regex("<resultCode>(\\d+)</resultCode>").find(xml)?.groupValues?.get(1) != "1") return@runCatching null
    val code = Regex("<versionCode>(\\d+)</versionCode>").find(xml)?.groupValues?.get(1)?.toLongOrNull() ?: return@runCatching null
    StoreInfo(code, Regex("<versionName>(.*?)</versionName>").find(xml)?.groupValues?.get(1).orEmpty())
}.getOrNull()

/**
 * One screen: the installed modules first (a tap opens the module, an Update button when the feed has a newer one), then the
 * modules this phone does not have, grouped by what they change, each with a Download button.
 */
@Composable
fun GoodLockHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mods by remember { mutableStateOf<List<Module>?>(null) }
    val store = remember { androidx.compose.runtime.mutableStateMapOf<String, StoreInfo>() }
    var feedErr by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    BackHandler { onBack() }
    // the installed modules are read, then Samsung's store is asked for each of them (and for the known ones that are missing), all at the same time
    fun reload() {
        scope.launch {
            checking = true; feedErr = null
            val ms = withContext(Dispatchers.IO) { installed(ctx) }
            mods = ms
            val pkgs = (ms.map { it.pkg } + KNOWN.values).distinct()
            val got = withContext(Dispatchers.IO) { kotlinx.coroutines.coroutineScope { pkgs.map { pk -> async { pk to storeInfo(pk) } }.awaitAll() } }
            got.forEach { (pk, i) -> if (i != null) store[pk] = i }
            if (got.none { it.second != null }) feedErr = tr("no answer", "cevap yok")
            checking = false
        }
    }
    fun check() = reload()
    LaunchedEffect(Unit) { reload() }
    // a module is outdated when the store lists a higher version code than the installed one
    fun latest(m: Module): String? = store[m.pkg]?.name?.takeIf { it.isNotBlank() }
    fun hasUpdate(m: Module): Boolean = store[m.pkg]?.let { it.code > m.code && m.code > 0L } == true
    val list = mods.orEmpty()
    // the names of the modules this phone does not have, in their groups (a name in no group goes to "Other")
    val missing = NAMES.filter { n -> list.none { it.label.contains(n, true) } }
    val grouped = CATEGORIES.map { (title, names) -> title to names.filter { it in missing } }.toMutableList()
    val placed = grouped.flatMap { it.second }.toSet()
    val rest = missing.filter { it !in placed }
    if (rest.isNotEmpty()) grouped[grouped.lastIndex] = grouped.last().first to (grouped.last().second + rest)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("Good Lock", color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable { reload(); check() }, contentAlignment = Alignment.Center) { PIcon("refresh", T.text, 22.dp) }
            ExtraLookIcon()
        }
        // a failed check is said out loud, never shown as "no updates"
        feedErr?.let { Text(tr("The update check failed ($it). Versions shown are the installed ones only.", "Güncelleme denetimi başarısız ($it). Yalnızca kurulu sürümler gösteriliyor."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Heading(tr("Installed", "Kurulu") + " (${list.size})") }
            items(list, key = { it.pkg }) { m ->
                val upd = hasUpdate(m)
                // a tap opens the module itself
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(T.panel)
                    .clickable { openModule(ctx, m.pkg) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModIcon(m.pkg, m.label)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.label, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.version + (latest(m)?.takeIf { upd }?.let { "  →  $it" } ?: ""), color = if (upd) Look.accent else T.sub, fontSize = 13.sp)
                    }
                    if (upd) Text(tr("Update", "Güncelle"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Look.accent).clickable { openStore(ctx, m.pkg) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    Spacer(Modifier.width(6.dp))
                    // the system asks for the confirmation itself
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { uninstall(ctx, m.pkg) }, contentAlignment = Alignment.Center) {
                        PIcon("delete", T.sub, 20.dp)
                    }
                }
            }
            grouped.forEach { (title, names) ->
                if (names.isNotEmpty()) {
                    item { Heading(title) }
                    items(names, key = { "n_$it" }) { n ->
                        // no transparent panel behind the row: the picture and the Download button stand on the wallpaper
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ModPlaceholder(n)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                KNOWN[n]?.let { store[it]?.name }?.takeIf { it.isNotBlank() }?.let { Text(it, color = T.sub, fontSize = 13.sp) }
                            }
                            Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Look.accent, RoundedCornerShape(10.dp)).clickable { KNOWN[n]?.let { openStore(ctx, it) } ?: openMirror(ctx, n) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                PIcon("download", Look.accent, 16.dp); Spacer(Modifier.width(5.dp))
                                Text(tr("Download", "İndir"), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** A module's own picture when it is installed; the first letter on a colour of its name otherwise. */
@Composable
private fun ModIcon(pkg: String, label: String) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
    else ModPlaceholder(label)
}

/** A picture stand-in for a module that is not installed: its first letter on its own colour. */
@Composable
private fun ModPlaceholder(name: String) {
    val hue = (kotlin.math.abs(name.hashCode()) % 360).toFloat()
    val tint = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, .5f, .7f)))
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tint), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The module's own screen: its launcher entry, else any screen it has, else the app's info page, so a tap always does something
 * and never just starts Good Lock.
 */
private fun openModule(ctx: Context, pkg: String) {
    val pm = ctx.packageManager
    val launch = pm.getLaunchIntentForPackage(pkg)
    val any = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(pkg), 0).firstOrNull()?.activityInfo }.getOrNull()
    val intent = launch ?: any?.let { Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name) }
    if (intent != null && runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The system's uninstall screen for [pkg]; the system asks for the confirmation. */
private fun uninstall(ctx: Context, pkg: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The app's page in the Galaxy Store (where the update is made), else its page on the store's website. */
private fun openStore(ctx: Context, pkg: String) {
    val ok = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://galaxystore.samsung.com/detail/$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The page of the newest release when the feed gave one, else the search for the name. */
private fun openMirror(ctx: Context, label: String, link: String? = null) {
    // the release page of the feed when it has one; else a web search limited to APKMirror and Samsung (the site's own search lists other apps)
    val url = link?.takeIf { it.startsWith("https://www.apkmirror.com/") }
        ?: ("https://www.google.com/search?q=" + Uri.encode("site:apkmirror.com Samsung \"$label\" apk"))
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
