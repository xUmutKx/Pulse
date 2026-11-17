package com.drdisagree.glasslayer.xposed.systemui

import android.content.res.Resources
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.hookMatchingBefore
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.math.roundToInt

object BrightnessGlass {

    private const val SLIDER_DEFAULTS = "androidx.compose.material3.SliderDefaults"
    private const val SLIDER_COLORS_CLASS = "androidx.compose.material3.SliderColors"
    private const val BRIGHTNESS_SLIDER_KT = "${SystemUiModule.PACKAGE}.brightness.ui.compose.BrightnessSliderKt"
    private const val BRIGHTNESS_COMPOSE_PACKAGE = "${SystemUiModule.PACKAGE}.brightness.ui.compose."
    private const val MIRROR_GLASS_KEY = 100
    private const val BRIGHTNESS_VIEW_MODEL_SUFFIX = "BrightnessSliderViewModel"
    private const val BRIGHTNESS_DIMENSIONS_SUFFIX = "BrightnessSliderDimensions"
    private const val CONTAINER_COLORS_SUFFIX = "brightness.ui.compose.ContainerColors"
    private val SLIDER_COLOR_FIELDS = listOf(
        "thumbColor",
        "activeTrackColor",
        "activeTickColor",
        "inactiveTrackColor",
        "inactiveTickColor",
        "disabledThumbColor",
        "disabledActiveTrackColor",
        "disabledActiveTickColor",
        "disabledInactiveTrackColor",
        "disabledInactiveTickColor"
    )

    private var sliderGlassPass = 0
    private var sliderGlassSegment = 0
    private var brightnessViewModel: WeakReference<Any>? = null
    private var mirrorFrameWidthDp: Float? = null
    private var mirrorFrameHeightDp: Float? = null
    private var mirrorCornerDp: Float? = null
    private var mirrorThumbOverhangDp = 0f

    private val sliderColorsConstructor: Constructor<*>? by lazy {
        HookEnv.findClass(SLIDER_COLORS_CLASS)
            ?.declaredConstructors
            ?.firstOrNull { constructor ->
                constructor.parameterTypes.size == SLIDER_COLOR_FIELDS.size &&
                        constructor.parameterTypes.all { it == Long::class.javaPrimitiveType }
            }
            ?.apply { isAccessible = true }
    }

    fun install(): Int {
        var hooks = 0
        val sliderDefaults = HookEnv.findClass(SLIDER_DEFAULTS)

        hooks += sliderDefaults.hookMatching(Regex("Track.*")) { chain ->
            chain.proceedWithArgs { args ->
                if (!GlassConfig.brightnessActive || !isCalledFromBrightnessSlider()) return@proceedWithArgs
                glassBrightnessSlider(chain.executable as Method, args)
            }
        }

        hooks += sliderDefaults.hookMatchingBefore(Regex("drawTrackPath.*")) { chain ->
            drawSliderGlassSegment(chain.args)
        }

        hooks += HookEnv.findClass(BRIGHTNESS_SLIDER_KT).hookAll("BrightnessSliderContainer") { chain ->
            chain.proceedWithArgs { args -> captureBrightnessContainer(args) }
        }
        return hooks
    }

    private fun isCalledFromBrightnessSlider(): Boolean =
        Throwable().stackTrace.any { it.className.startsWith(BRIGHTNESS_COMPOSE_PACKAGE) }

    private fun glassBrightnessSlider(method: Method, args: Array<Any?>) {
        if (!method.name.startsWith("Track")) return
        if (method.name.startsWith("TrackImpl")) return

        val types = method.parameterTypes
        val colorsIndex = types.indexOfFirst { it.name == SLIDER_COLORS_CLASS }
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (colorsIndex == -1 || modifierIndex == -1) return

        args[colorsIndex]?.let { glassSliderColors(it) }?.let { args[colorsIndex] = it }

        val drawer = ComposeModifiers.contentProxy("BrightnessGlass.draw") { drawScope ->
            drawMirrorGlass(drawScope)
            sliderGlassPass++
            try {
                drawScope.call("drawContent")
            } finally {
                sliderGlassPass--
                if (sliderGlassPass == 0) sliderGlassSegment = 0
            }
        } ?: return

        val passed = args[modifierIndex]
        val original = passed ?: ComposeToolkit.emptyModifier ?: return
        try {
            ComposeModifiers.drawWithContent(original, drawer)?.let {
                args[modifierIndex] = it
                if (passed == null) ComposeToolkit.clearDefaultBit(method, args, modifierIndex)
            }
        } catch (_: Throwable) {
        }
    }

