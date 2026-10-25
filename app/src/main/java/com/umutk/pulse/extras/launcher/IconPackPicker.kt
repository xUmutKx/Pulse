package com.umutk.pulse.extras.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PRadio
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A few apps of the phone to show every pack on: (package, activity, label), the first six launchable ones by name. */
private fun sampleApps(ctx: Context): List<Triple<String, String, String>> {
    val pm = ctx.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }
        .take(6).map { Triple(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm).toString()) }
}

/** The six sample apps drawn with a pack: "" the phone's own icons, a built-in pack by the Palette engine, an installed pack from its appfilter. */
private fun preview(ctx: Context, id: String, apps: List<Triple<String, String, String>>): List<ImageBitmap?> {
    val pm = ctx.packageManager
    fun original(a: Triple<String, String, String>) = runCatching { pm.getActivityIcon(ComponentName(a.first, a.second)).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    return when {
        id.isEmpty() -> apps.map(::original)
        PulseIconPacks.isBuiltin(id) -> runCatching { PulseIconPacks.render(ctx, id, apps, 96) }.getOrDefault(apps.map { null }).mapIndexed { i, b -> b ?: original(apps[i]) }
        else -> {
            val map = IconPacks.filter(ctx, id)
            apps.map { a -> IconPacks.icon(ctx, id, map, a.first, a.second)?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() } ?: original(a) }
        }
    }
}

/** The icon pack list: the packs inside Pulse (made by Palette) and the installed ones in two groups, each with a row of sample icons to see it before choosing. */
@Composable
fun IconPackPicker(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = LocalWindowLook.current
    val apps = remember { sampleApps(ctx) }
    val installed = remember { IconPacks.installed(ctx) }
    @Composable fun Row1(id: String, name: String) {
        val pics by produceState<List<ImageBitmap?>>(emptyList(), id) { value = withContext(Dispatchers.Default) { runCatching { preview(ctx, id, apps) }.getOrDefault(emptyList()) } }
        Column(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PRadio(id == current) { onPick(id) }
                Spacer(Modifier.width(8.dp))
                Text(name, color = w.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Row(Modifier.padding(start = 40.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pics.forEach { b -> if (b != null) Image(b, null, Modifier.size(34.dp)) else Spacer(Modifier.size(34.dp)) }
            }
        }
    }
    @Composable fun Head(t: String) = Text(t, color = w.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    WinAlert(onDismissRequest = onDismiss,
        title = { Text(tr("Icon pack", "Simge paketi"), color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Row1("", tr("Default", "Varsayılan"))
                Head(tr("Made in Pulse (Palette)", "Pulse içinde (Palette)"))
                PulseIconPacks.ids.forEach { Row1(it, PulseIconPacks.label(it)) }
                if (installed.isNotEmpty()) {
                    Head(tr("Installed packs", "Yüklü paketler"))
                    installed.forEach { Row1(it.pkg, it.label) }
                }
                Text(tr("Pulse Palette uses the look made in the Palette app.", "Pulse Palette, Palette uygulamasında hazırlanan görünümü kullanır."), color = w.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { PButton(tr("Open Palette", "Palette'i aç")) { runCatching { ctx.startActivity(Intent().setClassName(ctx, "com.umutk.pulse.ToolOpenActivity").putExtra("route", "palette").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } },
        dismissButton = { PButton(tr("Close", "Kapat")) { onDismiss() } })
}
