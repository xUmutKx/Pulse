package com.drdisagree.glasslayer.xposed.systemui

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.R as AndroidR
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.glass.GlassRim
import com.drdisagree.glasslayer.xposed.glass.PressHighlight
import com.drdisagree.glasslayer.xposed.glass.PressSpring
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import io.github.libxposed.api.XposedInterface.Chain
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

object ButtonGlass {

    private const val BUTTON_KT = "androidx.compose.material3.ButtonKt"
    private const val ICON_BUTTON_KT = "androidx.compose.material3.IconButtonKt"
    private const val SURFACE_KT = "androidx.compose.material3.SurfaceKt"
    private const val MATERIAL_BUTTON = "com.google.android.material.button.MaterialButton"
    private const val SURFACE_ARGS = 5
    private const val COLOR_INDEX = 2
    private const val BORDER_INDEX = 3
    private const val OPAQUE_PARENT = 230
    private const val MAX_RETRIES = 3
    private const val OPAQUE = 255
    private const val PRESS_GROW_DP = 2f
    private const val MAX_KNOWN = 128
    private const val LOW_BITS = 0xFFFFFFFFL
    private const val RADII = 8
    private val ENABLED = intArrayOf(AndroidR.attr.state_enabled)
    private val DISABLED = intArrayOf(-AndroidR.attr.state_enabled)

