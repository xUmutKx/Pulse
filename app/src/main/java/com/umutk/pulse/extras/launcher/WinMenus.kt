package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PCheck
import com.umutk.pulse.PField
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import java.io.File
import java.text.DateFormat
import java.util.Date

/** How one Windows look draws its right-click menu: the ground, the edge, the rows, the icon gutter. */
internal class MenuSkin(
    val bg: Brush, val border: Color, val borderW: Dp, val radius: Dp, val shadow: Dp,
    val padH: Dp, val padV: Dp, val rowH: Dp, val rowRadius: Dp, val rowPad: Dp, val gutter: Dp, val icon: Dp, val font: TextUnit, val text: Color,
)

internal val LocalMenuSkin = compositionLocalOf<MenuSkin?> { null }

/** The menu of each Windows look; null for the other looks (they keep the plain menu). */
internal fun menuSkin(id: String, dark: Boolean): MenuSkin? = when (id) {
    // Windows 11: soft rounded card, a thin light edge, roomy rows with a small rounded hover
    "win11" -> MenuSkin(Brush.verticalGradient(listOf(if (dark) Color(0xFF2C2C2C) else Color(0xFFF9F9F9), if (dark) Color(0xFF2C2C2C) else Color(0xFFF9F9F9))),
        if (dark) Color(0x33FFFFFF) else Color(0x1F000000), 1.dp, 8.dp, 10.dp, 4.dp, 4.dp, 36.dp, 4.dp, 10.dp, 32.dp, 16.dp, 13.sp, if (dark) Color.White else Color(0xFF1B1B1B))
    // Windows 10: flat, square, a grey edge, a narrow gutter
    "win10", "win10black" -> MenuSkin(Brush.verticalGradient(listOf(if (dark) Color(0xFF2B2B2B) else Color(0xFFF2F2F2), if (dark) Color(0xFF2B2B2B) else Color(0xFFF2F2F2))),
        if (dark) Color(0xFF626262) else Color(0xFFA0A0A0), 1.dp, 0.dp, 6.dp, 2.dp, 2.dp, 32.dp, 0.dp, 8.dp, 30.dp, 16.dp, 12.5.sp, if (dark) Color.White else Color.Black)
    // Windows 7: pale Aero card with a lighter inner edge, small rounded corners, rows of 24 dp
    "win7" -> MenuSkin(Brush.verticalGradient(listOf(Color(0xFFFAFBFD), Color(0xFFEEF1F6))),
        Color(0xFF979797), 1.dp, 3.dp, 6.dp, 2.dp, 2.dp, 26.dp, 2.dp, 8.dp, 28.dp, 16.dp, 12.sp, Color.Black)
    // Windows XP: white bar menu, a grey edge, tight rows
    "xp" -> MenuSkin(Brush.verticalGradient(listOf(Color.White, Color.White)),
        Color(0xFF7E7E7E), 1.dp, 0.dp, 3.dp, 2.dp, 2.dp, 23.dp, 0.dp, 6.dp, 26.dp, 16.dp, 11.5.sp, Color.Black)
    // Windows 95: the grey bevelled box
    "win95" -> MenuSkin(Brush.verticalGradient(listOf(Color(0xFFC0C0C0), Color(0xFFC0C0C0))),
        Color(0xFF404040), 2.dp, 0.dp, 0.dp, 2.dp, 2.dp, 22.dp, 0.dp, 6.dp, 24.dp, 14.dp, 11.sp, Color.Black)
    else -> null
}

/** The app whose Properties window is open (null: none) and the page of the window. The launcher's home composes [PropertiesHost] once. */
object PropState {
    var app by mutableStateOf<LaunchApp?>(null)
    var tab by mutableIntStateOf(0)
}

private fun size(n: Long): String {
    val kb = n / 1024.0
    val text = if (kb < 1024) "%.1f KB".format(kb) else "%.1f MB".format(kb / 1024.0)
    return "$text (%,d bytes)".format(n)
}

private fun date(ms: Long): String = if (ms <= 0) "-" else DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM).format(Date(ms))

/**
 * The Properties window of an app, drawn by the look's own dialog (Windows 7 Aero, 10 flat, XP blue, 95 grey): General (the name, which can be changed, the kind, the place, the size, the
 * dates, the Hidden mark), Details (the package, the versions, the paths) and Permissions (what the app asks for).
 */
