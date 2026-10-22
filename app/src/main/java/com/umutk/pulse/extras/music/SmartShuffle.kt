package com.umutk.pulse.extras.music

import android.content.Context
import com.umutk.pulse.extras.music.stats.MusicStats
import kotlin.math.pow

/**
 * A shuffle that listens instead of rolling dice: each next song is drawn at random but weighted by how well it follows the one before it
 * (same mood, same energy, same genre, a neighbouring year), by the rating and stars you gave and by how often you play it, so a calm
 * song is not followed by a heavy one and the songs you rated low come rarely. [randomness] 0..1 flattens the weights: 1 is a plain shuffle.
 */
object SmartShuffle {
    /** The queue as an order of indices, the song at [start] first. */
    fun order(ctx: Context, queue: List<Track>, start: Int, randomness: Float): IntArray {
        val n = queue.size
        if (n <= 2) return IntArray(n) { it }
        val ratings = MusicStats.ratings(ctx); val fav = MusicStats.favourites(ctx)
        val stats = MusicStats.all(ctx).associateBy { it.path }
        val now = System.currentTimeMillis()
        val moods = queue.map { Recommendations.moodsOf(it).toSet() }
        val energy = queue.map { Recommendations.energyOf(it.genre) }
        val exp = 2.0 - 1.9 * randomness.coerceIn(0f, 1f)
        val rnd = java.util.Random()
        fun own(i: Int): Double {
            val t = queue[i]
            var w = 1.0
            val r = ratings[t.path] ?: 0
            if (r in 1..2) w *= .35 else if (r >= 4) w *= 1.0 + (r - 3) * .5
            if (t.path in fav) w *= 1.6
            stats[t.path]?.let { s ->
                w *= 1.0 + (s.plays.coerceAtMost(20)) * .03
                if (now - s.lastPlayed < 6 * 3600_000L) w *= .5   // just heard
            }
            return w
        }
        fun follow(a: Int, b: Int): Double {
            var w = 1.0
            if (moods[a].isNotEmpty() && moods[b].isNotEmpty()) w *= if (moods[a].any { it in moods[b] }) 2.2 else .45
            val ea = energy[a]; val eb = energy[b]
            if (ea != null && eb != null) w *= if (ea == eb) 1.8 else .25   // a calm song does not hand over to a heavy one
            val ga = queue[a].genre; val gb = queue[b].genre
            if (!ga.isNullOrBlank() && ga.equals(gb, true)) w *= 1.7
            val ya = queue[a].year; val yb = queue[b].year
            if (ya > 0 && yb > 0 && kotlin.math.abs(ya - yb) <= 5) w *= 1.25
            if (queue[a].artist != null && queue[a].artist == queue[b].artist) w *= .6   // not the same artist twice in a row
            return w
        }
        val left = (0 until n).filter { it != start }.toMutableList()
        val out = ArrayList<Int>(n); out += start
        var prev = start
        while (left.isNotEmpty()) {
            val ws = left.map { (own(it) * follow(prev, it)).coerceAtLeast(1e-6).pow(exp) }
            var pick = rnd.nextDouble() * ws.sum()
            var k = 0
            while (k < ws.lastIndex && pick > ws[k]) { pick -= ws[k]; k++ }
            prev = left.removeAt(k); out += prev
        }
        return out.toIntArray()
    }
}
