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
    private val files = listOf("kbd_user.txt", "kbd_pairs.txt", "kbd_forms.txt", "kbd_typos.txt", "kbd_banned.txt", "kbd_rejects.txt", "kbd_keys.txt", "kbd_touch.txt", "kbd_typo_seen.txt", "kbd_clips.json")
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
    fun export(c: Context, uri: Uri, password: String = ""): Int = c.contentResolver.openOutputStream(uri)?.use { out ->
        if (password.isEmpty()) write(c, out)
        else { val buf = java.io.ByteArrayOutputStream(); val n = write(c, buf); out.write(seal(buf.toByteArray(), password)); n }
    } ?: 0

    // Encrypted backups (as in WM Keyboard): AES-256-GCM, the key from the password with PBKDF2 (120k rounds, a random salt)
    private val MAGIC = "PKBE1".toByteArray()
    private fun key(pw: String, salt: ByteArray): javax.crypto.spec.SecretKeySpec {
        val f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(javax.crypto.spec.PBEKeySpec(pw.toCharArray(), salt, 120_000, 256)).encoded
        return javax.crypto.spec.SecretKeySpec(k, "AES")
    }
    private fun seal(data: ByteArray, pw: String): ByteArray {
        val rnd = java.security.SecureRandom(); val salt = ByteArray(16).also { rnd.nextBytes(it) }; val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key(pw, salt), javax.crypto.spec.GCMParameterSpec(128, iv)) }
        return MAGIC + salt + iv + ci.doFinal(data)
    }
    private fun open(data: ByteArray, pw: String): ByteArray? = runCatching {
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + 16); val iv = data.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.DECRYPT_MODE, key(pw, salt), javax.crypto.spec.GCMParameterSpec(128, iv)) }
        ci.doFinal(data, MAGIC.size + 28, data.size - MAGIC.size - 28)
    }.getOrNull()

    /** Writes the backup to [out] (closes it); returns how many files went in. */
    fun write(c: Context, out: java.io.OutputStream): Int {
        KeyboardDictionary.saveUser(c, force = true)
        var n = 0
        out.let { raw ->
            ZipOutputStream(raw.buffered()).use { z ->
                z.putNextEntry(ZipEntry(PREFS)); z.write(prefsJson(c).toByteArray()); z.closeEntry(); n++
                files.forEach { f -> File(c.filesDir, f).takeIf { it.exists() }?.let { z.putNextEntry(ZipEntry(f)); it.inputStream().use { i -> i.copyTo(z) }; z.closeEntry(); n++ } }
                // the clipboard pictures never go in the backup (only the other files do)
                if (false) File(c.filesDir, "clips").listFiles()?.forEach { p -> z.putNextEntry(ZipEntry("clips/" + p.name)); p.inputStream().use { i -> i.copyTo(z) }; z.closeEntry(); n++ }
            }
        }
        return n
    }

    /** Reads a backup made by [export]; returns how many files came back, or -1 when [uri] is not one. Blocking: call off the main thread. */
    fun import(c: Context, uri: Uri, password: String = ""): Int {
        var n = 0; var ok = false
        var bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return -1
        // an encrypted backup: -2 when the password is missing or wrong
        if (bytes.size > MAGIC.size + 28 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) bytes = open(bytes, password) ?: return -2
        bytes.inputStream().use { raw ->
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
