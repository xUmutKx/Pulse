package com.drdisagree.glasslayer.domain.usecase

import com.drdisagree.glasslayer.domain.model.ModuleStatus
import com.drdisagree.glasslayer.domain.repository.StatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveModuleStatusUseCase(private val repository: StatusRepository) {
    operator fun invoke(): Flow<ModuleStatus> = combine(repository.framework, repository.hookReport) { framework, report ->
        val active = framework != null
        ModuleStatus(
            frameworkActive = active,
            restartNeeded = active && (report == null || report.isStale(repository.installedApkPath())),
            safeMode = active && report?.safeMode == true
        )
    }
}
