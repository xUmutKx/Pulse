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

object LockscreenShortcutGlass {

    private const val BINDER = "${SystemUiModule.PACKAGE}.keyguard.ui.binder.KeyguardQuickAffordanceViewBinder"
    private const val SHORTCUT_PROVIDER = "${SystemUiModule.PACKAGE}.keyguard.ui.composable.elements.ShortcutElementProvider"
    private const val EXPANDABLE_CONTROLLER_KT = "com.android.compose.animation.ExpandableControllerKt"
    private const val EXPANDABLE_KT = "com.android.compose.animation.ExpandableKt"
    private const val BACKGROUND_KT = "androidx.compose.foundation.BackgroundKt"

    private val shortcutViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val shortcutControllers = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val modifierToOriginal = WeakHashMap<Any, Any>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var shortcutDepth = 0
    private var shortcutShape: Any? = null

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(BINDER).hookAllAfter("bind") { chain, _ ->
            val view = chain.args.firstOrNull() as? View ?: return@hookAllAfter
            shortcutViews.add(view)
            applyShortcutGlass(view)
        }

        hooks += HookEnv.findClass(SHORTCUT_PROVIDER).hookMatching(Regex("KeyguardShortcut")) { chain ->
            captureRecomposeScope(chain.executable as Method, chain.args)
            shortcutDepth++
            try {
                chain.proceed()
            } finally {
                shortcutDepth--
            }
        }

        hooks += HookEnv.findClass(EXPANDABLE_CONTROLLER_KT).hookMatching(Regex("rememberExpandableController.*")) { chain ->
            if (shortcutDepth <= 0) return@hookMatching chain.proceed()
            val result = chain.proceedWithArgs { args -> applyShortcutColor(chain.executable as Method, args) }
            result?.let { shortcutControllers.add(it) }
            result
        }

        hooks += HookEnv.findClass(EXPANDABLE_KT).hookMatching(Regex("Expandable(-.*)?")) { chain ->
            val isShortcut = shortcutDepth > 0 || chain.args.any { it != null && it in shortcutControllers }
            if (!isShortcut) return@hookMatching chain.proceed()
            chain.proceedWithArgs { args ->
                val method = chain.executable as Method
                applyShortcutColor(method, args)
                applyComposeGlass(method, args)
            }
        }

        hooks += HookEnv.findClass(BACKGROUND_KT).hookMatching(Regex("background-.*")) { chain ->
            val args = chain.args
            val circle = ComposeToolkit.circleShape
            if (!GlassConfig.lockscreenShortcutsEnabled || circle == null || args.size < 3 ||
                args[2] !== circle || args[1] !is Long
            ) {
                return@hookMatching chain.proceed()
            }
            if (shortcutDepth <= 0 && !calledFromShortcut()) return@hookMatching chain.proceed()
            chain.proceedWithArgs { values -> values[1] = tinted(values[1] as Long) }
        }

        DozeTracker.addListener(::refresh)
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            shortcutViews.toList().forEach { applyShortcutGlass(it) }
            recomposeScopes.toList().forEach { it.call("invalidateForResult", null) }
        }
    }

    private fun captureRecomposeScope(method: Method, args: List<Any?>) {
        val composerIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
        args.getOrNull(composerIndex)?.call("getRecomposeScope")?.let { recomposeScopes.add(it) }
    }

    private fun calledFromShortcut(): Boolean =
        Thread.currentThread().stackTrace.any { it.className.startsWith(SHORTCUT_PROVIDER) }

    private fun tinted(color: Long): Long {
        val argb = ComposeToolkit.toArgb(color) ?: return color
        val alpha = (255 * GlassConfig.lockscreenShortcutTint).roundToInt().coerceIn(0, 255)
        return ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, alpha))
    }

    private fun applyShortcutColor(method: Method, args: Array<Any?>) {
        val types = method.parameterTypes
        val shapeIndex = types.indexOfFirst { it.name == ComposeToolkit.SHAPE_CLASS }
        if (shapeIndex != -1) args[shapeIndex]?.let { shortcutShape = it }
        if (types.firstOrNull() != Long::class.javaPrimitiveType) return
        if (!GlassConfig.lockscreenShortcutsEnabled) return
        val color = args[0] as? Long ?: return
        args[0] = tinted(color)
    }

    private fun applyComposeGlass(method: Method, args: Array<Any?>) {
        val modifierIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (modifierIndex == -1) return
        val passed = args[modifierIndex]
        val original = passed?.let { modifierToOriginal[it] ?: it } ?: ComposeToolkit.emptyModifier ?: return
        args[modifierIndex] = passed?.let { original }
        if (!GlassConfig.lockscreenShortcutsEnabled) return
        val shape = shortcutShape ?: return

        val drawer = ComposeModifiers.proxy("LockscreenShortcutGlass.draw") { drawScope -> drawComposeGlass(drawScope, shape) }
            ?: return
        ComposeModifiers.drawBehind(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[modifierIndex] = it
            if (passed == null) ComposeToolkit.clearDefaultBit(method, args, modifierIndex)
        }
    }

    private fun drawComposeGlass(drawScope: Any, shape: Any) {
        if (!GlassConfig.lockscreenShortcutsActive) return
        val surface = GlassConfig.surface(PrefKeys.LOCKSCREEN_SHORTCUTS)
        Guard.run("LockscreenShortcutGlass.compose_glass", Unit) {
            ComposeLiquidGlass.draw(drawScope, shape, surface.blurRadiusPx, Color.TRANSPARENT, surface.style)
        }
    }

    private fun applyShortcutGlass(view: View) {
        ViewGlassUnderlay.attach(
            view,
            PrefKeys.LOCKSCREEN_SHORTCUTS,
            { GlassConfig.lockscreenShortcutsActive },
            { GlassConfig.lockscreenShortcutTint }
        )
    }
}
