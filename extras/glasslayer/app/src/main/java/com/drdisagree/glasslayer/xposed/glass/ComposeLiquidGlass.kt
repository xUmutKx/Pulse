package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.SurfaceControl
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.callStatic
import com.drdisagree.glasslayer.xposed.hook.field
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

object ComposeLiquidGlass : BackdropClient {

    private const val STALE_MS = 400L
    private const val STALE_FRAMES = 3L
    private const val LAYOUT_NODE = "layoutNode"
    private const val OWNER = "owner"
    private const val SCALE_BLUR_PX = 10f
    private const val OPEN_EDGE_RATIO = 0.5f
    private const val LAYER_REFRESH_MS = 150L
    private const val MAX_BACKDROP_NODES = 8
    private const val MIN_SCREEN_SCALE = 0.01f
    private const val BACKDROP_NODE_NAME = "GlassLayerBackdrop"
    private const val LAYOUT_COORDINATES_CLASS = "androidx.compose.ui.layout.LayoutCoordinates"
    private const val INT_STATE_CLASS = "androidx.compose.runtime.ParcelableSnapshotMutableIntState"
    private const val AMBIENT_REFRESH_RATE = 30f

    private val surfaces = WeakHashMap<Any, GlassSurface>()
    private val views = WeakHashMap<View, Long>()
    private val pendingViews = WeakHashMap<View, Boolean>()
    private val viewLocation = IntArray(2)
    private val ghostPlacement = FloatArray(4)
    private val excluded = ArrayList<ExcludedWindow>()
    private val frames = WeakHashMap<View, BackdropFrame>()
    private val previousFrames = WeakHashMap<View, BackdropFrame>()
    private val glassRoots = ArrayList<View>()
    private var topRoot: WeakReference<View>? = null
    private val holders = WeakHashMap<View, Boolean>()
    private val heldRoots = WeakHashMap<View, Boolean>()
    private val releasedRoots = WeakHashMap<View, Long>()

    @Volatile
    private var ambientCapture = false

    @Volatile
    private var pendingRoot: WeakReference<View>? = null

    @Volatile
    private var inFlightRoot: WeakReference<View>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var excludedAt = 0L
    private var registered = false

    @Volatile
    private var frameInterval = 0L

    private val watchLocation = IntArray(2)
    private val movedViews = ArrayList<View>()
    private val watchedObservers = WeakHashMap<ViewTreeObserver, Boolean>()
    private var framePosted = false

    @Volatile
    private var framesUntil = 0L
    private var lastFrameAt = 0L
    private var frame: BackdropFrame? = null
    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val backdrops = WeakHashMap<Any, HashMap<Float, SharedBackdrop>>()
    private var backdropCount = 0
    private var frameCount = 0

    @Volatile
    private var display = 0

    @Volatile
    private var scaleHint = 0.5f

    override val preferredScale: Float
        get() = scaleHint

    @Volatile
    private var blurHint = 0f

    override val preferredBlur: Float
        get() = blurHint

    override val wantsFullDisplay: Boolean
        get() = !ambientCapture

    @Volatile
    private var displayRefreshRate = DEFAULT_REFRESH_RATE

    override val refreshRate: Float
        get() = if (ambientCapture) minOf(AMBIENT_REFRESH_RATE, displayRefreshRate) else displayRefreshRate

    val isAvailable: Boolean
        get() = BackdropCapture.isAvailable && localToScreen != null

    private val localToScreen: Method? by lazy {
        ComposeToolkit.loadClass(LAYOUT_COORDINATES_CLASS)
            ?.methods
            ?.firstOrNull {
                it.name.startsWith("localToScreen") &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Long::class.javaPrimitiveType
            }
    }

    private val isAttachedMethod: Method? by lazy {
        ComposeToolkit.loadClass(LAYOUT_COORDINATES_CLASS)
            ?.methods
            ?.firstOrNull { it.name.startsWith("isAttached") && it.parameterTypes.isEmpty() }
    }

