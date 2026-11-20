package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Your own sticker packs (as in WM Keyboard): the pictures in Download/Pulse/Stickers and Pictures/Stickers, a folder for each pack; a tap sends one as a picture. */
object KbStickers {
    private val EXT = setOf("png", "webp", "gif", "jpg", "jpeg")

    fun files(c: Context): List<File> {
        val roots = listOf(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/Stickers"), File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Stickers"))
        // the folder is made, so there is a place to put them
        runCatching { roots[0].mkdirs() }
        return roots.flatMap { r -> r.walkTopDown().maxDepth(3).filter { it.isFile && it.extension.lowercase() in EXT }.toList() }.sortedBy { it.path.lowercase() }.take(400)
    }

    private val cache = object : android.util.LruCache<String, ImageBitmap>(48) {}

    fun thumb(f: File): ImageBitmap? {
        cache.get(f.path)?.let { return it }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var k = 1; while (o.outWidth / (k * 2) >= 160 && o.outHeight / (k * 2) >= 160) k *= 2
        val b = runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = k }) }.getOrNull() ?: return null
        return b.asImageBitmap().also { cache.put(f.path, it) }
    }
}
