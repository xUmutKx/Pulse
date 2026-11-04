package com.umutk.pulse.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.features.CoverService

/**
 * The screen cover as a widget: the same page the cover shows (its colours, font and layout), in any size.
 * A tap turns the screen cover on or off, the same as its Quick settings tile. The clock redraws every minute.
 */
open class CoverWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, textId) ?: (110 to 110)
        val w = (sz.first * dens).toInt().coerceIn(120, 1400); val h = (sz.second * dens).toInt().coerceIn(120, 1400)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            setImageViewBitmap(R.id.tile, CoverFaces.draw(ctx, CoverFaces.page(ctx), w, h, System.currentTimeMillis()))
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
        CoverTicker.start(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (CoverService.running) CoverService.stop(ctx) else CoverService.start(ctx)
                redrawAll(ctx)
            }
            ACTION_TICK -> { redrawAll(ctx); CoverTicker.start(ctx) }
            else -> super.onReceive(ctx, intent)
        }
    }

    /** Every cover widget is drawn again (the clock moved, or the screen cover changed). */
    private fun redrawAll(ctx: Context) {
        val any = refreshAll(ctx)
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

        /** Draws every placed screen cover widget again (after the cover's page or its colours changed). Returns false when none is placed. */
        fun refreshAll(ctx: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(ctx)
            var any = false
            listOf(CoverWidget(), CoverWideWidget(), CoverTallWidget()).forEach { w ->
                mgr.getAppWidgetIds(ComponentName(ctx, w.javaClass)).forEach { id ->
                    any = true
                    mgr.updateAppWidget(id, w.contentFor(ctx, id, WidgetStyles.of(ctx, id)))
                }
            }
            return any
        }
    }
}

/**
 * Redraws the placed screen cover widgets once a second, so the second hand and the seconds are live. The ticker runs only while a cover widget is placed.
 * The app process runs it; if the system stops the process, the minute alarm starts it again.
 */
object CoverTicker {
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            val c = app
            if (c == null || !CoverWidget.refreshAll(c)) { running = false; return }
            // the next redraw lands on the next whole second
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    fun start(ctx: Context) {
        app = ctx.applicationContext
        if (running) return
        running = true
        handler.post(tick)
    }
}

/** The screen cover in a wide cell (two across, one down): the same page as the cover. */
open class CoverWideWidget : CoverWidget()

/** The screen cover in a tall cell (one across, two down): the same page as the cover. */
open class CoverTallWidget : CoverWidget()
