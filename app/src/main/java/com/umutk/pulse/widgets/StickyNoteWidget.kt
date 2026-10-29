package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.stickynote.StickyNoteActivity
import com.umutk.pulse.extras.stickynote.StickyStore

/** A Windows 7 style sticky note on the home screen: each widget shows one note, a tap opens it in the editor. */
class StickyNoteWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val note = StickyStore.noteFor(ctx, id) ?: StickyStore.create(ctx).also { StickyStore.bind(ctx, id, it.id) }
            val open = Intent(ctx, StickyNoteActivity::class.java)
                .putExtra("note", note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val tap = PendingIntent.getActivity(ctx, 7_400 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(ctx.packageName, R.layout.widget_stickynote).apply {
                setInt(R.id.root, "setBackgroundColor", StickyStore.argb(note.color))
                setTextViewText(R.id.text, note.text)
                setOnClickPendingIntent(R.id.root, tap)
            }
            mgr.updateAppWidget(id, views)
        }
    }

    companion object {
        /** Redraws the widgets that show a note (not the ones still without a note); called when a note changes. */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(android.content.ComponentName(ctx, StickyNoteWidget::class.java)).forEach { id ->
                val note = StickyStore.noteFor(ctx, id) ?: return@forEach
                val open = Intent(ctx, StickyNoteActivity::class.java).putExtra("note", note.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val tap = PendingIntent.getActivity(ctx, 7_400 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                mgr.updateAppWidget(id, RemoteViews(ctx.packageName, R.layout.widget_stickynote).apply {
                    setInt(R.id.root, "setBackgroundColor", StickyStore.argb(note.color))
                    setTextViewText(R.id.text, note.text)
                    setOnClickPendingIntent(R.id.root, tap)
                })
            }
        }
    }

    // the note itself is kept when its widget goes; only the widget-to-note link is removed
    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { StickyStore.unbind(ctx, it) }
}
