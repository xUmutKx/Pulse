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
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object StatusBarChipGlass {

    private const val CHIP_KT = "${SystemUiModule.PACKAGE}.statusbar.chips.ui.compose.OngoingActivityChipKt"
    private const val EXPANDABLE_KT = "com.android.compose.animation.ExpandableKt"
    private const val ANIMATED_BACKGROUND_KT = "com.android.compose.modifiers.AnimatedBackgroundKt"
    private const val PRIVACY_CHIP = "${SystemUiModule.PACKAGE}.privacy.OngoingPrivacyChip"
    private const val STATUS_BAR_EVENTS = "${SystemUiModule.PACKAGE}.statusbar.events."
    private const val ICONS_CONTAINER = "iconsContainer"
    private const val OPAQUE = 255
    private const val MAX_SHAPES = 8
    private const val OVERLAY_KEY = 1

    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val privacyChips = WeakHashMap<View, Boolean>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val chipShapes = ArrayDeque<WeakReference<Any>>()
    private val wrapped = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    private var chipDepth = 0
    private var expandableDepth = 0

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(CHIP_KT).hookMatching(Regex("OngoingActivityChip")) { chain ->
            captureRecomposeScope(chain.executable as Method, chain.args)
            chipDepth++
            try {
                chain.proceed()
            } finally {
                chipDepth--
            }
        }
        hooks += HookEnv.findClass(EXPANDABLE_KT).hookMatching(Regex("Expandable(-.*)?")) { chain ->
            val method = chain.executable as Method
            if (chipDepth <= 0 || method.parameterTypes.firstOrNull() != Long::class.javaPrimitiveType) {
                return@hookMatching chain.proceed()
            }
            rememberShape(method, chain.args)
            expandableDepth++
            try {
                chain.proceedWithArgs { args -> tintChip(args) }
            } finally {
                expandableDepth--
            }
        }
        hooks += HookEnv.findClass(ANIMATED_BACKGROUND_KT).hookMatching(Regex("animatedBackground(\\\$default)?")) { chain ->
            val method = chain.executable as Method
            if (expandableDepth <= 0 && !isChipShape(method, chain.args)) return@hookMatching chain.proceed()
            chain.proceedWithArgs { args -> wrapBackground(method, args) }
        }
        hooks += HookEnv.findClass(PRIVACY_CHIP).hookAllAfter("updateResources") { chain, _ ->
            val chip = chain.thisObject as? View ?: return@hookAllAfter
            Guard.run("StatusBarChipGlass.privacy", Unit) { trackPrivacy(chip) }
        }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            recomposeScopes.toList().forEach { it.call("invalidateForResult", null) }
        }
    }

    private fun captureRecomposeScope(method: Method, args: List<Any?>) {
        val composerIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
        args.getOrNull(composerIndex)?.call("getRecomposeScope")?.let { recomposeScopes.add(it) }
    }

    private fun rememberShape(method: Method, args: List<Any?>) {
        val shape = args.getOrNull(method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.SHAPE_CLASS }) ?: return
        chipShapes.removeAll { it.get().let { known -> known == null || known === shape } }
        chipShapes.addLast(WeakReference(shape))
        while (chipShapes.size > MAX_SHAPES) chipShapes.removeFirst()
    }

    private fun isChipShape(method: Method, args: List<Any?>): Boolean {
        val shape = args.getOrNull(method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.SHAPE_CLASS }) ?: return false
        return chipShapes.any { it.get() === shape }
    }

    private fun tintChip(args: Array<Any?>) {
        if (!GlassConfig.statusBarChipsActive) return
        val color = args[0] as? Long ?: return
        val argb = ComposeToolkit.toArgb(color) ?: return
        val alpha = (OPAQUE * GlassConfig.statusBarChipTint).roundToInt().coerceIn(0, OPAQUE)
        args[0] = ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, alpha))
    }

    private fun wrapBackground(method: Method, args: Array<Any?>) {
        if (!GlassConfig.statusBarChipsActive) return
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        val shapeIndex = types.indexOfFirst { it.name == ComposeToolkit.SHAPE_CLASS }
        if (modifierIndex == -1 || shapeIndex == -1) return
        val receiver = args[modifierIndex] ?: return
        if (receiver in wrapped) return
        val shape = args[shapeIndex] ?: return
        val drawer = ComposeModifiers.contentProxy("StatusBarChipGlass.draw") { drawScope -> drawChip(drawScope, shape) } ?: return
        ComposeModifiers.drawWithContent(receiver, drawer)?.let {
            wrapped.add(it)
            args[modifierIndex] = it
        }
    }

    private fun drawChip(drawScope: Any, shape: Any) {
        if (!GlassConfig.statusBarChipsActive) {
            drawScope.call("drawContent")
            return
        }
        val surface = GlassConfig.surface(PrefKeys.STATUS_BAR_CHIPS)
        Guard.run("StatusBarChipGlass.chip_glass", Unit) {
            ComposeLiquidGlass.draw(drawScope, shape, surface.blurRadiusPx, Color.TRANSPARENT, surface.style)
        }
        drawScope.call("drawContent")
        Guard.run("StatusBarChipGlass.chip_rim", Unit) {
            ComposeLiquidGlass.draw(drawScope, shape, surface.blurRadiusPx, Color.TRANSPARENT, surface.style.copy(overlay = true), key = OVERLAY_KEY)
        }
    }

    private fun trackPrivacy(chip: View) {
        if (!privacyChips.containsKey(chip)) {
            if (!calledFromStatusBarEvent()) return
            privacyChips[chip] = true
        }
        val container = chip.field(ICONS_CONTAINER) as? View ?: return
        ViewGlassUnderlay.attach(
            container,
            PrefKeys.STATUS_BAR_CHIPS,
            { GlassConfig.statusBarChipsActive },
            { GlassConfig.statusBarChipTint }
        )
    }

    private fun calledFromStatusBarEvent(): Boolean =
        Thread.currentThread().stackTrace.any { it.className.startsWith(STATUS_BAR_EVENTS) }
}
