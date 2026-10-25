package com.umutk.pulse.features

import android.content.ClipData
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.umutk.pulse.Cap7
import com.umutk.pulse.CapGlyph95
import com.umutk.pulse.TitleIcon95
import com.umutk.pulse.bevel
import com.umutk.pulse.winButtonLooks
import com.umutk.pulse.xpCaption
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import com.umutk.pulse.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.withStyle
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
import com.umutk.pulse.PSwitch
import com.umutk.pulse.onAccent
import com.umutk.pulse.Look
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PTheme
import com.umutk.pulse.R
import com.umutk.pulse.Shell
import com.umutk.pulse.tr
import androidx.compose.ui.draw.alpha
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

/** The standalone Files app shows the install window inside its own task (FilesRoot): no second activity, Pulse is never started. */
object FilesInstall { var file by mutableStateOf<File?>(null) }

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
    val bak = "$BACKUP_DIR/${pk.pkg}"
    fun fail(en: String, trk: String) = tr("Backup failed, nothing was removed: $en", "Yedek alınamadı, hiçbir şey silinmedi: $trk")
    if (!Regex("[A-Za-z0-9_.]+").matches(pk.pkg)) return fail("bad package name.", "geçersiz paket adı.")
    if (Shell.root != true && !Shell.detectRoot()) return fail("root is needed. Shizuku runs as the shell user, which cannot read other apps' data.", "root gerekli. Shizuku shell kullanıcısıdır, diğer uygulamaların verisini okuyamaz.")
    if (!Shell.runRoot("id -u").trim().endsWith("0")) return fail("root was refused.", "root reddedildi.")
    // the app's data folder: several paths, each seen through every root mount view (plain su may run in an isolated namespace)
    val why = StringBuilder()
    val (mode, data) = Shell.rootSee(Shell.dataPaths(pk.pkg), why)
        ?: return fail("root cannot see the app's data folder. Tried: $why", "root uygulamanın veri klasörünü göremiyor. Denenen: $why")
    fun count(d: String) = Shell.runRoot("find '$d' | wc -l", mode).trim().toIntOrNull() ?: -1
    val saved = Shell.runRoot("rm -rf '$bak' && mkdir -p '$bak' && cp -a '$data/.' '$bak/' && echo BACKUP_OK", mode)
    if (!saved.contains("BACKUP_OK")) return fail(saved.trim(), saved.trim())
    val n = count(data)
    if (n < 1 || n != count(bak)) { Shell.runRoot("rm -rf '$bak'", mode); return fail("the copy does not match the original.", "kopya aslıyla uyuşmuyor.") }
    val un = Shell.runRoot("pm uninstall ${pk.pkg}")
    if (!un.contains("Success")) { Shell.runRoot("rm -rf '$bak'", mode); return tr("Could not remove the app, nothing changed: ${un.trim()}", "Uygulama kaldırılamadı, hiçbir şey değişmedi: ${un.trim()}") }
    val out = Shell.runRoot(Installer.silentCmd(pk, down = true, user = 0))
    if (!out.contains("Success")) return tr("Install failed, data kept in $bak: ${out.trim()}", "Kurulum başarısız, veri $bak içinde: ${out.trim()}")
    val uid = Shell.runRoot("stat -c %u '$data'", mode).trim()
    if (uid.toIntOrNull() == null) return tr("Installed, but the new data folder is missing. Data kept in $bak", "Kuruldu ama yeni veri klasörü yok. Veri $bak içinde")
    val back = Shell.runRoot("cp -a '$bak/.' '$data/' && chown -R $uid:$uid '$data' && (restorecon -RF '$data' || true) && echo RESTORED", mode)
    if (!back.contains("RESTORED") || count(data) < n) return tr("Restore failed, data kept in $bak: ${back.trim()}", "Geri yükleme başarısız, veri $bak içinde: ${back.trim()}")
    Shell.runRoot("rm -rf '$bak'", mode)
    return "Restored data for ${pk.label}"
}

/** The installed app as it is now, shown beside the new APK. */
private data class OldApp(val version: String, val code: Long, val minSdk: Int, val target: Int)

/** Xposed fields from the APK's meta-data: the description and the minimum API version. */
private data class XposedInfo(val description: String, val minVersion: String)

private fun oldApp(ctx: Context, pkg: String): OldApp? = try {
    val i = ctx.packageManager.getPackageInfo(pkg, 0)
    val ai = i.applicationInfo
    OldApp(i.versionName.orEmpty(), com.umutk.pulse.Compat.longVersion(i), if (android.os.Build.VERSION.SDK_INT >= 24) ai?.minSdkVersion ?: 0 else 0, ai?.targetSdkVersion ?: 0)
} catch (e: PackageManager.NameNotFoundException) { null }

private fun xposedInfo(ctx: Context, path: String): XposedInfo? {
    val ai = ctx.packageManager.getPackageArchiveInfo(path, PackageManager.GET_META_DATA)?.applicationInfo ?: return null
    ai.sourceDir = path; ai.publicSourceDir = path
    if (!isXposedModule(ai)) return null
    val md = ai.metaData
    return XposedInfo(md?.getString("xposeddescription").orEmpty(), md?.get("xposedminversion")?.toString().orEmpty())
}

