package com.umutk.pulse.features

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Per-theme icon packs. Windows 11's is inside the app; every other look's pack is a download of the Extra store (icons-<look>) that comes by itself the first time the look is used
 * ([PackAuto]); until it is there the look draws its vector icons. Null when the pack has no file for that key.
 */
object ThemeIcons {
    private val cache = HashMap<String, ImageBitmap?>()
    private val INSIDE = setOf("win11")

    /** Changes when a pack has been installed, so the screens that asked for an icon ask again. */
    private var version by androidx.compose.runtime.mutableIntStateOf(0)
    private var seenRev = -1

    fun bitmap(context: Context, themeId: String, key: String): ImageBitmap? {
        version   // read: a composable that asks for an icon asks again when a pack arrives
        val rev = com.umutk.pulse.extras.store.StoreInstaller.rev.value
        if (rev != seenRev) { seenRev = rev; cache.entries.removeAll { it.value == null }; version++ }
        // the picture is a WebP (lossless, same pixels as the PNG it came from); a PNG is still read when there is only that
        val key0 = "icons/$themeId/$key"
        if (!cache.containsKey(key0)) {
            val app = context.applicationContext
            cache[key0] = runCatching {
                if (themeId in INSIDE) (runCatching { app.assets.open("$key0.webp") }.getOrElse { app.assets.open("$key0.png") }).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                else {
                    val pack = com.umutk.pulse.extras.store.StoreInstaller.dataDir(app, "icons-$themeId")
                    val f = java.io.File(pack, "$key.webp").takeIf { it.isFile } ?: java.io.File(pack, "$key.png").takeIf { it.isFile }
                    if (f == null) { com.umutk.pulse.extras.store.PackAuto.ensure(app, "icons-$themeId"); null } else BitmapFactory.decodeFile(f.path)?.asImageBitmap()
                }
            }.getOrNull()
            // a missing file is not remembered for good when its pack is still on the way: it is read again after the install
            if (cache[key0] == null && themeId !in INSIDE) cache.remove(key0)
        }
        return cache[key0]
    }
}

// Win11 look: every action has its own Fluent glyph in FluentPaths (keys: trash cut share properties open openFolder openWith star starOn lock unlock eyeOn eyeOff zip upload refresh sortName sortSize sortDate filter selectAll add newfile check edit save link pin tag storage bell shield bolt batteryFull clock home forward sizes recent emptyFolder bigFile dupes undo redo more globe image music video code print person key calendar mail chat cloud bug wrench flag gridView listView compare up down stop trashAll restore fullscreen zoomIn notes shieldOk warning error question usb sdcard folderLink foldersCopy). Menus pass these keys to PIcon / ExIcon.
