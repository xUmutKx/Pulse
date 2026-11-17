package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.repository.AppStateRepository

class CompleteOnboardingUseCase(private val repository: AppStateRepository) {
    operator fun invoke() = repository.completeOnboarding()
}