/** LSPosed Manager, or the older EdXposed manager, whichever is installed. Not the patch tools (LSPatch, "core patch"): those are no manager. */
fun lsposedIntent(c: Context): Intent? {
    listOf("org.lsposed.manager", "io.github.lsposed.manager", "com.solohsu.android.edxp.manager")
        .firstNotNullOfOrNull { c.packageManager.getLaunchIntentForPackage(it) }?.let { return it }
    // forks have their own package names: the first installed app with "vector" or "lsposed" in it that can be opened (patch tools left out)
    return c.packageManager.getInstalledPackages(0).map { it.packageName }
        .firstOrNull { p -> (p.contains("vector", true) || p.contains("lsposed", true)) && !p.contains("patch", true) && c.packageManager.getLaunchIntentForPackage(p) != null }
        ?.let { c.packageManager.getLaunchIntentForPackage(it) }
}

/**
 * Vector and LSPosed run their manager "parasitic" (no launcher icon of its own): it opens through the system shell's warning activity with the manager's category,
 * or through the secret dialer code 5776733. Each way is tried in turn; nothing tells whether the manager came up, so a message is shown only when none could be sent.
 */
private fun openLSPosed(c: Context) {
    lsposedIntent(c)?.let { runCatching { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } }
    for (cat in listOf("org.lsposed.manager.LAUNCH_MANAGER", "org.matrix.vector.LAUNCH_MANAGER")) {
        val ok = runCatching {
            c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(cat).setClassName("com.android.shell", "com.android.shell.BugreportWarningActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return
    }
    val sent = runCatching { c.sendBroadcast(Intent("android.provider.Telephony.SECRET_CODE", Uri.parse("android_secret_code://5776733"))) }.isSuccess
    if (!sent) Toast.makeText(c, "LSPosed / Vector Manager could not be opened", Toast.LENGTH_LONG).show()
}

/** Plain next steps for the error the system installer gave. */
private fun suggestions(err: String): List<String> {
    val e = err.uppercase()
    val out = mutableListOf<String>()
    if ("DOWNGRADE" in e) out += "Downgrade needs root. Use Downgrade, or uninstall the newer app first."
    if ("INCOMPATIBLE" in e || "SIGNATURE" in e) out += tr("The signature differs from the installed app. Remove and restore data needs root. Uninstalling the old app instead deletes its data.", "İmza kurulu uygulamadan farklı. Kaldır ve veriyi geri yükle root ister. Eski uygulamayı kaldırmak verisini siler.")
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
        if (android.os.Build.VERSION.SDK_INT < 28) { then(); return }
        askPrompt(c, then)
    }
}

@androidx.annotation.RequiresApi(28)
private fun askPrompt(c: Context, then: () -> Unit) {
    val cancel = CancellationSignal()
    BioGate.pending = cancel
    val prompt = BiometricPrompt.Builder(c)
        .setTitle("Install app")
        .setDescription("Confirm it is you to install this app.")
        .setNegativeButton("Cancel", c.mainExecutor) { _, _ -> cancel.cancel() }
        .build()
    prompt.authenticate(cancel, c.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { BioGate.pending = null; then() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            BioGate.pending = null
            if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)
                Toast.makeText(c, "Install stopped: $errString", Toast.LENGTH_LONG).show()
        }
    })
}

/** Radius (0..1) of each loader shape at angle [a]: circle, 8-lobe burst, rounded pentagon, oval, 9-lobe cookie, rounded square, clover. */
private fun loaderRadius(shape: Int, a: Float): Float {
    val c = kotlin.math.cos(a); val s = kotlin.math.sin(a)
    return when (shape) {
        0 -> 1f
        1 -> (1f + .12f * kotlin.math.cos(8 * a)) / 1.12f
        2 -> {
            val seg = 2f * Math.PI.toFloat() / 5
            val local = a % seg - seg / 2
            val poly = kotlin.math.cos(Math.PI.toFloat() / 5) / kotlin.math.cos(local)
            .65f * poly + .35f
        }
        3 -> { val b = .7f; b / kotlin.math.sqrt(b * b * c * c + s * s) }
        4 -> (1f + .08f * kotlin.math.cos(9 * a)) / 1.08f
        5 -> {
            val sq = 1f / Math.pow((Math.pow(kotlin.math.abs(c).toDouble(), 4.0) + Math.pow(kotlin.math.abs(s).toDouble(), 4.0)), .25).toFloat()
            sq / 1.19f
        }
        else -> (1f + .18f * kotlin.math.cos(6 * a)) / 1.18f
    }
}

