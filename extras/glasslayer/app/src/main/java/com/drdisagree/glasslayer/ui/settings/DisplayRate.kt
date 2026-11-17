package com.drdisagree.glasslayer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import kotlin.math.roundToInt

@Composable
internal fun rememberDisplayRate(): Int {
    val context = LocalContext.current
    return remember(context) {
        try {
            val display = context.display
            val current = display.mode
            display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxOfOrNull { it.refreshRate }
                ?.roundToInt()
                ?: display.refreshRate.roundToInt()
        } catch (_: Throwable) {
            FALLBACK_RATE
        }.coerceAtLeast(FALLBACK_RATE)
    }
}

internal fun PerformanceMode.fps(displayRate: Int): Int = if (maxFps <= 0) displayRate else minOf(maxFps, displayRate)

private const val FALLBACK_RATE = 60
