package com.umutk.pulse.extras.music.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.umutk.pulse.widgets.music.WinampWidget
import com.umutk.pulse.widgets.music.WinampWidgetCompact

/**
 * Picker support for "each Winamp skin is a widget choice": a skin card pins the Winamp widget and, when the
 * launcher reports the pin as placed, the new widget id gets that skin (the same prefs WinampWidgetConfig writes).
 * The new id is the one that was not there before the pin. Unverified on devices: launchers differ in whether
 * the success callback arrives.
 */
object WinampSkinPick {
    const val ACTION = "com.umutk.pulse.winamp.SKIN_PINNED"
    private const val PENDING_CODE = 7101

    private fun sp(c: Context) = c.getSharedPreferences("winamp_skin_pick", Context.MODE_PRIVATE)

    private fun ids(c: Context): Set<Int> {
        val m = AppWidgetManager.getInstance(c)
        return (m.getAppWidgetIds(ComponentName(c, WinampWidget::class.java)).toList() +
            m.getAppWidgetIds(ComponentName(c, WinampWidgetCompact::class.java)).toList()).toSet()
    }

    /** Asks the launcher to place a Winamp widget that will show [skinFile] (the file name of an installed skin). False when the launcher cannot pin. */
    fun pin(c: Context, skinFile: String, compact: Boolean): Boolean {
        val mgr = c.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!mgr.isRequestPinAppWidgetSupported) return false
        sp(c).edit().putString("skin", skinFile).putString("before", ids(c).joinToString(",")).apply()
        val cb = PendingIntent.getBroadcast(
            c, PENDING_CODE, Intent(ACTION).setComponent(ComponentName(c, WinampSkinPicked::class.java)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cls = if (compact) WinampWidgetCompact::class.java else WinampWidget::class.java
        return mgr.requestPinAppWidget(ComponentName(c, cls), null, cb)
    }

    /** Plain pin of a player widget that has no skin choice (Poweramp, the visualizer). False when the launcher cannot pin. */
    fun pinWidget(c: Context, cls: Class<*>): Boolean {
        val mgr = c.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(c, cls), null, null)
    }

    /** Gives the widget ids that appeared since [pin] the chosen skin and redraws them. */
    internal fun assignNew(c: Context) {
        val p = sp(c)
        val skin = p.getString("skin", null) ?: return
        val before = p.getString("before", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }.toSet()
        val fresh = ids(c) - before
        val edit = WinampWidget.prefs(c).edit()
        fresh.forEach { edit.putString("skin_$it", skin) }
        edit.apply()
        val m = AppWidgetManager.getInstance(c)
        fresh.forEach { m.updateAppWidget(it, WinampWidget.build(c, it)) }
        p.edit().remove("skin").remove("before").apply()
    }
}

/** Receives the launcher's "pinned" callback for a skin card (see [WinampSkinPick.pin]); registered in the manifest, not exported. */
class WinampSkinPicked : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == WinampSkinPick.ACTION) WinampSkinPick.assignNew(c)
    }
}
