package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.features.BlurBackdrop
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.umutk.pulse.Compat
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "gamehub"

/** A look of Game Hub: its colours, its corner radius, and whether it is flat (no gradients, as the Windows looks are). */
private class Pal(val bg: Color, val card: Color, val text: Color, val sub: Color, val accent: Color, val radius: Dp = 14.dp, val flat: Boolean = false)

/** The looks of Game Hub, in the order they are offered. */
private val THEMES = listOf("shelf", "amoled", "material", "glass", "win95", "winxp", "win8", "win10", "win11")

private fun themeName(k: String): String = when (k) {
    "material" -> "Material"
    "glass" -> tr("Material, transparent", "Material, şeffaf")
    "shelf" -> tr("Shelf (default)", "Raf (varsayılan)")
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
    // the shelf: a dark room, warm wood-brown cards and an amber accent (the look of a game shelf with the box art standing on it)
    "shelf" -> Pal(Color(0xFF0E0B09), Color(0xFF2A211B), Color(0xFFF4E9DC), Color(0xFFA89684), Color(0xFFF2A54A), 14.dp)
    // Material, see-through: a light veil for the ground and frosted cards, over a soft colour wash drawn behind the page
    "glass" -> Pal(Color(0x33000000), Color(0x40FFFFFF), Color.White, Color(0xFFD6D6DC), systemColor(c, android.R.color.system_accent1_200, Color(0xFFD0BCFF)), 20.dp)
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

/** The ground of a page: the look's colour; the transparent look gets a soft wash of its accent under the veil. */
private fun Modifier.hubGround(theme: String, pal: Pal): Modifier =
    if (theme == "glass") background(Brush.linearGradient(listOf(pal.accent.copy(alpha = .55f), Color(0xFF0A0A12), pal.accent.copy(alpha = .3f)))).background(pal.bg) else background(pal.bg)

private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
private fun hiddenSet(c: Context): Set<String> = prefs(c).getStringSet("hidden", emptySet()) ?: emptySet()
private fun saveHidden(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("hidden", s).apply() }
/** Apps the person added to the library by hand (any app, not only the ones the system marks as games). */
private fun gridDp(c: Context): Int = when (prefs(c).getInt("grid", 0)) { 0 -> 84; 2 -> 128; else -> 100 }
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
private fun playCoverFile(c: Context, pkg: String) = java.io.File(c.filesDir, "gamehub/play_" + pkg.replace('.', '_') + ".jpg")
private fun favSet(c: Context): Set<String> = prefs(c).getStringSet("favs", emptySet()) ?: emptySet()
private fun toggleFav(c: Context, pkg: String) {
    prefs(c).edit().putStringSet("favs", if (pkg in favSet(c)) favSet(c) - pkg else favSet(c) + pkg).apply()
    GameHubState.favVersion.intValue++
}

/**
 * The store picture of a game from its Google Play page (its wide feature graphic when the page shows one), kept as the game's cover until the person picks their own.
 * Tried once every two weeks per game; a page that cannot be read (no network, no listing) just leaves the cover as it was.
 */
private fun fetchPlayCover(c: Context, pkg: String): Boolean {
    val now = System.currentTimeMillis()
    if (now - prefs(c).getLong("pt_$pkg", 0L) < 14 * 86_400_000L) return false
    prefs(c).edit().putLong("pt_$pkg", now).apply()
    return runCatching {
        val html = (java.net.URL("https://play.google.com/store/apps/details?id=$pkg&hl=en&gl=US").openConnection() as java.net.HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000; setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            try { if (responseCode != 200) return@runCatching false; inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
        }
        val img = Regex("https://play-lh\\.googleusercontent\\.com/([A-Za-z0-9_\\-]+)=w(\\d+)-h(\\d+)").findAll(html).firstOrNull { it.groupValues[2].toInt() >= it.groupValues[3].toInt() * 3 / 2 && it.groupValues[2].toInt() >= 400 }
            ?: return@runCatching false
        val url = "https://play-lh.googleusercontent.com/" + img.groupValues[1] + "=w1024"
        val bytes = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 12000
            try { if (responseCode != 200) return@runCatching false; inputStream.use { it.readBytes() } } finally { disconnect() }
        }
        if (bytes.size < 4000 || bytes.size > COVER_MAX) return@runCatching false
        playCoverFile(c, pkg).parentFile?.mkdirs(); playCoverFile(c, pkg).writeBytes(bytes)
        true
    }.getOrDefault(false)
}

private fun coverFile(c: Context, pkg: String) = java.io.File(c.filesDir, "gamehub/cover_" + pkg.replace('.', '_') + ".jpg")

/** The icon file of a hidden game (kept, because a hidden app has no icon for the system any more). */
private fun iconFile(c: Context, pkg: String): java.io.File {
    // the icons of other apps are not Pulse's own data: they live in the folder that is private and left out of every backup (noBackupFilesDir)
    val n = "gamehub/icon_" + pkg.replace('.', '_') + ".png"
    val f = java.io.File(c.noBackupFilesDir, n)
    val old = java.io.File(c.filesDir, n)
    // an icon kept by an older version moves over once
    if (!f.exists() && old.exists()) runCatching { f.parentFile?.mkdirs(); old.copyTo(f, overwrite = true); old.delete() }
    return f
}

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

/** A shortcut of the game for the home screen: a tap starts it through [GameLaunch], which shows a hidden game for its start. */
internal fun pinGame(c: Context, pkg: String): Boolean {
    val name = prefs(c).getString("name_$pkg", null) ?: runCatching { c.packageManager.let { it.getApplicationLabel(it.getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg)
    val bmp = runCatching { android.graphics.BitmapFactory.decodeFile(iconFile(c, pkg).path) }.getOrNull()
        ?: runCatching { c.packageManager.getApplicationIcon(pkg).toBitmap(128, 128) }.getOrNull()
    val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(c, "game_$pkg").setShortLabel(name)
        .setIntent(android.content.Intent(c, GameLaunch::class.java).setAction(android.content.Intent.ACTION_VIEW).putExtra("pkg", pkg))
        .also { b -> bmp?.let { b.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(it)) } }.build()
    val ok = androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(c) && androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(c, info, null)
    if (!ok) toast(c, tr("This launcher cannot pin shortcuts", "Bu başlatıcı kısayol sabitleyemiyor"))
    return ok
}

/** Frees the memory the background apps hold (the cleanup of a game boost). */
private fun freeMemory(c: Context) {
    val out = Privilege.run("am kill-all")
    toast(c, if (out.code >= 0) tr("Background apps closed", "Arka plan uygulamaları kapatıldı") else tr("Needs root or Shizuku", "Root ya da Shizuku gerekir"))
}

/** Opens the game itself (its launch activity). */
internal fun openGame(c: Context, pkg: String) {
    runCatching { prepareGame(c, pkg) }
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
            GameRehideService.start(c)
            return
        }
        Privilege.run("pm hide $pkg")
    }
    toast(c, tr("This app cannot be opened from here", "Bu uygulama buradan açılamıyor") + if (out.text.isBlank()) "" else ": " + out.text.take(100))
}

/**
 * Hides a game again once it has closed, without Game Hub having to come back to the screen. A hidden app is stopped by `pm hide`, so it cannot be hidden while it
 * runs: this service looks every few seconds whether the game's process is still there and hides it the moment it is gone. A game that never showed up within
 * 40 seconds (it did not start) is hidden at once; the wait ends after 12 hours.
 */
