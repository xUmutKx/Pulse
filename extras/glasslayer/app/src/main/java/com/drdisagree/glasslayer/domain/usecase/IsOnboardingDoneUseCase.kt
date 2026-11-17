package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.repository.AppStateRepository

class IsOnboardingDoneUseCase(private val repository: AppStateRepository) {
    operator fun invoke(): Boolean = repository.isOnboardingDone()
}
