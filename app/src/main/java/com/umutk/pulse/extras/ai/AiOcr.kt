package com.umutk.pulse.extras.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Text of a picture read by Pulse AI's vision model (the one picked in the Models tab, when it can see): offline, on the phone. Null when there is no such model. */
object AiOcr {
    suspend fun read(c: Context, bmp: Bitmap): String? = withContext(Dispatchers.Default) {
        runCatching {
            if (!aiSupported || chosenHasVision(c).not()) return@runCatching null
            if (!AiEngine.start(c, "You read text in pictures. Reply with the text that is written in the picture only, exactly as written, one line per line, no comments.")) return@runCatching null
            if (!AiEngine.vision) return@runCatching null
            val small = if (maxOf(bmp.width, bmp.height) > 1280) { val s = 1280f / maxOf(bmp.width, bmp.height); Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) } else bmp
            val out = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            val sb = StringBuilder()
            AiEngine.askPicture(c, "Read all the text in this picture.", out, 512).collect { sb.append(it) }
            sb.toString().trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun chosenHasVision(c: Context) = AiEngine.chosenModel(c)?.projector != null
}