class GameRehideService : android.app.Service() {
    companion object {
        @Volatile private var running = false
        fun start(c: Context) {
            val i = android.content.Intent(c, GameRehideService::class.java)
            runCatching { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) }
        }
    }

    override fun onBind(i: android.content.Intent?): android.os.IBinder? = null

    override fun onStartCommand(i: android.content.Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel("game_rehide", "Game Hub", android.app.NotificationManager.IMPORTANCE_MIN))
        val n = androidx.core.app.NotificationCompat.Builder(this, "game_rehide").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Game Hub").setContentText(tr("A game is open; its settings go back when it closes", "Bir oyun açık; kapanınca ayarlar eski hâline döner")).setOngoing(true).setSilent(true).build()
        runCatching { if (Build.VERSION.SDK_INT >= 34) startForeground(21, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(21, n) }
        if (running) return START_NOT_STICKY
        running = true
        val ctx = applicationContext
        Thread {
            try {
                if (Privilege.mode == Privilege.Mode.None) runCatching { Privilege.detect() }
                val seen = HashSet<String>()
                val t0 = System.currentTimeMillis()
                while (System.currentTimeMillis() - t0 < 12 * 3600_000L) {
                    val hide = prefs(ctx).getStringSet("pendingHide", emptySet()) ?: emptySet()
                    val prof = prefs(ctx).getStringSet("profileOn", emptySet()) ?: emptySet()
                    if (hide.isEmpty() && prof.isEmpty()) break
                    for (pkg in hide + prof) {
                        val up = Privilege.run("pidof $pkg").text.trim().let { it.isNotEmpty() && it.first().isDigit() }
                        if (up) seen += pkg
                        // the game has closed (or never started within 40 seconds): its settings go back and it is hidden again
                        if (up || !(pkg in seen || System.currentTimeMillis() - t0 > 40_000L)) continue
                        if (pkg in prof) revertProfile(ctx, pkg)
                        if (pkg in hide && setGameHidden(ctx, pkg, true, quiet = true)) prefs(ctx).edit().putStringSet("pendingHide", (prefs(ctx).getStringSet("pendingHide", emptySet()) ?: emptySet()) - pkg).apply()
                    }
                    Thread.sleep(4000)
                }
            } finally { running = false; stopSelf() }
        }.start()
        return START_NOT_STICKY
    }
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
    /** Raised when a cover has arrived from the store, so the pictures on the screen are read again. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

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
        val f = coverFile(c, pkg).takeIf { it.isFile } ?: playCoverFile(c, pkg).takeIf { it.isFile } ?: return null
        return cached("c/$pkg/${f.path.takeLast(14)}/${f.lastModified()}/$px") { decodeFile(f.path, px) }
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
    val ver = GameArt.version.intValue
    val art by produceState<ImageBitmap?>(null, pkg, apkOnly, wide, ver) {
        value = withContext(Dispatchers.IO) { (if (apkOnly) null else GameArt.cover(ctx, pkg, px)) ?: GameArt.banner(ctx, pkg, px) }
    }
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { GameArt.icon(ctx, pkg, 256) }
    }
    val bg = if (pal.flat) Modifier.background(pal.card) else Modifier.background(Brush.verticalGradient(listOf(pal.card, pal.bg)))
    Box(Modifier.fillMaxSize().then(bg), contentAlignment = Alignment.Center) {
        val a = art
        val ic = icon
        // the tile's ambient glow: its icon, blurred and under a black veil, so the colours show but the icon is not read (the crisp icon is on top)
        if (!wide && ic != null) {
            Image(ic, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
        }
        // a wide banner (a landscape store picture) is shown whole on a tile, so its logo is not cut off; covers still fill the card
        val wideArt = a != null && !wide && a.width > a.height * 1.2f
        if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = if (wideArt) ContentScale.Fit else ContentScale.Crop)
        else ic?.let { Image(it, null, Modifier.fillMaxSize(0.6f), contentScale = ContentScale.Fit) }
    }
}

/** One game as a store card: a tall cover (2:3, like a Steam library), the name under it, a small info line and a mark when it is hidden. */
@Composable
private fun GameTile(g: GameRow, pal: Pal, hidden: Boolean, info: String?, onClick: () -> Unit) {
    Column(Modifier.padding(6.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.Start) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(pal.radius))) { GameCover(g.pkg, pal, wide = false) }
        // the info line always takes its place, so a tile keeps its height when the sort changes
        Box(Modifier.padding(top = 4.dp).heightIn(min = 20.dp)) { if (info != null) Text(info, color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        Text(g.name, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 17.sp)
        if (hidden) Text(tr("hidden", "gizli"), color = pal.text.copy(alpha = .8f), fontSize = 12.sp)
    }
}

