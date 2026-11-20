package com.umutk.pulse.extras.stickynote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.umutk.pulse.PIcon
import com.umutk.pulse.widgets.StickyNoteWidget
import kotlinx.coroutines.delay

/**
 * A sticky note written on in place, as on the Windows 7 desktop: a tap on the note widget opens the note card itself over the home screen, where the widget is,
 * a little tilted like a paper note; the text is saved as it is typed and the widget follows. A tap beside it puts it back; the full editor is one button away.
 */
class StickyQuickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val id = intent.getStringExtra("note")
        val at = intent.sourceBounds
        setContent {
            StickyQuick(id, at?.left ?: -1, at?.top ?: -1, at?.width() ?: 0, at?.height() ?: 0, { finish() }) {
                startActivity(Intent(this, StickyNoteActivity::class.java).putExtra("note", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
            }
        }
    }

    override fun onStop() { super.onStop(); StickyNoteWidget.refresh(this) }
}

@Composable
private fun StickyQuick(id: String?, x: Int, y: Int, w: Int, h: Int, close: () -> Unit, full: () -> Unit) {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf(StickyStore.get(ctx, id) ?: StickyStore.create(ctx)) }
    var text by remember { mutableStateOf(note.text) }
    val focus = remember { FocusRequester() }
    fun save() { note = note.copy(text = text); StickyStore.put(ctx, note); StickyNoteWidget.refresh(ctx) }
    // saved a moment after typing stops, so the widget keeps up without a write on every letter
    LaunchedEffect(text) { delay(400); if (text != note.text) save() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val d = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().clickable(MutableInteractionSource(), null) { save(); close() }) {
        // the card is at least a comfortable size to write on, and stays inside the screen
        val cw = maxOf(with(d) { w.toDp() }, 260.dp).coerceAtMost(maxWidth - 24.dp)
        val ch = maxOf(with(d) { h.toDp() }, 220.dp).coerceAtMost(maxHeight - 48.dp)
        val cx = if (x < 0) (maxWidth - cw) / 2 else with(d) { x.toDp() }.coerceIn(12.dp, maxOf(12.dp, maxWidth - cw - 12.dp))
        val cy = if (y < 0) (maxHeight - ch) / 3 else with(d) { y.toDp() }.coerceIn(24.dp, maxOf(24.dp, maxHeight - ch - 24.dp))
        Column(Modifier.offset(cx, cy).size(cw, ch).graphicsLayer { rotationZ = -2.5f }.shadow(10.dp).background(Color(StickyStore.argb(note.color)))
            .clickable(MutableInteractionSource(), null) { }.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // the note's colour: a tap goes to the next one
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(StickyStore.argb((note.color + 1) % StickyStore.count))).clickable { note = note.copy(color = (note.color + 1) % StickyStore.count); save() })
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(26.dp).clickable { save(); full() }) { PIcon("edit", Color(0xFF333333), 20.dp) }
                Box(Modifier.size(26.dp).clickable { save(); close() }) { PIcon("close", Color(0xFF333333), 20.dp) }
            }
            BasicTextField(text, { text = it }, Modifier.fillMaxSize().padding(top = 6.dp).focusRequester(focus),
                textStyle = TextStyle(color = Color(0xFF222222), fontSize = 16.sp), cursorBrush = SolidColor(Color(0xFF333333)))
        }
    }
}
