package com.umutk.pulse.extras.gamehub

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
                val f = coverFile(c, pkg).takeIf { it.isFile } ?: steamCoverFile(c, pkg).takeIf { it.isFile } ?: playCoverFile(c, pkg).takeIf { it.isFile } ?: return null
                WebResourceResponse("image/jpeg", null, FileInputStream(f))
            }
            "hero" -> {
                val pkg = parts.getOrNull(1) ?: return null
                WebResourceResponse("image/jpeg", null, FileInputStream(steamHeroFile(c, pkg).takeIf { it.isFile } ?: return null))
            }
            else -> null
        }
    }.getOrNull()
}

/** What the page can ask Pulse for. */
private class PulseHost(val c: Context, val onLaunch: (String) -> Unit, val onDetails: (String) -> Unit, val onBack: () -> Unit, val onSettings: () -> Unit) {
    @JavascriptInterface
    fun games(): String {
        val hidden = hiddenSet(c)
        val times = runCatching { playTimes(c, 30) }.getOrDefault(emptyMap())
        val used = runCatching { usageOf(c, 30) }.getOrDefault(emptyMap())
        val arr = JSONArray()
        // the same rule as the library: a hidden game shows only while "show hidden games" is on (hiding itself stays Game Hub's: pm hide, shown for its start)
        val showHidden = prefs(c).getBoolean("showHidden", true)
        loadGames(c).filter { showHidden || it.pkg !in hidden }.forEach { g ->
            val hasCover = coverFile(c, g.pkg).isFile || steamCoverFile(c, g.pkg).isFile || playCoverFile(c, g.pkg).isFile
            arr.put(JSONObject().put("pkg", g.pkg).put("name", g.name).put("hidden", g.pkg in hidden)
                .put("icon", "https://$HOST/icon/${g.pkg}")
                .put("cover", if (hasCover) "https://$HOST/cover/${g.pkg}" else JSONObject.NULL)
                .put("hero", if (steamHeroFile(c, g.pkg).isFile) "https://$HOST/hero/${g.pkg}" else JSONObject.NULL)
                .put("playMs", times[g.pkg] ?: 0L).put("lastUsed", used[g.pkg]?.lastTimeUsed ?: 0L))
        }
        return arr.toString()
    }
    /** The case style of the shelf: the id of one of Boxshelf's own cases (pc = a Steam-like case, custom, ps5, ps4, x360, sw ...). */
    /** The guide kept for a game (empty until it was read), and the start of reading it in the background. */
    @JavascriptInterface fun guideCached(pkg: String): String = GameGuide.cached(c, pkg)
    @JavascriptInterface fun guideFetch(pkg: String) { GameGuide.fetchAsync(c, pkg, loadGames(c).firstOrNull { it.pkg == pkg }?.name ?: pkg) }
    @JavascriptInterface fun caseStyle(): String = prefs(c).getString("caseStyle", "pc") ?: "pc"
    @JavascriptInterface fun launch(pkg: String) { android.os.Handler(android.os.Looper.getMainLooper()).post { onLaunch(pkg) } }
    @JavascriptInterface fun details(pkg: String) { android.os.Handler(android.os.Looper.getMainLooper()).post { onDetails(pkg) } }
    /** The Settings row of the shelf's menu: Game Hub's own settings (theme, case style, setup). */
    @JavascriptInterface fun settings() { android.os.Handler(android.os.Looper.getMainLooper()).post { onSettings() } }
    @JavascriptInterface fun back() { android.os.Handler(android.os.Looper.getMainLooper()).post { onBack() } }
}

/** The WebView of the shelf. [onLaunch] starts a game, [onDetails] opens Pulse's page for it. Back goes one step back inside the shelf (Esc), as Boxshelf does. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BoxshelfWeb(modifier: Modifier = Modifier, onLaunch: (String) -> Unit, onDetails: (String) -> Unit, onExit: () -> Unit, onSettings: () -> Unit = {}) {
    val ctx = LocalContext.current
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
            if (e == Lifecycle.Event.ON_RESUME) holder[0]?.evaluateJavascript("window.__pulseExited&&window.__pulseExited()", null)
            if (e == Lifecycle.Event.ON_PAUSE) holder[0]?.onPause() else if (e == Lifecycle.Event.ON_RESUME) holder[0]?.onResume()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); holder[0]?.destroy(); holder[0] = null }
    }
    // Back: Esc into the page; the page decides (it closes a panel, a case, a menu); at the top it asks Pulse to leave
    BackHandler(enabled = !leaving) { holder[0]?.evaluateJavascript("window.__pulseBack&&window.__pulseBack()", null) }
    AndroidView(modifier = modifier.fillMaxSize(), factory = { c ->
        WebView(c).also { w ->
            holder[0] = w
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
