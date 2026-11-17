package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.repository.StatusRepository

class GetInstalledApkUseCase(private val repository: StatusRepository) {
    operator fun invoke(): String = repository.installedApkPath()
}
