package com.umutk.pulse.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.CancellationSignal
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PTheme
import com.umutk.pulse.R
import com.umutk.pulse.Shell
import com.umutk.pulse.Themes
import com.umutk.pulse.WinAlert
import com.umutk.pulse.amoledWindow
import com.umutk.pulse.monoWindow
import com.umutk.pulse.effective
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The APK the install window shows. MainActivity sets it when an APK is opened from outside, Downloads.openFile when one is tapped. */
object InstallState { var file by mutableStateOf<File?>(null) }

private val Warn = Color(0xFFB00020)
private const val BACKUP_DIR = "/data/local/tmp/pulse_restore"

private enum class Status(val label: String) {
    NEW("New"), IDENTICAL("Identical"), UPGRADE("Upgrade"), DOWNGRADE("Downgrade"), SIG("Signature differs"),
}

private fun statusOf(p: Picked): Status {
    val old = p.installedCode ?: return Status.NEW
    return when {
        p.mismatch -> Status.SIG
        p.code == old -> Status.IDENTICAL
        p.code > old -> Status.UPGRADE
        else -> Status.DOWNGRADE
    }
}

/** The app icon from the APK itself, or null when it cannot be read. */
private fun apkIcon(ctx: Context, path: String): ImageBitmap? = runCatching {
    val pm = ctx.packageManager
    val ai = pm.getPackageArchiveInfo(path, 0)?.applicationInfo ?: return null
    ai.sourceDir = path; ai.publicSourceDir = path
    ai.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
}.getOrNull()

/**
 * Root only. Keeps the app's data folder in [BACKUP_DIR], removes the app, installs [pk] as given,
 * then puts the data back under the new owner. The backup stays until the restore is done.
 */
private fun removeAndRestore(pk: Picked): String {
    val data = "/data/data/${pk.pkg}"
    val bak = "$BACKUP_DIR/${pk.pkg}"
    val saved = Shell.run("rm -rf '$bak' && mkdir -p '$bak' && cp -a '$data/.' '$bak/' && echo BACKUP_OK")
    if (!saved.contains("BACKUP_OK")) return "Backup failed, nothing was removed: ${saved.trim()}"
    Shell.run("pm uninstall ${pk.pkg}")
    val out = Shell.run(Installer.silentCmd(pk, down = true, user = 0))
    if (!out.contains("Success")) return "Install failed, data kept in $bak: ${out.trim()}"
    val uid = Shell.run("stat -c %u '$data'").trim()
    Shell.run("cp -a '$bak/.' '$data/' && chown -R $uid:$uid '$data' && rm -rf '$bak'")
    return "Restored data for ${pk.label}"
}

/** The installed app as it is now, shown beside the new APK. */
private data class OldApp(val version: String, val code: Long, val minSdk: Int, val target: Int)

/** Xposed fields from the APK's meta-data: the description and the minimum API version. */
private data class XposedInfo(val description: String, val minVersion: String)

private fun oldApp(ctx: Context, pkg: String): OldApp? = try {
    val i = ctx.packageManager.getPackageInfo(pkg, 0)
    val ai = i.applicationInfo
    OldApp(i.versionName.orEmpty(), i.longVersionCode, ai?.minSdkVersion ?: 0, ai?.targetSdkVersion ?: 0)
} catch (e: PackageManager.NameNotFoundException) { null }

private fun xposedInfo(ctx: Context, path: String): XposedInfo? {
    val ai = ctx.packageManager.getPackageArchiveInfo(path, PackageManager.GET_META_DATA)?.applicationInfo ?: return null
    ai.sourceDir = path; ai.publicSourceDir = path
    if (!isXposedModule(ai)) return null
    val md = ai.metaData
    return XposedInfo(md?.getString("xposeddescription").orEmpty(), md?.get("xposedminversion")?.toString().orEmpty())
}

/** LSPosed Manager, or the older EdXposed manager, whichever is installed. */
fun lsposedIntent(c: Context): Intent? {
    listOf("org.lsposed.manager", "io.github.lsposed.manager", "com.solohsu.android.edxp.manager")
        .firstNotNullOfOrNull { c.packageManager.getLaunchIntentForPackage(it) }?.let { return it }
    // Vector and other forks have their own package names: the first installed app with "vector" or "lsposed" in it that can be opened
    return c.packageManager.getInstalledPackages(0).map { it.packageName }
        .firstOrNull { (it.contains("vector", true) || it.contains("lsposed", true)) && c.packageManager.getLaunchIntentForPackage(it) != null }
        ?.let { c.packageManager.getLaunchIntentForPackage(it) }
}

