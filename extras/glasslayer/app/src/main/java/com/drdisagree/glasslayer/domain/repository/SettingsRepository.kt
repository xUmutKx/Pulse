package com.drdisagree.glasslayer.domain.repository

import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.GlassLevel
import com.drdisagree.glasslayer.domain.model.GlassSettings
import com.drdisagree.glasslayer.domain.model.GlassSurface
import com.drdisagree.glasslayer.domain.model.GlassToggle
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.domain.model.StyleParam
import com.drdisagree.glasslayer.domain.model.BatterySaverBackground
import com.drdisagree.glasslayer.domain.model.ThermalAction
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<GlassSettings>
    val defaults: GlassSettings
    suspend fun setToggle(toggle: GlassToggle, enabled: Boolean)
    suspend fun setLevel(level: GlassLevel, value: Float)
    suspend fun setStyle(param: StyleParam, value: Float)
    suspend fun setStyleOverride(surface: GlassSurface, param: StyleParam, value: Float?)
    suspend fun setPerformance(mode: PerformanceMode)
    suspend fun setBatterySaverAction(action: BatterySaverAction)
    suspend fun setThermalAction(action: ThermalAction)
    suspend fun setBatterySaverBackground(background: BatterySaverBackground)
}
