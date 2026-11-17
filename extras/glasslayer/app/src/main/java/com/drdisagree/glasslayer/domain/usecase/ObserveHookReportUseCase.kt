package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.HookReport
import com.drdisagree.glasslayer.domain.repository.StatusRepository
import kotlinx.coroutines.flow.Flow

class ObserveHookReportUseCase(private val repository: StatusRepository) {
    operator fun invoke(): Flow<HookReport?> = repository.hookReport
}
