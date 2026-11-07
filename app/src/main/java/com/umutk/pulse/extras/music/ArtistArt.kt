package com.umutk.pulse.extras.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File

/** Pictures of artists (named "Artist.jpg" in Poweramp's artist_art folder): kept by the artist's name, shown in the artists list. */
object ArtistArt {
    /** A list row never shows more than this, so a bigger picture only takes space on the phone. */
    private const val MAX_SIDE = 512
    /** Files at or under this size are already small and are left alone by the one-time shrink. */
    private const val SMALL_FILE = 150_000L

    private fun key(name: String) = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }
    private fun folder(c: Context) = File(c.filesDir, "artist_art")
    private fun file(c: Context, name: String) = File(folder(c), key(name).ifEmpty { "x" } + ".jpg")

    /** The picture scaled so its longest side is at most [MAX_SIDE] px, as a JPEG; the original bytes when they cannot be decoded. */
    private fun small(bytes: ByteArray): ByteArray {
        return runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var ss = 1; while (maxOf(o.outWidth, o.outHeight) / ss > MAX_SIDE * 2) ss *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = ss })
            if (bmp == null) bytes else {
                val k = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
                val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
                ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
            }
        }.getOrDefault(bytes)
    }

    /** Decoded pictures kept in memory: a row that scrolls away and back shows its picture at once instead of decoding it again. */
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 160
    }

    fun save(c: Context, name: String, bytes: ByteArray) {
        val f = file(c, name); f.parentFile?.mkdirs(); f.writeBytes(small(bytes))
        synchronized(memory) { memory.remove(key(name).ifEmpty { "x" }) }
    }

    /** Pictures saved before the size rule are scaled down once, in place; a file that does not get smaller is kept. */
    fun shrinkStored(c: Context) {
        folder(c).listFiles()?.forEach { f ->
            if (f.length() <= SMALL_FILE) return@forEach
            runCatching {
                val shrunk = small(f.readBytes())
                if (shrunk.size < f.length()) {
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    tmp.writeBytes(shrunk)
                    if (!tmp.renameTo(f)) tmp.delete()
                }
            }
        }
    }

    /** The picture of [name], decoded small enough for a list row, or null. */
    fun load(c: Context, name: String): ImageBitmap? {
        val k = key(name).ifEmpty { "x" }
        synchronized(memory) { memory[k] }?.let { return it }
        val bmp = file(c, name).takeIf { it.isFile }?.let { f ->
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                var ss = 1; while (o.outWidth / ss > 400) ss *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss })?.asImageBitmap()
            }.getOrNull()
        }
        if (bmp != null) synchronized(memory) { memory[k] = bmp }
        return bmp
    }
}
