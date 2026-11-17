package com.drdisagree.glasslayer.ui.onboarding

import com.drdisagree.glasslayer.domain.model.FrameworkInfo
import com.drdisagree.glasslayer.domain.model.HookReport

data class OnboardingUiState(
    val framework: FrameworkInfo? = null,
    val report: HookReport? = null,
    val root: RootState = RootState.UNKNOWN,
    val restarting: Boolean = false,
    val installedApkPath: String = ""
) {
    val isScoped: Boolean
        get() = framework?.scope?.contains(SYSTEMUI) == true

    val isLoaded: Boolean
        get() = report != null && !report.isStale(installedApkPath)

    val isReady: Boolean
        get() = framework != null && isScoped && isLoaded
}

private const val SYSTEMUI = "com.android.systemui"
