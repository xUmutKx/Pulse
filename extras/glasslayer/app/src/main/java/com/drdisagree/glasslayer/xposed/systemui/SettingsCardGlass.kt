package com.drdisagree.glasslayer.xposed.systemui

import android.R as AndroidR
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

object SettingsCardGlass {

    private const val ADAPTER = "com.android.settingslib.widget.SettingsPreferenceGroupAdapter"
    private const val LEGACY_ADAPTER = "com.android.settings.core.RoundCornerPreferenceAdapter"
    private const val BIND = "onBindViewHolder"
    private const val ITEM_VIEW = "itemView"
    private const val RECYCLER_VIEW = "androidx.recyclerview.widget.RecyclerView"
    private const val DRAW = "draw"
    private const val MAIN_SWITCH_BAR = "com.android.settingslib.widget.MainSwitchBar"
    private const val FRAME_VIEW = "mFrameView"

    private val views = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(ADAPTER).hookAllAfter(BIND) { chain, _ -> bind(chain.args.firstOrNull()) }
        hooks += HookEnv.findClass(LEGACY_ADAPTER).hookAllAfter(BIND) { chain, _ -> bind(chain.args.firstOrNull()) }
        val switchBar = HookEnv.findClass(MAIN_SWITCH_BAR)
        hooks += switchBar.hookAllAfter("setChecked") { chain, _ -> switchBar(chain.thisObject) }
        hooks += switchBar.hookAllAfter("setBackground") { chain, _ -> switchBar(chain.thisObject) }
        if (hooks > 0) {
            hooks += HookEnv.findClass(RECYCLER_VIEW).hookAllBefore(DRAW) { chain ->
                val parent = chain.thisObject as? ViewGroup ?: return@hookAllBefore
                val canvas = chain.args.firstOrNull() as? Canvas ?: return@hookAllBefore
                if (!GlassConfig.settingsCardsActive) return@hookAllBefore
                Guard.run("SettingsCardGlass.shadow", Unit) {
                    SettingsCardShadow.draw(parent, canvas, { it in views }, GlassConfig.surface(PrefKeys.SETTINGS_CARDS).style.shadow)
                }
            }
        }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            views.toList().forEach {
                it.invalidate()
                (it.parent as? View)?.invalidate()
            }
        }
    }

    private fun bind(holder: Any?) {
        val view = holder.field(ITEM_VIEW) as? View ?: return
        if (SolidShapes.of(view.background) != null) {
            views.add(view)
            val ref = WeakReference(view)
            ViewGlassUnderlay.attach(
                view,
                PrefKeys.SETTINGS_CARDS,
                { GlassConfig.settingsCardsActive },
                { cardTint(ref.get()) },
                segmented = true
            )
        }
        val icon = view.findViewById<View>(AndroidR.id.icon) as? ImageView ?: return
        if (iconShape(icon) == null) return
        views.add(icon)
        ViewGlassUnderlay.attach(
            icon,
            PrefKeys.SETTINGS_CARDS,
            { GlassConfig.settingsCardsActive },
            { GlassConfig.settingsIconTint },
            source = { iconShape(it) },
            imageContent = true,
            rimOnTop = true,
            sheenOnTop = true
        )
    }

    private fun switchBar(bar: Any?) {
        val frame = bar.field(FRAME_VIEW) as? View ?: return
        if (SolidShapes.of(frame.background) == null) return
        views.add(frame)
        val ref = WeakReference(frame)
        ViewGlassUnderlay.attach(
            frame,
            PrefKeys.SETTINGS_CARDS,
            { GlassConfig.settingsCardsActive },
            { cardTint(ref.get()) },
            rimOnTop = true,
            sheenOnTop = true
        )
    }

    private fun cardTint(view: View?): Float =
        if (view != null && AccentColors.isAccent(view)) GlassConfig.settingsAccentTint else GlassConfig.settingsCardTint

    private fun iconShape(view: View): Drawable? {
        val layers = (view as? ImageView)?.drawable as? LayerDrawable ?: return null
        if (layers.numberOfLayers < 2) return null
        return layers.getDrawable(0) as? ShapeDrawable
    }
}
