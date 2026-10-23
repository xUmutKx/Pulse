package com.umutk.pulse.extras.lyrics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.PIcon
import com.umutk.pulse.PLoader
import com.umutk.pulse.extras.music.MusicPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ctl = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ctl.savedStateRegistry
    fun start() { ctl.performRestore(Bundle()); reg.currentState = Lifecycle.State.RESUMED }
    fun stop() { reg.currentState = Lifecycle.State.DESTROYED }
}

private fun overlayAllowed(ctx: Context): Boolean {
    if (com.umutk.pulse.Compat.canOverlay(ctx)) return true
    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return false
}

private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

/** Draggable lyrics-by-text search in a window above other apps. Needs "display over other apps"; without it the settings screen opens. */
object FloatingLyricsSearch {
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun show(ctx: Context) {
        val app = ctx.applicationContext
        if (view != null || !overlayAllowed(app)) return
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = app.resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            (d.widthPixels * 0.92f).toInt(), (d.heightPixels * 0.55f).toInt(), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (d.widthPixels * 0.04f).toInt(); y = (d.heightPixels * 0.1f).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val o = OverlayOwner().also { it.start() }
        val v = ComposeView(app).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            // a window above other apps has no activity behind it, and the search screen's back handling asks for one: it gets a dispatcher of its own (without it the app crashed)
            val back = object : androidx.activity.OnBackPressedDispatcherOwner {
                override val lifecycle: Lifecycle get() = o.lifecycle
                override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
            }
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides back) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        FloatingBody(
                            onDrag = { dx, dy -> lp.x += dx.toInt(); lp.y += dy.toInt(); runCatching { wm.updateViewLayout(this, lp) } },
                            onClose = { hide(app) },
                        )
                    }
                }
            }
        }
        view = v; owner = o
        runCatching { wm.addView(v, lp) }.onFailure { view = null; o.stop(); owner = null }
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        runCatching { (ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        owner?.stop(); owner = null; view = null
    }
}

@Composable
private fun FloatingBody(onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<LyricHit?>(null) }
    var res by remember { mutableStateOf<LyricsResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        val h = open ?: return@LaunchedEffect
        busy = true; res = null
        res = Lyrics.forHit(ctx, h)
        busy = false
    }
    Column(Modifier.fillMaxSize().background(Color(0xE6121212))) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF202020)).pointerInput(Unit) { detectDragGestures { c, a -> c.consume(); onDrag(a.x, a.y) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open != null) IconButton(onClick = { open = null }) { PIcon("back", Color.White, 20.dp) }
            Box(Modifier.weight(1f))
            IconButton(onClick = onClose) { PIcon("close", Color.White, 20.dp) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val r = res
            when {
                open == null -> LyricsSearchScreen(onOpen = { open = it }, onClose = onClose)
                busy -> Box(Modifier.fillMaxSize(), Alignment.Center) { PLoader() }
                r != null -> LyricsView(r, -1, null, Modifier.fillMaxSize().padding(horizontal = 16.dp))
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { PIcon("book", Color.White.copy(alpha = .6f), 40.dp) }
            }
        }
    }
}

/** Gives [MediaSessionManager] a notification-listener component, which is what allows reading other players' sessions. Declared in the manifest, no logic. */
class LyricsListenerService : NotificationListenerService()

/**
 * One-line lyrics above other apps that follow whatever is playing. Other players are read through their media sessions,
 * which needs notification access for [LyricsListenerService]; without it only Pulse's own player is followed.
 * Lines come from LRCLIB/lyrics.ovh by title and artist (no local file path is known for other players).
 */
object MiniLyrics {
    private var tv: TextView? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val running get() = tv != null

    fun hasNotificationAccess(ctx: Context) =
        Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true

    fun openNotificationAccess(ctx: Context) =
        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (tv != null || !overlayAllowed(app)) return
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = app.resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = (d.heightPixels * 0.75f).toInt() }
        val pad = (12 * d.density).toInt()
        val t = TextView(app).apply {
            setTextColor(AColor.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            maxLines = 2; maxWidth = (d.widthPixels * 0.9f).toInt(); setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply { setColor(0xAA000000.toInt()); cornerRadius = 20 * d.density }
            visibility = View.GONE
            var dx = 0f; var dy = 0f
            setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx = lp.x - e.rawX; dy = lp.y - e.rawY }
                    MotionEvent.ACTION_MOVE -> { lp.x = (e.rawX + dx).toInt(); lp.y = (e.rawY + dy).toInt(); runCatching { wm.updateViewLayout(v, lp) } }
                }
                true
            }
        }
        if (runCatching { wm.addView(t, lp) }.isFailure) return
        tv = t
        job = scope.launch { follow(app, t) }
    }

    fun stop(ctx: Context) {
        job?.cancel(); job = null
        tv?.let { v -> runCatching { (ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } }
        tv = null
    }

    /** [path] is set for Pulse's own player: its file is read first (tag, .lrc / .txt). */
    private class Playing(val key: String, val title: String, val artist: String, val album: String?, val durationMs: Long, val posMs: Long, val path: String? = null)

    private fun fromSessions(ctx: Context): Playing? {
        val sm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list: List<MediaController> = try {
            sm.getActiveSessions(ComponentName(ctx, LyricsListenerService::class.java))
        } catch (e: SecurityException) { return null }
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING && it.packageName != ctx.packageName } ?: return null
        val m = c.metadata ?: return null
        val title = m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val ps = c.playbackState!!
        val pos = ps.position + ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
        return Playing("${c.packageName}|$artist|$title", title, artist, m.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM), m.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION), pos)
    }

    private fun fromOwnPlayer(ctx: Context): Playing? {
        val e = MusicPlayerEngine.get(ctx)
        val st = e.state.value
        val t = st.current ?: return null
        if (!st.isPlaying) return null
        val (a, ti) = guessArtistTitle(t)
        return Playing("own|${t.path}", t.title ?: ti, t.artist ?: a, t.album, t.durationSec * 1000L, e.positionMs.toLong(), t.path)
    }

    private suspend fun follow(ctx: Context, t: TextView) {
        var key = ""
        var res: LyricsResult? = null
        var lookup: Job? = null
        while (scope.isActive && tv === t) {
            val p = fromSessions(ctx) ?: fromOwnPlayer(ctx)
            if (p == null) t.visibility = View.GONE
            else {
                if (p.key != key) {
                    key = p.key; res = null; lookup?.cancel()
                    lookup = scope.launch { res = Lyrics.forTrack(ctx, p.title, p.artist, p.album, p.durationMs, p.path)?.takeIf { it.synced } }
                }
                val r = res
                val line = r?.lines?.lastOrNull { it.timeMs <= p.posMs }?.text?.takeIf { it.isNotBlank() }
                t.text = line.orEmpty()
                t.visibility = if (line != null) View.VISIBLE else View.GONE
            }
            delay(400)
        }
    }
}
