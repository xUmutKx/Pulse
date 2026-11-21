package com.umutk.pulse.extras.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** A picture of the person's own for one app or file: it is drawn in place of the app's icon (and of the icon pack's) everywhere the launcher shows that app. */
object AppIcons {
    /** The app whose icon is being chosen (the picture picker opens for it). */
    var pick by mutableStateOf<LaunchApp?>(null)
    /** Changes when an icon is set or reset, so the drawn pictures read again. */
    var rev by mutableIntStateOf(0)
    private val cache = HashMap<String, ImageBitmap?>()

    private fun file(c: Context, pkg: String) = File(File(c.filesDir, "appicons"), pkg.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png")

    fun has(c: Context, pkg: String): Boolean = file(c, pkg).isFile

    /** The person's picture for [pkg], or null (read once, then kept until it changes). */
    fun bitmap(c: Context, pkg: String): ImageBitmap? = cache.getOrPut(pkg) {
        file(c, pkg).takeIf { it.isFile }?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }

    /** Takes the chosen picture, cut to a square and made 256 px, as the app's icon. */
    fun set(c: Context, pkg: String, uri: Uri) {
        runCatching {
            val src = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
            val side = minOf(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 256, 256, true)
            val f = file(c, pkg); f.parentFile?.mkdirs()
            f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cache.remove(pkg); rev++
        }
    }

    fun clear(c: Context, pkg: String) { file(c, pkg).delete(); cache.remove(pkg); rev++ }
}
