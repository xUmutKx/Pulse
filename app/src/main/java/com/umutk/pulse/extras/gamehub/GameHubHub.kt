package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.extras.blackout.Privilege
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One game of the library: its package, its name, and whether it is hidden from every launcher (pm hide). */
private class GameRow(val pkg: String, val name: String)

private const val PREFS = "gamehub"

private fun hiddenSet(c: Context): Set<String> = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("hidden", emptySet()) ?: emptySet()

private fun saveHidden(c: Context, s: Set<String>) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("hidden", s).apply() }

/** The installed games: apps the system marks as games (Android 8 and later), not Pulse itself. */
private fun loadGames(c: Context): List<GameRow> {
    val pm = c.packageManager
    return runCatching {
        pm.getInstalledApplications(0)
            .filter { ai -> ai.packageName != c.packageName && Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME }
            .map { ai -> GameRow(ai.packageName, pm.getApplicationLabel(ai).toString()) }
            .sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())
}

/** Hides or shows a game in every launcher through the root or Shizuku shell (pm hide / pm unhide). */
private fun setGameHidden(c: Context, pkg: String, hide: Boolean): Boolean {
    val out = Privilege.run(if (hide) "pm hide $pkg" else "pm unhide $pkg")
    val ok = out.code >= 0 && out.text.contains("new hidden state")
    if (ok) saveHidden(c, if (hide) hiddenSet(c) + pkg else hiddenSet(c) - pkg)
    else Toast.makeText(c, tr("Could not change it. Root or Shizuku is needed.", "Değiştirilemedi. Root ya da Shizuku gerekir."), Toast.LENGTH_SHORT).show()
    return ok
}

/** The game's own icon from its APK, drawn small; null when the system gives none. */
@Composable
private fun GameIcon(pkg: String) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(44.dp).background(T.line.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Game Hub: a library of the phone's games, each one hidden from every launcher with a switch, and the settings. */
@Composable
fun GameHubHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var games by remember { mutableStateOf(emptyList<GameRow>()) }
    var hidden by remember { mutableStateOf(hiddenSet(ctx)) }
    var mode by remember { mutableStateOf(Privilege.mode) }
    fun recheck() { Privilege.detect(); mode = Privilege.mode }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        games = withContext(Dispatchers.IO) { loadGames(ctx) }
        withContext(Dispatchers.IO) { Privilege.detect() }
        mode = Privilege.mode
    }
    Column(Modifier.fillMaxSize().background(T.bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("Game Hub", color = T.text, fontSize = 20.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(tab == 0, tr("Library", "Kütüphane")) { tab = 0 }
            PChip(tab == 1, tr("Home", "Ana sayfa")) { tab = 1 }
            PChip(tab == 2, tr("Settings", "Ayarlar")) { tab = 2 }
        }
        when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize()) {
                if (mode == Privilege.Mode.None) item { Text(tr("Hiding needs root or Shizuku.", "Gizleme için root ya da Shizuku gerekir."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                if (games.isEmpty()) item { Text(tr("No games found on this phone.", "Bu telefonda oyun bulunamadı."), color = T.sub, fontSize = 14.sp) }
                items(games, key = { it.pkg }) { g ->
                    val on = g.pkg in hidden
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(g.pkg)
                        Spacer(Modifier.width(12.dp))
                        Text(g.name, color = T.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PSwitch(on, { want -> if (setGameHidden(ctx, g.pkg, want)) hidden = hiddenSet(ctx) }, enabled = mode != Privilege.Mode.None)
                    }
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize()) {
                val hiddenGames = games.filter { it.pkg in hidden }
                item { Text(tr("${hiddenGames.size} hidden from every launcher", "${hiddenGames.size} oyun her başlatıcıdan gizli"), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                items(hiddenGames, key = { it.pkg }) { g ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(g.pkg)
                        Spacer(Modifier.width(12.dp))
                        Text(g.name, color = T.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PSwitch(true, { want -> if (setGameHidden(ctx, g.pkg, want)) hidden = hiddenSet(ctx) }, enabled = mode != Privilege.Mode.None)
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                val text = when (mode) {
                    Privilege.Mode.Root -> tr("Root is on", "Root açık")
                    Privilege.Mode.Shizuku -> tr("Shizuku is on", "Shizuku açık")
                    Privilege.Mode.None -> tr("No root and no Shizuku", "Root ve Shizuku yok")
                }
                Text(text, color = T.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
                PChip(false, tr("Check again", "Yeniden kontrol et")) { recheck() }
            }
        }
    }
}
