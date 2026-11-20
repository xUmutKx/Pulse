package com.umutk.pulse.extras.code

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** Which notes are in code mode (and in which language): kept beside the notes, so the notes file itself does not change. */
object NoteCodePrefs {
    private fun p(c: android.content.Context) = c.getSharedPreferences("pulse_code", 0)
    /** Null: not a code note; "" a code note whose language is found from its text; else the scope of the language chosen. */
    fun get(c: android.content.Context, id: String): String? = p(c).getString("note_$id", null)
    fun set(c: android.content.Context, id: String, scope: String?) { p(c).edit().apply { if (scope == null) remove("note_$id") else putString("note_$id", scope) }.apply() }
}

/**
 * The small editor of a note in code mode: the same coloured editor as in Files, a few lines tall, with the language on the left and a button at the top right that
 * opens the whole editor (folding, search and replace, undo, symbols) on the same text.
 */
@Composable
fun NoteCodeBody(noteId: String, body: String, onBody: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var scope by remember(noteId) { mutableStateOf(NoteCodePrefs.get(ctx, noteId).orEmpty()) }
    // the full editor is open on the note's text; the small one is made again from the text it leaves
    var full by remember(noteId) { mutableStateOf(false) }
    var rev by remember(noteId) { mutableStateOf(0) }
    var pick by remember(noteId) { mutableStateOf(false) }
    val lang = remember(scope, rev) { PulseCode.common.firstOrNull { it.scope == scope } ?: PulseCode.detect("", body.take(400)) }
    val h = remember(noteId, rev) { CodeHandle() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(lang?.label ?: tr("Plain text", "Düz metin"), color = Look.accent, fontSize = 13.sp, modifier = Modifier.clickable { pick = !pick }.padding(vertical = 6.dp, horizontal = 4.dp))
            Box(Modifier.weight(1f))
            Box(Modifier.size(36.dp).clickable { full = true }, contentAlignment = Alignment.Center) { PIcon("fullscreen", T.text, 20.dp) }
        }
        if (pick) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PulseCode.common.forEach { l -> PChip(lang?.scope == l.scope, l.label) { scope = l.scope; NoteCodePrefs.set(ctx, noteId, l.scope); pick = false } }
        }
        key(rev) { CodePane(h, body, lang, true, false, 13f, Modifier.fillMaxWidth().height(280.dp), onText = onBody) }
    }
    // made once for each opening (a new one every recomposition would restart the editor)
    val src = remember(full) { CodeSource(tr("Note", "Not"), { body }, { onBody(it); true }) }
    if (full) CodeEditorHost(src, true, lang, { l -> scope = l.scope; NoteCodePrefs.set(ctx, noteId, l.scope) }) { full = false; rev++ }
}