@Composable
fun PropertiesHost() {
    val app = PropState.app ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val w = LocalWindowLook.current
    var name by remember(app.pkg) { mutableStateOf(app.label) }
    var hidden by remember(app.pkg) { mutableStateOf(app.pkg in LauncherPrefs.hidden) }
    val info = remember(app.pkg) { appFacts(ctx, app.pkg) }
    fun close() { PropState.app = null }
    WinAlert(
        onDismissRequest = { close() },
        title = { Text(tr("${app.label} Properties", "${app.label} Özellikleri")) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // the tabs of the window
                Row(Modifier.fillMaxWidth()) {
                    listOf(tr("General", "Genel"), tr("Details", "Ayrıntılar"), tr("Permissions", "İzinler")).forEachIndexed { i, t ->
                        val on = PropState.tab == i
                        Text(t, color = w.text, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.background(if (on) w.panel else w.bg).border(1.dp, w.line).clickable { PropState.tab = i }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                Column(Modifier.fillMaxWidth().border(1.dp, w.line).background(w.panel).padding(12.dp).heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    when (PropState.tab) {
                        0 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp)) { LaunchAppPicture(app, 40.dp, w.bg, w.text) }
                                Spacer(Modifier.width(12.dp))
                                PField(name, { name = it }, tr("Name", "Ad"), Modifier.weight(1f))
                            }
                            Rule()
                            Fact(tr("Type", "Tür"), if (info.installed) tr("Application", "Uygulama") else tr("Shortcut", "Kısayol"))
                            Fact(tr("Opens with", "Şununla açılır"), "Pulse")
                            // a file, a folder or a picture on the desktop: its real place, size and dates
                            if (app.pkg.startsWith("file:")) {
                                val f = java.io.File(app.pkg.removePrefix("file:"))
                                Fact(tr("Location", "Konum"), f.parent.orEmpty())
                                if (f.isFile) Fact(tr("Size", "Boyut"), size(f.length()))
                                Fact(tr("Modified", "Değiştirme"), date(f.lastModified()))
                            }
                            if (app.pkg.startsWith("url:")) Fact(tr("Address", "Adres"), app.pkg.removePrefix("url:"))
                            if (info.installed) {
                                Fact(tr("Location", "Konum"), info.dir)
                                Fact(tr("Size", "Boyut"), size(info.bytes))
                                Fact(tr("Size on disk", "Diskteki boyut"), size(info.disk))
                                Rule()
                                Fact(tr("Installed", "Kurulum"), date(info.installedAt))
                                Fact(tr("Modified", "Değiştirme"), date(info.updatedAt))
                            }
                            Rule()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Attributes:", "Öznitelikler:"), color = w.sub, fontSize = 12.sp, modifier = Modifier.width(86.dp))
                                PCheck(hidden, { hidden = it })
                                Text(tr("Hidden", "Gizli"), color = w.text, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                if (info.installed) PButton(tr("Advanced…", "Gelişmiş…")) { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                            }
                        }
                        1 -> {
                            Fact(tr("Package", "Paket"), app.pkg)
                            if (info.installed) {
                                Fact(tr("Version", "Sürüm"), info.version)
                                Fact(tr("Version code", "Sürüm kodu"), info.code.toString())
                                Fact(tr("Target SDK", "Hedef SDK"), info.target.toString())
                                Fact(tr("Minimum SDK", "En düşük SDK"), info.min.toString())
                                Fact(tr("User id", "Kullanıcı no"), info.uid.toString())
                                Fact(tr("Data folder", "Veri klasörü"), info.data)
                                Fact(tr("Installed from", "Kurulduğu yer"), info.source)
                                Fact(tr("System app", "Sistem uygulaması"), if (info.system) tr("Yes", "Evet") else tr("No", "Hayır"))
                            }
                        }
                        else -> {
                            if (info.perms.isEmpty()) Text(tr("No permissions asked", "İstenen izin yok"), color = w.sub, fontSize = 12.sp)
                            info.perms.forEach { Text(it, color = w.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = { PButton("OK") {
            // the name: an empty or the same name puts the real one back
            val n = name.trim()
            LauncherPrefs.names = if (n.isEmpty() || n == app.label && app.pkg !in LauncherPrefs.names) LauncherPrefs.names - app.pkg else LauncherPrefs.names + (app.pkg to n)
            LauncherPrefs.hidden = if (hidden) LauncherPrefs.hidden + app.pkg else LauncherPrefs.hidden - app.pkg
            LauncherPrefs.save(ctx); close()
        } },
        dismissButton = { PButton(tr("Cancel", "Vazgeç")) { close() } },
    )
}

@Composable
private fun Rule() { Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(LocalWindowLook.current.line)) }

@Composable
private fun Fact(label: String, value: String) {
    val w = LocalWindowLook.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label + ":", color = w.sub, fontSize = 12.sp, modifier = Modifier.width(96.dp))
        Text(value, color = w.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

private class AppFacts(
    val installed: Boolean = false, val dir: String = "", val bytes: Long = 0, val disk: Long = 0, val installedAt: Long = 0, val updatedAt: Long = 0,
    val version: String = "", val code: Long = 0, val target: Int = 0, val min: Int = 0, val uid: Int = 0, val data: String = "", val source: String = "", val system: Boolean = false,
    val perms: List<String> = emptyList(),
)

private fun appFacts(ctx: Context, pkg: String): AppFacts {
    val pm = ctx.packageManager
    return runCatching {
        val pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        val ai = pi.applicationInfo ?: return@runCatching AppFacts()
        val apk = File(ai.sourceDir)
        val splits = ai.splitSourceDirs.orEmpty().sumOf { File(it).length() }
        AppFacts(
            installed = true, dir = apk.parent.orEmpty(), bytes = apk.length() + splits, disk = apk.length() + splits,
            installedAt = pi.firstInstallTime, updatedAt = pi.lastUpdateTime, version = pi.versionName.orEmpty(),
            code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong(),
            target = ai.targetSdkVersion, min = if (android.os.Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 0, uid = ai.uid, data = ai.dataDir.orEmpty(),
            source = runCatching { if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg) }.getOrNull() ?: "-",
            system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
            perms = pi.requestedPermissions.orEmpty().map { it.substringAfterLast('.') }.sorted(),
        )
    }.getOrDefault(AppFacts())
}
