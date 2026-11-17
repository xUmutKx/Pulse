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
import com.drdisagree.glasslayer.xposed.hook.hookMatchingAfter
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import kotlin.math.roundToInt

object QsFooterGlass {

    private const val FOOTER_ACTIONS = "${SystemUiModule.PACKAGE}.qs.footer.ui.compose.FooterActionsKt"
    private const val FOOTER_DEFAULTS = "${SystemUiModule.PACKAGE}.qs.footer.ui.compose.FooterActionsDefaults"
    private const val FOOTER_VIEW_MODEL_PREFIX =
        "${SystemUiModule.PACKAGE}.qs.footer.ui.viewmodel.FooterActionsButtonViewModel"
    private const val ACTIVE_FOOTER_BUTTON = "PowerActionViewModel"
    private const val EXPANDABLE_CONTROLLER_KT = "com.android.compose.animation.ExpandableControllerKt"

    private val footerButtonTypes = ArrayDeque<String>()
    private val footerStates = ArrayDeque<ExpandableState>()
    private val footerModifierToOriginal = WeakHashMap<Any, Any>()
    private var chipDepth = 0
    private val footerButtonHeightDp: Float? by lazy { readFooterButtonHeight() }

    fun install(): Int {
        var hooks = 0
        val footerActions = HookEnv.findClass(FOOTER_ACTIONS) ?: return 0

        hooks += footerActions.hookAll("IconButton") { chain ->
            val model = chain.args.firstOrNull {
                it?.javaClass?.name?.startsWith(FOOTER_VIEW_MODEL_PREFIX) == true
            } ?: return@hookAll chain.proceed()
            footerButtonTypes.addLast(model.javaClass.simpleName)
            footerStates.addLast(ExpandableState())
            try {
                chain.proceed()
            } finally {
                footerButtonTypes.removeLastOrNull()
                footerStates.removeLastOrNull()
            }
        }

        hooks += HookEnv.findClass(EXPANDABLE_CONTROLLER_KT).hookMatchingAfter(Regex("rememberExpandableController.*")) { _, result ->
            footerStates.lastOrNull()?.let { it.controller = result }
        }

        hooks += footerActions.hookMatching(Regex("AnimatedFooterTextButton|NumberButton")) { chain ->
            chipDepth++
            try {
                chain.proceed()
            } finally {
                chipDepth--
            }
        }

        hooks += footerActions.hookMatching(Regex("CircleExpandable.*")) { chain ->
            val isChip = chipDepth > 0 && footerButtonTypes.isEmpty()
            if (!isChip && footerButtonTypes.isEmpty()) return@hookMatching chain.proceed()
            chain.proceedWithArgs { args ->
                if (!GlassConfig.footerActive) return@proceedWithArgs
                val method = chain.executable as Method
                if (isChip) applyFooterChipGlass(method, args) else applyFooterButtonGlass(method, args)
            }
        }
        return hooks
    }

    private fun applyFooterButtonGlass(method: Method, args: Array<Any?>) {
        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (modifierIndex == -1 || types.firstOrNull() != Long::class.javaPrimitiveType) return

        val modifier = args[modifierIndex] ?: return
        if (footerModifierToOriginal.containsKey(modifier)) return
        val buttonType = footerButtonTypes.lastOrNull() ?: return
        val color = args[0] as? Long ?: return
        val argb = ComposeToolkit.toArgb(color) ?: return
        val factor = if (buttonType.contains(ACTIVE_FOOTER_BUTTON)) GlassConfig.activeFooterTint else GlassConfig.footerTint
        args[0] = ComposeToolkit.colorOf(ColorUtils.setAlphaComponent(argb, (255 * factor).roundToInt()))

        val state = footerStates.lastOrNull() ?: ExpandableState()
        val drawer = ComposeModifiers.proxy("QsFooterGlass.draw") { drawScope -> drawFooterGlass(drawScope, state) } ?: return
        ComposeModifiers.drawBehind(modifier, drawer)?.let {
            footerModifierToOriginal[it] = modifier
            args[modifierIndex] = it
        }
    }

    private fun applyFooterChipGlass(method: Method, args: Array<Any?>) {
        if (!GlassConfig.footerActive) return

        val types = method.parameterTypes
        val modifierIndex = types.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (modifierIndex == -1 || types.firstOrNull() != Long::class.javaPrimitiveType) return
        if (footerModifierToOriginal.containsKey(args[modifierIndex])) return

        val color = args[0] as? Long ?: return
        val argb = ComposeToolkit.toArgb(color) ?: return
        args[0] = ComposeToolkit.colorOf(
            ColorUtils.setAlphaComponent(argb, (255 * GlassConfig.footerTint).roundToInt())
        )

        val modifier = args[modifierIndex] ?: return
        val drawer = ComposeModifiers.proxy("QsFooterGlass.chip") { drawScope -> drawFooterChipGlass(drawScope) } ?: return
        ComposeModifiers.drawBehind(modifier, drawer)?.let {
            footerModifierToOriginal[it] = modifier
            args[modifierIndex] = it
        }
    }

    private fun drawFooterChipGlass(drawScope: Any) {
        if (!GlassConfig.footerActive) return
        Guard.run("QsFooterGlass.footer_chip_glass", Unit) {
            val packedSize = ComposeToolkit.drawSize(drawScope) ?: return
            val width = Float.fromBits((packedSize ushr 32).toInt())
            val height = Float.fromBits((packedSize and 0xFFFFFFFFL).toInt())
            val density = Resources.getSystem().displayMetrics.density
            val chipHeight = (footerButtonHeightDp?.let { it * density } ?: height).coerceAtMost(height)
            val radius = chipHeight / 2f

            ComposeLiquidGlass.drawSegment(
                drawScope,
                0,
                0f,
                (height - chipHeight) / 2f,
                width,
                chipHeight,
                FloatArray(8) { radius },
                GlassConfig.surface(PrefKeys.FOOTER).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.FOOTER).style
            )
        }
    }

    private fun drawFooterGlass(drawScope: Any, state: ExpandableState) {
        if (!GlassConfig.footerActive || ExpandableTransitions.isTransitioning(state.controller)) return
        Guard.run("QsFooterGlass.footer_glass", Unit) {
            val packedSize = ComposeToolkit.drawSize(drawScope) ?: return
            val width = Float.fromBits((packedSize ushr 32).toInt())
            val height = Float.fromBits((packedSize and 0xFFFFFFFFL).toInt())
            val density = Resources.getSystem().displayMetrics.density
            val diameter = (footerButtonHeightDp?.let { it * density } ?: minOf(width, height))
                .coerceAtMost(minOf(width, height))
            val radius = diameter / 2f

            ComposeLiquidGlass.drawSegment(
                drawScope,
                0,
                (width - diameter) / 2f,
                (height - diameter) / 2f,
                diameter,
                diameter,
                FloatArray(8) { radius },
                GlassConfig.surface(PrefKeys.FOOTER).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.FOOTER).style
            )
        }
    }

    private fun readFooterButtonHeight(): Float? = HookEnv.findClass(
        FOOTER_DEFAULTS)?.let { defaults ->
        try {
            defaults.getDeclaredField("FooterButtonHeight")
                .apply { isAccessible = true }
                .let { field ->
                    val owner = if (Modifier.isStatic(field.modifiers)) {
                        null
                    } else {
                        defaults.getField("INSTANCE").get(null)
                    }
                    field.getFloat(owner)
                }
        } catch (_: Throwable) {
            null
        }
    }
}
