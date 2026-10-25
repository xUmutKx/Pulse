package com.umutk.pulse.extras.music

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Pictures of artists (named "Artist.jpg" in Poweramp's artist_art folder): kept by the artist's name, shown in the artists list. */
object ArtistArt {
    private fun key(name: String) = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }
    private fun file(c: Context, name: String) = File(File(c.filesDir, "artist_art"), key(name).ifEmpty { "x" } + ".jpg")

    fun save(c: Context, name: String, bytes: ByteArray) { val f = file(c, name); f.parentFile?.mkdirs(); f.writeBytes(bytes) }

    /** The picture of [name], decoded small enough for a list row, or null. */
    fun load(c: Context, name: String): ImageBitmap? = file(c, name).takeIf { it.isFile }?.let { f ->
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var ss = 1; while (o.outWidth / ss > 400) ss *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss })?.asImageBitmap()
        }.getOrNull()
    }
}
