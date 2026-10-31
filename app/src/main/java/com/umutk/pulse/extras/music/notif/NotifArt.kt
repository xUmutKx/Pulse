package com.umutk.pulse.extras.music.notif

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.trackArt

/**
 * The cover of the media notification and the lock screen. A cover the user picked, the one inside the file or one found online wins;
 * only a song without any falls back to the system's album art (the artwork uri).
 */
@OptIn(UnstableApi::class)
class NotifArt(private val ctx: Context) : BitmapLoader {
    private val base = DataSourceBitmapLoader(ctx)

    override fun supportsMimeType(mimeType: String) = base.supportsMimeType(mimeType)
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = base.decodeBitmap(data)
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = base.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val path = metadata.extras?.getString(KEY_PATH)
        // an internet radio station has no cover: a drawn radio picture instead of the grey placeholder
        if (path != null && path.startsWith("http")) return SettableFuture.create<Bitmap>().also { it.set(GeneratedArt.make(GeneratedArt.Kind.RADIO)) }
        if (path == null) return base.loadBitmapFromMetadata(metadata)
        val f = SettableFuture.create<Bitmap>()
        Thread {
            val own = runCatching { trackArt(ctx, Track(path, metadata.title?.toString(), artist = metadata.artist?.toString(), album = metadata.albumTitle?.toString()))?.asAndroidBitmap() }.getOrNull()
            if (own != null) f.set(own)
            else {
                val fb = base.loadBitmapFromMetadata(metadata)
                if (fb == null) f.setException(IllegalStateException("no cover")) else f.setFuture(fb)
            }
        }.start()
        return f
    }

    companion object { const val KEY_PATH = "pulse_path" }
}
