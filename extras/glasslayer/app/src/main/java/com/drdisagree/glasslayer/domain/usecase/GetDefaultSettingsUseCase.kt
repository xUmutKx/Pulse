package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.GlassSettings
import com.drdisagree.glasslayer.domain.repository.SettingsRepository

class GetDefaultSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): GlassSettings = repository.defaults
}
