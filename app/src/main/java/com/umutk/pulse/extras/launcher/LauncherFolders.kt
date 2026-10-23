package com.umutk.pulse.extras.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr

const val FOLDER_PREFIX = "folder:"
fun isFolder(app: LaunchApp) = app.pkg.startsWith(FOLDER_PREFIX)

/**
 * Folders made in the apps list. They share LauncherPrefs.folders ("name|pkg,pkg") with the home grid's folders;
 * [LauncherPrefs.folderOff] lists the ones that are not shown on the home screen (a new folder starts there).
 */
object LauncherFolders {
    private fun clean(n: String) = n.filter { it != '|' && it != ',' && it != '\n' }.trim().take(24)
    private fun members(v: String) = v.substringAfter('|', "").split(',').filter { it.isNotBlank() }
    private fun name(v: String) = v.substringBefore('|')
    private fun put(c: Context, m: Map<String, String>, off: Set<String> = LauncherPrefs.folderOff) {
        LauncherPrefs.folders = m; LauncherPrefs.folderOff = off.filter { it in m }.toSet(); LauncherPrefs.save(c)
    }
    private fun without(m: Map<String, String>, pkg: String) = m.mapValues { (_, v) -> name(v) + "|" + (members(v) - pkg).joinToString(",") }

    fun of(pkg: String) = LauncherPrefs.folders.entries.firstOrNull { pkg in members(it.value) }?.key

    fun create(c: Context, nm: String, first: String) {
        val id = System.currentTimeMillis().toString(36)
        put(c, without(LauncherPrefs.folders, first) + (id to (clean(nm).ifEmpty { tr("Folder", "Klasör") } + "|" + first)), LauncherPrefs.folderOff + id)
    }
    fun add(c: Context, id: String, pkg: String) {
        val m = without(LauncherPrefs.folders, pkg)
        put(c, m + (id to (name(m[id].orEmpty()) + "|" + (members(m[id].orEmpty()) + pkg).joinToString(","))))
    }
    fun remove(c: Context, pkg: String) = put(c, without(LauncherPrefs.folders, pkg))
    fun rename(c: Context, id: String, nm: String) {
        val v = LauncherPrefs.folders[id] ?: return
        put(c, LauncherPrefs.folders + (id to (clean(nm).ifEmpty { name(v) } + "|" + members(v).joinToString(","))))
    }
    fun setHome(c: Context, id: String, on: Boolean) = put(c, LauncherPrefs.folders, if (on) LauncherPrefs.folderOff - id else LauncherPrefs.folderOff + id)
    fun delete(c: Context, id: String) = put(c, LauncherPrefs.folders - id, LauncherPrefs.folderOff - id)

    /** The apps list with the folders' members folded into folder icons, folders first. */
    fun fold(apps: List<LaunchApp>): List<LaunchApp> {
        val fs = LauncherPrefs.folders
        if (fs.isEmpty()) return apps
        val inside = fs.values.flatMap { members(it) }.toSet()
        val made = fs.entries.map { (id, v) -> LaunchApp(name(v), FOLDER_PREFIX + id, icon(members(v).mapNotNull { p -> apps.firstOrNull { it.pkg == p } }), system = true) }
        return made + apps.filter { it.pkg !in inside }
    }

    fun members(id: String, apps: List<LaunchApp>) = members(LauncherPrefs.folders[id].orEmpty()).mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

    /** Folder picture: a soft tile with up to four of the apps' icons in a 2 x 2 grid. */
    private fun icon(inside: List<LaunchApp>): ImageBitmap {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.color = 0x66A0A0A0
        cv.drawRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), s * .28f, s * .28f, p)
        val cell = s / 2f; val pad = s * .09f
        inside.take(4).forEachIndexed { i, a ->
            val b = a.icon?.asAndroidBitmap() ?: return@forEachIndexed
            val x = (i % 2) * cell; val y = (i / 2) * cell
            cv.drawBitmap(b, null, RectF(x + pad, y + pad, x + cell - pad, y + cell - pad), p)
        }
        return bmp.asImageBitmap()
    }
}

/** Which folder dialog is open: [pick] = choose a folder for this app, [open] = the folder's contents. */
object FolderUi {
    var pick by mutableStateOf<LaunchApp?>(null)
    var open by mutableStateOf<String?>(null)
}

@Composable
private fun FRow(icon: String, label: String, onClick: () -> Unit) {
    val w = LocalWindowLook.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, w.text, 22.dp); Spacer(Modifier.width(12.dp))
        Text(label, color = w.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Folder dialogs of the apps list; [apps] = every visible app. */
@Composable
fun FolderDialogs(apps: List<LaunchApp>, onOpenApp: (LaunchApp) -> Unit, onLong: (LaunchApp) -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    FolderUi.pick?.let { app ->
        var nm by remember { mutableStateOf("") }
        val close = { FolderUi.pick = null }
        WinAlert(onDismissRequest = close,
            title = { Text(tr("Add to folder", "Klasöre ekle"), color = w.text) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    LauncherPrefs.folders.forEach { (id, v) -> FRow("folder", v.substringBefore('|')) { LauncherFolders.add(ctx, id, app.pkg); close() } }
                    Spacer(Modifier.height(8.dp))
                    PField(nm, { nm = it }, tr("New folder", "Yeni klasör"), Modifier.fillMaxWidth())
                    FRow("add", nm.ifBlank { tr("New folder", "Yeni klasör") }) { LauncherFolders.create(ctx, nm, app.pkg); close() }
                }
            },
            confirmButton = { PButton(tr("Close", "Kapat"), onClick = close) })
    }
    FolderUi.open?.let { id ->
        if (id !in LauncherPrefs.folders) { FolderUi.open = null; return@let }
        val inside = LauncherFolders.members(id, apps)
        val close = { FolderUi.open = null }
        WinAlert(onDismissRequest = close,
            title = { Text(LauncherPrefs.folders[id].orEmpty().substringBefore('|'), color = w.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inside.isEmpty()) Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { PIcon("folder", T.sub, 32.dp) }
                    inside.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { a ->
                                Column(Modifier.weight(1f).pointerInput(a.pkg) { detectTapGestures(onTap = { close(); onOpenApp(a) }, onLongPress = { onLong(a) }) },
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    LaunchAppPicture(a, 44.dp, T.panel, T.text)
                                    Text(a.label, color = w.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = { PButton(tr("Close", "Kapat"), onClick = close) })
    }
}

/** Rows of the action popup for a folder icon: rename, show on home, delete. */
@Composable
fun FolderActionRows(app: LaunchApp, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val id = app.pkg.removePrefix(FOLDER_PREFIX)
    var nm by remember { mutableStateOf(app.label) }
    val onHome = id !in LauncherPrefs.folderOff
    PField(nm, { nm = it }, tr("Folder name", "Klasör adı"), Modifier.fillMaxWidth())
    FRow("check", tr("Save name", "Adı kaydet")) { LauncherFolders.rename(ctx, id, nm); onDismiss() }
    FRow(if (onHome) "eyeOff" else "pin", if (onHome) tr("Remove from home", "Ana ekrandan kaldır") else tr("Add to home", "Ana ekrana ekle")) { LauncherFolders.setHome(ctx, id, !onHome); onDismiss() }
    FRow("trash", tr("Delete folder", "Klasörü sil")) { LauncherFolders.delete(ctx, id); onDismiss() }
}
