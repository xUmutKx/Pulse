package com.umutk.pulse.ai

import android.content.Context
import android.graphics.Bitmap
import com.umutk.pulse.extras.ai.AiOcr

/** The FOSS build has no text-reading model of its own: Pulse AI's vision model (the full build, when one is chosen in the Models tab) reads the picture instead. */
object Ocr {
    suspend fun read(c: Context, bmp: Bitmap): String? = AiOcr.read(c, bmp)
}
