package com.umutk.pulse.extras.palette.iconpack

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * The icon packs installed on the phone (the standard launcher icon-pack intents), and the icon each one gives an app.
 * A pack's appfilter.xml is read from its own resources, so Palette uses the picture of an installed pack without bundling it.
 */
object InstalledIconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme", "com.fede.launcher.THEME_ICONPACK", "com.dlto.atom.launcher.THEME", "com.gtp.nextlauncher.theme",
        "com.zeroteam.zerolauncher.theme", "com.lge.launcher2.THEME", "mobi.bbase.ahome.THEME", "com.phonemetra.turbo.launcher.THEMES",
        "home.solo.launcher.free.THEMES", "ginlemon.smartlauncher.THEMES", "com.daeva112.manager.THEME", "com.android.dxtop.launcher.THEME",
    )

    /** Each installed icon pack: its package name and its label. */
    data class Pack(val pkg: String, val label: String)

    fun list(c: Context): List<Pack> {
        val pm = c.packageManager
        return ACTIONS.flatMap { a -> runCatching { pm.queryIntentActivities(Intent(a), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }.distinct().sorted()
            .map { p -> Pack(p, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)) }
    }
}

/** One installed pack, read once: the drawable name of each app, by its component or its package. */
class IconPack(c: Context, val pkg: String) {
    private val res: Resources = c.packageManager.getResourcesForApplication(pkg)
    private val map: Map<String, String> by lazy { readMap() }

    private fun readMap(): Map<String, String> {
        val out = HashMap<String, String>()
        val id = res.getIdentifier("appfilter", "xml", pkg)
        runCatching {
            if (id != 0) parse(res.getXml(id), out)
            else res.assets.open("appfilter.xml").use { ins ->
                val p = Xml.newPullParser(); p.setInput(ins, "utf-8"); parse(p, out)
            }
        }
        return out
    }

    private fun parse(p: XmlPullParser, out: MutableMap<String, String>) {
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "item") {
                val comp = p.getAttributeValue(null, "component")
                val draw = p.getAttributeValue(null, "drawable")
                if (comp != null && draw != null) {
                    Regex("""ComponentInfo\{([^/}]+)/([^}]+)\}""").find(comp)?.let { m ->
                        val cls = m.groupValues[2].let { if (it.startsWith(".")) m.groupValues[1] + it else it }
                        out[m.groupValues[1] + "/" + cls] = draw
                    }
                }
            }
            ev = p.next()
        }
    }

    /** Every drawable name the pack uses, for the name search below. */
    private val names: List<String> by lazy { map.values.distinct().sorted() }

    /**
     * The picture this pack gives the activity [activity] of [app]: its own entry first; without one, the first icon whose name is
     * the app's name (or starts with it, as a word). Names shorter than four letters are never searched ("fm", "get" match nothing).
     */
    fun iconFor(app: String, activity: String, label: String): Drawable? {
        val name = map["$app/$activity"] ?: byName(label) ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    private fun byName(label: String): String? {
        val full = label.lowercase().filter { it.isLetterOrDigit() }
        val first = label.lowercase().trim().split(' ').firstOrNull()?.filter { it.isLetterOrDigit() }.orEmpty()
        for (key in listOf(full, first)) {
            if (key.length < 4) continue
            names.filter { it == key || it.startsWith(key + "_") }.minByOrNull { it.length }?.let { return it }
        }
        return null
    }
}

/** The packs already read, by package name. */
object IconPackCache {
    private val packs = HashMap<String, IconPack?>()
    fun get(c: Context, pkg: String): IconPack? = synchronized(packs) {
        packs.getOrPut(pkg) { runCatching { IconPack(c.applicationContext, pkg) }.getOrNull() }
    }
    fun forget() = synchronized(packs) { packs.clear() }
}
