package com.drdisagree.glasslayer.xposed.systemui

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.glass.OutlineRadius
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.roundToInt

object InstallerDialogGlass {

    private const val DIALOG = "android.app.Dialog"
    private const val MATERIAL_SHAPE = "com.google.android.material.shape.MaterialShapeDrawable"
    private const val FULL_PANEL = 0.9f
    private const val SOLID_ALPHA = 200
    private const val OPAQUE_ALPHA = -0x1000000
    private val INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")

    private val blurs = WeakHashMap<View, Drawable>()
    private val windows = WeakHashMap<View, WeakReference<Window>>()
    private val dimmed = WeakHashMap<View, Boolean>()
    private val fillColors = WeakHashMap<View, Int>()
    private val tinted = WeakHashMap<View, Drawable>()
    private val appliedColors = WeakHashMap<View, Int>()
    private val stockColors = WeakHashMap<View, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val panel = Rect()

    fun install(): Int = HookEnv.findClass(DIALOG).hookAllAfter("onStart") { chain, _ ->
        val dialog = chain.thisObject as? Dialog ?: return@hookAllAfter
        if (dialog.context.packageName !in INSTALLERS) return@hookAllAfter
        val window = dialog.window ?: return@hookAllAfter
        val decor = window.decorView
        decor.post { Guard.run("InstallerDialogGlass.attach", Unit) { attach(window, decor) } }
    }

    fun refresh() {
        mainHandler.post {
            windows.keys.toList().forEach { decor ->
                Guard.run("InstallerDialogGlass.refresh", Unit) {
                    update(decor)
                    decor.invalidate()
                }
            }
        }
    }

    private fun attach(window: Window, decor: View) {
        val background = decor.background
        if (SolidShapes.of(background) == null && !isMaterialShape(background)) return
        if (!windows.containsKey(decor)) {
            windows[decor] = WeakReference(window)
            decor.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                Guard.run("InstallerDialogGlass.layout", Unit) {
                    hideFills(view, view)
                    update(view)
                }
            }
        }
        ViewGlassUnderlay.attach(
            decor,
            PrefKeys.INSTALLER_DIALOGS,
            { GlassConfig.installerDialogsActive },
            { GlassConfig.installerDialogTint },
            rimOnTop = true,
            sheenOnTop = true,
            backdrop = { blurOf(it) }
        )
        hideFills(decor, decor)
        update(decor)
    }

    private fun hideFills(decor: View, view: View) {
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            val fill = child.background as? ColorDrawable
            if (fill != null && Color.alpha(fill.color) >= SOLID_ALPHA && !ViewGlassUnderlay.isAttached(child)) {
                fill.mutate()
                stockColors[child] = fill.color
                ViewGlassUnderlay.attach(child, PrefKeys.INSTALLER_DIALOGS, { GlassConfig.installerDialogsActive }, { 0f }, glass = false)
            }
            hideFills(decor, child)
        }
    }

    private fun panelColor(decor: View): Int? {
        var best: Int? = null
        var bestDepth = -1
        var maxArea = 0
        fun scan(view: View, depth: Int, collect: Boolean) {
            if (view !is ViewGroup) return
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                val color = stockColors[child] ?: (child.background as? ColorDrawable)?.color
                if (color != null && Color.alpha(color) >= SOLID_ALPHA && child.isShown) {
                    val area = child.width * child.height
                    if (!collect) {
                        if (area > maxArea) maxArea = area
                    } else if (area > 0 && area >= maxArea * FULL_PANEL && depth > bestDepth) {
                        best = color
                        bestDepth = depth
                    }
                }
                scan(child, depth + 1, collect)
            }
        }
        scan(decor, 0, false)
        if (maxArea == 0) return null
        scan(decor, 0, true)
        return best
    }

    private fun blurOf(decor: View): Drawable? {
        blurs[decor]?.let { return it }
        val blur = decor.call("getViewRootImpl").call("createBackgroundBlurDrawable") as? Drawable ?: return null
        blurs[decor] = blur
        update(decor)
        return blur
    }

    private fun update(decor: View) {
        val active = GlassConfig.installerDialogsActive
        windows[decor]?.get()?.let { window ->
            if (active && dimmed[decor] != true && window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                dimmed[decor] = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            } else if (!active && dimmed.remove(decor) == true) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        applyFillColor(decor, active)
        val blur = blurs[decor] ?: return
        val background = decor.background
        panel.set(0, 0, decor.width, decor.height)
        insetOf(background)?.drawable?.bounds?.takeUnless { it.isEmpty }?.let { panel.set(it) }
        blur.bounds = panel
        blur.call("setCornerRadius", OutlineRadius.of(innermost(background)) ?: 0f)
        blur.call("setBlurRadius", if (active) GlassConfig.surface(PrefKeys.INSTALLER_DIALOGS).blurRadiusPx.roundToInt() else 0)
    }

    private fun applyFillColor(decor: View, active: Boolean) {
        val background = decor.background
        panelColor(decor)?.let { fillColors[decor] = it }
        val color = fillColors[decor]
        if (active && color != null && background != null) {
            val tint = color or OPAQUE_ALPHA
            if (tinted[decor] !== background || appliedColors[decor] != tint) {
                tinted[decor] = background
                appliedColors[decor] = tint
                background.setTintList(ColorStateList.valueOf(tint))
            }
        } else {
            appliedColors.remove(decor)
            tinted.remove(decor)?.setTintList(null)
        }
    }

    private fun insetOf(drawable: Drawable?): InsetDrawable? = when (drawable) {
        is InsetDrawable -> drawable
        is DrawableWrapper -> insetOf(drawable.drawable)
        else -> null
    }

    private fun innermost(drawable: Drawable?): Drawable? =
        if (drawable is DrawableWrapper) innermost(drawable.drawable) ?: drawable else drawable

    private fun isMaterialShape(drawable: Drawable?): Boolean {
        if (drawable is DrawableWrapper) return isMaterialShape(drawable.drawable)
        var type: Class<*>? = drawable?.javaClass
        while (type != null) {
            if (type.name == MATERIAL_SHAPE) return true
            type = type.superclass
        }
        return false
    }
}
