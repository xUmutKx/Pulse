package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object PowerMenuGlass {

    private const val LAYOUT = "${SystemUiModule.PACKAGE}.globalactions.GlobalActionsLayoutLite"
    private const val ADAPTER = "${SystemUiModule.PACKAGE}.globalactions.GlobalActionsDialogLite\$MyAdapter"

    private const val OPAQUE = 255
    private const val MAX_DEPTH = 12
    private const val TYPE_STATUS_BAR_SUB_PANEL = WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW + 14
    private const val VIEW_ROOT = "android.view.ViewRootImpl"

    private val views = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val dimmedRoots = WeakHashMap<View, Float>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(LAYOUT).hookAllAfter("onLayout") { chain, _ ->
            val view = chain.thisObject as? View ?: return@hookAllAfter
            Guard.run("PowerMenuGlass.layout", Unit) {
                wrapTree(view)
                moveDim(view.rootView)
            }
        }
        hooks += HookEnv.findClass(VIEW_ROOT).hookAllBefore("setView") { chain ->
            val root = chain.args.getOrNull(0) as? View ?: return@hookAllBefore
            val params = chain.args.getOrNull(1) as? WindowManager.LayoutParams ?: return@hookAllBefore
            Guard.run("PowerMenuGlass.window", Unit) { stripDim(root, params) }
        }
        hooks += HookEnv.findClass(ADAPTER).hookAllAfter("getView") { _, result ->
            val view = result as? View ?: return@hookAllAfter
            Guard.run("PowerMenuGlass.item", Unit) { wrapTree(view) }
        }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            Guard.run("PowerMenuGlass.refresh", Unit) {
                if (!GlassConfig.powerMenuActive) dimmedRoots.keys.toList().forEach { restoreDim(it) }
            }
        }
    }

    private fun stripDim(root: View, params: WindowManager.LayoutParams) {
        if (!GlassConfig.powerMenuActive) return
        if (params.type != TYPE_STATUS_BAR_SUB_PANEL) return
        if (params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND == 0) return
        if (!root.hosts(LAYOUT, 0)) return
        dimmedRoots[root] = params.dimAmount
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        attachScrim(root)
    }

    private fun View.hosts(name: String, depth: Int): Boolean {
        if (javaClass.name == name) return true
        if (this !is ViewGroup || depth >= MAX_DEPTH) return false
        for (index in 0 until childCount) if (getChildAt(index).hosts(name, depth + 1)) return true
        return false
    }

    private fun attachScrim(root: View) {
        ViewGlassUnderlay.attachScrim(root, { GlassConfig.powerMenuActive }) {
            Color.argb(((dimmedRoots[root] ?: 0f) * OPAQUE).roundToInt().coerceIn(0, OPAQUE), 0, 0, 0)
        }
    }

    private fun moveDim(root: View) {
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        if (!GlassConfig.powerMenuActive) {
            restoreDim(root)
            return
        }
        if (params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND == 0) return
        dimmedRoots[root] = params.dimAmount
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        root.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        attachScrim(root)
    }

    private fun restoreDim(root: View) {
        val amount = dimmedRoots.remove(root) ?: return
        ViewGlassUnderlay.detach(root)
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        if (!root.isAttachedToWindow) return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        params.dimAmount = amount
        root.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
    }

    private fun wrapTree(view: View) {
        if (view !in views && SolidShapes.of(view.background) != null) {
            views.add(view)
            val panel = view is ViewGroup
            ViewGlassUnderlay.attach(
                view,
                PrefKeys.POWER_MENU,
                { GlassConfig.powerMenuActive },
                {
                    when {
                        panel -> GlassConfig.powerMenuTint
                        AccentColors.isAccent(view) -> GlassConfig.powerMenuAccentTint
                        else -> GlassConfig.powerMenuButtonTint
                    }
                }
            )
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) wrapTree(view.getChildAt(index))
        }
    }
}
