package com.umutk.pulse.extras.gamehub

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import com.umutk.pulse.Compat
import com.umutk.pulse.extras.blackout.Privilege
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/*
 * Game Hub's shelf: Boxshelf's own screen (GPL-3.0, https://github.com/sassiestrhihno/boxshelf-source), run unchanged in a WebView from the app's
 * assets (assets/boxshelf, see NOTICE.txt there). Boxshelf's screen asks one object for its games; pulse-api.js there answers with the games below
 * (PulseHost), and Play starts the game through Pulse. Nothing is fetched from the net: the page, its icons and covers are served from the app.
 */

private const val HOST = "boxshelf.pulse.local"

/** The app's own pictures and pages for the page: /app/<file> (the assets), /icon/<package> (a PNG), /cover/<package> (the cover picture). */
private fun serve(c: Context, url: android.net.Uri): WebResourceResponse? {
    if (url.host != HOST) return null
    val parts = url.pathSegments
    val kind = parts.firstOrNull() ?: return null
    return runCatching {
        when (kind) {
            "app" -> {
                val rel = parts.drop(1).joinToString("/").ifEmpty { "index.html" }
                val ins = c.assets.open("boxshelf/$rel")
                val mime = when (rel.substringAfterLast('.', "").lowercase()) {
                    "html" -> "text/html"; "js" -> "text/javascript"; "css" -> "text/css"; "json" -> "application/json"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
                    "svg" -> "image/svg+xml"; "woff2" -> "font/woff2"; "woff" -> "font/woff"; "webp" -> "image/webp"; "glb" -> "model/gltf-binary"; else -> "application/octet-stream"
                }
                WebResourceResponse(mime, "utf-8", ins)
            }
            "icon" -> {
                val pkg = parts.getOrNull(1) ?: return null
                val bmp = GameArt.icon(c, pkg, 256)?.asAndroidBitmap() ?: return null
                val out = java.io.ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                WebResourceResponse("image/png", null, ByteArrayInputStream(out.toByteArray()))
            }
            "cover" -> {
                val pkg = parts.getOrNull(1) ?: return null
                val f = coverFile(c, pkg).takeIf { it.isFile } ?: caseFile(c, pkg).takeIf { it.isFile } ?: steamCoverFile(c, pkg).takeIf { it.isFile } ?: playCoverFile(c, pkg).takeIf { it.isFile } ?: return null
                WebResourceResponse("image/jpeg", null, FileInputStream(f))
            }
            "shot" -> {
                val pkg = parts.getOrNull(1) ?: return null
                val k = parts.getOrNull(2)?.toIntOrNull() ?: return null
                WebResourceResponse("image/jpeg", null, FileInputStream(steamShotFile(c, pkg, k).takeIf { it.isFile } ?: return null))
            }
            "hero" -> {
                val pkg = parts.getOrNull(1) ?: return null
                WebResourceResponse("image/jpeg", null, FileInputStream(steamHeroFile(c, pkg).takeIf { it.isFile } ?: return null))
            }
            else -> null
        }
    }.getOrNull()
}

/** Counts the changes the web shelf must read again (the boxes were made in another style, covers were found): the shelf is keyed by it. */
internal object ShelfSync {
    val rev = androidx.compose.runtime.mutableIntStateOf(0)
    fun bump() { android.os.Handler(android.os.Looper.getMainLooper()).post { rev.intValue++ } }
}

/** The game's box in the chosen console case (none for the Steam style). */
internal fun caseFile(c: Context, pkg: String): java.io.File = CaseCover.fileFor(c, pkg) ?: java.io.File(c.filesDir, "none")

/** Whether Boxshelf's opening animation has ended (the page says so); the shelf's + waits for it. A fallback shows it after a few seconds anyway. */
internal object ShelfIntro { val done = androidx.compose.runtime.mutableStateOf(false) }

