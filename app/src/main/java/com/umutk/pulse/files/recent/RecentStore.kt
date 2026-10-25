package com.umutk.pulse.files.recent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/** Recently opened files (newest first, [MAX] at most) and the newest files from MediaStore; prefs live in the shared "p" file. */
object RecentStore {
    const val MAX = 50
    private const val KEY = "ex_recent"          // same key as the old ten-item list, so the history carries over

    class Arrival(val path: String, val name: String, val addedMs: Long)

    private fun prefs(c: Context) = com.umutk.pulse.filesPrefs(c)

    fun list(c: Context): List<String> = prefs(c).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }

    /** Moves [path] to the top (adding it if needed) and returns the new list. */
    fun add(c: Context, path: String): List<String> {
        val l = (listOf(path) + list(c).filter { it != path }).take(MAX)
        prefs(c).edit().putString(KEY, l.joinToString("\n")).apply()
        return l
    }


    /** Other apps' files are listed only with all-files access (Android 11+) or the read permission (up to Android 12L). */
    fun canListAll(c: Context): Boolean =
        com.umutk.pulse.Compat.isStorageManager() ||
            (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

    /** Newest first by MediaStore date_added; empty without access or on a provider error. */
    fun arrivals(c: Context): List<Arrival> {
        if (!canListAll(c)) return emptyList()
        return runCatching {
            val out = ArrayList<Arrival>()
            c.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DATE_ADDED),
                null, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cur ->
                val dataCol = cur.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val addCol = cur.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                while (out.size < MAX && cur.moveToNext()) {
                    val p = cur.getString(dataCol) ?: continue
                    val f = File(p)
                    if (f.isFile) out += Arrival(p, f.name, cur.getLong(addCol) * 1000L)
                }
            }
            out
        }.getOrDefault(emptyList())
    }
}
