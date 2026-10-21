package com.umutk.pulse.extras.music

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.util.LruCache
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private object ArtCache {
    val map = LruCache<String, ImageBitmap>(48)
    val none = HashSet<String>() // tracks known to have no cover, so lists do not reopen the file each frame
}

/** Embedded cover of [track] (cached, at most about 512 px), or null. Reads the file: call from a composable's remember, not per frame. */
fun trackArt(ctx: Context, track: Track): ImageBitmap? {
    val key = track.path
    ArtCache.map.get(key)?.let { return it }
    if (synchronized(ArtCache.none) { key in ArtCache.none }) return null
    // a cover the user picked or one found online wins over the embedded picture; both are saved on disk, so they survive a restart
    val bmp = PowerampCovers.saved(ctx, key) ?: embeddedArt(ctx, key) ?: CoverFetch.find(ctx, track)
    // a skipped lookup on the main thread is not remembered as "no cover"
    if (bmp != null) ArtCache.map.put(key, bmp) else if (Looper.myLooper() != Looper.getMainLooper()) synchronized(ArtCache.none) { ArtCache.none += key }
    return bmp
}

/** Forgets the cached cover of [path], so the next read sees a cover that was just saved or picked. */
internal fun forgetArt(path: String) {
    ArtCache.map.remove(path)
    synchronized(ArtCache.none) { ArtCache.none -= path }
}

/** Forgets every cached cover and every "no cover" mark (after the saved covers were cleared). */
internal fun forgetAllArt() {
    ArtCache.map.evictAll()
    synchronized(ArtCache.none) { ArtCache.none.clear() }
}

internal fun embeddedArt(ctx: Context, key: String): ImageBitmap? {
    return try {
        val r = MediaMetadataRetriever()
        try {
            if (key.startsWith("http")) return null
            if (key.contains("://")) r.setDataSource(ctx, Uri.parse(key)) else r.setDataSource(key)
            r.embeddedPicture?.let { b ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(b, 0, b.size, o)
                var s = 1
                while (o.outWidth / (s * 2) >= 512) s *= 2
                BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
            }
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        null
    }?.asImageBitmap()
}

/** Cover of [track] without the online lookup: the cache, a cover the user picked, or the embedded picture. Quick, so a new track shows its cover at once. */
internal fun trackArtLocal(ctx: Context, track: Track): ImageBitmap? {
    val key = track.path
    ArtCache.map.get(key)?.let { return it }
    val bmp = PowerampCovers.saved(ctx, key) ?: embeddedArt(ctx, key)
    if (bmp != null) ArtCache.map.put(key, bmp)
    return bmp
}

/** Cover of [t] as composable state; null while loading or when the track has none. The local cover comes first, the online search only after it found nothing. */
/** Decoded covers kept in memory by path, so a track change shows the cover at once instead of the previous one while the new one loads. */
private val artMem = object : LinkedHashMap<String, ImageBitmap>(16, .75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 30 }
@Synchronized private fun artCached(path: String?): ImageBitmap? = path?.let { artMem[it] }
@Synchronized private fun artKeep(path: String, b: ImageBitmap) { artMem[path] = b }

/** The cover of [t]: the one in memory at once, else read off the main thread (saved and embedded art first, then the internet). Never the cover of the previous track. */
@Composable
fun rememberTrackArt(t: Track?): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(artCached(t?.path), t?.path) {
        // the state outlives a key change: reset it to this track's own cover (or none) before anything is loaded
        value = artCached(t?.path)
        if (t == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { runCatching { trackArtLocal(ctx, t) }.getOrNull() }?.also { artKeep(t.path, it) }
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { trackArt(ctx, t) }.getOrNull() }?.also { artKeep(t.path, it) }
    }.value
}

/** Covers of the queue neighbours of [index], read off the main thread, for [CoverCarousel]. Each is its own track's cover, never a stale one. */
@Composable
fun neighbourCovers(queue: List<Track>, index: Int, cover: (Track) -> ImageBitmap?): Pair<ImageBitmap?, ImageBitmap?> {
    val prev = queue.getOrNull(index - 1)
    val next = queue.getOrNull(index + 1)
    val p = produceState<ImageBitmap?>(artCached(prev?.path), prev?.path) {
        value = artCached(prev?.path)
        if (prev != null && value == null) value = withContext(Dispatchers.IO) { runCatching { cover(prev) }.getOrNull() }?.also { artKeep(prev.path, it) }
    }.value
    val n = produceState<ImageBitmap?>(artCached(next?.path), next?.path) {
        value = artCached(next?.path)
        if (next != null && value == null) value = withContext(Dispatchers.IO) { runCatching { cover(next) }.getOrNull() }?.also { artKeep(next.path, it) }
    }.value
    return p to n
}

/**
 * Cover carousel: the neighbour covers sit at the sides and move with the finger. A drag past a fifth of the width
 * changes track and the neighbour slides in from the side of the drag; a shorter drag snaps back. [slot] draws one cover.
 */