    private val frameState: Any? by lazy {
        try {
            ComposeToolkit.loadClass(INT_STATE_CLASS)
                ?.getConstructor(Int::class.javaPrimitiveType)
                ?.newInstance(0)
        } catch (_: Throwable) {
            null
        }
    }

    override val displayId: Int
        get() = display

    override val captureToken: Any?
        get() = inFlightRoot

    fun draw(
        drawScope: Any,
        shape: Any,
        blurRadius: Float,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f,
        key: Int = 0
    ): Boolean = render(drawScope, key, blurRadius, tint, style, alpha) { width, height, _ ->
        val outline = ComposeToolkit.outline(shape, drawScope) ?: return@render null
        val path = ComposeToolkit.outlinePath(outline) ?: return@render null
        val radius = ComposeToolkit.outlineCornerRadius(outline) ?: (minOf(width, height) / 2f)
        GlassSegment(0f, 0f, width, height, path, FloatArray(4) { radius })
    }

    fun drawRounded(
        drawScope: Any,
        cornerRadius: Float?,
        blurRadius: Float,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f
    ): Boolean = render(drawScope, 0, blurRadius, tint, style, alpha) { width, height, path ->
        val radius = (cornerRadius ?: (minOf(width, height) / 2f)).coerceAtMost(minOf(width, height) / 2f)
        path.addRoundRect(0f, 0f, width, height, radius, radius, Path.Direction.CW)
        GlassSegment(0f, 0f, width, height, path, FloatArray(4) { radius })
    }

    fun drawSegment(
        drawScope: Any,
        key: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radii: FloatArray,
        blurRadius: Float,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f
    ): Boolean = render(drawScope, key, blurRadius, tint, style, alpha) { _, _, path ->
        if (width <= 0f || height <= 0f) return@render null
        path.addRoundRect(0f, 0f, width, height, radii, Path.Direction.CW)
        val corners = floatArrayOf(radii[0], radii[2], radii[4], radii[6])
        GlassSegment(left, top, width, height, path, corners)
    }

    fun drawView(
        view: View,
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radii: FloatArray,
        blurRadius: Float,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f,
        key: Int = 0,
        openSmallEdges: Boolean = false,
        join: GlassJoin? = null
    ): Boolean {
        if (width <= 0f || height <= 0f || !view.isAttachedToWindow) return false

        val ghostRoot = GhostPlacement.resolve(view, view.width.toFloat(), view.height.toFloat(), ghostPlacement)
        val scaleX: Float
        val scaleY: Float
        val screenLeft: Float
        val screenTop: Float
        if (ghostRoot != null) {
            scaleX = ghostPlacement[2]
            scaleY = ghostPlacement[3]
            screenLeft = ghostPlacement[0] + left * scaleX
            screenTop = ghostPlacement[1] + top * scaleY
        } else {
            view.getLocationOnScreen(viewLocation)
            scaleX = 1f
            scaleY = 1f
            screenLeft = viewLocation[0] + left
            screenTop = viewLocation[1] + top
        }
        val root = ghostRoot ?: view.rootView
        val now = SystemClock.uptimeMillis()

        val surface = synchronized(surfaces) {
            surfaces.getOrPut(view) { GlassSurface() }.also {
                it.rect.set(
                    floor(screenLeft).toInt(),
                    floor(screenTop).toInt(),
                    ceil(screenLeft + width * scaleX).toInt(),
                    ceil(screenTop + height * scaleY).toInt()
                )
                it.lastDrawn = now
                it.originX = if (ghostRoot != null) floor(ghostPlacement[0]).toInt() else viewLocation[0]
                it.originY = if (ghostRoot != null) floor(ghostPlacement[1]).toInt() else viewLocation[1]
                if (it.root?.get() !== root) {
                    it.root = WeakReference(root)
                    excludedAt = 0L
                }
            }
        }
        views[view] = now

        refreshExcludedLayers()
        if (!registered) {
            registered = true
            BackdropCapture.register(this)
        }
        watch(view)

        val current = frameFor(surface) ?: run {
            pendingViews[view] = true
            return false
        }
        val path = surface.path(key)
        path.addRoundRect(0f, 0f, width, height, radii, Path.Direction.CW)
        val corners = surface.corners
        corners[0] = radii[0]
        corners[1] = radii[2]
        corners[2] = radii[4]
        corners[3] = radii[6]
        var topExtend = 0f
        var bottomExtend = 0f
        if (openSmallEdges) {
            val largest = radii.maxOrNull() ?: 0f
            val threshold = largest * OPEN_EDGE_RATIO
            if (largest > 0f && maxOf(radii[0], radii[2]) < threshold) topExtend = height
            if (largest > 0f && maxOf(radii[4], radii[6]) < threshold) bottomExtend = height
        }

        canvas.save()
        canvas.translate(left, top)
        val drawn = paint(
            surface.renderer(key),
            canvas,
            path,
            current,
            surface.root?.get() ?: this,
            screenLeft,
            screenTop,
            width,
            height,
            corners,
            blurRadius,
            tint,
            style,
            alpha,
            topExtend,
            bottomExtend,
            scaleX,
            scaleY,
            join
        )
        canvas.restore()
        return drawn
    }

