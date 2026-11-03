package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.Dp
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

/** A look of Game Hub: its colours, its corner radius, and whether it is flat (no gradients, as the Windows looks are). */
private class Pal(val bg: Color, val card: Color, val text: Color, val sub: Color, val accent: Color, val radius: Dp = 14.dp, val flat: Boolean = false)

/** The looks of Game Hub, in the order they are offered. */
private val THEMES = listOf("amoled", "material", "win95", "winxp", "win8", "win10", "win11")

private fun themeName(k: String): String = when (k) {
    "material" -> "Material"
    "win95" -> "Windows 95"
    "winxp" -> "Windows XP"
    "win8" -> "Windows 8"
    "win10" -> "Windows 10"
    "win11" -> "Windows 11"
    else -> "Amoled"
}

/** A colour of the system theme (Android 12 and later: the wallpaper colours), or the fallback on older phones. */
private fun systemColor(c: Context, res: Int, fallback: Color): Color =
    if (Build.VERSION.SDK_INT >= 31) runCatching { Color(c.getColor(res)) }.getOrDefault(fallback) else fallback

private fun palOf(theme: String, c: Context): Pal = when (theme) {
    // Material follows the phone's own colours (the wallpaper palette) for the background, the cards and the accent
    "material" -> Pal(
        systemColor(c, android.R.color.system_neutral1_900, Color(0xFF1C1B1F)),
        systemColor(c, android.R.color.system_neutral1_800, Color(0xFF2B2930)),
        systemColor(c, android.R.color.system_neutral1_50, Color(0xFFE6E1E5)),
        systemColor(c, android.R.color.system_neutral2_200, Color(0xFFCAC4D0)),
        systemColor(c, android.R.color.system_accent1_200, Color(0xFFD0BCFF)),
        20.dp,
    )
    "win95" -> Pal(Color(0xFF008080), Color(0xFFC0C0C0), Color.Black, Color(0xFF404040), Color(0xFF000080), 0.dp, true)
    "winxp" -> Pal(Color(0xFFECE9D8), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF444444), Color(0xFF316AC5), 6.dp, true)
    "win8" -> Pal(Color(0xFF000000), Color(0xFF1F1F1F), Color.White, Color(0xFFB0B0B0), Color(0xFF1BA1E2), 0.dp, true)
    "win10" -> Pal(Color(0xFFF0F0F0), Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0xFF5A5A5A), Color(0xFF0078D4), 4.dp, true)
    "win11" -> Pal(Color(0xFFF3F3F3), Color(0xCCFFFFFF), Color(0xFF1A1A1A), Color(0xFF5A5A5A), Color(0xFF0067C0), 8.dp, true)
    else -> Pal(Color.Black, Color(0xFF111111), Color.White, Color(0xFF9A9A9A), Color(0xFF00E676))
}

private class GameRow(val pkg: String, val name: String)

private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
private fun hiddenSet(c: Context): Set<String> = prefs(c).getStringSet("hidden", emptySet()) ?: emptySet()
private fun saveHidden(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("hidden", s).apply() }
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

/** Whether the system still shows the app to Pulse (a hidden app cannot be found by its package name). */
private fun visibleTo(c: Context, pkg: String): Boolean = runCatching { c.packageManager.getApplicationInfo(pkg, 0) }.isSuccess

/** Games switched on in the old add list (before a switch meant hiding) are hidden now, once: each one is hidden and leaves that list. */
private fun migrateAdded(c: Context) {
    if (!Privilege.mode.let { it == Privilege.Mode.Root || it == Privilege.Mode.Shizuku }) return
    val left = customSet(c).filter { pkg -> !setGameHidden(c, pkg, true, quiet = true) }.toSet()
    if (left != customSet(c)) saveCustom(c, left)
}

/** The hidden list must match the system: a game listed as hidden that the system still shows is hidden again, and one that
 *  stays visible leaves the list, so the switch never says "hidden" for a game that is on the screen. Needs root or Shizuku. */
