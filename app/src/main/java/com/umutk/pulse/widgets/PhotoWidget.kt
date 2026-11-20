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
    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { File(ctx.filesDir, "photo_widget/$it.src").delete(); prefs(ctx).edit().remove("c$it").remove("r$it").remove("b$it").remove("f$it").apply() }

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

        /** The frames a photo can wear: none (the photo alone on an empty, see-through place, the default), a Polaroid print, wood, gold or a black mat. */
        val FRAMES = listOf("none", "polaroid", "wood", "gold", "mat")

        /** crop: fill its place (true) or fit the whole photo (false); [radius] 0..1 of half the tile; [border] a thin line round it; [frame] one of [FRAMES]. */
        fun render(photo: Bitmap?, crop: Boolean, radius: Float, border: Boolean, frame: String = "none"): Bitmap {
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val S = SIZE.toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // the frame is drawn first, the photo goes into its window
            val win = when (frame) {
                "polaroid" -> { paint.color = 0xFFF7F5EF.toInt(); cv.drawRoundRect(RectF(20f, 10f, S - 20f, S - 10f), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; maskFilter = android.graphics.BlurMaskFilter(14f, android.graphics.BlurMaskFilter.Blur.NORMAL) }); cv.drawRect(RectF(26f, 8f, S - 26f, S - 14f), paint); RectF(56f, 38f, S - 56f, S - 130f) }
                "wood" -> { paint.shader = android.graphics.LinearGradient(0f, 0f, S, S, intArrayOf(0xFF6B3E1E.toInt(), 0xFF9C6236.toInt(), 0xFF5A3218.toInt(), 0xFF8B5A2B.toInt()), null, android.graphics.Shader.TileMode.CLAMP); cv.drawRoundRect(RectF(0f, 0f, S, S), 18f, 18f, paint); paint.shader = null; RectF(54f, 54f, S - 54f, S - 54f) }
                "gold" -> { paint.shader = android.graphics.LinearGradient(0f, 0f, S, S, intArrayOf(0xFFB8860B.toInt(), 0xFFFFE08A.toInt(), 0xFFC9A227.toInt(), 0xFFFFF1B8.toInt(), 0xFFA67C00.toInt()), null, android.graphics.Shader.TileMode.CLAMP); cv.drawRoundRect(RectF(0f, 0f, S, S), 12f, 12f, paint); paint.shader = null; paint.color = 0xFF7A5A00.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; cv.drawRect(RectF(40f, 40f, S - 40f, S - 40f), paint); paint.style = Paint.Style.FILL; RectF(44f, 44f, S - 44f, S - 44f) }
                "mat" -> { paint.color = 0xFF111111.toInt(); cv.drawRoundRect(RectF(0f, 0f, S, S), 8f, 8f, paint); paint.color = 0xFFF2F0EA.toInt(); cv.drawRect(RectF(26f, 26f, S - 26f, S - 26f), paint); RectF(84f, 84f, S - 84f, S - 84f) }
                else -> RectF(0f, 0f, S, S)
            }
            val r = if (frame == "none") radius.coerceIn(0f, 1f) * SIZE / 2f else 0f
            val clip = Path().apply { addRoundRect(win, r, r, Path.Direction.CW) }
            cv.save(); cv.clipPath(clip)
            // with no frame and no photo the place stays empty (see-through); a framed window without a photo is a soft grey
            if (frame != "none" && photo == null) cv.drawColor(0xFF2A2A2A.toInt())
            if (photo != null) {
                val ww = win.width(); val wh = win.height()
                val k = if (crop || frame != "none") maxOf(ww / photo.width, wh / photo.height) else minOf(ww / photo.width, wh / photo.height)
                val w = photo.width * k; val h = photo.height * k
                cv.drawBitmap(photo, null, RectF(win.centerX() - w / 2, win.centerY() - h / 2, win.centerX() + w / 2, win.centerY() + h / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
            cv.restore()
            if (border && frame == "none") cv.drawPath(Path().apply { addRoundRect(RectF(3f, 3f, SIZE - 3f, SIZE - 3f), maxOf(0f, r - 3f), maxOf(0f, r - 3f), Path.Direction.CW) },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0x66FFFFFF })
            return out
        }

        fun build(c: Context, id: Int): RemoteViews {
            val p = prefs(c)
            val photo = runCatching { BitmapFactory.decodeFile(src(c, id).path) }.getOrNull()
            val bm = render(photo, p.getBoolean("c$id", true), p.getFloat("r$id", .18f), p.getBoolean("b$id", false), p.getString("f$id", "none") ?: "none")
            val open = Intent(c, PhotoWidgetConfig::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val tap = PendingIntent.getActivity(c, 7_600 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return RemoteViews(c.packageName, R.layout.widget_photo).apply { setImageViewBitmap(R.id.photo, bm); setOnClickPendingIntent(R.id.root, tap) }
        }

        fun refresh(c: Context, id: Int) { AppWidgetManager.getInstance(c).updateAppWidget(id, build(c, id)) }
        fun provider(c: Context) = ComponentName(c, PhotoWidget::class.java)
    }
}
