package com.umutk.pulse.extras.ytx

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player


val LocalVideoSurface = androidx.compose.runtime.staticCompositionLocalOf<@Composable (Modifier) -> Unit> { { m -> VideoSurface(null, m) } }


val NoVideoSurface: @Composable (Modifier) -> Unit = {}











object SharedVideoTexture {
    var texture: SurfaceTexture? = null
        private set
    var surface: Surface? = null
        private set

    var version by mutableIntStateOf(0)
        private set

    fun adopt(view: TextureView, fresh: SurfaceTexture) {
        val kept = texture
        if (kept == null) {
            texture = fresh
            surface = Surface(fresh)
            version++
        } else if (kept !== fresh) {

            runCatching { view.setSurfaceTexture(kept) }
                .onFailure { Log.w("YTXTube", "couldn't re-attach the video texture", it) }
        }
    }
}





/** The faster video path: a SurfaceView goes straight to the display (as in the YouTube app); the TextureView path redraws the app window at every frame. */
object YtxPerf {
    var surfaceView by androidx.compose.runtime.mutableStateOf(true)
    fun load(c: android.content.Context) { surfaceView = c.getSharedPreferences("ytx_perf", 0).getBoolean("surfaceView", true) }
    fun set(c: android.content.Context, v: Boolean) { surfaceView = v; c.getSharedPreferences("ytx_perf", 0).edit().putBoolean("surfaceView", v).apply() }
}

@Composable
private fun SurfaceVideo(player: Player?, modifier: Modifier) {
    var view by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.view.SurfaceView?>(null) }
    AndroidView(modifier = modifier, factory = { ctx -> android.view.SurfaceView(ctx).also { view = it } })
    androidx.compose.runtime.DisposableEffect(player, view) {
        val v = view; val p = player
        if (v != null && p != null) runCatching { p.setVideoSurfaceView(v) }
        onDispose { if (v != null && p != null) runCatching { p.clearVideoSurfaceView(v) } }
    }
}

@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    if (YtxPerf.surfaceView && player != null) { SurfaceVideo(player, modifier); return }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {

                SharedVideoTexture.texture?.let { runCatching { setSurfaceTexture(it) } }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) = SharedVideoTexture.adopt(this@apply, st)
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = st !== SharedVideoTexture.texture
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = AvSync.onFrameDrawn()
                }
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {

                        val kept = SharedVideoTexture.texture ?: return
                        if (surfaceTexture !== kept) runCatching { setSurfaceTexture(kept) }
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        },
    )
    val version = SharedVideoTexture.version
    LaunchedEffect(player, version) {
        val surface = SharedVideoTexture.surface ?: return@LaunchedEffect
        player?.setVideoSurface(surface)
    }
}