private fun reconcileHidden(c: Context) {
    if (!Privilege.mode.let { it == Privilege.Mode.Root || it == Privilege.Mode.Shizuku }) return
    val real = hiddenSet(c).filter { pkg ->
        if (!visibleTo(c, pkg)) true
        else { Privilege.run("pm hide $pkg"); !visibleTo(c, pkg) }
    }.toSet()
    if (real != hiddenSet(c)) saveHidden(c, real)
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
private fun setGameHidden(c: Context, pkg: String, hide: Boolean, quiet: Boolean = false): Boolean {
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
    // success: the shell ran and printed no error (the wording of pm differs between Android versions)
    val ok = out.code >= 0 && !Regex("(?i)error|exception|failure|unknown|denied").containsMatchIn(out.text)
    if (ok) saveHidden(c, if (hide) hiddenSet(c) + pkg else hiddenSet(c) - pkg)
    else if (!quiet) toast(c, tr("Not changed: ", "Değişmedi: ") + (out.text.ifBlank { tr("no answer from root or Shizuku", "root ya da Shizuku yanıt vermedi") }).take(140))
    return ok
}

/** Frees the memory the background apps hold (the cleanup of a game boost). */
private fun freeMemory(c: Context) {
    val out = Privilege.run("am kill-all")
    toast(c, if (out.code >= 0) tr("Background apps closed", "Arka plan uygulamaları kapatıldı") else tr("Needs root or Shizuku", "Root ya da Shizuku gerekir"))
}

/** Opens the game itself (its launch activity). */
private fun openGame(c: Context, pkg: String) {
    val pm = c.packageManager
    // the launch activity; when the system gives none, the launcher activity of the package; else the root or Shizuku shell starts it
    val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(pkg)
    val activity = pm.queryIntentActivities(launcher, 0).firstOrNull()?.activityInfo?.name
    val intent = pm.getLaunchIntentForPackage(pkg)
        ?: activity?.let { android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setClassName(pkg, it) }
    val started = intent != null && runCatching {
        c.startActivity(android.content.Intent(intent).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
    if (started) return
    val out = Privilege.run("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
    val ok = out.code >= 0 && !Regex("(?i)aborted|error|no activities|exception").containsMatchIn(out.text)
    if (ok) return
    // a hidden game: shown for the start only (pm unhide), and hidden again when Game Hub comes back to the screen
    if (pkg in hiddenSet(c)) {
        Privilege.run("pm unhide $pkg")
        val again = pm.getLaunchIntentForPackage(pkg)
        if (again != null && runCatching { c.startActivity(android.content.Intent(again).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
            val pending = prefs(c).getStringSet("pendingHide", emptySet()) ?: emptySet()
            prefs(c).edit().putStringSet("pendingHide", pending + pkg).apply()
            return
        }
        Privilege.run("pm hide $pkg")
    }
    toast(c, tr("This app cannot be opened from here", "Bu uygulama buradan açılamıyor") + if (out.text.isBlank()) "" else ": " + out.text.take(100))
}

/** Hides again the hidden games that were shown for their start; the ones that still cannot be hidden stay in the list. */
private fun rehideLaunched(c: Context) {
    val pending = prefs(c).getStringSet("pendingHide", emptySet()) ?: emptySet()
    if (pending.isEmpty()) return
    val left = pending.filter { pkg -> !setGameHidden(c, pkg, true, quiet = true) }.toSet()
    prefs(c).edit().putStringSet("pendingHide", left).apply()
}

private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

/** Decoded pictures of the games, kept in memory: each picture is decoded once, at the size it is drawn (no decode on every scroll or return). */
private object GameArt {
    private val cache = android.util.LruCache<String, ImageBitmap>(120)

    private fun cached(key: String, make: () -> ImageBitmap?): ImageBitmap? {
        synchronized(cache) { cache.get(key)?.let { return it } }
        val made = runCatching { make() }.getOrNull() ?: return null
        synchronized(cache) { cache.put(key, made) }
        return made
    }

    private fun decodeFile(path: String, px: Int): ImageBitmap? {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, o)
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit((maxOf(o.outWidth, o.outHeight) / px).coerceAtLeast(1))
        }
        return android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }

    /** The picture the person chose (at most 3 MB), decoded at [px] wide. */
    fun cover(c: Context, pkg: String, px: Int): ImageBitmap? {
        val f = coverFile(c, pkg).takeIf { it.isFile } ?: return null
        return cached("c/$pkg/${f.lastModified()}/$px") { decodeFile(f.path, px) }
    }

    /** The game's own banner from its APK (the art its developer made for the store), when the APK has one. */
    fun banner(c: Context, pkg: String, px: Int): ImageBitmap? = cached("b/$pkg/$px") {
        val pm = c.packageManager
        val d = pm.getApplicationInfo(pkg, 0).loadBanner(pm) ?: return@cached null
        val w = px
        val h = (px * (d.intrinsicHeight.coerceAtLeast(1).toFloat() / d.intrinsicWidth.coerceAtLeast(1))).toInt().coerceAtLeast(1)
        d.toBitmap(w, h).asImageBitmap()
    }

    /** The game's icon from its APK (or the one kept when it was hidden). */
    fun icon(c: Context, pkg: String, px: Int): ImageBitmap? = cached("i/$pkg/$px") {
        val f = iconFile(c, pkg)
        if (f.isFile) decodeFile(f.path, px)
        else c.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
    }
}

/** The game's icon, drawn small; an empty square when the system gives none. */
@Composable
private fun GameIcon(pkg: String, pal: Pal) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { GameArt.icon(ctx, pkg, 96) }
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(pal.card), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** The usage record of each app over the last [days] days: total time in the foreground and the last time it was used. */
private fun usageOf(c: Context, days: Int): Map<String, android.app.usage.UsageStats> {
    if (Build.VERSION.SDK_INT < 22) return emptyMap()
    val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return emptyMap()
    val end = System.currentTimeMillis()
    return runCatching { usm.queryAndAggregateUsageStats(end - days * 86_400_000L, end) }.getOrDefault(emptyMap())
}

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

/**
 * A game's cover, filling its card: the picture the person picked, else the APK's own banner (the art of its store page), else its icon on the card.
 * "APK only" in the settings skips the picked picture.
 */
@Composable
private fun GameCover(pkg: String, pal: Pal, wide: Boolean = true) {
    val ctx = LocalContext.current
    val apkOnly = prefs(ctx).getInt("iconMode", 0) == 1
    val px = if (wide) 960 else 600
    val art by produceState<ImageBitmap?>(null, pkg, apkOnly, wide) {
        value = withContext(Dispatchers.IO) { (if (apkOnly) null else GameArt.cover(ctx, pkg, px)) ?: GameArt.banner(ctx, pkg, px) }
    }
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { GameArt.icon(ctx, pkg, 192) }
    }
    val bg = if (pal.flat) Modifier.background(pal.card) else Modifier.background(Brush.verticalGradient(listOf(pal.card, pal.bg)))
    Box(Modifier.fillMaxSize().then(bg), contentAlignment = Alignment.Center) {
        val a = art
        if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else icon?.let { Image(it, null, Modifier.fillMaxSize(0.5f)) }
    }
}

/** One game as a store card: a tall cover (2:3, like a Steam library), the name under it, a small info line and a mark when it is hidden. */
@Composable
private fun GameTile(g: GameRow, pal: Pal, hidden: Boolean, info: String?, onClick: () -> Unit) {
    Column(Modifier.padding(6.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.Start) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(pal.radius))) { GameCover(g.pkg, pal, wide = false) }
        // the info line always takes its place, so a tile keeps its height when the sort changes
        Box(Modifier.padding(top = 4.dp).height(16.dp)) { if (info != null) Text(info, color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        Text(g.name, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 17.sp)
        if (hidden) Text(tr("hidden", "gizli"), color = pal.sub, fontSize = 11.sp)
    }
}

/** A game's page, like a store page: the banner with the cover, the name, a big Play button, the play time and the last played day, and the settings of the game. */
@Composable
private fun GamePanel(g: GameRow, pal: Pal, can: Boolean, total: Long, lastUsed: Long, onHidden: (Boolean) -> Unit) {
    val ctx = LocalContext.current
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
    Column(Modifier.fillMaxWidth()) {
        // the banner: the cover across the top, the icon and the name over its bottom edge
        key(coverTick) {
            Box(Modifier.fillMaxWidth().height(230.dp).background(pal.bg)) {
                GameCover(g.pkg, pal)
                if (!pal.flat) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, pal.bg))))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-30).dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(pal.card), contentAlignment = Alignment.Center) { GameIcon(g.pkg, pal) }
            Spacer(Modifier.width(12.dp))
            Text(g.name, color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-18).dp)) {
            if (!can) Text(tr("Root or Shizuku is not connected: the hide and background switches are off. Press Check again in Settings.", "Root ya da Shizuku bağlı değil: gizleme ve arka plan anahtarları kapalı. Ayarlar'da Yeniden kontrol et'e bas."), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            // the big Play button
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.accent).clickable { openGame(ctx, g.pkg) }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluentIcon("play", pal.bg, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Play", "Oyna"), color = pal.bg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            // the facts: total time, last played
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(pal, Modifier.weight(1f), tr("Total played", "Toplam oynama"), if (total > 0) hm(total) else "-")
                StatCard(pal, Modifier.weight(1f), tr("Last played", "Son oynama"), if (lastUsed > 0) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(lastUsed)) else "-")
            }
            SectionTitle(pal, tr("Cover", "Kapak"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(pal, tr("Change cover", "Kapağı değiştir")) { pick.launch("image/*") }
                Chip(pal, tr("Remove cover", "Kapağı kaldır")) { coverFile(ctx, g.pkg).delete(); coverTick++ }
            }
            // achievements and community need a service the phone's games do not have: said here, not faked
            SectionTitle(pal, tr("Achievements", "Başarımlar"))
            Text(tr("This game does not report achievements to Pulse.", "Bu oyun başarımlarını Pulse'a bildirmiyor."), color = pal.sub, fontSize = 13.sp)
            SectionTitle(pal, tr("Community", "Topluluk"))
            Text(tr("No community for an Android app here.", "Bir Android uygulaması için burada topluluk yok."), color = pal.sub, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
        }
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

/** The apps that open, and the hidden ones: a switch on an app hides it at once (it leaves every launcher and moves to the hidden list at the top), a switch on a hidden app shows it again. One scrolling list, so the hidden list never runs off the dialog. */
@Composable
private fun AddGamesDialog(pal: Pal, onClose: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var hiddenPkgs by remember { mutableStateOf(hiddenSet(ctx)) }
    val apps by produceState<List<GameRow>>(emptyList(), tick) {
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
    var hiddenOpen by remember { mutableStateOf(true) }
    val hiddenList = hiddenPkgs.map { GameRow(it, prefs(ctx).getString("name_$it", null) ?: it) }.sortedBy { it.name.lowercase() }
    val visible = apps.filter { it.pkg !in hiddenPkgs && it.name.contains(query, true) }
    fun changed() { hiddenPkgs = hiddenSet(ctx); tick++; onChanged() }
    Dialog(onDismissRequest = onClose) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(20.dp)).background(pal.card).padding(16.dp)) {
            item { Text(tr("Add to the library", "Kütüphaneye ekle"), color = pal.text, fontSize = 18.sp) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(pal.bg).clickable { hiddenOpen = !hiddenOpen }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Hidden", "Gizli") + " (" + hiddenList.size + ")", color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FluentIcon(if (hiddenOpen) "up" else "down", pal.sub, 16.dp)
                }
            }
            if (hiddenOpen) items(hiddenList, key = { "h_" + it.pkg }) { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(h.pkg, pal)
                    Spacer(Modifier.width(10.dp))
                    Text(h.name, color = pal.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    PSwitch(true, { want -> if (!want && setGameHidden(ctx, h.pkg, false)) changed() })
                }
            }
            item { PField(query, { query = it }, tr("Search apps", "Uygulama ara"), Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            items(visible, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(a.pkg, pal)
                    Spacer(Modifier.width(10.dp))
                    Text(a.name, color = pal.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    PSwitch(false, { want -> if (want && setGameHidden(ctx, a.pkg, true)) changed() })
                }
            }
        }
    }
}

