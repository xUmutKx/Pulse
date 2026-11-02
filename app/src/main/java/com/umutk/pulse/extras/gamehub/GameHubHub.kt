package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.FluentFilled
import com.umutk.pulse.FluentPaths
import com.umutk.pulse.PField
import com.umutk.pulse.PSwitch
import com.umutk.pulse.extras.blackout.Privilege
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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


/** A Fluent glyph for the Game Hub's own pages: the same vector paths the rest of Pulse uses (Windows 11 looks). */
@Composable
private fun FluentIcon(key: String, tint: Color, size: androidx.compose.ui.unit.Dp) {
    val paths = remember(key) { ((FluentPaths.map[key] ?: FluentPaths.map["info"]!!)).map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } }
    }
}

/** One game as a card: its cover (or icon) and name, and a mark when it is hidden. */
@Composable
private fun GameTile(g: GameRow, pal: Pal, hidden: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp)).background(pal.card).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.bg), contentAlignment = Alignment.Center) {
            GameIcon(g.pkg, pal)
        }
        Spacer(Modifier.height(6.dp))
        Text(g.name, color = pal.text, fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
        if (hidden) Text(tr("hidden", "gizli"), color = pal.sub, fontSize = 10.sp)
    }
}

/** A game's page: cover, play time, cover picture, hide and background switches, memory clean-up. */
@Composable
private fun GamePanel(g: GameRow, pal: Pal, can: Boolean, hidden: Boolean, time: Long, onHidden: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var restrict by remember(g.pkg) { mutableStateOf(restrictOn(ctx, g.pkg)) }
    var coverTick by remember(g.pkg) { mutableStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        key(coverTick) {
            Box(Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(16.dp)).background(pal.bg), contentAlignment = Alignment.Center) { GameIcon(g.pkg, pal) }
        }
        Spacer(Modifier.height(10.dp))
        Text(g.name, color = pal.text, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text(tr("Played this week: ", "Bu hafta: ") + (if (time > 0) hm(time) else tr("no data", "veri yok")), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(pal, tr("Change cover", "Kapağı değiştir")) { pick.launch("image/*") }
            Chip(pal, tr("Remove cover", "Kapağı kaldır")) { coverFile(ctx, g.pkg).delete(); coverTick++ }
        }
        SettingRow(pal, tr("Hidden from every launcher", "Her başlatıcıdan gizli"), hidden, can, onHidden)
        SettingRow(pal, tr("Restrict in background", "Arka planda kısıtla"), restrict, can) { want -> if (setRestricted(ctx, g.pkg, want)) restrict = want }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(pal.accent.copy(alpha = .2f))
                .clickable(enabled = can) { freeMemory(ctx) }.padding(12.dp),
            horizontalArrangement = Arrangement.Center,
        ) { Text(tr("Free memory now", "Şimdi bellek boşalt"), color = pal.text, fontSize = 14.sp) }
    }
}

/** A row with a label and a switch. */
@Composable
private fun SettingRow(pal: Pal, label: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        PSwitch(on, onChange, enabled = enabled)
    }
}