    private fun captureBrightnessContainer(args: Array<Any?>) {
        args.firstOrNull { it?.javaClass?.name?.endsWith(BRIGHTNESS_VIEW_MODEL_SUFFIX) == true }
            ?.let { brightnessViewModel = WeakReference(it) }

        val dimensions = args.firstOrNull { it?.javaClass?.name?.endsWith(BRIGHTNESS_DIMENSIONS_SUFFIX) == true }
            ?: HookEnv.findClass("${SystemUiModule.PACKAGE}.brightness.ui.compose.BrightnessSliderDimensions")
                ?.field("Default")
        mirrorFrameWidthDp = dimensions?.field("backgroundFrameWidth") as? Float
        mirrorFrameHeightDp = dimensions?.field("backgroundFrameHeight") as? Float
        mirrorCornerDp = dimensions?.field("backgroundRoundedCorner") as? Float
        val thumbHeight = dimensions?.field("thumbHeight") as? Float
        val trackHeight = dimensions?.field("trackHeight") as? Float
        mirrorThumbOverhangDp = if (thumbHeight != null && trackHeight != null) {
            ((thumbHeight - trackHeight) / 2f).coerceAtLeast(0f)
        } else {
            0f
        }

        if (!GlassConfig.brightnessActive) return
        val colorsIndex = args.indexOfFirst { it?.javaClass?.name?.endsWith(CONTAINER_COLORS_SUFFIX) == true }
        if (colorsIndex == -1) return
        val colors = args[colorsIndex] ?: return
        val idle = colors.field("idleColor") as? Long ?: return
        val mirror = colors.field("mirrorColor") as? Long ?: return
        val argb = ComposeToolkit.toArgb(mirror) ?: return
        val scaled = ComposeToolkit.colorOf(
            ColorUtils.setAlphaComponent(argb, (255 * GlassConfig.brightnessTint).roundToInt())
        )

        try {
            colors.javaClass.declaredConstructors
                .firstOrNull { constructor ->
                    constructor.parameterTypes.size == 2 &&
                            constructor.parameterTypes.all { it == Long::class.javaPrimitiveType }
                }
                ?.apply { isAccessible = true }
                ?.newInstance(idle, scaled)
                ?.let { args[colorsIndex] = it }
        } catch (_: Throwable) {
        }
    }

    private fun drawMirrorGlass(drawScope: Any) {
        if (!GlassConfig.brightnessActive) return
        val viewModel = brightnessViewModel?.get() ?: return
        val frameWidth = mirrorFrameWidthDp ?: return
        val frameHeight = mirrorFrameHeightDp ?: return
        val corner = mirrorCornerDp ?: return
        if (!viewModel.isMirrorShowing()) return

        val packedSize = ComposeToolkit.drawSize(drawScope) ?: return
        val width = Float.fromBits((packedSize ushr 32).toInt())
        val height = Float.fromBits((packedSize and 0xFFFFFFFFL).toInt())
        val density = Resources.getSystem().displayMetrics.density
        val insetX = frameWidth * density
        val insetY = (frameHeight + mirrorThumbOverhangDp) * density
        val radius = corner * density

        Guard.run("BrightnessGlass.mirror_glass", Unit) {
            ComposeLiquidGlass.drawSegment(
                drawScope,
                MIRROR_GLASS_KEY,
                -insetX,
                -insetY,
                width + insetX * 2f,
                height + insetY * 2f,
                FloatArray(8) { radius },
                GlassConfig.surface(PrefKeys.BRIGHTNESS).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.BRIGHTNESS).style
            )
        }
    }

    private fun Any.isMirrorShowing(): Boolean {
        (call("getShowMirror") as? Boolean)?.let { return it }
        val delegate = field("showMirror${'$'}delegate") ?: return false
        (delegate.call("getValue") as? Boolean)?.let { return it }
        delegate.javaClass.declaredFields.forEach { field ->
            val value = try {
                field.isAccessible = true
                field.get(delegate)
            } catch (_: Throwable) {
                null
            }
            (value?.call("getValue") as? Boolean)?.let { return it }
        }
        return false
    }

    private fun drawSliderGlassSegment(args: List<Any?>) {
        if (sliderGlassPass <= 0 || !GlassConfig.brightnessActive || args.size < 7) return
        val drawScope = args[0] ?: return
        val offset = args[2] as? Long ?: return
        val size = args[3] as? Long ?: return
        val startRadius = args[5] as? Float ?: return
        val endRadius = args[6] as? Float ?: return
        val vertical = args[1].toString() == "Vertical"

        val radii = if (vertical) {
            floatArrayOf(
                startRadius, startRadius, startRadius, startRadius,
                endRadius, endRadius, endRadius, endRadius
            )
        } else {
            floatArrayOf(
                startRadius, startRadius, endRadius, endRadius,
                endRadius, endRadius, startRadius, startRadius
            )
        }

        Guard.run("BrightnessGlass.slider_glass", Unit) {
            ComposeLiquidGlass.drawSegment(
                drawScope,
                sliderGlassSegment++,
                Float.fromBits((offset ushr 32).toInt()),
                Float.fromBits((offset and 0xFFFFFFFFL).toInt()),
                Float.fromBits((size ushr 32).toInt()),
                Float.fromBits((size and 0xFFFFFFFFL).toInt()),
                radii,
                GlassConfig.surface(PrefKeys.BRIGHTNESS).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.BRIGHTNESS).style
            )
        }
    }

    private fun glassSliderColors(original: Any): Any? {
        val values = SLIDER_COLOR_FIELDS.map { name ->
            val color = original.field(name) as? Long ?: return null
            val factor = when {
                name.contains("InactiveTrack") || name.contains("inactiveTrack") -> GlassConfig.brightnessTint
                name.contains("ActiveTrack") || name.contains("activeTrack") -> GlassConfig.activeBrightnessTint
                else -> return@map color
            }
            val argb = ComposeToolkit.toArgb(color) ?: return null
            ComposeToolkit.colorOf(
                ColorUtils.setAlphaComponent(argb, (255 * factor).roundToInt())
            )
        }

        return try {
            sliderColorsConstructor?.newInstance(*values.toTypedArray())
        } catch (_: Throwable) {
            null
        }
    }
}
