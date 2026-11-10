package com.umutk.pulse.extras.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** A picture for the vision model: its bytes as a JPEG no larger than 768 pixels (the model shrinks it anyway, so this is quicker). */
internal fun pictureFrom(c: Context, u: Uri): Pair<String, ByteArray>? = runCatching {
    val name = c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "picture"
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    c.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 768) sample *= 2
    val bmp = c.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
    bmp.recycle()
    name to out.toByteArray()
}.getOrNull()

