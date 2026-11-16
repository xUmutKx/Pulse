package com.umutk.pulse.extras.keyboard

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * The numbers the keyboard remembers (a phone number, an ID number): sealed with the phone's own key, like the password vault (AES-GCM, a file private to Pulse and
 * left out of the backups), never as plain text. Number to how many times it was typed.
 */
internal object KbNumbers {
    private const val FILE = "kb-numbers.bin"
    @Volatile private var cache: Map<String, Int>? = null
    private fun file(c: Context) = File(c.noBackupFilesDir, FILE)

    @Synchronized fun all(c: Context): Map<String, Int> {
        cache?.let { return it }
        migrate(c)
        val f = file(c)
        val m = if (!f.exists()) emptyMap() else runCatching {
            val o = JSONObject(String(com.umutk.pulse.features.AuthStore.openRaw(f.readBytes()), Charsets.UTF_8))
            o.keys().asSequence().associateWith { o.optInt(it) }
        }.getOrDefault(emptyMap())
        cache = m
        return m
    }

    @Synchronized fun save(c: Context, m: Map<String, Int>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }
        val f = file(c)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(com.umutk.pulse.features.AuthStore.sealRaw(o.toString().toByteArray(Charsets.UTF_8)))
        tmp.renameTo(f)
        cache = m
    }

    fun clear(c: Context) { runCatching { file(c).delete() }; cache = emptyMap() }

    /** The numbers an earlier version kept as plain preferences move into the sealed file, and the plain ones are wiped. */
    private fun migrate(c: Context) {
        val sp = c.getSharedPreferences("pulse_kb_numbers", Context.MODE_PRIVATE)
        if (sp.all.isEmpty()) return
        val old = sp.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
        sp.edit().clear().apply()
        val f = file(c)
        val have = if (!f.exists()) emptyMap() else runCatching {
            val o = JSONObject(String(com.umutk.pulse.features.AuthStore.openRaw(f.readBytes()), Charsets.UTF_8)); o.keys().asSequence().associateWith { o.optInt(it) }
        }.getOrDefault(emptyMap())
        val merged = HashMap(have); old.forEach { (k, v) -> merged[k] = maxOf(merged[k] ?: 0, v) }
        val o = JSONObject(); merged.forEach { (k, v) -> o.put(k, v) }
        f.parentFile?.mkdirs()
        f.writeBytes(com.umutk.pulse.features.AuthStore.sealRaw(o.toString().toByteArray(Charsets.UTF_8)))
    }
}