private fun openLSPosed(c: Context) {
    val i = lsposedIntent(c)
    if (i == null) Toast.makeText(c, "LSPosed Manager is not installed", Toast.LENGTH_LONG).show()
    else c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Plain next steps for the error the system installer gave. */
private fun suggestions(err: String): List<String> {
    val e = err.uppercase()
    val out = mutableListOf<String>()
    if ("DOWNGRADE" in e) out += "Downgrade needs root. Use Downgrade, or uninstall the newer app first."
    if ("INCOMPATIBLE" in e || "SIGNATURE" in e) out += "The signature differs from the installed app. Remove and restore data (root), or uninstall the old app first."
    if ("INSUFFICIENT_STORAGE" in e) out += "Free some storage, then try again."
    if ("SDK" in e) out += "This Android version does not match the app's SDK."
    if ("NO_MATCHING_ABIS" in e) out += "The app does not support this phone's CPU type."
    if ("PARSE" in e) out += "The file looks damaged. Download it again."
    if ("TEST_ONLY" in e) out += "This is a test build. It can only be installed with adb install -t."
    if (out.isEmpty()) out += "Read the error above. Restarting the phone and trying again often helps."
    return out
}

/** Runs an install only after a fingerprint or face check, when the Installer setting asks for it. */
object BioGate {
    /** The open prompt, so dismissing the window can cancel it. */
    var pending: CancellationSignal? = null

    fun run(c: Context, then: () -> Unit) {
        if (!InstallShow.biometric) { then(); return }
        val cancel = CancellationSignal()
        pending = cancel
        val prompt = BiometricPrompt.Builder(c)
            .setTitle("Install app")
            .setDescription("Confirm it is you to install this app.")
            .setNegativeButton("Cancel", c.mainExecutor) { _, _ -> cancel.cancel() }
            .build()
        prompt.authenticate(cancel, c.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { pending = null; then() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                pending = null
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)
                    Toast.makeText(c, "Install stopped: $errString", Toast.LENGTH_LONG).show()
            }
        })
    }
}

/** The loading bar while an APK is read or installed: plain and white on Material and AMOLED, Windows 11 blue on Windows 11. */
@Composable
private fun BusyLine(w: PTheme) {
    LinearProgressIndicator(Modifier.fillMaxWidth(), color = if (w.material) w.text else w.accent, trackColor = Color.Transparent)
}

/** The look of the install window, from Settings > Installer. */
@Composable
private fun installLook(): PTheme {
    val t = when (InstallShow.theme) {
        "material" -> monoWindow(effective(Themes.first { it.id == "material" }))
        "amoled" -> amoledWindow()
        else -> effective(Themes.first { it.id == "win11" })
    }
    // Windows 10 stays square; the others are rounded (16 dp) unless the setting is off; same colours
    return PTheme(id = t.id, name = t.name, dark = t.dark, bg = t.bg, panel = t.panel, text = t.text, sub = t.sub, accent = t.accent, line = t.line,
        radius = if (t.id != "win10" && InstallShow.round) 16.dp else 0.dp, tabs = t.tabs, material = t.material, classicGraph = t.classicGraph,
        bevel = t.bevel, title = t.title, titleText = t.titleText, titleName = t.titleName, tabSel = t.tabSel, glassA = t.glassA, wall = t.wall)
}

/** Shown while an APK from another app is copied into the cache: the download status, with a bar. */
@Composable
fun ApkCopying(progress: Float) {
    CompositionLocalProvider(LocalWindowLook provides installLook()) {
        val w = LocalWindowLook.current
        WinAlert(onDismissRequest = {}, text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BusyLine(w)
                Text("Copying the APK... ${(progress * 100).toInt()}%", color = w.sub, fontSize = 13.sp)
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = Look.accent)
            }
        }, confirmButton = {})
    }
}

/** Check mark that pops in when the install is confirmed by PackageManager. */
@Composable
private fun DoneMark() {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = .5f)) }
    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(48.dp).graphicsLayer { scaleX = grow.value; scaleY = grow.value }, tint = Look.accent)
}

