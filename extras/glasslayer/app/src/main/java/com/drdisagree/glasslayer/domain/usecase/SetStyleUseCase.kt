package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.GlassSurface
import com.drdisagree.glasslayer.domain.model.StyleParam
import com.drdisagree.glasslayer.domain.repository.SettingsRepository
import kotlin.math.roundToInt

class SetStyleUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(surface: GlassSurface?, param: StyleParam, value: Float?) {
        val clamped = value?.coerceIn(0f, param.max)?.roundToInt()?.toFloat()
        when {
            surface != null -> repository.setStyleOverride(surface, param, clamped)
            clamped != null -> repository.setStyle(param, clamped)
            else -> repository.setStyle(param, repository.defaults.style(param))
        }
    }
}
