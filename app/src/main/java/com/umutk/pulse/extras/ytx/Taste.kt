package com.umutk.pulse.extras.ytx

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * YTXTube's own taste, kept on the phone: videos and channels the person said "not interested" to never show again, and the channels they watch
 * rise in the home feed (a stable re-order: the feed's own order stays among channels of the same weight).
 */
object Taste {
    private fun p() = runCatching { YTXTubeApplication.appContext.getSharedPreferences("ytx_taste", Context.MODE_PRIVATE) }.getOrNull()
    private var hiddenVideos: Set<String> = emptySet()
    private var hiddenChannels: Set<String> = emptySet()
    private var scores: MutableMap<String, Int> = HashMap()
    private var loaded = false
    /** Changes when something is hidden, so the lists drop it at once. */
    var rev by mutableStateOf(0)
        private set

    private fun load() {
        if (loaded) return
        val sp = p() ?: return
        hiddenVideos = sp.getStringSet("hv", emptySet()) ?: emptySet()
        hiddenChannels = sp.getStringSet("hc", emptySet()) ?: emptySet()
        scores = (sp.getString("sc", "") ?: "").split('\n').mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }.toMap(HashMap())
        loaded = true
    }

    private fun key(v: VideoItem) = v.channelUrl ?: v.channel

    fun ok(v: VideoItem): Boolean { load(); return v.id !in hiddenVideos && key(v) !in hiddenChannels }

    fun notInterested(v: VideoItem) { load(); hiddenVideos = hiddenVideos + v.id; scores[key(v)] = (scores[key(v)] ?: 0) - 3; save() }
    fun hideChannel(v: VideoItem) { load(); hiddenChannels = hiddenChannels + key(v); save() }

    /** A video was opened: its channel weighs a little more. */
    fun watched(v: VideoItem) { load(); scores[key(v)] = ((scores[key(v)] ?: 0) + 1).coerceAtMost(50); save() }

    /** The home feed in the person's order: hidden ones out, the channels they watch first. */
    fun rank(list: List<VideoItem>): List<VideoItem> { load(); return list.filter(::ok).sortedByDescending { (scores[key(it)] ?: 0).coerceIn(-10, 10) / 3 } }

    private fun save() {
        rev++
        val top = scores.entries.sortedByDescending { kotlin.math.abs(it.value) }.take(400)
        p()?.edit()?.putStringSet("hv", hiddenVideos.toList().takeLast(3000).toSet())?.putStringSet("hc", hiddenChannels)?.putString("sc", top.joinToString("\n") { it.key + "\t" + it.value })?.apply()
    }
}
