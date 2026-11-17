package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object BouncerGlass {

    private const val NUM_PAD_KEY = "com.android.keyguard.NumPadKey"
    private const val NUM_PAD_BUTTON = "com.android.keyguard.NumPadButton"
    private const val EMERGENCY_BUTTON = "com.android.keyguard.EmergencyButton"
    private const val PIN_BOUNCER_KT = "${SystemUiModule.PACKAGE}.bouncer.ui.composable.PinBouncerKt"
    private const val DRAW_SCOPE = "androidx.compose.ui.graphics.drawscope.DrawScope"
    private const val BOUNCER_CONTENT_KT = "${SystemUiModule.PACKAGE}.bouncer.ui.composable.BouncerContentKt"
    private const val CLIP_KT = "androidx.compose.ui.draw.ClipKt"
    private const val BACKGROUND_KT = "androidx.compose.foundation.BackgroundKt"
    private const val BACKGROUND_ARGS = 3
    private const val PIN_GLASS_KEY = 0
    private const val ROUND_RECT_ARGS = 5

    private val views = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val modifierToOriginal = WeakHashMap<Any, Any>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pinDrawDepth = 0
    private var actionDepth = 0
    private var actionShape: Any? = null

    fun install(): Int {
        var hooks = 0
        listOf(NUM_PAD_KEY, NUM_PAD_BUTTON).forEach { name ->
            val type = HookEnv.findClass(name)
            listOf("onLayout", "onConfigurationChanged").forEach { method ->
                hooks += type.hookAllAfter(method) { chain, _ -> track(chain.thisObject as View) }
            }
        }
        hooks += HookEnv.findClass(EMERGENCY_BUTTON).hookAllAfter("onFinishInflate") { chain, _ ->
            track(chain.thisObject as View)
        }
        hooks += HookEnv.findClass(PIN_BOUNCER_KT).hookMatching(Regex("PinPadButton(-.*)?")) { chain ->
            val method = chain.executable as Method
            captureRecomposeScope(method, chain.args)
            chain.proceedWithArgs { args -> applyComposeGlass(method, args) }
        }
        hooks += HookEnv.findClass(DRAW_SCOPE).hookMatching(Regex("drawRoundRect-[^\$]*[\$]default")) { chain ->
            if (pinDrawDepth > 0 && GlassConfig.bouncerActive) {
                Guard.run("BouncerGlass.pin_glass", Unit) { drawPinGlass(chain.args) }
            }
            chain.proceed()
        }
        hooks += HookEnv.findClass(BOUNCER_CONTENT_KT).hookMatching(Regex("ActionArea")) { chain ->
            captureRecomposeScope(chain.executable as Method, chain.args)
            actionDepth++
            actionShape = null
            try {
                chain.proceed()
            } finally {
                actionDepth--
            }
        }
        hooks += HookEnv.findClass(CLIP_KT).hookMatching(Regex("clip")) { chain ->
            if (actionDepth > 0) chain.args.getOrNull(1)?.let { actionShape = it }
            chain.proceed()
        }
        hooks += HookEnv.findClass(BACKGROUND_KT).hookMatching(Regex("background-.*")) { chain ->
            if (actionDepth <= 0 || chain.args.size != BACKGROUND_ARGS || chain.args[1] !is Long) {
                return@hookMatching chain.proceed()
            }
            chain.proceedWithArgs { args -> applyActionGlass(args) }
        }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            views.toList().forEach { apply(it) }
            recomposeScopes.toList().forEach { it.call("invalidateForResult", null) }
        }
    }

    private fun track(view: View) {
        views.add(view)
        apply(view)
    }

    private fun apply(view: View) {
        ViewGlassUnderlay.attach(view, PrefKeys.BOUNCER, { GlassConfig.bouncerActive }, { GlassConfig.bouncerTint })
    }

    private fun captureRecomposeScope(method: Method, args: List<Any?>) {
        val composerIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
        args.getOrNull(composerIndex)?.call("getRecomposeScope")?.let { recomposeScopes.add(it) }
    }

    private fun applyComposeGlass(method: Method, args: Array<Any?>) {
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (modifierIndex == -1) return
        val passed = args[modifierIndex]
        val original = passed?.let { modifierToOriginal[it] ?: it } ?: ComposeToolkit.emptyModifier ?: return
        args[modifierIndex] = passed?.let { original }
        if (!GlassConfig.bouncerActive) return

        val colorIndex = types.indexOfFirst { it == Long::class.javaPrimitiveType }
        (args.getOrNull(colorIndex) as? Long)?.let { color ->
            val argb = ComposeToolkit.toArgb(color)
            if (argb != null) {
                val alpha = (OPAQUE * GlassConfig.bouncerTint).roundToInt().coerceIn(0, OPAQUE)
                args[colorIndex] = ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, alpha))
            }
        }

        val drawer = ComposeModifiers.contentProxy("BouncerGlass.draw") { drawScope ->
            pinDrawDepth++
            try {
                drawScope.call("drawContent")
            } finally {
                pinDrawDepth--
            }
        } ?: return
        ComposeModifiers.drawWithContent(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[modifierIndex] = it
            if (passed == null) ComposeToolkit.clearDefaultBit(method, args, modifierIndex)
        }
    }

    private fun applyActionGlass(args: Array<Any?>) {
        if (!GlassConfig.bouncerActive) return
        val shape = actionShape ?: return
        val modifier = args[0] ?: return
        val argb = ComposeToolkit.toArgb(args[1] as Long) ?: return
        val alpha = (OPAQUE * GlassConfig.bouncerTint).roundToInt().coerceIn(0, OPAQUE)
        args[1] = ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, alpha))
        val drawer = ComposeModifiers.proxy("BouncerGlass.action") { drawScope -> drawActionGlass(drawScope, shape) } ?: return
        ComposeModifiers.drawBehind(modifier, drawer)?.let { args[0] = it }
    }

    private fun drawActionGlass(drawScope: Any, shape: Any) {
        if (!GlassConfig.bouncerActive) return
        val surface = GlassConfig.surface(PrefKeys.BOUNCER)
        Guard.run("BouncerGlass.action_glass", Unit) {
            ComposeLiquidGlass.draw(drawScope, shape, surface.blurRadiusPx, Color.TRANSPARENT, surface.style)
        }
    }

    private fun drawPinGlass(args: List<Any?>) {
        if (args.size < ROUND_RECT_ARGS) return
        val drawScope = args[0] ?: return
        val topLeft = args[2] as? Long ?: return
        val size = args[3] as? Long ?: return
        val corner = args[4] as? Long ?: return
        val radius = Float.fromBits((corner ushr 32).toInt())
        val colorAlpha = (args[1] as? Long)?.let { ComposeToolkit.toArgb(it) }?.let { Color.alpha(it) / OPAQUE.toFloat() } ?: 1f
        val tint = GlassConfig.bouncerTint
        val fade = if (tint > 0f) (colorAlpha / tint).coerceIn(0f, 1f) else 1f
        if (fade <= 0f) return
        val surface = GlassConfig.surface(PrefKeys.BOUNCER)
        ComposeLiquidGlass.drawSegment(
            drawScope,
            PIN_GLASS_KEY,
            Float.fromBits((topLeft ushr 32).toInt()),
            Float.fromBits((topLeft and LOW_BITS).toInt()),
            Float.fromBits((size ushr 32).toInt()),
            Float.fromBits((size and LOW_BITS).toInt()),
            FloatArray(RADII) { radius },
            surface.blurRadiusPx,
            Color.TRANSPARENT,
            surface.style,
            fade
        )
    }

    private const val OPAQUE = 255
    private const val RADII = 8
    private const val LOW_BITS = 0xFFFFFFFFL
}
