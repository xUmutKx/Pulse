package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.FrameworkInfo
import com.drdisagree.glasslayer.domain.repository.StatusRepository
import kotlinx.coroutines.flow.Flow

class ObserveFrameworkUseCase(private val repository: StatusRepository) {
    operator fun invoke(): Flow<FrameworkInfo?> = repository.framework
}
