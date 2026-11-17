package com.drdisagree.glasslayer.xposed.systemui

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore
import com.drdisagree.glasslayer.xposed.hook.setField
import kotlin.math.roundToInt
import java.util.Collections
import java.util.WeakHashMap

object DialogGlass {

    private const val SYSTEM_UI_DIALOG = "${SystemUiModule.PACKAGE}.statusbar.phone.SystemUIDialog"
    private const val POWER_MENU_PACKAGE = "${SystemUiModule.PACKAGE}.globalactions."
    private const val PANEL_LAYER = 0.97f
    private const val OPAQUE = 255
    private const val BASE_SLIDER = "com.google.android.material.slider.BaseSlider"
    private const val INACTIVE_TRACK = "trackColorInactive"
    private const val INACTIVE_PAINT = "inactiveTrackPaint"
    private const val TRACK_THICKNESS = "trackThickness"
    private const val THICK_TRACK_DP = 24f

    private val views = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val watchedRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val sliders = WeakHashMap<View, DialogSliderState>()

    fun install(): Int {
        var hooks = HookEnv.findClass(SYSTEM_UI_DIALOG).hookAllAfter("onStart") { chain, _ ->
            val dialog = chain.thisObject as? Dialog ?: return@hookAllAfter
            if (dialog.javaClass.name.startsWith(POWER_MENU_PACKAGE) || VolumePanelGlass.owns(dialog)) return@hookAllAfter
            val root = dialog.window?.decorView ?: return@hookAllAfter
            Guard.run("DialogGlass.start", Unit) {
                watch(root)
                wrapTree(root, root)
            }
        }
        hooks += HookEnv.findClass(BASE_SLIDER).hookAllBefore("onDraw") { chain ->
            val view = chain.thisObject as? View ?: return@hookAllBefore
            val state = sliders[view] ?: return@hookAllBefore
            Guard.run("DialogGlass.slider", Unit) { applySlider(view, state) }
        }
        return hooks
    }

    private fun applySlider(view: View, state: DialogSliderState) {
        val current = view.field(INACTIVE_TRACK) as? ColorStateList ?: return
        if (current !== state.applied) state.stock = current
        val stock = state.stock ?: return
        val target = if (GlassConfig.dialogsActive) {
            val alpha = (Color.alpha(stock.defaultColor) * GlassConfig.dialogButtonTint).roundToInt().coerceIn(0, OPAQUE)
            state.applied?.takeIf { current === it && Color.alpha(it.defaultColor) == alpha } ?: stock.withAlpha(alpha)
        } else {
            stock
        }
        if (target !== current) view.setField(INACTIVE_TRACK, target)
        state.applied = target.takeIf { it !== stock }
        (view.field(INACTIVE_PAINT) as? Paint)?.color = target.getColorForState(view.drawableState, target.defaultColor)
    }

    private fun trackSlider(view: View) {
        if (sliders.containsKey(view) || HookEnv.findClass(BASE_SLIDER)?.isInstance(view) != true) return
        val thickness = (view.field(TRACK_THICKNESS) as? Number)?.toFloat() ?: return
        if (thickness <= THICK_TRACK_DP * view.resources.displayMetrics.density) return
        sliders[view] = DialogSliderState()
        view.invalidate()
    }

    private fun watch(root: View) {
        if (!watchedRoots.add(root)) return
        root.viewTreeObserver.addOnGlobalLayoutListener(
            ViewTreeObserver.OnGlobalLayoutListener {
                Guard.run("DialogGlass.layout", Unit) { wrapTree(root, root) }
            }
        )
    }

    private fun coversAncestor(view: View, root: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent in views) {
                val glassWidth = SolidShapes.of(parent.background)?.bounds?.width()?.takeIf { it > 0 } ?: parent.width
                return glassWidth > 0 && view.width >= glassWidth * PANEL_LAYER
            }
            if (parent === root) return false
            parent = parent.parent
        }
        return false
    }

    private fun wrapTree(view: View, root: View) {
        trackSlider(view)
        val taken = ButtonGlass.owns(view)
        if (view.width > 0 && view !in views && (taken || !ViewGlassUnderlay.isAttached(view)) && SolidShapes.of(view.background) != null) run {
            if (taken) {
                ButtonGlass.release(view)
                ViewGlassUnderlay.detach(view)
            }
            views.add(view)
            val panel = view === root
            val group = view is ViewGroup
            if (!panel && group && !AccentColors.isAccent(view) && coversAncestor(view, root)) {
                ViewGlassUnderlay.attach(view, PrefKeys.DIALOGS, { GlassConfig.dialogsActive }, { 0f }, glass = false)
                return@run
            }
            ViewGlassUnderlay.attach(
                view,
                PrefKeys.DIALOGS,
                { GlassConfig.dialogsActive },
                {
                    when {
                        panel -> GlassConfig.dialogTint
                        AccentColors.isAccent(view) -> if (group) GlassConfig.dialogActiveTint else GlassConfig.dialogAccentTint
                        group -> GlassConfig.dialogTint
                        else -> GlassConfig.dialogButtonTint
                    }
                },
                rimOnTop = !panel
            )
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) wrapTree(view.getChildAt(index), root)
        }
    }
}