/** The Material layout's own loader: a solid shape that keeps turning and morphs from one shape to the next (circle, burst, pentagon, oval, cookie, square, clover), drawn in the accent. */
@Composable
private fun MaterialLoader(color: Color) {
    val count = 7
    val inf = rememberInfiniteTransition(label = "matLoader")
    val step by inf.animateFloat(0f, count.toFloat(), infiniteRepeatable(tween(count * 750, easing = LinearEasing)), label = "step")
    Canvas(Modifier.size(44.dp)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val base = size.minDimension / 2f * .86f
        val from = step.toInt() % count
        val to = (from + 1) % count
        // hold the shape for a third of the step, then ease into the next one
        val f = ((step - step.toInt() - .33f) / .67f).coerceIn(0f, 1f)
        val k = f * f * (3f - 2f * f)
        // one turn per cycle, continuous: a per-step push reset to zero at each step and jumped
        val rot = step * 2f * Math.PI.toFloat() / count
        val path = androidx.compose.ui.graphics.Path()
        val n = 72
        for (i in 0..n) {
            val a = 2f * Math.PI.toFloat() * i / n
            val r = base * (loaderRadius(from, a) * (1f - k) + loaderRadius(to, a) * k)
            val x = cx + r * kotlin.math.cos(a + rot); val y = cy + r * kotlin.math.sin(a + rot)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

/** The loading indicator while an APK is read or installed: the Material layout uses its own shape, the other looks the chosen style in the accent (Material/AMOLED) or white (Windows). Round by default, a rounded bar or a thick short wave; style from Settings > Installer. */
@Composable
private fun BusyLine(w: PTheme) {
    val color = if (w.material) w.accent else Color.White
    val track = color.copy(alpha = .2f)
    if (w.id == "material") { MaterialLoader(color); return }
    if (w.id in winButtonLooks) { WinProgress(w, null); return }
    when (InstallShow.loader) {
        "bar" -> LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = color, trackColor = track)
        "wave" -> {
            // thick and short: a 4 dp tall wave, 6 dp stroke
            val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, 2f * Math.PI.toFloat(),
                infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "phase")
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val amp = size.height * .3f; val mid = size.height / 2f; val step = 4f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, mid + amp * kotlin.math.sin(phase))
                    var x = step
                    while (x <= size.width) { lineTo(x, mid + amp * kotlin.math.sin(x / size.width * 3f * Math.PI.toFloat() + phase)); x += step }
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        else -> CircularProgressIndicator(Modifier.size(36.dp), color = color, strokeWidth = 3.dp, trackColor = track)
    }
}

/** The look of the install window, from Settings > Installer. */
@Composable
internal fun installLook(sel: String = InstallShow.theme): PTheme {
    val id = if (sel == "app") Look.theme.id else sel
    val t = when (id) {
        "amoled" -> amoledWindow()
        "coloros" -> effective(Themes.first { it.id == "coloros" })
        in winButtonLooks, "oneui" -> effective(Themes.first { it.id == id })
        else -> monoWindow(effective(Themes.first { it.id == "material" }))
    }
    // Windows 10 stays square; the others are rounded (16 dp) unless the setting is off; same colours
    return PTheme(id = t.id, name = t.name, dark = t.dark, bg = t.bg, panel = t.panel, text = t.text, sub = t.sub, accent = t.accent, line = t.line,
        radius = if (t.id != "win10" && InstallShow.round) 16.dp else 0.dp, tabs = t.tabs, material = t.material, classicGraph = t.classicGraph,
        bevel = t.bevel, title = t.title, titleText = t.titleText, titleName = t.titleName, tabSel = t.tabSel, glassA = t.glassA, wall = t.wall)
}

/** Shown while an APK from another app is copied into the cache: the download status, with a bar. */
@Composable
fun ApkCopying(progress: Float, onCancel: () -> Unit = {}) {
    CompositionLocalProvider(LocalWindowLook provides installLook()) {
        val w = LocalWindowLook.current
        if (w.id in winButtonLooks) {
            WinFrame(w, null, "Copying the APK", onMin = onCancel, onClose = onCancel) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Copying the APK... ${(progress * 100).toInt()}%", color = w.text, fontSize = 13.sp)
                    WinProgress(w, progress)
                }
            }
            return@CompositionLocalProvider
        }
        WinAlert(onDismissRequest = {}, text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BusyLine(w)
                Text("Copying the APK... ${(progress * 100).toInt()}%", color = w.sub, fontSize = 13.sp)
                val color = if (w.material) w.accent else Color.White
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = color, trackColor = color.copy(alpha = .2f))
            }
        }, confirmButton = {})
    }
}

/** Check mark that pops in when the install is confirmed by PackageManager. */
@Composable
private fun DoneMark() {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = .5f)) }
    val w = LocalWindowLook.current
    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(48.dp).graphicsLayer { scaleX = grow.value; scaleY = grow.value }, tint = if (w.material) w.accent else Look.accent)
}

/**
 * Installer buttons, Material 3, monochrome: stacked full-width pills.
 * [primary] is the main action: a filled tonal button, dark grey with white text.
 * The rest are low-emphasis: a faint white wash with white text. [danger] is the only colour.
 */
