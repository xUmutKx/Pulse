package com.umutk.pulse.extras.store

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * A pack of the Extra store, offered where it is used: a line with its size and a Download button, the progress while it comes, and [onInstalled] when it is there.
 * Nothing is drawn once the pack is installed (the caller shows what it holds) or while the store's list cannot be read (offline and never opened).
 */
@Composable
fun InlinePackRow(id: String, title: String, onInstalled: () -> Unit = {}) {
    val ctx = LocalContext.current
    var module by remember(id) { mutableStateOf(StoreCatalog.load(ctx).firstOrNull { it.id == id }) }
    // the list is asked for when this pack is not in the saved one yet (a manifest older than the pack)
    LaunchedEffect(id) {
        if (module == null) { StoreCatalog.refresh(ctx, force = true); module = StoreCatalog.load(ctx).firstOrNull { it.id == id } }
    }
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

/** The high-resolution wallpapers pack (4K and Full HD pictures), unzipped by the store into its own folder. */
object HdWalls {
    const val ID = "wallpapers-hd"
    class Item(val id: String, val name: String, val file4k: File, val fileFhd: File, val credit: String)

    fun installed(c: Context): Boolean = File(StoreInstaller.dataDir(c, ID), "pack.json").isFile

    fun items(c: Context, turkish: Boolean): List<Item> = runCatching {
        val dir = StoreInstaller.dataDir(c, ID)
        val arr = JSONObject(File(dir, "pack.json").readText()).getJSONArray("wallpapers")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val a = File(dir, o.getString("file4k")); val b = File(dir, o.getString("fileFhd"))
            if (!a.isFile && !b.isFile) null else Item(o.getString("id"), (if (turkish) o.optString("tr") else o.optString("en")).ifEmpty { o.getString("id") }, a, b, o.optString("credit"))
        }
    }.getOrDefault(emptyList())

    /** The 4K picture on a tablet (smallest width 600 dp or more), the Full HD one on a phone; whichever of the two is there. */
    fun fileFor(c: Context, it: Item): File {
        val tablet = c.resources.configuration.smallestScreenWidthDp >= 600
        return if (tablet) it.file4k.takeIf { f -> f.isFile } ?: it.fileFhd else it.fileFhd.takeIf { f -> f.isFile } ?: it.file4k
    }
}

/** The 4K / Full HD wallpapers: the download row first, then, once the pack is there, a strip of pictures; a tap gives the file for this screen. */
@Composable
fun HdWallpaperStrip(onPick: (File) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val turkish = tr("en", "tr") == "tr"
    val items = remember(tick) { HdWalls.items(ctx, turkish) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InlinePackRow(HdWalls.ID, tr("4K and Full HD wallpapers", "4K ve Full HD duvar kağıtları")) { tick++ }
        if (items.isNotEmpty()) {
            Text(tr("4K and Full HD wallpapers", "4K ve Full HD duvar kağıtları"), color = T.sub, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { it ->
                    val thumb = remember(it.id) { runCatching { android.graphics.BitmapFactory.decodeFile(it.fileFhd.takeIf { f -> f.isFile }?.path ?: it.file4k.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap() }.getOrNull() }
                    Column(Modifier.width(120.dp).clickable { onPick(HdWalls.fileFor(ctx, it)) }) {
                        Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(6.dp))) {
                            if (thumb != null) Image(thumb, it.name, Modifier.fillMaxWidth().height(68.dp), contentScale = ContentScale.Crop)
                        }
                        Text(it.name, color = T.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
