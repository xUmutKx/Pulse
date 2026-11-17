package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore
import java.util.Collections
import java.util.WeakHashMap

object BackGestureGlass {

    private const val BACK_PANEL = "${SystemUiModule.PACKAGE}.navigationbar.gestural.BackPanel"
    private const val POSITION = "pos"
    private const val RADII = 8

    private val panels = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val tintedPaints = Collections.newSetFromMap(WeakHashMap<Paint, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val radii = FloatArray(RADII)
    private var filterTint = Float.NaN
    private var filter: ColorMatrixColorFilter? = null

    fun install(): Int = HookEnv.findClass(BACK_PANEL).hookAllBefore("onDraw") { chain ->
        val panel = chain.thisObject as? View ?: return@hookAllBefore
        val canvas = chain.args.firstOrNull() as? Canvas ?: return@hookAllBefore
        panels.add(panel)
        Guard.run("BackGestureGlass.panel", Unit) { drawPanel(panel, canvas) }
    }

    fun refresh() {
        mainHandler.post { panels.toList().forEach { it.invalidate() } }
    }

    private fun drawPanel(panel: View, canvas: Canvas) {
        val paint = panel.field("arrowBackgroundPaint") as? Paint ?: return
        if (!GlassConfig.backGestureActive) {
            if (tintedPaints.remove(paint)) paint.colorFilter = null
            return
        }
        val tintFilter = filterFor(GlassConfig.backGestureTint)
        if (paint.colorFilter !== tintFilter) paint.colorFilter = tintFilter
        tintedPaints.add(paint)

        val alpha = panel.position("backgroundAlpha") ?: return
        if (alpha <= 0f) return
        val edge = panel.position("backgroundEdgeCornerRadius") ?: return
        val far = panel.position("backgroundFarCornerRadius") ?: return
        val halfHeight = (panel.position("backgroundHeight") ?: return) / 2f
        val width = panel.position("backgroundWidth") ?: return
        val pivot = panel.position("scalePivotX") ?: return
        val shiftX = panel.position("horizontalTranslation") ?: return
        val shiftY = panel.position("verticalTranslation") ?: return
        val scale = panel.position("scale") ?: return
        if (width <= 0f || halfHeight <= 0f || scale <= 0f) return

        val start = pivot + (0f - pivot) * scale + shiftX
        val end = pivot + (width - pivot) * scale + shiftX
        val centerY = panel.height * 0.5f + shiftY
        val top = centerY - halfHeight * scale
        val bottom = centerY + halfHeight * scale
        val leftPanel = panel.field("isLeftPanel") == true
        val left = if (leftPanel) start else panel.width - end
        val right = if (leftPanel) end else panel.width - start
        val edgeRadius = edge * scale
        val farRadius = far * scale
        val startRadius = if (leftPanel) edgeRadius else farRadius
        val endRadius = if (leftPanel) farRadius else edgeRadius
        radii[0] = startRadius
        radii[1] = startRadius
        radii[2] = endRadius
        radii[3] = endRadius
        radii[4] = endRadius
        radii[5] = endRadius
        radii[6] = startRadius
        radii[7] = startRadius

        val surface = GlassConfig.surface(PrefKeys.BACK_GESTURE)
        ComposeLiquidGlass.drawView(
            panel,
            canvas,
            left,
            top,
            right - left,
            bottom - top,
            radii,
            surface.blurRadiusPx,
            Color.TRANSPARENT,
            surface.style,
            alpha.coerceIn(0f, 1f)
        )
    }

    private fun View.position(name: String): Float? = field(name)?.field(POSITION) as? Float

    private fun filterFor(tint: Float): ColorMatrixColorFilter {
        filter?.takeIf { filterTint == tint }?.let { return it }
        val matrix = ColorMatrix()
        matrix.setScale(1f, 1f, 1f, tint.coerceIn(0f, 1f))
        return ColorMatrixColorFilter(matrix).also {
            filter = it
            filterTint = tint
        }
    }
}
