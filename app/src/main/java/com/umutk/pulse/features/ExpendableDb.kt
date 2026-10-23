package com.umutk.pulse.features

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Known junk folders of public storage, read from rule files in the style of SD Maid's "expendables" (thumbnail caches, ad and
 * analytics leftovers, crash reports, offline and web caches). Only plain folder prefixes on the SD card are used; a rule that needs a
 * pattern is skipped. [scan] lists the folders that exist, [allowed] re-checks one before it is deleted.
 */
object ExpendableDb {
    private val ext: String by lazy { runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrDefault("/storage/emulated/0") }
    private val files = listOf(
        "db_thumbnail_files.json" to ("Thumbnail caches" to "thumb"), "db_advertisement_files.json" to ("Ad leftovers" to "ads"),
        "db_analytics_files.json" to ("Analytics leftovers" to "ana"), "db_bug_reporting_files.json" to ("Crash and bug reports" to "bug"),
        "db_offline_cache_files.json" to ("Offline caches" to "off"), "db_webcaches.json" to ("Web caches" to "web"),
    )
    private var cache: Map<String, Pair<String, String>>? = null  // relative folder -> (title, key)

    private fun folders(c: Context): Map<String, Pair<String, String>> = cache ?: run {
        val out = LinkedHashMap<String, Pair<String, String>>()
        for ((name, tk) in files) runCatching {
            val root = JSONObject(c.assets.open("expendables/$name").bufferedReader().use { it.readText() })
            val apps = root.optJSONArray("appFilter") ?: return@runCatching
            for (i in 0 until apps.length()) {
                val ff = apps.getJSONObject(i).optJSONArray("fileFilter") ?: continue
                for (j in 0 until ff.length()) {
                    val f = ff.getJSONObject(j)
                    val loc = f.optJSONArray("locations")
                    if (loc == null || loc.length() != 1 || loc.getString(0) != "SDCARD") continue
                    if (f.has("contains") || f.has("notContains") || f.has("exclusions")) continue
                    val sw = f.optJSONArray("startsWith") ?: continue
                    for (k in 0 until sw.length()) {
                        val p = sw.getString(k)
                        if (p.endsWith("/") && p.length > 3 && ".." !in p && !p.startsWith("/") && !p.startsWith("Android/")) out[p.trimEnd('/')] = tk
                    }
                }
            }
        }
        out.also { cache = it }
    }

    /** The existing junk folders as (path, title). */
    fun scan(c: Context): List<Pair<File, String>> = folders(c).mapNotNull { (rel, tk) ->
        File(ext, rel).takeIf { it.isDirectory && !it.list().isNullOrEmpty() }?.let { it to tk.first }
    }

    fun allowed(c: Context, path: String): Boolean = path.startsWith("$ext/") && folders(c).containsKey(path.removePrefix("$ext/"))
}
