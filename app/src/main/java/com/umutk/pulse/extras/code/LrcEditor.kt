package com.umutk.pulse.extras.code

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import java.io.File
import java.util.Locale

/** One line of a lyrics file: its time in milliseconds (null while it has none) and its words. */
private class LrcLine(t: Long?, text: String) {
    var t by mutableStateOf(t)
    var text by mutableStateOf(text)
}

private val TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

private fun fmt(ms: Long): String {
    val m = ms / 60000; val s = (ms / 1000) % 60; val c = (ms % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", m, s, c)
}

private fun parse(text: String): Pair<List<String>, List<LrcLine>> {
    val meta = ArrayList<String>(); val lines = ArrayList<LrcLine>()
    text.lines().forEach { raw ->
        val l = raw.trimEnd('\r')
        val tags = TAG.findAll(l).toList()
        // header tags ([ar:..], [ti:..], [by:..]) stay as they are, at the top
        if (tags.isEmpty()) { if (Regex("""\[[a-zA-Z]+:.*]""").matches(l.trim())) meta += l else if (l.isNotBlank()) lines += LrcLine(null, l) else lines += LrcLine(null, "") ; return@forEach }
        val words = l.substring(tags.last().range.last + 1).trim()
        tags.forEach { m ->
            val frac = m.groupValues[3]
            val ms = m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 + (if (frac.isEmpty()) 0L else (frac.padEnd(3, '0').take(3)).toLong())
            lines += LrcLine(ms, words)
        }
    }
    // several times on one line became several lines: they go in time order, the lines with no time keep their places
    val timed = lines.filter { it.t != null }.sortedBy { it.t }
    return meta to (if (timed.size == lines.size) timed else lines)
}

private fun write(meta: List<String>, lines: List<LrcLine>): String =
    (meta + lines.map { l -> (l.t?.let { "[" + fmt(it) + "]" } ?: "") + l.text }).joinToString("\n")

private val AUDIO = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma")

/**
 * The editor of a lyrics file (.lrc): each line with its time, the song of the same name next to it plays here (or one that is picked), a tap on a time goes to that
 * moment, "Stamp" gives the chosen line the time of the song now and moves on to the next line, and the arrows nudge the time by a tenth of a second.
 */
@Composable
fun LrcEditorDialog(file: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val (meta0, lines0) = remember(file) { parse(runCatching { file.readText() }.getOrDefault("")) }
    val meta = remember(file) { meta0 }
    val lines = remember(file) { mutableStateListOf<LrcLine>().apply { addAll(lines0) } }
    var sel by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    fun attach(uri: Uri?, path: String?) {
        runCatching { player?.release() }
        player = runCatching {
            MediaPlayer().apply { if (path != null) setDataSource(path) else setDataSource(ctx, uri!!); prepare(); dur = duration.toLong() }
        }.getOrNull()
        playing = false
    }
    // the song of the same name beside the file
    LaunchedEffect(file) {
        val song = AUDIO.firstNotNullOfOrNull { e -> File(file.parentFile, file.nameWithoutExtension + "." + e).takeIf { it.isFile } }
        if (song != null) attach(null, song.path)
    }
    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) attach(u, null) }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }
    LaunchedEffect(player) {
        while (true) {
            val p = player
            if (p != null) { pos = runCatching { p.currentPosition.toLong() }.getOrDefault(0L); playing = runCatching { p.isPlaying }.getOrDefault(false) }
            kotlinx.coroutines.delay(100)
        }
    }
    val cur = lines.indexOfLast { it.t != null && it.t!! <= pos }
    val list = rememberLazyListState()
    LaunchedEffect(cur) { if (cur >= 0 && playing) list.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    fun save() { saved = runCatching { file.writeText(write(meta, lines)); true }.getOrDefault(false).also { if (!it) android.widget.Toast.makeText(ctx, tr("Could not save", "Kaydedilemedi"), android.widget.Toast.LENGTH_SHORT).show() } }

    Dialog(onDismissRequest = { onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 22.dp) }
                Text(file.name + if (saved) "" else " •", color = T.text, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 6.dp))
                Box(Modifier.size(40.dp).clickable { pickSong.launch("audio/*") }, contentAlignment = Alignment.Center) { PIcon("music", T.text, 20.dp) }
                Box(Modifier.size(40.dp).clickable { save() }, contentAlignment = Alignment.Center) { PIcon("save", T.accent, 22.dp) }
            }
            if (player != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(T.accent.copy(alpha = .2f)).clickable { player?.let { if (it.isPlaying) it.pause() else it.start() } }, contentAlignment = Alignment.Center) {
                        PIcon(if (playing) "pause" else "play", T.accent, 22.dp)
                    }
                    Slider(pos.toFloat(), { v -> player?.seekTo(v.toInt()); pos = v.toLong() }, valueRange = 0f..dur.coerceAtLeast(1L).toFloat(), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text(fmt(pos).dropLast(3) + " / " + fmt(dur).dropLast(3), color = T.sub, fontSize = 12.sp)
                }
                // the stamp: the chosen line gets the time of the song now, and the next line is chosen
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    @Composable fun btn(label: String, act: () -> Unit) {
                        Text(label, color = T.text, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(T.panel).clickable { act() }.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                    btn(tr("Stamp", "Zamanla")) { lines.getOrNull(sel)?.let { it.t = pos; saved = false; if (sel < lines.lastIndex) sel++ } }
                    btn("−0.1") { lines.getOrNull(sel)?.let { l -> l.t = ((l.t ?: pos) - 100).coerceAtLeast(0); saved = false } }
                    btn("+0.1") { lines.getOrNull(sel)?.let { l -> l.t = (l.t ?: pos) + 100; saved = false } }
                    btn(tr("Add", "Ekle")) { lines.add((sel + 1).coerceAtMost(lines.size), LrcLine(null, "")); sel = (sel + 1).coerceAtMost(lines.lastIndex); saved = false }
                }
            } else Text(tr("No song beside this file: pick one with the note", "Dosyanın yanında şarkı yok: notayla bir tane seç"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(Modifier.weight(1f), state = list) {
                itemsIndexed(lines) { i, l ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (i == sel) T.accent.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent).clickable { sel = i }.padding(vertical = 6.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l.t?.let { fmt(it) } ?: "--:--.--", color = if (i == cur) T.accent else T.sub, fontSize = 13.sp, modifier = Modifier.width(76.dp).clickable { l.t?.let { t -> player?.seekTo(t.toInt()); pos = t }; sel = i })
                        BasicTextField(l.text, { l.text = it; saved = false }, textStyle = TextStyle(color = if (i == cur) T.accent else T.text, fontSize = 15.sp), cursorBrush = SolidColor(T.accent), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