@Composable
private fun IButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)   // squarer corners, like the Windows 11 buttons
    val container = when {
        danger -> Color(0x33FF5252)
        primary -> Color(0xFF3A3A3A)
        else -> Color(0x24FFFFFF)
    }
    if (LocalWindowLook.current.id in winButtonLooks) { WinBtn(LocalWindowLook.current, text, modifier, danger, primary, onClick); return }
    // Material 3: a filled pill in the accent for the main action, tonal pills for the rest, ink from the look
    val w = LocalWindowLook.current
    // a Material window wears its own accent (black or white), not the Windows accent colour of the app
    val acc = (if (w.material) w.accent else Look.accent).copy(alpha = 1f)
    val fill = when { danger -> Color(0xFFC42B1C).copy(alpha = .16f); primary -> acc; else -> w.text.copy(alpha = .09f) }
    val ink = when { danger -> Color(0xFFC42B1C); primary -> onAccent(acc); else -> w.text }
    Button(onClick, modifier.height(52.dp), shape = androidx.compose.foundation.shape.CircleShape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = ink)) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OptRow(label: String, on: Boolean, set: (Boolean) -> Unit) {
    val w = LocalWindowLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = w.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        val acc = (if (w.material) w.accent else Look.accent).copy(alpha = 1f)
        androidx.compose.material3.Switch(on, set, colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = onAccent(acc), checkedTrackColor = acc, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = w.sub, uncheckedThumbColor = w.sub))
    }
}

/** The install options for this app, from the active profile; the header opens and closes them. */
@Composable
private fun InstallOptions(open: Boolean, toggle: (Boolean) -> Unit, extra: @Composable () -> Unit = {}) {
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
        // CPU libraries the phone does not use (v7, x86 on an arm64 phone) are cut from a new install of a universal APK
        OptRow(tr("Remove unused CPU libraries (new installs)", "Kullanılmayan işlemci kütüphanelerini kaldır (yeni kurulum)"), InstallShow.shrink) { v -> InstallShow.set(ctx, "inst_shrink", v) }
        extra()
    }
}

/** Install source choice; shared by the install window and Settings > Installer. Disabled without root/Shizuku. */
@Composable
fun InstallSourcePicker() {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    val ok = InstallShow.shellOk
    Column(Modifier.alpha(if (ok) 1f else .5f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Install source", "Kurulum kaynağı"), color = w.text, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PChip(InstallShow.srcMode == "def", "Pulse") { if (ok) InstallShow.setSrc(ctx, "def") }
            PChip(InstallShow.srcMode == "play", "Play Store") { if (ok) InstallShow.setSrc(ctx, "play") }
            PChip(InstallShow.srcMode == "custom", tr("Custom", "Özel")) { if (ok) InstallShow.setSrc(ctx, "custom") }
        }
        if (InstallShow.srcMode == "custom" && ok) androidx.compose.foundation.text.BasicTextField(
            InstallShow.srcCustom, { InstallShow.setSrc(ctx, "custom", it.trim()) }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = w.text, fontSize = 13.sp),
            cursorBrush = SolidColor(w.accent),
            modifier = Modifier.fillMaxWidth().background(w.line.copy(alpha = .3f)).padding(8.dp),
            decorationBox = { f -> if (InstallShow.srcCustom.isEmpty()) Text("com.google.android.packageinstaller", color = w.sub, fontSize = 13.sp); f() })
        if (!ok) Text(tr("Needs root or Shizuku", "Root veya Shizuku gerekir"), color = w.sub, fontSize = 12.sp)
        else if (InstallShow.srcMode != "def") Text(tr("Only the installer name changes; a genuine Play licence check still fails", "Yalnızca kurucu adı değişir; gerçek Play lisans denetimi yine başarısız olur"), color = w.sub, fontSize = 12.sp)
    }
}

@Composable
private fun VersionRows(pk: Picked, old: OldApp?) {
    val w = LocalWindowLook.current
    val new = "${pk.version.ifEmpty { "-" }} (${pk.code})"
    val prev = old?.let { "${it.version.ifEmpty { "-" }} (${it.code})" }
    if (prev == null || (InstallShow.sameSingle && prev == new)) { Text("Version: $new", color = w.text, fontSize = 14.sp); return }
    // one line always: the type shrinks until "old -> new" fits, it never wraps. Installed in pastel red, new in green
    val red = androidx.compose.ui.graphics.Color(0xFFE88B8B); val green = androidx.compose.ui.graphics.Color(0xFF6FCF8F)
    var size by remember(prev, new) { mutableStateOf(14f) }
    Text(androidx.compose.ui.text.buildAnnotatedString {
        append("Version: "); withStyle(androidx.compose.ui.text.SpanStyle(color = red)) { append(prev) }; append(" \u2192 "); withStyle(androidx.compose.ui.text.SpanStyle(color = green)) { append(new) }
    }, color = w.text, fontSize = size.sp, maxLines = 1, softWrap = false,
        onTextLayout = { if (it.didOverflowWidth && size > 8f) size -= .5f })
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
        Text("Xposed module", color = if (w.material) w.accent else Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (m.minVersion.isNotEmpty()) Text("Needs Xposed API ${m.minVersion} or newer", color = w.sub, fontSize = 12.sp)
        Text("Turn it on in LSPosed", color = w.sub, fontSize = 12.sp)
        IButton("Open", Modifier.fillMaxWidth()) { openLSPosed(ctx) }
    }
}

