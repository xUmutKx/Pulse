package com.umutk.pulse.extras.launcher

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PCheck
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private fun bytes(n: Long): String {
    val kb = n / 1024.0
    val t = when { kb < 1 -> "$n bytes"; kb < 1024 -> "%.1f KB".format(kb); kb < 1024 * 1024 -> "%.1f MB".format(kb / 1024); else -> "%.2f GB".format(kb / 1024 / 1024) }
    return if (kb < 1) t else "$t (%,d bytes)".format(n)
}

/** The space a size takes on the disk: whole 4 KB blocks, as Windows shows "Size on disk". */
private fun onDisk(n: Long): Long = if (n <= 0) 0 else ((n + 4095) / 4096) * 4096

private fun day(ms: Long): String = if (ms <= 0) "-" else DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM).format(Date(ms))

/** What a folder holds: files, folders and the bytes of all its files, counted on a worker thread (stops at 200 000 entries). */
private class Tally(val files: Int, val dirs: Int, val bytes: Long, val disk: Long, val done: Boolean)

private fun tally(d: File): Tally {
    var files = 0; var dirs = 0; var b = 0L; var disk = 0L; var seen = 0
    val stack = ArrayDeque<File>().apply { add(d) }
    while (stack.isNotEmpty()) {
        val cur = stack.removeLast()
        cur.listFiles()?.forEach { f ->
            seen++
            if (f.isDirectory) { dirs++; stack.add(f) } else { files++; val l = f.length(); b += l; disk += onDisk(l) }
        }
        if (seen > 200_000) return Tally(files, dirs, b, disk, false)
    }
    return Tally(files, dirs, b, disk, true)
}

/** What Android knows of the file itself: its owner and its rwx bits (there is no Windows ACL on a phone, these are the real rights). */
private class Stat(val mode: Int, val uid: Int, val gid: Int, val created: Long, val accessed: Long)

private fun stat(f: File): Stat? = runCatching {
    val s = android.system.Os.stat(f.path)
    val created = if (android.os.Build.VERSION.SDK_INT >= 26) runCatching {
        java.nio.file.Files.readAttributes(f.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).creationTime().toMillis()
    }.getOrDefault(0L) else 0L
    Stat(s.st_mode, s.st_uid, s.st_gid, created, s.st_atime * 1000)
}.getOrNull()

/** Facts of a picture, a song, a video or an app package, for the Details page. */
private fun details(ctx: android.content.Context, f: File, mime: String): List<Pair<String, String>> = runCatching {
    val out = ArrayList<Pair<String, String>>()
    when {
        mime.startsWith("image/") -> {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth > 0) {
                out += tr("Dimensions", "Boyutlar") to "${o.outWidth} x ${o.outHeight}"
                out += tr("Width", "Genişlik") to "${o.outWidth} " + tr("pixels", "piksel")
                out += tr("Height", "Yükseklik") to "${o.outHeight} " + tr("pixels", "piksel")
            }
            runCatching {
                val ex = android.media.ExifInterface(f.path)
                ex.getAttribute(android.media.ExifInterface.TAG_MAKE)?.let { out += tr("Camera maker", "Kamera üreticisi") to it }
                ex.getAttribute(android.media.ExifInterface.TAG_MODEL)?.let { out += tr("Camera model", "Kamera modeli") to it }
                ex.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)?.let { out += tr("Date taken", "Çekim tarihi") to it }
                ex.getAttribute(android.media.ExifInterface.TAG_F_NUMBER)?.let { out += tr("F-stop", "F-durağı") to "f/$it" }
                ex.getAttribute(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { out += "ISO" to it }
            }
        }
        mime.startsWith("audio/") || mime.startsWith("video/") -> {
            val r = android.media.MediaMetadataRetriever()
            try {
                r.setDataSource(f.path)
                fun put(k: Int, label: String) { r.extractMetadata(k)?.takeIf { it.isNotBlank() }?.let { out += label to it } }
                r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ms ->
                    val s = ms / 1000; out += tr("Length", "Uzunluk") to "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
                }
                put(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE, tr("Title", "Başlık"))
                put(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST, tr("Contributing artists", "Katkıda bulunan sanatçılar"))
                put(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM, tr("Album", "Albüm"))
                put(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR, tr("Year", "Yıl"))
                put(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE, tr("Genre", "Tür"))
                r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { out += tr("Bit rate", "Bit hızı") to "${it / 1000} kbps" }
                val vw = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val vh = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (vw != null && vh != null) out += tr("Frame size", "Çerçeve boyutu") to "$vw x $vh"
            } finally { runCatching { r.release() } }
        }
        f.name.endsWith(".apk", true) -> {
            ctx.packageManager.getPackageArchiveInfo(f.path, 0)?.let { pi ->
                out += tr("Package", "Paket") to pi.packageName
                out += tr("Version", "Sürüm") to pi.versionName.orEmpty()
            }
        }
    }
    out
}.getOrDefault(emptyList())

