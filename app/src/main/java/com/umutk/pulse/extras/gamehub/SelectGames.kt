package com.umutk.pulse.extras.gamehub

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The games kept off the shelf by the person (the Select games list). */
internal fun shelfHiddenSet(c: Context): Set<String> = prefs(c).getStringSet("shelfHidden", emptySet()) ?: emptySet()
private fun saveShelfHidden(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("shelfHidden", s).apply() }

private class SelRow(val pkg: String, val name: String, val kind: Int)

/** Select games: the games of the phone, then the hidden ones, then every other app; a tick keeps the game on the shelf (other apps: puts it in the library). */
@Composable
internal fun SelectGamesDialog(ctx: Context, pal: Pal, onClose: () -> Unit, onChanged: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    val rows by produceState<List<SelRow>>(emptyList(), tick) {
        value = withContext(Dispatchers.IO) {
            val games = loadGames(ctx)
            val hidden = hiddenSet(ctx)
            val hiddenAll = runCatching { hiddenAppsAll(ctx) }.getOrDefault(emptyList())
            val pm = ctx.packageManager
            val known = games.map { it.pkg }.toSet()
            val other = runCatching {
                pm.getInstalledApplications(0).filter { it.packageName != ctx.packageName && it.packageName !in known && pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { SelRow(it.packageName, pm.getApplicationLabel(it).toString(), 2) }
            }.getOrDefault(emptyList())
            games.filter { it.pkg !in hidden }.map { SelRow(it.pkg, it.name, 0) }.sortedBy { it.name.lowercase() } +
                (games.filter { it.pkg in hidden }.map { SelRow(it.pkg, it.name, 1) } + hiddenAll.filter { it.pkg !in known }.map { SelRow(it.pkg, it.name, 1) }).sortedBy { it.name.lowercase() } +
                other.sortedBy { it.name.lowercase() }
        }
    }
    var off by remember { mutableStateOf(shelfHiddenSet(ctx)) }
    var mine by remember { mutableStateOf(customSet(ctx)) }
    var hiddenNow by remember { mutableStateOf(hiddenSet(ctx)) }
    // a game is hidden for real (pm hide: gone from every launcher) through root or Shizuku; without them it is only kept off the shelf
    fun setShown(r: SelRow, on: Boolean) {
        if (r.kind == 2) { mine = if (on) mine + r.pkg else mine - r.pkg; saveCustom(ctx, mine) }
        else {
            val done = setGameHidden(ctx, r.pkg, !on, quiet = true)
            hiddenNow = hiddenSet(ctx)
            if (done) { off = off - r.pkg; saveShelfHidden(ctx, off) }
            else {
                off = if (on) off - r.pkg else off + r.pkg; saveShelfHidden(ctx, off)
                android.widget.Toast.makeText(ctx, tr("Hidden from the shelf only: root or Shizuku hides it from the phone", "Yalnız raftan gizlendi: telefondan gizlemek için root ya da Shizuku gerekir"), android.widget.Toast.LENGTH_SHORT).show()
            }
            tick++
        }
        onChanged()
    }
    fun all(on: Boolean) {
        off = if (on) emptySet() else rows.filter { it.kind != 2 }.map { it.pkg }.toSet(); saveShelfHidden(ctx, off); onChanged(); tick++
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).background(pal.card, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Select games", "Oyunları seç"), color = pal.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(tr("All", "Tümü"), color = pal.accent, fontSize = 14.sp, modifier = Modifier.clickable { all(true) }.padding(8.dp))
                Text(tr("None", "Hiçbiri"), color = pal.accent, fontSize = 14.sp, modifier = Modifier.clickable { all(false) }.padding(8.dp))
                Text("✕", color = pal.text, fontSize = 18.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                var last = -1
                rows.forEach { r ->
                    if (r.kind != last) {
                        last = r.kind
                        val label = when (r.kind) { 0 -> tr("Games on this phone", "Telefondaki oyunlar"); 1 -> tr("Hidden games", "Gizli oyunlar"); else -> tr("Other apps", "Diğer uygulamalar") }
                        val n = rows.count { it.kind == r.kind }
                        item(key = "h$last") { Text("$label · $n", color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                    }
                    item(key = "${r.kind}_${r.pkg}") {
                        val on = if (r.kind == 2) r.pkg in mine else r.pkg !in off && r.pkg !in hiddenNow
                        Row(Modifier.fillMaxWidth().clickable { setShown(r, !on) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Checkbox(on, { setShown(r, it) }, colors = CheckboxDefaults.colors(checkedColor = pal.accent, uncheckedColor = pal.sub))
                            Text(r.name, color = pal.text, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
