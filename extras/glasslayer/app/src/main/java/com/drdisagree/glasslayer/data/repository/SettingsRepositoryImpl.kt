package com.drdisagree.glasslayer.data.repository

import com.drdisagree.glasslayer.data.mapper.GlassPrefMapper
import com.drdisagree.glasslayer.data.source.LocalPrefsSource
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.data.source.XposedServiceSource
import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.GlassLevel
import com.drdisagree.glasslayer.domain.model.GlassSettings
import com.drdisagree.glasslayer.domain.model.GlassSurface
import com.drdisagree.glasslayer.domain.model.GlassToggle
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.domain.model.StyleParam
import com.drdisagree.glasslayer.domain.model.BatterySaverBackground
import com.drdisagree.glasslayer.domain.model.ThermalAction
import com.drdisagree.glasslayer.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val local: LocalPrefsSource,
    private val xposed: XposedServiceSource
) : SettingsRepository {

    private val keys: Set<String> = buildSet {
        GlassToggle.entries.forEach { add(GlassPrefMapper.key(it)) }
        GlassLevel.entries.forEach { add(GlassPrefMapper.key(it)) }
        add(PrefKeys.PERFORMANCE_MODE)
        add(PrefKeys.BATTERY_SAVER_ACTION)
        add(PrefKeys.THERMAL_ACTION)
        add(PrefKeys.BATTERY_SAVER_BACKGROUND)
        StyleParam.entries.forEach { param ->
            add(GlassPrefMapper.key(param))
            GlassSurface.entries.forEach { add(GlassPrefMapper.key(it, param)) }
        }
    }

    init {
        xposed.addBindListener(::pushAll)
    }

    override val settings: Flow<GlassSettings> = local.changes { it in keys }
        .map { read() }
        .flowOn(Dispatchers.IO)

    override val defaults: GlassSettings = GlassSettings(
        toggles = GlassToggle.entries.associateWith(GlassPrefMapper::default),
        levels = GlassLevel.entries.associateWith(GlassPrefMapper::default),
        style = StyleParam.entries.associateWith(GlassPrefMapper::default)
    )

    override suspend fun setToggle(toggle: GlassToggle, enabled: Boolean) = withContext(Dispatchers.IO) {
        val key = GlassPrefMapper.key(toggle)
        local.putBoolean(key, enabled)
        xposed.edit { it.putBoolean(key, enabled) }
    }

    override suspend fun setLevel(level: GlassLevel, value: Float) = putFloat(GlassPrefMapper.key(level), value)

    override suspend fun setStyle(param: StyleParam, value: Float) = putFloat(GlassPrefMapper.key(param), value)

    override suspend fun setStyleOverride(surface: GlassSurface, param: StyleParam, value: Float?) =
        putFloat(GlassPrefMapper.key(surface, param), value ?: PrefKeys.INHERIT)

    override suspend fun setPerformance(mode: PerformanceMode) = putFloat(PrefKeys.PERFORMANCE_MODE, mode.ordinal.toFloat())

    override suspend fun setBatterySaverAction(action: BatterySaverAction) =
        putFloat(PrefKeys.BATTERY_SAVER_ACTION, action.code.toFloat())

    override suspend fun setThermalAction(action: ThermalAction) =
        putFloat(PrefKeys.THERMAL_ACTION, action.ordinal.toFloat())

    override suspend fun setBatterySaverBackground(background: BatterySaverBackground) =
        putFloat(PrefKeys.BATTERY_SAVER_BACKGROUND, background.ordinal.toFloat())

    private suspend fun putFloat(key: String, value: Float) = withContext(Dispatchers.IO) {
        local.putFloat(key, value)
        xposed.edit { it.putFloat(key, value) }
    }

    private fun read(): GlassSettings = GlassSettings(
        toggles = GlassToggle.entries.associateWith { local.boolean(GlassPrefMapper.key(it), GlassPrefMapper.default(it)) },
        levels = GlassLevel.entries.associateWith { local.float(GlassPrefMapper.key(it), GlassPrefMapper.default(it)) },
        style = StyleParam.entries.associateWith { local.float(GlassPrefMapper.key(it), GlassPrefMapper.default(it)) },
        overrides = GlassSurface.entries.associateWith { surface ->
            StyleParam.entries
                .associateWith { local.float(GlassPrefMapper.key(surface, it), PrefKeys.INHERIT) }
                .filterValues { it >= 0f }
        }.filterValues { it.isNotEmpty() },
        performance = PerformanceMode.entries.getOrElse(local.float(PrefKeys.PERFORMANCE_MODE, 1f).toInt()) { PerformanceMode.FULL },
        batterySaver = local.float(PrefKeys.BATTERY_SAVER_ACTION, BatterySaverAction.ULTRA.code.toFloat()).toInt()
            .let { code -> BatterySaverAction.entries.firstOrNull { it.code == code } } ?: BatterySaverAction.ULTRA,
        thermal = ThermalAction.entries.getOrElse(
            local.float(PrefKeys.THERMAL_ACTION, ThermalAction.SAVER.ordinal.toFloat()).toInt()
        ) { ThermalAction.SAVER },
        batterySaverBackground = BatterySaverBackground.entries.getOrElse(
            local.float(PrefKeys.BATTERY_SAVER_BACKGROUND, BatterySaverBackground.SEMI_TRANSPARENT.ordinal.toFloat()).toInt()
        ) { BatterySaverBackground.SEMI_TRANSPARENT }
    )

    private fun pushAll() {
        val current = read()
        xposed.edit { editor ->
            current.toggles.forEach { (toggle, enabled) -> editor.putBoolean(GlassPrefMapper.key(toggle), enabled) }
            current.levels.forEach { (level, value) -> editor.putFloat(GlassPrefMapper.key(level), value) }
            current.style.forEach { (param, value) -> editor.putFloat(GlassPrefMapper.key(param), value) }
            editor.putFloat(PrefKeys.PERFORMANCE_MODE, current.performance.ordinal.toFloat())
            editor.putFloat(PrefKeys.BATTERY_SAVER_ACTION, current.batterySaver.code.toFloat())
            editor.putFloat(PrefKeys.THERMAL_ACTION, current.thermal.ordinal.toFloat())
            editor.putFloat(PrefKeys.BATTERY_SAVER_BACKGROUND, current.batterySaverBackground.ordinal.toFloat())
            GlassSurface.entries.forEach { surface ->
                StyleParam.entries.forEach { param ->
                    editor.putFloat(
                        GlassPrefMapper.key(surface, param),
                        current.override(surface, param) ?: PrefKeys.INHERIT
                    )
                }
            }
        }
    }
}
