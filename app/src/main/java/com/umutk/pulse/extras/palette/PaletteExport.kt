package com.umutk.pulse.extras.palette

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The icons of the look written as PNG pictures, one per app, into a zip in Downloads. Returns how many. Blocking: call off the main thread. */
object PaletteExport {
    fun zip(c: Context, apps: List<AppInfo>, spec: ThemeSpec): Int {
        val engine = PaletteStudio.engine(c)
        engine.prepareLuminanceRanks(apps)
        val (out, close) = open(c)
        var n = 0
        ZipOutputStream(out).use { z ->
            apps.forEachIndexed { i, app ->
                val bmp = kotlinx.coroutines.runBlocking { engine.render(app, spec, i, 192, PaletteStore.overrides(c)[app.key], useCache = false) }
                z.putNextEntry(ZipEntry(app.safeName + ".png"))
                bmp.compress(Bitmap.CompressFormat.PNG, 100, z)
                z.closeEntry(); n++
            }
        }
        close()
        return n
    }

    private fun open(c: Context): Pair<OutputStream, () -> Unit> {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, "Pulse-Palette.zip"); put(MediaStore.Downloads.MIME_TYPE, "application/zip"); put(MediaStore.Downloads.IS_PENDING, 1) }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("no uri")
            val s = c.contentResolver.openOutputStream(uri) ?: error("no stream")
            return s to { c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null); Unit }
        }
        val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse-Palette.zip")
        return f.outputStream() to { }
    }
}
