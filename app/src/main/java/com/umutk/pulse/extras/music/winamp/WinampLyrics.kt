package com.umutk.pulse.extras.music.winamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.umutk.pulse.T
import com.umutk.pulse.extras.lyrics.LyricLine
import com.umutk.pulse.extras.lyrics.LyricsPanel
import com.umutk.pulse.extras.lyrics.LyricsResult
import com.umutk.pulse.extras.lyrics.Lyrics
import com.umutk.pulse.extras.lyrics.guessArtistTitle
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.PlaybackState
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.extras.music.skin.skinText
import com.umutk.pulse.extras.music.skin.skinTextWidth
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** Lyrics pane in the skin: WINAMP_W skin pixels wide, PANE_H tall, text.bmp rows of ROW_H pixels. */
private const val PANE_H = 116
private const val PAD = 8
private const val ROW_H = 8
private const val ROWS = PANE_H / ROW_H
private const val CHARS = (WINAMP_W - 2 * PAD) / 5

/**
 * The lyrics of [track] drawn with the skin's own font and colours, [positionMs] picks the highlighted line.
 * Data comes from [Lyrics.forTrack] (the same source as the other players). Tap a synced line to seek to it.
 * [scaleDp] is dp per skin pixel, the same value the main window uses, so the pane lines up with it.
 */
@Composable
fun WinampLyricsPane(
    skin: WinampSkin,
    track: Track?,
    positionMs: Int,
    onSeek: (Int) -> Unit,
    scaleDp: Float,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var res by remember { mutableStateOf<LyricsResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(track?.path) {
        res = null
        if (track == null) { busy = false; return@LaunchedEffect }
        busy = true
        val (artist, title) = guessArtistTitle(track)
        res = Lyrics.forTrack(ctx, title, artist, track.album, track.durationSec.coerceAtLeast(0) * 1000L, track.path)
        busy = false
    }
    val r = res
    val rows = remember(r) { r?.let { wrapRows(it.lines) }.orEmpty() }
    val cur = if (r != null && r.synced) r.lines.indexOfLast { it.timeMs <= positionMs.toLong() } else -1
    val curRow = rows.indexOfFirst { it.first == cur }
    // keep the current line near the middle; unsynced text stays at the top
    val top = if (curRow < 0) 0 else (curRow - ROWS / 2).coerceIn(0, (rows.size - ROWS).coerceAtLeast(0))
    val msg = when {
        busy -> "..."
        r == null -> tr("NO LYRICS", "SÖZ YOK")
        r.lines.isEmpty() -> tr("NO LYRICS", "SÖZ YOK")
        else -> null
    }

    Canvas(
        modifier.size((WINAMP_W * scaleDp).dp, (PANE_H * scaleDp).dp)
            .semantics { contentDescription = tr("Lyrics", "Şarkı sözleri") }
            .pointerInput(r, top) {
                detectTapGestures { o ->
                    if (r == null || !r.synced) return@detectTapGestures
                    val row = top + (o.y * PANE_H / size.height / ROW_H).toInt()
                    rows.getOrNull(row)?.first?.takeIf { it >= 0 }?.let { onSeek(r.lines[it].timeMs.toInt()) }
                }
            },
    ) {
        val u = size.width / WINAMP_W
        drawRect(skin.playlist.normalBg)
        if (msg != null) {
            skinText(skin, msg, (WINAMP_W - skinTextWidth(msg)) / 2f, (PANE_H - 6) / 2f, u)
            return@Canvas
        }
        rows.drop(top).take(ROWS).forEachIndexed { i, (line, text) ->
            val y = i * ROW_H.toFloat()
            if (line == cur) drawRect(skin.playlist.selectedBg, Offset(0f, y * u), Size(size.width, ROW_H * u))
            skinText(skin, text, PAD.toFloat(), y + 1f, u)
        }
    }
}

/** Lyric lines cut into skin rows of at most [CHARS] characters; each row keeps the index of its line. */
internal fun wrapRows(lines: List<LyricLine>): List<Pair<Int, String>> {
    val out = ArrayList<Pair<Int, String>>()
    lines.forEachIndexed { i, l ->
        var rest = skinFold(l.text.trim())
        if (rest.isEmpty()) { out += i to ""; return@forEachIndexed }
        while (rest.length > CHARS) {
            val cut = rest.lastIndexOf(' ', CHARS).takeIf { it > 0 } ?: CHARS
            out += i to rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        out += i to rest
    }
    return out
}

/** The skin font has capitals and a few Turkish letters only: upper-case and fold the rest (unknown letters would be blanks). */
internal fun skinFold(s: String): String = s.uppercase().map {
    when (it) { 'Ç' -> 'C'; 'Ğ' -> 'G'; 'İ' -> 'I'; 'Ş' -> 'S'; 'Ü' -> 'U'; else -> it }
}.joinToString("")

/**
 * The Lyrics look of the music player: the track title and artist over its lyrics, in the app's look colours.
 * MusicHub shows it for MusicLook.LYRICS.
 */
@Composable
fun LyricsLookPage(engine: MusicPlayerEngine, st: PlaybackState, modifier: Modifier = Modifier) {
    var pos by remember { mutableStateOf(0) }
    LaunchedEffect(st.current, st.isPlaying) {
        while (true) {
            pos = engine.positionMs
            delay(if (st.isPlaying) 250L else 1000L)
        }
    }
    val t = st.current
    Column(modifier.fillMaxSize().background(T.bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(trackLabel(t), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        t?.shownArtist()?.let { Text(it, color = T.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        LyricsPanel(t, pos, Modifier.fillMaxSize().padding(top = 8.dp), onSeek = { engine.seekTo(it) })
    }
}
