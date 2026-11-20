package com.umutk.pulse.extras.ytx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page


val LocalChannelOpener = staticCompositionLocalOf<(String) -> Unit> { {} }

enum class ChannelTab(val label: String) {
    Home("Home"),
    Videos("Videos"),
    Shorts("Shorts"),
    Live("Live"),
    Playlists("Playlists"),
    Community("Community"),
}

@Stable
class ChannelState(val url: String, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subscribed by mutableStateOf<Boolean?>(null)
        private set
    var activeTab by mutableStateOf(ChannelTab.Home)
        private set
    var liveVideos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var liveLoading by mutableStateOf(false)
        private set
    var shorts by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var shortsLoading by mutableStateOf(false)
        private set
    var communityPosts by mutableStateOf<List<CommunityPost>>(emptyList())
        private set
    var communityLoading by mutableStateOf(false)
        private set

    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    var playlistsLoading by mutableStateOf(false)
        private set
    /** The orders the channel offers for its videos (Latest, Popular, Oldest) and the one shown. */
    var sorts by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var sortIndex by mutableStateOf(0)
        private set

    private var next: String? = null
    private var shortsNext: String? = null
    val hasMore get() = next != null

    init { load() }

    fun selectTab(tab: ChannelTab) {
        activeTab = tab
        when (tab) {
            ChannelTab.Shorts -> if (shorts.isEmpty() && !shortsLoading) loadShorts()
            ChannelTab.Live -> if (liveVideos.isEmpty() && !liveLoading) loadLive()
            ChannelTab.Community -> if (communityPosts.isEmpty() && !communityLoading) loadCommunity()
            ChannelTab.Playlists -> if (playlists.isEmpty() && !playlistsLoading) loadPlaylists()
            ChannelTab.Videos, ChannelTab.Home -> {}
        }
    }

    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching { YouTube.channel(url) }
                .onSuccess { base ->
                    var d = base
                    details = d
                    if (Account.signedIn) launch { subscribed = runCatching { InnerTube.channelSubscribed(d.id) }.getOrNull() }
                    runCatching { YouTube.channelVideos(d, null) }
                        .onSuccess { p ->
                            videos = p.videos
                            next = p.next
                            sorts = p.sorts; sortIndex = 0
                            p.channelHeader?.let { d = d.withHeader(it) }
                            details = d
                        }
                        .onFailure {
                            details = d
                            error = YouTube.friendlyError(it)
                        }
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadPlaylists() {
        val d = details ?: return
        playlistsLoading = true
        scope.launch {
            playlists = runCatching { InnerTube.channelPlaylists(d.id) }.getOrDefault(emptyList())
            playlistsLoading = false
        }
    }

    /** The videos again in the order picked (Latest, Popular, Oldest). */
    fun sortBy(i: Int) {
        val d = details ?: return
        val tok = sorts.getOrNull(i)?.second ?: return
        if (i == sortIndex) return
        sortIndex = i
        loading = true
        videos = emptyList(); next = null
        scope.launch {
            runCatching { YouTube.channelVideos(d, tok) }.onSuccess { p -> videos = p.videos; next = p.next }
            loading = false
        }
    }

    fun loadLive() {
        val d = details ?: return
        liveLoading = true
        scope.launch {
            runCatching { YouTube.channelLive(d, null) }
                .onSuccess { p ->
                    liveVideos = p.videos.ifEmpty { videos.filter { it.isLive } }
                }
                .onFailure {
                    liveVideos = videos.filter { it.isLive }
                }
            liveLoading = false
        }
    }

    fun loadShorts() {
        val d = details ?: return
        if (shortsLoading) return
        shortsLoading = true
        scope.launch {
            runCatching { YouTube.channelShorts(d, null) }
                .onSuccess { p ->
                    shorts = p.videos
                    shortsNext = p.next
                }
            shortsLoading = false
        }
    }

    fun loadMoreShorts() {
        val d = details ?: return
        val page = shortsNext ?: return
        if (shortsLoading) return
        shortsLoading = true
        scope.launch {
            runCatching { YouTube.channelShorts(d, page) }
                .onSuccess { p ->
                    val seen = shorts.mapTo(HashSet()) { it.id }
                    shorts = shorts + p.videos.filter { seen.add(it.id) }
                    shortsNext = p.next
                }
            shortsLoading = false
        }
    }

    fun loadCommunity() {
        val d = details ?: return
        communityLoading = true
        scope.launch {
            runCatching { YouTube.channelCommunity(d, null) }
                .onSuccess { p ->
                    communityPosts = p.posts
                }
                .onFailure {
                    communityPosts = emptyList()
                }
            communityLoading = false
        }
    }

    fun loadMore() {
        val d = details ?: return
        val page = next ?: return
        if (loading) return
        loading = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, page) }
                .onSuccess { p ->
                    val seen = videos.mapTo(HashSet()) { it.id }
                    videos = videos + p.videos.filter { seen.add(it.id) }
                    next = p.next
                }
            loading = false
        }
    }

    fun toggleSubscribe(onError: (String) -> Unit) {
        val id = details?.id ?: return
        val before = subscribed ?: return
        subscribed = !before
        scope.launch {
            runCatching { InnerTube.setSubscribed(id, !before) }
                .onFailure { subscribed = before; onError("Couldn't update your subscription.") }
        }
    }
}