private val PAGE_TITLES = listOf("Home", "Library", "Settings")

/** What Game Hub showed last time, kept while the app runs: the screen opens with it, then it is refreshed. */
private object GameHubState {
    var checked = false
    var games: List<GameRow>? = null
    var times: Map<String, Long>? = null
    var usage: Map<String, android.app.usage.UsageStats>? = null
    var sizes: Map<String, Long>? = null
}

/**
 * Game Hub: a side menu (Home, Library with its filters, Settings), the home page with the week's play time, the library as a
 * grid of covers with a round add button, and the settings.
 */
@Composable
fun GameHubHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var theme by remember { mutableStateOf(prefs(ctx).getString("theme", "material") ?: "material") }
    var themeOpen by remember { mutableStateOf(false) }
    val pal = palOf(theme, ctx)
    var page by remember { mutableStateOf(prefs(ctx).getInt("start", 1)) }   // 0 home, 1 library, 2 settings
    var filter by remember { mutableStateOf(0) }                              // library: 0 all, 1 installed, 2 hidden
    var games by remember { mutableStateOf(GameHubState.games ?: emptyList()) }
    var hidden by remember { mutableStateOf(hiddenSet(ctx)) }
    var mode by remember { mutableStateOf(Privilege.mode) }
    var profile by remember { mutableStateOf<GameRow?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var times by remember { mutableStateOf(GameHubState.times ?: emptyMap()) }
    var usage by remember { mutableStateOf(GameHubState.usage ?: emptyMap()) }
    var sort by remember { mutableStateOf(prefs(ctx).getInt("sort", 0)) }   // 0 name, 1 play time, 2 size
    var sizes by remember { mutableStateOf(GameHubState.sizes ?: emptyMap()) }
    var tileSize by remember { mutableStateOf(prefs(ctx).getInt("grid", 1)) }
    var showHidden by remember { mutableStateOf(showHiddenInLibrary(ctx)) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        // the lists of the last visit are on the screen at once; the shell checks run once per start of the app, not on every return
        if (!GameHubState.checked) {
            withContext(Dispatchers.IO) {
                Privilege.detect()
                readHiddenInfo(ctx); migrateAdded(ctx); reconcileHidden(ctx)
                if (Privilege.mode != Privilege.Mode.None) grantUsageAccess(ctx)
            }
            GameHubState.checked = true
        }
        mode = Privilege.mode
        val g = withContext(Dispatchers.IO) { loadGames(ctx) }
        games = g; GameHubState.games = g
        hidden = hiddenSet(ctx)
        val t = withContext(Dispatchers.IO) { playTimes(ctx, 7) }
        times = t; GameHubState.times = t
        val u = withContext(Dispatchers.IO) { usageOf(ctx, 3650) }
        usage = u; GameHubState.usage = u
        val z = withContext(Dispatchers.IO) { g.associate { it.pkg to appSize(ctx, it.pkg) } }
        sizes = z; GameHubState.sizes = z
    }
    val can = mode != Privilege.Mode.None
    fun setHidden(pkg: String, want: Boolean) { if (setGameHidden(ctx, pkg, want)) hidden = hiddenSet(ctx) }
    fun go(p: Int) { page = p; prefs(ctx).edit().putInt("start", p).apply(); scope.launch { drawer.close() } }
    fun setTheme(k: String) { theme = k; prefs(ctx).edit().putString("theme", k).apply() }
    // back on the screen: the games shown for their start are hidden again
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch { withContext(Dispatchers.IO) { rehideLaunched(ctx) }; hidden = hiddenSet(ctx) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // the system back: first closes what is open (the game page, the side menu), then goes to the library, and only then leaves Game Hub
    BackHandler(enabled = profile != null || drawer.isOpen || page != 1) {
        when {
            profile != null -> profile = null
            drawer.isOpen -> scope.launch { drawer.close() }
            else -> page = 1
        }
    }

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
                    // a new order starts at the top, so the list does not jump to a row that moved
                    LaunchedEffect(sort, filter) { gridState.scrollToItem(0) }
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
                        state = gridState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 56.dp, bottom = 90.dp),
                    ) {
                        items(sortGames(shown, sort, usage, sizes), key = { it.pkg }) { g -> GameTile(g, pal, g.pkg in hidden, infoOf(g, sort, usage, sizes)) { profile = g } }
                    }
                    // the sort row, like the library bar of a store: name, play time, size
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to tr("Name", "Ad"), 1 to tr("Play time", "Süre"), 2 to tr("Size", "Boyut")).forEach { (k, label) ->
                            Chip(pal, if (sort == k) "● $label" else label) { sort = k; prefs(ctx).edit().putInt("sort", k).apply() }
                        }
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
                    ValueRow(pal, tr("Look", "Görünüm"), themeName(theme)) { themeOpen = true }
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
                    SectionTitle(pal, tr("Icons", "Simgeler"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to tr("Cover, else APK icon", "Kapak, yoksa APK simgesi"), 1 to tr("APK icon only", "Yalnız APK simgesi")).forEach { (k, label) ->
                            Chip(pal, if (prefs(ctx).getInt("iconMode", 0) == k) "● $label" else label) { prefs(ctx).edit().putInt("iconMode", k).apply(); page = page }
                        }
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
    if (themeOpen) ThemeDialog(theme, ctx, onPick = { k -> setTheme(k); themeOpen = false }, onClose = { themeOpen = false })
    if (addOpen) AddGamesDialog(pal, onClose = { addOpen = false }, onChanged = { games = loadGames(ctx) })
    // a game opens as its own page: the back arrow at the top left returns to the library
    profile?.let { g ->
        Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { profile = null }, contentAlignment = Alignment.Center) { FluentIcon("back", pal.text, 22.dp) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val u = usage[g.pkg]
                GamePanel(g, pal, can, u?.totalTimeInForeground ?: 0L, u?.lastTimeUsed ?: 0L) { want -> setHidden(g.pkg, want) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun gridDpOf(size: Int): Int = when (size) { 0 -> 150; 2 -> 240; else -> 190 }

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

/** The size of an app's APK in bytes (0 when it cannot be read). */
private fun appSize(c: Context, pkg: String): Long = runCatching { java.io.File(c.packageManager.getApplicationInfo(pkg, 0).sourceDir).length() }.getOrDefault(0L)

/** The library in the chosen order: name, the play time of the last years, or the size of the app. */
private fun sortGames(list: List<GameRow>, sort: Int, usage: Map<String, android.app.usage.UsageStats>, sizes: Map<String, Long>): List<GameRow> = when (sort) {
    1 -> list.sortedByDescending { usage[it.pkg]?.totalTimeInForeground ?: 0L }
    2 -> list.sortedByDescending { sizes[it.pkg] ?: 0L }
    else -> list
}
