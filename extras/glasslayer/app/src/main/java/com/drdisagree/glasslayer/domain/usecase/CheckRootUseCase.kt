package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.repository.AppStateRepository

class CheckRootUseCase(private val repository: AppStateRepository) {
    suspend operator fun invoke(): Boolean = repository.hasRoot()
}
