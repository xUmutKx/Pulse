package com.umutk.pulse.extras.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Artists and albums of YouTube Music for the music player's online search: "manifest" shows the artist, their albums (each opens with its songs),
 * and "Play artist" plays every song of theirs that YouTube Music has, one after the other.
 */
object OnlineArtists {
    class Artist(val name: String, val thumb: String?, val url: String)
    class Album(val title: String, val artist: String, val thumb: String?, val url: String)

    private val yt get() = ServiceList.YouTube
    private fun init(ctx: Context) = runCatching { com.umutk.pulse.extras.ytx.YTXTubeApplication.init(ctx) }
    private fun best(l: List<org.schabi.newpipe.extractor.Image>) = l.maxByOrNull { it.height }?.url
    private fun idOf(url: String) = Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1) ?: Regex("youtu\\.be/([\\w-]{11})").find(url)?.groupValues?.get(1)
    private fun key(s: String) = s.lowercase().replace(" - topic", "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun search(q: String, filter: String) = SearchInfo.getInfo(yt, yt.searchQHFactory.fromQuery(q, listOf(filter), "")).relatedItems

    private fun hit(s: StreamInfoItem): OnlineMusic.Hit? {
        val id = idOf(s.url) ?: return null
        return OnlineMusic.Hit(s.name, s.uploaderName.orEmpty().replace(" - Topic", "", true).trim(), best(s.thumbnails), "yt", id)
    }

    /** The artists whose name fits [q] (the best first, three at most). */
    suspend fun artists(ctx: Context, q: String): List<Artist> = withContext(Dispatchers.IO) {
        init(ctx)
        runCatching { search(q, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS).filterIsInstance<ChannelInfoItem>().map { Artist(it.name, best(it.thumbnails), it.url) } }
            .getOrDefault(emptyList()).sortedBy { if (key(it.name) == key(q)) 0 else 1 }.take(3)
    }

    /** The albums (and EPs, singles) of [artist]. */
    suspend fun albums(ctx: Context, artist: String): List<Album> = withContext(Dispatchers.IO) {
        init(ctx)
        val k = key(artist)
        runCatching {
            search(artist, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS).filterIsInstance<PlaylistInfoItem>()
                .filter { key(it.uploaderName.orEmpty()).contains(k) || k.contains(key(it.uploaderName.orEmpty()).ifEmpty { "\u0000" }) }
                .map { Album(it.name, it.uploaderName.orEmpty(), best(it.thumbnails), it.url) }
        }.getOrDefault(emptyList()).distinctBy { key(it.title) }.take(24)
    }

    /** The songs of an album, in its order. */
    suspend fun albumSongs(ctx: Context, a: Album): List<OnlineMusic.Hit> = withContext(Dispatchers.IO) {
        init(ctx)
        runCatching { PlaylistInfo.getInfo(yt, a.url).relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { s -> hit(s)?.let { h -> if (h.thumb == null) OnlineMusic.Hit(h.title, h.artist.ifEmpty { a.artist }, a.thumb, "yt", h.ytId) else h } } }
            .getOrDefault(emptyList())
    }

    /** Every song of [artist] YouTube Music has: their songs, then the songs of their albums not listed yet; each title once. */
    suspend fun artistSongs(ctx: Context, artist: String): List<OnlineMusic.Hit> = withContext(Dispatchers.IO) {
        init(ctx)
        val k = key(artist)
        val out = LinkedHashMap<String, OnlineMusic.Hit>()
        fun add(h: OnlineMusic.Hit) { val t = key(h.title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")); if (t.isNotEmpty()) out.putIfAbsent(t, h) }
        runCatching { search(artist, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS).filterIsInstance<StreamInfoItem>().filter { key(it.uploaderName.orEmpty()).contains(k) }.mapNotNull { hit(it) }.forEach { add(it) } }
        for (a in albums(ctx, artist).take(8)) albumSongs(ctx, a).forEach { add(it) }
        out.values.toList()
    }
}
