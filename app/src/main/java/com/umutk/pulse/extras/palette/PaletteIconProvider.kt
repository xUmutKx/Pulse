package com.umutk.pulse.extras.palette

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * The icons of the saved look, drawn on request, for launchers that read an icon pack through a content provider:
 *   content://<package>.palette.icons/icon/<package>/<activity>
 *   content://<package>.palette.icons/list
 */
class PaletteIconProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val c = context ?: return null
        return when (uri.pathSegments.firstOrNull()) {
            "list" -> runBlocking {
                val cur = MatrixCursor(arrayOf("component", "label", "uri"))
                PaletteStudio.apps(c).load().forEach { cur.addRow(arrayOf(it.component, it.label, "content://${c.packageName}.palette.icons/icon/${it.packageName}/${it.activityName}")) }
                cur
            }
            "info" -> MatrixCursor(arrayOf("name", "dynamic", "version")).apply { addRow(arrayOf<Any>("Pulse Palette", 1, 1)) }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val c = context ?: return null
        val seg = uri.pathSegments
        if (seg.size < 3 || seg[0] != "icon") return null
        return runBlocking {
            val spec = PaletteStudio.spec(c)
            val apps = PaletteStudio.apps(c).load()
            val idx = apps.indexOfFirst { it.packageName == seg[1] && it.activityName == seg[2] }
            val info = apps.getOrNull(idx) ?: return@runBlocking null
            val engine = PaletteStudio.engine(c)
            engine.prepareLuminanceRanks(apps)
            val bmp = engine.render(info, spec, idx, 192, PaletteStore.overrides(c)[info.key])
            val f = File(c.cacheDir, "palette_provider").apply { mkdirs() }.resolve("${seg[1]}_${seg[2].hashCode()}.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    override fun getType(uri: Uri): String = "image/png"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