/** A Material 3 tonal button in the installer's look: a pill on Material and AMOLED, square corners on Windows 11. */
@Composable
private fun IButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val w = LocalWindowLook.current
    val shape = if (w.material) RoundedCornerShape(50) else RoundedCornerShape(4.dp)
    val ink = if (danger) Warn else w.text
    FilledTonalButton(onClick, modifier, shape = shape, border = BorderStroke(1.dp, w.line),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = w.bg, contentColor = ink)) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OptRow(label: String, on: Boolean, set: (Boolean) -> Unit) {
    val w = LocalWindowLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = w.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        PChip(on, "On") { set(true) }
        Spacer(Modifier.width(6.dp))
        PChip(!on, "Off") { set(false) }
    }
}

/** The install options for this app, from the active profile; the header opens and closes them. */
@Composable
private fun InstallOptions(open: Boolean, toggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    val prof = Installer.cur
    Row(Modifier.fillMaxWidth().clickable { toggle(!open) }, verticalAlignment = Alignment.CenterVertically) {
        Text("Install options", color = w.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (open) "\u25BE" else "\u25B8", color = w.sub, fontSize = 13.sp)
    }
    if (open) {
        OptRow("Optimise after install (dexopt)", prof.dex) { v -> Installer.edit(ctx) { it.copy(dex = v) } }
        OptRow("Delete the source file after install", prof.dropSrc) { v -> Installer.edit(ctx) { it.copy(dropSrc = v) } }
        OptRow("Install on open", prof.auto) { v -> Installer.edit(ctx) { it.copy(auto = v) } }
    }
}

@Composable
private fun VersionRows(pk: Picked, old: OldApp?) {
    val w = LocalWindowLook.current
    val new = "${pk.version.ifEmpty { "-" }} (${pk.code})"
    val prev = old?.let { "${it.version.ifEmpty { "-" }} (${it.code})" }
    if (prev == null || (InstallShow.sameSingle && prev == new)) { Text("Version: $new", color = w.text, fontSize = 14.sp); return }
    if (InstallShow.cmpSingle) Text("Version: $prev \u2192 $new", color = w.text, fontSize = 14.sp)
    else {
        Text("Installed: $prev", color = w.text, fontSize = 14.sp)
        Text("New: $new", color = w.text, fontSize = 14.sp)
    }
}

@Composable
private fun SdkRows(pk: Picked, old: OldApp?) {
    val w = LocalWindowLook.current
    val same = old == null || (old.minSdk == pk.minSdk && old.target == pk.targetSdk)
    if (same && (old == null || InstallShow.sameSingle)) { Text("SDK: min ${pk.minSdk}, target ${pk.targetSdk}", color = w.sub, fontSize = 12.sp); return }
    val o = old ?: return
    if (InstallShow.sdkMulti) {
        Text("Min SDK: ${o.minSdk} \u2192 ${pk.minSdk}", color = w.sub, fontSize = 12.sp)
        Text("Target SDK: ${o.target} \u2192 ${pk.targetSdk}", color = w.sub, fontSize = 12.sp)
    } else Text("SDK: min ${o.minSdk} \u2192 ${pk.minSdk}, target ${o.target} \u2192 ${pk.targetSdk}", color = w.sub, fontSize = 12.sp)
}

/** The module card: what the module needs, and an Open button for LSPosed. */
@Composable
private fun XposedRows(m: XposedInfo) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (w.material) 16.dp else 4.dp)).background(w.line.copy(alpha = .3f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Xposed module", color = Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (m.description.isNotEmpty()) Text(m.description, color = w.sub, fontSize = 12.sp)
        if (m.minVersion.isNotEmpty()) Text("Needs Xposed API ${m.minVersion} or newer", color = w.sub, fontSize = 12.sp)
        Text("Turn it on in LSPosed", color = w.sub, fontSize = 12.sp)
        IButton("Open", Modifier.fillMaxWidth()) { openLSPosed(ctx) }
    }
}

/** Centred install window for an APK, in the look chosen under Settings > Installer. */
@Composable
fun InstallWindow(file: File, onDone: () -> Unit) {
    CompositionLocalProvider(LocalWindowLook provides installLook()) { InstallWindowBody(file, onDone) }
}

