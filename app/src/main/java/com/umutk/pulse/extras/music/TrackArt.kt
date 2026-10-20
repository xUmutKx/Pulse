package com.umutk.pulse.extras.music

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.util.LruCache
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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

/** Cover of [t] as composable state; null while loading or when the track has none. */
@Composable
fun rememberTrackArt(t: Track?): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, t?.path) {
        value = if (t == null) null else withContext(Dispatchers.IO) { runCatching { trackArt(ctx, t) }.getOrNull() }
    }.value
}

/** Covers of the queue neighbours of [index], read off the main thread, for [CoverCarousel]. */
@Composable
fun neighbourCovers(queue: List<Track>, index: Int, cover: (Track) -> ImageBitmap?): Pair<ImageBitmap?, ImageBitmap?> {
    val prev = queue.getOrNull(index - 1)
    val next = queue.getOrNull(index + 1)
    val p = produceState<ImageBitmap?>(null, prev?.path) { value = prev?.let { t -> withContext(Dispatchers.IO) { runCatching { cover(t) }.getOrNull() } } }.value
    val n = produceState<ImageBitmap?>(null, next?.path) { value = next?.let { t -> withContext(Dispatchers.IO) { runCatching { cover(t) }.getOrNull() } } }.value
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
    slot: @Composable (ImageBitmap?) -> Unit,
) {
    // one plain value follows the finger; the settle is a single eased tween (a spring overshoots and rocks back and forth)
    var slide by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(0f) }
    val prevNow by rememberUpdatedState(onPrev)
    val nextNow by rememberUpdatedState(onNext)
    val canPrev by rememberUpdatedState(hasPrev)
    val canNext by rememberUpdatedState(hasNext)
    val upNow by rememberUpdatedState(onSwipeUp)
    val downNow by rememberUpdatedState(onSwipeDown)
    val slop = with(LocalDensity.current) { 12.dp.toPx() }
    val reach = with(LocalDensity.current) { 72.dp.toPx() }
    Box(
        modifier.clipToBounds().onSizeChanged { width = it.width.toFloat() }.pointerInput(Unit) {
            // one drag, one axis: the first real movement decides between sideways (the carousel) and up or down (the page)
            var startSlide = 0f
            var totalX = 0f
            var totalY = 0f
            var vertical = false
            var axisSet = false
            detectDragGestures(
                // a new drag stops a settle still running and carries on from where the cover is
                onDragStart = { settle?.cancel(); startSlide = slide; totalX = 0f; totalY = 0f; vertical = false; axisSet = false },
                onDragEnd = {
                    if (vertical) {
                        if (totalY < -reach) upNow?.invoke() else if (totalY > reach) downNow?.invoke()
                    } else {
                        val w = size.width.toFloat()
                        val sum = totalX
                        // dir: 1 = next track (slides in from the right), -1 = previous (from the left), 0 = snap back
                        val dir = if (abs(sum) < w * .2f) 0 else if (sum < 0 && canNext) 1 else if (sum > 0 && canPrev) -1 else 0
                        settle = scope.launch {
                            animate(slide, if (dir == 0) 0f else -dir * w, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> slide = v }
                            if (dir != 0) { if (dir > 0) nextNow() else prevNow(); slide = 0f }
                        }
                    }
                },
                onDragCancel = { if (!vertical) settle = scope.launch { animate(slide, 0f, animationSpec = tween(180)) { v, _ -> slide = v } } },
                onDrag = { c, d ->
                    c.consume()
                    totalX += d.x; totalY += d.y
                    if (!axisSet && (abs(totalX) > slop || abs(totalY) > slop)) {
                        axisSet = true
                        vertical = abs(totalY) > abs(totalX)
                    }
                    if (axisSet && !vertical) slide = startSlide + totalX
                },
            )
        },
    ) {
        if (hasPrev) Box(Modifier.fillMaxSize().offset { IntOffset((slide - width).roundToInt(), 0) }) { slot(prevArt) }
        if (hasNext) Box(Modifier.fillMaxSize().offset { IntOffset((slide + width).roundToInt(), 0) }) { slot(nextArt) }
        Box(Modifier.fillMaxSize().offset { IntOffset(slide.roundToInt(), 0) }) { slot(art) }
    }
}
