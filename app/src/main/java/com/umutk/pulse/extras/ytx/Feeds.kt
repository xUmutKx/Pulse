package com.umutk.pulse.extras.ytx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


@Stable
class FeedState(
    private val scope: CoroutineScope,
    




    private val freshOnRefresh: Boolean = false,
    /** When set, the last feed is kept on the phone and shown at once at the next start, while the new one loads (as Flow's home feed cache). */
    private val cacheKey: String? = null,
    private val loader: suspend (next: String?) -> FeedPage,
) {
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var generation by mutableStateOf(0)
        private set

    var refreshing by mutableStateOf(false)
        private set
    private var next: String? = null
    private var loaded = false

    private val shown = HashSet<String>()
    private var job: Job? = null
    val hasMore get() = next != null
    /** Takes out what was just marked "not interested" (or its channel) without loading again. */
    fun dropHidden() { videos = videos.filter(Taste::ok) }

    fun ensureLoaded() {
        if (loaded || loading) return
        // the feed of the last time shows at once (read off the main thread); the new one replaces it when it is in
        if (cacheKey != null && videos.isEmpty()) scope.launch {
            val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FeedCache.read(cacheKey) }.filter(Taste::ok)
            if (videos.isEmpty() && cached.size >= 6) { videos = cached; generation++ }
        }
        refresh()
    }

    fun refresh() {
        loaded = true
        loading = true
        refreshing = videos.isNotEmpty()
        error = null
        job?.cancel()
        job = scope.launch {
            runCatching { if (freshOnRefresh && shown.isNotEmpty()) loadFresh() else loader(null) }
                .onSuccess {
                    // the ranking by taste is work for the processor: done off the main thread, so the list does not stutter while the page arrives
                    val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { if (freshOnRefresh) Taste.rank(it.videos) else it.videos.filter(Taste::ok) }
                    videos = list
                    next = it.next
                    generation++
                    if (freshOnRefresh) it.videos.forEach { v -> shown += v.id }
                    cacheKey?.let { k -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FeedCache.write(k, list.take(40)) } }
                }
                .onFailure { error = if (videos.isEmpty()) friendly(it) else null }
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        val token = next ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            runCatching { loader(token) }
                .onSuccess { page ->
                    val seen = videos.mapTo(HashSet()) { it.id }
                    val added = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { page.videos.filter { seen.add(it.id) && Taste.ok(it) } }
                    videos = videos + added
                    if (freshOnRefresh) added.forEach { v -> shown += v.id }
                    next = page.next
                }
                .onFailure { error = friendly(it) }
            loading = false
        }
    }

    



    private suspend fun loadFresh(): FeedPage {
        val first = loader(null)
        val fresh = LinkedHashMap<String, VideoItem>()
        var token = first.next
        first.videos.filter { it.id !in shown }.forEach { fresh[it.id] = it }
        var pages = 1
        while (fresh.size < FRESH_TARGET && token != null && pages < FRESH_PAGES) {
            val page = loader(token)
            page.videos.filter { it.id !in shown }.forEach { fresh.putIfAbsent(it.id, it) }
            token = page.next
            pages++
        }
        if (fresh.isEmpty()) return first
        return FeedPage(fresh.values.toList(), token)
    }

    fun reset() {
        job?.cancel()
        shown.clear()
        videos = emptyList(); next = null; loaded = false; loading = false; error = null
    }

    private companion object {
        const val FRESH_TARGET = 16
        const val FRESH_PAGES = 5
    }

    private fun friendly(t: Throwable) = when (t) {
        is NotSignedInException -> "You're signed out."
        else -> t.message?.takeIf { it.startsWith("YouTube") } ?: YouTube.friendlyError(t)
    }
}


@Stable
class LibraryData(scope: CoroutineScope) {

    val home = FeedState(scope, freshOnRefresh = true, cacheKey = "home") { InnerTube.homeFeed(it) }
    val subscriptions = FeedState(scope) { InnerTube.subscriptionsFeed(it) }
    val history = FeedState(scope) { if (Account.signedIn) InnerTube.history(it) else FeedPage(WatchedLog.all(), null) }
    var channels by mutableStateOf<List<ChannelRef>>(emptyList())
        private set
    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    private val scope = scope

    fun loadChannels() = scope.launch { runCatching { InnerTube.channels() }.onSuccess { channels = it } }
    fun loadPlaylists() = scope.launch { runCatching { InnerTube.playlists() }.onSuccess { playlists = it } }

