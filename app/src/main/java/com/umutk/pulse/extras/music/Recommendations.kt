package com.umutk.pulse.extras.music

import com.umutk.pulse.extras.music.stats.TrackStat
import java.util.Calendar

/** One section of the daily picks. The UI makes the title from [pick]. */
sealed interface Pick {
    data class Artist(val name: String) : Pick
    data class Playlist(val name: String) : Pick
    data class Time(val part: Int) : Pick
    data class Energy(val high: Boolean) : Pick
}

data class PickSection(val pick: Pick, val tracks: List<Track>)

/** The phone's local day: [dayKey] seeds the random parts, [dayStartMs] is local midnight, [hour] the local hour. */
data class Today(val dayKey: Long, val dayStartMs: Long, val hour: Int)

/**
 * Recommendations from this device only: play counts, ratings, favourites, playlists and the library's genre tags.
 * Nothing leaves the phone. Pure functions, no Android types.
 */
object Recommendations {
    private const val SECTION_SIZE = 10

    fun today(now: Long = System.currentTimeMillis()): Today {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val hour = c.get(Calendar.HOUR_OF_DAY)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return Today(c.timeInMillis / 86_400_000L, c.timeInMillis, hour)
    }

    /** Time-of-day bucket of an hour: 0 morning, 1 afternoon, 2 evening, 3 night. */
    fun partOfDay(hour: Int) = when (hour) { in 5..11 -> 0; in 12..16 -> 1; in 17..20 -> 2; else -> 3 }

    /**
     * The 3x3 grid: 3 most played, 2 played before today, 2 top rated or favourite, 2 seeded random, then fill-ups.
     * With no plays yet it is the first 9 library tracks.
     */
    fun grid(library: List<Track>, stats: List<TrackStat>, ratings: Map<String, Int>, favourites: Set<String>, today: Today): List<Track> {
        val byPath = library.associateBy { it.path }
        val played = stats.filter { it.plays > 0 && it.path in byPath }
        if (played.isEmpty()) return library.take(9)
        val mostPlayed = played.sortedByDescending { it.plays }.map { it.path }
        val notToday = played.filter { it.lastPlayed in 1 until today.dayStartMs }.sortedByDescending { it.lastPlayed }.map { it.path }
        val rated = library.map { it.path }
            .filter { (ratings[it] ?: 0) >= 4 || it in favourites }
            .sortedByDescending { (ratings[it] ?: 0) + if (it in favourites) 1 else 0 }
        // the same seed all day: the random picks only change with the date
        val daily = library.shuffled(java.util.Random(today.dayKey)).map { it.path }
        return (mostPlayed.take(3) + notToday.take(2) + rated.take(2) + daily.take(2) + mostPlayed + daily)
            .distinct().take(9).mapNotNull { byPath[it] }
    }

    /**
     * Daily picks, empty sections left out: the top artist, the most played playlist, what is usually played at this
     * time of day, and the played tracks grouped by genre energy (genres without a known energy are skipped).
     */
    fun picks(library: List<Track>, stats: List<TrackStat>, playlists: Map<String, List<String>>, today: Today): List<PickSection> {
        val byPath = library.associateBy { it.path }
        val played = stats.filter { it.plays > 0 && it.path in byPath }
        if (played.isEmpty()) return emptyList()
        val seed = java.util.Random(today.dayKey)
        fun pick(list: List<Track>) = list.distinctBy { it.path }.shuffled(seed).take(SECTION_SIZE)
        val out = mutableListOf<PickSection>()

        played.filter { it.artist.isNotBlank() }.groupBy { it.artist }
            .mapValues { e -> e.value.sumOf { it.plays } }.maxByOrNull { it.value }?.let { (artist, _) ->
                out += PickSection(Pick.Artist(artist), pick(library.filter { it.artist == artist }))
            }

        val playCount = played.associate { it.path to it.plays }
        playlists.mapValues { (_, paths) -> paths.sumOf { playCount[it] ?: 0 } }
            .filterValues { it > 0 }.maxByOrNull { it.value }?.let { (name, _) ->
                out += PickSection(Pick.Playlist(name), pick(playlists.getValue(name).mapNotNull { byPath[it] }))
            }

        val part = partOfDay(today.hour)
        val timed = played.filter { it.parts.getOrElse(part) { 0 } > 0 }.sortedByDescending { it.parts.getOrElse(part) { 0 } }
        if (timed.isNotEmpty()) out += PickSection(Pick.Time(part), timed.take(SECTION_SIZE).mapNotNull { byPath[it.path] })

        val byEnergy = played.mapNotNull { s -> energyOf(byPath[s.path]?.genre)?.let { it to byPath.getValue(s.path) } }
            .groupBy({ it.first }, { it.second })
        for (high in listOf(true, false)) {
            byEnergy[high]?.let { out += PickSection(Pick.Energy(high), pick(it)) }
        }
        return out
    }

    private val highWords = listOf("rock", "metal", "punk", "dance", "electro", "edm", "techno", "house", "trance", "hip", "rap", "drum", "dubstep", "hardcore", "party", "workout")
    private val lowWords = listOf("ambient", "classical", "sleep", "soundtrack", "new age", "lounge", "acoustic", "jazz", "folk", "piano", "chill", "meditation", "relax")

    /** Energy from the genre tag: true high, false calm, null when the genre says nothing. */
    fun energyOf(genre: String?): Boolean? {
        val g = genre?.lowercase().orEmpty()
        return when {
            highWords.any { it in g } -> true
            lowWords.any { it in g } -> false
            else -> null
        }
    }
}
