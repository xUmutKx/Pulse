package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.repository.StatusRepository

class GetInstallTimeUseCase(private val repository: StatusRepository) {
    operator fun invoke(): Long = repository.installedAt()
}
