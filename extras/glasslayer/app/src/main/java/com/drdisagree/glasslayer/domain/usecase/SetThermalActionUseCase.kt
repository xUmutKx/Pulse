package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.ThermalAction
import com.drdisagree.glasslayer.domain.repository.SettingsRepository

class SetThermalActionUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(action: ThermalAction) = repository.setThermalAction(action)
}
