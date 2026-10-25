package com.umutk.pulse.extras.palette.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.core.icon.WidgetStyle

/** Themed card widget: gradient/glass/minimal/swatch background, theme name label. */
class PaletteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(ctx, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, id: Int, options: android.os.Bundle
    ) = updateOne(ctx, mgr, id)

    companion object {
        internal fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val palette = WidgetRefresher.currentPalette(ctx)
            val style = WidgetRefresher.currentStyle(ctx)
            val labelVisible = WidgetRefresher.labelVisible(ctx)

            val options = mgr.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 110)
                .coerceAtLeast(40)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                .coerceAtLeast(40)
            val density = ctx.resources.displayMetrics.density
            val widthPx = (widthDp * density).toInt()
            val heightPx = (heightDp * density).toInt()
            val cornerPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24f, ctx.resources.displayMetrics
            )

            val photo = if (style == WidgetStyle.PHOTO) WidgetRefresher.photoBitmap(ctx) else null
            val bg = WidgetRenderer.background(palette, style, widthPx, heightPx, cornerPx, photo)
            val textColor = WidgetRenderer.readableTextColor(if (photo != null) 0xFF000000.toInt() else palette.accent)

            val views = RemoteViews(ctx.packageName, R.layout.palette_widget_card)
            views.setImageViewBitmap(R.id.palette_w_bg, bg)
            views.setTextViewText(R.id.palette_w_sub, palette.name)
            views.setTextColor(R.id.palette_w_title, textColor)
            views.setTextColor(R.id.palette_w_sub, textColor)
            views.setViewVisibility(
                R.id.palette_w_sub,
                if (labelVisible && style != WidgetStyle.SWATCH) android.view.View.VISIBLE else android.view.View.GONE
            )
            views.setViewVisibility(
                R.id.palette_w_title,
                if (style != WidgetStyle.SWATCH) android.view.View.VISIBLE else android.view.View.GONE
            )
            mgr.updateAppWidget(id, views)
        }
    }
}
