package com.umutk.pulse.ai

import android.graphics.Bitmap

/** The FOSS build carries no text-reading model (the Play build has one): reading a picture gives nothing here. */
object Ocr {
    const val available = false
    @Suppress("UNUSED_PARAMETER")
    suspend fun read(bmp: Bitmap): String? = null
}
