package com.drdisagree.glasslayer.xposed.systemui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils

internal object KyantTrackColors {

    private val DARK_OFF = Color.argb(92, 120, 120, 128)
    private val LIGHT_OFF = Color.argb(51, 120, 120, 120)
    private const val DISABLED = 0.5f

    fun off(context: Context?, enabled: Boolean): Int {
        val night = context != null &&
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val color = if (night) DARK_OFF else LIGHT_OFF
        return if (enabled) color else ColorUtils.setAlphaComponent(color, (Color.alpha(color) * DISABLED).toInt())
    }
}
