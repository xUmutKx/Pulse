package com.drdisagree.glasslayer.ui.status

import com.drdisagree.glasslayer.domain.model.FrameworkInfo
import com.drdisagree.glasslayer.domain.model.HookReport

data class StatusUiState(
    val framework: FrameworkInfo? = null,
    val report: HookReport? = null,
    val restarting: Boolean = false,
    val refresh: RefreshState = RefreshState.IDLE,
    val save: SaveState = SaveState.IDLE,
    val installedAt: Long = 0L,
    val installedApkPath: String = ""
) {
    val isScoped: Boolean
        get() = framework?.scope?.contains(SYSTEMUI) == true

    val isStale: Boolean
        get() = report?.isStale(installedApkPath) != false
}

private const val SYSTEMUI = "com.android.systemui"
