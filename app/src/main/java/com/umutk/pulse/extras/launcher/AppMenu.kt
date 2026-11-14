package com.umutk.pulse.extras.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PIcon
import com.umutk.pulse.extras.gamehub.FluentIcon
import com.umutk.pulse.tr

/** One thing the menu of an app can do. [quick] ones also go in the row of icons at the top of the Windows 11 menu. */
class MenuAct(val key: String, val en: String, val trName: String, val icon: String, val quick: Boolean = false, val danger: Boolean = false)

/** Every action of the menu, in the default order. The order and the switches are the user's (Settings > Context menu, and the menu's own "Customize"). */
val MENU_ACTS = listOf(
    MenuAct("open", "Open", "Aç", "open", quick = true),
    MenuAct("pin", "Pin to dock", "Dock'a sabitle", "pin", quick = true),
    MenuAct("rename", "Rename", "Yeniden adlandır", "rename", quick = true),
    MenuAct("share", "Share", "Paylaş", "share", quick = true),
    MenuAct("uninstall", "Uninstall", "Kaldır", "trash", quick = true, danger = true),
    MenuAct("info", "App info", "Uygulama bilgisi", "info"),
    MenuAct("hide", "Hide from launcher", "Ana ekranda gizle", "eyeOff"),
    MenuAct("folder", "Add to folder", "Klasöre ekle", "folder"),
    MenuAct("copypkg", "Copy package name", "Paket adını kopyala", "copy"),
    MenuAct("store", "Open in Play Store", "Play Store'da aç", "globe"),
    MenuAct("stop", "Force stop", "Durmaya zorla", "forceStop"),
    MenuAct("extract", "Extract APK", "APK çıkar", "apkPackage"),
    MenuAct("props", "Properties", "Özellikler", "properties"),
)

/** The actions in the user's order, with the switched-off ones left out; an action added by a later version joins at its default place. */
fun orderedMenuActs(includeOff: Boolean = false): List<MenuAct> {
    val order = LauncherPrefs.menuOrder
    val sorted = MENU_ACTS.sortedBy { a -> order.indexOf(a.key).let { if (it < 0) 1000 + MENU_ACTS.indexOf(a) else it } }
    return if (includeOff) sorted else sorted.filter { it.key !in LauncherPrefs.menuOff }
}

/** True for the icon sets that are Fluent (the Windows 11 and 10 menus); the other looks draw the look's own icons. */
@Composable
private fun MenuGlyph(key: String, tint: Color, size: androidx.compose.ui.unit.Dp, fluent: Boolean) {
    if (fluent) FluentIcon(key, tint, size) else PIcon(key, tint, size)
}

private fun runAct(ctx: Context, a: MenuAct, app: LaunchApp, auto: List<String>, dismiss: () -> Unit) {
    val pkg = app.pkg
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    when (a.key) {
        "open" -> ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { open(it) }
        "pin" -> togglePin(ctx, pkg, auto)
        "rename", "props" -> { PropState.app = app; PropState.tab = 0 }
        "share" -> open(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${app.label}: https://play.google.com/store/apps/details?id=$pkg"), null))
        "uninstall" -> com.umutk.pulse.features.AppUninstall.run(ctx, pkg) { ok -> if (ok) { LauncherPrefs.names = LauncherPrefs.names - pkg; LauncherPrefs.save(ctx) } }
        "info" -> open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
        "hide" -> { LauncherPrefs.hidden = LauncherPrefs.hidden + pkg; LauncherPrefs.save(ctx) }
        "folder" -> FolderUi.pick = app
        "copypkg" -> { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("package", pkg)); android.widget.Toast.makeText(ctx, pkg, android.widget.Toast.LENGTH_SHORT).show() }
        "store" -> open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        "stop" -> Thread { com.umutk.pulse.extras.blackout.Privilege.run("am force-stop $pkg") }.start()
        "extract" -> Thread { val r = com.umutk.pulse.features.extractApp(ctx, pkg); android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(ctx, r, android.widget.Toast.LENGTH_LONG).show() } }.start()
    }
    dismiss()
}

/**
 * The menu of one app (a long press, or a right click): in Windows 11 it is the new compact menu, a row of icons on top (open, pin, rename, share, uninstall) and the rest as rows with
 * Fluent icons, the less common ones behind "Show more options"; the other looks list every action as rows. Which actions there are, and in what order, is the user's to set.
 */
