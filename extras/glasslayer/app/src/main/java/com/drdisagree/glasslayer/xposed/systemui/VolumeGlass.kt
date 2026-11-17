package com.drdisagree.glasslayer.xposed.systemui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.GlassJoin
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookMatching
import com.drdisagree.glasslayer.xposed.hook.hookMatchingBefore
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object VolumeGlass {

    private const val DIALOG_BINDER = "${SystemUiModule.PACKAGE}.volume.dialog.ui.binder.VolumeDialogViewBinder"
    private const val RINGER_BINDER = "${SystemUiModule.PACKAGE}.volume.dialog.ringer.ui.binder.VolumeDialogRingerViewBinder"
    private const val SETTINGS_BINDER = "${SystemUiModule.PACKAGE}.volume.dialog.settings.ui.binder.VolumeDialogSettingsButtonViewBinder"
    private const val SLIDER_TRACK_KT = "${SystemUiModule.PACKAGE}.volume.dialog.sliders.ui.compose.VolumeDialogSliderTrackKt"
    private const val SLIDER_DEFAULTS = "androidx.compose.material3.SliderDefaults"
    private const val VOLUME_SLIDER_PACKAGE = "${SystemUiModule.PACKAGE}.volume.dialog.sliders."
    private const val VOLUME_PANEL_PACKAGE = "${SystemUiModule.PACKAGE}.volume.panel."
    private const val PACKAGE_SHIFT = 24
    private const val OPAQUE = 255
    private const val PANEL_ID = "volume_dialog_background"
    private const val RINGER_ID = "ringer_buttons_background"
    private const val TRANSITION_ALPHA = "mAlpha"

    private val views = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val watchedRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private var panelRef: WeakReference<View>? = null
    private val track = TrackGlass("VolumeGlass", PrefKeys.VOLUME) { panelDim() }
    private var ringerRef: WeakReference<View>? = null
    private val recomposeScopes = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val panelScopes = WeakHashMap<Any, Boolean>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeDepth = 0

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(DIALOG_BINDER).hookAllAfter("bind") { chain, _ ->
            val dialog = chain.args.firstOrNull { it is Dialog } as? Dialog ?: return@hookAllAfter
            val root = dialog.window?.decorView ?: return@hookAllAfter
            watch(root)
            root.post { Guard.run("VolumeGlass.panel", Unit) { wrapTree(root) } }
        }
        hooks += HookEnv.findClass(SETTINGS_BINDER).hookAllAfter("bind") { chain, _ ->
            val root = chain.args.firstOrNull { it is View } as? View ?: return@hookAllAfter
            wrapTree(root)
        }
        val ringer = HookEnv.findClass(RINGER_BINDER)
        hooks += ringer.hookAllAfter("updateBackground") { chain, _ ->
            val view = chain.args.firstOrNull { it is View } as? View ?: return@hookAllAfter
            track(view)
        }
        hooks += ringer.hookAllAfter("bindDrawerButton") { chain, _ ->
            val view = chain.args.firstOrNull { it is View } as? View ?: return@hookAllAfter
            track(view)
        }
        hooks += HookEnv.findClass(SLIDER_TRACK_KT).hookMatching(Regex("SliderTrack(-.*)?")) { chain ->
            val method = chain.executable as Method
            val scope = recomposeScope(method, chain.args)
            val inPanel = scope?.let { panelScopes.getOrPut(it) { calledFromVolumePanel() } } ?: calledFromVolumePanel()
            if (inPanel) return@hookMatching chain.proceed()
            scope?.let { recomposeScopes.add(it) }
            composeDepth++
            try {
                chain.proceedWithArgs { args -> applyTrackColors(method, args) }
            } finally {
                composeDepth--
            }
        }
        hooks += HookEnv.findClass(SLIDER_DEFAULTS).hookMatching(Regex("Track(-.*)?")) { chain ->
            if (!GlassConfig.volumeActive || (composeDepth <= 0 && !calledFromDialogSlider())) {
                return@hookMatching chain.proceed()
            }
            chain.proceedWithArgs { args -> track.wrap(chain.executable as Method, args) }
        }
        hooks += HookEnv.findClass(SLIDER_DEFAULTS).hookMatchingBefore(Regex("drawTrackPath.*")) { chain ->
            if (GlassConfig.volumeActive) {
                Guard.run("VolumeGlass.segment_glass", Unit) { track.draw(chain.args) }
            }
        }
        return hooks
    }

    fun refresh() {
        mainHandler.post {
            views.toList().forEach { apply(it) }
            recomposeScopes.toList().forEach { it.call("invalidateForResult", null) }
        }
    }

    private fun watch(root: View) {
        if (!watchedRoots.add(root)) return
        root.viewTreeObserver.addOnGlobalLayoutListener(
            ViewTreeObserver.OnGlobalLayoutListener {
                Guard.run("VolumeGlass.layout", Unit) { discover(root) }
            }
        )
    }

    private fun discover(view: View) {
        if (view !in views && SolidShapes.of(view.background) != null) track(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) discover(view.getChildAt(index))
        }
    }

    private fun wrapTree(view: View) {
        if (SolidShapes.of(view.background) != null) track(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) wrapTree(view.getChildAt(index))
        }
    }

    private fun track(view: View) {
        if (SolidShapes.of(view.background) == null && view !in views) return
        views.add(view)
        apply(view)
    }

    private fun apply(view: View) {
        val id = idName(view)
        if (id == PANEL_ID) panelRef = WeakReference(view)
        if (id == RINGER_ID) ringerRef = WeakReference(view)
        val ref = WeakReference(view)
        ViewGlassUnderlay.attach(
            view,
            PrefKeys.VOLUME,
            { GlassConfig.volumeActive },
            { ref.get()?.let { tintOf(it) } ?: GlassConfig.volumeTint },
            joinWith = when (id) {
                PANEL_ID -> { host, out -> shapeOf(ringerRef?.get(), host, out) }
                RINGER_ID -> { host, out -> shapeOf(panelRef?.get(), host, out) }
                else -> null
            },
            clipJoin = id == RINGER_ID,
            tintInGlass = id == PANEL_ID || id == RINGER_ID
        )
    }

    private fun tintOf(view: View): Float {
        val active = activeAmount(view)
        val base = GlassConfig.volumeTint
        return if (active <= 0f) base else base + (GlassConfig.activeVolumeSliderTint - base) * active
    }

    private fun activeAmount(view: View): Float {
        if (view is ViewGroup) return 0f
        if (view.isSelected) return 1f
        val transition = transitionOf(view.background) ?: return 0f
        val alpha = transition.field(TRANSITION_ALPHA) as? Int ?: return 0f
        return (alpha / OPAQUE.toFloat()).coerceIn(0f, 1f)
    }

    private fun transitionOf(drawable: Drawable?): TransitionDrawable? = when (drawable) {
        is TransitionDrawable -> drawable
        is DrawableWrapper -> transitionOf(drawable.drawable)
        else -> null
    }

    private fun shapeOf(other: View?, host: View, out: GlassJoin): Boolean {
        val view = other?.takeIf { it.isShown && it.width > 0 && it.height > 0 } ?: return false
        val shape = SolidShapes.of(view.background) ?: return false
        val bounds = shape.bounds.takeUnless { it.isEmpty } ?: return false
        val rect = out.rect
        rect.set(bounds)
        rect.offset(rootX(view) - rootX(host), rootY(view) - rootY(host))
        val maxRadius = minOf(rect.width(), rect.height()) / 2f
        val shapeRadii = shape.cornerRadii
        if (shape.shape == GradientDrawable.OVAL) {
            out.radii.fill(maxRadius)
        } else if (shapeRadii != null && shapeRadii.size == GlassJoin.RADII) {
            shapeRadii.forEachIndexed { index, value -> out.radii[index] = value.coerceAtMost(maxRadius) }
        } else {
            out.radii.fill(shape.cornerRadius.coerceAtMost(maxRadius))
        }
        return true
    }

    private fun idName(view: View): String? {
        if (view.id == View.NO_ID || view.id ushr PACKAGE_SHIFT == 0) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            null
        }
    }

    private fun rootX(view: View): Float {
        var x = view.left + view.translationX
        var parent = view.parent
        while (parent is View) {
            x += parent.left + parent.translationX - parent.scrollX
            parent = parent.parent
        }
        return x
    }

    private fun rootY(view: View): Float {
        var y = view.top + view.translationY
        var parent = view.parent
        while (parent is View) {
            y += parent.top + parent.translationY - parent.scrollY
            parent = parent.parent
        }
        return y
    }

    private fun recomposeScope(method: Method, args: List<Any?>): Any? {
        val composerIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.COMPOSER_CLASS }
        return args.getOrNull(composerIndex)?.call("getRecomposeScope")
    }

    private fun calledFromVolumePanel(): Boolean =
        Thread.currentThread().stackTrace.any { it.className.startsWith(VOLUME_PANEL_PACKAGE) }

    private fun calledFromDialogSlider(): Boolean {
        var slider = false
        for (element in Thread.currentThread().stackTrace) {
            val name = element.className
            if (name.startsWith(VOLUME_PANEL_PACKAGE)) return false
            if (name.startsWith(VOLUME_SLIDER_PACKAGE)) slider = true
        }
        return slider
    }

    private fun applyTrackColors(method: Method, args: Array<Any?>) {
        if (!GlassConfig.volumeActive) return
        SliderColorsTint.apply(method, args, GlassConfig.volumeSliderTint, GlassConfig.activeVolumeSliderTint)
    }

    private fun panelDim(): Int {
        val color = panelRef?.get()?.background.let { SolidShapes.of(it) }?.color?.defaultColor ?: return Color.TRANSPARENT
        return ColorUtils.setAlphaComponent(color, (Color.alpha(color) * GlassConfig.volumeTint).roundToInt().coerceIn(0, OPAQUE))
    }
}
