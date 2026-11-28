package com.umutk.pulse.ai

import android.content.Context
import android.graphics.Bitmap

/** The photo scan of the calculator needs Pulse's text reader: not in a lean app (the scan answers "no sum found"). */
object Ocr { fun read(@Suppress("UNUSED_PARAMETER") c: Context, @Suppress("UNUSED_PARAMETER") b: Bitmap): String? = null }

object OcrMath { fun extract(@Suppress("UNUSED_PARAMETER") text: String): String? = null }
