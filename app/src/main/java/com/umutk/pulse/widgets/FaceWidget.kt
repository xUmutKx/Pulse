package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umutk.pulse.MainActivity
import com.umutk.pulse.R

/** A clock or day face widget: one provider per face, so each shows up on its own in the launcher's picker. */
abstract class FaceWidget(val look: Int) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, views(ctx, look, WidgetAccent.of(ctx, it), it)) }
        if (look == 16) scheduleTick(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: android.os.Bundle?) =
        mgr.updateAppWidget(id, views(ctx, look, WidgetAccent.of(ctx, id), id))

    override fun onDeleted(ctx: Context, ids: IntArray) = WidgetAccent.remove(ctx, ids)

    override fun onDisabled(ctx: Context) { if (look == 16) alarm(ctx).cancel(tickIntent(ctx)) }

    /** Clock, zone and date changes redraw the faces that draw the date or time into a bitmap; the analog ones turn by themselves. */
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, TICK -> {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, javaClass))
                ids.forEach { mgr.updateAppWidget(it, views(ctx, look, WidgetAccent.of(ctx, it), it)) }
                if (look == 16 && ids.isNotEmpty()) scheduleTick(ctx)
            }
        }
    }

    // shortcut: the sun-and-time tile prints HH:mm into a bitmap, so it is redrawn by an inexact per-minute alarm (may lag in Doze); the analog faces need none
    private fun alarm(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    private fun tickIntent(ctx: Context) = PendingIntent.getBroadcast(ctx, look, Intent(ctx, javaClass).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun scheduleTick(ctx: Context) =
        alarm(ctx).setAndAllowWhileIdle(android.app.AlarmManager.RTC, (System.currentTimeMillis() / 60_000 + 1) * 60_000, tickIntent(ctx))

    /** The face at the current time in [accent]; analog faces are a live AnalogClock (dial and hands as separate translucent bitmaps), the others one bitmap. A tap opens the matching app. */
    fun views(ctx: Context, look: Int, accent: Int, id: Int = 0): RemoteViews {
        val frost = Faces.looks[look]?.frost == true
        // like the previews: the frosted clock always follows the opacity setting, the others only once the widget has its own
        val op = if (frost || WidgetOver.has(ctx, id)) WidgetOver.opacity(ctx, id).coerceIn(0f, 1f) else 1f
        val v = if (Faces.analog(look)) {
            val px = (WidgetSize.dp(ctx, id)?.let { minOf(it.first, it.second) * ctx.resources.displayMetrics.density }?.toInt() ?: 400).coerceIn(160, 512)
            RemoteViews(ctx.packageName, R.layout.widget_face_analog).apply {
                // the frosted clock sits on the Windows 11 tile like the date widget, so it draws in that tile's light accent
                val acc = if (frost) Skins.win11Accent(ctx, id) else accent
                fun ic(part: Int) = android.graphics.drawable.Icon.createWithBitmap(Faces.draw(look, px, 0, acc, part))
                val dial = Faces.draw(look, px, 0, acc, 1)
                val base = if (frost) Skins.win11Dial(ctx, id, px) else fade(dial, op)
                setIcon(R.id.clock, "setDial", android.graphics.drawable.Icon.createWithBitmap(base))
                setIcon(R.id.clock, "setHourHand", ic(2))
                setIcon(R.id.clock, "setMinuteHand", ic(3))
                setIcon(R.id.clock, "setSecondHand", ic(4))
            }
        } else RemoteViews(ctx.packageName, R.layout.widget_face).apply {
            setImageViewBitmap(R.id.face, fade(Faces.draw(look, 320, batteryPercent(ctx), accent), op))
        }
        v.setOnClickPendingIntent(R.id.root, WidgetTap.face(ctx, look, id))
        return v
    }

    private fun fade(b: android.graphics.Bitmap, a: Float): android.graphics.Bitmap {
        if (a >= 1f) return b
        val o = android.graphics.Bitmap.createBitmap(b.width, b.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(o).drawBitmap(b, 0f, 0f, android.graphics.Paint().apply { alpha = (a * 255).toInt() })
        return o
    }

    /** Charge in percent from the sticky battery broadcast; 0 when it is not available. */
    private fun batteryPercent(ctx: Context): Int {
        val i = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        return if (scale > 0) level * 100 / scale else 0
    }

    companion object {
        private const val TICK = "com.umutk.pulse.FACE_TICK"

        /** Pushes the current time to every placed face widget; called with the other widget refreshes. */
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            FaceWidgets.all.forEach { w ->
                mgr.getAppWidgetIds(android.content.ComponentName(ctx, w.javaClass)).forEach { id ->
                    mgr.updateAppWidget(id, w.views(ctx, w.look, WidgetAccent.of(ctx, id), id))
                }
            }
        }
    }
}
