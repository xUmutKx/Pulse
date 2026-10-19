package com.umutk.pulse.extras.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umutk.pulse.extras.lyrics.LyricsPanel

/** The one place the players show lyrics (embedded tags, .lrc beside the file, online). Swap the body when the lyrics API changes. */
@Composable
fun MusicLyricsPane(track: Track?, positionMs: Int, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    LyricsPanel(track, positionMs, modifier, onSeek = onSeek)
}
