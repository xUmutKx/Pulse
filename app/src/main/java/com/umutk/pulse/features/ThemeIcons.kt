package com.umutk.pulse.features

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Per-theme icon packs from assets/icons/<theme>/<key>.png; null when the pack has no file for that key. */
object ThemeIcons {
    private val cache = HashMap<String, ImageBitmap?>()

    fun bitmap(context: Context, themeId: String, key: String): ImageBitmap? {
        val path = "icons/$themeId/$key.png"
        if (!cache.containsKey(path)) {
            cache[path] = runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
        return cache[path]
    }
}
