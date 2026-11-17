package com.drdisagree.glasslayer.xposed.systemui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.graphics.ColorUtils
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.HostApplication
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.extra
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore
import com.drdisagree.glasslayer.xposed.hook.hookMatchingAfter
import com.drdisagree.glasslayer.xposed.hook.setExtra
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

@SuppressLint("DiscouragedApi")
object NotificationGlass {

    private const val BACKGROUND_VIEW = "${SystemUiModule.PACKAGE}.statusbar.notification.row.NotificationBackgroundView"
    private const val HEADER_VIEW = "android.view.NotificationHeaderView"
    private const val EXPAND_BUTTON = "com.android.internal.widget.NotificationExpandButton"
    private const val ROW_SEARCH_DEPTH = 6
    private const val ROW_CLASS_SUFFIX = "ExpandableNotificationRow"
    private const val COLOR_LAYER_ID = "notification_background_color_layer"
    private const val NOTIFICATION_GLASS_FIELD = "glassLayerNotificationGlass"
    private const val NOTIFICATION_COLOR_FIELD = "glassLayerNotificationColor"
    private const val HEADER_ALPHA_FIELD = "glassLayerHeaderAlpha"
    private const val HEADER_COLOR_FIELD = "glassLayerHeaderColor"
    private val FOOTER_VIEW_CLASSES = arrayOf(
        "${SystemUiModule.PACKAGE}.statusbar.notification.footer.ui.view.FooterView",
        "${SystemUiModule.PACKAGE}.statusbar.notification.row.FooterView"
    )
    private val FOOTER_BUTTON_FIELDS = listOf("mClearAllButton", "mHistoryButton", "mSettingsButton")

    private val footerViews = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val backgroundViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintPath = Path()
    private var colorLayerId: Int? = null

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(BACKGROUND_VIEW).hookAllBefore("onDraw") { chain ->
            val canvas = chain.args.firstOrNull() as? Canvas ?: return@hookAllBefore
            val view = chain.thisObject as View
            backgroundViews.add(view)
            drawNotificationGlass(view, canvas)
        }
        DozeTracker.addListener(::redrawRows)

        hooks += HookEnv.findClass(HEADER_VIEW).hookAllBefore("onDraw") { chain ->
            val header = chain.thisObject
            val background = header.field("mBackground") as? Drawable ?: return@hookAllBefore
            val stockAlpha = header.extra(HEADER_ALPHA_FIELD) as? Int

            if (GlassConfig.notificationsActive) {
                if (stockAlpha == null) header.setExtra(HEADER_ALPHA_FIELD, background.alpha)
                if (background is ColorDrawable) {
                    val stockColor = header.extra(HEADER_COLOR_FIELD) as? Int
                        ?: background.color.also { header.setExtra(HEADER_COLOR_FIELD, it) }
                    background.color = withOpacity(stockColor, GlassConfig.notificationTint)
                } else {
                    val baseAlpha = stockAlpha ?: background.alpha
                    background.alpha = (baseAlpha * GlassConfig.notificationTint).roundToInt().coerceIn(0, 255)
                }
                val canvas = chain.args.firstOrNull() as? Canvas
                if (canvas != null) drawGroupHeaderGlass(header as View, canvas)
            } else if (stockAlpha != null) {
                (header.extra(HEADER_COLOR_FIELD) as? Int)?.let { (background as? ColorDrawable)?.color = it }
                background.alpha = stockAlpha
                header.setExtra(HEADER_ALPHA_FIELD, null)
                header.setExtra(HEADER_COLOR_FIELD, null)
            }
        }

        hooks += HookEnv.findClass(EXPAND_BUTTON).hookAllAfter("updateColors") { chain, _ ->
            val pill = chain.thisObject.field("mPillDrawable") as? Drawable ?: return@hookAllAfter
            pill.alpha = if (GlassConfig.notificationsActive) {
                (255 * GlassConfig.notificationTint).roundToInt().coerceIn(0, 255)
            } else {
                255
            }
        }

