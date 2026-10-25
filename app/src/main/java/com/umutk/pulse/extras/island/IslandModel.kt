package com.umutk.pulse.extras.island

import android.graphics.Bitmap

/** A button on the opened island. */
class IslandAct(val label: String, val run: () -> Unit)

/**
 * One thing the island shows: a song, a charger, a headset, a notification. [prio] decides which one is in front when several are there (higher first);
 * [ttl] is how long a short alert stays, in ms (0 = until its source takes it away, like the torch or a song).
 */
class IslandItem(
    val id: String, val kind: String, val title: String, val sub: String = "", val icon: Bitmap? = null, val color: Int = 0,
    val progress: Float = -1f, val level: Float = -1f, val acts: List<IslandAct> = emptyList(), val ttl: Long = 0L, val prio: Int = 1,
    val playing: Boolean = false, val onTap: (() -> Unit)? = null, val at: Long = System.currentTimeMillis(),
)
