package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.GlassLevel
import com.drdisagree.glasslayer.domain.repository.SettingsRepository
import kotlin.math.roundToInt

class SetLevelUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(level: GlassLevel, value: Float) =
        repository.setLevel(level, value.coerceIn(0f, level.max).roundToInt().toFloat())
}
