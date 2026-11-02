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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
/** Apps the person added to the library by hand (any app, not only the ones the system marks as games). */
private fun gridDp(c: Context): Int = when (prefs(c).getInt("grid", 1)) { 0 -> 84; 2 -> 128; else -> 100 }
private fun showHiddenInLibrary(c: Context): Boolean = prefs(c).getBoolean("showHidden", true)
private fun customSet(c: Context): Set<String> = prefs(c).getStringSet("custom", emptySet()) ?: emptySet()
private fun saveCustom(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("custom", s).apply() }

/** A hidden game has no name or icon for the system: each one without a saved name is shown for a moment (pm unhide), its name and icon are kept, and it is hidden again. */
private fun readHiddenInfo(c: Context) {
    if (!Privilege.mode.let { it == Privilege.Mode.Root || it == Privilege.Mode.Shizuku }) return
    val pm = c.packageManager
    for (pkg in hiddenSet(c)) {
        if (prefs(c).contains("name_$pkg")) continue
        Privilege.run("pm unhide $pkg")
        runCatching {
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            prefs(c).edit().putString("name_$pkg", label).apply()
            val bmp = pm.getApplicationIcon(pkg).toBitmap(128, 128)
            iconFile(c, pkg).parentFile?.mkdirs()
            iconFile(c, pkg).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        Privilege.run("pm hide $pkg")
    }
}

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
    val added = customSet(c).filter { it !in seen && it !in hiddenSet(c) }.mapNotNull { pkg ->
        runCatching { GameRow(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
    }
    return (installed + hiddenRows + added).distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
}

/** The cover a person picked for a game (at most 3 MB). */
private const val COVER_MAX = 3L * 1024 * 1024
private fun coverFile(c: Context, pkg: String) = java.io.File(c.filesDir, "gamehub/cover_" + pkg.replace('.', '_') + ".jpg")

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
                val cover = coverFile(ctx, pkg)
                val f = iconFile(ctx, pkg)
                if (cover.isFile) android.graphics.BitmapFactory.decodeFile(cover.path).asImageBitmap()
                else if (f.isFile) android.graphics.BitmapFactory.decodeFile(f.path).asImageBitmap()
                else ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(pal.card), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Game Hub: the games, hidden from every launcher with a switch, a profile per game, and the settings with the three looks. */
/** Time in the foreground of each game over the last [days] days (Android usage statistics; needs the usage access, granted by root or Shizuku). */
private fun playTimes(c: Context, days: Int): Map<String, Long> {
    if (Build.VERSION.SDK_INT < 22) return emptyMap()
    val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return emptyMap()
    val end = System.currentTimeMillis()
    val begin = end - days * 86_400_000L
    return runCatching { usm.queryAndAggregateUsageStats(begin, end).mapValues { it.value.totalTimeInForeground } }.getOrDefault(emptyMap())
}

/** Asks the usage access for Pulse through the root or Shizuku shell, so the play time can be read. */
private fun grantUsageAccess(c: Context) {
    Privilege.run("appops set ${c.packageName} GET_USAGE_STATS allow")
}

private fun hm(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

/** One game as a tile of the library: its icon and name. */
@Composable
private fun GameTile(g: GameRow, pal: Pal, selected: Boolean, hidden: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.padding(6.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) pal.accent.copy(alpha = .18f) else pal.card)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameIcon(g.pkg, pal)
        Spacer(Modifier.height(6.dp))
        Text(g.name, color = pal.text, fontSize = 12.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hidden) Text(tr("hidden", "gizli"), color = pal.sub, fontSize = 10.sp)
    }
}

/** A game's panel: play time, hide and background switches, memory clean-up. Used beside the grid on a wide screen and in a dialog on a tall one. */
@Composable
private fun GamePanel(g: GameRow, pal: Pal, can: Boolean, hidden: Boolean, time: Long, onHidden: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var restrict by remember(g.pkg) { mutableStateOf(restrictOn(ctx, g.pkg)) }
    var coverTick by remember(g.pkg) { mutableStateOf(0) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val size = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L }.getOrDefault(-1L)
            if (size > COVER_MAX) toast(ctx, tr("The picture is bigger than 3 MB", "Resim 3 MB'tan büyük"))
            else runCatching {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
                if (bytes.size > COVER_MAX) toast(ctx, tr("The picture is bigger than 3 MB", "Resim 3 MB'tan büyük"))
                else { coverFile(ctx, g.pkg).parentFile?.mkdirs(); coverFile(ctx, g.pkg).writeBytes(bytes); coverTick++ }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        key(coverTick) { GameIcon(g.pkg, pal) }
        Spacer(Modifier.height(8.dp))
        Text(g.name, color = pal.text, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(tr("Played: ", "Oynanan: ") + (if (time > 0) hm(time) else tr("no data", "veri yok")), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(false, tr("Change cover", "Kapağı değiştir")) { pick.launch("image/*") }
            PChip(false, tr("Remove cover", "Kapağı kaldır")) { coverFile(ctx, g.pkg).delete(); coverTick++ }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Hidden from every launcher", "Her başlatıcıdan gizli"), color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PSwitch(hidden, onHidden, enabled = can)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Restrict in background", "Arka planda kısıtla"), color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PSwitch(restrict, { want -> if (setRestricted(ctx, g.pkg, want)) restrict = want }, enabled = can)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(pal.accent.copy(alpha = .2f))
                .clickable(enabled = can) { freeMemory(ctx) }.padding(12.dp),
            horizontalArrangement = Arrangement.Center,
        ) { Text(tr("Free memory now", "Şimdi bellek boşalt"), color = pal.text, fontSize = 14.sp) }
    }
}

/** Every app that opens (launchable), with a switch that adds it to the library or takes it out again. */
@Composable
private fun AddGamesDialog(pal: Pal, onClose: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<GameRow>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            runCatching {
                pm.getInstalledApplications(0)
                    .filter { it.packageName != ctx.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { GameRow(it.packageName, pm.getApplicationLabel(it).toString()) }
                    .sortedBy { it.name.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    var query by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(customSet(ctx)) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(18.dp)).background(pal.card).padding(14.dp)) {
            Text(tr("Add to the library", "Kütüphaneye ekle"), color = pal.text, fontSize = 17.sp)
            com.umutk.pulse.PField(query, { query = it }, tr("Search apps", "Uygulama ara"), Modifier.fillMaxWidth().padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(apps.filter { it.name.contains(query, true) }, key = { it.pkg }) { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(a.pkg, pal)
                        Spacer(Modifier.width(10.dp))
                        Text(a.name, color = pal.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PSwitch(a.pkg in added, { want ->
                            val next = if (want) customSet(ctx) + a.pkg else customSet(ctx) - a.pkg
                            saveCustom(ctx, next); added = next; onChanged()
                        })
                    }
                }
            }
        }
    }
}

/** Game Hub: the library (a grid; wide screens show a game's panel beside it), the home page with the play time, and the settings. */
@Composable
fun GameHubHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(prefs(ctx).getString("theme", "amoled") ?: "amoled") }
    val pal = palOf(theme)
    var tab by remember { mutableStateOf(prefs(ctx).getInt("start", 0)) }
    var games by remember { mutableStateOf(emptyList<GameRow>()) }
    var hidden by remember { mutableStateOf(hiddenSet(ctx)) }
    var mode by remember { mutableStateOf(Privilege.mode) }
    var selected by remember { mutableStateOf<String?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    // a settings change redraws the page
    var gridTick by remember { mutableStateOf(0) }
    // read here, so that a change of the settings redraws the library
    if (gridTick < 0) return
    var times by remember { mutableStateOf(emptyMap<String, Long>()) }
    fun recheck() { Privilege.detect(); mode = Privilege.mode }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Privilege.detect() }
        mode = Privilege.mode
        withContext(Dispatchers.IO) { readHiddenInfo(ctx) }
        games = withContext(Dispatchers.IO) { loadGames(ctx) }
        if (mode != Privilege.Mode.None) withContext(Dispatchers.IO) { grantUsageAccess(ctx) }
        times = withContext(Dispatchers.IO) { playTimes(ctx, 7) }
    }
    val can = mode != Privilege.Mode.None
    fun setHidden(pkg: String, want: Boolean) { if (setGameHidden(ctx, pkg, want)) hidden = hiddenSet(ctx) }
    val chosen = games.firstOrNull { it.pkg == selected }
    Column(Modifier.fillMaxSize().background(pal.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", pal.text, 22.dp) }
            Text("Game Hub", color = pal.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(tab == 0, tr("Library", "Kütüphane")) { tab = 0 }
            PChip(tab == 1, tr("Home", "Ana sayfa")) { tab = 1 }
            PChip(tab == 2, tr("Settings", "Ayarlar")) { tab = 2 }
        }
        when (tab) {
            0 -> BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp)) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { addOpen = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = pal.accent,
                    contentColor = pal.bg,
                ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Add, tr("Add", "Ekle")) }
                val wide = maxWidth > maxHeight
                if (!can) Text(tr("Hiding and profiles need root or Shizuku.", "Gizleme ve profiller root ya da Shizuku ister."), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                Row(Modifier.fillMaxSize()) {
                    val grid: @Composable () -> Unit = {
                        if (games.isEmpty()) Text(tr("No games found on this phone.", "Bu telefonda oyun bulunamadı."), color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                        else LazyVerticalGrid(GridCells.Adaptive(gridDp(ctx).dp), Modifier.fillMaxSize().padding(horizontal = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                            items(games.filter { showHiddenInLibrary(ctx) || it.pkg !in hidden }, key = { it.pkg }) { g ->
                                GameTile(g, pal, selected == g.pkg, g.pkg in hidden) {
                                    selected = g.pkg
                                    if (!wide) profileOpen = true
                                }
                            }
                        }
                    }
                    if (wide) {
                        Box(Modifier.weight(1.6f).fillMaxHeight()) { grid() }
                        Box(Modifier.weight(1f).fillMaxHeight().padding(8.dp).clip(RoundedCornerShape(16.dp)).background(pal.card), contentAlignment = Alignment.TopCenter) {
                            if (chosen != null) GamePanel(chosen, pal, can, chosen.pkg in hidden, times[chosen.pkg] ?: 0L) { setHidden(chosen.pkg, it) }
                            else Text(tr("Pick a game", "Bir oyun seç"), color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                        }
                    } else Box(Modifier.fillMaxSize()) { grid() }
                }
            }
            1 -> Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
                val week = times.filterKeys { k -> games.any { it.pkg == k } }
                val total = week.values.sum()
                Text(tr("This week", "Bu hafta"), color = pal.sub, fontSize = 13.sp)
                Text(if (total > 0) hm(total) else tr("No play yet", "Henüz oynanmadı"), color = pal.text, fontSize = 30.sp)
                Text(tr("${games.size} games · ${hidden.size} hidden", "${games.size} oyun · ${hidden.size} gizli"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
                val top = games.map { it to (week[it.pkg] ?: 0L) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(7)
                if (top.isNotEmpty()) {
                    val max = top.first().second.toFloat()
                    Canvas(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)).background(pal.card)) {
                        val bw = size.width / (top.size * 1.6f)
                        top.forEachIndexed { i, (_, t) ->
                            val h = size.height * 0.85f * (t / max)
                            drawRect(pal.accent, topLeft = androidx.compose.ui.geometry.Offset(i * bw * 1.6f + bw * 0.3f, size.height - h - 10f), size = androidx.compose.ui.geometry.Size(bw, h))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    top.forEach { (g, t) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(g.pkg, pal)
                            Spacer(Modifier.width(10.dp))
                            Text(g.name, color = pal.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(hm(t), color = pal.sub, fontSize = 13.sp)
                        }
                    }
                } else Text(tr("Play a game and its time shows here.", "Bir oyun oynayın, süresi burada görünür."), color = pal.sub, fontSize = 13.sp)
            }
            else -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                Text(tr("Look", "Görünüm"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("amoled" to "Amoled", "material" to "Material", "windows" to "Windows").forEach { (k, label) ->
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
                Text(tr("Library", "Kütüphane"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                Text(tr("Tile size", "Kart boyutu"), color = pal.text, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to tr("Small", "Küçük"), 1 to tr("Medium", "Orta"), 2 to tr("Large", "Büyük")).forEach { (k, label) ->
                        PChip(prefs(ctx).getInt("grid", 1) == k, label) { prefs(ctx).edit().putInt("grid", k).apply(); gridTick++ }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Show hidden games in the library", "Gizli oyunları kütüphanede göster"), color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    PSwitch(showHiddenInLibrary(ctx), { on -> prefs(ctx).edit().putBoolean("showHidden", on).apply(); gridTick++ })
                }
                Text(tr("Opens on", "Açılış sayfası"), color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to tr("Library", "Kütüphane"), 1 to tr("Home", "Ana sayfa")).forEach { (k, label) ->
                        PChip(prefs(ctx).getInt("start", 0) == k, label) { prefs(ctx).edit().putInt("start", k).apply(); gridTick++ }
                    }
                }
                Text(tr("A cover picture can be set on each game (at most 3 MB).", "Her oyuna kapak resmi konabilir (en fazla 3 MB)."), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
    if (addOpen) AddGamesDialog(pal, onClose = { addOpen = false }, onChanged = { games = loadGames(ctx) })
    // a tall screen: a game's panel opens in a dialog
    if (profileOpen && chosen != null) Dialog(onDismissRequest = { profileOpen = false }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card)) {
            GamePanel(chosen, pal, can, chosen.pkg in hidden, times[chosen.pkg] ?: 0L) { setHidden(chosen.pkg, it) }
        }
    }
}
