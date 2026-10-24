package com.umutk.pulse.features

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Cfg
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import java.io.File

/**
 * "Pulse · Save to…" in the system's "Open with" and "Share" lists: whatever file another app hands over (any type) is saved as a copy under a name and
 * in a folder picked here. Nothing is opened; the file stays where it was.
 */
class SaveToActivity : ComponentActivity() {
    private fun uris(): List<Uri> {
        val i = intent
        return when (i.action) {
            Intent.ACTION_VIEW -> listOfNotNull(i.data)
            Intent.ACTION_SEND_MULTIPLE -> androidx.core.content.IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
        }
    }

    private fun nameOf(u: Uri): String {
        if (u.scheme == "file") return File(u.path.orEmpty()).name
        return runCatching { contentResolver.query(u, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null } }.getOrNull() ?: "file"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        val list = uris()
        if (list.isEmpty()) { finish(); return }
        // its own look, Material: it is a small panel over another app, and it must not wear whatever look Pulse has (Windows 11 mixed in before)
        setContent { LookHost(transparent = true, lookId = "material") { Panel(list) } }
    }

    @Composable
    private fun Panel(list: List<Uri>) {
        val top = Environment.getExternalStorageDirectory()
        val dirs = listOf("Download", "Documents", "DCIM", "Pictures", "Music", "Movies").map { File(top, it) }.filter { it.isDirectory }
        var target by remember { mutableStateOf(dirs.firstOrNull() ?: top) }
        var name by remember { mutableStateOf(nameOf(list.first())) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(horizontal = pageGutter).widthIn(max = 380.dp).fillMaxWidth().background(T.bg).border(1.dp, T.line).padding(20.dp)) {
                Text(tr("Save to…", "Şuraya kaydet…"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                if (list.size == 1) PField(name, { name = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
                else Text(tr("${list.size} files", "${list.size} dosya"), color = T.sub, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dirs.forEach { d -> PChip(target.path == d.path, d.name) { target = d } }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton(text = tr("Cancel", "İptal"), modifier = Modifier.weight(1f)) { finish() }
                    PButton(text = tr("Save", "Kaydet"), modifier = Modifier.weight(1f)) { save(list, target, name) }
                }
            }
        }
    }

    /** Android 10+: the copy goes in through the media store under [folder] (Download, Documents, DCIM, Pictures, Music, Movies), in the collection that fits its type. */
    private fun saveViaMediaStore(u: Uri, name: String, folder: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: contentResolver.getType(u) ?: "application/octet-stream"
        val (coll, rel) = when {
            mime.startsWith("image/") && folder in setOf("Pictures", "DCIM") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI to folder
            mime.startsWith("video/") && folder in setOf("Movies", "DCIM") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI to folder
            mime.startsWith("audio/") && folder == "Music" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to folder
            folder == "Documents" -> android.provider.MediaStore.Files.getContentUri("external") to "Documents"
            else -> android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI to "Download"
        }
        return runCatching {
            val v = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            val dst = contentResolver.insert(coll, v) ?: return@runCatching false
            contentResolver.openInputStream(u)?.use { ins -> contentResolver.openOutputStream(dst)?.use { ins.copyTo(it) } }
            true
        }.getOrDefault(false)
    }

    private fun save(list: List<Uri>, dir: File, single: String) {
        Thread {
            var ok = 0
            list.forEachIndexed { i, u ->
                runCatching {
                    val raw = (if (list.size == 1) single else nameOf(u)).trim().ifEmpty { "file" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    var out = File(dir, raw); var n = 2
                    while (out.exists()) { out = File(dir, raw.substringBeforeLast('.', raw) + " ($n)" + raw.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }); n++ }
                    val direct = runCatching {
                        dir.mkdirs()
                        contentResolver.openInputStream(u)?.use { ins -> out.outputStream().use { ins.copyTo(it) } } ?: error("no input")
                        android.media.MediaScannerConnection.scanFile(this, arrayOf(out.path), null, null)
                    }.isSuccess
                    // without the all-files permission the folder cannot be written directly: the media store takes the copy instead
                    if (direct || saveViaMediaStore(u, raw, dir.name)) ok++
                }
            }
            runOnUiThread { android.widget.Toast.makeText(this, if (ok > 0) tr("Saved $ok to ${dir.name}", "$ok dosya ${dir.name} içine kaydedildi") else tr("Could not save", "Kaydedilemedi"), android.widget.Toast.LENGTH_SHORT).show(); finish() }
        }.start()
    }
}
