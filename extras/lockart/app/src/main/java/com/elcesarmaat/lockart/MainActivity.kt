package com.elcesarmaat.lockart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ajustes tal como los ve la interfaz; Config es la copia que se guarda y se manda a SystemUI. */
data class Settings(
    val enabled: Boolean = true,
    val blur: Int = 0,
    val dim: Int = 25,
    val autoVeil: Boolean = true,
    val keepPaused: Boolean = false,
    val lyrics: Boolean = true,
    val musicMode: Boolean = true,
    val aodLyrics: Boolean = false,
    val aodToggle: Boolean = true,
    val lyricsAnim: Int = LyricsView.ANIM_SCROLL,
) {
    fun into(c: Config) {
        c.enabled = enabled
        c.blur = blur
        c.dim = dim
        c.autoVeil = autoVeil
        c.keepPaused = keepPaused
        c.lyrics = lyrics
        c.musicMode = musicMode
        c.aodLyrics = aodLyrics
        c.aodToggle = aodToggle
        c.lyricsAnim = lyricsAnim
    }

    companion object {
        fun from(c: Config) = Settings(c.enabled, c.blur, c.dim, c.autoVeil, c.keepPaused, c.lyrics, c.musicMode, c.aodLyrics, c.aodToggle, c.lyricsAnim)
    }
}

/** Estado del gancho en SystemUI según su última respuesta. */
sealed interface Link {
    data object Waiting : Link
    data object Off : Link
    data class On(val attached: Boolean, val sessions: Int) : Link
}

data class NowPlaying(
    val art: Bitmap? = null,
    val app: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val veilLight: Boolean = false,
    /** Líneas de la letra que SystemUI tiene para la canción actual (0 = sin letra). */
    val lyricLines: Int = 0,
)

class MainActivity : ComponentActivity() {

    private val cfg = Config()
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var settings by mutableStateOf(Settings())
    private var link by mutableStateOf<Link>(Link.Waiting)
    private var playing by mutableStateOf(NowPlaying())
    private var framework by mutableStateOf<String?>(null)
    /** null = sin servicio de LSPosed; si es false, la app ofrece pedir el ámbito de Spotify. */
    private var spotifyScoped by mutableStateOf<Boolean?>(null)

    private val noAnswer = Runnable { link = Link.Off }
    private val sendPing = Runnable { sendPing() }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onStatus(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        cfg.load(prefs)
        settings = Settings.from(cfg)
        lifecycleScope.launch {
            Xposed.service.collect { service ->
                framework = service?.let { "${it.frameworkName} ${it.frameworkVersion} · API ${it.apiVersion}" }
                spotifyScoped = service?.let { s -> runCatching { SpotifyLyrics.PACKAGE in s.scope }.getOrNull() }
                // Al conectar se suben los ajustes locales, por si se cambiaron con el módulo inactivo.
                service?.let(::pushRemote)
            }
        }
        setContent {
            LockArtTheme {
                SettingsScreen(
                    settings = settings,
                    link = link,
                    playing = playing,
                    framework = framework,
                    spotifyScoped = spotifyScoped,
                    onRequestSpotify = ::requestSpotifyScope,
                    onChange = ::update,
                    onRestart = ::restartSystemUi,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Solo SystemUI (que tiene STATUS_BAR_SERVICE) puede mandar el estado.
        registerReceiver(
            statusReceiver, IntentFilter(Config.ACTION_STATUS),
            "android.permission.STATUS_BAR_SERVICE", handler, RECEIVER_EXPORTED,
        )
        sendPing()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
        handler.removeCallbacks(noAnswer)
    }

    private fun update(s: Settings) {
        settings = s
        s.into(cfg)
        cfg.save(prefs)
        Xposed.service.value?.let(::pushRemote)
    }

    /**
     * Los ajustes van a las preferencias remotas de LSPosed; SystemUI escucha sus cambios y responde con su estado,
     * así que no hace falta mandarlos por broadcast.
     */
    private fun pushRemote(service: XposedService) {
        runCatching { cfg.save(service.getRemotePreferences(Config.PREFS)) }
            .onFailure { Log.w("LockArt", "No se pudieron guardar los ajustes remotos", it) }
    }

    /** SystemUI responde con ACTION_STATUS, así se sabe si el gancho está cargado. */
    private fun sendPing() {
        sendBroadcast(Intent(Config.ACTION_PING).setPackage("com.android.systemui"))
        handler.removeCallbacks(noAnswer)
        handler.postDelayed(noAnswer, ANSWER_TIMEOUT_MS)
    }

    private fun onStatus(i: Intent) {
        handler.removeCallbacks(noAnswer)
        link = Link.On(i.getBooleanExtra("attached", false), i.getIntExtra("sessions", 0))
        val thumb = i.getByteArrayExtra("thumb")
        playing = NowPlaying(
            art = thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size) },
            app = i.getStringExtra("pkg")?.let(::appLabel),
            title = i.getStringExtra("title"),
            artist = i.getStringExtra("artist"),
            veilLight = i.getBooleanExtra("veil_light", false),
            lyricLines = i.getIntExtra("lyrics_lines", 0),
        )
    }

    /** LSPosed muestra la petición; al aprobarla el módulo carga en Spotify la próxima vez que se abra. */
    private fun requestSpotifyScope() {
        val service = Xposed.service.value ?: return
        runCatching {
            service.requestScope(listOf(SpotifyLyrics.PACKAGE), object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) = runOnUiThread {
                    spotifyScoped = SpotifyLyrics.PACKAGE in approved || spotifyScoped == true
                    Toast.makeText(this@MainActivity, R.string.scope_approved, Toast.LENGTH_LONG).show()
                }

                override fun onScopeRequestFailed(message: String) = runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.scope_failed, message), Toast.LENGTH_LONG).show()
                }
            })
        }.onFailure { Log.w("LockArt", "No se pudo pedir el ámbito de Spotify", it) }
    }

    private suspend fun restartSystemUi(): Boolean {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "killall com.android.systemui")).waitFor() == 0
            }.getOrDefault(false)
        }
        if (ok) {
            link = Link.Waiting
            handler.postDelayed(sendPing, RESTART_WAIT_MS)
        }
        return ok
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val ANSWER_TIMEOUT_MS = 1500L
        const val RESTART_WAIT_MS = 5000L
    }
}