@Composable
fun CoverCarousel(
    art: ImageBitmap?,
    prevArt: ImageBitmap?,
    nextArt: ImageBitmap?,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    // a vertical drag is for the page, not the carousel: up calls onSwipeUp, down calls onSwipeDown (both optional)
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    // when set, a vertical drag moves the whole page instead (dy per move, then the release): the cover itself stays put
    onPageDrag: ((Float) -> Unit)? = null,
    onPageRelease: (() -> Unit)? = null,
    slot: @Composable (ImageBitmap?) -> Unit,
) {
    // one plain value follows the finger; the settle is a single eased tween (a spring overshoots and rocks back and forth)
    var slide by remember { mutableFloatStateOf(0f) }
    // a vertical drag only nudges the cover: it follows the finger a little and springs back with a bounce
    var pull by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    // after a swipe the cover that slid in stays on screen until the new track's own art arrives, so the old one never pops back
    var carried by remember { mutableStateOf<ImageBitmap?>(null) }
    var carrying by remember { mutableStateOf(false) }
    LaunchedEffect(art) { carrying = false; carried = null }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    val prevNow by rememberUpdatedState(onPrev)
    val nextNow by rememberUpdatedState(onNext)
    val prevArtNow by rememberUpdatedState(prevArt)
    val nextArtNow by rememberUpdatedState(nextArt)
    val canPrev by rememberUpdatedState(hasPrev)
    val canNext by rememberUpdatedState(hasNext)
    val upNow by rememberUpdatedState(onSwipeUp)
    val downNow by rememberUpdatedState(onSwipeDown)
    val pageDragNow by rememberUpdatedState(onPageDrag)
    val pageReleaseNow by rememberUpdatedState(onPageRelease)
    val slop = with(LocalDensity.current) { 12.dp.toPx() }
    val reach = with(LocalDensity.current) { 72.dp.toPx() }
    val ctx = LocalContext.current
    Box(
        // clipped at the two sides only: the neighbour covers stay out of sight, but a cover pulled up or down may leave the frame and cross the page
        modifier.drawWithContent { clipRect(0f, -size.height * 8f, size.width, size.height * 9f) { this@drawWithContent.drawContent() } }
            .onSizeChanged { width = it.width.toFloat(); height = it.height.toFloat() }.pointerInput(Unit) {
            // one drag, one axis: the first real movement decides between sideways (the carousel) and up or down (the page)
            var startSlide = 0f
            var totalX = 0f
            var totalY = 0f
            var vertical = false
            var axisSet = false
            var sideways = true // CoverGestures: off means a sideways drag does nothing
            detectDragGestures(
                // a new drag stops a settle still running and carries on from where the cover is
                onDragStart = { settle?.cancel(); startSlide = slide; totalX = 0f; totalY = 0f; vertical = false; axisSet = false; sideways = CoverGestures.swipeChangesTrack(ctx) },
                onDragEnd = {
                    if (vertical && pageDragNow != null) pageReleaseNow?.invoke()
                    else if (vertical) {
                        // a long pull with an action behind it sends the cover across the whole page, then runs the action (the previous screen opens); a short one springs back
                        val dirY = if (totalY < -reach && upNow != null) -1 else if (totalY > reach && downNow != null) 1 else 0
                        settle = scope.launch {
                            if (dirY != 0) {
                                animate(pull, dirY * size.height * 3.2f, animationSpec = tween(240, easing = FastOutLinearInEasing)) { v, _ -> pull = v }
                                if (dirY < 0) upNow?.invoke() else downNow?.invoke()
                                // if the page is still there (the lyrics opened over it) the cover comes back; a closed page takes this scope with it
                                delay(400)
                            }
                            animate(pull, 0f, animationSpec = spring(dampingRatio = .6f, stiffness = Spring.StiffnessLow)) { v, _ -> pull = v }
                        }
                    } else {
                        val w = size.width.toFloat()
                        val sum = totalX
                        // dir: 1 = next track (slides in from the right), -1 = previous (from the left), 0 = snap back
                        val dir = if (!sideways || abs(sum) < w * .2f) 0 else if (sum < 0 && canNext) 1 else if (sum > 0 && canPrev) -1 else 0
                        settle = scope.launch {
                            animate(slide, if (dir == 0) 0f else -dir * w, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> slide = v }
                            if (dir != 0) {
                                carried = if (dir > 0) nextArtNow else prevArtNow
                                carrying = true
                                if (dir > 0) nextNow() else prevNow()
                                slide = 0f
                            }
                        }
                    }
                },
                onDragCancel = {
                    if (vertical && pageDragNow != null) pageReleaseNow?.invoke()
                    else settle = scope.launch {
                        if (vertical) animate(pull, 0f, animationSpec = spring(dampingRatio = .4f, stiffness = Spring.StiffnessMedium)) { v, _ -> pull = v }
                        else animate(slide, 0f, animationSpec = tween(180)) { v, _ -> slide = v }
                    }
                },
                onDrag = { c, d ->
                    c.consume()
                    totalX += d.x; totalY += d.y
                    if (!axisSet && (abs(totalX) > slop || abs(totalY) > slop)) {
                        axisSet = true
                        vertical = abs(totalY) > abs(totalX)
                    }
                    if (axisSet && !vertical && sideways) slide = startSlide + totalX
                    if (axisSet && vertical) { val pd = pageDragNow; if (pd != null) pd(d.y) else pull = totalY }
                },
            )
        },
    ) {
        // the covers sit on a wheel whose middle is far below them: sliding turns the wheel, so each cover swings along an arc and tilts up to about 20 degrees
        val wheel = 3f
        val step = if (height > 0f) width / (wheel * height) else 0f
        fun Modifier.onWheel(index: Int, lift: Boolean = false) = graphicsLayer {
            val a = (if (height > 0f) slide / (wheel * height) else 0f) + index * step
            rotationZ = Math.toDegrees(a.toDouble()).toFloat()
            transformOrigin = TransformOrigin(.5f, .5f + wheel)
            if (lift) translationY = pull
        }
        // a neighbour exists on screen only while the cover is being swiped toward it: at rest its tilted corner must never peek in
        if (hasPrev && slide > 0.5f) Box(Modifier.fillMaxSize().onWheel(-1)) { slot(prevArt) }
        if (hasNext && slide < -0.5f) Box(Modifier.fillMaxSize().onWheel(1)) { slot(nextArt) }
        Box(Modifier.fillMaxSize().onWheel(0, lift = true)) { slot(if (carrying) carried else art) }
    }
}
