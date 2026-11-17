package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.repository.SettingsRepository

class SetBatterySaverActionUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(action: BatterySaverAction) = repository.setBatterySaverAction(action)
}
