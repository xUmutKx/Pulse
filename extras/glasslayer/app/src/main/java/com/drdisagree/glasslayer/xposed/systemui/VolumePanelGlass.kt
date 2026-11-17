package com.drdisagree.glasslayer.xposed.systemui

import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.hookMatchingBefore
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object VolumePanelGlass {

    private const val DIALOG_FACTORY_EXT = "${SystemUiModule.PACKAGE}.statusbar.phone.SystemUIDialogFactoryExtKt"
    private const val VOLUME_NAVIGATOR = "${SystemUiModule.PACKAGE}.volume.ui.navigation.VolumeNavigator"
    private const val PANEL_COMPONENT = "${SystemUiModule.PACKAGE}.volume.panel.component"
    private const val PANEL_BUTTON_KT = "$PANEL_COMPONENT.button.ui.composable.VolumePanelButtonKt"
    private const val MEDIA_OUTPUT = "$PANEL_COMPONENT.mediaoutput.ui.composable.MediaOutputComponent"
    private const val MEDIA_INPUT = "$PANEL_COMPONENT.mediainput.ui.composable.MediaInputComponent"
    private const val VOLUME_PANEL_PACKAGE = "${SystemUiModule.PACKAGE}.volume.panel."
    private const val SLIDER_TRACK_KT = "${SystemUiModule.PACKAGE}.volume.dialog.sliders.ui.compose.VolumeDialogSliderTrackKt"
    private const val SURFACE_KT = "androidx.compose.material3.SurfaceKt"
    private const val EXPANDABLE_KT = "com.android.compose.animation.ExpandableKt"
    private const val SLIDER_DEFAULTS = "androidx.compose.material3.SliderDefaults"
    private const val ROUND_RECT = "roundRect"
    private const val TOP_LEFT = "topLeftCornerRadius"
    private const val TOP_RIGHT = "topRightCornerRadius"
    private const val BOTTOM_RIGHT = "bottomRightCornerRadius"
    private const val BOTTOM_LEFT = "bottomLeftCornerRadius"
    private const val SHEET_KEY = 0
    private const val COLOR_INDEX = 2
    private const val OPAQUE = 255
    private const val LOW_BITS = 0xFFFFFFFFL

    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val panelScopes = WeakHashMap<Any, Boolean>()
    private val modifierToOriginal = WeakHashMap<Any, Any>()
    private val activeButtons = ArrayDeque<Boolean>()
    private var mediaDepth = 0
    private val track = TrackGlass("VolumePanelGlass", PrefKeys.VOLUME_PANEL) { sheetDim() }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var panelDialog: WeakReference<Dialog>? = null
    private var sheetPending = false
    private var sheetDepth = 0
    private var trackDepth = 0

    @Volatile
    private var sheetColor: Int? = null

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(DIALOG_FACTORY_EXT).hookMatching(Regex("createBottomSheet.*")) { chain ->
            val result = chain.proceed()
            if (result is Dialog && calledFrom(VOLUME_NAVIGATOR)) {
                panelDialog = WeakReference(result)
                sheetPending = true
            }
            result
        }
        hooks += HookEnv.findClass(SURFACE_KT).hookMatching(Regex("Surface-.*")) { chain ->
            val method = chain.executable as Method
            if (sheetDepth > 0 || !isSheetSurface(method, chain.args)) return@hookMatching chain.proceed()
            captureRecomposeScope(method, chain.args)
            sheetDepth++
            try {
                chain.proceedWithArgs { args -> applySheetGlass(method, args) }
            } finally {
                sheetDepth--
            }
        }
        hooks += HookEnv.findClass(PANEL_BUTTON_KT).hookMatching(Regex("VolumePanelButton")) { chain ->
            val method = chain.executable as Method
            val activeIndex = method.parameterTypes.indexOfFirst { it == Boolean::class.javaPrimitiveType }
            captureRecomposeScope(method, chain.args)
            activeButtons.addLast(chain.args.getOrNull(activeIndex) as? Boolean ?: false)
            try {
                chain.proceed()
            } finally {
                activeButtons.removeLastOrNull()
            }
        }
        listOf(MEDIA_OUTPUT, MEDIA_INPUT).forEach { name ->
            hooks += HookEnv.findClass(name).hookMatching(Regex("Content")) { chain ->
                captureRecomposeScope(chain.executable as Method, chain.args)
                mediaDepth++
                try {
                    chain.proceed()
                } finally {
                    mediaDepth--
                }
            }
        }
        hooks += HookEnv.findClass(EXPANDABLE_KT).hookMatching(Regex("Expandable(-.*)?")) { chain ->
            val method = chain.executable as Method
            if (method.parameterTypes.firstOrNull() != Long::class.javaPrimitiveType) return@hookMatching chain.proceed()
            if (mediaDepth > 0 && activeButtons.isEmpty()) {
                if (!GlassConfig.volumePanelActive) return@hookMatching chain.proceed()
                return@hookMatching chain.proceedWithArgs { args -> args[0] = ComposeToolkit.colorOf(Color.TRANSPARENT) }
            }
            if (activeButtons.isEmpty()) return@hookMatching chain.proceed()
            val active = activeButtons.lastOrNull() == true
            chain.proceedWithArgs { args -> applyButtonGlass(method, args, active) }
        }
        hooks += HookEnv.findClass(SLIDER_TRACK_KT).hookMatching(Regex("SliderTrack(-.*)?")) { chain ->
            if (!panelShowing()) return@hookMatching chain.proceed()
            val method = chain.executable as Method
            val scope = recomposeScope(method, chain.args)
            val inPanel = scope?.let { panelScopes.getOrPut(it) { calledFrom(VOLUME_PANEL_PACKAGE) } }
                ?: calledFrom(VOLUME_PANEL_PACKAGE)
            if (!inPanel) return@hookMatching chain.proceed()
            scope?.let { recomposeScopes.add(it) }
            trackDepth++
            try {
                chain.proceedWithArgs { args ->
                    if (GlassConfig.volumePanelActive) {
                        SliderColorsTint.apply(
                            method,
                            args,
                            GlassConfig.volumePanelSliderTint,
                            GlassConfig.activeVolumePanelSliderTint
                        )
                    }
                }
            } finally {
                trackDepth--
            }
        }
        hooks += HookEnv.findClass(SLIDER_DEFAULTS).hookMatching(Regex("Track(-.*)?")) { chain ->
            val inside = trackDepth > 0 || (panelShowing() && calledFrom(VOLUME_PANEL_PACKAGE))
            if (!GlassConfig.volumePanelActive || !inside) return@hookMatching chain.proceed()
            chain.proceedWithArgs { args -> track.wrap(chain.executable as Method, args) }
        }
        hooks += HookEnv.findClass(SLIDER_DEFAULTS).hookMatchingBefore(Regex("drawTrackPath.*")) { chain ->
            if (GlassConfig.volumePanelActive) {
                Guard.run("VolumePanelGlass.segment_glass", Unit) { track.draw(chain.args) }
            }
        }
        return hooks
    }

    fun owns(dialog: Dialog): Boolean = panelDialog?.get() === dialog

    fun refresh() {
        mainHandler.post {
            recomposeScopes.toList().forEach { it.call("invalidateForResult", null) }
        }
    }

    private fun panelShowing(): Boolean = panelDialog?.get()?.let { sheetPending || it.isShowing } == true

    private fun isSheetSurface(method: Method, args: List<Any?>): Boolean {
        val types = method.parameterTypes
        if (types.size <= COLOR_INDEX || types[0].name != ComposeToolkit.MODIFIER_CLASS ||
            types[1].name != ComposeToolkit.SHAPE_CLASS || types[COLOR_INDEX] != Long::class.javaPrimitiveType
        ) {
            return false
        }
        val passed = args[0]
        if (passed != null && modifierToOriginal.containsKey(passed)) return true
        if (!panelShowing() || !calledFrom(DIALOG_FACTORY_EXT)) return false
        sheetPending = false
        return true
    }

    private fun applySheetGlass(method: Method, args: Array<Any?>) {
        val passed = args[0]
        val original = passed?.let { modifierToOriginal[it] ?: it } ?: ComposeToolkit.emptyModifier ?: return
        args[0] = passed?.let { original }
        if (!GlassConfig.volumePanelActive) return
        val shape = args[1] ?: return
        val argb = (args[COLOR_INDEX] as? Long)?.let { ComposeToolkit.toArgb(it) } ?: return
        sheetColor = argb
        args[COLOR_INDEX] = tinted(argb, GlassConfig.volumePanelTint)

        val drawer = ComposeModifiers.proxy("VolumePanelGlass.sheet") { drawScope -> drawSheet(drawScope, shape) } ?: return
        ComposeModifiers.drawBehind(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[0] = it
            if (passed == null) ComposeToolkit.clearDefaultBit(method, args, 0)
        }
    }

    private fun applyButtonGlass(method: Method, args: Array<Any?>, active: Boolean) {
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        val shapeIndex = types.indexOfFirst { it.name == ComposeToolkit.SHAPE_CLASS }
        if (modifierIndex == -1 || shapeIndex == -1) return
        val passed = args[modifierIndex]
        val original = passed?.let { modifierToOriginal[it] ?: it } ?: ComposeToolkit.emptyModifier ?: return
        args[modifierIndex] = passed?.let { original }
        if (!GlassConfig.volumePanelActive) return
        val shape = args[shapeIndex] ?: return
        val argb = (args[0] as? Long)?.let { ComposeToolkit.toArgb(it) } ?: return
        args[0] = tinted(argb, if (active) GlassConfig.activeVolumePanelButtonTint else GlassConfig.volumePanelButtonTint)

        val drawer = ComposeModifiers.proxy("VolumePanelGlass.button") { drawScope -> drawButton(drawScope, shape) } ?: return
        ComposeModifiers.drawBehind(original, drawer)?.let {
            modifierToOriginal[it] = original
            args[modifierIndex] = it
            if (passed == null) ComposeToolkit.clearDefaultBit(method, args, modifierIndex)
        }
    }

    private fun drawSheet(drawScope: Any, shape: Any) {
        if (!GlassConfig.volumePanelActive) return
        val surface = GlassConfig.surface(PrefKeys.VOLUME_PANEL)
        Guard.run("VolumePanelGlass.sheet_glass", Unit) {
            val packed = ComposeToolkit.drawSize(drawScope) ?: return
            val roundRect = ComposeToolkit.outline(shape, drawScope)?.field(ROUND_RECT)
            if (roundRect == null) {
                ComposeLiquidGlass.draw(drawScope, shape, surface.blurRadiusPx, Color.TRANSPARENT, surface.style)
                return
            }
            val topLeft = roundRect.radius(TOP_LEFT)
            val topRight = roundRect.radius(TOP_RIGHT)
            val bottomRight = roundRect.radius(BOTTOM_RIGHT)
            val bottomLeft = roundRect.radius(BOTTOM_LEFT)
            ComposeLiquidGlass.drawSegment(
                drawScope,
                SHEET_KEY,
                0f,
                0f,
                Float.fromBits((packed ushr 32).toInt()),
                Float.fromBits((packed and LOW_BITS).toInt()),
                floatArrayOf(topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft),
                surface.blurRadiusPx,
                Color.TRANSPARENT,
                surface.style
            )
        }
    }

    private fun drawButton(drawScope: Any, shape: Any) {
        if (!GlassConfig.volumePanelActive) return
        val surface = GlassConfig.surface(PrefKeys.VOLUME_PANEL)
        Guard.run("VolumePanelGlass.button_glass", Unit) {
            ComposeLiquidGlass.draw(drawScope, shape, surface.blurRadiusPx, sheetDim(), surface.style)
        }
    }

    private fun Any.radius(name: String): Float =
        (field(name) as? Long)?.let { Float.fromBits((it ushr 32).toInt()) } ?: 0f

    private fun tinted(argb: Int, tint: Float): Long =
        ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, (OPAQUE * tint).roundToInt().coerceIn(0, OPAQUE)))

    private fun sheetDim(): Int {
        val color = sheetColor ?: return Color.TRANSPARENT
        return ColorUtils.setAlphaComponent(color, (OPAQUE * GlassConfig.volumePanelTint).roundToInt().coerceIn(0, OPAQUE))
    }

    private fun captureRecomposeScope(method: Method, args: List<Any?>) {
        recomposeScope(method, args)?.let { recomposeScopes.add(it) }
    }

    private fun recomposeScope(method: Method, args: List<Any?>): Any? {
        val composerIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
        return args.getOrNull(composerIndex)?.call("getRecomposeScope")
    }

    private fun calledFrom(prefix: String): Boolean =
        Thread.currentThread().stackTrace.any { it.className.startsWith(prefix) }
}
