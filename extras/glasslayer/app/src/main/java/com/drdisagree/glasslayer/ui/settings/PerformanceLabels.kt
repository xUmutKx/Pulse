package com.drdisagree.glasslayer.ui.settings

import androidx.annotation.StringRes
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.domain.model.BatterySaverBackground
import com.drdisagree.glasslayer.domain.model.ThermalAction

@StringRes
internal fun modeTitle(mode: PerformanceMode): Int = when (mode) {
    PerformanceMode.FULL -> R.string.performance_full
    PerformanceMode.BALANCED -> R.string.performance_balanced
    PerformanceMode.SAVER -> R.string.performance_saver
    PerformanceMode.ULTRA -> R.string.performance_ultra
}

@StringRes
internal fun modeSummary(mode: PerformanceMode): Int = when (mode) {
    PerformanceMode.FULL -> R.string.performance_full_summary
    PerformanceMode.BALANCED -> R.string.performance_balanced_summary
    PerformanceMode.SAVER -> R.string.performance_saver_summary
    PerformanceMode.ULTRA -> R.string.performance_ultra_summary
}

@StringRes
internal fun saverTitle(action: BatterySaverAction): Int = when (action) {
    BatterySaverAction.NONE -> R.string.saver_none
    BatterySaverAction.BALANCED -> R.string.performance_balanced
    BatterySaverAction.SAVER -> R.string.performance_saver
    BatterySaverAction.ULTRA -> R.string.saver_ultra
    BatterySaverAction.PAUSE -> R.string.saver_pause
}

@StringRes
internal fun saverSummary(action: BatterySaverAction): Int = when (action) {
    BatterySaverAction.NONE -> R.string.saver_none_summary
    BatterySaverAction.BALANCED -> R.string.saver_balanced_summary
    BatterySaverAction.SAVER -> R.string.saver_saver_summary
    BatterySaverAction.ULTRA -> R.string.saver_ultra_summary
    BatterySaverAction.PAUSE -> R.string.saver_pause_summary
}

@StringRes
internal fun thermalTitle(action: ThermalAction): Int = when (action) {
    ThermalAction.NONE -> R.string.thermal_none
    ThermalAction.BALANCED -> R.string.performance_balanced
    ThermalAction.SAVER -> R.string.performance_saver
    ThermalAction.ULTRA -> R.string.performance_ultra
    ThermalAction.PAUSE -> R.string.thermal_pause
}

@StringRes
internal fun thermalSummary(action: ThermalAction): Int = when (action) {
    ThermalAction.NONE -> R.string.thermal_none_summary
    ThermalAction.BALANCED -> R.string.thermal_balanced_summary
    ThermalAction.SAVER -> R.string.thermal_saver_summary
    ThermalAction.ULTRA -> R.string.thermal_ultra_summary
    ThermalAction.PAUSE -> R.string.thermal_pause_summary
}

@StringRes
internal fun saverBackgroundTitle(background: BatterySaverBackground): Int = when (background) {
    BatterySaverBackground.KEEP_TINT -> R.string.saver_background_keep
    BatterySaverBackground.SEMI_TRANSPARENT -> R.string.saver_background_semi
    BatterySaverBackground.STOCK -> R.string.saver_background_stock
}

@StringRes
internal fun saverBackgroundSummary(background: BatterySaverBackground): Int = when (background) {
    BatterySaverBackground.KEEP_TINT -> R.string.saver_background_keep_summary
    BatterySaverBackground.SEMI_TRANSPARENT -> R.string.saver_background_semi_summary
    BatterySaverBackground.STOCK -> R.string.saver_background_stock_summary
}
