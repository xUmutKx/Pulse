package com.drdisagree.glasslayer.xposed.systemui

import android.R as AndroidR
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.AbsSeekBar
import android.widget.SeekBar
import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object SliderGlass {

    private const val SLIDER_DEFAULTS = "androidx.compose.material3.SliderDefaults"
    private const val SLIDER_STATE = "androidx.compose.material3.SliderState"
    private const val FRACTION = "getCoercedValueAsFraction"
    private const val TRACK_DP = 6f
    private const val BASE_SLIDER = "com.google.android.material.slider.BaseSlider"
    private const val BRIGHTNESS = "${SystemUiModule.PACKAGE}.brightness."
    private const val VOLUME = "${SystemUiModule.PACKAGE}.volume."
    private const val MEDIA = "${SystemUiModule.PACKAGE}.media."
    private const val INACTIVE_ALPHA = 0.3f
    private const val TRADITIONAL_MAX_DP = 24f
    private const val TRADITIONAL_THUMB_DP = 48f
    private const val DISABLED_ALPHA = 0.38f
    private const val RTL = "Rtl"

    private val motions = WeakHashMap<Any, SliderThumbMotion>()
    private val viewMotions = WeakHashMap<View, ViewSwitchMotion>()
    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val modifierToOriginal = WeakHashMap<Any, Any>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(SLIDER_DEFAULTS).hookMatching(Regex("Thumb(-.*)?")) { chain ->
            val method = chain.executable as Method
            captureScope(method, chain.args)
            if (!GlassConfig.slidersActive || ownedElsewhere()) return@hookMatching chain.proceed()
            chain.proceedWithArgs { args -> wrap(method, args) }
        }
        hooks += HookEnv.findClass(SLIDER_DEFAULTS).hookMatching(Regex("Track(-.*)?")) { chain ->
            val method = chain.executable as Method
            captureScope(method, chain.args)
            if (!GlassConfig.slidersActive || ownedElsewhere()) return@hookMatching chain.proceed()
            chain.proceedWithArgs { args -> wrapTrack(method, args) }
        }
        hooks += AbsSeekBar::class.java.hookAll("onDraw") { chain ->
            val view = chain.thisObject as? SeekBar
            val canvas = chain.args.firstOrNull() as? Canvas
            if (view == null || canvas == null || !GlassConfig.slidersActive || view.javaClass != SeekBar::class.java || inMedia(view) ||
                view.maxHeight > TRADITIONAL_MAX_DP * view.resources.displayMetrics.density
            ) {
                return@hookAll chain.proceed()
            }
            if (Guard.run("SliderGlass.seekbar", false) { drawSeekBar(view, canvas); true }) null else chain.proceed()
        }
        hooks += HookEnv.findClass(BASE_SLIDER).hookAll("onDraw") { chain ->
            val view = chain.thisObject as? View
            val canvas = chain.args.firstOrNull() as? Canvas
            if (view == null || canvas == null || !GlassConfig.slidersActive) return@hookAll chain.proceed()
            if (Guard.run("SliderGlass.material", false) { drawMaterialSlider(view, canvas) }) null else chain.proceed()
        }
        return hooks
    }

    private fun drawSeekBar(view: SeekBar, canvas: Canvas) {
        val range = (view.max - view.min).coerceAtLeast(1)
        val fraction = (view.progress - view.min).toFloat() / range
        val active = view.progressTintList?.getColorForState(view.drawableState, Color.WHITE) ?: accent(view)
        val inactive = view.progressBackgroundTintList?.getColorForState(view.drawableState, Color.GRAY) ?: ColorBlend.scaleAlpha(active, INACTIVE_ALPHA)
        drawViewSlider(view, canvas, fraction, view.isPressed, active, inactive, if (view.tickMark != null) range else 0)
    }

    private fun drawMaterialSlider(view: View, canvas: Canvas): Boolean {
        val values = view.field("values") as? List<*> ?: return false
        if (values.size != 1) return false
        val thickness = view.field("trackThickness") as? Int ?: return false
        if (thickness > TRADITIONAL_MAX_DP * view.resources.displayMetrics.density) return false
        val value = values.first() as? Float ?: return false
        val from = view.field("valueFrom") as? Float ?: return false
        val to = view.field("valueTo") as? Float ?: return false
        val activeList = view.field("trackColorActive") as? ColorStateList
        val inactiveList = view.field("trackColorInactive") as? ColorStateList
        val active = activeList?.getColorForState(view.drawableState, activeList.defaultColor) ?: accent(view)
        val inactive = inactiveList?.getColorForState(view.drawableState, inactiveList.defaultColor) ?: ColorBlend.scaleAlpha(active, INACTIVE_ALPHA)
        val fraction = if (to > from) (value - from) / (to - from) else 0f
        val step = view.field("stepSize") as? Float ?: 0f
        val shown = (view.field("ticksCoordinates") as? FloatArray)?.isNotEmpty() == true
        val ticks = if (step > 0f && shown && to > from) ((to - from) / step).roundToInt() else 0
        val tickActiveList = view.field("tickColorActive") as? ColorStateList
        val tickInactiveList = view.field("tickColorInactive") as? ColorStateList
        drawViewSlider(
            view,
            canvas,
            fraction,
            view.field("thumbIsPressed") == true,
            active,
            inactive,
            ticks,
            tickActiveList?.getColorForState(view.drawableState, tickActiveList.defaultColor) ?: inactive,
            tickInactiveList?.getColorForState(view.drawableState, tickInactiveList.defaultColor) ?: active
        )
        return true
    }

    private fun drawViewSlider(
        view: View,
        canvas: Canvas,
        fraction: Float,
        pressed: Boolean,
        active: Int,
        inactive: Int,
        ticks: Int = 0,
        tickActive: Int = inactive,
        tickInactive: Int = active
    ) {
        val motion = viewMotions.getOrPut(view) { ViewSwitchMotion() }
        motion.update(fraction, pressed, SystemClock.uptimeMillis())
        if (motion.animating) view.postInvalidateOnAnimation()
        val behind = SurfaceBehind.color(view)
        motion.thumb.drawSlider(
            canvas,
            0f,
            view.width.toFloat(),
            view.height / 2f,
            fraction,
            view.layoutDirection == View.LAYOUT_DIRECTION_RTL,
            motion.press,
            motion.velocity,
            view.resources.displayMetrics.density,
            active,
            inactive,
            if (Color.alpha(behind) == 0) Color.BLACK else behind,
            if (view.isEnabled) 1f else DISABLED_ALPHA,
            ticks,
            tickActive,
            tickInactive,
            0f,
            view.height.toFloat()
        )
    }

    private fun accent(view: View): Int {
        val value = TypedValue()
        return if (view.context.theme.resolveAttribute(AndroidR.attr.colorAccent, value, true)) value.data else Color.WHITE
    }

    private fun inMedia(view: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent.javaClass.name.startsWith(MEDIA)) return true
            parent = parent.parent
        }
        return false
    }

    private fun ownedElsewhere(): Boolean = Thread.currentThread().stackTrace.any { element ->
        val name = element.className
        name.startsWith(BRIGHTNESS) || name.startsWith(VOLUME) || name.startsWith(MEDIA)
    }

    private fun captureScope(method: Method, args: List<Any?>) {
        val composerIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
        args.getOrNull(composerIndex)?.call("getRecomposeScope")?.let { recomposeScopes.add(it) }
    }

    private fun wrapTrack(method: Method, args: Array<Any?>) {
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        val colorsIndex = types.indexOfFirst { it.name == SliderColorsTint.SLIDER_COLORS_CLASS }
        val stateIndex = types.indexOfFirst { it.name == SLIDER_STATE }
        if (modifierIndex == -1 || colorsIndex == -1 || stateIndex == -1) return
        val colors = args[colorsIndex]
        val state = args[stateIndex] ?: return
        val enabled = args.getOrNull(modifierIndex + 1) as? Boolean ?: true
        val passed = args[modifierIndex] ?: return
        val original = modifierToOriginal[passed] ?: passed
        val drawer = ComposeModifiers.contentProxy("SliderGlass.track") { drawScope ->
            drawTrack(drawScope, state, colors, enabled)
        } ?: return
        ComposeModifiers.drawWithContent(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[modifierIndex] = it
        }
    }

    private fun drawTrack(drawScope: Any, state: Any, colors: Any?, enabled: Boolean) {
        val canvas = ComposeToolkit.nativeCanvas(drawScope) ?: return drawScope.drawStock()
        val size = ComposeToolkit.drawSize(drawScope) ?: return drawScope.drawStock()
        val fraction = (state.call(FRACTION) as? Float ?: return drawScope.drawStock()).coerceIn(0f, 1f)
        val width = Float.fromBits((size ushr 32).toInt())
        val height = Float.fromBits((size and 0xFFFFFFFFL).toInt())
        if (width <= 0f || height <= 0f) return
        val density = drawScope.call("getDensity") as? Float ?: 1f
        if (minOf(width, height) > TRADITIONAL_MAX_DP * density) return drawScope.drawStock()
        val thickness = TRACK_DP * density
        val horizontal = width >= height
        val rtl = drawScope.call("getLayoutDirection")?.toString() == RTL
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        val active = colors.color(if (enabled) "activeTrackColor" else "disabledActiveTrackColor")
        val inactive = colors.color(if (enabled) "inactiveTrackColor" else "disabledInactiveTrackColor")
        val radius = thickness / 2f
        if (horizontal) {
            val top = (height - thickness) / 2f
            trackRect.set(0f, top, width, top + thickness)
            trackPaint.color = inactive
            trackPaint.alpha = (Color.alpha(inactive) * alpha).toInt()
            canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
            val length = (width * fraction).coerceAtLeast(thickness)
            if (rtl) trackRect.set(width - length, top, width, top + thickness) else trackRect.set(0f, top, length, top + thickness)
        } else {
            val left = (width - thickness) / 2f
            trackRect.set(left, 0f, left + thickness, height)
            trackPaint.color = inactive
            trackPaint.alpha = (Color.alpha(inactive) * alpha).toInt()
            canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
            val length = (height * fraction).coerceAtLeast(thickness)
            trackRect.set(left, 0f, left + thickness, length)
        }
        trackPaint.color = active
        trackPaint.alpha = (Color.alpha(active) * alpha).toInt()
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
    }

    fun refresh() {
        mainHandler.post { recomposeScopes.toList().forEach { it.call("invalidateForResult", null) } }
    }

    private fun wrap(method: Method, args: Array<Any?>) {
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        val colorsIndex = types.indexOfFirst { it.name == SliderColorsTint.SLIDER_COLORS_CLASS }
        if (modifierIndex == -1 || colorsIndex == -1) return
        val colors = args[colorsIndex]
        val enabled = args.getOrNull(colorsIndex + 1) as? Boolean ?: true
        val passed = args[modifierIndex] ?: return
        val original = modifierToOriginal[passed] ?: passed
        val drawer = ComposeModifiers.contentProxy("SliderGlass.thumb") { drawScope ->
            drawThumb(drawScope, colors, enabled)
        } ?: return
        ComposeModifiers.drawWithContent(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[modifierIndex] = it
        }
    }

    private fun drawThumb(drawScope: Any, colors: Any?, enabled: Boolean) {
        ComposeLiquidGlass.observeFrames()
        val canvas = ComposeToolkit.nativeCanvas(drawScope) ?: return drawScope.drawStock()
        val size = ComposeToolkit.drawSize(drawScope) ?: return drawScope.drawStock()
        val node = drawScope.field("drawNode") ?: return drawScope.drawStock()
        val width = Float.fromBits((size ushr 32).toInt())
        val height = Float.fromBits((size and 0xFFFFFFFFL).toInt())
        if (width <= 0f || height <= 0f) return

        val thumbDensity = drawScope.call("getDensity") as? Float ?: 1f
        if (maxOf(width, height) > TRADITIONAL_THUMB_DP * thumbDensity) return drawScope.drawStock()
        val horizontal = height >= width
        val motion = motions.getOrPut(node) { SliderThumbMotion() }
        motion.update(minOf(width, height), SystemClock.uptimeMillis())
        if (motion.animating) ComposeLiquidGlass.requestFrame()

        val active = colors.color(if (enabled) "activeTrackColor" else "disabledActiveTrackColor")
        val inactive = colors.color(if (enabled) "inactiveTrackColor" else "disabledInactiveTrackColor")
        val behind = SurfaceBehind.color(ComposeToolkit.ownerView(drawScope))
        val rtl = drawScope.call("getLayoutDirection")?.toString() == RTL
        motion.thumb.drawSliderThumb(
            canvas,
            width,
            height,
            horizontal,
            if (horizontal) !rtl else false,
            motion.press,
            thumbDensity,
            active,
            inactive,
            if (Color.alpha(behind) == 0) Color.BLACK else behind,
            if (enabled) 1f else DISABLED_ALPHA
        )
    }

    private fun Any.drawStock() {
        call("drawContent")
    }

    private fun Any?.color(name: String): Int {
        val packed = this?.field(name) as? Long ?: return Color.TRANSPARENT
        return ComposeToolkit.toArgb(packed) ?: Color.TRANSPARENT
    }
}
