package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.License
import com.drdisagree.glasslayer.domain.repository.LicenseRepository

class GetLicensesUseCase(private val repository: LicenseRepository) {
    suspend operator fun invoke(): List<License> = repository.licenses()
}