/** What the page can ask Pulse for. */
private class PulseHost(val c: Context, val onLaunch: (String) -> Unit, val onDetails: (String) -> Unit, val onBack: () -> Unit, val onSettings: () -> Unit) {
    @JavascriptInterface fun introDone() { android.os.Handler(android.os.Looper.getMainLooper()).post { ShelfIntro.done.value = true } }
    @JavascriptInterface
    fun games(): String {
        val hidden = hiddenSet(c)
        val times = runCatching { playTimes(c, 30) }.getOrDefault(emptyMap())
        val used = runCatching { usageOf(c, 30) }.getOrDefault(emptyMap())
        val arr = JSONArray()
        // the same rule as the library: a hidden game shows only while "show hidden games" is on (hiding itself stays Game Hub's: pm hide, shown for its start)
        val showHidden = prefs(c).getBoolean("showHidden", true)
        // in the mix the shelf is in colour groups (Steam, PS5, Switch, Xbox Series), each group in the order of the library
        loadGames(c).filter { showHidden || it.pkg !in hidden }.sortedBy { CaseCover.groupOf(c, it.pkg) }.forEach { g ->
            val hasCover = coverFile(c, g.pkg).isFile || caseFile(c, g.pkg).isFile || steamCoverFile(c, g.pkg).isFile || playCoverFile(c, g.pkg).isFile
            arr.put(JSONObject().put("pkg", g.pkg).put("name", g.name).put("hidden", g.pkg in hidden)
                .put("icon", "https://$HOST/icon/${g.pkg}")
                .put("cover", if (hasCover) "https://$HOST/cover/${g.pkg}" else JSONObject.NULL)
                .put("hero", if (steamHeroFile(c, g.pkg).isFile) "https://$HOST/hero/${g.pkg}" else JSONObject.NULL)
                // the disc inside the case is the game's wide picture, so it is not the same picture as the front cover
                .put("disc", if (steamHeroFile(c, g.pkg).isFile) "https://$HOST/hero/${g.pkg}" else JSONObject.NULL)
                // the Steam screenshots for the back of the box (up to four; none until the covers were looked for)
                // the box style the game's box is made in: the spine says its console (Steam: the shelf's own PC spine)
                .put("box", CaseCover.styleOf(c, g.pkg))
                .put("shots", JSONArray((0 until 4).filter { steamShotFile(c, g.pkg, it).isFile }.map { "https://$HOST/shot/${g.pkg}/$it" }))
                .put("playMs", times[g.pkg] ?: 0L).put("lastUsed", used[g.pkg]?.lastTimeUsed ?: 0L))
        }
        return arr.toString()
    }
    /** The case style of the shelf: the id of one of Boxshelf's own cases (pc = a Steam-like case, custom, ps5, ps4, x360, sw ...). */
    /** The guide kept for a game (empty until it was read), and the start of reading it in the background. */
    @JavascriptInterface fun guideCached(pkg: String): String = GameGuide.cached(c, pkg)
    @JavascriptInterface fun guideFetch(pkg: String) { GameGuide.fetchAsync(c, pkg, loadGames(c).firstOrNull { it.pkg == pkg }?.name ?: pkg) }
    /** Pure black screen (the AMOLED setting; by default on when Pulse's own look is AMOLED). */
    @JavascriptInterface fun amoled(): Boolean = prefs(c).getBoolean("amoledShelf", com.umutk.pulse.Look.theme.id == "amoled")
    @JavascriptInterface fun caseStyle(): String = prefs(c).getString("caseStyle", "pc") ?: "pc"
    // Game Hub's own settings, read and written from Boxshelf's Settings page: only these keys, each in the type and with the
    // default the Game Hub page uses (the same preferences, so both pages show the same values)
    @JavascriptInterface fun ghGet(key: String): String {
        val p = prefs(c)
        return when (key) {
            "caseStyle" -> p.getString(key, "pc") ?: "pc"
            "sgdbKey" -> p.getString(key, "") ?: ""
            "grid", "view", "iconMode" -> p.getInt(key, 0).toString()
            "start" -> p.getInt(key, 1).toString()
            "showHidden" -> p.getBoolean(key, true).toString()
            "playCovers" -> p.getBoolean(key, false).toString()
            "boxStyle" -> CaseCover.style(c)
            "menu" -> GameMenuService.enabled(c).toString()
            "coversRunning" -> CoverJob.running.value.toString()
            "theme" -> p.getString(key, "shelf") ?: "shelf"
            "access" -> Privilege.mode.name
            else -> ""
        }
    }
    @JavascriptInterface fun ghSet(key: String, value: String) {
        // the box style applies to every game's box (made again in the background); the menu asks for its permissions first
        if (key == "boxStyle") {
            if (value in CaseCover.STYLES) { CaseCover.setStyle(c, value); val pkgs = loadGames(c).map { it.pkg }; Thread { CaseCover.applyAll(c, pkgs, value); ShelfSync.bump() }.start() }
            return
        }
        if (key == "menu") { setMenu(value == "true"); return }
        if (key == "theme") { if (value in THEMES) prefs(c).edit().putString("theme", value).apply(); return }
        val e = prefs(c).edit()
        when (key) {
            "caseStyle", "sgdbKey" -> e.putString(key, value)
            "grid", "view", "iconMode", "start" -> e.putInt(key, value.toIntOrNull() ?: 0)
            "showHidden", "playCovers" -> e.putBoolean(key, value == "true")
            else -> return
        }
        e.apply()
    }
    private fun setMenu(on: Boolean) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (on && !Compat.canOverlay(c)) {
                runCatching { c.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + c.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return@post
            }
            if (on && !usageAllowed(c)) {
                runCatching { c.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return@post
            }
            prefs(c).edit().putBoolean("menu", on).apply()
            if (on) GameMenuService.start(c) else GameMenuService.stop(c)
        }
    }
    /** Game Hub's cover search, started and stopped from Boxshelf's Settings page (the key is needed first, as on the Game Hub page). */
    @JavascriptInterface fun ghRun(action: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            when (action) {
                "lookCovers" -> if (sgdbKey(c).isNotEmpty()) CoverJob.start(c, loadGames(c))
                "stopCovers" -> CoverJob.cancel()
                "checkAccess" -> Thread { Privilege.detect() }.start()
                // "Add a game" of the shelf's own settings: Game Hub's list of the phone's apps opens
                "add" -> ShelfAsk.add.value = true
            }
        }
    }
    /** Looks for covers of [query] (the game's name, or what was typed) and hands the list to the page (window.__pulseCovers). */
    @JavascriptInterface fun coverSearch(pkg: String, query: String) {
        Thread {
            val list = runCatching { coverCandidates(c, query.ifBlank { loadGames(c).firstOrNull { it.pkg == pkg }?.name ?: pkg }) }.getOrDefault(org.json.JSONArray())
            ShelfJs.run("window.__pulseCovers&&window.__pulseCovers(" + JSONObject.quote(pkg) + "," + list.toString() + ")")
        }.start()
    }
    /** The cover picked on the page: downloaded, made the game's cover, and the shelf is drawn again. */
    @JavascriptInterface fun coverSet(pkg: String, url: String) {
        Thread { if (setChosenCover(c, pkg, url)) ShelfSync.bump() else ShelfJs.run("window.__pulseCoverFail&&window.__pulseCoverFail()") }.start()
    }
    @JavascriptInterface fun launch(pkg: String) { android.os.Handler(android.os.Looper.getMainLooper()).post { onLaunch(pkg) } }
    @JavascriptInterface fun details(pkg: String) { android.os.Handler(android.os.Looper.getMainLooper()).post { onDetails(pkg) } }
    /** The Settings row of the shelf's menu: Game Hub's own settings (theme, case style, setup). */
    @JavascriptInterface fun settings() { android.os.Handler(android.os.Looper.getMainLooper()).post { onSettings() } }
    @JavascriptInterface fun back() { android.os.Handler(android.os.Looper.getMainLooper()).post { onBack() } }
}