    fun playlistFeed(id: String) = FeedState(scope) {
        if (id == "MDL") {
            val items = Downloads.musicDownloads.map { it.toVideoItem() }
            FeedPage(items, null)
        } else if (id == "DL") {
            FeedPage(emptyList(), null)
        } else {
            InnerTube.playlist(id, it)
        }
    }

    fun reset() {
        home.reset(); subscriptions.reset(); history.reset()
        channels = emptyList(); playlists = emptyList()
    }
}






@Composable
fun rememberScrollToTop(signal: Int): LazyListState {
    val state = rememberLazyListState()
    val initial = remember { signal }
    LaunchedEffect(signal) { if (signal != initial) state.animateScrollToItem(0) }
    return state
}


@Composable
fun FeedList(
    feed: FeedState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    header: LazyListScope.() -> Unit = {},
    scrollToTop: Int = 0,
    pullToRefresh: Boolean = true,

    indicatorTop: Dp = 0.dp,

    videos: List<VideoItem>? = null,

    filterScrollKey: Any? = null,

    channelAvatarSeed: Map<String, String?> = emptyMap(),
    /** Compact rows (a small picture at the left) instead of the big cards: the playlist page. */
    compact: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    // "not interested": the video (or its channel) leaves the list at once
    LaunchedEffect(Taste.rev) { if (Taste.rev > 0) feed.dropHidden() }
    // the filter of what the person gave up runs when the list or the choices change, not on every redraw of the screen
    val listVideos = remember(videos, feed.videos, Taste.rev) { (videos ?: feed.videos).let { l -> if (videos != null) l.filter(Taste::ok) else l } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(listVideos, channelAvatarSeed) {
        ChannelAvatars.resolve(scope, listVideos, channelAvatarSeed)
    }
    val list = rememberScrollToTop(scrollToTop)

    LaunchedEffect(feed.generation, filterScrollKey) { list.scrollToItem(0) }
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, listVideos.size) { if (nearEnd && listVideos.isNotEmpty()) feed.loadMore() }
    PullToRefresh(enabled = pullToRefresh, refreshing = feed.refreshing, onRefresh = feed::refresh, topPadding = indicatorTop) {
    LazyColumn(state = list, contentPadding = contentPadding) {
        header()
        // one kind of row, no item animation: the list reuses its rows as it scrolls and nothing is measured twice
        items(listVideos, key = { it.id }, contentType = { "video" }) { video ->
            if (compact) Box(Modifier.padding(horizontal = 16.dp)) { VideoRow(video, onClick = { onOpen(video) }) }
            else VideoCard(
                video,
                onClick = { onOpen(video) },
            )
        }
        when {


            feed.error != null && listVideos.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = "Couldn't load this",
                    body = feed.error.orEmpty(),
                    action = { PillButton("Try again", { feed.refresh() }) },
                )
            }
            feed.loading || feed.hasMore -> item {
                Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
    }
}





@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(enabled: Boolean, refreshing: Boolean, onRefresh: () -> Unit, topPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val c = MaterialTheme.colorScheme
    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pull,
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = pull, isRefreshing = refreshing,
                containerColor = c.primaryContainer, color = c.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
            )
        },
    ) { content() }
}


/** The last feed of a page kept on the phone (a small JSON file): shown at once at the next start while the new one loads. */
internal object FeedCache {
    private fun file(key: String) = java.io.File(YTXTubeApplication.appContext.filesDir, "ytx_feed_$key.json")

    fun read(key: String): List<VideoItem> = runCatching {
        val a = org.json.JSONArray(file(key).readText())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); if (id.isBlank()) return@mapNotNull null
            VideoItem(id, o.optString("t"), o.optString("c"), o.optString("cu").ifBlank { null }, o.optString("th").ifBlank { null }, o.optString("av").ifBlank { null },
                o.optLong("d"), o.optLong("v", -1), o.optString("u").ifBlank { null }, isLive = o.optBoolean("l"))
        }
    }.getOrDefault(emptyList())

    fun write(key: String, list: List<VideoItem>) {
        runCatching {
            val a = org.json.JSONArray()
            list.filter { it.localUri == null }.forEach { v ->
                a.put(org.json.JSONObject().put("id", v.id).put("t", v.title).put("c", v.channel).put("cu", v.channelUrl ?: "").put("th", v.thumbnail ?: "").put("av", v.avatar ?: "")
                    .put("d", v.durationSec).put("v", v.views).put("u", v.uploaded ?: "").put("l", v.isLive))
            }
            val f = file(key); val tmp = java.io.File(f.path + ".tmp"); tmp.writeText(a.toString()); tmp.renameTo(f)
        }
    }
}
