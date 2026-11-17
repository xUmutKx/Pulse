package com.drdisagree.glasslayer.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.glasslayer.domain.usecase.GetInstallTimeUseCase
import com.drdisagree.glasslayer.domain.usecase.GetInstalledApkUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveFrameworkUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveHookReportUseCase
import com.drdisagree.glasslayer.domain.usecase.RequestHookReportUseCase
import com.drdisagree.glasslayer.domain.usecase.RestartSystemUiUseCase
import com.drdisagree.glasslayer.domain.usecase.SaveReportUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class StatusViewModel(
    observeFramework: ObserveFrameworkUseCase,
    private val observeHookReport: ObserveHookReportUseCase,
    private val getInstallTime: GetInstallTimeUseCase,
    private val requestHookReport: RequestHookReportUseCase,
    private val restartSystemUi: RestartSystemUiUseCase,
    private val saveReport: SaveReportUseCase,
    private val getInstalledApk: GetInstalledApkUseCase
) : ViewModel() {

    private val restarting = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState.IDLE)
    private val saveState = MutableStateFlow(SaveState.IDLE)
    private var refreshJob: Job? = null

    val uiState: StateFlow<StatusUiState> = combine(
        observeFramework(),
        observeHookReport(),
        restarting,
        refreshState,
        saveState
    ) { framework, report, isRestarting, refresh, save ->
        StatusUiState(framework, report, isRestarting, refresh, save, getInstallTime(), getInstalledApk())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT),
        StatusUiState(installedAt = getInstallTime(), installedApkPath = getInstalledApk())
    )

    fun refresh() {
        refreshJob?.cancel()
        refreshState.value = RefreshState.CHECKING
        refreshJob = viewModelScope.launch {
            val requestedAt = System.currentTimeMillis()
            val reply = withTimeoutOrNull(RESPONSE_TIMEOUT) {
                observeHookReport()
                    .onStart { requestHookReport() }
                    .first { it != null && it.timestamp >= requestedAt - CLOCK_SLACK }
            }
            delay(MIN_CHECKING)
            refreshState.value = if (reply != null) RefreshState.UPDATED else RefreshState.NO_RESPONSE
            delay(if (reply != null) UPDATED_HOLD else NO_RESPONSE_HOLD)
            refreshState.value = RefreshState.IDLE
        }
    }

    fun onSaveReport(target: String, report: String) {
        if (saveState.value == SaveState.SAVING) return
        saveState.value = SaveState.SAVING
        viewModelScope.launch {
            val saved = saveReport(target, report)
            saveState.value = if (saved) SaveState.SAVED else SaveState.FAILED
            delay(SAVE_HOLD)
            saveState.value = SaveState.IDLE
        }
    }

    fun onRestartSystemUi() {
        if (restarting.value) return
        restarting.value = true
        viewModelScope.launch {
            if (restartSystemUi()) {
                delay(RESTART_SETTLE)
                refresh()
            }
            restarting.value = false
        }
    }
}

private const val STOP_TIMEOUT = 5_000L
private const val RESTART_SETTLE = 6_000L
private const val RESPONSE_TIMEOUT = 4_000L
private const val CLOCK_SLACK = 1_000L
private const val MIN_CHECKING = 450L
private const val UPDATED_HOLD = 1_500L
private const val NO_RESPONSE_HOLD = 2_500L
private const val SAVE_HOLD = 2_000L