@Composable
private fun InstallWindowBody(file: File, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    val fancy = w.material
    val scope = rememberCoroutineScope()
    var p by remember(file) { mutableStateOf<Picked?>(null) }
    var icon by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    var xp by remember(file) { mutableStateOf<XposedInfo?>(null) }
    var old by remember(file) { mutableStateOf<OldApp?>(null) }
    var error by remember(file) { mutableStateOf("") }
    var busy by remember(file) { mutableStateOf(false) }
    // true from the moment the session is handed to PackageManager until its result (or a failure) arrives
    var installing by remember(file) { mutableStateOf(false) }
    var doneOk by remember(file) { mutableStateOf(false) }
    var moduleDone by remember(file) { mutableStateOf(false) }
    var more by remember(file) { mutableStateOf(false) }
    var opts by remember(file) { mutableStateOf(InstallShow.expand) }
    val root = Shell.root == true
    val enter = remember(file) { Animatable(0f) }
    LaunchedEffect(file) { enter.animateTo(1f, tween(360)) }

    LaunchedEffect(file) {
        try {
            val picked = withContext(Dispatchers.IO) { Installer.inspect(ctx, Uri.fromFile(file)) }
            val path = picked.files.first().path
            val mod = withContext(Dispatchers.IO) { xposedInfo(ctx, path) }
            picked.xposed = mod != null
            xp = mod
            old = withContext(Dispatchers.IO) { oldApp(ctx, picked.pkg) }
            p = picked
            icon = withContext(Dispatchers.IO) { apkIcon(ctx, path) }
        } catch (e: Exception) { error = e.message ?: "Not an app package" }
    }

    // hands the package to the system installer; the result comes back through InstallReceiver
    fun commit(pk: Picked) {
        pk.drop = Installer.cur.dropSrc
        if (Installer.cur.mode == "background") { Installer.startBackground(ctx, listOf(pk)); onDone(); return }
        busy = true; installing = true; error = ""
        Installer.outcome = null
        scope.launch {
            try {
                Installer.pending = pk
                withContext(Dispatchers.IO) { Installer.commit(ctx, pk) }
                // the window stays on screen; InstallReceiver's result comes back through Installer.outcome
            } catch (e: Exception) {
                Installer.finish(ctx, false, e.message.orEmpty())
                error = e.message ?: "Install failed"
                busy = false; installing = false
            }
        }
    }

    // the result from InstallReceiver: done only when PackageManager really lists the app
    val outcome = Installer.outcome
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        val pk = p ?: return@LaunchedEffect
        if (o.pkg != pk.pkg) return@LaunchedEffect
        val installed = o.ok && withContext(Dispatchers.IO) { Installer.installedCode(ctx, pk.pkg) } != null
        if (installed) doneOk = true else error = o.msg.ifEmpty { "Install failed" }
        busy = false; installing = false
    }

    // the installed app opens when its install is done, and the window closes behind it
    LaunchedEffect(doneOk) {
        if (doneOk) p?.pkg?.let { pkg -> ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onDone() } }
    }

    // silent install with root: keeps data for a downgrade (-d) when the signature matches
    fun silent(pk: Picked, down: Boolean) {
        busy = true
        pk.drop = Installer.cur.dropSrc
        scope.launch {
            val out = withContext(Dispatchers.IO) { Shell.run(Installer.silentCmd(pk, down, 0, Installer.cur.dex)) }
            if (out.contains("Success")) {
                if (pk.drop) Installer.dropSource(ctx, pk)
                // a module stays on screen so the LSPosed button can follow the install
                if (pk.xposed) moduleDone = true else doneOk = true
                busy = false
            } else { error = out.trim().ifEmpty { "Install failed" }; busy = false }
        }
    }

    fun restore(pk: Picked) {
        busy = true
        pk.drop = Installer.cur.dropSrc
        scope.launch {
            val msg = withContext(Dispatchers.IO) { removeAndRestore(pk) }
            if (msg.startsWith("Restored")) { if (pk.drop) Installer.dropSource(ctx, pk); doneOk = true; busy = false } else { error = msg; busy = false }
        }
    }

    // the uninstall dialog returns here; the install goes on only when the old app is really gone
    val uninstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val pk = p ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (withContext(Dispatchers.IO) { Installer.installedCode(ctx, pk.pkg) } == null) commit(pk)
            else error = "Not uninstalled"
        }
    }

    // install on open: only New or Upgrade, never a blacklisted package, and only after the biometric check if it is on
    LaunchedEffect(p) {
        val a = p ?: return@LaunchedEffect
        // with root the APK goes to pm at once; without root only an automatic profile installs without a tap
        if ((root || Installer.cur.auto) && a.pkg !in Installer.black && statusOf(a) in listOf(Status.NEW, Status.UPGRADE)) BioGate.run(ctx) { if (root) silent(a, down = false) else commit(a) }
    }

    val pk = p
    val st = pk?.let { statusOf(it) }
    // the Android logo: its wordmark is white, so it reads on AMOLED and dark windows; black on light windows, accent on Material
    val logoTint = when { w.id == "amoled" -> null; w.material -> ColorFilter.tint(w.accent); w.dark -> null; else -> ColorFilter.tint(Color.Black) }
    fun gate(f: () -> Unit) = BioGate.run(ctx, f)
    WinAlert(
        // the window stays until the install is really done; only a finished or idle window can be closed
        onDismissRequest = { if (!installing) { if (InstallShow.cancelOnDismiss) BioGate.pending?.cancel(); onDone() } },
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().graphicsLayer {
                if (fancy) { alpha = enter.value; scaleX = .92f + .08f * enter.value; scaleY = .92f + .08f * enter.value }
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val img = icon
                when {
                    doneOk -> DoneMark()
                    img != null -> Image(img, null, Modifier.size(64.dp))
                    fancy -> BusyLine(w)
                    else -> Image(painterResource(R.drawable.ic_android_logo), null, Modifier.size(64.dp), colorFilter = logoTint)
                }
                if (pk == null) {
                    Text(error.ifEmpty { "Reading ${file.name}..." }, color = w.sub, fontSize = 13.sp)
                    return@Column
                }
                if (InstallShow.name) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pk.label, color = w.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(22.dp).background(w.line, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Android, null, Modifier.size(14.dp), tint = w.text)
                    }
                }
                if (InstallShow.pkg) Text(pk.pkg, color = w.sub, fontSize = 12.sp)
                if (InstallShow.version) VersionRows(pk, old)
                if (InstallShow.chip) {
                    val (chipBg, chipInk) = when (st) {
                        Status.NEW, Status.UPGRADE -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
                        Status.DOWNGRADE, Status.SIG -> Color(0xFFFFCDD2) to Color(0xFFB71C1C)
                        else -> w.line.copy(alpha = .5f) to w.text
                    }
                    Box(Modifier.background(chipBg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(st!!.label, color = chipInk, fontSize = 12.sp)
                    }
                }
                // the warning for the state, shown above the buttons
                val warning = when (st) {
                    Status.SIG -> if (root) "The signature differs from the installed app. Remove and restore data deletes the app, then puts its data back. If the restore fails, the data stays in $BACKUP_DIR."
                        else "The signature differs, so Install will fail. Removing the app and keeping its data needs root."
                    Status.DOWNGRADE -> if (root) "A downgrade keeps the data only when the signature matches. Otherwise use Remove and restore data, which may break the data for an older version."
                        else "Downgrade needs root to keep the data. Without it the system refuses the older version."
                    else -> null
                }
                if (warning != null) Text(warning, color = if (st == Status.SIG) Warn else w.sub, fontSize = 12.sp)
                if (InstallShow.sdk) SdkRows(pk, old)
                xp?.let { XposedRows(it) }
                if (InstallShow.cert) InfoRows(listOf("Certificate" to (pk.sigs.firstOrNull()?.let { "SHA-256 " + it.take(23) + "..." } ?: "Not signed")))
                SplitList(pk)
                if (error.isNotEmpty()) Text(error, color = Warn, fontSize = 12.sp)
                if (error.isNotEmpty() && InstallShow.suggest) suggestions(error).forEach { Text("\u2022 $it", color = w.sub, fontSize = 12.sp) }
                val full = Modifier.fillMaxWidth()
                if (moduleDone) {
                    Text("${pk.label} is installed. Turn it on in LSPosed, then reboot if LSPosed asks.", color = w.text, fontSize = 13.sp)
                    IButton("Open LSPosed", full) { openLSPosed(ctx) }
                    IButton("Close", full) { onDone() }
                    return@Column
                }
                if (doneOk) {
                    Text("${pk.label} is installed.", color = w.text, fontSize = 13.sp)
                    IButton("Close", full) { onDone() }
                    return@Column
                }
                if (busy) {
                    // the animation keeps moving until the result; the system installer may ask for a confirm tap meanwhile
                    BusyLine(w)
                    Text(if (installing) "Installing ${pk.label}... Confirm in the system installer if it asks."
                        else "Working...", color = w.sub, fontSize = 12.sp)
                    return@Column
                }
                if (InstallShow.extended) {
                    IButton(if (more) "Fewer options" else "More options", full) { more = !more }
                    if (more) {
                        IButton("Copy package name", full) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("package", pk.pkg))
                        }
                        if (old != null) IButton("App info", full) {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${pk.pkg}")))
                        }
                    }
                }
                InstallOptions(opts) { opts = it }
                if (pk.pkg in Installer.black) Text("On the blacklist: Pulse does not install this package", color = Warn, fontSize = 12.sp)
                else when (st) {
                    Status.NEW, Status.UPGRADE, Status.IDENTICAL -> IButton(if (st == Status.UPGRADE) "Upgrade" else "Install", full) { gate { if (root) silent(pk, down = false) else commit(pk) } }
                    Status.DOWNGRADE -> {
                        IButton("Downgrade", full) { gate { if (root && pk.sigs == pk.installedSigs) silent(pk, down = true) else commit(pk) } }
                        if (root) IButton("Remove and restore data", full, danger = true) { gate { restore(pk) } }
                    }
                    Status.SIG -> if (root) IButton("Remove and restore data", full, danger = true) { gate { restore(pk) } }
                    null -> Unit
                }
                IButton("Cancel", full) { onDone() }
            }
        },
        confirmButton = {},
        dismissButton = null,
        containerColor = w.panel.copy(alpha = .8f), // see-through: what is behind shows faintly, never black
        noDim = true,
    )
}

