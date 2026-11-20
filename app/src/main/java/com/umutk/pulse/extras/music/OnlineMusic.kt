package com.umutk.pulse.extras.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.umutk.pulse.extras.music.poweramp.displayName

/**
 * YouTube Music and Spotify in the music player's search (a setting, off by default): the songs on the phone come first, then YouTube Music's,
 * then Spotify's (when signed in). A song that is already on the phone is not listed again. YouTube Music songs play in the music player itself (their audio stream); a Spotify song
 * is played from YouTube Music too (the same title and artist), since Spotify only lets its own app play its songs.
 */
object OnlineMusic {
    class Hit(val title: String, val artist: String, val thumb: String?, val source: String, val ytId: String?)

    private fun norm(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?\\]|official|audio|video|lyrics|- topic"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private suspend fun ytInit(ctx: Context) = withContext(Dispatchers.Main) { runCatching { com.umutk.pulse.extras.ytx.YTXTubeApplication.init(ctx) } }

    suspend fun youtube(ctx: Context, q: String): List<Hit> {
        ytInit(ctx)
        return runCatching { com.umutk.pulse.extras.ytx.YouTube.searchMusic(q) }.getOrDefault(emptyList()).take(10)
            .map { Hit(it.title, it.channel.replace(" - Topic", "", true).trim(), it.thumbnail, "yt", it.id) }
    }

    private fun hits(l: List<com.umutk.pulse.extras.ytx.VideoItem>) = l.map { Hit(it.title, it.channel.replace(" - Topic", "", true).trim(), it.thumbnail, "yt", it.id) }

    /** The YouTube Music account signed in in YTXTube (its own sign-in): its liked songs, what it played lately, its playlists. Empty when not signed in. */
    suspend fun ytSignedIn(ctx: Context): Boolean { ytInit(ctx); return runCatching { com.umutk.pulse.extras.ytx.Account.signedIn }.getOrDefault(false) }
    suspend fun ytLiked(ctx: Context): List<Hit> { ytInit(ctx); return hits(runCatching { com.umutk.pulse.extras.ytx.InnerTube.musicLiked() }.getOrDefault(emptyList())) }
    suspend fun ytHistory(ctx: Context): List<Hit> { ytInit(ctx); return hits(runCatching { com.umutk.pulse.extras.ytx.InnerTube.musicHistory() }.getOrDefault(emptyList())) }
    suspend fun ytPlaylists(ctx: Context): List<com.umutk.pulse.extras.ytx.PlaylistRef> { ytInit(ctx); return runCatching { com.umutk.pulse.extras.ytx.InnerTube.playlists() }.getOrDefault(emptyList()) }
    suspend fun ytPlaylist(ctx: Context, id: String): List<Hit> { ytInit(ctx); return hits(runCatching { com.umutk.pulse.extras.ytx.InnerTube.playlist(id).videos }.getOrDefault(emptyList())) }

    suspend fun spotify(ctx: Context, q: String): List<Hit> =
        runCatching { com.umutk.pulse.extras.spotify.SpotifyApi(ctx).search(q) }.getOrDefault(emptyList()).take(10)
            .map { Hit(it.title, it.artist, it.image, "sp", null) }

    /** The online hits that are not already among the [local] songs (title and artist), YouTube Music first, then Spotify; each song once. */
    fun merge(local: List<Track>, yt: List<Hit>, sp: List<Hit>): List<Hit> {
        val have = local.map { norm(it.displayName()) }.toHashSet()
        val seen = HashSet<String>()
        return (yt + sp).filter { h -> val k = norm(h.title); k.isNotEmpty() && k !in have && have.none { it.contains(k) } && seen.add(k + "|" + norm(h.artist)) }
    }

    /** The direct audio address of a YouTube Music song (the one the music player's own engine can play), or null. Blocking. */
    private fun streamUrl(id: String): String? = runCatching {
        com.umutk.pulse.extras.ytx.StreamSelection.selectAudio(com.umutk.pulse.extras.ytx.StreamCache.getOrLoad(id))?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The YouTube Music id of [h]: its own, or (a Spotify song) the best match of its title and artist. */
    private suspend fun idOf(ctx: Context, h: Hit): String? = h.ytId ?: youtube(ctx, "${h.artist} ${h.title}").firstOrNull()?.ytId

    private fun track(h: Hit, url: String): Track { OnlineArt.put(url, h.thumb); return Track(path = url, title = h.title, artist = h.artist) }

    /**
     * Plays [list] from [index] in the music player itself (its engine, its queue, its notification, whichever look is on): nothing else opens.
     * The tapped song starts first; the next ones are found in the background and join the queue as they are ready.
     */
    suspend fun playHere(ctx: Context, list: List<Hit>, index: Int): Boolean {
        val h = list.getOrNull(index) ?: return false
        ytInit(ctx)
        val first = withContext(Dispatchers.IO) { idOf(ctx, h)?.let { streamUrl(it) } } ?: return false
        val engine = MusicPlayerEngine.get(ctx)
        withContext(Dispatchers.Main) { engine.playQueue(listOf(track(h, first)), 0) }
        for (n in list.drop(index + 1).take(15)) {
            val u = withContext(Dispatchers.IO) { idOf(ctx, n)?.let { streamUrl(it) } } ?: continue
            withContext(Dispatchers.Main) { engine.addToQueue(track(n, u)) }
        }
        return true
    }

    /** Plays [h] in YTXTube's music player: the fallback when the song could not be opened in the music player (a Spotify song is looked up on YouTube Music first). */
    suspend fun play(ctx: Context, h: Hit) {
        val id = h.ytId ?: youtube(ctx, "${h.artist} ${h.title}").firstOrNull()?.ytId ?: return
        withContext(Dispatchers.Main) {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=$id"))
                    .setClassName(ctx, "com.umutk.pulse.extras.ytx.YtxActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
