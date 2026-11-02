package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.extras.blackout.Privilege
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS = "gamehub"

/** The three looks of Game Hub: Amoled (true black), Material (dark grey) and Windows (light grey). */
private class Pal(val bg: Color, val card: Color, val text: Color, val sub: Color, val accent: Color)

private fun palOf(theme: String): Pal = when (theme) {
    "material" -> Pal(Color(0xFF1C1B1F), Color(0xFF2B2930), Color(0xFFE6E1E5), Color(0xFFCAC4D0), Color(0xFFD0BCFF))
    "windows" -> Pal(Color(0xFFF0F0F0), Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0xFF5A5A5A), Color(0xFF0078D4))
    else -> Pal(Color.Black, Color(0xFF111111), Color.White, Color(0xFF9A9A9A), Color(0xFF00E676))
}

private class GameRow(val pkg: String, val name: String)

private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
private fun hiddenSet(c: Context): Set<String> = prefs(c).getStringSet("hidden", emptySet()) ?: emptySet()
private fun saveHidden(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("hidden", s).apply() }
private fun restrictOn(c: Context, pkg: String): Boolean = prefs(c).getBoolean("restrict_$pkg", false)

/** The games: the installed ones the system marks as games (Android 8 and later), and the hidden ones from the saved name. */
private fun loadGames(c: Context): List<GameRow> {
    val pm = c.packageManager
    val installed = runCatching {
        pm.getInstalledApplications(0)
            .filter { ai -> ai.packageName != c.packageName && Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME }
            .map { ai -> GameRow(ai.packageName, pm.getApplicationLabel(ai).toString()) }
    }.getOrDefault(emptyList())
    val seen = installed.map { it.pkg }.toSet()
    val hiddenRows = hiddenSet(c).filter { it !in seen }.map { GameRow(it, prefs(c).getString("name_$it", null) ?: it) }
    return (installed + hiddenRows).sortedBy { it.name.lowercase() }
}

/** The icon file of a hidden game (kept, because a hidden app has no icon for the system any more). */
private fun iconFile(c: Context, pkg: String) = java.io.File(c.filesDir, "gamehub/icon_" + pkg.replace('.', '_') + ".png")

