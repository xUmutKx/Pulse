package com.drdisagree.glasslayer.xposed.systemui

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
import com.drdisagree.glasslayer.xposed.hook.anyField
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookMatchingAfter
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import com.drdisagree.glasslayer.xposed.hook.setField
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object QsTileGlass {

    private const val TILE_DEFAULTS = "${SystemUiModule.PACKAGE}.qs.panels.ui.compose.infinitegrid.TileDefaults"
    private const val TILE_KT = "${SystemUiModule.PACKAGE}.qs.panels.ui.compose.infinitegrid.TileKt"
    private const val STATE_ACTIVE = 2
    private const val RGB_MASK = 0x00FFFFFF
    private const val EXPANDABLE_CONTROLLER_KT = "com.android.compose.animation.ExpandableControllerKt"

    private val tileModifierToOriginal = WeakHashMap<Any, Any>()
    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val composing = ArrayDeque<ExpandableState>()

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(TILE_KT).hookAll("TileExpandable") { chain ->
            val state = ExpandableState()
            composing.addLast(state)
            try {
                chain.proceedWithArgs { args -> applyTileGlass(chain.executable as Method, args, state) }
            } finally {
                composing.removeLastOrNull()
            }
        }
        hooks += HookEnv.findClass(EXPANDABLE_CONTROLLER_KT).hookMatchingAfter(Regex("rememberExpandableController.*")) { _, result ->
            composing.lastOrNull()?.let { it.controller = result }
        }

        hooks += HookEnv.findClass(TILE_DEFAULTS).hookAllAfter("getColorForState") { chain, result ->
            captureRecomposeScope(chain.executable as Method, chain.args)
            if (GlassConfig.tilesActive) applyGlassTileAlpha(chain.args.firstOrNull(), result)
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

    private fun applyTileGlass(method: Method, args: Array<Any?>, state: ExpandableState) {
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        val shapeIndex = types.indexOfFirst { it.name == ComposeToolkit.SHAPE_CLASS }
        if (modifierIndex == -1 || shapeIndex == -1) return

        val modifier = args[modifierIndex] ?: return
        val original = tileModifierToOriginal[modifier] ?: modifier
        args[modifierIndex] = original
        if (!GlassConfig.tilesActive) return

        val shape = args[shapeIndex] ?: return
        val drawer = ComposeModifiers.proxy("QsTileGlass.draw") { drawScope -> drawTileGlass(drawScope, shape, state) } ?: return
        ComposeModifiers.drawBehind(original, drawer)?.let {
            tileModifierToOriginal[it] = original
            args[modifierIndex] = it
        }
    }

    private fun drawTileGlass(drawScope: Any, shape: Any, state: ExpandableState) {
        if (!GlassConfig.tilesActive || ExpandableTransitions.isTransitioning(state.controller)) return
        Guard.run("QsTileGlass.tile_glass", Unit) {
            ComposeLiquidGlass.draw(
                drawScope,
                shape,
                GlassConfig.surface(PrefKeys.TILES).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.TILES).style
            )
        }
    }

    private fun applyGlassTileAlpha(tileUiState: Any?, colors: Any?) {
        val state = tileUiState.anyField("visualState", "state") as? Int
        if (state == STATE_ACTIVE) {
            val dualTarget = colors.rgb("background").let { it != null && it != colors.rgb("iconBackground") }
            colors.scaleColorAlpha("background", if (dualTarget) GlassConfig.tileTint else GlassConfig.activeTileTint)
            colors.scaleColorAlpha("iconBackground", GlassConfig.activeTileTint)
        } else {
            colors.scaleColorAlpha("background", GlassConfig.tileTint)
        }
    }

    private fun Any?.rgb(field: String): Int? {
        val color = field(field) as? Long ?: return null
        return ComposeToolkit.toArgb(color)?.and(RGB_MASK)
    }

    private fun Any?.scaleColorAlpha(field: String, factor: Float) {
        val color = field(field) as? Long ?: return
        val argb = ComposeToolkit.toArgb(color) ?: return
        val alpha = (255 * factor).roundToInt().coerceIn(0, 255)
        setField(field, ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, alpha)))
    }
}
