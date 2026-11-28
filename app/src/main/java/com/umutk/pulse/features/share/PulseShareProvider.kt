package com.umutk.pulse.features.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The data of one app of Pulse for the same app on its own (Android keeps each app's files private, so an app cannot read another's): the app on its own asks Pulse for
 * `content://com.umutk.pulse.share/<app>` and gets a zip of that app's preferences (prefs.json) and files (files/...). Only an app signed with the same key can ask
 * (the signature permission SHARE_DATA). Nothing is sent anywhere else and Pulse's own copy stays as it is.
 */
class PulseShareProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val c = context ?: throw FileNotFoundException()
        val id = uri.lastPathSegment.orEmpty()
        val spec = PulseShareCatalog.specs[id] ?: throw FileNotFoundException("no such app")
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "$id.zip")
        PulseShare.pack(c, spec, out)
        return ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}

object PulseShare {
    private const val MAX_BYTES = 200L * 1024 * 1024

    /** Writes the zip of [spec]: prefs.json (each preferences file with the type of each value) and the files. */
    fun pack(c: Context, spec: ShareSpec, out: File) {
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            val all = JSONObject()
            for (name in spec.prefs) {
                val sp = c.getSharedPreferences(name, Context.MODE_PRIVATE)
                val o = JSONObject()
                for ((k, v) in sp.all) {
                    val e = JSONArray()
                    when (v) {
                        is String -> { e.put("s"); e.put(v) }
                        is Int -> { e.put("i"); e.put(v) }
                        is Long -> { e.put("l"); e.put(v) }
                        is Float -> { e.put("f"); e.put(v.toDouble()) }
                        is Boolean -> { e.put("b"); e.put(v) }
                        is Set<*> -> { e.put("S"); e.put(JSONArray(v.map { it.toString() })) }
                        else -> continue
                    }
                    o.put(k, e)
                }
                if (o.length() > 0) all.put(name, o)
            }
            z.putNextEntry(ZipEntry("prefs.json")); z.write(all.toString().toByteArray()); z.closeEntry()
            var total = 0L
            fun add(f: File, rel: String) {
                if (total > MAX_BYTES) return
                if (f.isDirectory) f.listFiles()?.forEach { add(it, "$rel/${it.name}") }
                else if (f.isFile) { z.putNextEntry(ZipEntry("files/$rel")); f.inputStream().use { it.copyTo(z) }; z.closeEntry(); total += f.length() }
            }
            for (rel in spec.files) File(c.filesDir, rel).takeIf { it.exists() }?.let { add(it, rel) }
        }
    }

    /** What a pickup brought: the preferences files and the files taken in. */
    class Result(val prefs: Int, val files: Int)

    /** Asks Pulse (the main app) for the data of [id] and takes it in: preferences are written through the preferences themselves, files into the files folder. Null when Pulse is not there or refuses. */
    fun pickUp(c: Context, id: String): Result? = runCatching {
        val uri = Uri.parse("content://com.umutk.pulse.share/$id")
        var prefs = 0; var files = 0
        val root = c.filesDir.canonicalPath
        c.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "prefs.json") {
                        val all = JSONObject(String(z.readBytes()))
                        for (name in all.keys()) {
                            val ed = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                            val o = all.getJSONObject(name)
                            for (k in o.keys()) {
                                val a = o.getJSONArray(k)
                                when (a.getString(0)) {
                                    "s" -> ed.putString(k, a.getString(1)); "i" -> ed.putInt(k, a.getInt(1)); "l" -> ed.putLong(k, a.getLong(1))
                                    "f" -> ed.putFloat(k, a.getDouble(1).toFloat()); "b" -> ed.putBoolean(k, a.getBoolean(1))
                                    "S" -> ed.putStringSet(k, (0 until a.getJSONArray(1).length()).map { a.getJSONArray(1).getString(it) }.toSet())
                                }
                            }
                            ed.apply(); prefs++
                        }
                    } else if (e.name.startsWith("files/") && !e.isDirectory) {
                        val f = File(c.filesDir, e.name.removePrefix("files/"))
                        // nothing is written outside the files folder
                        if (f.canonicalPath.startsWith(root)) { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) }; files++ }
                    }
                    e = z.nextEntry
                }
            }
        }
        Result(prefs, files)
    }.getOrNull()

    /** True when Pulse itself is installed (the main app that holds the data). */
    fun pulseThere(c: Context): Boolean = runCatching { c.packageManager.getPackageInfo("com.umutk.pulse", 0); true }.getOrDefault(false)
}
