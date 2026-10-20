package com.umutk.pulse.extras.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.umutk.pulse.ExtraToolActivity

/** What the next Notes screen opens: "" a new note, another value a note id, null the list. Read once by [NotesHub]. */
object NotesEntry { var request: String? = null }

/**
 * Entry point for the widget, the notification and the shortcut: sets [NotesEntry.request], then starts Notes.
 * It has no window of its own (NoDisplay theme), so no splash shows.
 */
class NotesEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotesEntry.request = if (intent.getBooleanExtra("new", false)) "" else intent.getStringExtra("open")
        startActivity(Intent(this, ExtraToolActivity::class.java).putExtra("route", "notes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
