package com.umutk.pulse.features

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap

/** The bitmap icon packs of Pulse (Windows 11 inside the app, 95 / XP / 7 / One UI / ColorOS as downloads) are not in a lean app: the look draws its vector icons. */
object ThemeIcons {
    fun bitmap(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") themeId: String, @Suppress("UNUSED_PARAMETER") key: String): ImageBitmap? = null
}

/** Pulse's wallpaper backdrop (Windows 11 blur): a lean app has none, the look draws its own material. */
object Backdrop {
    val mode: String get() = "off"
    val image: ImageBitmap? get() = null
}

object BlurBackdrop {
    fun blurredWallpaper(@Suppress("UNUSED_PARAMETER") ctx: Context, @Suppress("UNUSED_PARAMETER") blurDp: Float, @Suppress("UNUSED_PARAMETER") tint: Int): android.graphics.Bitmap =
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
}
