package com.umutk.pulse.extras.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
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

    /** What each online stream address in the queue is: its song and YouTube id (the radio and the history need them). */
    private val byUrl = java.util.concurrent.ConcurrentHashMap<String, Pair<Hit, String>>()
    private var radioJob: kotlinx.coroutines.Job? = null

    private fun track(h: Hit, url: String, id: String): Track { OnlineArt.put(url, h.thumb); byUrl[url] = h to id; return Track(path = url, title = h.title, artist = h.artist) }

    private fun hitOf(v: com.umutk.pulse.extras.ytx.VideoItem) = Hit(v.title, v.channel.replace(" - Topic", "", true).trim(), v.thumbnail, "yt", v.id)

    /** The songs the station refills from: the seed and what YouTube placed closest to it (Flow's MusicRadioPlanner, GPL-3.0: refilling from the newest tail instead drifts away from the seed's genre). */
    private var radioAnchors: List<String> = emptyList()
    private val usedAnchors = HashSet<String>()
    private const val MAX_PER_ARTIST = 2

    private fun artistKey(a: String) = a.lowercase().substringBefore(",").substringBefore("&").substringBefore(" feat").trim()

    /** Adds the next songs of YouTube Music's radio for [seed] to the queue: none that was played lately or is queued, and no artist more than twice (as Flow's stations). */
    private suspend fun addRadio(ctx: Context, engine: MusicPlayerEngine, seed: String, artistHint: String) {
        val played = OnlineHistory.all(ctx).take(60).map { it.id }.toSet()
        val queue = engine.state.value.queue
        val queued = queue.mapNotNull { byUrl[it.path]?.second }.toSet()
        val counts = HashMap<String, Int>()
        queue.forEach { t -> byUrl[t.path]?.first?.artist?.let { counts.merge(artistKey(it), 1, Int::plus) } }
        val all = com.umutk.pulse.extras.ytx.MusicRadio.after(seed).filter { it.id !in played && it.id !in queued }
        if (radioAnchors.isEmpty() && all.isNotEmpty()) { radioAnchors = (listOf(seed) + all.map { it.id }).distinct().take(8); usedAnchors.clear(); usedAnchors.add(seed) }
        var next = all.map { hitOf(it) }.filter { h -> val k = artistKey(h.artist); val c = counts.getOrDefault(k, 0); if (c >= MAX_PER_ARTIST) false else { counts[k] = c + 1; true } }.take(8)
        // the radio gave nothing (no mix for this song): songs of the same artist, as before
        if (next.isEmpty() && artistHint.isNotBlank()) next = runCatching { youtube(ctx, "$artistHint songs") }.getOrDefault(emptyList()).filter { it.ytId != null && it.ytId !in played && it.ytId !in queued }.take(8)
        for (n in next) {
            val id = n.ytId ?: continue
            val u = withContext(Dispatchers.IO) { streamUrl(id) } ?: continue
            withContext(Dispatchers.Main) { engine.addToQueue(track(n, u, id)) }
        }
    }

    /**
     * Keeps the queue going while online songs play: every song that starts is written to the history, and when fewer than four are left the queue is filled from
     * YouTube Music's radio of the last one. It stops by itself when a song of the phone starts.
     */
    private fun startRadio(ctx: Context, engine: MusicPlayerEngine, seedId: String) {
        radioJob?.cancel()
        radioAnchors = emptyList(); usedAnchors.clear()
        radioJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            var fetching = false
            engine.state.takeWhile { st -> st.current == null || byUrl.containsKey(st.current.path) }.collect { st ->
                st.current?.path?.let { byUrl[it] }?.let { (hit, id) -> OnlineHistory.record(ctx, id, hit.title, hit.artist, hit.thumb) }
                val left = st.queue.size - st.index - 1
                if (!fetching && st.queue.isNotEmpty() && left < 4) {
                    fetching = true
                    try {
                        val last = st.queue.lastOrNull()?.path?.let { byUrl[it] }
                        // the next anchor not used yet; with none left (or none known yet) the last queued song
                        val anchor = (if (radioAnchors.isEmpty()) null else radioAnchors.firstOrNull { it !in usedAnchors }?.also { usedAnchors.add(it) }) ?: last?.second ?: seedId
                        addRadio(ctx, engine, anchor, last?.first?.artist.orEmpty())
                    } finally { fetching = false }
                }
            }
        }
    }

    /** "For you": the radio of what was played lately (and of the liked songs when signed in), mixed, without what was played already. */
    suspend fun forYou(ctx: Context): List<Hit> {
        ytInit(ctx)
        val hist = OnlineHistory.all(ctx)
        val seeds = (hist.take(3).map { it.id } + (if (ytSignedIn(ctx)) ytLiked(ctx).take(2).mapNotNull { it.ytId } else emptyList())).distinct()
        if (seeds.isEmpty()) return youtube(ctx, "top hits this week")
        val played = hist.take(80).map { it.id }.toSet()
        val lists = seeds.map { s -> com.umutk.pulse.extras.ytx.MusicRadio.after(s).filter { it.id !in played }.map { hitOf(it) } }
        val out = LinkedHashMap<String, Hit>()
        var i = 0
        while (out.size < 40 && lists.any { i < it.size }) { lists.forEach { l -> l.getOrNull(i)?.let { h -> h.ytId?.let { id -> out.putIfAbsent(id, h) } } }; i++ }
        return out.values.toList().ifEmpty { youtube(ctx, "top hits this week") }
    }

    /** The online songs played lately (newest first). */
    fun history(ctx: Context): List<Hit> = OnlineHistory.all(ctx).map { Hit(it.title, it.artist, it.thumb, "yt", it.id) }

    /**
     * Plays [list] from [index] in the music player itself (its engine, its queue, its notification, whichever look is on): nothing else opens.
     * The tapped song starts first; the next ones are found in the background and join the queue as they are ready.
     */
    suspend fun playHere(ctx: Context, list: List<Hit>, index: Int): Boolean {
        val h = list.getOrNull(index) ?: return false
        ytInit(ctx)
        radioJob?.cancel()
        val seedId = withContext(Dispatchers.IO) { idOf(ctx, h) } ?: return false
        val first = withContext(Dispatchers.IO) { streamUrl(seedId) } ?: return false
        val engine = MusicPlayerEngine.get(ctx)
        withContext(Dispatchers.Main) { engine.playQueue(listOf(track(h, first, seedId)), 0) }
        // the radio runs from the first song on (it fills the queue behind the rest of the list and goes on for as long as online songs play)
        startRadio(ctx, engine, seedId)
        val rest = list.drop(index + 1).take(15)
        for (n in rest) {
            val id = withContext(Dispatchers.IO) { idOf(ctx, n) } ?: continue
            val u = withContext(Dispatchers.IO) { streamUrl(id) } ?: continue
            withContext(Dispatchers.Main) { engine.addToQueue(track(n, u, id)) }
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
