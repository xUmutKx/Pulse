package com.umutk.pulse.extras.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr

/**
 * The lyrics editor of the player, over the whole screen: every line with its time, the line that is playing lit. A line takes the song's
 * position as its time (the clock button, while the song plays), a line moves a tenth of a second at a time, and the whole song can move
 * half a second at once. The check keeps the lines (the song's tag or a .lrc beside it).
 */
@Composable
fun LyricsEditor(res: LyricsResult, positionMs: Long, onSave: (LyricsResult) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val lines = remember(res) { mutableStateListOf<LyricLine>().apply { addAll(res.lines) } }
    val listState = rememberLazyListState()
    val current = if (res.synced) lines.indexOfLast { it.timeMs in 0..positionMs } else -1
    fun setTime(i: Int, ms: Long) { lines[i] = lines[i].copy(timeMs = ms.coerceAtLeast(0L), words = emptyList()) }
    fun nudge(i: Int, d: Long) { val t = lines[i].timeMs; setTime(i, (if (t < 0) positionMs else t) + d) }
    fun shiftAll(d: Long) { lines.indices.forEach { i -> val t = lines[i].timeMs; if (t >= 0) setTime(i, t + d) } }
    fun save() {
        val synced = res.synced || lines.any { it.timeMs >= 0 }
        val out = if (synced) lines.sortedBy { if (it.timeMs < 0) Long.MAX_VALUE else it.timeMs } else lines.toList()
        onSave(LyricsResult(out.filter { it.text.isNotBlank() || it.timeMs >= 0 }, synced, res.source))
    }
    Column(modifier.background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { PIcon("close", Color.White, 22.dp) }
            Text(tr("Edit lyrics", "Sözleri düzenle"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { save() }) { PIcon("check", Color.White, 22.dp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Whole song", "Tüm şarkı"), color = Color.White.copy(alpha = .7f), fontSize = 13.sp, modifier = Modifier.weight(1f))
            EdChip("−.5 s") { shiftAll(-500) }
            EdChip("+.5 s") { shiftAll(500) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            itemsIndexed(lines) { i, l ->
                val now = i == current
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (now) Color.White.copy(alpha = .16f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(84.dp)) {
                        Text(if (l.timeMs < 0) "--:--.--" else stamp(l.timeMs), color = Color.White.copy(alpha = if (now) 1f else .6f), fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            EdChip("−.1") { nudge(i, -100) }
                            EdChip("+.1") { nudge(i, 100) }
                        }
                    }
                    Box(Modifier.size(32.dp).clickable { setTime(i, positionMs) }, contentAlignment = Alignment.Center) { PIcon("clock", Color.White, 18.dp) }
                    BasicTextField(
                        l.text, { lines[i] = lines[i].copy(text = it) },
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Box(Modifier.size(32.dp).clickable { lines.removeAt(i) }, contentAlignment = Alignment.Center) { PIcon("delete", Color.White.copy(alpha = .6f), 18.dp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.background(Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp)).clickable { lines.add(LyricLine(if (res.synced) positionMs else -1L, "")) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PIcon("add", Color.White, 18.dp)
            }
        }
    }
}

@Composable
private fun EdChip(t: String, onClick: () -> Unit) {
    Text(t, color = Color.White, fontSize = 11.sp, modifier = Modifier.background(Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 4.dp))
}

private fun stamp(ms: Long) = "%02d:%02d.%02d".format(ms / 60000, ms / 1000 % 60, ms / 10 % 100)
