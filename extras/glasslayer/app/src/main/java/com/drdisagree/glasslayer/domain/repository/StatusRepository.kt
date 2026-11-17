package com.drdisagree.glasslayer.domain.repository

import com.drdisagree.glasslayer.domain.model.FrameworkInfo
import com.drdisagree.glasslayer.domain.model.HookReport
import com.drdisagree.glasslayer.domain.model.ReportLog
import kotlinx.coroutines.flow.Flow

interface StatusRepository {
    val framework: Flow<FrameworkInfo?>
    val hookReport: Flow<HookReport?>
    fun installedAt(): Long
    fun installedApkPath(): String
    fun requestHookReport()
    suspend fun restartSystemUi(): Boolean
    suspend fun systemLogs(): Map<ReportLog, String>
    suspend fun saveReport(target: String, files: Map<String, String>): Boolean
}
