package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.repository.StatusRepository

class RequestHookReportUseCase(private val repository: StatusRepository) {
    operator fun invoke() = repository.requestHookReport()
}