    private inline fun render(
        drawScope: Any,
        key: Int,
        blurRadius: Float,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float,
        clip: (Float, Float, Path) -> GlassSegment?
    ): Boolean {
        frameState?.call("getIntValue")

        val node = drawScope.field("drawNode") ?: return false
        val coordinator = node.field("coordinator") ?: return false
        if (isAttachedMethod?.invoke(coordinator) == false) return false

        val packedSize = ComposeToolkit.drawSize(drawScope) ?: return false
        val width = Float.fromBits((packedSize ushr 32).toInt())
        val height = Float.fromBits((packedSize and 0xFFFFFFFFL).toInt())
        if (width <= 0f || height <= 0f) return false

        val position = localToScreen?.invoke(coordinator, 0L) as? Long ?: return false
        val left = Float.fromBits((position ushr 32).toInt())
        val top = Float.fromBits((position and 0xFFFFFFFFL).toInt())
        val corner = localToScreen?.invoke(coordinator, packedSize) as? Long
        val scaleX = corner
            ?.let { (Float.fromBits((it ushr 32).toInt()) - left) / width }
            ?.takeIf { it.isFinite() && abs(it) > MIN_SCREEN_SCALE }
            ?: 1f
        val scaleY = corner
            ?.let { (Float.fromBits((it and 0xFFFFFFFFL).toInt()) - top) / height }
            ?.takeIf { it.isFinite() && abs(it) > MIN_SCREEN_SCALE }
            ?: 1f
        val right = left + width * scaleX
        val bottom = top + height * scaleY

        val surface = synchronized(surfaces) {
            surfaces.getOrPut(node) { GlassSurface() }.also {
                val now = SystemClock.uptimeMillis()
                it.rect.set(
                    floor(minOf(left, right)).toInt(),
                    floor(minOf(top, bottom)).toInt(),
                    ceil(maxOf(left, right)).toInt(),
                    ceil(maxOf(top, bottom)).toInt()
                )
                it.lastDrawn = now
                it.originX = floor(left).toInt()
                it.originY = floor(top).toInt()
                it.coordinator = coordinator
                val owner = composeOwner(coordinator)?.rootView
                if (owner != null && it.root?.get() !== owner) {
                    it.root = WeakReference(owner)
                    excludedAt = 0L
                }
            }
        }

        refreshExcludedLayers()
        if (!registered) {
            registered = true
            BackdropCapture.register(this)
        }
        composeOwner(coordinator)?.let { watch(it) }

        val current = frameFor(surface) ?: return false
        val canvas = ComposeToolkit.nativeCanvas(drawScope) ?: return false
        val segment = clip(width, height, surface.path(key)) ?: return false

        canvas.save()
        canvas.translate(segment.left, segment.top)
        val drawn = paint(
            surface.renderer(key),
            canvas,
            segment.path,
            current,
            surface.root?.get() ?: this,
            left + segment.left * scaleX,
            top + segment.top * scaleY,
            segment.width,
            segment.height,
            segment.radii,
            blurRadius,
            tint,
            style,
            alpha,
            screenScaleX = scaleX,
            screenScaleY = scaleY
        )
        canvas.restore()
        return drawn
    }

