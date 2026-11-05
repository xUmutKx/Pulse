package com.umutk.pulse.extras.video

/**
 * A clean name for a video file: "Dexter.S03E08.The.Damage.A.Man.Can.Do.1080p.ITA-ENG.BluRay.x265.AAC-V3SP4EV3R.mkv" reads "Dexter S03E08 · The Damage A Man Can Do",
 * "Dexter.S03E07.1080p.5.1Ch.BluRay.ReEnc-DeeJayAhmed.mkv" reads "Dexter S03E07", a film "The.Matrix.1999.1080p.BluRay.x264-GRP.mkv" reads "The Matrix (1999)".
 * The release tags (resolution, source, codec, audio, group) are left out. A name that fits none of this is only cleaned of its extension and dots.
 */
private val tagRe = Regex(
    "^(\\d{3,4}[pi]|4k|uhd|hdr\\d*|hdr|dv|bluray|blu-ray|brrip|bdrip|bd|web-?dl|web-?rip|web|webrip|hdtv|hdrip|dvdrip|dvd|remux|repack|proper|extended|unrated|" +
        "x26[45]|h\\.?26[45]|hevc|avc|xvid|divx|aac\\d*|ac3|eac3|dd\\d?|dts|dts-?hd|ddp\\d*|truehd|atmos|flac|mp3|5\\.1\\w*|7\\.1\\w*|2\\.0|ch|reenc|re-enc|multi|dual|ita|eng|ita-eng|tr|turkish|" +
        "hardsub|hc|10bit|8bit|yts|yify|rarbg|ettv|amzn|nf|dsnp|hmax|hulu|nvenc|ddp5)$", RegexOption.IGNORE_CASE,
)
private val episodeRe = Regex("(?i)^(.*?)[ ._-]+s(\\d{1,2})[ ._-]?e(\\d{1,3})(?:[ ._-]?e\\d{1,3})*(.*)$")
private val altEpisodeRe = Regex("(?i)^(.*?)[ ._-]+(\\d{1,2})x(\\d{2})(.*)$")
private val yearRe = Regex("^(.*?)[ ._(\\[-]+((?:19|20)\\d{2})(?![\\dpi])(.*)$")

private fun words(s: String): List<String> = s.replace('_', ' ').replace('.', ' ').split(' ', '-').map { it.trim('(', ')', '[', ']') }.filter { it.isNotEmpty() }

/** The words before the first release tag. */
private fun untilTag(tokens: List<String>): List<String> {
    val out = ArrayList<String>()
    for (t in tokens) { if (tagRe.matches(t)) break; out += t }
    return out
}

fun smartName(file: String): String {
    val base = file.substringBeforeLast('.', file).trim()
    if (base.isEmpty()) return file
    // a name with spaces already is a person's own: only the extension goes
    val spaced = base.count { it == ' ' } >= 3
    val m = episodeRe.find(base) ?: altEpisodeRe.find(base)
    if (m != null) {
        val series = words(m.groupValues[1]).joinToString(" ")
        val season = m.groupValues[2].toInt(); val ep = m.groupValues[3].toInt()
        val code = "S%02dE%02d".format(season, ep)
        val title = untilTag(words(m.groupValues[4].trimStart('.', ' ', '_', '-'))).joinToString(" ")
        if (series.isNotBlank()) return listOf("$series $code", title).filter { it.isNotBlank() }.joinToString(" · ")
    }
    if (!spaced) {
        val y = yearRe.find(base)
        if (y != null) {
            val title = words(y.groupValues[1]).joinToString(" ")
            if (title.isNotBlank()) return "$title (${y.groupValues[2]})"
        }
        val t = untilTag(words(base)).joinToString(" ")
        if (t.isNotBlank()) return t
    }
    return if (spaced) base else words(base).joinToString(" ").ifBlank { base }
}