        hooks += HookEnv.findClass(*FOOTER_VIEW_CLASSES).hookMatchingAfter(Regex("updateColors.*")) { chain, _ ->
            footerViews.add(chain.thisObject)
            applyFooterGlass(chain.thisObject)
        }
        return hooks
    }

    fun refresh() {
        refreshFooterGlass()
        redrawRows()
    }

    private fun redrawRows() {
        mainHandler.post {
            backgroundViews.toList().forEach { if (it.isAttachedToWindow) it.invalidate() }
        }
    }

    private fun rowSurface(pinned: Boolean, onKeyguard: Boolean): String = when {
        pinned -> PrefKeys.HEADS_UP
        onKeyguard -> PrefKeys.LOCKSCREEN_NOTIFICATIONS
        else -> PrefKeys.NOTIFICATIONS
    }

    private fun drawNotificationGlass(view: View, canvas: Canvas) {
        val background = view.field("mBackground") as? Drawable ?: return
        val drawableAlpha = (view.field("mDrawableAlpha") as? Number)?.toInt() ?: 255
        val row = view.parent as? View

        val pinned = row?.call("isPinned") == true
        val onKeyguard = row?.field("mOnKeyguard") == true
        val surfaceKey = rowSurface(pinned, onKeyguard)
        val tintLevel = when (surfaceKey) {
            PrefKeys.HEADS_UP -> GlassConfig.headsUpTint
            PrefKeys.LOCKSCREEN_NOTIFICATIONS -> GlassConfig.lockscreenNotificationTint
            else -> GlassConfig.notificationTint
        }
        val surface = GlassConfig.surface(surfaceKey)
        val actualWidth = (view.field("mActualWidth") as? Int)?.takeIf { it >= 0 }?.coerceAtMost(view.width) ?: view.width
        val expandedGroup = row?.call("isSummaryWithChildren") == true && row.call("isGroupExpanded") == true
        val stockHidden = drawableAlpha == 0 || actualWidth == 0
        val enabled = (if (pinned) GlassConfig.headsUpActive else GlassConfig.notificationsActive) &&
                (!onKeyguard || GlassConfig.lockscreenNotificationsActive) &&
                view.field("mExpandAnimationRunning") != true
        val active = enabled && !stockHidden && !expandedGroup

        val colorLayer = background.notificationColorLayer() as? GradientDrawable

        if (enabled && expandedGroup) {
            if (background.alpha != 0) background.alpha = 0
            view.setExtra(NOTIFICATION_GLASS_FIELD, true)
            return
        }

        if (!active) {
            if (view.extra(NOTIFICATION_GLASS_FIELD) == true) {
                (view.extra(NOTIFICATION_COLOR_FIELD) as? ColorStateList)
                    ?.let { colorLayer?.color = it }
                view.setExtra(NOTIFICATION_COLOR_FIELD, null)
                background.alpha = drawableAlpha
                view.setExtra(NOTIFICATION_GLASS_FIELD, false)
            }
            return
        }

        val actualHeight = view.field("mActualHeight") as? Int ?: return
        val clipTop = view.field("mClipTopAmount") as? Int ?: 0
        val clipBottom = view.field("mClipBottomAmount") as? Int ?: 0
        val radii = view.field("mCornerRadii") as? FloatArray ?: return
        if (radii.size < 8 || clipTop + clipBottom >= actualHeight) return

        var bottom = actualHeight
        if (view.field("mBottomIsRounded") == true &&
            view.field("mBottomAmountClips") == true
        ) {
            bottom -= clipBottom
        }

        val left = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) view.width - actualWidth else 0
        val right = left + actualWidth

        val saveCount = canvas.save()
        try {
            Guard.run("NotificationGlass.notification_glass", Unit) {
                canvas.clipRect(left, clipTop, right, actualHeight - clipBottom)
                ComposeLiquidGlass.drawView(
                    view,
                    canvas,
                    left.toFloat(),
                    0f,
                    actualWidth.toFloat(),
                    bottom.toFloat(),
                    radii,
                    surface.blurRadiusPx,
                    Color.TRANSPARENT,
                    surface.style,
                    openSmallEdges = true
                )

                if (colorLayer != null) {
                    val tint = (view.field("mTintColor") as? Int)?.takeIf { it != 0 }
                        ?: view.field("mNormalColor") as? Int
                    if (tint != null) {
                        tintPath.reset()
                        tintPath.addRoundRect(
                            left.toFloat(),
                            0f,
                            right.toFloat(),
                            bottom.toFloat(),
                            radii,
                            Path.Direction.CW
                        )
                        tintPaint.color = withOpacity(tint, tintLevel * drawableAlpha / 255f)
                        canvas.drawPath(tintPath, tintPaint)
                    }
                }
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }

        if (colorLayer != null) {
            if (view.extra(NOTIFICATION_COLOR_FIELD) == null) {
                view.setExtra(NOTIFICATION_COLOR_FIELD, colorLayer.color)
            }
            if (colorLayer.color?.defaultColor != Color.TRANSPARENT) colorLayer.setColor(Color.TRANSPARENT)
            background.alpha = drawableAlpha
        } else {
            background.alpha = (drawableAlpha * tintLevel).roundToInt().coerceIn(0, 255)
        }
        view.setExtra(NOTIFICATION_GLASS_FIELD, true)
    }

    private fun drawGroupHeaderGlass(header: View, canvas: Canvas) {
        if (header.width <= 0 || header.height <= 0) return

        var row: View? = header.parent as? View
        var depth = 0
        while (row != null && depth < ROW_SEARCH_DEPTH && !row.javaClass.name.endsWith(ROW_CLASS_SUFFIX)) {
            row = row.parent as? View
            depth++
        }
        if (row == null || !row.javaClass.name.endsWith(ROW_CLASS_SUFFIX)) return
        if (row.field("mOnKeyguard") == true && !GlassConfig.lockscreenNotificationsActive) return
        if (row.call("isPinned") == true && !GlassConfig.headsUpActive) return
        val surface = GlassConfig.surface(rowSurface(row.call("isPinned") == true, row.field("mOnKeyguard") == true))

        val rowRadii = (row.field("mBackgroundNormal") as? View)
            ?.field("mCornerRadii") as? FloatArray
        val top = rowRadii?.getOrNull(0) ?: 0f
        val radii = floatArrayOf(top, top, top, top, 0f, 0f, 0f, 0f)

        Guard.run("NotificationGlass.group_header_glass", Unit) {
            ComposeLiquidGlass.drawView(
                header,
                canvas,
                0f,
                0f,
                header.width.toFloat(),
                header.height.toFloat(),
                radii,
                surface.blurRadiusPx,
                Color.TRANSPARENT,
                surface.style
            )
        }
    }

    private fun Drawable.notificationColorLayer(): Drawable? {
        val layers = this as? LayerDrawable ?: return null
        if (colorLayerId == null) {
            colorLayerId = HostApplication.get()?.resources?.getIdentifier(COLOR_LAYER_ID, "id", SystemUiModule.PACKAGE)
        }
        val id = colorLayerId ?: 0
        if (id != 0) layers.findDrawableByLayerId(id)?.let { return it }
        return if (layers.numberOfLayers > 1) layers.getDrawable(0) else null
    }

    private fun withOpacity(color: Int, opacity: Float): Int =
        ColorUtils.setAlphaComponent(color, (255 * opacity).roundToInt().coerceIn(0, 255))

    private fun refreshFooterGlass() {
        mainHandler.post {
            footerViews.toList().forEach { applyFooterGlass(it) }
        }
    }

    private fun applyFooterGlass(footerView: Any) {
        FOOTER_BUTTON_FIELDS.forEach { field ->
            val button = footerView.field(field) as? View ?: return@forEach
            ViewGlassUnderlay.attach(
                button,
                PrefKeys.NOTIFICATIONS,
                { GlassConfig.notificationsActive },
                { GlassConfig.notificationFooterTint }
            )
        }
    }
}
