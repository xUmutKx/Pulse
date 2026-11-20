package com.umutk.pulse.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.umutk.pulse.extras.ai.AiOcr
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Reads the text of a picture on the phone: ML Kit's bundled Latin model (the Play build), else Pulse AI's vision model when one is chosen. Nothing is sent anywhere. */
object Ocr {
    private val rec by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(c: Context, bmp: Bitmap): String? {
        val kit = suspendCancellableCoroutine<String?> { k ->
            runCatching {
                rec.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { if (k.isActive) k.resume(it.text.takeIf { t -> t.isNotBlank() }) }
                    .addOnFailureListener { if (k.isActive) k.resume(null) }
            }.onFailure { if (k.isActive) k.resume(null) }
        }
        return kit ?: AiOcr.read(c, bmp)
    }
}
