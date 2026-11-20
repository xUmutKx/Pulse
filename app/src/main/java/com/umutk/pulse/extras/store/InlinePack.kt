package com.umutk.pulse.extras.store

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.T
import com.umutk.pulse.tr
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A pack of the Extra store, offered where it is used: a line with its size and a Download button, the progress while it comes, and [onInstalled] when it is there.
 * Nothing is drawn once the pack is installed (the caller shows what it holds) or while the store's list cannot be read (offline and never opened).
 */
@Composable
fun InlinePackRow(id: String, title: String, auto: Boolean = false, onInstalled: () -> Unit = {}, ask: Boolean = true) {
    val ctx = LocalContext.current
    // the packs under 1 MB are one download ("small"): the pack asked for is that one
    val rid = SmallPack.real(id)
    var module by remember(rid) { mutableStateOf(StoreCatalog.load(ctx).firstOrNull { it.id == rid }) }
    // the list is asked for when this pack is not in the saved one yet (a manifest older than the pack)
    LaunchedEffect(rid) {
        if (module == null) { StoreCatalog.refresh(ctx, force = true); module = StoreCatalog.load(ctx).firstOrNull { it.id == rid } ?: HdWalls.fallback(rid) }
    }
    // [auto]: the first time the pack is wanted it is asked once ("Download it?"); when yes it comes by itself (the row shows how far it is), when no the row keeps its button
    if (auto && ask) PackAsk(id, title)
    if (auto && (!ask || PackConsent.state(ctx, rid) == 1)) LaunchedEffect(id) { PackAuto.ensure(ctx, id) }
    val progress by StoreInstaller.progress.collectAsState()
    val errors by StoreInstaller.errors.collectAsState()
    val rev by StoreInstaller.rev.collectAsState()
    val m = module ?: return
    val state = remember(m, progress, rev) { StoreInstaller.state(ctx, m) }
    var wasInstalled by remember(id) { mutableStateOf(state == ModuleState.INSTALLED) }
    LaunchedEffect(state) { if (state == ModuleState.INSTALLED && !wasInstalled) { wasInstalled = true; onInstalled() } }
    if (state == ModuleState.INSTALLED) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                state == ModuleState.DOWNLOADING -> tr("Downloading ", "İndiriliyor ") + "${((progress[rid] ?: 0f) * 100).toInt()}%"
                state == ModuleState.FAILED -> errors[rid] ?: tr("Could not download", "İndirilemedi")
                else -> formatMb(m.boyut)
            }
            Text(sub, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (state != ModuleState.DOWNLOADING) PButton(if (state == ModuleState.FAILED) tr("Try again", "Tekrar dene") else tr("Download", "İndir"), compact = true) { StoreInstaller.download(ctx, m) }
    }
}

/** What the person answered when a pack was first wanted: 0 not asked, 1 yes, 2 no. */
object PackConsent {
    private fun sp(c: Context) = c.getSharedPreferences("pack_consent", Context.MODE_PRIVATE)
    fun state(c: Context, id: String): Int = sp(c).getInt(id, 0)
    fun set(c: Context, id: String, yes: Boolean) { sp(c).edit().putInt(id, if (yes) 1 else 2).apply() }
}

/** The question the first time a pack is wanted: "Download it?" with its size. Not asked when the pack is on the phone or was answered before. */
@Composable
fun PackAsk(id: String, title: String) {
    val ctx = LocalContext.current
    val rid = SmallPack.real(id)
    var show by remember(id) { mutableStateOf(!PackAuto.installed(ctx, id) && PackConsent.state(ctx, rid) == 0) }
    var module by remember(rid) { mutableStateOf(StoreCatalog.load(ctx).firstOrNull { it.id == rid }) }
    LaunchedEffect(rid, show) {
        if (show && module == null) { StoreCatalog.refresh(ctx, force = true); module = StoreCatalog.load(ctx).firstOrNull { it.id == rid } }
    }
    if (show) com.umutk.pulse.PDialog(
        title = title,
        text = tr("Download it now" + (module?.let { " (" + formatMb(it.boyut) + ")" } ?: "") + "? It is kept on the phone and can be removed in Settings > About > Downloaded packs.",
            "Şimdi indirilsin mi" + (module?.let { " (" + formatMb(it.boyut) + ")" } ?: "") + "? Telefonda saklanır, Ayarlar > Hakkında > İndirilen paketler'den kaldırılabilir."),
        confirm = tr("Download", "İndir"),
        onConfirm = { PackConsent.set(ctx, rid, true); show = false; PackAuto.ensure(ctx, id) },
        onDismiss = { PackConsent.set(ctx, rid, false); show = false },
    )
}

/**
 * The higher-quality versions of the stock wallpapers, one pack per look (Windows 11 in 4K, Windows 10 and 7 in 1920 x 1200+): the same pictures as the ones in the app, from the
 * same sources, under the same names. A pack, once installed, takes the place of the small picture wherever that one is drawn.
 */
object HdWalls {
    private val FAMILIES = listOf("wallpapers-hd-win11" to "win11_wall_", "wallpapers-hd-win10" to "wall_w10_", "wallpapers-hd-win7" to "wall_w7_")

    /** The big file of the stock wallpaper [resId] when its pack is installed, else null (the picture inside the app is used). */
    fun file(c: Context, resId: Int): File? {
        val name = runCatching { c.resources.getResourceEntryName(resId) }.getOrNull() ?: return null
        val pack = FAMILIES.firstOrNull { name.startsWith(it.second) }?.first ?: return null
        return File(StoreInstaller.dataDir(c, pack), "files/$name.webp").takeIf { it.isFile }
    }

