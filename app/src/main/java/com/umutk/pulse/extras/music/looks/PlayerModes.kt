package com.umutk.pulse.extras.music.looks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.PlayModes
import com.umutk.pulse.extras.music.ShuffleKind
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

/** The shuffle icon: its own look for every kind (off dim, queue lit, all songs and albums lit with a small mark). */
@Composable
fun ShuffleGlyph(kind: ShuffleKind, lit: Color, dim: Color, size: Dp) {
    Box(Modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(if (kind == ShuffleKind.OFF) Icons.Rounded.Shuffle else Icons.Rounded.ShuffleOn, null, tint = if (kind == ShuffleKind.OFF) dim else lit, modifier = Modifier.fillMaxSize())
        PlayModes.badge(kind)?.let { Text(it, color = lit, fontSize = (size.value * .27f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd)) }
    }
}

/** The repeat icon: off dim, all lit, one lit with the "1". */
@Composable
fun RepeatGlyph(mode: Int, lit: Color, dim: Color, size: Dp) {
    Icon(when (mode) { Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn; Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn; else -> Icons.Rounded.Repeat }, null,
        tint = if (mode == Player.REPEAT_MODE_OFF) dim else lit, modifier = Modifier.size(size))
}

/** A pop-up menu with a black background, for the mode lists of every player. */
@Composable
fun ModesMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    DropdownMenu(expanded, onDismiss, modifier = Modifier.background(Color.Black), content = content)
}

@Composable
fun ModesItem(text: String, checked: Boolean, accent: Color, onClick: () -> Unit) {
    DropdownMenuItem({ Text((if (checked) "\u2713  " else "     ") + text, color = if (checked) accent else Color.White) }, onClick)
}

/** The shuffle modes as menu rows (black menu): off, queue, all songs, albums, and a new order for the one that is on. */
@Composable
fun ShuffleMenuItems(c: android.content.Context, accent: Color, dismiss: () -> Unit, onChange: () -> Unit = {}) {
    val k = PlayModes.shuffle
    ModesItem(tr("Shuffle off", "Karıştırma kapalı"), k == ShuffleKind.OFF, accent) { dismiss(); PlayModes.setShuffle(c, ShuffleKind.OFF); onChange() }
    ModesItem(tr("Shuffle the queue", "Sırayı karıştır"), k == ShuffleKind.QUEUE, accent) { dismiss(); PlayModes.setShuffle(c, ShuffleKind.QUEUE); onChange() }
    ModesItem(tr("Shuffle all songs", "Tüm şarkıları karıştır"), k == ShuffleKind.ALL, accent) { dismiss(); PlayModes.setShuffle(c, ShuffleKind.ALL); onChange() }
    ModesItem(tr("Shuffle albums", "Albümleri karıştır"), k == ShuffleKind.ALBUMS, accent) { dismiss(); PlayModes.setShuffle(c, ShuffleKind.ALBUMS); onChange() }
    if (k != ShuffleKind.OFF) ModesItem(tr("Shuffle again", "Yeniden karıştır"), false, accent) { dismiss(); PlayModes.reshuffle(c); onChange() }
}

/** The repeat modes as menu rows (black menu). */
@Composable
fun RepeatMenuItems(c: android.content.Context, mode: Int, accent: Color, dismiss: () -> Unit, onChange: (Int) -> Unit = {}) {
    fun set(m: Int) { dismiss(); MusicPlayerEngine.get(c).player.repeatMode = m; onChange(m) }
    ModesItem(tr("Repeat off", "Tekrar kapalı"), mode == Player.REPEAT_MODE_OFF, accent) { set(Player.REPEAT_MODE_OFF) }
    ModesItem(tr("Repeat all", "Tümünü tekrarla"), mode == Player.REPEAT_MODE_ALL, accent) { set(Player.REPEAT_MODE_ALL) }
    ModesItem(tr("Repeat this song", "Bu şarkıyı tekrarla"), mode == Player.REPEAT_MODE_ONE, accent) { set(Player.REPEAT_MODE_ONE) }
}

/** Shuffle button: a press goes to the next kind, a long press lists them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShuffleButton(engine: MusicPlayerEngine, on: Boolean, lit: Color, dim: Color, size: Dp, pad: Dp, onChange: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val c = androidx.compose.ui.platform.LocalContext.current
    PlayModes.load(c)
    Box {
        Box(Modifier.combinedClickable(onLongClick = { menu = true }) { PlayModes.cycleShuffle(c); onChange(PlayModes.shuffle != ShuffleKind.OFF) }.padding(pad)) { ShuffleGlyph(PlayModes.shuffle, lit, dim, size) }
        ModesMenu(menu, { menu = false }) { ShuffleMenuItems(c, lit, { menu = false }) { onChange(PlayModes.shuffle != ShuffleKind.OFF) } }
    }
}

/** Repeat button: a press goes to the next mode (off, all, one), a long press lists them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RepeatButton(engine: MusicPlayerEngine, mode: Int, lit: Color, dim: Color, size: Dp, pad: Dp, onChange: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val c = androidx.compose.ui.platform.LocalContext.current
    Box {
        Box(Modifier.combinedClickable(onLongClick = { menu = true }) { onChange(PlayModes.cycleRepeat(c)) }.padding(pad)) { RepeatGlyph(mode, lit, dim, size) }
        ModesMenu(menu, { menu = false }) { RepeatMenuItems(c, mode, lit, { menu = false }, onChange) }
    }
}
