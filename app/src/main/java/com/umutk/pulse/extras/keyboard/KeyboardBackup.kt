package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The keyboard's own data in one file (a zip): its settings, the learned words and word pairs, the typos it learned, the words never to suggest, the finger drift
 * and the kept clipboard (texts and pictures). Import puts them back and the keyboard reads them again.
 */
object KeyboardBackup {
    private val files = listOf("kbd_user.txt", "kbd_pairs.txt", "kbd_typos.txt", "kbd_banned.txt", "kbd_rejects.txt", "kbd_keys.txt", "kbd_touch.txt", "kbd_clips.json")
    private const val PREFS = "prefs.json"

    private fun prefsJson(c: Context): String {
        val o = JSONObject()
        for ((k, v) in c.getSharedPreferences(KeyboardPrefs.FILE, Context.MODE_PRIVATE).all) {
            val e = JSONObject().put("v", if (v is Set<*>) JSONArray(v.toList()) else v)
            e.put("t", when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is Set<*> -> "s"; else -> "t" })
            o.put(k, e)
        }
        return o.toString()
    }

    /** Writes the backup to [uri]; returns how many files went in. Blocking: call off the main thread. */
    fun export(c: Context, uri: Uri): Int {
        KeyboardDictionary.saveUser(c, force = true)
        var n = 0
        c.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { z ->
                z.putNextEntry(ZipEntry(PREFS)); z.write(prefsJson(c).toByteArray()); z.closeEntry(); n++
                files.forEach { f -> File(c.filesDir, f).takeIf { it.exists() }?.let { z.putNextEntry(ZipEntry(f)); it.inputStream().use { i -> i.copyTo(z) }; z.closeEntry(); n++ } }
                File(c.filesDir, "clips").listFiles()?.forEach { p -> z.putNextEntry(ZipEntry("clips/" + p.name)); p.inputStream().use { i -> i.copyTo(z) }; z.closeEntry(); n++ }
            }
        }
        return n
    }

    /** Reads a backup made by [export]; returns how many files came back, or -1 when [uri] is not one. Blocking: call off the main thread. */
    fun import(c: Context, uri: Uri): Int {
        var n = 0; var ok = false
        c.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name == PREFS -> { applyPrefs(c, JSONObject(String(z.readBytes()))); ok = true; n++ }
                        name in files -> { File(c.filesDir, name).writeBytes(z.readBytes()); ok = true; n++ }
                        name.startsWith("clips/") && '/' !in name.removePrefix("clips/") && ".." !in name -> { File(c.filesDir, "clips").apply { mkdirs() }.let { File(it, name.removePrefix("clips/")).writeBytes(z.readBytes()) }; n++ }
                    }
                    e = z.nextEntry
                }
            }
        }
        if (!ok) return -1
        KeyboardDictionary.reloadUser(c)
        KeyboardPrefs.loaded = false
        KeyboardPrefs.load(c)
        ClipStore.reload()
        return n
    }

    private fun applyPrefs(c: Context, o: JSONObject) {
        val ed = c.getSharedPreferences(KeyboardPrefs.FILE, Context.MODE_PRIVATE).edit().clear()
        o.keys().forEach { k ->
            val e = o.getJSONObject(k)
            when (e.getString("t")) {
                "b" -> ed.putBoolean(k, e.getBoolean("v")); "i" -> ed.putInt(k, e.getInt("v")); "l" -> ed.putLong(k, e.getLong("v")); "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "s" -> ed.putStringSet(k, e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                else -> ed.putString(k, e.getString("v"))
            }
        }
        ed.apply()
    }
}
