package com.umutk.pulse.files.home

import androidx.compose.foundation.layout.navigationBarsPadding
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.features.ExNav
import com.umutk.pulse.features.exSize
import com.umutk.pulse.features.exThumb
import com.umutk.pulse.files.recent.RecentStore
import com.umutk.pulse.frame
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where the bottom bar of the standalone Files app and the home page send the app. */
object FilesHomeNav {
    /** Set by the bottom bar's Search: the explorer opens its search field when it appears. */
    var openSearch by mutableStateOf(false)
    /** Browse pressed while the Browse page is already open: the explorer goes back to the top folder. */
    var toRoot by mutableStateOf(false)
    /** A file tapped on the home page: the Browse tab opens its folder and the file in its own way (an archive shows its contents inside). */
    var pendingFile by mutableStateOf<File?>(null)
    /** A type tapped on the home page ("images", "videos", ...): the types page opens straight on that list. */
    var kind by mutableStateOf<String?>(null)
}

/** The folders opened lately (newest first), kept next to the recent files. */
object RecentDirs {
    private const val KEY = "ex_recent_dirs"
    private fun prefs(c: Context) = com.umutk.pulse.filesPrefs(c)
    fun list(c: Context): List<String> = prefs(c).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
    fun add(c: Context, path: String) {
        prefs(c).edit().putString(KEY, (listOf(path) + list(c).filter { it != path }).take(12).joinToString("\n")).apply()
    }
    fun clear(c: Context) = prefs(c).edit().remove(KEY).apply()
}

private class Part(val key: String, val en: String, val tr: String, val icon: String, val color: Color, var bytes: Long = 0, var count: Int = 0)

private val PARTS = listOf(
    Part("images", "Images", "Resimler", "image", Color(0xFFE5484D)),
    Part("videos", "Videos", "Videolar", "video", Color(0xFF8E4EC6)),
    Part("audio", "Audio", "Ses", "music", Color(0xFF12A594)),
    Part("docs", "Documents", "Belgeler", "document", Color(0xFF3E63DD)),
    Part("archives", "Archives", "Arşivler", "zip", Color(0xFFD6409F)),
    Part("apps", "Apps", "Uygulamalar", "apps", Color(0xFF46A758)),
)

private class Stats(val total: Long, val free: Long, val parts: List<Part>, val other: Long)

private fun partOf(media: Int, mime: String?): String? {
    val m = mime.orEmpty()
    return when {
        media == 1 || m.startsWith("image/") -> "images"
        media == 3 || m.startsWith("video/") -> "videos"
        media == 2 || m.startsWith("audio/") -> "audio"
        m == "application/vnd.android.package-archive" -> "apps"
        m in setOf("application/zip", "application/x-rar-compressed", "application/vnd.rar", "application/x-7z-compressed", "application/gzip", "application/x-tar", "application/x-bzip2", "application/x-xz") -> "archives"
        m == "application/pdf" || m.startsWith("text/") || m.startsWith("application/msword") || m.contains("officedocument") || m.contains("ms-excel") || m.contains("ms-powerpoint") || m == "application/rtf" -> "docs"
        else -> null
    }
}

/** The last numbers of the home page, kept in memory and on disk: the page opens with them at once and the fresh ones replace them a moment later. */
private object StatsCache {
    private var mem: Stats? = null
    private fun sp(c: Context) = c.getSharedPreferences("files_home_cache", Context.MODE_PRIVATE)
    fun get(c: Context): Stats? = mem ?: runCatching {
        val p = sp(c)
        if (!p.contains("total")) return@runCatching null
        val parts = PARTS.map { Part(it.key, it.en, it.tr, it.icon, it.color).also { x -> x.bytes = p.getLong("b_" + it.key, 0L); x.count = p.getInt("n_" + it.key, 0) } }
        Stats(p.getLong("total", 0L), p.getLong("free", 0L), parts, p.getLong("other", 0L))
    }.getOrNull().also { mem = it }
    fun put(c: Context, s: Stats) {
        mem = s
        sp(c).edit().apply { putLong("total", s.total); putLong("free", s.free); putLong("other", s.other); s.parts.forEach { putLong("b_" + it.key, it.bytes); putInt("n_" + it.key, it.count) } }.apply()
    }
}

