package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.widget.RemoteViews
import com.umutk.pulse.R
import java.io.File

/** A photo frame on the home screen: one photo per widget, drawn as a square tile (the Windows tile shape) with rounded corners and an optional border. A tap reopens the settings. */
class PhotoWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { mgr.updateAppWidget(it, build(ctx, it)) } }
    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { File(ctx.filesDir, "photo_widget/$it.src").delete(); prefs(ctx).edit().remove("c$it").remove("r$it").remove("b$it").apply() }

    companion object {
        private const val SIZE = 640
        fun prefs(c: Context) = c.getSharedPreferences("photo_widget", Context.MODE_PRIVATE)
        private fun src(c: Context, id: Int) = File(c.filesDir, "photo_widget/$id.src")

        /** Keeps a scaled copy of the picked photo for the widget (the picked address may not stay readable). */
        fun adopt(c: Context, id: Int, uri: Uri): Boolean = runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1; while (o.outWidth / s > SIZE * 2 && o.outHeight / s > SIZE * 2) s *= 2
            val bm = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return false
            src(c, id).also { it.parentFile?.mkdirs() }.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)

        fun hasPhoto(c: Context, id: Int) = src(c, id).exists()

        /** crop: fill the tile (true) or fit the whole photo (false); [radius] 0..1 of half the tile; [border] a thin line round the tile. */
        fun render(photo: Bitmap?, crop: Boolean, radius: Float, border: Boolean): Bitmap {
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val r = radius.coerceIn(0f, 1f) * SIZE / 2f
            val tile = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
            val clip = Path().apply { addRoundRect(tile, r, r, Path.Direction.CW) }
            cv.save(); cv.clipPath(clip)
            cv.drawColor(0xFF202020.toInt())
            if (photo != null) {
                val k = if (crop) maxOf(SIZE / photo.width.toFloat(), SIZE / photo.height.toFloat()) else minOf(SIZE / photo.width.toFloat(), SIZE / photo.height.toFloat())
                val w = photo.width * k; val h = photo.height * k
                cv.drawBitmap(photo, null, RectF((SIZE - w) / 2, (SIZE - h) / 2, (SIZE + w) / 2, (SIZE + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
            cv.restore()
            if (border) cv.drawPath(Path().apply { addRoundRect(RectF(3f, 3f, SIZE - 3f, SIZE - 3f), maxOf(0f, r - 3f), maxOf(0f, r - 3f), Path.Direction.CW) },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0x66FFFFFF })
            return out
        }

        fun build(c: Context, id: Int): RemoteViews {
            val p = prefs(c)
            val photo = runCatching { BitmapFactory.decodeFile(src(c, id).path) }.getOrNull()
            val bm = render(photo, p.getBoolean("c$id", true), p.getFloat("r$id", .18f), p.getBoolean("b$id", false))
            val open = Intent(c, PhotoWidgetConfig::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val tap = PendingIntent.getActivity(c, 7_600 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return RemoteViews(c.packageName, R.layout.widget_photo).apply { setImageViewBitmap(R.id.photo, bm); setOnClickPendingIntent(R.id.root, tap) }
        }

        fun refresh(c: Context, id: Int) { AppWidgetManager.getInstance(c).updateAppWidget(id, build(c, id)) }
        fun provider(c: Context) = ComponentName(c, PhotoWidget::class.java)
    }
}
