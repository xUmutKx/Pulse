package com.umutk.pulse.extras.code

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr
import java.io.File

/** The file another app handed to Code ("Open with"): read once by the home screen of Code. */
object CodeIntake { var uri by androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }

/**
 * Code, the code editor as an app of its own (its launcher entry and the Extra apps menu): an AMOLED black page with the files opened lately, Open (any file the
 * phone's picker gives) and New (a file in Documents/Pulse/Code). A file opens in the same editor as in Files: colours by language, folds, search, Run for a page.
 */
@Composable
fun CodeHome(back: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pulse_code", 0) }
    var recent by remember { mutableStateOf(prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() && File(it).isFile }) }
    var file by remember { mutableStateOf<File?>(null) }
    var doc by remember { mutableStateOf<CodeSource?>(null) }
    fun keep(f: File) {
        recent = (listOf(f.path) + recent.filter { it != f.path }).take(30)
        prefs.edit().putString("recent", recent.joinToString("\n")).apply()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            val name = runCatching { ctx.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "file"
            doc = CodeSource(name,
                { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() },
                { t -> runCatching { ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(t.toByteArray()) } != null }.getOrDefault(false) })
        }
    }
    // a file handed over by "Open with": a real path opens in the editor of Files, any other address is read as text through the system
    val handed = CodeIntake.uri
    androidx.compose.runtime.LaunchedEffect(handed) {
        val u = handed ?: return@LaunchedEffect
        CodeIntake.uri = null
        val path = if (u.scheme == "file") u.path else null
        if (path != null && File(path).isFile) { keep(File(path)); file = File(path); return@LaunchedEffect }
        val name = runCatching { ctx.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: u.lastPathSegment ?: "file"
        doc = CodeSource(name,
            { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() },
            { t -> runCatching { ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(t.toByteArray()) } != null }.getOrDefault(false) })
    }
    var menu by remember { mutableStateOf(false) }
    fun newFile() {
        val dir = File(android.os.Environment.getExternalStorageDirectory(), "Documents/Pulse/Code").also { it.mkdirs() }
        var n = 1
        var nf = File(dir, "untitled.txt")
        while (nf.exists()) { n++; nf = File(dir, "untitled $n.txt") }
        if (runCatching { nf.writeText(""); true }.getOrDefault(false)) { keep(nf); file = nf }
    }
    val f = file
    val d = doc
    if (f != null) { CodeEditorDialog(f) { file = null }; return }
    if (d != null) { CodeEditorHost(d, false, null, null) { doc = null }; return }
    BackHandler { back() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { menu = !menu }, contentAlignment = Alignment.Center) { PIcon("menu", Color.White, 22.dp) }
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", Color.White, 22.dp) }
            Text(tr("Code", "Kod"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple<String, String, () -> Unit>("open", tr("Open", "Aç"), { pick.launch(arrayOf("*/*")) }), Triple<String, String, () -> Unit>("add", tr("New", "Yeni"), { newFile() })).forEach { (ic, name, act) ->
                Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF1A1A1A)).clickable { act() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(ic, Color.White, 18.dp)
                    Text(name, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(tr("Recent", "Son açılanlar"), color = Color(0xFF8AB4F8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(recent) { p ->
                val rf = File(p)
                val lang = PulseCode.detect(rf.name)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { keep(rf); file = rf }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val badge = com.umutk.pulse.features.exBadgeOf(rf.extension)
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                        if (badge != null) androidx.compose.material3.Icon(badge.second, null, tint = badge.first, modifier = Modifier.size(22.dp))
                        else Text(rf.extension.uppercase().take(3), color = Color(0xFF8AB4F8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(rf.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(lang?.label, rf.parent).joinToString("  ·  "), color = Color(0xFF9AA0A6), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    // the menu of the three lines: open, new, and the projects (the files of Documents/Pulse/Code)
    if (menu) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { menu = false })
        Column(Modifier.statusBarsPadding().padding(top = 56.dp, start = 12.dp).width(300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1A1A)).padding(vertical = 8.dp)) {
            listOf(Triple<String, String, () -> Unit>("open", tr("Open a file", "Dosya aç"), { menu = false; pick.launch(arrayOf("*/*")) }), Triple<String, String, () -> Unit>("add", tr("New file", "Yeni dosya"), { menu = false; newFile() })).forEach { (ic, name, act) ->
                Row(Modifier.fillMaxWidth().clickable { act() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(ic, Color.White, 20.dp); Text(name, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
                }
            }
            // the folder tree: the folder you picked (Documents/Pulse/Code at first), folders open and close, a file opens in the editor
            var root by remember { mutableStateOf(prefs.getString("root", null)?.let { File(it) }?.takeIf { it.isDirectory } ?: File(android.os.Environment.getExternalStorageDirectory(), "Documents/Pulse/Code").also { it.mkdirs() }) }
            val pickRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
                val id = u?.let { runCatching { android.provider.DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                val path = id?.let { if (it.startsWith("primary:")) android.os.Environment.getExternalStorageDirectory().path + "/" + it.removePrefix("primary:") else "/storage/" + it.replace(":", "/") }
                if (path != null && File(path).isDirectory) { root = File(path); prefs.edit().putString("root", path).apply() }
            }
            Row(Modifier.fillMaxWidth().clickable { pickRoot.launch(null) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("folder", Color(0xFF8AB4F8), 18.dp); Text(root.name.ifBlank { root.path }, color = Color(0xFF8AB4F8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                PIcon("chevron", Color(0xFF9AA0A6), 16.dp)
            }
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                CodeTree(root, 0) { f -> menu = false; keep(f); file = f }
            }
        }
    }
    }
}


/** The files and folders under [dir]: a folder opens and closes where it stands, a file is handed to [open]. */
@Composable
private fun CodeTree(dir: File, depth: Int, open: (File) -> Unit) {
    val items = remember(dir.path) { dir.listFiles()?.filter { !it.name.startsWith(".") }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.take(300).orEmpty() }
    items.forEach { f ->
        if (f.isDirectory) {
            var opened by remember(f.path) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { opened = !opened }.padding(start = (16 + depth * 14).dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(if (opened) "chevron" else "chevron", Color(0xFF9AA0A6), 14.dp, Modifier.graphicsLayer { rotationZ = if (opened) 90f else 0f })
                PIcon("folder", Color(0xFF8AB4F8), 18.dp, Modifier.padding(start = 6.dp))
                Text(f.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            if (opened) CodeTree(f, depth + 1, open)
        } else {
            val badge = com.umutk.pulse.features.exBadgeOf(f.extension)
            Row(Modifier.fillMaxWidth().clickable { open(f) }.padding(start = (36 + depth * 14).dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) androidx.compose.material3.Icon(badge.second, null, tint = badge.first, modifier = Modifier.size(18.dp)) else PIcon("details", Color(0xFF9AA0A6), 18.dp)
                Text(f.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
