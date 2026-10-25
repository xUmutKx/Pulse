package com.umutk.pulse.extras.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Canvas: a short looping video of a song that plays in the cover's place, like Spotify's. The video is one the user picks for the song
 * (a file on the phone); nothing is downloaded from any service.
 */
object TrackCanvas {
    private fun sp(c: Context) = c.getSharedPreferences("track_canvas", Context.MODE_PRIVATE)

    /** Bumped on every change so the covers pick the new video up at once. */
    val version = mutableIntStateOf(0)

    fun get(c: Context, key: String): String? = sp(c).getString(key, null)

    fun set(c: Context, key: String, uri: String?) {
        sp(c).edit().apply { if (uri == null) remove(key) else putString(key, uri) }.apply()
        version.intValue++
    }

    /** Keeps the read permission of a picked file across restarts. */
    fun keep(c: Context, uri: Uri) { runCatching { c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
}

/** The song's canvas, filling its box (cropped to fit, muted, looping); nothing is drawn when the song has none. */
@Composable
fun CanvasVideo(key: String?, playing: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val v by TrackCanvas.version
    val uri = remember(key, v) { key?.let { TrackCanvas.get(ctx, it) } } ?: return
    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { c ->
            FrameLayout(c).apply {
                clipChildren = true
                val vv = VideoView(c)
                addView(vv, FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
                vv.setOnPreparedListener { mp ->
                    mp.isLooping = true; mp.setVolume(0f, 0f)
                    // the video is scaled up until it covers the whole box
                    vv.post {
                        val pw = width.toFloat(); val ph = height.toFloat()
                        val vw = vv.width.toFloat().coerceAtLeast(1f); val vh = vv.height.toFloat().coerceAtLeast(1f)
                        val s = maxOf(pw / vw, ph / vh)
                        vv.scaleX = s; vv.scaleY = s
                    }
                }
                vv.setOnErrorListener { _, _, _ -> true }
                vv.setVideoURI(Uri.parse(uri))
                vv.start()
                tag = vv
            }
        },
        update = { f -> (f.tag as? VideoView)?.let { if (playing) { if (!it.isPlaying) it.start() } else if (it.isPlaying) it.pause() } },
    )
}
