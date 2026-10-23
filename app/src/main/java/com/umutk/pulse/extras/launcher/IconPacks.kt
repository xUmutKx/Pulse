package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Icon packs of the Nova / ADW kind (the ones in the Play Store): an installed app that answers one of the launcher theme intents and carries an
 * appfilter.xml mapping each app (by its main activity) to a drawable. The launcher draws the app pictures from the pack where it has one.
 */
object IconPacks {
    class Pack(val pkg: String, val label: String)

    private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK", "com.gau.go.launcherex.theme")

    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        return ACTIONS.flatMap { a -> runCatching { pm.queryIntentActivities(Intent(a), 0) }.getOrDefault(emptyList()) }
            .distinctBy { it.activityInfo.packageName }
            .map { Pack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** The pack's appfilter: "package/activity" -> drawable name. Empty when the pack has none. */
    fun filter(ctx: Context, pack: String): Map<String, String> = runCatching {
        val res = ctx.packageManager.getResourcesForApplication(pack)
        val id = res.getIdentifier("appfilter", "xml", pack)
        val parser: XmlPullParser = if (id != 0) res.getXml(id) else XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), null) }
        val out = HashMap<String, String>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component"); val dr = parser.getAttributeValue(null, "drawable")
                // "ComponentInfo{com.example/com.example.Main}"
                if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) out.putIfAbsent(comp.substring(14, comp.length - 1), dr)
            }
            ev = parser.next()
        }
        out
    }.getOrDefault(emptyMap())

    /** The pack's drawable for [activity] of [pkg], or null when the pack has none for it. */
    fun icon(ctx: Context, pack: String, map: Map<String, String>, pkg: String, activity: String): Drawable? {
        val name = map["$pkg/$activity"] ?: return null
        return runCatching {
            val res = ctx.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier(name, "drawable", pack).takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", pack)
            if (id == 0) null else androidx.core.content.res.ResourcesCompat.getDrawable(res, id, null)
        }.getOrNull()
    }
}
