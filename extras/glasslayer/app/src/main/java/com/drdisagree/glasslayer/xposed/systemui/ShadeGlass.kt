package com.drdisagree.glasslayer.xposed.systemui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.domain.model.BatterySaverBackground
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.BackdropZoom
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

object ShadeGlass {

    private const val SCRIM_CONTROLLER = "${SystemUiModule.PACKAGE}.statusbar.phone.ScrimController"
    private const val BLUR_UTILS = "${SystemUiModule.PACKAGE}.statusbar.BlurUtils"
    private const val ANIMATED_BACKGROUND_CLASS = "com.android.compose.modifiers.AnimatedBackgroundKt"
    private const val NOTIFICATIONS_COMPOSABLE_CLASS =
        "${SystemUiModule.PACKAGE}.notifications.ui.composable.NotificationsKt"
    private const val SHADE_WINDOW_TITLE = "NotificationShade"
    private const val OPAQUE = 255
    private const val SEMI_TRANSPARENT_ALPHA = 0.85f
    private val GLASS_SCRIM_STATES = setOf("SHADE_LOCKED", "UNLOCKED")

    private val scrimAlphas = WeakHashMap<Any, Any>()
    private val zoomLocation = IntArray(2)
    private val lastScrimCalls = WeakHashMap<View, Array<Any?>>()
    private val peakScrimAlphas = WeakHashMap<Any, Float>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scrimController: WeakReference<Any>? = null
    private var scrimMethod: Method? = null

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(SCRIM_CONTROLLER).hookAll("updateScrimColor") { chain ->
            chain.proceedWithArgs { args ->
                remember(chain.thisObject, chain.executable as? Method, args)
                if (!GlassConfig.shadeBackgroundActive || usesStockScrim()) return@proceedWithArgs
                val colorIndex = args.indexOfFirst { it is Int }
                val alphaIndex = args.indexOfFirst { it is Float }
                if (colorIndex < 0 || alphaIndex < 0) return@proceedWithArgs
                val color = args[colorIndex] as Int
                val alpha = args[alphaIndex] as Float
                val scrim = args.firstOrNull { it is View } ?: return@proceedWithArgs
                val state = chain.thisObject.field("mState").toString()
                if (state !in GLASS_SCRIM_STATES) return@proceedWithArgs

                if (scrim === chain.thisObject.field("mScrimBehind") ||
                    scrim === chain.thisObject.field("mNotificationsScrim")
                ) {
                    args[colorIndex] = ColorUtils.setAlphaComponent(color, OPAQUE)
                    args[alphaIndex] = scrimAlpha(scrim, alpha)
                }
            }
        }

        hooks += HookEnv.findClass(ANIMATED_BACKGROUND_CLASS).hookAll("animatedBackground") { chain ->
            chain.proceedWithArgs { args ->
                if (args.size < 3) return@proceedWithArgs
                val alpha = args[2] ?: return@proceedWithArgs
                if (alpha in scrimAlphas.values) return@proceedWithArgs
                if (!isCalledFromNotificationPanel()) return@proceedWithArgs

                args[2] = scrimAlphas.getOrPut(alpha) {
                    ComposeToolkit.function0(
                        "ShadeGlass.scrimAlpha",
                        fallback = { ComposeToolkit.invokeFunction(alpha) ?: 1f }
                    ) {
                        val value = ComposeToolkit.invokeFunction(alpha) as? Float ?: 1f
                        if (GlassConfig.shadeBackgroundActive && !usesStockScrim()) scrimAlpha(alpha, value) else value
                    } ?: return@proceedWithArgs
                }
            }
        }

        hooks += HookEnv.findClass(BLUR_UTILS).hookAll("applyBlur") { chain ->
            chain.proceedWithArgs { args ->
                if (args.size < 2) return@proceedWithArgs
                val title = (args[0].field("mWindowAttributes") as? WindowManager.LayoutParams)?.title?.toString()
                if (title != SHADE_WINDOW_TITLE) return@proceedWithArgs
                Guard.run("ShadeGlass.zoom", Unit) { trackZoom(args[0], args.getOrNull(2) as? Float) }
                if (!GlassConfig.shadeBackgroundActive) return@proceedWithArgs
                val radius = args[1] as? Int ?: return@proceedWithArgs
                if (radius <= 0) return@proceedWithArgs
                args[1] = (radius * GlassConfig.backgroundBlur).roundToInt()
            }
        }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            val controller = scrimController?.get() ?: return@post
            val method = scrimMethod ?: return@post
            lastScrimCalls.values.toList().forEach { args ->
                Guard.run("ShadeGlass.refresh", Unit) { method.invoke(controller, *args) }
            }
        }
    }

    fun onBlurChanged() {
        peakScrimAlphas.clear()
        refresh()
    }

    private fun usesStockScrim(): Boolean =
        !WindowBlurState.enabled && GlassConfig.batterySaverBackground == BatterySaverBackground.STOCK

    private fun scrimAlpha(scrim: Any, alpha: Float): Float {
        if (WindowBlurState.enabled || GlassConfig.batterySaverBackground == BatterySaverBackground.KEEP_TINT) {
            return alpha * GlassConfig.backgroundDim
        }
        return expansionOf(scrim, alpha) * SEMI_TRANSPARENT_ALPHA
    }

    private fun expansionOf(scrim: Any, alpha: Float): Float {
        val peak = maxOf(peakScrimAlphas[scrim] ?: 0f, alpha)
        peakScrimAlphas[scrim] = peak
        return if (peak > 0f) alpha / peak else 0f
    }

    private fun remember(controller: Any?, method: Method?, args: Array<Any?>) {
        controller ?: return
        method ?: return
        val scrim = args.firstOrNull { it is View } as? View ?: return
        scrimController = WeakReference(controller)
        scrimMethod = method
        lastScrimCalls[scrim] = args.copyOf()
    }

    private fun trackZoom(viewRoot: Any?, scale: Float?) {
        val root = viewRoot.call("getView") as? View
        if (root != null && root.isAttachedToWindow) {
            root.getLocationOnScreen(zoomLocation)
            BackdropZoom.centerX = zoomLocation[0] + root.width / 2f
            BackdropZoom.centerY = zoomLocation[1] + root.height / 2f
        }
        BackdropZoom.scale = scale?.takeIf { it > 0f } ?: 1f
    }

    private fun isCalledFromNotificationPanel(): Boolean {
        val caller = Thread.currentThread().stackTrace.firstOrNull { frame ->
            (frame.className.startsWith("com.android.systemui.") ||
                    frame.className.startsWith("com.android.compose.")) &&
                    !frame.className.startsWith(ANIMATED_BACKGROUND_CLASS)
        } ?: return false

        return caller.className.startsWith(NOTIFICATIONS_COMPOSABLE_CLASS)
    }
}
