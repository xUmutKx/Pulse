package com.umutk.pulse.extras.stickynote

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.PIcon

/**
 * The note editor: the colour row and the text, saved at each change. The widget opens it with the note's id
 * (see StickyNoteActivity); without an id it opens the latest note, or a new one when there is none.
 */
@Composable
fun StickyNoteScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val id = (ctx as? Activity)?.intent?.getStringExtra("note")
    var note by remember { mutableStateOf(StickyStore.get(ctx, id) ?: StickyStore.latest(ctx) ?: StickyStore.create(ctx)) }
    fun edit(n: StickyNote) { note = n; StickyStore.put(ctx, n) }
    // a new note, and the delete of this one: the editor then shows the latest note left, or a fresh one
    fun newNote() { note = StickyStore.create(ctx) }
    fun delete() { StickyStore.remove(ctx, note.id); note = StickyStore.latest(ctx) ?: StickyStore.create(ctx) }
    val ink = Color(0xFF222222)

    Column(Modifier.fillMaxSize().background(Color(StickyStore.argb(note.color))).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", ink, 22.dp) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp).clickable { newNote() }, contentAlignment = Alignment.Center) { PIcon("add", ink, 20.dp) }
            Box(Modifier.size(36.dp).clickable { delete() }, contentAlignment = Alignment.Center) { PIcon("delete", ink, 20.dp) }
            ExtraLookIcon(ink)
            StickyStore.colors.indices.forEach { i ->
                Box(Modifier.padding(horizontal = 2.dp).size(24.dp).clip(CircleShape)
                    .background(Color(StickyStore.argb(i))).border(1.dp, Color(0x66000000), CircleShape)
                    .clickable { edit(note.copy(color = i)) })
            }
        }
        BasicTextField(
            value = note.text,
            onValueChange = { edit(note.copy(text = it)) },
            textStyle = TextStyle(color = ink, fontSize = 18.sp),
            cursorBrush = SolidColor(ink),
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        )
    }
}
