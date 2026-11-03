package com.umutk.pulse.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.features.CoverService

/**
 * The screen cover as a widget: its clock face (analog here; the wide and tall sizes show the digital and matrix faces).
 * A tap turns the screen cover on or off, the same as its Quick settings tile. The clock redraws every minute.
 */
open class CoverWidget : PulseWidget() {
    /** The face this size shows: "analog", "digital" or "matrix". */
    protected open val face: String get() = "analog"

    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, textId) ?: (110 to 110)
        val w = (sz.first * dens).toInt().coerceIn(120, 1400); val h = (sz.second * dens).toInt().coerceIn(120, 1400)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            setImageViewBitmap(R.id.tile, CoverFaces.draw(ctx, face, w, h, System.currentTimeMillis()))
            setViewVisibility(R.id.tile, View.VISIBLE)
            setViewVisibility(R.id.icon, View.GONE); setViewVisibility(R.id.title, View.GONE); setViewVisibility(R.id.value, View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            val toggle = Intent(ctx, CoverWidget::class.java).setAction(ACTION_TOGGLE)
            setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(ctx, 9600, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        super.onUpdate(ctx, mgr, ids)
        scheduleTick(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (CoverService.running) CoverService.stop(ctx) else CoverService.start(ctx)
                redrawAll(ctx)
            }
            ACTION_TICK -> redrawAll(ctx)
            else -> super.onReceive(ctx, intent)
        }
    }

    /** Every cover widget is drawn again (the clock moved, or the screen cover changed). */
    private fun redrawAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        var any = false
        listOf(CoverWidget(), CoverWideWidget(), CoverTallWidget()).forEach { w ->
            mgr.getAppWidgetIds(ComponentName(ctx, w.javaClass)).forEach { id ->
                any = true
                mgr.updateAppWidget(id, w.contentFor(ctx, id, WidgetStyles.of(ctx, id)))
            }
        }
        if (!any) ctx.getSystemService(AlarmManager::class.java)?.cancel(tickPending(ctx))
    }

    private fun scheduleTick(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L
        am.setRepeating(AlarmManager.RTC, next, 60_000L, tickPending(ctx))
    }

    private fun tickPending(ctx: Context) = PendingIntent.getBroadcast(ctx, 9601,
        Intent(ctx, CoverWidget::class.java).setAction(ACTION_TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_TOGGLE = "com.umutk.pulse.action.COVER_TOGGLE"
        const val ACTION_TICK = "com.umutk.pulse.action.COVER_TICK"
    }
}

/** The screen cover in a wide cell (two across, one down): the digital face. */
open class CoverWideWidget : CoverWidget() {
    override val face: String get() = "digital"
}

/** The screen cover in a tall cell (one across, two down): the matrix face. */
open class CoverTallWidget : CoverWidget() {
    override val face: String get() = "matrix"
}