/** Centred install window for an APK, in the look chosen under Settings > Installer. */
@Composable
fun InstallWindow(file: File, look: String? = null, onDone: () -> Unit) {
    CompositionLocalProvider(LocalWindowLook provides installLook(look ?: InstallShow.theme)) { InstallWindowBody(file, onDone) }
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
    // a custom install source needs the shell path (root or Shizuku)
    val useShell = root || InstallShow.srcPkg != null
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

    // after a successful install the window stays on screen with its result; only its Close button (or the back gesture) closes it


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
        if (Installer.cur.auto && a.pkg !in Installer.black && statusOf(a) in listOf(Status.NEW, Status.UPGRADE)) BioGate.run(ctx) { if (useShell) silent(a, down = false) else commit(a) }
    }

    val pk = p
    val st = pk?.let { statusOf(it) }
    // the Android logo: its wordmark is white, so it reads on AMOLED and dark windows; black on light windows, accent on Material
    val logoTint = when { w.id == "amoled" -> null; w.material -> ColorFilter.tint(w.accent); w.dark -> null; else -> ColorFilter.tint(Color.Black) }
    fun gate(f: () -> Unit) = BioGate.run(ctx, f)
    // the window stays until the install is really done; only a finished or idle window can be closed
    val dismiss = { if (!installing) { if (InstallShow.cancelOnDismiss) BioGate.pending?.cancel(); onDone() } }
    val body: @Composable () -> Unit = {
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
                    Status.SIG -> if (root) tr("The signature differs. Remove and restore data copies the data aside and checks the copy, then removes the app, installs this one and puts the data back. If the restore fails, the data stays in $BACKUP_DIR.", "İmza farklı. Kaldır ve veriyi geri yükle önce veriyi kopyalayıp doğrular, sonra uygulamayı kaldırır, bunu kurar ve veriyi geri koyar. Geri yükleme olmazsa veri $BACKUP_DIR içinde kalır.")
                        else tr("The signature differs, so Install will fail. Keeping the data while replacing the app needs root.", "İmza farklı, kurulum başarısız olur. Veriyi koruyarak değiştirmek root ister.")
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
                    // the LSPosed button stays on the module card above; here the app itself opens, as after any install
                    ctx.packageManager.getLaunchIntentForPackage(pk.pkg)?.let { li -> IButton("Open", full, primary = true) { ctx.startActivity(li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); onDone() } }
                    IButton("Close", full) { onDone() }
                    return@Column
                }
                if (doneOk) {
                    Text("${pk.label} is installed.", color = w.text, fontSize = 13.sp)
                    // opens the app that was just installed
                    ctx.packageManager.getLaunchIntentForPackage(pk.pkg)?.let { li -> IButton("Open", full, primary = true) { ctx.startActivity(li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); onDone() } }
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
                InstallOptions(opts, { opts = it })
                if (pk.pkg in Installer.black) Text("On the blacklist: Pulse does not install this package", color = Warn, fontSize = 12.sp)
                else when (st) {
                    Status.NEW, Status.UPGRADE, Status.IDENTICAL -> IButton(if (st == Status.UPGRADE) "Upgrade" else "Install", full, primary = true) { gate { if (useShell) silent(pk, down = false) else commit(pk) } }
                    Status.DOWNGRADE -> {
                        IButton("Downgrade", full, primary = true) { gate { if (useShell && pk.sigs == pk.installedSigs) silent(pk, down = true) else commit(pk) } }
                        if (root) IButton("Remove and restore data", full, danger = true) { gate { restore(pk) } }
                    }
                    Status.SIG -> if (root) IButton("Remove and restore data", full, danger = true) { gate { restore(pk) } }
                    null -> Unit
                }
                IButton("Cancel", full) { onDone() }
            }
    }
    // − hides the window; a committed install goes on in PackageInstaller (window installs post no notification of their own)
    if (w.id in winButtonLooks) WinFrame(w, icon, pk?.label ?: file.name, onMin = onDone, onClose = dismiss, content = body)
    else WinAlert(
        onDismissRequest = dismiss,
        text = body,
        confirmButton = {},
        dismissButton = null,
        containerColor = w.panel, // matte: an opaque panel, nothing shows through the install window
        noDim = true,
    )
}

