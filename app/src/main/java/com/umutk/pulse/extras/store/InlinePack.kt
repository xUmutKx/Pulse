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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A pack of the Extra store, offered where it is used: a line with its size and a Download button, the progress while it comes, and [onInstalled] when it is there.
 * Nothing is drawn once the pack is installed (the caller shows what it holds) or while the store's list cannot be read (offline and never opened).
 */
@Composable
fun InlinePackRow(id: String, title: String, auto: Boolean = false, onInstalled: () -> Unit = {}) {
    val ctx = LocalContext.current
    var module by remember(id) { mutableStateOf(StoreCatalog.load(ctx).firstOrNull { it.id == id }) }
    // the list is asked for when this pack is not in the saved one yet (a manifest older than the pack)
    LaunchedEffect(id) {
        if (module == null) { StoreCatalog.refresh(ctx, force = true); module = StoreCatalog.load(ctx).firstOrNull { it.id == id } }
    }
    // [auto]: the download starts by itself, without a tap (the row then shows how far it is)
    if (auto) LaunchedEffect(id) { PackAuto.ensure(ctx, id) }
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
                state == ModuleState.DOWNLOADING -> tr("Downloading ", "İndiriliyor ") + "${((progress[id] ?: 0f) * 100).toInt()}%"
                state == ModuleState.FAILED -> errors[id] ?: tr("Could not download", "İndirilemedi")
                else -> formatMb(m.boyut)
            }
            Text(sub, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (state != ModuleState.DOWNLOADING) PButton(if (state == ModuleState.FAILED) tr("Try again", "Tekrar dene") else tr("Download", "İndir"), compact = true) { StoreInstaller.download(ctx, m) }
    }
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
    InlinePackRow(id, title, auto = true)
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

    fun ensure(c: Context, id: String) {
        val app = c.applicationContext
        if (installed(app, id) || !asked.add(id)) return
        scope.launch {
            var m = StoreCatalog.load(app).firstOrNull { it.id == id }
            // the saved list may be older than the pack (or never read): it is asked for once
            if (m == null) { StoreCatalog.refresh(app, force = true); m = StoreCatalog.load(app).firstOrNull { it.id == id } }
            if (m != null && StoreInstaller.state(app, m) != ModuleState.INSTALLED) StoreInstaller.download(app, m)
            else if (m == null) asked.remove(id)   // offline: tried again next time
        }
    }
}
