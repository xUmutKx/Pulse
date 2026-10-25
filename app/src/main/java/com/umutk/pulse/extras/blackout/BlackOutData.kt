package com.umutk.pulse.extras.blackout

import android.content.Context
import android.content.SharedPreferences
import com.umutk.pulse.Shell
import org.json.JSONArray
import org.json.JSONObject

/** The settings of BlackOut live in one preference file, "blackout"; the LSPosed module in other apps reads the same file. Backup, restore and the import from the stand-alone BlackOut app. */
object BlackOutData {
    const val STANDALONE = "com.umutk.blackout"

    // kept in a field: preference listeners are held weakly by the system
    private var autoBackup: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun prefs(c: Context): SharedPreferences {
        val sp = c.getSharedPreferences("blackout", Context.MODE_PRIVATE)
        if (autoBackup == null) {
            val app = c.applicationContext
            // every change goes to the backup file the user picked (it survives an uninstall; import it after a reinstall)
            autoBackup = SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
                if (k == "backup_uri") return@OnSharedPreferenceChangeListener
                val uri = p.getString("backup_uri", null) ?: return@OnSharedPreferenceChangeListener
                try { app.contentResolver.openOutputStream(android.net.Uri.parse(uri), "wt")?.use { it.write(export(p).toByteArray()) } } catch (_: Throwable) { }
            }
            sp.registerOnSharedPreferenceChangeListener(autoBackup)
        }
        return sp
    }

    /** All settings and colour rules as one JSON text. */
    fun export(sp: SharedPreferences): String {
        val o = JSONObject()
        for ((k, v) in sp.all) when (v) {
            is Set<*> -> o.put(k, JSONArray(v.map { it.toString() }))
            else -> o.put(k, v)
        }
        return o.toString(2)
    }

    /** Replaces the current settings with the ones in [text]. */
    fun import(sp: SharedPreferences, text: String) {
        val o = JSONObject(text)
        val e = sp.edit().clear()
        for (k in o.keys()) when (val v = o.get(k)) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Double -> e.putFloat(k, v.toFloat())
            is String -> e.putString(k, v)
            is JSONArray -> e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
        }
        e.apply()
    }

    /** True when the stand-alone BlackOut app is installed on the phone. */
    fun standaloneInstalled(c: Context): Boolean = runCatching { c.packageManager.getPackageInfo(STANDALONE, 0); true }.getOrDefault(false)

    /**
     * Reads the settings of the stand-alone app straight from its preference file (root only: other apps' data is closed to everyone else) and puts them in place of the
     * current ones. Returns a message; the number of settings taken is in it.
     */
    fun importFromStandalone(c: Context): String {
        val paths = Shell.dataPaths(STANDALONE).map { "$it/shared_prefs/blackout.xml" }
        val seen = Shell.rootSee(paths) ?: return "The settings file of BlackOut could not be seen. Root is needed for this; or export the settings in BlackOut and import the file here."
        val xml = Shell.runRoot("cat '${seen.second}'", seen.first)
        if (!xml.contains("<map")) return "BlackOut answered, but not with a settings file."
        val e = prefs(c).edit().clear()
        var n = 0
        val p = android.util.Xml.newPullParser()
        p.setInput(java.io.StringReader(xml))
        var ev = p.eventType
        var setName: String? = null
        val set = LinkedHashSet<String>()
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                val name = p.getAttributeValue(null, "name")
                when (p.name) {
                    "boolean" -> { e.putBoolean(name, p.getAttributeValue(null, "value") == "true"); n++ }
                    "int" -> { e.putInt(name, p.getAttributeValue(null, "value").toIntOrNull() ?: 0); n++ }
                    "long" -> { e.putLong(name, p.getAttributeValue(null, "value").toLongOrNull() ?: 0L); n++ }
                    "float" -> { e.putFloat(name, p.getAttributeValue(null, "value").toFloatOrNull() ?: 0f); n++ }
                    "string" -> if (setName == null) { e.putString(name, p.nextText()); n++ } else set += p.nextText()
                    "set" -> { setName = name; set.clear() }
                }
            } else if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && p.name == "set" && setName != null) {
                e.putStringSet(setName, LinkedHashSet(set)); n++; setName = null
            }
            ev = p.next()
        }
        e.putBoolean("imported_standalone", true).apply()
        return "Took $n settings from BlackOut."
    }
}
