package com.umutk.pulse.features

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Per-theme icon packs from assets/icons/<theme>/<key>.webp (or .png); null when the pack has no file for that key. */
object ThemeIcons {
    private val cache = HashMap<String, ImageBitmap?>()

    fun bitmap(context: Context, themeId: String, key: String): ImageBitmap? {
        // the picture is a WebP (lossless, same pixels as the PNG it came from); a PNG is still read when there is only that
        val key0 = "icons/$themeId/$key"
        if (!cache.containsKey(key0)) {
            cache[key0] = runCatching {
                (runCatching { context.assets.open("$key0.webp") }.getOrElse { context.assets.open("$key0.png") })
                    .use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
        return cache[key0]
    }
}

// Win11 look: every action has its own Fluent glyph in FluentPaths (keys: trash cut share properties open openFolder openWith star starOn lock unlock eyeOn eyeOff zip upload refresh sortName sortSize sortDate filter selectAll add newfile check edit save link pin tag storage bell shield bolt batteryFull clock home forward sizes recent emptyFolder bigFile dupes undo redo more globe image music video code print person key calendar mail chat cloud bug wrench flag gridView listView compare up down stop trashAll restore fullscreen zoomIn notes shieldOk warning error question usb sdcard folderLink foldersCopy). Menus pass these keys to PIcon / ExIcon.
