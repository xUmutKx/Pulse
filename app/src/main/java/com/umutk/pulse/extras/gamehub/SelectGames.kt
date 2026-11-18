package com.umutk.pulse.extras.gamehub

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.extras.blackout.Privilege
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun shelfHiddenSet(c: Context): Set<String> = prefs(c).getStringSet("shelfHidden", emptySet()) ?: emptySet()

private fun hasShell() = Privilege.mode.let { it == Privilege.Mode.Root || it == Privilege.Mode.Shizuku }

/** Games the person showed again by hand: the default hiding leaves them visible. */
private fun userShown(c: Context): Set<String> = prefs(c).getStringSet("userShown", emptySet()) ?: emptySet()

/** The games Game Hub knows are games are hidden from the phone by default (root or Shizuku only: nothing can be hidden without them), except the ones the person showed again. */
internal fun autoHideGames(c: Context) {
    if (!prefs(c).getBoolean("autoHide", true) || !hasShell()) return
    val keep = userShown(c); val have = hiddenSet(c); val mine = customSet(c)
    loadGames(c).filter { it.pkg !in have && it.pkg !in keep && it.pkg !in mine && !it.pkg.contains(':') }.forEach { runCatching { setGameHidden(c, it.pkg, true, quiet = true) } }
}

private class SelRow(val pkg: String, val name: String, val hidden: Boolean)

/** Select games: the hidden games on top (they fold away), then every app of the phone; a tick hides the app from the phone (root or Shizuku), no tick shows it. */
@Composable
internal fun SelectGamesDialog(ctx: Context, pal: Pal, onClose: () -> Unit, onChanged: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var folded by remember { mutableStateOf(false) }
    val rows by produceState<List<SelRow>>(emptyList(), tick) {
        value = withContext(Dispatchers.IO) {
            val hidden = hiddenSet(ctx)
            val pm = ctx.packageManager
            val hiddenRows = (loadGames(ctx).filter { it.pkg in hidden }.map { SelRow(it.pkg, it.name, true) } + runCatching { hiddenAppsAll(ctx) }.getOrDefault(emptyList()).map { SelRow(it.pkg, it.name, true) })
                .distinctBy { it.pkg }
            val seen = hiddenRows.map { it.pkg }.toSet()
            val apps = runCatching {
                pm.getInstalledApplications(0).filter { it.packageName != ctx.packageName && it.packageName !in seen && pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { SelRow(it.packageName, pm.getApplicationLabel(it).toString(), false) }
            }.getOrDefault(emptyList())
            hiddenRows.sortedBy { it.name.lowercase() } + apps.sortedBy { it.name.lowercase() }
        }
    }
    fun toggle(r: SelRow) {
        if (!hasShell()) { android.widget.Toast.makeText(ctx, tr("Hiding needs root or Shizuku", "Gizlemek için root ya da Shizuku gerekir"), android.widget.Toast.LENGTH_SHORT).show(); return }
        val hide = !r.hidden
        if (setGameHidden(ctx, r.pkg, hide, quiet = true)) {
            // a game the person shows again stays visible when the default hiding runs next time
            prefs(ctx).edit().putStringSet("userShown", if (hide) userShown(ctx) - r.pkg else userShown(ctx) + r.pkg).apply()
            tick++; onChanged()
        } else android.widget.Toast.makeText(ctx, tr("Not changed", "Değişmedi"), android.widget.Toast.LENGTH_SHORT).show()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).background(pal.card, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Select games", "Oyunları seç"), color = pal.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text("✕", color = pal.text, fontSize = 18.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            if (!hasShell()) Text(tr("Without root or Shizuku an app cannot be hidden from the phone.", "Root ya da Shizuku olmadan uygulama telefondan gizlenemez."), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            val hiddenRows = rows.filter { it.hidden }; val appRows = rows.filter { !it.hidden }
            LazyColumn(Modifier.weight(1f)) {
                item(key = "hh") {
                    Row(Modifier.fillMaxWidth().clickable { folded = !folded }.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Hidden games", "Gizli oyunlar") + " · ${hiddenRows.size}", color = pal.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(if (folded) "▾" else "▴", color = pal.sub, fontSize = 14.sp)
                    }
                }
                if (!folded) hiddenRows.forEach { r ->
                    item(key = "h_${r.pkg}") { SelLine(r, true, pal) { toggle(r) } }
                }
                item(key = "ha") { Text(tr("All apps of the phone", "Telefondaki tüm uygulamalar") + " · ${appRows.size}", color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                appRows.forEach { r -> item(key = "a_${r.pkg}") { SelLine(r, false, pal) { toggle(r) } } }
            }
        }
    }
}

@Composable
private fun SelLine(r: SelRow, ticked: Boolean, pal: Pal, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Checkbox(ticked, { onClick() }, colors = CheckboxDefaults.colors(checkedColor = pal.accent, uncheckedColor = pal.sub))
        Text(r.name, color = pal.text, fontSize = 15.sp, maxLines = 1)
    }
}