/** A line of script for the shelf's page, from any thread (the answer of a search that ran in the background). */
internal object ShelfJs {
    @Volatile var web: WebView? = null
    fun run(js: String) { android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { web?.evaluateJavascript(js, null) } } }
}

/** What the shelf's page asks Game Hub to open (its own dialogs): the list of apps to add a game from. */
internal object ShelfAsk { val add = androidx.compose.runtime.mutableStateOf(false) }

/** The WebView of the shelf. [onLaunch] starts a game, [onDetails] opens Pulse's page for it. Back goes one step back inside the shelf (Esc), as Boxshelf does. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BoxshelfWeb(modifier: Modifier = Modifier, onLaunch: (String) -> Unit, onDetails: (String) -> Unit, onExit: () -> Unit, onSettings: () -> Unit = {}) {
    val ctx = LocalContext.current
    // each time the shelf opens, its opening animation comes first: the + waits for the page to say it ended (or a few seconds)
    androidx.compose.runtime.DisposableEffect(Unit) { ShelfIntro.done.value = false; onDispose { ShelfIntro.done.value = false } }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); ShelfIntro.done.value = true }
    val launch by rememberUpdatedState(onLaunch)
    val details by rememberUpdatedState(onDetails)
    val exit by rememberUpdatedState(onExit)
    val settings by rememberUpdatedState(onSettings)
    // Back on the bare shelf leaves Game Hub itself (to whatever was before it): the shelf's own Back handler steps aside first, then the system's goes on
    var leaving by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val holder = remember { arrayOfNulls<WebView>(1) }
    androidx.compose.runtime.LaunchedEffect(Unit) { com.umutk.pulse.extras.launcher.LauncherSounds.preloadAlways(ctx, "win11") }
    val owner = LocalLifecycleOwner.current
    // the shelf is told when Pulse is in front again (a game has closed), and rests while it is away
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) holder[0]?.onPause()
            else if (e == Lifecycle.Event.ON_RESUME) {
                val w = holder[0] ?: return@LifecycleEventObserver
                w.onResume()
                // the page's return step (the shelf comes back) runs once the page is running again: its timers and frames were stopped while it was away
                w.evaluateJavascript("window.__pulseExited&&window.__pulseExited()", null)
                // back from a game the picture can stay black until something redraws it (a touch did): the layer is reset, and the view is drawn
                // again several times while the window comes back (the first draws can land before it is in front), the page nudged to paint too
                w.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                w.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                w.invalidate(); w.requestLayout()
                val h = android.os.Handler(android.os.Looper.getMainLooper())
                for (d in longArrayOf(100L, 250L, 500L, 900L, 1500L, 2300L)) h.postDelayed({
                    if (holder[0] !== w) return@postDelayed
                    w.invalidate()
                    w.evaluateJavascript("document.documentElement.style.opacity='.999';requestAnimationFrame(function(){document.documentElement.style.opacity=''})", null)
                    if (d == 250L || d == 900L) { w.visibility = android.view.View.INVISIBLE; h.postDelayed({ if (holder[0] === w) w.visibility = android.view.View.VISIBLE }, 40L) }
                }, d)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); if (ShelfJs.web === holder[0]) ShelfJs.web = null; holder[0]?.destroy(); holder[0] = null }
    }
    // Back: Esc into the page; the page decides (it closes a panel, a case, a menu); at the top it asks Pulse to leave
    BackHandler(enabled = !leaving) { holder[0]?.evaluateJavascript("window.__pulseBack&&window.__pulseBack()", null) }
    AndroidView(modifier = modifier.fillMaxSize(), factory = { c ->
        WebView(c).also { w ->
            holder[0] = w
            ShelfJs.web = w
            w.setBackgroundColor(android.graphics.Color.BLACK)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.mediaPlaybackRequiresUserGesture = false
            // the page is laid out for its own sizes: the phone's font size setting must not make the writing bigger than the boxes
            w.settings.textZoom = 100
            // a long press selects nothing (the blue selection) and opens no system menu
            w.isLongClickable = false
            w.setOnLongClickListener { true }
            w.isHapticFeedbackEnabled = false
            w.settings.allowFileAccess = false
            w.settings.allowContentAccess = false
            w.addJavascriptInterface(PulseHost(c.applicationContext, { launch(it) }, { details(it) }, { exit(); leaving = true; android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ (ctx as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() }, 120L) }, { settings() }), "PulseHost")
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = serve(c, request.url)
            }
            w.loadUrl("https://$HOST/app/index.html?shelf=1&platform=android")
        }
    })
}
