package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.GlassToggle
import com.drdisagree.glasslayer.domain.repository.SettingsRepository

class SetToggleUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(toggle: GlassToggle, enabled: Boolean) = repository.setToggle(toggle, enabled)
}
