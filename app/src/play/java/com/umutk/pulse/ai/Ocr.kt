package com.umutk.pulse.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Reads the text of a picture on the phone (ML Kit's bundled Latin model; nothing is sent anywhere). The Play build only; the FOSS build has a stand-in that reads nothing. */
object Ocr {
    const val available = true
    private val rec by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bmp: Bitmap): String? = suspendCancellableCoroutine { c ->
        runCatching {
            rec.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { if (c.isActive) c.resume(it.text.takeIf { t -> t.isNotBlank() }) }
                .addOnFailureListener { if (c.isActive) c.resume(null) }
        }.onFailure { if (c.isActive) c.resume(null) }
    }
}
