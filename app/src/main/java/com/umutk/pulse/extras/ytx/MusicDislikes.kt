package com.umutk.pulse.extras.ytx

import android.content.Context

/** Songs marked "not for me" in the music player: they are left out of the queue, of what plays next and of the music home, and kept across restarts. */
object MusicDislikes {
    private val prefs by lazy { YTXTubeApplication.appContext.getSharedPreferences("music_dislikes", Context.MODE_PRIVATE) }
    private val ids = HashSet<String>()
    private var loaded = false

    private fun load() { if (loaded) return; loaded = true; ids += prefs.getStringSet("ids", emptySet()).orEmpty() }

    fun has(id: String): Boolean { load(); return id in ids }

    /** Marks or unmarks [id]; true when it is marked now. */
    fun toggle(id: String): Boolean {
        load()
        val now = if (id in ids) { ids.remove(id); false } else { ids.add(id); true }
        prefs.edit().putStringSet("ids", HashSet(ids)).apply()
        return now
    }

    fun clean(list: List<VideoItem>): List<VideoItem> = list.filter { !has(it.id) }
}
