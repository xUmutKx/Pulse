package com.umutk.pulse.extras.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.tr
import com.umutk.pulse.widgets.PulseWidget
import com.umutk.pulse.widgets.Tile
import com.umutk.pulse.widgets.WidgetSize
import com.umutk.pulse.widgets.WidgetStyle
import com.umutk.pulse.widgets.WidgetStyles

/**
 * Notes widget: the newest note (pinned first). A wide cell lists the next two titles too; a small cell shows the count.
 * A tap writes a new note. The tile is drawn at the cell's real size, so the whole window always shows.
 */
class NotesWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val notes = NotesStore.load(ctx).filter { it.trashedAt == 0L }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
        val wide = (WidgetSize.dp(ctx, com.umutk.pulse.widgets.textId)?.first ?: 250) >= 250
        val big = notes.firstOrNull()?.let { label(it) } ?: tr("New note", "Yeni not")
        val empty = tr("Tap to write", "Yazmak için dokun")
        val sub = if (wide) notes.drop(1).take(2).map { label(it) }.ifEmpty { listOf(empty) }
        else listOf(if (notes.isEmpty()) empty else tr("${notes.size} notes", "${notes.size} not"))
        return views(ctx, s, R.layout.widget_notes, tr("Notes", "Notlar"), big, "", Tile(big, sub)).apply {
            setOnClickPendingIntent(R.id.root, newNote(ctx))
        }
    }

    private fun label(n: Note) = if (n.locked) tr("Locked note", "Kilitli not") else NotesStore.titleOf(n).ifBlank { tr("Untitled", "Adsız") }

    private fun newNote(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0x4E01, Intent(c, NotesEntryActivity::class.java).putExtra("new", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        /** Redraws every placed notes widget; called after each save. */
        fun refresh(c: Context) {
            val mgr = AppWidgetManager.getInstance(c)
            val w = NotesWidget()
            mgr.getAppWidgetIds(ComponentName(c, NotesWidget::class.java)).forEach { id -> mgr.updateAppWidget(id, w.contentFor(c, id, WidgetStyles.of(c, id))) }
        }
    }
}