/** Android does not let an app make itself the default: this opens the screen where the user picks Pulse for APK files. */
fun openDefaultApkSettings(c: Context) {
    val i = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${c.packageName}"))
    runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** What the install window shows and how, switched in Settings > Installer; kept in the "p" preferences. */
object InstallShow {
    var name by mutableStateOf(true)
    var version by mutableStateOf(true)
    var pkg by mutableStateOf(false)
    var chip by mutableStateOf(true)
    /** The window's look: win11, material or amoled. */
    var theme by mutableStateOf("amoled")
    var biometric by mutableStateOf(false)
    var cmpSingle by mutableStateOf(true)
    var sdkMulti by mutableStateOf(true)
    var sameSingle by mutableStateOf(true)
    var extended by mutableStateOf(false)
    var suggest by mutableStateOf(true)
    var expand by mutableStateOf(false)
    var cancelOnDismiss by mutableStateOf(false)
    /** Rounded corners in the install window; off = square. */
    var round by mutableStateOf(true)
    /** SDK min and target rows; off by default to show less. */
    var sdk by mutableStateOf(false)
    /** The certificate fingerprint row; off by default. */
    var cert by mutableStateOf(false)
    private var loaded = false
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE)
    fun load(c: android.content.Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        name = p.getBoolean("inst_name", true); version = p.getBoolean("inst_ver", true)
        pkg = p.getBoolean("inst_pkg", false); chip = p.getBoolean("inst_chip", true)
        theme = p.getString("inst_theme", "amoled") ?: "amoled"
        biometric = p.getBoolean("inst_bio", false); cmpSingle = p.getBoolean("inst_cmp1", true)
        sdkMulti = p.getBoolean("inst_sdkm", true); sameSingle = p.getBoolean("inst_same1", true)
        extended = p.getBoolean("inst_ext", false); suggest = p.getBoolean("inst_sugg", true)
        expand = p.getBoolean("inst_exp", false); cancelOnDismiss = p.getBoolean("inst_cancel", false)
        round = p.getBoolean("inst_round", true)
        sdk = p.getBoolean("inst_sdk", false); cert = p.getBoolean("inst_cert", false)
    }
    fun set(c: android.content.Context, key: String, v: Boolean) {
        when (key) {
            "inst_name" -> name = v; "inst_ver" -> version = v; "inst_pkg" -> pkg = v; "inst_chip" -> chip = v
            "inst_bio" -> biometric = v; "inst_cmp1" -> cmpSingle = v; "inst_sdkm" -> sdkMulti = v; "inst_same1" -> sameSingle = v
            "inst_ext" -> extended = v; "inst_sugg" -> suggest = v; "inst_exp" -> expand = v; "inst_cancel" -> cancelOnDismiss = v
            "inst_round" -> round = v; "inst_sdk" -> sdk = v; "inst_cert" -> cert = v
        }
        prefs(c).edit().putBoolean(key, v).apply()
    }
    fun setTheme(c: android.content.Context, v: String) { theme = v; prefs(c).edit().putString("inst_theme", v).apply() }
}
