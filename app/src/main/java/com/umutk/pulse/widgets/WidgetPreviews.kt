package com.umutk.pulse.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews

/**
 * The launcher picker's previews, drawn by the same code as the placed widgets (so they match the in-app previews and the current look).
 * Android 15 lets an app hand the picker a RemoteViews per provider; compileSdk is 34, hence reflection. Older Androids keep the static previewImage.
 */
object WidgetPreviews {
    // shortcut: the system rate-limits setWidgetPreview, so a refused call is dropped; the next look change tries again
    fun publish(ctx: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        Thread {
            try {
                val c = ctx.applicationContext
                val mgr = AppWidgetManager.getInstance(c)
                val set = AppWidgetManager::class.java.getMethod("setWidgetPreview", ComponentName::class.java, Int::class.javaPrimitiveType, RemoteViews::class.java)
                val accent = WidgetAccent.app(c)
                fun put(cls: Class<*>, v: () -> RemoteViews) = try { set.invoke(mgr, ComponentName(c, cls), 1, v()) } catch (e: Exception) { }
                val style = WidgetStyles.default(c)
                WidgetData.all.forEach { w -> put(w.javaClass) { w.contentFor(c, 0, style) } }
                SkinWidgets.all.forEach { w -> put(w.javaClass) { w.views(c, accent) } }
                FaceWidgets.all.forEach { w -> put(w.javaClass) { w.views(c, w.look, accent) } }
            } catch (e: Exception) { }
        }.start()
    }
}
