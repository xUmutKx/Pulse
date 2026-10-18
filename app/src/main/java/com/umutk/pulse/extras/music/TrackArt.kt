package com.umutk.pulse.extras.music

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private object ArtCache {
    val map = LruCache<String, ImageBitmap>(48)
    val none = HashSet<String>() // tracks known to have no cover, so lists do not reopen the file each frame
}

/** Embedded cover of [track] (cached, at most about 512 px), or null. Reads the file: call from a composable's remember, not per frame. */
fun trackArt(ctx: Context, track: Track): ImageBitmap? {
    val key = track.path
    ArtCache.map.get(key)?.let { return it }
    if (synchronized(ArtCache.none) { key in ArtCache.none }) return null
    val bmp = try {
        val r = MediaMetadataRetriever()
        try {
            if (key.startsWith("http")) return null
            if (key.contains("://")) r.setDataSource(ctx, Uri.parse(key)) else r.setDataSource(key)
            r.embeddedPicture?.let { b ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(b, 0, b.size, o)
                var s = 1
                while (o.outWidth / (s * 2) >= 512) s *= 2
                BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
            }
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        null
    }?.asImageBitmap()
    if (bmp != null) ArtCache.map.put(key, bmp) else synchronized(ArtCache.none) { ArtCache.none += key }
    return bmp
}
