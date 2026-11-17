package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.repository.StatusRepository

class RestartSystemUiUseCase(private val repository: StatusRepository) {
    suspend operator fun invoke(): Boolean = repository.restartSystemUi()
}
