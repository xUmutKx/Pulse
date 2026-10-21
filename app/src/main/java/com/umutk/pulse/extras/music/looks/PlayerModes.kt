package com.umutk.pulse.extras.music.looks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.tr

/** The queue order before the last shuffle, so switching shuffle off puts it back. */
private var orderBefore: List<MediaItem>? = null

/** Shuffles the queue once into a fixed order (the playing song stays where it is), instead of a random pick at every skip. */
fun MusicPlayerEngine.shuffleQueueOnce() {
    val p = player
    val n = p.mediaItemCount
    if (n < 2) return
    val all = List(n) { p.getMediaItemAt(it) }
    val cur = p.currentMediaItemIndex
    orderBefore = all
    val rest = all.filterIndexed { i, _ -> i != cur }.shuffled()
    p.shuffleModeEnabled = false
    p.removeMediaItems(cur + 1, n)
    p.removeMediaItems(0, cur)
    p.addMediaItems(rest)
}

/** Puts the queue back in the order it had before [shuffleQueueOnce]. */
fun MusicPlayerEngine.restoreQueueOrder() {
    val p = player
    val was = orderBefore ?: return
    orderBefore = null
    val cur = p.currentMediaItemIndex
    val now = p.getMediaItemAt(cur).mediaId
    val at = was.indexOfFirst { it.mediaId == now }
    if (at < 0) return
    p.removeMediaItems(cur + 1, p.mediaItemCount)
    p.removeMediaItems(0, cur)
    p.addMediaItems(0, was.subList(0, at))
    p.addMediaItems(was.subList(at + 1, was.size))
}

/**
 * Shuffle button. A tap shuffles the queue once into a fixed order, or puts the old order back; a long press offers the choices:
 * shuffle the queue, play all songs shuffled, off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShuffleButton(engine: MusicPlayerEngine, on: Boolean, lit: Color, dim: Color, size: Dp, pad: Dp, onChange: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Icon(Icons.Rounded.Shuffle, null, tint = if (on) lit else dim, modifier = Modifier.size(size)
            .combinedClickable(onLongClick = { menu = true }) { if (on) engine.restoreQueueOrder() else engine.shuffleQueueOnce(); onChange(!on) }.padding(pad))
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(tr("Shuffle the queue", "Sırayı karıştır")) }, { menu = false; engine.restoreQueueOrder(); engine.shuffleQueueOnce(); onChange(true) })
            DropdownMenuItem({ Text(tr("All songs, shuffled", "Tüm şarkılar, karışık")) }, {
                menu = false
                val all = MusicLibrary.tracks.value
                if (all.isNotEmpty()) { engine.player.shuffleModeEnabled = false; engine.playQueue(all.shuffled(), 0); onChange(true) }
            })
            DropdownMenuItem({ Text(tr("Off", "Kapalı")) }, { menu = false; engine.restoreQueueOrder(); engine.player.shuffleModeEnabled = false; onChange(false) })
        }
    }
}

/** Repeat button. A tap switches repeat all on or off; a long press offers off, all songs or the single song. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepeatButton(engine: MusicPlayerEngine, mode: Int, lit: Color, dim: Color, size: Dp, pad: Dp, onChange: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    fun set(m: Int) { engine.player.repeatMode = m; onChange(m) }
    Box {
        Icon(if (mode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, tint = if (mode != 0) lit else dim,
            modifier = Modifier.size(size).combinedClickable(onLongClick = { menu = true }) { set(if (mode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF) }.padding(pad))
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(tr("Off", "Kapalı")) }, { menu = false; set(Player.REPEAT_MODE_OFF) })
            DropdownMenuItem({ Text(tr("All songs", "Tüm şarkılar")) }, { menu = false; set(Player.REPEAT_MODE_ALL) })
            DropdownMenuItem({ Text(tr("This song", "Bu şarkı")) }, { menu = false; set(Player.REPEAT_MODE_ONE) })
        }
    }
}
