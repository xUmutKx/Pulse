package com.drdisagree.glasslayer.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.glasslayer.domain.usecase.CheckRootUseCase
import com.drdisagree.glasslayer.domain.usecase.CompleteOnboardingUseCase
import com.drdisagree.glasslayer.domain.usecase.GetInstalledApkUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveFrameworkUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveHookReportUseCase
import com.drdisagree.glasslayer.domain.usecase.RequestHookReportUseCase
import com.drdisagree.glasslayer.domain.usecase.RestartSystemUiUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    observeFramework: ObserveFrameworkUseCase,
    observeHookReport: ObserveHookReportUseCase,
    private val getInstalledApk: GetInstalledApkUseCase,
    private val requestHookReport: RequestHookReportUseCase,
    private val restartSystemUi: RestartSystemUiUseCase,
    private val checkRoot: CheckRootUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase
) : ViewModel() {

    private val root = MutableStateFlow(RootState.UNKNOWN)
    private val restarting = MutableStateFlow(false)

    val uiState: StateFlow<OnboardingUiState> = combine(
        observeFramework(),
        observeHookReport(),
        root,
        restarting
    ) { framework, report, rootState, isRestarting ->
        OnboardingUiState(framework, report, rootState, isRestarting, getInstalledApk())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        OnboardingUiState(installedApkPath = getInstalledApk())
    )

    fun onResume() {
        requestHookReport()
    }

    fun onGrantRoot() {
        if (root.value == RootState.CHECKING) return
        root.value = RootState.CHECKING
        viewModelScope.launch {
            root.value = if (checkRoot()) RootState.GRANTED else RootState.DENIED
        }
    }

    fun onRestart() {
        if (restarting.value) return
        restarting.value = true
        viewModelScope.launch {
            val restarted = restartSystemUi()
            if (restarted) root.value = RootState.GRANTED
            if (restarted) {
                delay(RESTART_SETTLE_MS)
                requestHookReport()
            }
            restarting.value = false
        }
    }

    fun onContinue() {
        completeOnboarding()
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
private const val RESTART_SETTLE_MS = 6_000L
