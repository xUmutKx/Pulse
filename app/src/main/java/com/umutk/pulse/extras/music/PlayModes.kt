package com.umutk.pulse.extras.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** The kinds of shuffle: off, the queue after the playing song, every song of the library, or the albums in a random order (each album plays in its own order). */
enum class ShuffleKind { OFF, QUEUE, ALL, ALBUMS }

/**
 * Shuffle and repeat for every player, the notification and the widget, with one set of rules:
 * - a press goes to the next mode (shuffle: off, queue, all songs, albums; repeat: off, all, one); a long press (in the apps) offers the modes as a list;
 * - shuffle never touches the playing song and never jumps: only the songs after it get a new order, and that order stays (it is the queue itself);
 * - switching it off puts the old order back; "Shuffle again" makes a new order, also only from the playing song on.
 */
object PlayModes {
    var shuffle by mutableStateOf(ShuffleKind.OFF)
        private set
    /** The queue as it was before the first shuffle, to put back when shuffle goes off. */
    private var original: List<MediaItem>? = null
    private var loaded = false
    private fun sp(c: Context) = c.getSharedPreferences("music_modes", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        shuffle = ShuffleKind.values().getOrElse(sp(c).getInt("shuffle_kind", 0)) { ShuffleKind.OFF }
    }

    private fun keep(c: Context, k: ShuffleKind) { shuffle = k; sp(c).edit().putInt("shuffle_kind", k.ordinal).apply() }

    fun cycleShuffle(c: Context) { load(c); setShuffle(c, ShuffleKind.values()[(shuffle.ordinal + 1) % ShuffleKind.values().size]) }

    fun setShuffle(c: Context, kind: ShuffleKind) {
        load(c)
        val p = MusicPlayerEngine.get(c).player
        // the player's own random mode is never used: the order is made here, once
        p.shuffleModeEnabled = false
        if (p.mediaItemCount == 0) { keep(c, kind); return }
        if (kind == ShuffleKind.OFF) { restore(p); keep(c, kind); return }
        if (original == null) original = List(p.mediaItemCount) { p.getMediaItemAt(it) }
        keep(c, kind)
        reshuffle(c)
    }

    /** A new order for the songs after the playing one, in the kind that is on. */
    fun reshuffle(c: Context) {
        if (shuffle == ShuffleKind.OFF) return
        val e = MusicPlayerEngine.get(c); val p = e.player
        val n = p.mediaItemCount
        if (n == 0) return
        val cur = p.currentMediaItemIndex.coerceIn(0, n - 1)
        val curId = p.getMediaItemAt(cur).mediaId
        val played = (0 until cur).map { p.getMediaItemAt(it).mediaId }.toHashSet()
        val lib = MusicLibrary.tracks.value
        when (shuffle) {
            ShuffleKind.QUEUE -> {
                val items = (cur + 1 until n).map { p.getMediaItemAt(it) }
                e.replaceUpcoming(smartOrRandom(c, lib, curId, items))
            }
            ShuffleKind.ALL -> {
                val rest = lib.filter { it.path != curId && it.path !in played }
                e.replaceUpcomingTracks(rest.shuffled())
            }
            ShuffleKind.ALBUMS -> {
                val groups = lib.filter { it.path != curId && it.path !in played }.groupBy { it.album?.takeIf { a -> a.isNotBlank() } ?: it.path.substringBeforeLast('/') }
                e.replaceUpcomingTracks(groups.values.shuffled().flatten())
            }
            ShuffleKind.OFF -> {}
        }
    }

    /** With the smart shuffle on (Settings > Misc) the order follows mood, energy and ratings; otherwise it is plain chance. */
    private fun smartOrRandom(c: Context, lib: List<Track>, curId: String, items: List<MediaItem>): List<MediaItem> {
        if (items.size < 3 || !MusicSettings.smartShuffle(c)) return items.shuffled()
        return runCatching {
            val byPath = lib.associateBy { it.path }
            val tracks = listOf(byPath[curId] ?: Track(curId)) + items.map { byPath[it.mediaId] ?: Track(it.mediaId) }
            val order = SmartShuffle.order(c, tracks, 0, MusicSettings.shuffleRandomness(c) / 100f)
            order.filter { it > 0 }.map { items[it - 1] }.takeIf { it.size == items.size } ?: items.shuffled()
        }.getOrElse { items.shuffled() }
    }

    private fun restore(p: Player) {
        val was = original ?: return
        original = null
        val cur = p.currentMediaItemIndex
        val now = p.currentMediaItem?.mediaId ?: return
        val at = was.indexOfFirst { it.mediaId == now }
        if (at < 0) return
        p.removeMediaItems(cur + 1, p.mediaItemCount)
        if (cur > 0) p.removeMediaItems(0, cur)
        p.addMediaItems(0, was.subList(0, at))
        p.addMediaItems(was.subList(at + 1, was.size))
    }

    /** A new queue was set while shuffle is on: it is shuffled from its first song on, and that is the order to go back to. */
    fun onQueueSet(c: Context) {
        load(c)
        original = null
        if (shuffle == ShuffleKind.OFF) return
        val p = MusicPlayerEngine.get(c).player
        p.shuffleModeEnabled = false
        original = List(p.mediaItemCount) { p.getMediaItemAt(it) }
        reshuffle(c)
    }

    /** The small mark under a shuffle icon that tells the kind apart (none for the plain queue shuffle); the Poweramp menu shows it. */
    fun badge(k: ShuffleKind): String? = when (k) { ShuffleKind.ALL -> "ALL"; ShuffleKind.ALBUMS -> "ALB"; else -> null }

    /** Repeat: off, all, one, off ... Returns the new mode. */
    fun cycleRepeat(c: Context): Int {
        val p = MusicPlayerEngine.get(c).player
        p.repeatMode = when (p.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
        return p.repeatMode
    }
}