/**
 * The Properties window of a file or a folder, drawn in the look's own dialog: General (name, type, opens with, location, size, size on disk, what a folder holds,
 * dates, Read-only and Hidden), Security (the owner and the real read / write / run rights of the file) and Details (picture, media or package facts).
 */
@Composable
fun FilePropertiesDialog(f: File, onClose: () -> Unit, changed: (String?) -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    var tab by remember(f.path) { mutableIntStateOf(0) }
    var name by remember(f.path) { mutableStateOf(f.name) }
    val wasHidden = f.name.startsWith(".")
    var hidden by remember(f.path) { mutableStateOf(wasHidden) }
    val wasRo = f.exists() && !f.canWrite()
    var ro by remember(f.path) { mutableStateOf(wasRo) }
    val ext = f.extension.lowercase()
    val mime = remember(f.path) { if (f.isDirectory) "" else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream" }
    val opener = remember(f.path) {
        if (f.isDirectory) "" else runCatching {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(f), mime)
            ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.takeIf { it.packageName != "android" }?.loadLabel(ctx.packageManager)?.toString()
        }.getOrNull() ?: tr("Pick an app", "Uygulama seç")
    }
    val st = remember(f.path) { stat(f) }
    val tl by produceState<Tally?>(null, f.path) { if (f.isDirectory) value = withContext(Dispatchers.IO) { tally(f) } }
    val extra by produceState(emptyList<Pair<String, String>>(), f.path) { if (f.isFile) value = withContext(Dispatchers.IO) { details(ctx, f, mime) } }
    val type = if (f.isDirectory) tr("File folder", "Dosya klasörü") else if (ext.isEmpty()) tr("File", "Dosya") else "${ext.uppercase()} " + tr("File", "Dosyası") + " ($mime)"

    WinAlert(
        onDismissRequest = onClose,
        title = { Text(tr("${f.name} Properties", "${f.name} Özellikleri"), maxLines = 1) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    listOf(tr("General", "Genel"), tr("Security", "Güvenlik"), tr("Details", "Ayrıntılar")).forEachIndexed { i, t ->
                        val on = tab == i
                        Text(t, color = w.text, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.background(if (on) w.panel else w.bg).border(1.dp, w.line).clickable { tab = i }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                Column(Modifier.fillMaxWidth().border(1.dp, w.line).background(w.panel).padding(12.dp).heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    when (tab) {
                        0 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PIcon(if (f.isDirectory) "folder" else com.umutk.pulse.features.fileTypeKey(f), if (f.isDirectory) androidx.compose.ui.graphics.Color(0xFFF1C84B) else w.text, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                PField(name, { name = it }, tr("Name", "Ad"), Modifier.weight(1f))
                            }
                            PropRule()
                            PropFact(tr("Type", "Tür"), type)
                            if (!f.isDirectory) PropFact(tr("Opens with", "Şununla açılır"), opener)
                            PropRule()
                            PropFact(tr("Location", "Konum"), f.parent.orEmpty())
                            if (f.isDirectory) {
                                val t = tl
                                PropFact(tr("Size", "Boyut"), t?.let { bytes(it.bytes) + if (it.done) "" else "+" } ?: tr("Counting…", "Sayılıyor…"))
                                PropFact(tr("Size on disk", "Diskteki boyut"), t?.let { bytes(it.disk) } ?: "…")
                                PropFact(tr("Contains", "İçerik"), t?.let { tr("${it.files} Files, ${it.dirs} Folders", "${it.files} Dosya, ${it.dirs} Klasör") } ?: "…")
                            } else {
                                PropFact(tr("Size", "Boyut"), bytes(f.length()))
                                PropFact(tr("Size on disk", "Diskteki boyut"), bytes(onDisk(f.length())))
                            }
                            PropRule()
                            st?.created?.takeIf { it > 0 }?.let { PropFact(tr("Created", "Oluşturulma"), day(it)) }
                            PropFact(tr("Modified", "Değiştirilme"), day(f.lastModified()))
                            st?.accessed?.takeIf { it > 0 }?.let { PropFact(tr("Accessed", "Erişilme"), day(it)) }
                            PropRule()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Attributes:", "Öznitelikler:"), color = w.sub, fontSize = 12.sp, modifier = Modifier.width(86.dp))
                                PCheck(ro, { ro = it })
                                Text(tr("Read-only", "Salt okunur"), color = w.text, fontSize = 12.sp)
                                Spacer(Modifier.width(10.dp))
                                PCheck(hidden, { hidden = it })
                                Text(tr("Hidden", "Gizli"), color = w.text, fontSize = 12.sp)
                            }
                        }
                        1 -> {
                            PropFact(tr("Object name", "Nesne adı"), f.path)
                            PropRule()
                            Text(tr("Group or user names:", "Grup veya kullanıcı adları:"), color = w.sub, fontSize = 12.sp)
                            val mine = st?.uid == android.os.Process.myUid()
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, w.line).padding(8.dp)) {
                                listOf(
                                    "person" to (if (mine) "Pulse (" + tr("owner", "sahip") + ")" else tr("Owner", "Sahip") + " (uid ${st?.uid ?: "?"})"),
                                    "apps" to tr("Apps with storage access", "Depolama erişimi olan uygulamalar") + " (gid ${st?.gid ?: "?"})",
                                    "settings" to "SYSTEM",
                                ).forEach { (ic, n) ->
                                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        PIcon(ic, w.text, 16.dp); Spacer(Modifier.width(8.dp)); Text(n, color = w.text, fontSize = 12.sp)
                                    }
                                }
                            }
                            Text(tr("Permissions", "İzinler"), color = w.sub, fontSize = 12.sp)
                            val m = st?.mode ?: 0
                            fun bit(shift: Int, b: Int) = (m shr shift) and b != 0
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text("", modifier = Modifier.weight(1.4f))
                                listOf(tr("Owner", "Sahip"), tr("Group", "Grup"), tr("Others", "Diğer")).forEach { Text(it, color = w.sub, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
                            }
                            listOf(tr("Read", "Okuma") to 4, tr("Write", "Yazma") to 2, tr("Run / list", "Çalıştırma / listeleme") to 1).forEach { (label, b) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = w.text, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                                    listOf(6, 3, 0).forEach { sh -> Box(Modifier.weight(1f)) { if (st != null && bit(sh, b)) PIcon("check", w.text, 14.dp) } }
                                }
                            }
                            PropRule()
                            PropFact(tr("Pulse can read", "Pulse okuyabilir"), if (f.canRead()) tr("Yes", "Evet") else tr("No", "Hayır"))
                            PropFact(tr("Pulse can change", "Pulse değiştirebilir"), if (f.canWrite()) tr("Yes", "Evet") else tr("No", "Hayır"))
                            if (st != null) PropFact(tr("Mode", "Mod"), "%o".format(m and 0x1FF))
                        }
                        else -> {
                            PropFact(tr("Name", "Ad"), f.name)
                            PropFact(tr("Item type", "Öğe türü"), type)
                            PropFact(tr("Folder path", "Klasör yolu"), f.parent.orEmpty())
                            if (f.isFile) PropFact(tr("Size", "Boyut"), bytes(f.length()))
                            st?.created?.takeIf { it > 0 }?.let { PropFact(tr("Date created", "Oluşturma tarihi"), day(it)) }
                            PropFact(tr("Date modified", "Değiştirme tarihi"), day(f.lastModified()))
                            if (extra.isNotEmpty()) PropRule()
                            extra.forEach { (k, v) -> PropFact(k, v) }
                        }
                    }
                }
            }
        },
        confirmButton = { PButton("OK") {
            var cur = f
            // a new name, and Hidden as Android has it: a dot at the start of the name
            var n = name.trim().ifEmpty { f.name }
            if (hidden != wasHidden) n = if (hidden) "." + n.trimStart('.') else n.trimStart('.')
            if (n != f.name && !n.contains('/')) { val t = File(f.parentFile, n); if (!t.exists() && f.renameTo(t)) cur = t }
            if (ro != wasRo) runCatching { cur.setWritable(!ro) }
            onClose(); changed(cur.path)
        } },
        dismissButton = { PButton(tr("Cancel", "Vazgeç")) { onClose() } },
    )
}

@Composable
private fun PropRule() { Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(LocalWindowLook.current.line)) }

@Composable
private fun PropFact(label: String, value: String) {
    val w = LocalWindowLook.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label:", color = w.sub, fontSize = 12.sp, modifier = Modifier.width(104.dp))
        Text(value, color = w.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}
