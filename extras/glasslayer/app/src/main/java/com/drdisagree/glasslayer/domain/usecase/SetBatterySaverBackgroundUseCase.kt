package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.BatterySaverBackground
import com.drdisagree.glasslayer.domain.repository.SettingsRepository

class SetBatterySaverBackgroundUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(background: BatterySaverBackground) = repository.setBatterySaverBackground(background)
}