    private val pending = ArrayDeque<ComposeButton>()
    private val surfaceColors = ArrayDeque<Int>()
    private val known = RecentMap<Any, ComposeButton>(MAX_KNOWN)
    private val springs = WeakHashMap<Any, PressSpring>()
    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val owned = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val checked = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val retries = WeakHashMap<View, Int>()
    private val fillLayers = WeakHashMap<LayerDrawable, List<Int>>()
    private val accentTexts = Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    private val stockTextColors = WeakHashMap<TextView, ColorStateList>()
    private val boostedTextColors = WeakHashMap<TextView, ColorStateList>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rimRect = RectF()
    private val rimRadii = FloatArray(RADII)
    private val fallbackPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun install(): Int {
        var hooks = 0
        hooks += TouchTracker.install()
        hooks += HookEnv.findClass(BUTTON_KT).hookAll("Button") { chain -> trackCompose(chain) }
        hooks += HookEnv.findClass(ICON_BUTTON_KT).hookMatching(Regex("(Filled(Tonal)?|Outlined)IconButton(-.*)?")) { chain -> trackCompose(chain) }
        hooks += HookEnv.findClass(SURFACE_KT).hookMatching(Regex("surface-.*")) { chain -> surface(chain) }
        hooks += HookEnv.findClass(SURFACE_KT).hookMatching(Regex("Surface-.*")) { chain -> trackSurface(chain) }
        ViewGlassUnderlay.discoverer = { view -> discover(view) }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            recomposeScopes.toList().forEach { it.call("invalidateForResult", null) }
            owned.toList().forEach { it.invalidate() }
            accentTexts.toList().forEach { Guard.run("ButtonGlass.text", Unit) { boostText(it) } }
        }
    }

    fun owns(view: View): Boolean = view in owned

    fun release(view: View) {
        owned.remove(view)
    }

    private fun trackCompose(chain: Chain): Any? {
        val method = chain.executable as Method
        val types = method.parameterTypes
        val args = chain.args
        val composerIndex = types.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
        args.getOrNull(composerIndex)?.call("getRecomposeScope")?.let { recomposeScopes.add(it) }
        val enabledIndex = types.indexOfFirst { it == Boolean::class.javaPrimitiveType }
        val mask = args.lastOrNull() as? Int ?: 0
        val defaulted = enabledIndex in 0 until Int.SIZE_BITS && mask and (1 shl enabledIndex) != 0
        val enabled = defaulted || args.getOrNull(enabledIndex) as? Boolean ?: true
        val parent = surfaceColors.lastOrNull()
        pending.addLast(ComposeButton(enabled, parent != null && Color.alpha(parent) >= OPAQUE_PARENT, parent))
        try {
            return chain.proceed()
        } finally {
            pending.removeLastOrNull()
        }
    }

    private fun trackSurface(chain: Chain): Any? {
        val types = (chain.executable as Method).parameterTypes
        val shapeIndex = types.indexOfFirst { it.name == ComposeToolkit.SHAPE_CLASS }
        val colorIndex = if (shapeIndex >= 0) shapeIndex + 1 else -1
        val packed = chain.args.getOrNull(colorIndex) as? Long ?: return chain.proceed()
        val argb = ComposeToolkit.toArgb(packed) ?: return chain.proceed()
        surfaceColors.addLast(argb)
        try {
            return chain.proceed()
        } finally {
            surfaceColors.removeLastOrNull()
        }
    }

    private fun surface(chain: Chain): Any? {
        val args = chain.args
        if (args.size < SURFACE_ARGS) return chain.proceed()
        val receiver = args[0] ?: return chain.proceed()
        val top = pending.lastOrNull()
        val button = if (top != null && !top.consumed) {
            top.consumed = true
            known[receiver] = top
            top
        } else {
            known[receiver]
        } ?: return chain.proceed()
        if (!GlassConfig.buttonsActive) return chain.proceed()
        val shape = args[1] ?: return chain.proceed()
        val color = args[COLOR_INDEX] as? Long ?: return chain.proceed()
        val argb = ComposeToolkit.toArgb(color) ?: return chain.proceed()
        val outlined = Color.alpha(argb) == 0 && args.getOrNull(BORDER_INDEX) != null
        if (Color.alpha(argb) == 0 && !outlined) return chain.proceed()
        val drawer = ComposeModifiers.contentProxy("ButtonGlass.draw") { drawScope -> drawCompose(drawScope, shape, button, argb) }
            ?: return chain.proceed()
        val wrapped = ComposeModifiers.drawWithContent(receiver, drawer) ?: return chain.proceed()
        return chain.proceedWithArgs { values ->
            values[0] = wrapped
            values[COLOR_INDEX] = ComposeToolkit.colorOf(Color.TRANSPARENT)
            if (outlined && !button.onOpaque) values[BORDER_INDEX] = null
        }
    }

    private fun tinted(argb: Int): Int {
        val alpha = minOf(Color.alpha(argb), (OPAQUE * GlassConfig.buttonTint).toInt().coerceIn(0, OPAQUE))
        return ColorUtils.setAlphaComponent(argb, alpha)
    }

    private fun drawCompose(drawScope: Any, shape: Any, button: ComposeButton, fill: Int) {
        ComposeLiquidGlass.observeFrames()
        val canvas = ComposeToolkit.nativeCanvas(drawScope)
        val size = ComposeToolkit.drawSize(drawScope)
        val node = drawScope.field("drawNode")
        if (canvas == null || size == null || node == null) {
            drawScope.call("drawContent")
            return
        }
        if (!GlassConfig.buttonsActive) {
            drawFill(canvas, drawScope, shape, size, fill)
            drawScope.call("drawContent")
            return
        }
        TouchTracker.listening = true
        val width = Float.fromBits((size ushr 32).toInt())
        val height = Float.fromBits((size and LOW_BITS).toInt())
        val origin = ComposeLiquidGlass.screenPosition(drawScope)
        val left = origin?.let { Float.fromBits((it ushr 32).toInt()) } ?: 0f
        val top = origin?.let { Float.fromBits((it and LOW_BITS).toInt()) } ?: 0f
        val spring = springs.getOrPut(node) { PressSpring() }
        spring.update(button.enabled && origin != null && TouchTracker.pressing(left, top, width, height), SystemClock.uptimeMillis())
        if (spring.animating) ComposeLiquidGlass.requestFrame()
        val density = drawScope.call("getDensity") as? Float ?: 1f
        val press = spring.value
        val scale = if (GlassConfig.buttonPress) 1f + PRESS_GROW_DP * density / height.coerceAtLeast(1f) * press.coerceIn(0f, 1f) else 1f
        val count = canvas.save()
        try {
            canvas.scale(scale, scale, width / 2f, height / 2f)
            val style = GlassConfig.surface(PrefKeys.BUTTONS)
            val owner = ComposeToolkit.ownerView(drawScope)
            val onGlass = !button.onOpaque && (!GlassConfig.glassAvailable || ComposeLiquidGlass.rootHasGlass(owner?.rootView) { springs.containsKey(it) })
            val live = GlassConfig.glassAvailable && onGlass
            if (live) {
                Guard.run("ButtonGlass.glass", Unit) {
                    ComposeLiquidGlass.draw(drawScope, shape, style.blurRadiusPx, baseOf(owner, button), style.style)
                }
            }
            val tintedFill = if (onGlass) tinted(fill) else fill
            drawFill(canvas, drawScope, shape, size, tintedFill)
            if (onGlass && AccentColors.isAccent(fill)) {
                val layer = canvas.saveLayer(0f, 0f, width, height, ContrastBoost.paintFor(fill))
                try {
                    drawScope.call("drawContent")
                } finally {
                    canvas.restoreToCount(layer)
                }
            } else {
                drawScope.call("drawContent")
            }
            val outline = ComposeToolkit.outline(shape, drawScope)
            val radius = (outline?.let { ComposeToolkit.outlineCornerRadius(it) } ?: (height / 2f)).coerceAtMost(minOf(width, height) / 2f)
            rimRect.set(0f, 0f, width, height)
            rimRadii.fill(radius)
            val strength = if (live) Color.alpha(tintedFill) / OPAQUE.toFloat() else 1f
            GlassRim.draw(canvas, rimRect, rimRadii, density, strength, sheen = false)
            if (press > 0f) {
                val path = outline?.let { ComposeToolkit.outlinePath(it) } ?: fallbackPath.apply {
                    rewind()
                    addRoundRect(0f, 0f, width, height, radius, radius, Path.Direction.CW)
                }
                PressHighlight.draw(canvas, path, width, height, TouchTracker.x - left, TouchTracker.y - top, press)
            }
        } finally {
            canvas.restoreToCount(count)
        }
    }

    fun ownsNode(key: Any): Boolean = springs.containsKey(key)

    private fun baseOf(owner: View?, button: ComposeButton): Int {
        val behind = owner?.let { ViewGlassUnderlay.glassBehind(it) } ?: Color.TRANSPARENT
        if (Color.alpha(behind) > 0) return behind
        return button.surface?.takeIf { Color.alpha(it) in 1 until OPAQUE_PARENT } ?: Color.TRANSPARENT
    }

    private fun drawFill(canvas: Canvas, drawScope: Any, shape: Any, size: Long, color: Int) {
        if (Color.alpha(color) == 0) return
        val width = Float.fromBits((size ushr 32).toInt())
        val height = Float.fromBits((size and LOW_BITS).toInt())
        val outline = ComposeToolkit.outline(shape, drawScope)
        val path = outline?.let { ComposeToolkit.outlinePath(it) } ?: fallbackPath.apply {
            rewind()
            val radius = (outline?.let { ComposeToolkit.outlineCornerRadius(it) } ?: 0f).coerceAtMost(minOf(width, height) / 2f)
            addRoundRect(0f, 0f, width, height, radius, radius, Path.Direction.CW)
        }
        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
    }

    private fun discover(view: View) {
        if (view !is Button && view !is ImageButton) return
        if (view is CompoundButton || !checked.add(view)) return
        trackView(view)
    }

    private fun trackView(view: View) {
        if (view is TextView && filled(view) && AccentColors.isAccent(view)) {
            accentTexts.add(view)
            boostText(view)
        }
        if (ViewGlassUnderlay.isAttached(view)) return
        if (view is ImageView && !filled(view) && SolidShapes.of(view.drawable) != null) {
            owned.add(view)
            ViewGlassUnderlay.attach(
                view,
                PrefKeys.BUTTONS,
                { GlassConfig.buttonsActive },
                { GlassConfig.buttonTint },
                source = { (it as? ImageView)?.drawable?.let { icon -> SolidShapes.of(icon) } },
                imageContent = true,
                rimOnTop = true
            )
            ViewGlassUnderlay.attachPress(view) { GlassConfig.buttonsActive }
            return
        }
        if (outlined(view)) {
            owned.add(view)
            ViewGlassUnderlay.attach(view, PrefKeys.BUTTONS, { GlassConfig.buttonsActive }, { 0f }, rimOnTop = true)
            ViewGlassUnderlay.attachPress(view) { GlassConfig.buttonsActive }
            return
        }
        if (!filled(view)) {
            maybeRetry(view)
            return
        }
        owned.add(view)
        ViewGlassUnderlay.attach(
            view,
            PrefKeys.BUTTONS,
            { GlassConfig.buttonsActive },
            { GlassConfig.buttonTint },
            source = { singleFill(it) },
            rimOnTop = true
        )
        ViewGlassUnderlay.attachPress(view) { GlassConfig.buttonsActive }
    }

    private fun boostText(view: TextView) {
        val current = view.textColors
        if (!GlassConfig.buttonsActive) {
            val stock = stockTextColors.remove(view) ?: return
            if (boostedTextColors.remove(view) === current) view.setTextColor(stock)
            return
        }
        if (boostedTextColors[view] === current) return
        val fillList = view.backgroundTintList ?: SolidShapes.of(view.background)?.color ?: return
        val fill = fillList.getColorForState(ENABLED, fillList.defaultColor)
        val disabled = current.getColorForState(DISABLED, current.defaultColor)
        val enabled = current.getColorForState(ENABLED, current.defaultColor)
        val boosted = ColorStateList(arrayOf(DISABLED, intArrayOf()), intArrayOf(disabled, ContrastBoost.color(enabled, fill)))
        stockTextColors[view] = current
        boostedTextColors[view] = boosted
        view.setTextColor(boosted)
    }

    private fun filled(view: View): Boolean {
        val background = view.background ?: return false
        if (SolidShapes.of(background) != null) return true
        if (!isMaterialButton(view.javaClass)) return false
        val tint = view.backgroundTintList ?: return false
        val stroke = view.call("getStrokeWidth") as? Int ?: 0
        return Color.alpha(tint.defaultColor) > 0 && stroke == 0
    }

    private fun singleFill(view: View): Drawable? {
        val background = view.background ?: return null
        if (!isMaterialButton(view.javaClass)) return background
        val layers = materialLayers(background) ?: return background
        if (layers.numberOfLayers < 2) return background
        val fills = fillLayers[layers] ?: detectFills(layers)?.also { fillLayers[layers] = it } ?: return background
        val keep = fills.lastOrNull() ?: return background
        val hidden = if (GlassConfig.buttonsActive) 0 else OPAQUE
        for (index in fills) {
            if (index == keep) continue
            layers.getDrawable(index)?.let { if (it.alpha != hidden) it.alpha = hidden }
        }
        return layers.getDrawable(keep) ?: background
    }

    private fun detectFills(layers: LayerDrawable): List<Int>? {
        val fills = (0 until layers.numberOfLayers).filter { ShapePaints.hasFill(layers.getDrawable(it)) }
        return fills.takeIf { it.isNotEmpty() }
    }

    private fun materialLayers(drawable: Drawable?): LayerDrawable? = when (drawable) {
        is RippleDrawable -> (0 until drawable.numberOfLayers)
            .firstOrNull { drawable.getId(it) != AndroidR.id.mask }
            ?.let { materialLayers(drawable.getDrawable(it)) }
        is DrawableWrapper -> materialLayers(drawable.drawable)
        is LayerDrawable -> drawable
        else -> null
    }

    private fun outlined(view: View): Boolean {
        val background = view.background ?: return false
        if (isMaterialButton(view.javaClass)) {
            val fill = view.backgroundTintList?.let { it.getColorForState(view.drawableState, it.defaultColor) } ?: Color.TRANSPARENT
            if (Color.alpha(fill) != 0) return false
            val stroke = view.call("getStrokeWidth") as? Int
            return if (stroke != null) stroke > 0 else ShapePaints.hasStroke(background)
        }
        return SolidShapes.of(background) == null && ShapePaints.hasStroke(background)
    }

    private fun maybeRetry(view: View) {
        if (!isMaterialButton(view.javaClass) && view.background == null) return
        val tries = (retries[view] ?: 0) + 1
        if (tries > MAX_RETRIES) return
        retries[view] = tries
        checked.remove(view)
    }

    private fun isMaterialButton(type: Class<*>): Boolean {
        var current: Class<*>? = type
        while (current != null && current != View::class.java) {
            if (current.name == MATERIAL_BUTTON) return true
            current = current.superclass
        }
        return false
    }
}