/** One game as a wide card: the cover across, the name and the info over its bottom edge, a star for a favourite. */
@Composable
private fun GameBanner(g: GameRow, pal: Pal, hidden: Boolean, fav: Boolean, info: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(pal.radius)).clickable(onClick = onClick)) {
        GameCover(g.pkg, pal)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(g.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            val sub = listOfNotNull(info, if (hidden) tr("hidden", "gizli") else null).joinToString("  ·  ")
            if (sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
        if (fav) Text("★", color = Color(0xFFFFD54F), fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

/** The small info under a banner for the chosen sort: the play time of the week, or the size of the app. */
private fun infoOf(g: GameRow, sort: Int, usage: Map<String, android.app.usage.UsageStats>, sizes: Map<String, Long>): String? = when (sort) {
    1 -> usage[g.pkg]?.totalTimeInForeground?.takeIf { it > 0 }?.let { hm(it) }
    2 -> sizes[g.pkg]?.takeIf { it > 0 }?.let { if (it >= 1_073_741_824L) "%.2f GB".format(it / 1_073_741_824.0) else "%.1f MB".format(it / 1_048_576.0) }
    else -> null
}

/** A row with a label, its value and a chevron, which opens a dialog. */
@Composable
private fun ValueRow(pal: Pal, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(pal.radius)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = pal.sub, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        FluentIcon("right", pal.sub, 14.dp)
    }
}

/** A small picture of a look: its desktop, a title bar in its accent, a window with a card and a sample of text. */
@Composable
private fun ThemePreview(tp: Pal) {
    Column(Modifier.size(112.dp, 66.dp).clip(RoundedCornerShape(tp.radius / 2)).background(tp.bg).padding(6.dp)) {
        Box(Modifier.fillMaxWidth().height(14.dp).background(tp.accent))
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(tp.radius / 2)).background(tp.card).padding(6.dp)) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(tp.radius / 2)).background(tp.accent))
            Spacer(Modifier.height(4.dp))
            Text("Aa", color = tp.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The look chooser: each look as a row with its preview; the chosen one is marked. */
@Composable
private fun ThemeDialog(current: String, ctx: Context, onPick: (String) -> Unit, onClose: () -> Unit) {
    val p = palOf(current, ctx)
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Look", "Görünüm"), color = p.text, fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
            THEMES.forEach { k ->
                val tp = palOf(k, ctx)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(p.bg).clickable { onPick(k) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThemePreview(tp)
                    Spacer(Modifier.width(12.dp))
                    Text(themeName(k), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (k == current) FluentIcon("check", p.accent, 18.dp)
                }
            }
        }
    }
}

/** A game's page, like a store page: the banner with the cover, the name, a big Play button, the play time and the last played day, and the settings of the game. */
@Composable
private fun GamePanel(g: GameRow, pal: Pal, can: Boolean, total: Long, lastUsed: Long, onHidden: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var coverTick by remember(g.pkg) { mutableStateOf(0) }
    var askAd by remember(g.pkg) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.GetContent()) { uri ->
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
    if (askAd) com.umutk.pulse.ads.RewardAdDialog(
        title = tr("Change the cover", "Kapağı değiştir"),
        text = tr("Watch a short ad to change the cover. It is given only when the ad is watched to the end.", "Kapağı değiştirmek için kısa bir reklam izle. Reklam sonuna kadar izlenirse verilir."),
        watch = tr("Watch", "İzle"), cancel = tr("Cancel", "İptal"),
        onWatch = {
            askAd = false
            val act = ctx as? android.app.Activity
            if (act == null) toast(ctx, tr("No ad now, try again", "Şu an reklam yok, tekrar dene"))
            else com.umutk.pulse.ads.PulseAds.showRewarded(act,
                onReward = { pick.launch("image/*") },
                onFail = { toast(ctx, tr("No ad now, try again", "Şu an reklam yok, tekrar dene")) })
        },
        onCancel = { askAd = false },
    )
    // the page's ground is the game's own cover, blurred and laid under the page (the flat colour when there is no cover)
    val blurCtx = LocalContext.current
    val blurred by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, g.pkg, coverTick) {
        value = withContext(Dispatchers.IO) {
            GameArt.cover(blurCtx, g.pkg, 240)?.let { img -> BlurBackdrop.blur(img.asAndroidBitmap(), 14f, pal.bg.toArgb()).asImageBitmap() }
        }
    }
    Box(Modifier.fillMaxWidth()) {
    blurred?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    if (blurred != null) Box(Modifier.matchParentSize().background(pal.bg.copy(alpha = .72f)))
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
            // the big Play button, and a shortcut of the game for the home screen (a hidden game starts from it too)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(pal.accent).clickable { openGame(ctx, g.pkg) }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FluentIcon("play", pal.bg, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Play", "Oyna"), color = pal.bg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(pal.card).clickable { pinGame(ctx, g.pkg) }, contentAlignment = Alignment.Center) { FluentIcon("add", pal.text, 22.dp) }
            }
            Spacer(Modifier.height(16.dp))
            // the facts: total time, last played
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(pal, Modifier.weight(1f), tr("Total played", "Toplam oynama"), if (total > 0) hm(total) else "-")
                StatCard(pal, Modifier.weight(1f), tr("Last played", "Son oynama"), if (lastUsed > 0) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(lastUsed)) else "-")
            }
            val favNow = GameHubState.favVersion.intValue.let { g.pkg in favSet(ctx) }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(pal, if (favNow) "★ " + tr("Favourite", "Favori") else "☆ " + tr("Favourite", "Favori")) { toggleFav(ctx, g.pkg) }
            }
            GamePerformance(pal, g.pkg, can)
            GameProfile(pal, g.pkg, can)
            SectionTitle(pal, tr("Cover", "Kapak"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(pal, tr("Change cover", "Kapağı değiştir")) { if (com.umutk.pulse.ads.PulseAds.ENABLED) askAd = true else pick.launch("image/*") }
                Chip(pal, tr("Remove cover", "Kapağı kaldır")) { coverFile(ctx, g.pkg).delete(); coverTick++ }
            }
            // the game does not report achievements: these are Pulse's own marks, counted from the time played
            GameMilestones(pal, g.pkg, total)
            GameHistory(pal, g.pkg)
            SectionTitle(pal, tr("Community", "Topluluk"))
            Text(tr("No community for an Android app here.", "Bir Android uygulaması için burada topluluk yok."), color = pal.sub, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
        }
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
    /** Raised when a favourite is switched, so the library re-sorts. */
    val favVersion = androidx.compose.runtime.mutableIntStateOf(0)
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
    var theme by remember { mutableStateOf(prefs(ctx).getString("theme", "shelf") ?: "shelf") }
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
    var tileSize by remember { mutableStateOf(prefs(ctx).getInt("grid", 0)) }
    var showHidden by remember { mutableStateOf(showHiddenInLibrary(ctx)) }
    var viewMode by remember { mutableStateOf(prefs(ctx).getInt("view", 0)) }   // library: 0 tiles, 1 banners
    val favs = remember(GameHubState.favVersion.intValue) { favSet(ctx) }
    // the library's own search: a floating button on the right opens the field under the sort row
    var libSearch by remember { mutableStateOf(false) }
    var libQuery by remember { mutableStateOf("") }
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
    // the store pictures of the games that have no cover yet are fetched one by one in the background
    LaunchedEffect(games.size) {
        if (!prefs(ctx).getBoolean("playCovers", true)) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            var got = 0
            for (g in games) {
                if (coverFile(ctx, g.pkg).isFile || playCoverFile(ctx, g.pkg).isFile) continue
                if (fetchPlayCover(ctx, g.pkg)) { got++; GameArt.version.intValue++ }
                kotlinx.coroutines.delay(600)
                if (got >= 40) break
            }
        }
    }
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
        Column(Modifier.fillMaxSize().hubGround(theme, pal)) {
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
                    // the game played last, to start again at once
                    games.filter { (usage[it.pkg]?.lastTimeUsed ?: 0L) > 0L }.maxByOrNull { usage[it.pkg]?.lastTimeUsed ?: 0L }?.let { last ->
                        Text(tr("Continue playing", "Oynamaya devam et"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(pal.radius)).clickable { profile = last }) {
                            GameCover(last.pkg, pal)
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f)))))
                            // removes the cover picture kept for the game (the one chosen or fetched); the game's own banner from its app stays
                            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = .5f))
                                .clickable { coverFile(ctx, last.pkg).delete(); playCoverFile(ctx, last.pkg).delete(); GameArt.version.intValue++ }, contentAlignment = Alignment.Center) {
                                FluentIcon("delete", Color.White, 16.dp)
                            }
                            Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(last.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(pal.accent).clickable { openGame(ctx, last.pkg) }.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(tr("Play", "Oyna"), color = pal.bg, fontWeight = FontWeight.Bold) }
                            }
                        }
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
                        (libQuery.isBlank() || g.name.contains(libQuery, true)) && when (filter) {
                            1 -> g.pkg !in hidden
                            2 -> g.pkg in hidden
                            else -> showHidden || g.pkg !in hidden
                        }
                    }
                    if (shown.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("No games here yet", "Burada henüz oyun yok"), color = pal.sub, fontSize = 15.sp, modifier = Modifier.padding(top = 40.dp))
                        Text(tr("Tap + to add an app", "Bir uygulama eklemek için + dokun"), color = pal.sub, fontSize = 13.sp)
                    } else if (theme == "shelf" && viewMode != 1) ShelfView(
                        sortGames(shown, sort, usage, sizes).sortedByDescending { it.pkg in favs }, hidden, pal,
                        onPlay = { g -> openGame(ctx, g.pkg) }, onDetails = { g -> profile = g },
                    ) else if (viewMode == 1) androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 56.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortGames(shown, sort, usage, sizes).sortedByDescending { it.pkg in favs }, key = { it.pkg }) { g -> GameBanner(g, pal, g.pkg in hidden, g.pkg in favs, infoLine(g, usage, sizes)) { profile = g } }
                    } else LazyVerticalGrid(
                        GridCells.Adaptive(gridDpOf(tileSize).dp),
                        Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        state = gridState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 56.dp, bottom = 90.dp),
                    ) {
                        items(sortGames(shown, sort, usage, sizes).sortedByDescending { it.pkg in favs }, key = { it.pkg }) { g -> GameTile(g, pal, g.pkg in hidden, infoLine(g, usage, sizes)) { profile = g } }
                    }
                    // the sort row, like the library bar of a store: name, play time, size
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to tr("Name", "Ad"), 1 to tr("Play time", "Süre"), 2 to tr("Size", "Boyut")).forEach { (k, label) ->
                            Chip(pal, if (sort == k) "● $label" else label) { sort = k; prefs(ctx).edit().putInt("sort", k).apply() }
                        }
                        Spacer(Modifier.weight(1f))
                        Chip(pal, if (viewMode == 0) tr("Banners", "Bannerlar") else tr("Tiles", "Kartlar")) { viewMode = 1 - viewMode; prefs(ctx).edit().putInt("view", viewMode).apply() }
                    }
                    // the search: a round button on the right; the field opens under the sort row
                    if (libSearch) PField(libQuery, { libQuery = it }, tr("Search games", "Oyun ara"),
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 56.dp))
                    FloatingActionButton(
                        onClick = { libSearch = !libSearch; if (!libSearch) libQuery = "" },
                        containerColor = pal.card,
                        contentColor = pal.text,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 96.dp),
                    ) { FluentIcon("search", pal.text, 22.dp) }
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
                    Text(tr("View", "Görünüm biçimi"), color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to tr("Tiles", "Kartlar"), 1 to tr("Banners", "Bannerlar")).forEach { (k, label) ->
                            Chip(pal, if (viewMode == k) "● $label" else label) { viewMode = k; prefs(ctx).edit().putInt("view", k).apply() }
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
                    SectionTitle(pal, tr("In-game menu", "Oyun içi menü"))
                    var menuOn by remember { mutableStateOf(GameMenuService.enabled(ctx)) }
                    SettingRow(pal, tr("Show the menu over a game", "Oyunun üstünde menüyü göster"), menuOn, true) { on ->
                        if (on && !Compat.canOverlay(ctx)) {
                            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        } else if (on && !usageAllowed(ctx)) {
                            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        } else {
                            menuOn = on; prefs(ctx).edit().putBoolean("menu", on).apply()
                            if (on) GameMenuService.start(ctx) else GameMenuService.stop(ctx)
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
                    var playCov by remember { mutableStateOf(prefs(ctx).getBoolean("playCovers", true)) }
                    SettingRow(pal, tr("Fetch covers from Google Play", "Kapakları Google Play'den getir"), playCov, true) { on -> playCov = on; prefs(ctx).edit().putBoolean("playCovers", on).apply() }
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
        Column(Modifier.fillMaxSize().hubGround(theme, pal).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { profile = null }, contentAlignment = Alignment.Center) { FluentIcon("back", pal.text, 22.dp) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val u = usage[g.pkg]
                GamePanel(g, pal, can, u?.totalTimeInForeground ?: 0L, u?.lastTimeUsed ?: 0L) { want -> setHidden(g.pkg, want) }
                // the size of the app, and its data folder opened straight in Pulse's Files
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Size", "Boyut"), color = pal.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(sizes[g.pkg]?.takeIf { it > 0 }?.let { "%.1f MB".format(it / 1_048_576.0) } ?: "-", color = pal.text, fontSize = 14.sp)
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(pal.card)
                    .clickable { openDataFolder(ctx, g.pkg) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    FluentIcon("folder", pal.accent, 20.dp); Spacer(Modifier.width(10.dp))
                    Text(tr("Open data folder", "Data klasörünü aç"), color = pal.text, fontSize = 15.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Play time and size of a game under its banner, both when known (the sort order does not hide either). */
private fun infoLine(g: GameRow, usage: Map<String, android.app.usage.UsageStats>, sizes: Map<String, Long>): String? {
    val parts = listOfNotNull(
        usage[g.pkg]?.totalTimeInForeground?.takeIf { it > 0 }?.let { hm(it) },
        sizes[g.pkg]?.takeIf { it > 0 }?.let { "%.0f MB".format(it / 1_048_576.0) },
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

/** The app's data folder in Pulse's Files, opened at once. */
private fun openDataFolder(c: Context, pkg: String) {
    val dir = "/storage/emulated/0/Android/data/$pkg"
    runCatching { c.startActivity(Intent(c, com.umutk.pulse.FilesActivity::class.java).putExtra("open_dir", dir).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

/**
 * The size of a game on the phone: its program files, its data and its extra files (the OBB). It is worked out from the best source there is:
 * with root or Shizuku the folders of the game are counted (this also works for a game that is hidden, which the package manager no longer shows),
 * else the system's own count (needs the usage access), else the APK files (base and splits). The last good number is kept, and shown while a
 * game cannot be measured; a fresh one is made at most every twelve hours, so the library opens fast.
 */
private fun appSize(c: Context, pkg: String): Long {
    val p = prefs(c)
    val cached = p.getLong("size2_$pkg", 0L)
    val age = System.currentTimeMillis() - p.getLong("sizet2_$pkg", 0L)
    if (cached > 0 && age in 0..43_200_000L) return cached
    var best = 0L
    // the program files: the base APK and every split
    runCatching {
        @Suppress("DEPRECATION") val ai = c.packageManager.getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
        var apk = java.io.File(ai.sourceDir).length()
        ai.splitSourceDirs?.forEach { apk += java.io.File(it).length() }
        best = maxOf(best, apk)
    }
    // the system's count: program, data and cache
    if (android.os.Build.VERSION.SDK_INT >= 26) runCatching {
        val ssm = c.getSystemService(Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
        val st = ssm.queryStatsForPackage(android.os.storage.StorageManager.UUID_DEFAULT, pkg, android.os.Process.myUserHandle())
        best = maxOf(best, st.appBytes + st.dataBytes + st.cacheBytes)
    }
    // root or Shizuku: the folders of the game, hidden or not (program, data, the OBB and the media folder)
    if (Privilege.mode != Privilege.Mode.None) runCatching {
        val out = Privilege.run(
            "du -sk /data/app/*/$pkg-* /data/app/$pkg-* /data/data/$pkg /data/user_de/0/$pkg /storage/emulated/0/Android/data/$pkg /storage/emulated/0/Android/obb/$pkg /storage/emulated/0/Android/media/$pkg 2>/dev/null " +
                "| awk '{ if (!seen[\$2]++) s += \$1 } END { print s * 1024 }'"
        ).text.lines().lastOrNull { it.isNotBlank() }?.trim()?.toDoubleOrNull()?.toLong() ?: 0L
        best = maxOf(best, out)
    }
    if (best > 0) { p.edit().putLong("size2_$pkg", best).putLong("sizet2_$pkg", System.currentTimeMillis()).apply(); return best }
    return cached
}

/** The library in the chosen order: name, the play time of the last years, or the size of the app. */
private fun sortGames(list: List<GameRow>, sort: Int, usage: Map<String, android.app.usage.UsageStats>, sizes: Map<String, Long>): List<GameRow> = when (sort) {
    1 -> list.sortedByDescending { usage[it.pkg]?.totalTimeInForeground ?: 0L }
    2 -> list.sortedByDescending { sizes[it.pkg] ?: 0L }
    else -> list
}

// ---- performance of a game ----------------------------------------------------------------------------------------------------------------------
/**
 * Per game: the performance mode (standard, performance, battery), the resolution scale (percent) and a frame-rate cap (0 = the game's own). They are set through
 * Android's own game manager in the root or Shizuku shell (`cmd game`), and again before each start from Game Hub. What a game takes of them is up to the game
 * and the phone: a game that does not support game modes ignores them.
 */
private fun tuneOf(c: Context, pkg: String) = Triple(prefs(c).getString("tm_$pkg", "performance") ?: "performance", prefs(c).getInt("td_$pkg", 100), prefs(c).getInt("tf_$pkg", 0))

private fun applyTune(c: Context, pkg: String): Boolean {
    val (mode, down, fps) = tuneOf(c, pkg)
    val cmds = if (mode == "standard" && down == 100 && fps == 0) listOf("cmd game reset $pkg")
    else listOf("cmd game set --mode $mode --downscale ${if (down >= 100) "disable" else "%.2f".format(java.util.Locale.US, down / 100f)} --fps ${if (fps <= 0) "disable" else fps} $pkg")
    return cmds.all { cmd ->
        val out = Privilege.run(cmd)
        out.code >= 0 && !Regex("(?i)error|exception|unknown|denied|not supported|unsupported").containsMatchIn(out.text)
    }
}

/** Before a start: the saved tuning of the game is set again, and the background apps are closed when that is switched on for the game. Needs root or Shizuku. */
private fun prepareGame(c: Context, pkg: String) {
    if (GameMenuService.enabled(c) && Compat.canOverlay(c)) GameMenuService.start(c)
    if (Privilege.mode == Privilege.Mode.None) return
    val (mode, down, fps) = tuneOf(c, pkg)
    if (mode != "standard" || down != 100 || fps != 0) applyTune(c, pkg)
    if (prefs(c).getBoolean("tb_$pkg", false)) Privilege.run("am kill-all")
    if (applyProfile(c, pkg)) GameRehideService.start(c)
}

// ---- the profile of a game: brightness, volume, do not disturb and the network, set when it starts and put back when it closes ----
private fun profileOf(c: Context, pkg: String) = listOf(prefs(c).getInt("pb_$pkg", -1), prefs(c).getInt("pv_$pkg", -1), if (prefs(c).getBoolean("pd_$pkg", false)) 1 else 0, when (prefs(c).getString("pn_$pkg", "")) { "wifi" -> 1; "mobile" -> 2; else -> 0 })

/** Sets the profile of [pkg]; what each value was is kept first. False when the game has no profile. */
private fun applyProfile(c: Context, pkg: String): Boolean {
    val (b, v, d, n) = profileOf(c, pkg)
    if (b < 0 && v < 0 && d == 0 && n == 0) return false
    val p = prefs(c).edit()
    val am = c.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    if (v >= 0) {
        p.putInt("old_v_$pkg", am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        runCatching { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) * v / 100).coerceAtLeast(0), 0) }
    }
    if (Privilege.mode != Privilege.Mode.None) {
        if (b >= 0) {
            p.putString("old_b_$pkg", Privilege.run("settings get system screen_brightness").text.trim()).putString("old_bm_$pkg", Privilege.run("settings get system screen_brightness_mode").text.trim())
            Privilege.run("settings put system screen_brightness_mode 0; settings put system screen_brightness ${(255 * b / 100).coerceIn(1, 255)}")
        }
        if (d == 1) {
            p.putInt("old_d_$pkg", (c.getSystemService(android.app.NotificationManager::class.java)).currentInterruptionFilter)
            Privilege.run("cmd notification set_dnd on")
        }
        if (n != 0) {
            p.putString("old_md_$pkg", Privilege.run("settings get global mobile_data").text.trim()).putString("old_wf_$pkg", Privilege.run("settings get global wifi_on").text.trim())
            Privilege.run(if (n == 1) "svc data disable" else "svc wifi disable")
        }
    }
    p.putStringSet("profileOn", (prefs(c).getStringSet("profileOn", emptySet()) ?: emptySet()) + pkg).apply()
    return true
}

/** Puts back what [applyProfile] changed. */
private fun revertProfile(c: Context, pkg: String) {
    val sp = prefs(c)
    val (b, v, d, n) = profileOf(c, pkg)
    val am = c.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    if (v >= 0 && sp.contains("old_v_$pkg")) runCatching { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, sp.getInt("old_v_$pkg", 0), 0) }
    if (Privilege.mode != Privilege.Mode.None) {
        if (b >= 0) {
            sp.getString("old_b_$pkg", null)?.takeIf { it.isNotEmpty() && it != "null" }?.let { Privilege.run("settings put system screen_brightness $it") }
            sp.getString("old_bm_$pkg", null)?.takeIf { it.isNotEmpty() && it != "null" }?.let { Privilege.run("settings put system screen_brightness_mode $it") }
        }
        if (d == 1 && sp.getInt("old_d_$pkg", android.app.NotificationManager.INTERRUPTION_FILTER_ALL) == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) Privilege.run("cmd notification set_dnd off")
        if (n == 1 && sp.getString("old_md_$pkg", "1") != "0") Privilege.run("svc data enable")
        if (n == 2 && sp.getString("old_wf_$pkg", "1") != "0") Privilege.run("svc wifi enable")
    }
    sp.edit().putStringSet("profileOn", (sp.getStringSet("profileOn", emptySet()) ?: emptySet()) - pkg).apply()
}

@Composable
private fun GameProfile(pal: Pal, pkg: String, can: Boolean) {
    val ctx = LocalContext.current
    var b by remember(pkg) { mutableStateOf(prefs(ctx).getInt("pb_$pkg", -1)) }
    var v by remember(pkg) { mutableStateOf(prefs(ctx).getInt("pv_$pkg", -1)) }
    var d by remember(pkg) { mutableStateOf(prefs(ctx).getBoolean("pd_$pkg", false)) }
    var n by remember(pkg) { mutableStateOf(prefs(ctx).getString("pn_$pkg", "") ?: "") }
    SectionTitle(pal, tr("Profile", "Profil"))
    Text(tr("Brightness", "Parlaklık"), color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-1 to tr("As is", "Aynı"), 30 to "30%", 60 to "60%", 100 to "100%").forEach { (k, l) -> Chip(pal, if (b == k) "● $l" else l) { if (can || k < 0) { b = k; prefs(ctx).edit().putInt("pb_$pkg", k).apply() } } }
    }
    Text(tr("Volume", "Ses"), color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-1 to tr("As is", "Aynı"), 0 to tr("Mute", "Sessiz"), 50 to "50%", 100 to "100%").forEach { (k, l) -> Chip(pal, if (v == k) "● $l" else l) { v = k; prefs(ctx).edit().putInt("pv_$pkg", k).apply() } }
    }
    SettingRow(pal, tr("Do not disturb while playing", "Oynarken rahatsız etme"), d, can) { on -> d = on; prefs(ctx).edit().putBoolean("pd_$pkg", on).apply() }
    Text(tr("Network", "Ağ"), color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("" to tr("As is", "Aynı"), "wifi" to tr("Wi-Fi only", "Yalnız Wi-Fi"), "mobile" to tr("Mobile only", "Yalnız mobil")).forEach { (k, l) -> Chip(pal, if (n == k) "● $l" else l) { if (can || k.isEmpty()) { n = k; prefs(ctx).edit().putString("pn_$pkg", k).apply() } } }
    }
}

private fun usageAllowed(c: Context): Boolean = runCatching {
    val ops = c.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) == android.app.AppOpsManager.MODE_ALLOWED
}.getOrDefault(false)

@Composable
private fun GamePerformance(pal: Pal, pkg: String, can: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember(pkg) { mutableStateOf(tuneOf(ctx, pkg).first) }
    var down by remember(pkg) { mutableStateOf(tuneOf(ctx, pkg).second) }
    var fps by remember(pkg) { mutableStateOf(tuneOf(ctx, pkg).third) }
    var boost by remember(pkg) { mutableStateOf(prefs(ctx).getBoolean("tb_$pkg", false)) }
    fun apply() {
        prefs(ctx).edit().putString("tm_$pkg", mode).putInt("td_$pkg", down).putInt("tf_$pkg", fps).apply()
        scope.launch(Dispatchers.IO) {
            val ok = applyTune(ctx, pkg)
            if (!ok) withContext(Dispatchers.Main) { toast(ctx, tr("The phone did not take this setting", "Telefon bu ayarı kabul etmedi")) }
        }
    }
    SectionTitle(pal, tr("Performance", "Performans"))
    if (!can) Text(tr("Needs root or Shizuku", "Root ya da Shizuku gerekir"), color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("standard" to tr("Standard", "Standart"), "performance" to tr("Performance", "Performans"), "battery" to tr("Battery", "Pil")).forEach { (k, label) ->
            Chip(pal, if (mode == k) "● $label" else label) { if (can) { mode = k; apply() } }
        }
    }
    Text(tr("Resolution", "Çözünürlük"), color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(100, 85, 70, 50).forEach { v ->
            Chip(pal, if (down == v) "● $v%" else "$v%") { if (can) { down = v; apply() } }
        }
    }
    Text("FPS", color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 30, 60, 90, 120).forEach { v ->
            val label = if (v == 0) tr("Game's own", "Oyunun") else "$v"
            Chip(pal, if (fps == v) "● $label" else label) { if (can) { fps = v; apply() } }
        }
    }
    SettingRow(pal, tr("Close background apps before play", "Oynamadan önce arka plan uygulamalarını kapat"), boost, can) { on ->
        boost = on; prefs(ctx).edit().putBoolean("tb_$pkg", on).apply()
    }
}

/** The time played on each of the last [days] days, oldest first (milliseconds). */
private fun dailyUsage(c: Context, pkg: String, days: Int): List<Long> = runCatching {
    val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
    val end = System.currentTimeMillis()
    val per = HashMap<String, Long>()
    usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, end - days * 86_400_000L, end).filter { it.packageName == pkg }.forEach { u ->
        val k = fmt.format(java.util.Date(u.firstTimeStamp)); per[k] = (per[k] ?: 0L) + u.totalTimeInForeground
    }
    (days - 1 downTo 0).map { per[fmt.format(java.util.Date(end - it * 86_400_000L))] ?: 0L }
}.getOrDefault(List(days) { 0L })

/** The last 30 days as bars, and the goal for the week: a number of hours the person sets, with how far this week has got. */
@Composable
private fun GameHistory(pal: Pal, pkg: String) {
    val ctx = LocalContext.current
    val daily by produceState(List(30) { 0L }, pkg) { value = withContext(Dispatchers.IO) { dailyUsage(ctx, pkg, 30) } }
    var goal by remember(pkg) { mutableStateOf(prefs(ctx).getInt("gw_$pkg", 0)) }
    val week = daily.takeLast(7).sum()
    SectionTitle(pal, tr("Last 30 days", "Son 30 gün"))
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
    Canvas(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(pal.card)) {
        val bw = size.width / (daily.size * 1.5f)
        daily.forEachIndexed { i, t ->
            val h = (size.height - 16f) * (t.toFloat() / max)
            drawRect(if (i == daily.lastIndex) pal.accent else pal.accent.copy(alpha = .55f), topLeft = Offset(i * bw * 1.5f + bw * .5f, size.height - 8f - h), size = Size(bw, h.coerceAtLeast(if (t > 0) 3f else 1f)))
        }
    }
    Text(tr("Most in a day", "Bir günde en çok") + ": " + hm(max.takeIf { daily.any { it > 0 } } ?: 0L), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    SectionTitle(pal, tr("Weekly goal", "Haftalık hedef"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to tr("None", "Yok"), 2 to "2 " + tr("h", "sa"), 5 to "5 " + tr("h", "sa"), 10 to "10 " + tr("h", "sa"), 20 to "20 " + tr("h", "sa")).forEach { (k, l) ->
            Chip(pal, if (goal == k) "● $l" else l) { goal = k; prefs(ctx).edit().putInt("gw_$pkg", k).apply() }
        }
    }
    if (goal > 0) {
        val frac = (week / (goal * 3_600_000f)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(pal.card)) { Box(Modifier.fillMaxWidth(frac).height(10.dp).background(pal.accent)) }
        Text(hm(week) + " / $goal " + tr("h", "sa") + if (frac >= 1f) "  ✓" else "", color = if (frac >= 1f) pal.accent else pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Marks for the time played (Pulse's own, since the game reports none), and the days played in the last 30. */
@Composable
private fun GameMilestones(pal: Pal, pkg: String, total: Long) {
    val ctx = LocalContext.current
    val days by produceState(0, pkg) { value = withContext(Dispatchers.IO) { daysPlayed(ctx, pkg, 30) } }
    val marks = listOf(1, 5, 10, 25, 50, 100, 250, 500)
    val hours = total / 3_600_000.0
    val got = marks.count { hours >= it }
    SectionTitle(pal, tr("Achievements", "Başarımlar") + "  $got/${marks.size}")
    marks.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { m ->
                val on = hours >= m
                Box(Modifier.weight(1f).clip(RoundedCornerShape(pal.radius.coerceAtLeast(6.dp))).background(if (on) pal.accent else pal.card).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (on) { FluentIcon("check", pal.bg, 14.dp); Spacer(Modifier.width(4.dp)) }
                        Text("$m " + tr("h", "sa"), color = if (on) pal.bg else pal.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    Text(tr("Days played in the last 30", "Son 30 günde oynanan gün") + ": $days", color = pal.sub, fontSize = 13.sp)
}

private fun daysPlayed(c: Context, pkg: String, days: Int): Int = runCatching {
    val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    val end = System.currentTimeMillis()
    usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, end - days * 86_400_000L, end).count { it.packageName == pkg && it.totalTimeInForeground > 0 }
}.getOrDefault(0)

// ---- the menu over a game ----------------------------------------------------------------------------------------------------------------------
/**
 * While a game of the library is on the screen, a small tab sits on the right edge; a tap opens a panel over the game: the battery, the heat and the free memory,
 * do not disturb, the performance mode, a screenshot and closing the background apps. It looks at the foreground app every few seconds (usage access) and draws over
 * other apps; the actions use root or Shizuku.
 */
class GameMenuService : android.app.Service() {
    companion object {
        fun enabled(c: Context) = prefs(c).getBoolean("menu", false)
        fun start(c: Context) { runCatching { val i = android.content.Intent(c, GameMenuService::class.java); if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) } }
        fun stop(c: Context) { c.stopService(android.content.Intent(c, GameMenuService::class.java)) }
    }

    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var wm: android.view.WindowManager
    private var handle: android.view.View? = null
    private var panel: android.view.View? = null
    private var shown: String? = null
    private var fg: String? = null
    private var lastTs = 0L
    private var nullStreak = 0
    private var games = emptySet<String>()
    private var gamesAt = 0L
    private val poll = object : Runnable { override fun run() { runCatching { check() }; h.postDelayed(this, 2500) } }
    // the frame counter, the recording and the warnings
    private var fpsView: android.widget.TextView? = null
    private var fpsLayer: String? = null
    private var fpsBusy = false
    private var recordingSince = 0L
    private var warnHeatAt = 0L
    private var warnBatAt = 0L
    private val fpsPoll = object : Runnable {
        override fun run() {
            val pkg = shown
            if (pkg != null && fpsView != null && !fpsBusy) {
                fpsBusy = true
                Thread {
                    val fps = runCatching { measureFps(pkg) }.getOrNull()
                    h.post { fpsView?.text = if (fps == null) "FPS –" else "FPS $fps"; fpsBusy = false }
                }.start()
            }
            if (fpsView != null) h.postDelayed(this, 1000)
        }
    }

    /** The frame rate of the game's window from SurfaceFlinger (root or Shizuku): the frames shown in the last second. Null when the window cannot be found. */
    private fun measureFps(pkg: String): Int? {
        var layer = fpsLayer
        if (layer == null) {
            val list = Privilege.run("dumpsys SurfaceFlinger --list").text.lines()
            layer = list.firstOrNull { it.contains(pkg) && it.contains("SurfaceView") } ?: list.firstOrNull { it.contains(pkg) && !it.contains("Background") } ?: return null
            fpsLayer = layer.trim()
        }
        val rows = Privilege.run("dumpsys SurfaceFlinger --latency '$layer'").text.lines().drop(1).mapNotNull { l -> l.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() }.filter { it > 0 && it < Long.MAX_VALUE / 2 }
        if (rows.size < 2) { fpsLayer = null; return null }
        val last = rows.max()
        return rows.count { it > last - 1_000_000_000L }
    }

    private fun showFps() {
        if (fpsView != null) return
        val d = resources.displayMetrics.density
        val v = android.widget.TextView(this).apply {
            text = "FPS …"; textSize = 13f; setTextColor(0xFF7CFC00.toInt()); setPadding((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadius = 8 * d }
        }
        val l = android.view.WindowManager.LayoutParams(-2, -2, android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, android.graphics.PixelFormat.TRANSLUCENT)
            .apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; x = (8 * d).toInt(); y = (8 * d).toInt() }
        if (runCatching { wm.addView(v, l) }.isSuccess) { fpsView = v; h.post(fpsPoll) }
    }

    private fun hideFps() { fpsView?.let { v -> runCatching { wm.removeView(v) } }; fpsView = null; fpsLayer = null }

    /** A warning, at most every ten minutes each: the phone is hot, or the battery is low and not charging. */
    private fun warn() {
        val bat = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return
        val temp = bat.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val pct = 100 * bat.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) / bat.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val charging = bat.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        val now = android.os.SystemClock.elapsedRealtime()
        if (temp >= 43f && now - warnHeatAt > 600_000L) { warnHeatAt = now; toast(this, tr("The phone is hot", "Telefon ısındı") + ": %.1f \u00B0C".format(temp)) }
        if (pct <= 15 && !charging && now - warnBatAt > 600_000L) { warnBatAt = now; toast(this, tr("Battery is low", "Pil azaldı") + ": $pct%") }
    }

    override fun onBind(i: android.content.Intent?): android.os.IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel("game_menu", "Game Hub", android.app.NotificationManager.IMPORTANCE_MIN))
        val n = androidx.core.app.NotificationCompat.Builder(this, "game_menu").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Game Hub").setOngoing(true).setSilent(true).build()
        runCatching { if (Build.VERSION.SDK_INT >= 34) startForeground(22, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(22, n) }
        if (!Compat.canOverlay(this)) { stopSelf(); return }
        wm = getSystemService(android.view.WindowManager::class.java)
        Thread { if (Privilege.mode == Privilege.Mode.None) runCatching { Privilege.detect() } }.start()
        h.post(poll)
    }

    override fun onStartCommand(i: android.content.Intent?, f: Int, id: Int): Int = START_STICKY

    override fun onDestroy() { h.removeCallbacksAndMessages(null); hide(); super.onDestroy() }

    private fun check() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(maxOf(lastTs, now - 20_000L), now) ?: return
        val e = android.app.usage.UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            lastTs = maxOf(lastTs, e.timeStamp + 1)
            when (e.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> fg = e.packageName
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> if (fg == e.packageName) fg = null
            }
        }
        if (now - gamesAt > 60_000L) { games = loadGames(this).map { it.pkg }.toSet(); gamesAt = now }
        val g = fg?.takeIf { it in games }
        if (g == null) { if (++nullStreak >= 2) hide() } else { nullStreak = 0; if (shown != g) show(g); warn() }
    }

    private fun lp(w: Int, hgt: Int, x: Int = 0) = android.view.WindowManager.LayoutParams(w, hgt, android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, android.graphics.PixelFormat.TRANSLUCENT).apply {
        gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL; this.x = x
    }

    private fun show(pkg: String) {
        hide()
        shown = pkg
        val d = resources.displayMetrics.density
        val v = android.widget.TextView(this).apply {
            text = "\u2039"; textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadii = floatArrayOf(14 * d, 14 * d, 0f, 0f, 0f, 0f, 14 * d, 14 * d) }
            setOnClickListener { togglePanel(pkg) }
        }
        if (runCatching { wm.addView(v, lp((26 * d).toInt(), (64 * d).toInt())) }.isSuccess) handle = v
        if (prefs(this).getBoolean("menuFps", false)) showFps()
    }

    private fun hide() {
        closePanel(); hideFps()
        handle?.let { v -> runCatching { wm.removeView(v) } }
        handle = null; shown = null
    }

    private fun closePanel() { panel?.let { v -> runCatching { wm.removeView(v) } }; panel = null }

    private fun togglePanel(pkg: String) { if (panel != null) closePanel() else openPanel(pkg) }

    private fun sh(cmd: String, done: (Privilege.Out) -> Unit = {}) { Thread { val o = Privilege.run(cmd); h.post { done(o) } }.start() }

    private fun openPanel(pkg: String) {
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(px(14), px(12), px(14), px(12))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xF0141414.toInt()); cornerRadius = 18 * d }
        }
        fun label(t: String, size: Float, c: Int = 0xFFFFFFFF.toInt()) = android.widget.TextView(this).apply { text = t; textSize = size; setTextColor(c) }
        fun button(t: String, on: () -> Unit) = android.widget.TextView(this).apply {
            text = t; textSize = 14f; setTextColor(0xFFFFFFFF.toInt()); gravity = android.view.Gravity.CENTER
            setPadding(px(10), px(9), px(10), px(9))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF2A2A2A.toInt()); cornerRadius = 10 * d }
            setOnClickListener { on() }
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) }
        }
        val name = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        col.addView(label(name, 16f))
        val bat = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val pct = bat?.let { 100 * it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) / it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
        val temp = (bat?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val mi = android.app.ActivityManager.MemoryInfo().also { (getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it) }
        col.addView(label("$pct%  ·  %.1f \u00B0C  ·  %.1f GB".format(temp, mi.availMem / 1_073_741_824.0), 13f, if (temp >= 43f || pct <= 15) 0xFFFF6E6E.toInt() else 0xFFB0B0B0.toInt()))
        // do not disturb
        val dndOn = { getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL }
        lateinit var dnd: android.widget.TextView
        fun dndText() = tr("Do not disturb", "Rahatsız etme") + ": " + if (dndOn()) tr("on", "açık") else tr("off", "kapalı")
        dnd = button(dndText()) { sh("cmd notification set_dnd ${if (dndOn()) "off" else "on"}") { h.postDelayed({ dnd.text = dndText() }, 300) } }
        col.addView(dnd)
        // the performance mode
        val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL; layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) } }
        listOf("standard" to tr("Standard", "Standart"), "performance" to tr("Performance", "Performans"), "battery" to tr("Battery", "Pil")).forEach { (k, t) ->
            row.addView(android.widget.TextView(this).apply {
                text = if (tuneOf(this@GameMenuService, pkg).first == k) "\u25CF $t" else t; textSize = 12f; setTextColor(0xFFFFFFFF.toInt()); gravity = android.view.Gravity.CENTER
                setPadding(px(4), px(9), px(4), px(9))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF2A2A2A.toInt()); cornerRadius = 10 * d }
                layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) }
                setOnClickListener {
                    prefs(this@GameMenuService).edit().putString("tm_$pkg", k).apply()
                    sh("cmd game mode $k $pkg") { o -> toast(this@GameMenuService, if (o.code >= 0 && !Regex("(?i)error|exception|unknown|denied").containsMatchIn(o.text)) t else tr("The phone did not take this setting", "Telefon bu ayarı kabul etmedi")); closePanel() }
                }
            })
        }
        col.addView(row)
        // the frame counter in the corner
        lateinit var fpsBtn: android.widget.TextView
        fun fpsText() = "FPS: " + if (fpsView != null) tr("on", "açık") else tr("off", "kapalı")
        fpsBtn = button(fpsText()) {
            val on = fpsView == null
            prefs(this).edit().putBoolean("menuFps", on).apply()
            if (on) showFps() else hideFps()
            fpsBtn.text = fpsText()
        }
        col.addView(fpsBtn)
        // the screen recording (up to three minutes), saved in Movies
        lateinit var recBtn: android.widget.TextView
        fun recText() = if (recordingSince > 0L && android.os.SystemClock.elapsedRealtime() - recordingSince < 180_000L) tr("Stop recording", "Kaydı durdur") else tr("Record screen", "Ekranı kaydet")
        recBtn = button(recText()) {
            if (recText() == tr("Stop recording", "Kaydı durdur")) {
                recordingSince = 0L
                sh("pkill -2 screenrecord") { toast(this, tr("Saved in Movies", "Movies klasörüne kaydedildi")) }
            } else {
                recordingSince = android.os.SystemClock.elapsedRealtime()
                closePanel()
                sh("mkdir -p /sdcard/Movies && (nohup screenrecord --time-limit 180 /sdcard/Movies/Game_${System.currentTimeMillis()}.mp4 >/dev/null 2>&1 &)") { toast(this, tr("Recording", "Kayıt başladı")) }
            }
            recBtn.text = recText()
        }
        col.addView(recBtn)
        col.addView(button(tr("Screenshot", "Ekran görüntüsü")) {
            closePanel(); handle?.visibility = android.view.View.INVISIBLE
            h.postDelayed({
                val f = "/sdcard/Pictures/Screenshots/Game_" + System.currentTimeMillis() + ".png"
                sh("mkdir -p /sdcard/Pictures/Screenshots && screencap -p $f") { o ->
                    handle?.visibility = android.view.View.VISIBLE
                    toast(this, if (o.code == 0 || o.text.isBlank()) tr("Saved", "Kaydedildi") else o.text.take(80))
                }
            }, 450)
        })
        col.addView(button(tr("Close background apps", "Arka plan uygulamalarını kapat")) { sh("am kill-all") { toast(this, tr("Done", "Tamam")); closePanel() } })
        val v = col
        if (runCatching { wm.addView(v, lp(px(270), -2, px(34))) }.isSuccess) panel = v
    }
}


