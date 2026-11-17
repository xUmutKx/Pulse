package com.drdisagree.glasslayer.xposed.systemui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.compose.ComposeModifiers
import com.drdisagree.glasslayer.xposed.compose.ComposeToolkit
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.extra
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAll
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.proceedWithArgs
import com.drdisagree.glasslayer.xposed.hook.setExtra
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object MediaGlass {

    private const val MEDIA_KT = "${SystemUiModule.PACKAGE}.media.remedia.ui.compose.MediaKt"
    private const val MEDIA_PANEL = "${SystemUiModule.PACKAGE}.media.controls.ui.controller.MediaControlPanel"
    private const val TRANSITION_LAYOUT = "${SystemUiModule.PACKAGE}.util.animation.TransitionLayout"
    private const val MODIFIER_SEARCH_DEPTH = 32
    private const val ART_TRANSPARENCY_RATIO = 0.4f
    private const val LOADED_ICON_SUFFIX = "Icon${'$'}Loaded"
    private const val FULL_ALPHA = 1f
    private const val MEDIA_OVERLAY_FIELD = "glassLayerMediaOverlay"
    private const val MEDIA_ART_FIELD = "glassLayerMediaHasArt"

    private val mediaPanels = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(MEDIA_KT).hookAll("CardBackground") { chain ->
            chain.proceedWithArgs { args -> applyMediaGlass(chain.executable as Method, args) }
        }

        hooks += HookEnv.findClass(MEDIA_PANEL).hookAllAfter("bindPlayer") { chain, _ ->
            val hasArt = chain.args.firstOrNull()?.field("artwork") != null
            mediaPanels.add(chain.thisObject)
            chain.thisObject.setExtra(MEDIA_ART_FIELD, hasArt)
            applyLegacyMediaGlass(chain.thisObject)
        }

        hooks += HookEnv.findClass(TRANSITION_LAYOUT).hookAllAfter("dispatchDraw") { chain, _ ->
            val canvas = chain.args.firstOrNull() as? Canvas ?: return@hookAllAfter
            drawLegacyMediaOverlay(chain.thisObject as View, canvas)
        }
        return hooks
    }

    fun refresh() = refreshLegacyMedia()

    private fun applyLegacyMediaGlass(panel: Any) {
        val holder = panel.field("mMediaViewHolder") ?: return
        val player = (holder.field("player") ?: holder.call("getPlayer")) as? View
            ?: return
        val album = (holder.field("albumView") ?: holder.call("getAlbumView")) as? View
        val hasArt = panel.extra(MEDIA_ART_FIELD) == true
        val current = player.background
        ViewGlassUnderlay.attach(player, PrefKeys.MEDIA, { GlassConfig.mediaActive }, { FULL_ALPHA })

        if (!GlassConfig.mediaActive) {
            player.setExtra(MEDIA_OVERLAY_FIELD, null)
            album?.alpha = 1f
            return
        }

        album?.alpha = mediaContentAlpha(hasArt)
        player.setExtra(
            MEDIA_OVERLAY_FIELD,
            if (hasArt) mediaCornerRadius(album?.background, current) else null
        )
        player.invalidate()
    }

    private fun mediaCornerRadius(vararg backgrounds: Drawable?): Float? {
        backgrounds.forEach { background ->
            background?.shapeCornerRadius()?.let { return it }
        }
        return null
    }

    private fun Drawable.shapeCornerRadius(): Float? {
        var drawable: Drawable? = this
        while (drawable != null) {
            when (drawable) {
                is GradientDrawable -> return drawable.cornerRadii?.firstOrNull() ?: drawable.cornerRadius
                is LayerDrawable -> drawable = if (drawable.numberOfLayers > 0) {
                    drawable.getDrawable(drawable.numberOfLayers - 1)
                } else {
                    null
                }

                is DrawableWrapper -> drawable = drawable.drawable
                else -> drawable = null
            }
        }
        return null
    }

    private fun drawLegacyMediaOverlay(view: View, canvas: Canvas) {
        if (!GlassConfig.mediaActive) return
        val radius = view.extra(MEDIA_OVERLAY_FIELD) as? Float ?: return
        Guard.run("MediaGlass.media_overlay", Unit) {
            ComposeLiquidGlass.drawView(
                view,
                canvas,
                0f,
                0f,
                view.width.toFloat(),
                view.height.toFloat(),
                FloatArray(8) { radius },
                GlassConfig.surface(PrefKeys.MEDIA).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.MEDIA).style.copy(overlay = true),
                key = 1
            )
        }
    }

    private fun mediaContentAlpha(hasArt: Boolean): Float =
        if (hasArt) 1f - (1f - GlassConfig.mediaTint) * ART_TRANSPARENCY_RATIO else GlassConfig.mediaTint

    private fun refreshLegacyMedia() {
        mainHandler.post { mediaPanels.toList().forEach { applyLegacyMediaGlass(it) } }
    }

    private fun applyMediaGlass(method: Method, args: Array<Any?>) {
        if (!GlassConfig.mediaActive) return
        val modifierIndex = method.parameterTypes.indexOfFirst { it.name == ComposeToolkit.MODIFIER_CLASS }
        if (modifierIndex == -1) return

        val hasArt = args.firstOrNull()?.javaClass?.name?.endsWith(LOADED_ICON_SUFFIX) == true
        val modifier = args[modifierIndex] ?: ComposeToolkit.emptyModifier ?: return
        val shape = findShape(modifier) ?: return
        val drawer = ComposeModifiers.contentProxy("MediaGlass.draw") { drawScope -> drawMediaGlass(drawScope, shape, hasArt) } ?: return
        try {
            ComposeModifiers.drawWithContent(modifier, drawer)?.let { args[modifierIndex] = it }
        } catch (_: Throwable) {
        }
    }

    private fun findShape(modifier: Any, depth: Int = 0): Any? {
        if (depth > MODIFIER_SEARCH_DEPTH) return null
        val shapeClass = ComposeToolkit.loadClass(ComposeToolkit.SHAPE_CLASS) ?: return null
        val outer = modifier.field("outer")
        val inner = modifier.field("inner")
        if (outer != null || inner != null) {
            return inner?.let { findShape(it, depth + 1) } ?: outer?.let { findShape(it, depth + 1) }
        }

        var type: Class<*>? = modifier.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field ->
                if (!shapeClass.isAssignableFrom(field.type)) return@forEach
                val value = try {
                    field.isAccessible = true
                    field.get(modifier)
                } catch (_: Throwable) {
                    null
                }
                if (value != null) return value
            }
            type = type.superclass
        }
        return null
    }

    private fun drawMediaGlass(drawScope: Any, shape: Any, hasArt: Boolean) {
        if (!GlassConfig.mediaActive) {
            drawScope.call("drawContent")
            return
        }

        val canvas = ComposeToolkit.nativeCanvas(drawScope)
        val packedSize = ComposeToolkit.drawSize(drawScope)
        if (canvas == null || packedSize == null) {
            drawScope.call("drawContent")
            return
        }

        val width = Float.fromBits((packedSize ushr 32).toInt())
        val height = Float.fromBits((packedSize and 0xFFFFFFFFL).toInt())
        val density = Resources.getSystem().displayMetrics.density

        Guard.run("MediaGlass.media_glass", Unit) {
            ComposeLiquidGlass.draw(
                drawScope,
                shape,
                GlassConfig.surface(PrefKeys.MEDIA).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.MEDIA).style
            )
        }

        val saveCount = canvas.saveLayerAlpha(
            0f,
            0f,
            width,
            height,
            (255 * mediaContentAlpha(hasArt)).roundToInt().coerceIn(0, 255)
        )
        try {
            drawScope.call("drawContent")
        } finally {
            canvas.restoreToCount(saveCount)
        }

        if (!hasArt) return
        Guard.run("MediaGlass.media_overlay", Unit) {
            ComposeLiquidGlass.draw(
                drawScope,
                shape,
                GlassConfig.surface(PrefKeys.MEDIA).blurRadiusPx,
                Color.TRANSPARENT,
                GlassConfig.surface(PrefKeys.MEDIA).style.copy(overlay = true),
                key = 1
            )
        }
    }
}
