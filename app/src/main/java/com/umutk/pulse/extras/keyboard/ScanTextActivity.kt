package com.umutk.pulse.extras.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.umutk.pulse.ai.Ocr
import com.umutk.pulse.tr
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Scan text" of the keyboard's menu: the camera opens, the photo's text is read on the phone and put on the clipboard (the keyboard offers it in its bar), then this window closes.
 * The keyboard itself cannot open a camera, so it asks this small window to do it.
 */
class ScanTextActivity : ComponentActivity() {
    private var shot: File? = null
    private val shoot = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = shot
        if (f == null || !ok) { f?.delete(); finish(); return@registerForActivityResult }
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.path, o)
                    var s = 1; while (o.outWidth / s > 2400 || o.outHeight / s > 2400) s *= 2
                    val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
                    val deg = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
                    val up: Bitmap? = if (bmp != null && deg != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true) else bmp
                    up?.let { Ocr.read(this@ScanTextActivity, it) }
                }.getOrNull().also { f.delete() }
            }
            if (text.isNullOrBlank()) Toast.makeText(this@ScanTextActivity, tr("No text found in the photo (without the Play build, Pulse AI needs a vision model)", "Fotoğrafta yazı bulunamadı (Play sürümü dışında Pulse AI'da görüntü modeli gerekir)"), Toast.LENGTH_LONG).show()
            else {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("scan", text.trim()))
                Toast.makeText(this@ScanTextActivity, tr("Text copied: tap it in the keyboard's bar", "Yazı kopyalandı: klavye çubuğundan dokun"), Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = runCatching {
            val f = File(getExternalFilesDir(null) ?: error("no storage"), "scan-text-${System.currentTimeMillis()}.jpg")
            shot = f
            FileProvider.getUriForFile(this, "$packageName.files", f)
        }.getOrNull()
        if (uri == null) { finish(); return }
        try { shoot.launch(uri) } catch (_: Exception) { shot = null; finish() }
    }
}
