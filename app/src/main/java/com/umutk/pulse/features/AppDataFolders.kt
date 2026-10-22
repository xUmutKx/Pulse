package com.umutk.pulse.features

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The app data folders under /data/data: one per installed app, shown in the Files root with the app's own icon. */
object AppDataFolders {
    private const val ROOT = "/data/data/"
    // the icons load off the main thread, one pass for the whole list; the rows show them as they arrive (a state map, so they redraw)
    private val icons = androidx.compose.runtime.mutableStateMapOf<String, ImageBitmap>()
    private val asked = ConcurrentHashMap<String, Boolean>()
    private val loader = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var listed: List<File>? = null

    /** One folder per installed package, sorted by name; read once per run (Refresh in Files reads it again through [forget]). Listing them needs root (see Explorer). */
    fun list(ctx: Context): List<File> = listed ?: ctx.packageManager.getInstalledApplications(0).map { File(ROOT + it.packageName) }.sortedBy { it.name }.also { listed = it }

    fun forget() { listed = null }

    /** The app's icon when [path] is one of these folders, null for any other folder (and until it has loaded). */
    fun icon(ctx: Context, path: String): ImageBitmap? {
        // /data/data/<pkg>, and the app folders of shared storage: Android/data/<pkg>, Android/obb/<pkg>, Android/media/<pkg>
        val pkg = if (path.startsWith(ROOT)) path.removePrefix(ROOT) else Regex(".*/Android/(?:data|obb|media)/([^/]+)$").matchEntire(path)?.groupValues?.get(1) ?: return null
        if (pkg.isEmpty() || pkg.contains('/')) return null
        icons[pkg]?.let { return it }
        if (asked.putIfAbsent(pkg, true) == null) {
            val app = ctx.applicationContext
            loader.execute {
                val b = runCatching { app.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                if (b != null) android.os.Handler(android.os.Looper.getMainLooper()).post { icons[pkg] = b }
            }
        }
        return null
    }
}
