package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.domain.repository.SettingsRepository

class SetPerformanceModeUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(mode: PerformanceMode) = repository.setPerformance(mode)
}