@Composable
fun ChannelTabStrip(selected: ChannelTab, onSelect: (ChannelTab) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    // Material tabs: the text, and a line under the one that is open
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            ChannelTab.entries.forEach { tab ->
                val on = tab == selected
                Column(Modifier.pressable(pressedScale = .97f) { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tab.label, color = if (on) c.onSurface else c.onSurfaceVariant, fontSize = 16.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp))
                    Box(Modifier.height(3.dp).fillMaxWidth().background(if (on) c.onSurface else Color.Transparent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = .5f)))
    }
}

@Composable
fun ChannelScreen(
    state: ChannelState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val openPlaylist = LocalPlaylistOpener.current
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, state.activeTab, state.videos.size, state.shorts.size) {
        if (!nearEnd) return@LaunchedEffect
        when (state.activeTab) {
            ChannelTab.Videos -> if (state.videos.isNotEmpty()) state.loadMore()
            ChannelTab.Home -> {}
            ChannelTab.Shorts -> if (state.shorts.isNotEmpty()) state.loadMoreShorts()
            else -> {}
        }
    }
    Box(Modifier.fillMaxSize().background(c.surface)) {
    CollapsingHeaderLayout(
        header = {
        BackScreenHeader(onBack = onBack) {
            ChannelNameWithBadge(
                state.details?.name.orEmpty(),
                verified = state.details?.verified == true,
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                maxLines = 1,
                letterSpacing = (-0.6).sp,
            )
        }
        },
    ) { top ->
        val d = state.details
        when {
            d == null && state.error != null -> Box(Modifier.padding(top = top)) { EmptyState(
                Icons.Rounded.CloudOff, "Couldn't load this channel", state.error.orEmpty(),
                action = { PillButton("Try again", state::load) },
            ) }
            d == null -> Box(Modifier.fillMaxWidth().padding(top = top + 60.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
            }
            else -> LazyColumn(
                state = list, contentPadding = contentPadding.withTop(top),
                // a swipe sideways moves to the next / previous tab (Home, Videos, Shorts, Live ...)
                modifier = Modifier.pointerInput(state.activeTab) {
                    var dx = 0f
                    androidx.compose.foundation.gestures.detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragCancel = { dx = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(dx) > 110f) {
                                val tabs = ChannelTab.entries
                                tabs.getOrNull(tabs.indexOf(state.activeTab) + if (dx < 0) 1 else -1)?.let { state.selectTab(it) }
                            }
                            dx = 0f
                        },
                    ) { _, d -> dx += d }
                },
            ) {
                item(key = "header") {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(260, easing = Motion.Emphasized)) +
                            slideInVertically(tween(320, easing = Motion.Emphasized)) { it / 8 },
                    ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        if (d.banner != null) {
                            Thumbnail(d.banner, Modifier.fillMaxWidth().aspectRatio(5.6f), radius = 12)
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(d.avatar, d.name, size = 108)
                            Spacer(Modifier.weight(1f))
                            state.subscribed?.let { subscribed -> Box(Modifier.width(210.dp)) { ChannelSubscribeButton(subscribed) { state.toggleSubscribe(onMessage) } } }
                        }
                        Spacer(Modifier.height(10.dp))
                        ChannelNameWithBadge(d.name, verified = d.verified, maxLines = 2, fontSize = 30.sp)
                        Text(
                            listOfNotNull(d.handle?.let { "@$it" }, channelStatLine(d.subscribers, d.videoCount).takeIf { it.isNotBlank() }).joinToString("   "),
                            color = c.onSurfaceVariant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                        if (d.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(d.description, color = c.onSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    }
                }
                item(key = "tabs") {
                    ChannelTabStrip(selected = state.activeTab, onSelect = { state.selectTab(it) })
                }
                when (state.activeTab) {
                    ChannelTab.Home -> {
                        // the newest video big, then the videos under it
                        val first = state.videos.firstOrNull()
                        if (first == null) item(key = "home_wait") {
                            Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp)) }
                        } else {
                            item(key = "home_feature") { VideoCard(first, onClick = { onOpen(first) }) }
                            item(key = "home_videos_title") { Text("Videos", color = c.onSurface, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
                            items(state.videos.drop(1).take(12), key = { "home_" + it.id }) { video -> Box(Modifier.padding(horizontal = 16.dp)) { VideoRow(video, onClick = { onOpen(video) }) } }
                            item(key = "home_more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                    Text("All videos", color = c.primary, fontSize = 16.sp, modifier = Modifier.pressable { state.selectTab(ChannelTab.Videos) }.padding(12.dp))
                                }
                            }
                        }
                    }
                    ChannelTab.Videos -> {
                        if (state.sorts.size > 1) item(key = "sorts") {
                            ChoiceChips(state.sorts.map { it.first }, state.sortIndex, { state.sortBy(it) })
                        }
                        items(state.videos, key = { it.id }) { video ->
                            VideoCard(video, onClick = { onOpen(video) }, modifier = Modifier.animateItem())
                        }
                        if (state.loading || state.hasMore) item {
                            Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    ChannelTab.Shorts -> {
                        if (state.shortsLoading && state.shorts.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.shorts.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    "No Shorts yet",
                                    "This channel hasn't posted any Shorts.",
                                )
                            }
                        } else {
                            items(state.shorts.chunked(3), key = { row -> row.first().id }) { row ->
                                ShortsRow(row, onOpenShort, Modifier.animateItem())
                            }
                            if (state.shortsLoading) item {
                                Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                    ChannelTab.Live -> {
                        if (state.liveLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.liveVideos.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    "No live streams",
                                    "This channel has no active live streams or past live broadcasts.",
                                )
                            }
                        } else {
                            items(state.liveVideos, key = { it.id }) { video ->
                                VideoCard(video, onClick = { onOpen(video) }, modifier = Modifier.animateItem())
                            }
                        }
                    }
                    ChannelTab.Playlists -> {
                        if (state.playlistsLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.playlists.isEmpty()) {
                            item { EmptyState(Icons.Rounded.CloudOff, "No playlists", "This channel has no public playlists.") }
                        } else {
                            items(state.playlists, key = { "pl-" + it.id }) { pl -> PlaylistRow(pl, Modifier.animateItem()) { openPlaylist(pl) } }
                        }
                    }
                    ChannelTab.Community -> {
                        if (state.communityLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (state.communityPosts.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Rounded.CloudOff,
                                    "No community posts",
                                    "This channel hasn't posted anything to their community tab yet.",
                                )
                            }
                        } else {
                            items(state.communityPosts, key = { it.id }) { post -> CommunityCard(post) }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ChannelSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (subscribed) c.secondaryContainer else c.onSurface,
        animationSpec = tween(240, easing = Motion.Emphasized),
        label = "subscribeContainer",
    )
    val content by animateColorAsState(
        targetValue = if (subscribed) c.onSecondaryContainer else c.surface,
        animationSpec = tween(240, easing = Motion.Emphasized),
        label = "subscribeContent",
    )
    Row(
        Modifier
            .squishOn(subscribed)
            .fillMaxWidth()
            .height(46.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(container),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = true, kind = Flourish.Ring, tint = content, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(if (subscribed) I18n.t("subscribed", "Subscribed") else I18n.t("subscribe", "Subscribe"), color = content, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun ShortsRow(row: List<VideoItem>, onOpen: (VideoItem) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.forEach { video -> ShortTile(video, Modifier.weight(1f), onOpen) }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ShortTile(video: VideoItem, modifier: Modifier, onOpen: (VideoItem) -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .pressable(pressedScale = 0.95f) { onOpen(video) },
    ) {
        Thumbnail(video.thumbnail, Modifier.fillMaxSize(), radius = 0, videoId = video.id)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(9.dp),
        ) {
            Text(
                video.title,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            video.views.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    "${formatCount(it)} views",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
