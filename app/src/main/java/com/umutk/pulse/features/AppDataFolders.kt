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
    private val icons = ConcurrentHashMap<String, ImageBitmap?>()

    /** One folder per installed package, sorted by name. Listing them needs root (see Explorer). */
    fun list(ctx: Context): List<File> =
        ctx.packageManager.getInstalledApplications(0).map { File(ROOT + it.packageName) }.sortedBy { it.name }

    /** The app's icon when [path] is one of these folders, null for any other folder. */
    fun icon(ctx: Context, path: String): ImageBitmap? {
        if (!path.startsWith(ROOT)) return null
        val pkg = path.removePrefix(ROOT)
        if (pkg.isEmpty() || pkg.contains('/')) return null
        return icons.getOrPut(pkg) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap() }.getOrNull()
        }
    }
}