/** A small pill button. */
@Composable
private fun Chip(pal: Pal, label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(pal.card).border(1.dp, pal.sub.copy(alpha = .35f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = pal.text, fontSize = 13.sp)
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
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(20.dp)).background(pal.card).padding(16.dp)) {
            Text(tr("Add to the library", "Kütüphaneye ekle"), color = pal.text, fontSize = 18.sp)
            PField(query, { query = it }, tr("Search apps", "Uygulama ara"), Modifier.fillMaxWidth().padding(vertical = 8.dp))
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

private val PAGE_TITLES = listOf("Home", "Library", "Settings")

/**
 * Game Hub: a side menu (Home, Library with its filters, Settings), the home page with the week's play time, the library as a
 * grid of covers with a round add button, and the settings.
 */
@Composable
fun GameHubHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var theme by remember { mutableStateOf(prefs(ctx).getString("theme", "amoled") ?: "amoled") }
    val pal = palOf(theme)
    var page by remember { mutableStateOf(prefs(ctx).getInt("start", 1)) }   // 0 home, 1 library, 2 settings
    var filter by remember { mutableStateOf(0) }                              // library: 0 all, 1 installed, 2 hidden
    var games by remember { mutableStateOf(emptyList<GameRow>()) }
    var hidden by remember { mutableStateOf(hiddenSet(ctx)) }
    var mode by remember { mutableStateOf(Privilege.mode) }
    var profile by remember { mutableStateOf<GameRow?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var times by remember { mutableStateOf(emptyMap<String, Long>()) }
    var tileSize by remember { mutableStateOf(prefs(ctx).getInt("grid", 1)) }
    var showHidden by remember { mutableStateOf(showHiddenInLibrary(ctx)) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Privilege.detect() }
        mode = Privilege.mode
        withContext(Dispatchers.IO) { readHiddenInfo(ctx) }
        games = withContext(Dispatchers.IO) { loadGames(ctx) }
        hidden = hiddenSet(ctx)
        if (mode != Privilege.Mode.None) withContext(Dispatchers.IO) { grantUsageAccess(ctx) }
        times = withContext(Dispatchers.IO) { playTimes(ctx, 7) }
    }
    val can = mode != Privilege.Mode.None
    fun setHidden(pkg: String, want: Boolean) { if (setGameHidden(ctx, pkg, want)) hidden = hiddenSet(ctx) }
    fun go(p: Int) { page = p; prefs(ctx).edit().putInt("start", p).apply(); scope.launch { drawer.close() } }
    fun setTheme(k: String) { theme = k; prefs(ctx).edit().putString("theme", k).apply() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = pal.card, drawerContentColor = pal.text) {
                Text("Game Hub", color = pal.text, fontSize = 20.sp, modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp))
                NavigationDrawerItem(label = { Text(tr("Home", "Ana sayfa")) }, selected = page == 0, onClick = { go(0) },
                    icon = { FluentIcon("home", pal.text, 22.dp) }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text(tr("All games", "Tüm oyunlar")) }, selected = page == 1 && filter == 0,
                    onClick = { filter = 0; go(1) }, icon = { FluentIcon("play", pal.text, 22.dp) }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text(tr("Installed", "Yüklü")) }, selected = page == 1 && filter == 1,
                    onClick = { filter = 1; go(1) }, icon = { FluentIcon("download", pal.text, 22.dp) }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text(tr("Hidden", "Gizli")) }, selected = page == 1 && filter == 2,
                    onClick = { filter = 2; go(1) }, icon = { FluentIcon("eyeOff", pal.text, 22.dp) }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text(tr("Settings", "Ayarlar")) }, selected = page == 2, onClick = { go(2) },
                    icon = { FluentIcon("settings", pal.text, 22.dp) }, modifier = Modifier.padding(horizontal = 12.dp))
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(pal.bg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { scope.launch { drawer.open() } }, contentAlignment = Alignment.Center) { FluentIcon("menu", pal.text, 24.dp) }
                Text(
                    when (page) { 0 -> tr("Home", "Ana sayfa"); 2 -> tr("Settings", "Ayarlar"); else -> when (filter) { 1 -> tr("Installed", "Yüklü"); 2 -> tr("Hidden", "Gizli"); else -> tr("Library", "Kütüphane") } },
                    color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Box(Modifier.size(44.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { FluentIcon("close", pal.text, 22.dp) }
            }
            when (page) {
                0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    val week = times.filterKeys { k -> games.any { it.pkg == k } }
                    val total = week.values.sum()
                    Text(tr("Welcome to Game Hub", "Game Hub'a hoş geldin"), color = pal.text, fontSize = 26.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(tr("Your games, in one place", "Oyunların, tek yerde"), color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(pal, Modifier.weight(1f), tr("Games", "Oyun"), games.size.toString())
                        StatCard(pal, Modifier.weight(1f), tr("Hidden", "Gizli"), hidden.size.toString())
                        StatCard(pal, Modifier.weight(1f), tr("This week", "Bu hafta"), if (total > 0) hm(total) else "-")
                    }
                    val top = games.map { it to (week[it.pkg] ?: 0L) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(7)
                    Text(tr("Most played this week", "Bu hafta en çok oynanan"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    if (top.isNotEmpty()) {
                        val max = top.first().second.toFloat()
                        Canvas(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(pal.card)) {
                            val bw = size.width / (top.size * 1.6f)
                            top.forEachIndexed { i, (_, t) ->
                                val h = size.height * 0.8f * (t / max)
                                drawRect(pal.accent, topLeft = Offset(i * bw * 1.6f + bw * 0.3f, size.height - h - 12f), size = Size(bw, h))
                            }
                        }
                        top.forEach { (g, t) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(pal.card), contentAlignment = Alignment.Center) { GameIcon(g.pkg, pal) }
                                Spacer(Modifier.width(12.dp))
                                Text(g.name, color = pal.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(hm(t), color = pal.sub, fontSize = 13.sp)
                            }
                        }
                    } else Text(tr("Play a game and its time shows here.", "Bir oyun oynayın, süresi burada görünür."), color = pal.sub, fontSize = 13.sp)
                    if (!can) Text(tr("Hiding, profiles and play time need root or Shizuku.", "Gizleme, profiller ve oynama süresi root ya da Shizuku ister."), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
                    Spacer(Modifier.height(24.dp))
                }
                1 -> Box(Modifier.fillMaxSize()) {
                    val shown = games.filter { g ->
                        when (filter) {
                            1 -> g.pkg !in hidden
                            2 -> g.pkg in hidden
                            else -> showHidden || g.pkg !in hidden
                        }
                    }
                    if (shown.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("No games here yet", "Burada henüz oyun yok"), color = pal.sub, fontSize = 15.sp, modifier = Modifier.padding(top = 40.dp))
                        Text(tr("Tap + to add an app", "Bir uygulama eklemek için + dokun"), color = pal.sub, fontSize = 13.sp)
                    } else LazyVerticalGrid(
                        GridCells.Adaptive(gridDpOf(tileSize).dp),
                        Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp),
                    ) {
                        items(shown, key = { it.pkg }) { g -> GameTile(g, pal, g.pkg in hidden) { profile = g } }
                    }
                    FloatingActionButton(
                        onClick = { addOpen = true },
                        containerColor = pal.accent,
                        contentColor = pal.bg,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    ) { FluentIcon("add", pal.bg, 24.dp) }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    SectionTitle(pal, tr("Look", "Görünüm"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("amoled" to "Amoled", "material" to "Material", "windows" to "Windows").forEach { (k, label) ->
                            Chip(pal, if (theme == k) "● $label" else label) { setTheme(k) }
                        }
                    }
                    SectionTitle(pal, tr("Library", "Kütüphane"))
                    Text(tr("Tile size", "Kart boyutu"), color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to tr("Small", "Küçük"), 1 to tr("Medium", "Orta"), 2 to tr("Large", "Büyük")).forEach { (k, label) ->
                            Chip(pal, if (tileSize == k) "● $label" else label) { tileSize = k; prefs(ctx).edit().putInt("grid", k).apply() }
                        }
                    }
                    SettingRow(pal, tr("Show hidden games in All games", "Tüm oyunlarda gizlileri göster"), showHidden, true) { on ->
                        showHidden = on; prefs(ctx).edit().putBoolean("showHidden", on).apply()
                    }
                    SectionTitle(pal, tr("Start page", "Açılış sayfası"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to tr("Home", "Ana sayfa"), 1 to tr("Library", "Kütüphane")).forEach { (k, label) ->
                            Chip(pal, if (prefs(ctx).getInt("start", 1) == k) "● $label" else label) { prefs(ctx).edit().putInt("start", k).apply(); page = page }
                        }
                    }
                    SectionTitle(pal, tr("Access", "Erişim"))
                    Text(
                        when (mode) {
                            Privilege.Mode.Root -> tr("Root is on", "Root açık")
                            Privilege.Mode.Shizuku -> tr("Shizuku is on", "Shizuku açık")
                            Privilege.Mode.None -> tr("No root and no Shizuku", "Root ve Shizuku yok")
                        },
                        color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Chip(pal, tr("Check again", "Yeniden kontrol et")) {
                        scope.launch { withContext(Dispatchers.IO) { Privilege.detect() }; mode = Privilege.mode }
                    }
                    SectionTitle(pal, tr("Covers", "Kapaklar"))
                    Text(tr("A cover picture can be set on each game from its page (at most 3 MB).", "Her oyunun sayfasından kapak resmi konabilir (en fazla 3 MB)."), color = pal.sub, fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (addOpen) AddGamesDialog(pal, onClose = { addOpen = false }, onChanged = { games = loadGames(ctx) })
    profile?.let { g ->
        Dialog(onDismissRequest = { profile = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.card)) {
                GamePanel(g, pal, can, g.pkg in hidden, times[g.pkg] ?: 0L) { want -> setHidden(g.pkg, want) }
            }
        }
    }
}

private fun gridDpOf(size: Int): Int = when (size) { 0 -> 92; 2 -> 150; else -> 118 }

/** A small number card on the home page. */
@Composable
private fun StatCard(pal: Pal, modifier: Modifier, label: String, value: String) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(pal.card).padding(12.dp)) {
        Text(value, color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = pal.sub, fontSize = 12.sp, maxLines = 1)
    }
}

/** A section heading on the settings page. */
@Composable
private fun SectionTitle(pal: Pal, label: String) {
    Text(label, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
}