private fun loadStats(ctx: Context): Stats {
    val top = Environment.getExternalStorageDirectory()
    val st = runCatching { StatFs(top.path) }.getOrNull()
    val total = st?.totalBytes ?: 0L; val free = st?.availableBytes ?: 0L
    val parts = PARTS.map { Part(it.key, it.en, it.tr, it.icon, it.color) }
    if (RecentStore.canListAll(ctx)) runCatching {
        ctx.contentResolver.query(MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val k = partOf(c.getInt(1), c.getString(2)) ?: continue
                val p = parts.first { it.key == k }
                p.bytes += c.getLong(0); p.count++
            }
        }
    }
    val other = (total - free - parts.sumOf { it.bytes }).coerceAtLeast(0L)
    return Stats(total, free, parts, other)
}

/**
 * The first page of the Files app: free space, what takes the room, recent files and folders, and the file types. Black in the Material looks, with the look's own accent colour;
 * the Windows looks draw it with their own panels and borders.
 */
@Composable
fun FilesHome(onTab: (String) -> Unit, onOpenDir: (String) -> Unit, onOpenFile: (File) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val mat = T.material
    val bg = if (mat) Color.Black else T.bg
    val fg = if (mat) Color.White else T.text
    val sub = if (mat) Color(0xFF8E8E93) else T.sub
    val tile = if (mat) Color(0xFF161616) else T.panel
    val accent = T.accent
    val mono = if (mat) FontFamily.Monospace else FontFamily.Default
    // the last numbers show at once; the fresh ones come in behind them
    var stats by remember { mutableStateOf(StatsCache.get(ctx)) }
    androidx.compose.runtime.LaunchedEffect(Unit) { val n = withContext(Dispatchers.IO) { loadStats(ctx) }; StatsCache.put(ctx, n); stats = n }
    val recentFiles = remember { RecentStore.list(ctx).map { File(it) }.filter { it.isFile }.take(12) }
    var dirs by remember { mutableStateOf(RecentDirs.list(ctx).filter { File(it).isDirectory }) }
    fun box(m: Modifier): Modifier = if (mat) m.clip(RoundedCornerShape(14.dp)).background(tile) else m.background(tile).frame(T)

    @Composable fun Caption(text: String, action: String? = null, onAction: () -> Unit = {}) {
        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text.uppercase(), color = sub, fontSize = 12.sp, fontFamily = mono, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            if (action != null) Text(action, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { onAction() })
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(bg).padding(horizontal = 18.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(tr("Files", "Dosyalar"), color = fg, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(".", color = accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(44.dp).clickable { onSettings() }, contentAlignment = Alignment.Center) { PIcon("settings", fg, 26.dp) }
            }
        }
        item {
            Caption(tr("Storage", "Depolama"), tr("Clean up", "Temizle")) { onTab("cache") }
            val s = stats
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                val freeGb = s?.free?.let { it / 1_073_741_824.0 }
                Text(if (freeGb == null) "—" else if (freeGb >= 10) "%.0f".format(freeGb) else "%.1f".format(freeGb), color = fg, fontSize = 72.sp, fontWeight = FontWeight.Light)
                Text(" GB", color = fg, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 14.dp))
                Text(" " + tr("free", "boş"), color = sub, fontSize = 15.sp, fontFamily = mono, modifier = Modifier.padding(bottom = 14.dp).weight(1f))
                if (s != null) Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 14.dp)) {
                    Text(exSize(s.total - s.free) + " " + tr("used", "kullanılan"), color = accent, fontSize = 15.sp, fontFamily = mono)
                    Text(tr("of ", "/ ") + exSize(s.total), color = sub, fontSize = 15.sp, fontFamily = mono)
                }
            }
            if (s != null && s.total > 0) {
                Spacer(Modifier.height(12.dp))
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val gap = 2.dp.toPx(); var x = 0f
                    val track = if (mat) Color(0xFF222222) else T.line
                    drawRoundRect(track, Offset.Zero, size, CornerRadius(4.dp.toPx()))
                    (s.parts.filter { it.bytes > 0 } ).forEach { p ->
                        val w = size.width * p.bytes / s.total
                        if (w > 1f) drawRect(p.color, Offset(x, 0f), Size((w - gap).coerceAtLeast(1f), size.height))
                        x += w
                    }
                    val ow = size.width * s.other / s.total
                    if (ow > 1f) drawRect(Color(0xFF8B8D98), Offset(x, 0f), Size((ow - gap).coerceAtLeast(1f), size.height))
                }
                Spacer(Modifier.height(14.dp))
                val legend = s.parts.filter { it.bytes > 0 }.map { Triple(it.color, tr(it.en, it.tr), exSize(it.bytes)) } + Triple(Color(0xFF8B8D98), tr("Other", "Diğer"), exSize(s.other)) + Triple(if (mat) Color(0xFF2A2A2A) else T.line, tr("Free", "Boş"), exSize(s.free))
                legend.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        row.forEach { (c, n, v) ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
                                Text(" $n", color = fg, fontSize = 14.sp, maxLines = 1)
                                Text(" $v", color = sub, fontSize = 12.sp, fontFamily = mono, maxLines = 1)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (recentFiles.isNotEmpty()) {
            item { Caption(tr("Recent", "Son"), tr("See all", "Tümü")) { onTab("files") } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentFiles, key = { it.path }) { f ->
                        Column(Modifier.width(112.dp).clickable { onOpenFile(f) }) {
                            Box(box(Modifier.fillMaxWidth().height(96.dp)), contentAlignment = Alignment.Center) {
                                val th = exThumb(f)
                                if (th != null) androidx.compose.foundation.Image(th, null, Modifier.fillMaxSize().clip(RoundedCornerShape(if (mat) 14.dp else 0.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                else PIcon("document", sub, 34.dp)
                            }
                            Text(f.name, color = fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(DateUtils.getRelativeTimeSpanString(f.lastModified(), System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString(), color = sub, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (dirs.isNotEmpty()) {
            item { Caption(tr("Recent folders", "Son klasörler"), tr("Clear", "Temizle")) { RecentDirs.clear(ctx); dirs = emptyList() } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(dirs, key = { it }) { d ->
                        Row(box(Modifier).clickable { onOpenDir(d) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon("folder", accent, 22.dp)
                            Text("  " + (File(d).name.ifEmpty { tr("Internal storage", "Dahili depolama") }), color = fg, fontSize = 16.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        item { Caption(tr("Types", "Türler")) }
        items(stats?.parts.orEmpty().filter { it.bytes > 0 || it.count > 0 }, key = { it.key }) { p ->
            Row(Modifier.fillMaxWidth().clickable { FilesHomeNav.kind = p.key; onTab("types") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(if (mat) 14.dp else 4.dp)).background(p.color), contentAlignment = Alignment.Center) { PIcon(p.icon, Color.White, 28.dp) }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(tr(p.en, p.tr), color = fg, fontSize = 18.sp)
                    Text("%,d ".format(p.count) + tr("files", "dosya"), color = sub, fontSize = 13.sp, fontFamily = mono)
                }
                Text(exSize(p.bytes), color = fg, fontSize = 15.sp, fontFamily = mono)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Opens a file with whatever the phone offers for it. */
fun openFileWithSystem(ctx: Context, f: File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val mime = com.umutk.pulse.features.fileMime(f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** The bottom bar of the standalone Files app: Home, Browse, Share, Search and Analyze, drawn in the look's colours. */
@Composable
fun FilesNavBar(tab: String, onTab: (String) -> Unit, onShare: () -> Unit) {
    val mat = T.material
    val bg = if (mat) Color.Black else T.panel
    val fg = if (mat) Color(0xFF9A9A9F) else T.sub
    val accent = T.accent
    var menu by remember { mutableStateOf(false) }
    val openTool = com.umutk.pulse.LocalOpenTool.current
    val items = listOf(
        Triple("home", "home" to tr("Home", "Ana"), "home"), Triple("files", "folder" to tr("Browse", "Gözat"), "files"), Triple("share", "share" to tr("Share", "Paylaş"), "share"),
        Triple("search", "search" to tr("Search", "Ara"), "search"), Triple("more", "more" to tr("More", "Diğer"), "more"),
    )
    Row(Modifier.fillMaxWidth().background(bg).then(if (mat) Modifier else Modifier.frame(T)).navigationBarsPadding().padding(vertical = 8.dp)) {
        items.forEach { (id, iconLabel, target) ->
            val on = tab == target || (id == "more" && tab in setOf("more", "tree", "types", "cache"))
            Column(Modifier.weight(1f).clickable {
                when (id) {
                    "more" -> onTab("more")
                    "share" -> onShare()
                    "search" -> { FilesHomeNav.openSearch = true; onTab("files") }
                    "files" -> if (tab == "files") FilesHomeNav.toRoot = true else onTab(target)
                    else -> onTab(target)
                }
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PIcon(iconLabel.first, if (on) accent else fg, 26.dp)
                        Text(iconLabel.second, color = if (on) accent else fg, fontSize = 12.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
                    }
                    if (id == "more") com.umutk.pulse.ThemedMenu(menu, { menu = false }) {
                        com.umutk.pulse.ThemedMenuItem(tr("Media tools and converter", "Medya araçları ve dönüştürücü"), { menu = false; openTool("mediatools") }, iconKey = "tool_mediatools")
                        com.umutk.pulse.ThemedMenuItem(tr("Folder sizes", "Klasör boyutları"), { menu = false; onTab("tree") }, iconKey = "disk")
                        com.umutk.pulse.ThemedMenuItem(tr("File types", "Dosya türleri"), { menu = false; onTab("types") }, iconKey = "folder")
                        com.umutk.pulse.ThemedMenuItem(tr("Clean cache", "Önbelleği temizle"), { menu = false; onTab("cache") }, iconKey = "delete")
                        com.umutk.pulse.ThemedMenuItem(tr("Settings", "Ayarlar"), { menu = false; openTool("files_settings") }, iconKey = "settings")
                    }
                }
            }
        }
    }
}


/** "More" as a page of its own: every extra of Files as a row with its icon, a name and a line about it, in a fixed order. */
@Composable
fun FilesMorePage(onTab: (String) -> Unit) {
    val openTool = com.umutk.pulse.LocalOpenTool.current
    val rows = listOf(
        Triple("tool_mediatools", tr("Media tools and converter", "Medya araçları ve dönüştürücü"), tr("Compress pictures and videos, make GIFs, convert files", "Resim ve videoları sıkıştır, GIF yap, dosya dönüştür")) to { openTool("mediatools") },
        Triple("disk", tr("Folder sizes", "Klasör boyutları"), tr("Where the storage went, folder by folder", "Depolama nereye gitti, klasör klasör")) to { onTab("tree") },
        Triple("filetypes", tr("File types", "Dosya türleri"), tr("Pictures, videos, audio, documents, archives, apps", "Resimler, videolar, ses, belgeler, arşivler, uygulamalar")) to { onTab("types") },
        Triple("clean", tr("Clean cache", "Önbelleği temizle"), tr("Junk of apps that is safe to delete", "Uygulamaların silinmesi güvenli artıkları")) to { onTab("cache") },
        Triple("settings", tr("Settings", "Ayarlar"), tr("Look and behaviour of Files", "Dosyalar'ın görünümü ve davranışı")) to { openTool("files_settings") },
    )
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
        item { Text(tr("More", "Diğer"), color = T.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }
        items(rows.size) { i ->
            val (info, go) = rows[i]
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(T.panel, androidx.compose.foundation.shape.RoundedCornerShape(T.radius)).clickable { go() }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(info.first, T.accent, 28.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(info.second, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(info.third, color = T.sub, fontSize = 12.sp)
                }
                PIcon("chevron", T.sub, 20.dp)
            }
        }
    }
}
