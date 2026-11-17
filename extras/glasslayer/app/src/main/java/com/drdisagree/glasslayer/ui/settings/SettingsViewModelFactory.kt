package com.drdisagree.glasslayer.ui.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drdisagree.glasslayer.di.AppContainer

fun settingsViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        SettingsViewModel(
            observeSettings = container.observeSettings,
            getDefaultSettings = container.getDefaultSettings,
            observeModuleStatus = container.observeModuleStatus,
            requestHookReport = container.requestHookReport,
            setToggle = container.setToggle,
            setLevel = container.setLevel,
            setStyle = container.setStyle,
            setPerformanceMode = container.setPerformanceMode,
            setBatterySaverAction = container.setBatterySaverAction,
            setThermalAction = container.setThermalAction,
            setBatterySaverBackground = container.setBatterySaverBackground,
            restartSystemUi = container.restartSystemUi
        )
    }
}
