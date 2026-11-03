package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.features.CoverService
import com.umutk.pulse.tr

/** The screen cover as a widget: its state, and a tap turns it on or off (the same as its Quick settings tile). */
class CoverWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val on = CoverService.running
        val title = tr("Screen cover", "Ekran kapağı")
        val value = if (on) tr("On", "Açık") else tr("Off", "Kapalı")
        return views(ctx, s, R.layout.widget_usage, title, value, "cover", Tile(value, listOf(title), if (on) 1f else 0f)).apply {
            val toggle = Intent(ctx, CoverWidget::class.java).setAction(ACTION_TOGGLE)
            setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(ctx, 9600, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            if (CoverService.running) CoverService.stop(ctx) else CoverService.start(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, CoverWidget::class.java)).forEach { mgr.updateAppWidget(it, contentFor(ctx, it, WidgetStyles.of(ctx, it))) }
            return
        }
        super.onReceive(ctx, intent)
    }

    private companion object {
        const val ACTION_TOGGLE = "com.umutk.pulse.action.COVER_TOGGLE"
    }
}
