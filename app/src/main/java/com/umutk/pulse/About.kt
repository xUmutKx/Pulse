package com.umutk.pulse

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A section that starts folded: the title with a chevron, the body when it is opened. */
@Composable
private fun Fold(title: String, body: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f })
    }
    if (open) body()
}

/** Settings > About: an animated heartbeat line, who made it, version and device, the GitHub link and the privacy promise. */
@Composable
fun AboutPage() {
    val ctx = LocalContext.current
    var licences by remember { mutableStateOf(false) }
    InnerBackStep(licences) { licences = false }
    if (licences) { LicencesPage(); return }
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    val t = rememberInfiniteTransition(label = "about")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "beat")
    val pts = remember { listOf(0f to .5f, .18f to .5f, .28f to .5f, .36f to .18f, .45f to .88f, .54f to .08f, .62f to .62f, .70f to .5f, 1f to .5f) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 12.dp)) {
            val w = size.width; val h = size.height
            val xy = pts.map { Offset(it.first * w, it.second * h) }
            val lens = xy.zipWithNext { a, b -> (b - a).getDistance() }
            val total = lens.sum()
            // the whole line, faint
            for (i in 0 until xy.size - 1) drawLine(Look.accent.copy(alpha = .25f), xy[i], xy[i + 1], 5f, StrokeCap.Round)
            // the bright part trailing the moving dot
            val head = p * total; val tail = (head - total * .3f).coerceAtLeast(0f)
            var acc = 0f
            for (i in 0 until xy.size - 1) {
                val a0 = acc; val a1 = acc + lens[i]; acc = a1
                if (a1 < tail || a0 > head) continue
                fun at(d: Float) = xy[i] + (xy[i + 1] - xy[i]) * ((d - a0) / lens[i].coerceAtLeast(.001f))
                drawLine(Look.accent, at(maxOf(a0, tail)), at(minOf(a1, head)), 6f, StrokeCap.Round)
            }
            acc = 0f
            for (i in 0 until xy.size - 1) {
                if (head in acc..(acc + lens[i])) { val pos = xy[i] + (xy[i + 1] - xy[i]) * ((head - acc) / lens[i].coerceAtLeast(.001f)); drawCircle(Look.accent.copy(alpha = .3f), 16f, pos); drawCircle(Look.accent, 8f, pos) }
                acc += lens[i]
            }
        }
        Text("Pulse", color = T.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("by UmutK", color = Look.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        PCard {
            InfoRows(listOf("Version" to ver, "Package" to ctx.packageName, "Android" to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", "Device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"))
        }
        val openTool = LocalOpenTool.current
        PButton(tr("Guide", "Kılavuz"), Modifier.fillMaxWidth()) { openTool("guide") }
        // moved here from the top-right menu: Settings > About
        PButton(tr("Device troubleshoot", "Cihaz sorun giderme"), Modifier.fillMaxWidth()) { openTool("troubleshoot") }
        PButton("GitHub · xUmutKx/Pulse", Modifier.fillMaxWidth()) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xUmutKx/Pulse")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        // manual check: the same release lookup as the start-up one, without the 12-hour wait
        val scope = rememberCoroutineScope()
        var upd by remember { mutableStateOf<Updates.Release?>(null) }
        var updMsg by remember { mutableStateOf("") }
        PCard(Modifier.clickable {
            scope.launch {
                updMsg = tr("Checking...", "Denetleniyor...")
                val r = Updates.fetch()
                updMsg = when {
                    r == null -> tr("Could not check", "Denetlenemedi")
                    Updates.newer(r.version, Updates.installed(ctx)) -> { upd = r; "" }
                    else -> tr("Up to date", "Güncel")
                }
            }
        }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Check for updates", "Güncellemeleri denetle"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(updMsg, color = T.sub, fontSize = 13.sp, maxLines = 1)
            }
        }
        upd?.let { r -> UpdateDialog(r) { upd = null } }
        Text("An all-in-one file manager for Android with the look of Windows, plus a task manager, a file explorer, downloads, an app installer, network usage, a 20-tool toolkit, widgets and a terminal.", color = T.sub, fontSize = 13.sp)
        PCard {
            Fold("Privacy") {
                Text("No data is shared. Pulse reads your phone's numbers on the phone and keeps them there. The only network use is the latency test, and only when you press Start. Authenticator secrets never leave the phone.", color = T.text, fontSize = 13.sp)
            }
        }
        // licences and credits open on their own page
        PCard(Modifier.clickable { licences = true }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Licences and thanks", "Lisanslar ve teşekkürler"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = -90f })
            }
        }
    }
}

/** Settings > About > Licences and thanks: where the pictures, icons and lists come from. */
@Composable
private fun LicencesPage() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Licences and thanks", "Lisanslar ve teşekkürler"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(vertical = 6.dp))
        PCard {
            Text(
                "Wikimedia Commons (each picture keeps its own licence):\n" +
                "Windows 95 logo (logo_win95.png): commons.wikimedia.org/wiki/File:Windows_95_logo.svg\n" +
                "Windows XP logo (logo_xp.png): commons.wikimedia.org/wiki/Category:Windows_XP_logos\n" +
                "Windows 7 logo (logo_win7.png): commons.wikimedia.org/wiki/Category:Windows_7_logos\n" +
                "Windows 8 logo (logo_win8.png): commons.wikimedia.org/wiki/File:Windows_8_logo_and_wordmark.svg\n" +
                "Windows 10 logo (logo_win10.png): commons.wikimedia.org/wiki/File:Windows_10_Logo.svg\n" +
                "Windows 11 logo (logo_win11.png): commons.wikimedia.org/wiki/File:Windows_11_logo.svg\n" +
                "Bliss, Windows XP wallpaper (splash_xp_bliss.jpg), Charles O'Rear, CC0: commons.wikimedia.org/wiki/Category:Bliss\n" +
                "Android Nougat wallpaper (splash_android.png), AOSP, Apache 2.0: commons.wikimedia.org/wiki/File:Android_Nougat_default_wallpaper_(sw720dp).png\n" +
                "Windows 7 and Windows 11 wallpapers (splash_win7_wallpaper.jpg, splash_win11_wallpaper.jpg): source not recorded.\n" +
                "Windows 11 icons: Complete-Windows-11-icon-pack.\n" +
                "Windows 7 icons: Windows-7-master (Se7en icon theme).\n" +
                "Windows XP icons: Windows XP High Resolution Icon Pack.\n" +
                "Windows 95 and XP icons: win95-winxp_icons.\n" +
                "Windows 11 wallpapers: windows11wallpapers.\n" +
                "Debloat list: Universal Android Debloater (Next Generation).\n" +
                "Windows 7 gadget art (clocks, CPU Meter, Calendar, Battery, Drive Info): the Windows Sidebar gadgets from the Gadgets-ESR1 pack.\n" +
                "Material and Fluent icons: Google Material Symbols, Microsoft Fluent System Icons (MIT).\n" +
                "Shizuku API: RikkaApps Shizuku (MIT).",
                color = T.sub, fontSize = 12.sp)
        }
        Text("Icons in the Windows themes: Microsoft Fluent System Icons (MIT). The Shizuku API files are from RikkaApps Shizuku (MIT).", color = T.sub, fontSize = 11.sp)
    }
}
