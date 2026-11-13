package com.umutk.pulse.extras.gamehub

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/*
 * The built-in games of Game Hub: fifty of the games of Mega Games (the author's own collection, one HTML page with three.js; about 1 MB in the app). Each is
 * a game of the shelf like any other and opens full screen in its own window; leaving the game comes back to the shelf. One switch in the shelf's
 * settings takes them off the shelf.
 */
internal object MegaGames {
    /** A built-in game: its id inside the page, its name, and whether it is one of the big ones (they start another way). */
    class Item(val id: String, val name: String, val big: Boolean = false)

    val LIST = listOf(
        Item("blade", "Blade Arena", true), Item("nebula", "Nebula Voyager", true), Item("voxel", "VoxelCraft 3D", true), Item("calradia", "Calradia: Warband Conquest", true),
        Item("drift3d", "CyberDrift Highway", true), Item("spellmerge", "SpellMerge Dungeon", true), Item("fighter", "Neon Fighter 2D", true), Item("horde", "Horde Survivors", true),
        Item("bastion", "Bastion Defense", true), Item("dungeon", "Dungeon Delvers", true),
        Item("2048", "2048 Neon"), Item("tetris", "Tetris"), Item("sudoku", "Sudoku Mini"), Item("minesweeper", "Minesweeper"), Item("watersort", "Water Sort"), Item("sokoban", "Sokoban"),
        Item("checkers", "Checkers"), Item("othello", "Othello"), Item("connect4", "Connect 4"), Item("battleship", "Battleship"), Item("bubble", "Bubble Shooter"), Item("samegame", "Same Game"),
        Item("pipes", "Pipe Mania"), Item("td", "Turret Defense"), Item("village", "Pixel Village"), Item("colony", "Moon Colony"), Item("p3empire", "Empire"), Item("p3roads", "Road City"),
        Item("idle", "Idle Tycoon"), Item("p14_idle_cookie", "Cookie Factory"), Item("farm", "Cyber Farm"), Item("factory", "Factory Line"), Item("blockcraft", "Block Miner"),
        Item("snake", "Cyber Snake"), Item("breakout", "Neon Breakout"), Item("pac", "Pellet Chase"), Item("pinball", "Neon Pinball"), Item("doodle", "Doodle Jump"), Item("hillclimb", "Hill Climb"),
        Item("golf", "Mini Golf"), Item("pool", "8-Ball Pool"), Item("bowling", "Strike Bowling"), Item("racing", "Turbo Racer"), Item("drift", "Neon Drift"), Item("invaders", "Space Invaders"),
        Item("asteroid", "Asteroid Miner"), Item("tank", "Tank Blitz"), Item("p4fps", "Neon Arena FPS"), Item("p4marble", "Marble Run 3D"), Item("runner3d", "3D Metro Dash"),
    )
    const val PREFIX = "mega:"

    /** On the shelf unless switched off in the shelf's settings. */
    fun enabled(c: Context): Boolean = prefs(c).getBoolean("miniGames", true)

    fun isMega(pkg: String) = pkg.startsWith(PREFIX)

    fun open(c: Context, pkg: String) {
        val it = LIST.firstOrNull { PREFIX + it.id == pkg } ?: return
        runCatching { c.startActivity(Intent(c, MegaGameActivity::class.java).putExtra("id", it.id).putExtra("big", it.big).apply { if (c !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    }
}

/** One built-in game, full screen. The page is the whole collection; the chosen game is started in it, and the page's own list is never shown. */
class MegaGameActivity : Activity() {
    private var web: WebView? = null
    private var started = false
    private val host = "mega.pulse.local"

    inner class Bridge {
        /** The page went back to its own list (the game was left): this window closes, the shelf is under it. */
        @JavascriptInterface fun hubShown() { runOnUiThread { if (started) finish() } }
        @JavascriptInterface fun vibrate(ms: Int) {
            runCatching {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                val d = ms.coerceIn(1, 2000).toLong()
                if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(d, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(d)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra("id") ?: run { finish(); return }
        val big = intent.getBooleanExtra("big", false)
        val w = WebView(this)
        web = w
        w.setBackgroundColor(Color.BLACK)
        w.alpha = 0f
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.textZoom = 100
        w.settings.allowFileAccess = false
        w.isLongClickable = false
        w.setOnLongClickListener { true }
        w.addJavascriptInterface(Bridge(), "AndroidBridge")
        w.webViewClient = object : WebViewClient() {
            // the page and three.js come from the app itself, under a name of their own (so the games keep their saves between starts)
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if (u.host != host) return null
                val name = u.lastPathSegment ?: return null
                if (name != "mega.html" && name != "three.min.js") return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                return runCatching { WebResourceResponse(if (name.endsWith(".js")) "application/javascript" else "text/html", "utf-8", assets.open("mega/$name")) }.getOrNull()
            }
            override fun onPageFinished(view: WebView, url: String?) { launch(id, big, 0) }
        }
        setContentView(w, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        immersive()
        w.loadUrl("https://$host/mega.html")
    }

    /** Starts the game once the page has its games ready (asked again a few times while it loads). */
    private fun launch(id: String, big: Boolean, n: Int) {
        val w = web ?: return
        if (started || n > 60) return
        val q = JSONObject.quote(id)
        val js = "(function(){try{if(typeof MINIS==='undefined'||typeof launchAAA!=='function')return 'wait';" +
            (if (big) "launchAAA($q);" else "var g=MINIS.find(function(m){return m.id===$q});if(!g)return 'none';hubOpen(g);") + "return 'ok'}catch(e){return 'err'}})()"
        w.evaluateJavascript(js) { r ->
            when {
                r != null && r.contains("ok") -> { w.postDelayed({ started = true; w.animate().alpha(1f).setDuration(150).start() }, 80) }
                r != null && r.contains("wait") -> w.postDelayed({ launch(id, big, n + 1) }, 150)
                else -> finish()
            }
        }
    }

    private fun immersive() {
        runCatching {
            val c = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onPause() { super.onPause(); web?.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; web = null; super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back leaves the game (the page closes it, which closes this window); when the page does not answer the window closes itself
        val w = web ?: run { finish(); return }
        w.evaluateJavascript("(function(){try{var h=document.getElementById('hubScreen');if(h&&h.classList.contains('hidden')&&typeof goHub==='function'){goHub();return 'back'}}catch(e){}return 'none'})()") { finish() }
    }
}