/** Acrylic grain: a 64 px tile of black and white pixels, drawn at 2% like the Windows noise texture. */
private val noise: ShaderBrush by lazy {
    val b = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
    val r = java.util.Random(11)
    for (y in 0 until 64) for (x in 0 until 64) b.setPixel(x, y, if (r.nextBoolean()) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
    ShaderBrush(ImageShader(b.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * The install window as a real window of the chosen Windows look: frame, title bar with the app icon, title, − and ×, then [content].
 * Windows 11 is acrylic (blur + luminosity + 70% #202020 tint + 2% noise, WinUI's dark recipe); 7's Aero frame is blurred glass too.
 */
@Composable
private fun WinFrame(w: PTheme, icon: ImageBitmap?, title: String, onMin: () -> Unit, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        val ctx = LocalContext.current
        val id = w.id
        val corner = if (id == "win11" && InstallShow.round) 8.dp else if (id == "win7") 6.dp else 0.dp
        val blur = if (id == "win11" || id == "win7") com.umutk.pulse.rememberWindowBlur(corner) else false
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { it.setDimAmount(0f); it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) } }
        val classic = id == "win95" || id == "xp"
        val bodyBg = when (id) { "win95" -> Color(0xFFC0C0C0); "xp" -> Color(0xFFECE9D8); "win7" -> Color.White; "win10" -> w.panel; else -> Color.Transparent }
        val outer = when (id) {
            "win95" -> Modifier.background(bodyBg).bevel(true).padding(3.dp)
            "xp" -> Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Color(0xFF0054E3)).padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
            "win7" -> Modifier.clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFC9DEF3), Color(0xFF9DBDE0), Color(0xFFB4CEEB)).map { it.copy(alpha = if (blur) .7f else 1f) }))
                .border(1.dp, Color(0xFF3A4A5E), RoundedCornerShape(6.dp)).padding(1.dp).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(5.dp))
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
            "win10" -> Modifier.background(bodyBg).border(1.dp, Look.accent.copy(alpha = 1f))
            else -> {
                // acrylic layers over the blur: luminosity, tint, noise; without blur WinUI's opaque fallback colour
                val sh = RoundedCornerShape(corner)
                val base = if (w.dark) Color(0xFF202020) else Color(0xFFF3F3F3)
                Modifier.clip(sh)
                    .then(if (blur) Modifier.background((if (w.dark) Color(0xFF2C2C2C) else Color.White).copy(alpha = .15f)).background(base.copy(alpha = .7f))
                        else Modifier.background((if (w.dark) Color(0xFF1C1C1C) else Color(0xFFEEEEEE)).copy(alpha = .9f)))
                    .background(noise, alpha = .02f)
                    .border(1.dp, if (w.dark) Color.White.copy(alpha = .1f) else Color.Black.copy(alpha = .08f), sh)
            }
        }
        val titleCol = if (classic) Color.White else w.text
        val width = minOf(LocalConfiguration.current.screenWidthDp - 32, 420).dp
        Column(Modifier.width(width).then(outer)) {
            Row(Modifier.fillMaxWidth().height(when (id) { "win95" -> 20.dp; "xp", "win7" -> 28.dp; else -> 32.dp })
                .then(when (id) {
                    "xp" -> Modifier.background(Brush.verticalGradient(0f to Color(0xFF0058EE), .14f to Color(0xFF3A93FF), .32f to Color(0xFF0A60EE), .85f to Color(0xFF0050E0), 1f to Color(0xFF0046C8)))
                    "win95" -> Modifier.background(Color(0xFF000080))
                    "win10" -> Modifier.background(w.panel)
                    else -> Modifier
                })
                .padding(start = if (id == "win95") 3.dp else 10.dp, end = if (classic) 2.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    icon != null -> Image(icon, null, Modifier.size(16.dp))
                    id == "win95" -> TitleIcon95()
                    else -> Icon(Icons.Outlined.Android, null, Modifier.size(16.dp), tint = titleCol)
                }
                Spacer(Modifier.width(6.dp))
                Text(title, color = titleCol, fontSize = if (id == "xp") 13.sp else 12.sp, fontWeight = if (classic) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.weight(1f))
                when (id) {
                    "win95" -> listOf(false to onMin, true to onClose).forEach { (close, f) ->
                        Box(Modifier.padding(start = 2.dp).size(16.dp, 14.dp).background(Color(0xFFC0C0C0)).bevel(true).clickable(onClick = f).capLabel(close), contentAlignment = Alignment.Center) { CapGlyph95(close) }
                    }
                    "xp" -> listOf(false to onMin, true to onClose).forEach { (close, f) ->
                        Box(Modifier.padding(start = 2.dp).size(21.dp).drawBehind { xpCaption(close) }.clickable(onClick = f).capLabel(close))
                    }
                    // Aero buttons hang from the frame top
                    "win7" -> Row(Modifier.offset(y = (-4.5).dp)) { Cap7(false, onMin); Cap7(true, onClose) }
                    else -> { FluentCap(false, titleCol, id == "win11", onMin); FluentCap(true, titleCol, id == "win11", onClose) }
                }
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().background(bodyBg)
                .then(if (id == "win7") Modifier.border(1.dp, Color(0xFF8A9BB0)) else Modifier)
                .verticalScroll(rememberScrollState()).padding(if (classic) 14.dp else 20.dp)) { content() }
        }
    }
}

private fun Modifier.capLabel(close: Boolean) = semantics { contentDescription = if (close) "Close" else "Minimise" }

/** Windows 10 / 11 caption button: 46 x 32, 10 dp glyph in 1 dp lines; × turns red while pressed. */
@Composable
private fun FluentCap(close: Boolean, ink: Color, w11: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val bg = when { close && down -> if (w11) Color(0xFFC42B1C) else Color(0xFFE81123); down -> ink.copy(alpha = .1f); else -> Color.Transparent }
    Box(Modifier.size(46.dp, 32.dp).background(bg).clickable(src, null, onClick = onClick).capLabel(close), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(10.dp)) {
            val g = if (close && down) Color.White else ink; val p = 1.dp.toPx(); val e = size.width
            if (close) { drawLine(g, Offset(0f, 0f), Offset(e, e), p); drawLine(g, Offset(e, 0f), Offset(0f, e), p) }
            else drawLine(g, Offset(0f, e / 2), Offset(e, e / 2), p)
        }
    }
}

