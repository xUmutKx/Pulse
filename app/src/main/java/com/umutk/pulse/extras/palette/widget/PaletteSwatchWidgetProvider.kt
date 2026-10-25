package com.umutk.pulse.extras.palette.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.palette.core.icon.WidgetStyle

/** Wide strip widget: the full active palette rendered as equal colour bars. */
class PaletteSwatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(ctx, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, id: Int, options: android.os.Bundle
    ) = updateOne(ctx, mgr, id)

    companion object {
        private fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val palette = WidgetRefresher.currentPalette(ctx)

            val options = mgr.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 220)
                .coerceAtLeast(60)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 60)
                .coerceAtLeast(30)
            val density = ctx.resources.displayMetrics.density
            val widthPx = (widthDp * density).toInt()
            val heightPx = (heightDp * density).toInt()
            val cornerPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 20f, ctx.resources.displayMetrics
            )

            val bg = WidgetRenderer.background(palette, WidgetStyle.SWATCH, widthPx, heightPx, cornerPx)
            val views = RemoteViews(ctx.packageName, R.layout.palette_widget_swatch)
            views.setImageViewBitmap(R.id.palette_w_bg, bg)
            mgr.updateAppWidget(id, views)
        }
    }
}