/** Hides or shows a game in every launcher through the root or Shizuku shell (pm hide / pm unhide). */
private fun setGameHidden(c: Context, pkg: String, hide: Boolean): Boolean {
    // the name and the icon are kept while the game is still visible to the system: afterwards it cannot be found by the system
    if (hide) runCatching {
        val pm = c.packageManager
        val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        prefs(c).edit().putString("name_$pkg", label).apply()
        val bmp = pm.getApplicationIcon(pkg).toBitmap(128, 128)
        iconFile(c, pkg).parentFile?.mkdirs()
        iconFile(c, pkg).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    val out = Privilege.run(if (hide) "pm hide $pkg" else "pm unhide $pkg")
    val ok = out.code >= 0 && out.text.contains("new hidden state")
    if (ok) saveHidden(c, if (hide) hiddenSet(c) + pkg else hiddenSet(c) - pkg)
    else toast(c, tr("Could not change it. Root or Shizuku is needed.", "Değiştirilemedi. Root ya da Shizuku gerekir."))
    return ok
}

/** The game's background restriction: no background run and the restricted standby bucket, lifted again on allow. */
private fun setRestricted(c: Context, pkg: String, restrict: Boolean): Boolean {
    val mode = if (restrict) "ignore" else "allow"
    val bucket = if (restrict) "restricted" else "active"
    val out = Privilege.run("cmd appops set $pkg RUN_IN_BACKGROUND $mode; cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode; am set-standby-bucket $pkg $bucket")
    val ok = out.code >= 0
    if (ok) prefs(c).edit().putBoolean("restrict_$pkg", restrict).apply()
    else toast(c, tr("Could not change it. Root or Shizuku is needed.", "Değiştirilemedi. Root ya da Shizuku gerekir."))
    return ok
}

/** Frees the memory the background apps hold (the cleanup of a game boost). */
private fun freeMemory(c: Context) {
    val out = Privilege.run("am kill-all")
    toast(c, if (out.code >= 0) tr("Background apps closed", "Arka plan uygulamaları kapatıldı") else tr("Needs root or Shizuku", "Root ya da Shizuku gerekir"))
}

private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

/** The game's own icon from its APK, drawn small; an empty square when the system gives none. */
@Composable
private fun GameIcon(pkg: String, pal: Pal) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = iconFile(ctx, pkg)
                if (f.isFile) android.graphics.BitmapFactory.decodeFile(f.path).asImageBitmap()
                else ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(pal.card), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Game Hub: the games, hidden from every launcher with a switch, a profile per game, and the settings with the three looks. */
@Composable
fun GameHubHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(prefs(ctx).getString("theme", "amoled") ?: "amoled") }
    val pal = palOf(theme)
    var tab by remember { mutableStateOf(0) }
    var games by remember { mutableStateOf(emptyList<GameRow>()) }
    var hidden by remember { mutableStateOf(hiddenSet(ctx)) }
    var mode by remember { mutableStateOf(Privilege.mode) }
    var profile by remember { mutableStateOf<GameRow?>(null) }
    fun recheck() { Privilege.detect(); mode = Privilege.mode }
    LaunchedEffect(Unit) {
        games = withContext(Dispatchers.IO) { loadGames(ctx) }
        withContext(Dispatchers.IO) { Privilege.detect() }
        mode = Privilege.mode
    }
    val can = mode != Privilege.Mode.None
    Column(Modifier.fillMaxSize().background(pal.bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", pal.text, 22.dp) }
            Text("Game Hub", color = pal.text, fontSize = 20.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(tab == 0, tr("Library", "Kütüphane")) { tab = 0 }
            PChip(tab == 1, tr("Home", "Ana sayfa")) { tab = 1 }
            PChip(tab == 2, tr("Settings", "Ayarlar")) { tab = 2 }
        }
        when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize()) {
                if (!can) item { Text(tr("Hiding and profiles need root or Shizuku.", "Gizleme ve profiller root ya da Shizuku ister."), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                if (games.isEmpty()) item { Text(tr("No games found on this phone.", "Bu telefonda oyun bulunamadı."), color = pal.sub, fontSize = 14.sp) }
                items(games, key = { it.pkg }) { g ->
                    Row(Modifier.fillMaxWidth().clickable { profile = g }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(g.pkg, pal)
                        Spacer(Modifier.width(12.dp))
                        Text(g.name, color = pal.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PSwitch(g.pkg in hidden, { want -> if (setGameHidden(ctx, g.pkg, want)) hidden = hiddenSet(ctx) }, enabled = can)
                    }
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize()) {
                val hiddenGames = games.filter { it.pkg in hidden }
                item { Text(tr("${hiddenGames.size} hidden from every launcher", "${hiddenGames.size} oyun her başlatıcıdan gizli"), color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                items(hiddenGames, key = { it.pkg }) { g ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(g.pkg, pal)
                        Spacer(Modifier.width(12.dp))
                        Text(g.name, color = pal.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PSwitch(true, { want -> if (setGameHidden(ctx, g.pkg, want)) hidden = hiddenSet(ctx) }, enabled = can)
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Text(tr("Look", "Görünüm"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("amoled" to tr("Amoled", "Amoled"), "material" to "Material", "windows" to "Windows").forEach { (k, label) ->
                        PChip(theme == k, label) { theme = k; prefs(ctx).edit().putString("theme", k).apply() }
                    }
                }
                Text(
                    when (mode) {
                        Privilege.Mode.Root -> tr("Root is on", "Root açık")
                        Privilege.Mode.Shizuku -> tr("Shizuku is on", "Shizuku açık")
                        Privilege.Mode.None -> tr("No root and no Shizuku", "Root ve Shizuku yok")
                    },
                    color = pal.text, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                PChip(false, tr("Check again", "Yeniden kontrol et")) { recheck() }
            }
        }
    }
    // a game's profile: background restriction and a memory clean-up before playing
    profile?.let { g ->
        var restrict by remember(g.pkg) { mutableStateOf(restrictOn(ctx, g.pkg)) }
        Dialog(onDismissRequest = { profile = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card).border(1.dp, pal.sub.copy(alpha = .3f), RoundedCornerShape(18.dp)).padding(18.dp)) {
                Text(g.name, color = pal.text, fontSize = 17.sp)
                Text(tr("Profile", "Profil"), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Restrict in background", "Arka planda kısıtla"), color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    PSwitch(restrict, { want -> if (setRestricted(ctx, g.pkg, want)) restrict = want }, enabled = can)
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.accent.copy(alpha = .18f)).clickable(enabled = can) { freeMemory(ctx) }.padding(12.dp)) {
                    Text(tr("Free memory now", "Şimdi bellek boşalt"), color = pal.text, fontSize = 15.sp)
                }
            }
        }
    }
}