/** A button of the Windows look: 95 grey bevel, XP Luna, 7 glossy glass, 10 flat grey (accent edge on the main one), 11 rounded (accent fill on the main one). */
@Composable
private fun WinBtn(w: PTheme, text: String, modifier: Modifier, danger: Boolean, primary: Boolean, onClick: () -> Unit) {
    val id = w.id
    val accent = Look.accent.copy(alpha = 1f)
    val shape = RoundedCornerShape(when (id) { "xp", "win7" -> 3.dp; "win11" -> 4.dp; else -> 0.dp })
    val fill: Brush = when (id) {
        "win95" -> SolidColor(Color(0xFFC0C0C0))
        "xp" -> Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F3EE), Color(0xFFECEBE6), Color(0xFFD6D0C5)))
        "win7" -> Brush.verticalGradient(0f to Color(0xFFF2F2F2), .5f to Color(0xFFEBEBEB), .5f to Color(0xFFDDDDDD), 1f to Color(0xFFCFCFCF))
        "win10" -> SolidColor(if (w.dark) Color(0xFF333333) else Color(0xFFE1E1E1))
        else -> SolidColor(if (primary) accent else if (w.dark) Color.White.copy(alpha = .06f) else Color.White.copy(alpha = .7f))
    }
    val edge = when (id) {
        "xp" -> Color(0xFF003C74); "win7" -> if (primary) Color(0xFF3C7FB1) else Color(0xFF707070)
        "win10" -> if (primary) accent else Color(0xFFADADAD); else -> if (w.dark) Color.White.copy(alpha = .1f) else Color.Black.copy(alpha = .1f)
    }
    val ink = when {
        id == "win11" && primary -> onAccent(accent)
        danger -> if (w.dark) Color(0xFFFF8A80) else Warn
        id == "win10" || id == "win11" -> w.text
        else -> Color.Black
    }
    Box(modifier.heightIn(min = 40.dp).clip(shape).background(fill, shape)
        .then(if (id == "win95") Modifier.bevel(true) else Modifier.border(1.dp, edge, shape))
        .clickable(role = Role.Button, onClick = onClick)
        // XP default button: blue inner ring; 7 default button: soft blue glow
        .then(if (primary && id == "xp") Modifier.padding(1.dp).border(2.dp, Brush.verticalGradient(listOf(Color(0xFFCEE7FF), Color(0xFF6982EE))), shape)
            else if (primary && id == "win7") Modifier.padding(1.dp).border(2.dp, Color(0xCC7FD3F8), shape) else Modifier)
        .padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ink, fontSize = if (classic(id)) 13.sp else 14.sp, fontWeight = if (id == "win11" && primary) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun classic(id: String) = id == "win95" || id == "xp"

/** Progress bar of the Windows look: 95 navy blocks in a sunken well, XP green blocks, 7 glossy green, 10 / 11 thin accent line. [progress] null: a moving piece. */
@Composable
private fun WinProgress(w: PTheme, progress: Float?) {
    val id = w.id
    val t by rememberInfiniteTransition(label = "winBar").animateFloat(0f, 1f, infiniteRepeatable(tween(if (classic(id)) 2400 else 1600, easing = LinearEasing)), label = "run")
    val accent = Look.accent.copy(alpha = 1f)
    val h = when (id) { "win95" -> 18.dp; "xp", "win7" -> 14.dp; "win10" -> 4.dp; else -> 3.dp }
    Canvas(Modifier.fillMaxWidth().height(h).then(if (id == "win95") Modifier.background(Color(0xFFC0C0C0)).bevel(false) else Modifier)) {
        val wd = size.width; val ht = size.height; val p = 1.dp.toPx()
        // the filled span 0..1: the progress, or a 30% piece sliding across
        val s = t * 1.3f - .3f
        val a = if (progress != null) 0f else s.coerceAtLeast(0f)
        val b = progress?.coerceIn(0f, 1f) ?: (s + .3f).coerceAtMost(1f)
        when (id) {
            "win95", "xp" -> {
                val pad = if (id == "win95") 3 * p else 2 * p
                if (id == "xp") { drawRoundRect(Color.White, cornerRadius = CornerRadius(3 * p)); drawRoundRect(Color(0xFF686868), cornerRadius = CornerRadius(3 * p), style = Stroke(p)) }
                val bw = if (id == "win95") 8 * p else 6 * p; val gap = 2 * p
                val x0 = pad + a * (wd - 2 * pad); val x1 = pad + b * (wd - 2 * pad)
                val green = Brush.verticalGradient(listOf(Color(0xFFB7F0B8), Color(0xFF2FC032), Color(0xFF1FA01F)), pad, ht - pad)
                var x = pad
                while (x + bw <= x1 + .5f) {
                    if (x >= x0 - .5f) {
                        if (id == "win95") drawRect(Color(0xFF000080), Offset(x, pad), Size(bw, ht - 2 * pad))
                        else drawRect(green, Offset(x, pad), Size(bw, ht - 2 * pad))
                    }
                    x += bw + gap
                }
            }
            "win7" -> {
                val r = CornerRadius(2 * p)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF3F3F3), Color(0xFFD6D6D6))), cornerRadius = r)
                drawRoundRect(Color(0xFF8E8E8E), cornerRadius = r, style = Stroke(p))
                val x0 = p + a * (wd - 2 * p); val fw = (b - a) * (wd - 2 * p)
                if (fw > 0f) {
                    drawRect(Brush.verticalGradient(0f to Color(0xFF8BE88D), .45f to Color(0xFF37D33A), .55f to Color(0xFF06B025), 1f to Color(0xFF0CA21C), startY = p, endY = ht - p), Offset(x0, p), Size(fw, ht - 2 * p))
                    drawRect(Color.White.copy(alpha = .35f), Offset(x0, p), Size(fw, (ht - 2 * p) * .45f))   // gloss
                }
            }
            else -> {
                val cap = if (id == "win11") StrokeCap.Round else StrokeCap.Butt
                if (id == "win10") drawRect(w.line) else drawLine(w.line.copy(alpha = .6f), Offset(ht / 2, ht / 2), Offset(wd - ht / 2, ht / 2), ht / 3, cap)
                if (b > a) drawLine(accent, Offset(a * wd + (if (id == "win11") ht / 2 else 0f), ht / 2), Offset(b * wd - (if (id == "win11") ht / 2 else 0f), ht / 2), ht, cap)
            }
        }
    }
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
    /** The window's look: material (default), amoled, or a Windows look (win95, xp, win7, win10, win11). */
    var theme by mutableStateOf("material")
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
    /** The loading indicator: round (default), bar (rounded corners) or wave. */
    var loader by mutableStateOf("round")
    /** SDK min and target rows; off by default to show less. */
    var sdk by mutableStateOf(false)
    /** The certificate fingerprint row; off by default. */
    var cert by mutableStateOf(false)
    /** Splits of other CPUs (v7, x86 on an arm64 phone) are left out of the install: unticked from the start. */
    var libs by mutableStateOf(false)
    /** A single APK that is not installed yet is cut down to the phone's CPU libraries and signed again. */
    var shrink by mutableStateOf(false)
    /** Install source: "def" (Pulse), "play" or "custom" with [srcCustom]. Needs root or Shizuku to take effect. */
    var srcMode by mutableStateOf("def")
    var srcCustom by mutableStateOf("")
    /** The installer package to fake, or null for the default. */
    val srcPkg: String? get() = when (srcMode) {
        "play" -> "com.android.vending"
        "custom" -> srcCustom.takeIf { Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(it) }
        else -> null
    }.takeIf { shellOk }
    val shellOk: Boolean get() = Shell.root == true || Shell.shizuku == 2
    fun setSrc(c: android.content.Context, mode: String, custom: String = srcCustom) {
        srcMode = mode; srcCustom = custom
        prefs(c).edit().putString("inst_src", mode).putString("inst_src_custom", custom).apply()
    }
    private var loaded = false
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE)
    fun load(c: android.content.Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        name = p.getBoolean("inst_name", true); version = p.getBoolean("inst_ver", true)
        pkg = p.getBoolean("inst_pkg", false); chip = p.getBoolean("inst_chip", true)
        theme = p.getString("inst_theme", "material") ?: "material"
        biometric = p.getBoolean("inst_bio", false); cmpSingle = p.getBoolean("inst_cmp1", true)
        sdkMulti = p.getBoolean("inst_sdkm", true); sameSingle = p.getBoolean("inst_same1", true)
        extended = p.getBoolean("inst_ext", false); suggest = p.getBoolean("inst_sugg", true)
        expand = p.getBoolean("inst_exp", false); cancelOnDismiss = p.getBoolean("inst_cancel", false)
        round = p.getBoolean("inst_round", true)
        loader = p.getString("inst_loader", "round") ?: "round"
        srcMode = p.getString("inst_src", "def") ?: "def"; srcCustom = p.getString("inst_src_custom", "") ?: ""
        sdk = p.getBoolean("inst_sdk", false); cert = p.getBoolean("inst_cert", false); libs = p.getBoolean("inst_libs", false); shrink = p.getBoolean("inst_shrink", false)
    }
    fun set(c: android.content.Context, key: String, v: Boolean) {
        when (key) {
            "inst_name" -> name = v; "inst_ver" -> version = v; "inst_pkg" -> pkg = v; "inst_chip" -> chip = v
            "inst_bio" -> biometric = v; "inst_cmp1" -> cmpSingle = v; "inst_sdkm" -> sdkMulti = v; "inst_same1" -> sameSingle = v
            "inst_ext" -> extended = v; "inst_sugg" -> suggest = v; "inst_exp" -> expand = v; "inst_cancel" -> cancelOnDismiss = v
            "inst_round" -> round = v; "inst_sdk" -> sdk = v; "inst_cert" -> cert = v; "inst_libs" -> libs = v; "inst_shrink" -> shrink = v
        }
        prefs(c).edit().putBoolean(key, v).apply()
    }
    fun setTheme(c: android.content.Context, v: String) { theme = v; prefs(c).edit().putString("inst_theme", v).apply() }
    fun setLoader(c: android.content.Context, v: String) { loader = v; prefs(c).edit().putString("inst_loader", v).apply() }
}