    /** The HD wallpaper packs as the app knows them, so the download button shows even when the list from GitHub could not be read (offline, an old saved list). */
    fun fallback(id: String): ExtraModule? {
        val (en, tr, size, sha) = when (id) {
            "wallpapers-hd-win11" -> listOf("Windows 11 wallpapers in 4K", "Windows 11 duvar kağıtları 4K", "4142368", "3b0eadc412da8bb799d53e4f5ecc931a5057d11b25b6cc6758a488498d0f4f00")
            "wallpapers-hd-win10" -> listOf("Windows 10 wallpapers in 1920 x 1200", "Windows 10 duvar kağıtları 1920 x 1200", "1330482", "d7d3747983bbe70e6cb67b81d185198ef69f05b89b09a65e923af451a28e4130")
            "wallpapers-hd-win7" -> listOf("Windows 7 wallpapers in 1920 x 1200", "Windows 7 duvar kağıtları 1920 x 1200", "2170762", "9bfc2dae8d801c1100d64d9f2999d472edaa4597582d48c376e0df6a4b0c4c2d")
            else -> return null
        }
        return ExtraModule(id, en, tr, "veri", "gorsel", size.toLong(), "1.0.0", "https://raw.githubusercontent.com/xUmutKx/Pulse/main/extras/packs/$id.zip", sha, null, null, null, null,
            "LicenseRef-Microsoft-default-wallpapers", null, null, en, tr, 120L, "2026-11-17T00:00:00Z")
    }

    /** The pack of a look and what it is called, or null for a look without one. */
    fun packFor(lookId: String): Pair<String, String>? = when (lookId) {
        "win11" -> "wallpapers-hd-win11" to tr("Windows 11 wallpapers in 4K", "Windows 11 duvar kağıtları 4K")
        "win10", "win10black" -> "wallpapers-hd-win10" to tr("Windows 10 wallpapers in 1920 x 1200", "Windows 10 duvar kağıtları 1920 x 1200")
        "win7" -> "wallpapers-hd-win7" to tr("Windows 7 wallpapers in 1920 x 1200", "Windows 7 duvar kağıtları 1920 x 1200")
        else -> null
    }
}

/** Under a look's wallpapers: the download of their higher-quality versions (a look with none shows nothing). */
@Composable
fun HdWallpaperRow(lookId: String) {
    val (id, title) = HdWalls.packFor(lookId) ?: return
    InlinePackRow(id, title, auto = true, ask = false)
}

/**
 * A pack that comes by itself the first time something needs it (an icon pack of a look, the sounds of the birds, the exercises of the gym): no question asked, one try per run.
 * What is not there yet is drawn without it (the vector icons, an empty list) until the pack is installed; [StoreInstaller.rev] changes then, so screens can read again.
 */
object PackAuto {
    private val asked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when the pack's files are unzipped on the phone. */
    fun installed(c: Context, id: String): Boolean = StoreInstaller.dataDir(c, id).let { d -> d.isDirectory && !d.list().isNullOrEmpty() }

    fun ensure(c: Context, id0: String) {
        val app = c.applicationContext
        // the packs under 1 MB are one download ("small")
        val id = SmallPack.real(id0)
        if (installed(app, id0) || !asked.add(id)) return
        scope.launch {
            var m = StoreCatalog.load(app).firstOrNull { it.id == id }
            // the saved list may be older than the pack (or never read): it is asked for once
            if (m == null) { StoreCatalog.refresh(app, force = true); m = StoreCatalog.load(app).firstOrNull { it.id == id } ?: HdWalls.fallback(id) }
            if (m != null && StoreInstaller.state(app, m) != ModuleState.INSTALLED) StoreInstaller.download(app, m)
            else if (m == null) asked.remove(id)   // offline: tried again next time
        }
    }

    /** First start: the small packs (one download of about 2 MB) come by themselves, without a question, once; when it cannot (offline) it is tried again at the next start. */
    fun firstRun(c: Context) {
        PackConsent.set(c, SmallPack.ID, true)
        ensure(c, "icons-xp")
    }
}

/** More pictures of a look (Windows XP, Windows 10): a pack of the store, listed under the stock pictures; a tap gives the file to use as the wallpaper. */
object MoreWalls {
    fun packFor(lookId: String): Pair<String, String>? = when (lookId) {
        "xp" -> "wallpapers-more-xp" to tr("More Windows XP wallpapers", "Daha fazla Windows XP duvar kağıdı")
        "win10", "win10black" -> "wallpapers-more-win10" to tr("More Windows 10 wallpapers", "Daha fazla Windows 10 duvar kağıdı")
        else -> null
    }

    fun files(c: Context, lookId: String): List<File> {
        val (id, _) = packFor(lookId) ?: return emptyList()
        return File(StoreInstaller.dataDir(c, id), "files").listFiles { f -> f.isFile && f.extension.equals("webp", true) }?.sortedBy { it.name }.orEmpty()
    }
}

@Composable
fun MoreWallpapersStrip(lookId: String, onPick: (File) -> Unit) {
    val (id, title) = MoreWalls.packFor(lookId) ?: return
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val files = remember(tick, lookId) { MoreWalls.files(ctx, lookId) }
    Column(Modifier.fillMaxWidth()) {
        InlinePackRow(id, title, auto = true) { tick++ }
        if (files.isNotEmpty()) {
            Text(title, color = T.sub, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                files.forEach { f ->
                    val thumb = remember(f.path) { runCatching { android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() }
                    Column(Modifier.width(110.dp).clickable { onPick(f) }) {
                        Box(Modifier.width(110.dp).height(66.dp).clip(RoundedCornerShape(6.dp))) {
                            if (thumb != null) Image(thumb, f.nameWithoutExtension, Modifier.width(110.dp).height(66.dp), contentScale = ContentScale.Crop)
                        }
                        Text(f.nameWithoutExtension, color = T.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