@Composable
fun AppActionDialog(app: LaunchApp, auto: List<String>, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val w = LocalWindowLook.current
    val pinned = app.pkg in LauncherPrefs.dock
    val tiles = LauncherPrefs.look == "winphone" && LauncherPrefs.phoneTiles
    if (isFolder(app)) { ContextPopup(230.dp, 250.dp, onDismiss) { FolderActionRows(app, onDismiss) }; return }
    val inFolder = LauncherFolders.of(app.pkg) != null
    val real = !app.pkg.startsWith("pulse:")
    val acts = orderedMenuActs().filter { a ->
        when (a.key) {
            "uninstall" -> real && !app.system
            "info", "store", "stop", "extract", "copypkg", "share" -> real
            "open" -> real
            else -> true
        }
    }
    val win11 = w.id == "win11"
    var more by remember { mutableStateOf(false) }
    ContextPopup(if (win11) 260.dp else 236.dp, if (tiles) 460.dp else if (win11) 420.dp else 400.dp, onDismiss) {
        val sk = LocalMenuSkin.current
        val fluent = w.id == "win11" || w.id == "win10" || w.id == "win10black"
        fun label(a: MenuAct) = when (a.key) { "pin" -> if (pinned) tr("Unpin from dock", "Dock'tan çıkar") else tr(a.en, a.trName); else -> tr(a.en, a.trName) }
        fun go(a: MenuAct) = runAct(ctx, a, app, auto, onDismiss)
        if (sk == null) Text(app.label, color = w.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp))
        // Windows Phone: the size of this app's tile
        if (tiles) listOf("s" to tr("Small tile", "Küçük kutucuk"), "m" to tr("Medium tile", "Orta kutucuk"), "w" to tr("Wide tile", "Geniş kutucuk")).forEach { (k, n) ->
            ActionRow("gridView", n, w.text) { LauncherPrefs.tileSizes = LauncherPrefs.tileSizes + (app.pkg to k); LauncherPrefs.save(ctx); onDismiss() }
        }
        if (win11 && sk != null) {
            // the row of icons at the top
            val quick = acts.filter { it.quick }
            if (quick.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    quick.forEach { a ->
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).clickable { go(a) }, contentAlignment = Alignment.Center) {
                            FluentIcon(if (a.key == "pin" && pinned) "pin" else a.icon, if (a.danger) Color(0xFFD13438) else sk.text, 18.dp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(sk.text.copy(alpha = .12f)))
            }
            val rest = acts.filter { !it.quick }
            val firstSet = rest.take(4); val moreSet = rest.drop(4)
            firstSet.forEach { a -> ActionRow(a.icon, label(a), sk.text) { go(a) } }
            if (moreSet.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(sk.text.copy(alpha = .12f)))
                if (!more) Row(Modifier.fillMaxWidth().height(sk.rowH).clip(RoundedCornerShape(sk.rowRadius)).clickable { more = true }.padding(horizontal = sk.rowPad), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(sk.gutter), contentAlignment = Alignment.CenterStart) { FluentIcon("more", sk.text, sk.icon) }
                    Text(tr("Show more options", "Daha fazla seçenek göster"), color = sk.text, fontSize = sk.font, modifier = Modifier.weight(1f))
                    Text("Shift+F10", color = sk.text.copy(alpha = .6f), fontSize = 11.sp)
                } else moreSet.forEach { a -> ActionRow(a.icon, label(a), sk.text) { go(a) } }
            }
        } else {
            acts.forEach { a ->
                if (fluent) FluentRow(a, label(a), sk?.text ?: w.text) { go(a) } else ActionRow(a.icon, label(a), w.text) { go(a) }
            }
            if (inFolder) ActionRow("close", tr("Remove from folder", "Klasörden çıkar"), w.text) { LauncherFolders.remove(ctx, app.pkg); onDismiss() }
        }
        // the menu is the user's to arrange: the way into its settings
        Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background((sk?.text ?: w.text).copy(alpha = .12f)))
        ActionRow("settings", tr("Customize this menu", "Bu menüyü düzenle"), sk?.text ?: w.text) {
            onDismiss(); runCatching { ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java).putExtra("cat", "menu")) }
        }
    }
}

/** A row of the Windows 10 menu with a Fluent icon (the look's own pictures are not used there). */
@Composable
private fun FluentRow(a: MenuAct, text: String, ink: Color, onClick: () -> Unit) {
    val sk = LocalMenuSkin.current
    Row(Modifier.fillMaxWidth().height(sk?.rowH ?: 36.dp).clickable(onClick = onClick).padding(horizontal = sk?.rowPad ?: 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(sk?.gutter ?: 30.dp), contentAlignment = Alignment.CenterStart) { FluentIcon(a.icon, if (a.danger) Color(0xFFD13438) else ink, sk?.icon ?: 16.dp) }
        Text(text, color = ink, fontSize = sk?.font ?: 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The page that arranges the menu of an app: each action has a switch and arrows to move it up and down; the first five that are on and marked as quick make the icon row of the
 * Windows 11 menu. Shown in the launcher settings (Context menu) and so in the Control Panel and the right-click menu.
 */
@Composable
fun MenuEditor() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val w = LocalWindowLook.current
    val all = orderedMenuActs(includeOff = true)
    fun move(key: String, by: Int) {
        val keys = all.map { it.key }.toMutableList()
        val i = keys.indexOf(key); val j = i + by
        if (i < 0 || j !in keys.indices) return
        keys.removeAt(i); keys.add(j, key)
        LauncherPrefs.menuOrder = keys; LauncherPrefs.save(ctx)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tr("Actions of the menu of an app, in their order", "Uygulama menüsünün eylemleri, sırasıyla"), color = w.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        all.forEachIndexed { i, a ->
            val on = a.key !in LauncherPrefs.menuOff
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                FluentIcon(a.icon, if (on) w.text else w.sub, 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr(a.en, a.trName) + if (a.quick) "  •" else "", color = if (on) w.text else w.sub, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clickable(enabled = i > 0) { move(a.key, -1) }, contentAlignment = Alignment.Center) { PIcon("up", if (i > 0) w.text else w.sub.copy(alpha = .3f), 18.dp) }
                Box(Modifier.size(36.dp).clickable(enabled = i < all.lastIndex) { move(a.key, 1) }, contentAlignment = Alignment.Center) { PIcon("down", if (i < all.lastIndex) w.text else w.sub.copy(alpha = .3f), 18.dp) }
                com.umutk.pulse.PSwitch(on, { LauncherPrefs.menuOff = if (it) LauncherPrefs.menuOff - a.key else LauncherPrefs.menuOff + a.key; LauncherPrefs.save(ctx) })
            }
        }
        Text("• " + tr("also in the icon row at the top of the Windows 11 menu", "Windows 11 menüsünün üstündeki simge satırında da yer alır"), color = w.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        com.umutk.pulse.PButton(tr("Reset the menu", "Menüyü sıfırla"), Modifier.padding(top = 8.dp)) { LauncherPrefs.menuOrder = emptyList(); LauncherPrefs.menuOff = emptySet(); LauncherPrefs.save(ctx) }
    }
}