    private fun paint(
        renderer: LiquidGlassRenderer,
        canvas: Canvas,
        path: Path,
        frame: BackdropFrame,
        owner: Any,
        screenLeft: Float,
        screenTop: Float,
        width: Float,
        height: Float,
        radii: FloatArray,
        blurRadius: Float,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float,
        topExtend: Float = 0f,
        bottomExtend: Float = 0f,
        screenScaleX: Float = 1f,
        screenScaleY: Float = 1f,
        join: GlassJoin? = null
    ): Boolean {
        blurHint = blurRadius
        scaleHint = (SCALE_BLUR_PX / blurRadius.coerceAtLeast(1f)).coerceIn(0.25f, 0.75f)

        val blurred = frame.blurred
        if (blurred != null && frame.blurRadius == blurRadius) {
            return renderer.drawDirect(
                canvas,
                path,
                frame,
                blurred,
                screenLeft,
                screenTop,
                width,
                height,
                radii,
                tint,
                style,
                alpha,
                topExtend,
                bottomExtend,
                screenScaleX,
                screenScaleY,
                join
            )
        }

        return renderer.drawShared(
            canvas,
            path,
            frame,
            backdropFor(owner, frame, blurRadius),
            screenLeft,
            screenTop,
            width,
            height,
            radii,
            tint,
            style,
            alpha,
            topExtend,
            bottomExtend,
            screenScaleX,
            screenScaleY,
            join
        )
    }

