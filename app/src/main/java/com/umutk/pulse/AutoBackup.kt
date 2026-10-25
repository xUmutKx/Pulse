package com.umutk.pulse

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A copy of the data every day without asking: two zips in Download/Pulse backup, one of the app (DataBackup) and one of the keyboard (KeyboardBackup),
 * the last [KEEP] of each. They live outside the app's own folders, so they stay when the app is removed and installed again.
 * On/off and the time of the last run are plain marker files in filesDir, so every process (Pulse, the keyboard, the extra apps) sees the same.
 */
object AutoBackup {
    private const val KEEP = 7
    private const val FOLDER = "Pulse backup"
    private const val DAY = 20L * 3600_000L

    private fun off(c: Context) = File(c.filesDir, "autobackup.off")
    private fun stamp(c: Context) = File(c.filesDir, "autobackup.last")

    fun enabled(c: Context) = !off(c).exists()
    fun setEnabled(c: Context, on: Boolean) { if (on) off(c).delete() else off(c).writeText("1") }
    fun lastRun(c: Context): Long = stamp(c).takeIf { it.exists() }?.lastModified() ?: 0L

    /** Runs a backup if it is on and the last one is old enough. Blocking: call off the main thread. */
    @Synchronized fun maybeRun(c: Context) {
        if (!enabled(c) || System.currentTimeMillis() - lastRun(c) < DAY) return
        run(c)
    }

    /** Writes both zips now; returns true when at least the app zip was saved. */
    fun run(c: Context): Boolean {
        val day = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        val app = runCatching { save(c, "pulse-$day.zip", "pulse-") { DataBackup.export(c, it) } }.getOrDefault(false)
        runCatching { save(c, "pulse-keyboard-$day.zip", "pulse-keyboard-") { com.umutk.pulse.extras.keyboard.KeyboardBackup.write(c, it) } }
        if (app) runCatching { stamp(c).apply { writeText(day) }.setLastModified(System.currentTimeMillis()) }
        return app
    }

    private fun save(c: Context, name: String, prefix: String, write: (OutputStream) -> Unit): Boolean {
        val ok: Boolean
        if (Build.VERSION.SDK_INT >= 29) {
            val rel = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return false
            ok = runCatching { c.contentResolver.openOutputStream(uri)?.use { write(it) } != null }.getOrDefault(false)
            if (!ok) { runCatching { c.contentResolver.delete(uri, null, null) }; return false }
            // only the newest few stay (the ones this install can see)
            runCatching {
                val old = ArrayList<Pair<String, android.net.Uri>>()
                c.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("$rel%", "$prefix%"), null)?.use { q ->
                    while (q.moveToNext()) {
                        val n = q.getString(1)
                        // "pulse-" must not pick up the keyboard zips
                        if (prefix == "pulse-" && n.startsWith("pulse-keyboard-")) continue
                        old += n to android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, q.getLong(0))
                    }
                }
                old.sortedByDescending { it.first }.drop(KEEP).forEach { runCatching { c.contentResolver.delete(it.second, null, null) } }
            }
            return true
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER).apply { mkdirs() }
        if (!dir.isDirectory) return false
        ok = File(dir, name).outputStream().use { write(it); true }
        dir.listFiles { f -> f.name.startsWith(prefix) && (prefix != "pulse-" || !f.name.startsWith("pulse-keyboard-")) }
            ?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        return ok
    }
}
