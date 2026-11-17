package com.drdisagree.glasslayer.domain.model

data class HookReport(
    val timestamp: Long,
    val framework: String,
    val systemUiVersion: String,
    val buildId: String,
    val moduleUpdatedAt: Long,
    val moduleLoadedAt: Long,
    val moduleApkPath: String,
    val uptimeMs: Long,
    val captureAvailable: Boolean,
    val mods: List<ModReport>,
    val broken: List<String>,
    val errors: List<String>,
    val logs: List<String>,
    val safeMode: Boolean = false
) {
    fun isStale(installedApkPath: String): Boolean = moduleApkPath.isEmpty() || moduleApkPath != installedApkPath
}