    private fun backdropFor(owner: Any, frame: BackdropFrame, blurRadius: Float): RenderNode {
        if (backdrops[owner]?.containsKey(blurRadius) != true && backdropCount >= MAX_BACKDROP_NODES) releaseBackdrops()
        val backdrop = backdrops.getOrPut(owner) { HashMap() }.getOrPut(blurRadius) {
            backdropCount++
            SharedBackdrop(RenderNode(BACKDROP_NODE_NAME))
        }
        val node = backdrop.node
        if (backdrop.frame === frame) return node

        val bitmap = frame.bitmap
        node.setPosition(0, 0, bitmap.width, bitmap.height)
        val recording = node.beginRecording(bitmap.width, bitmap.height)
        recording.drawBitmap(bitmap, 0f, 0f, backdropPaint)
        node.endRecording()

        if (backdrop.scale != frame.scale) {
            val radius = blurRadius * frame.scale
            node.setRenderEffect(
                if (radius > 0f) {
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
            backdrop.scale = frame.scale
        }
        backdrop.frame = frame
        return node
    }

    private fun releaseBackdrops() {
        backdrops.values.forEach { cache -> cache.values.forEach { it.node.discardDisplayList() } }
        backdrops.clear()
        backdropCount = 0
    }

    override fun captureRect(out: Rect): Boolean {
        val now = SystemClock.uptimeMillis()
        out.setEmpty()
        synchronized(surfaces) {
            glassRoots.clear()
            surfaces.values.forEach { surface ->
                if (now - surface.lastDrawn > staleMs() || surface.rect.isEmpty) return@forEach
                val root = surface.root?.get() ?: return@forEach
                if (glassRoots.none { it === root }) glassRoots.add(root)
            }
            val chosen = glassRoots.maxByOrNull { root -> windowIndex(root) }
            pendingRoot = chosen?.let { WeakReference(it) }
            ambientCapture = chosen != null && isAmbient(chosen)
            surfaces.values.forEach { surface ->
                if (now - surface.lastDrawn > staleMs() || surface.rect.isEmpty) return@forEach
                if (chosen != null && glassRoots.size > 1 && surface.root?.get() !== chosen) return@forEach
                if (out.isEmpty) out.set(surface.rect) else out.union(surface.rect)
            }
        }
        if (out.isEmpty) mainHandler.post { Guard.run("ComposeLiquidGlass.idle", Unit) { stopIfIdle() } }
        return !out.isEmpty
    }

    override fun excludeLayers(out: MutableCollection<SurfaceControl>) {
        val chosen = pendingRoot?.get()
        inFlightRoot = pendingRoot
        val roots = synchronized(surfaces) { glassRoots.toList() }
        synchronized(excluded) {
            val chosenIndex = excluded.indexOfFirst { it.root.get() === chosen }
            excluded.forEachIndexed { index, window ->
                val root = window.root.get()
                val glassBelow = chosenIndex >= 0 && index < chosenIndex && root != null && roots.any { it === root }
                if (!window.surface.isValid) excludedAt = 0L
                if (!glassBelow && window.surface.isValid) out.add(window.surface)
            }
        }
    }

    override fun onBackdrop(frame: BackdropFrame) {
        this.frame = frame
        val root = (frame.token as? WeakReference<*>)?.get() as? View
        val previous = topRoot?.get()
        if (previous != null && root != null && previous !== root && !heldRoots.containsKey(previous) &&
            windowIndex(root) > windowIndex(previous)
        ) {
            (previousFrames[previous] ?: frames[previous])?.let { frames[previous] = frozen(it) }
        }
        topRoot = root?.let { WeakReference(it) }
        root?.takeUnless { heldRoots.containsKey(it) || settling(it, frame) }?.let { key ->
            frames[key]?.let { previousFrames[key] = it }
            frames[key] = frame
        }
        frameCount++
        frameState?.call("setIntValue", frameCount)

        val now = SystemClock.uptimeMillis()
        if (lastFrameAt > 0L) frameInterval = now - lastFrameAt
        lastFrameAt = now
        val stale = staleMs()
        views.entries.toList().forEach { (view, lastDrawn) ->
            if (now - lastDrawn <= stale || pendingViews.containsKey(view)) view.invalidate() else views.remove(view)
        }
        pendingViews.clear()
    }

    private fun watch(view: View) {
        val root = view.rootView
        val observer = root.viewTreeObserver
        if (!observer.isAlive || watchedObservers.containsKey(observer)) return
        observer.addOnPreDrawListener(
            ViewTreeObserver.OnPreDrawListener {
                Guard.run("ComposeLiquidGlass.watch", Unit) { checkMoved(root) }
                true
            }
        )
        watchedObservers[observer] = true
    }

    private fun checkMoved(root: View) {
        var composeMoved = false
        synchronized(surfaces) {
            surfaces.forEach { (key, surface) ->
                if (key is View) {
                    if (key.rootView !== root || !key.isAttachedToWindow) return@forEach
                    key.getLocationOnScreen(watchLocation)
                    if (watchLocation[0] != surface.originX || watchLocation[1] != surface.originY) movedViews.add(key)
                } else if (!composeMoved) {
                    val coordinator = surface.coordinator ?: return@forEach
                    if (isAttachedMethod?.invoke(coordinator) == false) return@forEach
                    val position = localToScreen?.invoke(coordinator, 0L) as? Long ?: return@forEach
                    val x = floor(Float.fromBits((position ushr 32).toInt())).toInt()
                    val y = floor(Float.fromBits((position and 0xFFFFFFFFL).toInt())).toInt()
                    if (x != surface.originX || y != surface.originY) composeMoved = true
                }
            }
        }
        if (movedViews.isNotEmpty()) {
            movedViews.forEach { it.invalidate() }
            movedViews.clear()
        }
        if (composeMoved) {
            frameCount++
            frameState?.call("setIntValue", frameCount)
        }
    }

    fun rootHasGlass(root: View?, ignore: (Any) -> Boolean): Boolean {
        root ?: return false
        val now = SystemClock.uptimeMillis()
        val stale = staleMs()
        return synchronized(surfaces) {
            surfaces.any { (key, surface) -> surface.root?.get() === root && now - surface.lastDrawn <= stale && !ignore(key) }
        }
    }

    fun screenPosition(drawScope: Any): Long? {
        val coordinator = drawScope.field("drawNode")?.field("coordinator") ?: return null
        if (isAttachedMethod?.invoke(coordinator) == false) return null
        return localToScreen?.invoke(coordinator, 0L) as? Long
    }

    fun observeFrames() {
        frameState?.call("getIntValue")
    }

    fun keepFrames(durationMs: Long) {
        framesUntil = maxOf(framesUntil, SystemClock.uptimeMillis() + durationMs)
        requestFrame()
    }

    fun requestFrame() {
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback {
            framePosted = false
            frameCount++
            frameState?.call("setIntValue", frameCount)
            if (SystemClock.uptimeMillis() < framesUntil) requestFrame()
        }
    }

    private fun composeOwner(coordinator: Any): View? =
        coordinator.field(LAYOUT_NODE)?.field(OWNER) as? View

    fun ensureExcluded(root: View) {
        val known = synchronized(excluded) { excluded.any { it.root.get() === root } }
        if (known) return
        excludedAt = 0L
        refreshExcludedLayers()
    }

    private fun windowIndex(root: View): Int = synchronized(excluded) {
        excluded.indexOfFirst { it.root.get() === root }
    }

    private fun isAmbient(root: View): Boolean = synchronized(excluded) {
        excluded.firstOrNull { it.root.get() === root }?.ambient == true
    }

    fun hold(window: View) {
        if (holders.put(window, true) != null) return
        frames.keys.toList().forEach { root ->
            if (root === window || heldRoots.containsKey(root)) return@forEach
            frames[root]?.let { frames[root] = frozen(it) }
            heldRoots[root] = true
        }
    }

    fun release(window: View) {
        if (holders.remove(window) == null) return
        if (holders.isNotEmpty()) return
        val now = SystemClock.uptimeMillis()
        heldRoots.keys.forEach { releasedRoots[it] = now }
        heldRoots.clear()
    }

    private fun settling(root: View, frame: BackdropFrame): Boolean {
        val releasedAt = releasedRoots[root] ?: return false
        if (frame.capturedAt < releasedAt) return true
        releasedRoots.remove(root)
        return false
    }

    private fun frozen(frame: BackdropFrame): BackdropFrame = try {
        frame.copy(blurred = frame.blurred?.takeUnless { it.isRecycled }?.copy(Bitmap.Config.HARDWARE, false))
    } catch (_: Throwable) {
        frame.copy(blurred = null)
    }

    private fun frameFor(surface: GlassSurface): BackdropFrame? =
        surface.root?.get()?.let { frames[it] } ?: frame

    private fun staleMs(): Long = maxOf(STALE_MS, frameInterval * STALE_FRAMES)

    private fun stopIfIdle() {
        if (!registered) return
        val now = SystemClock.uptimeMillis()
        val idle = synchronized(surfaces) {
            surfaces.values.none { now - it.lastDrawn <= staleMs() }
        }
        if (!idle) return
        registered = false
        frame = null
        frames.clear()
        previousFrames.clear()
        lastFrameAt = 0L
        frameInterval = 0L
        releaseBackdrops()
        BackdropCapture.unregister(this)
    }

    private fun refreshExcludedLayers() {
        val now = SystemClock.uptimeMillis()
        if (now - excludedAt < LAYER_REFRESH_MS) return
        excludedAt = now

        val roots = try {
            val global = HookEnv.findClass("android.view.WindowManagerGlobal")
                .callStatic("getInstance")
            (global.field("mRoots") as? List<*>)?.toList()
        } catch (_: Throwable) {
            null
        } ?: return

        val layers = ArrayList<ExcludedWindow>()
        roots.forEach { root ->
            val view = root.call("getView") as? View ?: return@forEach
            if (!view.isAttachedToWindow) return@forEach
            val type = (root.field("mWindowAttributes") as? WindowManager.LayoutParams)?.type
            if (type == null || type < WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW) return@forEach
            val surface = root.call("getSurfaceControl") as? SurfaceControl
            if (surface != null && surface.isValid) {
                layers.add(ExcludedWindow(view, surface, type == WindowManager.LayoutParams.TYPE_STATUS_BAR))
            }
            if (view.visibility == View.VISIBLE) {
                view.display?.let {
                    display = it.displayId
                    displayRefreshRate = it.refreshRate
                }
            }
        }

        synchronized(excluded) {
            excluded.clear()
            excluded.addAll(layers)
        }
    }
}
