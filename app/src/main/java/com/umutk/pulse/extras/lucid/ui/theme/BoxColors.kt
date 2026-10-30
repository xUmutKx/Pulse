package com.umutk.pulse.extras.lucid.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class BoxColors(
    val settingsCard: Color? = null,
    val alarmCard: Color? = null,
    val worldClockCard: Color? = null
)

val LocalBoxColors = staticCompositionLocalOf { BoxColors() }