/**
 * The shelf: the games stand on wooden shelves, three to a row, each box art on its own card. A tap turns the card over (a small 3D turn, only that card
 * animates) and shows its name, the Play button and the page of the game. Rows are built lazily, so a long library stays quick.
 */
@Composable
private fun ShelfView(games: List<GameRow>, hidden: Set<String>, pal: Pal, onPlay: (GameRow) -> Unit, onDetails: (GameRow) -> Unit) {
    val rows = games.chunked(3)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 56.dp, bottom = 90.dp),
    ) {
        itemsIndexed(rows) { _, row ->
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    row.forEach { g ->
                        Box(Modifier.weight(1f)) { ShelfCard(g, pal, g.pkg in hidden, onPlay, onDetails) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                // the plank the boxes stand on
                Box(Modifier.fillMaxWidth().padding(top = 0.dp).height(10.dp).clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF6B4A33), Color(0xFF3E2A1D)))))
            }
        }
    }
}

@Composable
private fun ShelfCard(g: GameRow, pal: Pal, hidden: Boolean, onPlay: (GameRow) -> Unit, onDetails: (GameRow) -> Unit) {
    var back by remember(g.pkg) { mutableStateOf(false) }
    // the turn: 0 shows the front, 180 the back; only this card's layer is touched while it turns
    val turn by androidx.compose.animation.core.animateFloatAsState(if (back) 180f else 0f, androidx.compose.animation.core.tween(380), label = "turn")
    Box(
        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            .graphicsLayer { rotationY = turn; cameraDistance = 14f * density }
            .clip(RoundedCornerShape(10.dp))
            .clickable { back = !back },
    ) {
        if (turn <= 90f) {
            Box(Modifier.fillMaxSize()) { GameCover(g.pkg, pal, wide = false) }
            if (hidden) Text(tr("hidden", "gizli"), color = pal.text.copy(alpha = .8f), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = .5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        } else {
            // the back: drawn un-mirrored, the turn is already past the middle
            Column(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.background(pal.card).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(g.name, color = pal.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(pal.radius)).background(pal.accent)
                        .clickable { onPlay(g) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(tr("Play", "Oyna"), color = pal.bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(tr("Details", "Ayrıntılar"), color = pal.accent, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable { onDetails(g) }.padding(4.dp), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
