package com.umutk.pulse.extras.music

/** One playlist entry. [path] is a file path, a content:// or http(s):// URI, as written in the playlist. */
data class Track(
    val path: String,
    val title: String? = null,
    val durationSec: Int = -1,
    val artist: String? = null,
    val album: String? = null,
    val albumId: Long = -1,
    val genre: String? = null,
    val year: Int = 0,
    val composer: String? = null,
    val dateAdded: Long = 0,
)

/** Pure Kotlin M3U / M3U8 reader and writer. No Android types, so it can be unit tested on the JVM. */
object M3uParser {

    /**
     * Parses playlist text. Relative entries are resolved against [baseDir] when it is given.
     * Unknown `#` directives are ignored. An `#EXTINF` without a following path is dropped.
     */
    fun parse(text: String, baseDir: String? = null): List<Track> {
        val tracks = mutableListOf<Track>()
        var pendingDuration = -1
        var pendingTitle: String? = null
        var hasPending = false

        for (rawLine in text.removePrefix("\uFEFF").split('\n')) {
            val line = rawLine.trim()
            when {
                line.isEmpty() || line == "#EXTM3U" -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substring("#EXTINF:".length)
                    pendingDuration = body.substringBefore(',').trim().toIntOrNull() ?: -1
                    pendingTitle = body.substringAfter(',', "").trim().ifEmpty { null }
                    hasPending = true
                }
                line.startsWith("#") -> Unit
                else -> {
                    tracks += Track(
                        path = resolve(line.replace('\\', '/'), baseDir),
                        title = if (hasPending) pendingTitle else null,
                        durationSec = if (hasPending) pendingDuration else -1,
                    )
                    pendingDuration = -1
                    pendingTitle = null
                    hasPending = false
                }
            }
        }
        return tracks
    }

    /**
     * Writes an M3U8 playlist. Tracks under [baseDir] are written as relative paths so the
     * folder can be moved as a whole; everything else is written as it is.
     */
    fun write(tracks: List<Track>, baseDir: String? = null): String = buildString {
        append("#EXTM3U\n")
        val base = baseDir?.replace('\\', '/')?.trimEnd('/')
        for (track in tracks) {
            val title = track.title?.replace(Regex("[\r\n]+"), " ")?.trim().orEmpty()
            append("#EXTINF:").append(track.durationSec).append(',').append(title).append('\n')
            val path = track.path.replace('\\', '/')
            val entry = if (base != null && path.startsWith("$base/")) path.substring(base.length + 1) else path
            append(entry.replace(Regex("[\r\n]+"), " ")).append('\n')
        }
    }

    private fun resolve(entry: String, baseDir: String?): String {
        if (baseDir == null || isAbsolute(entry)) return entry
        val base = baseDir.replace('\\', '/').trimEnd('/')
        // normalize() would collapse "http://" into "http:/", so URL bases are only joined.
        if (base.contains("://")) return "$base/$entry"
        return normalize("$base/$entry")
    }

    private fun isAbsolute(path: String): Boolean =
        path.startsWith("/") || path.contains("://") || Regex("^[A-Za-z]:/").containsMatchIn(path)

    /** Removes "." and ".." segments so "../music/a.mp3" resolves to a plain path. */
    private fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(segment)
            }
        }
        return out.joinToString("/", prefix = if (path.startsWith("/")) "/" else "")
    }
}
