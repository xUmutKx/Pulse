package com.drdisagree.glasslayer.di

import android.content.Context
import com.drdisagree.glasslayer.data.repository.AppStateRepositoryImpl
import com.drdisagree.glasslayer.data.repository.LicenseRepositoryImpl
import com.drdisagree.glasslayer.data.repository.SettingsRepositoryImpl
import com.drdisagree.glasslayer.data.repository.StatusRepositoryImpl
import com.drdisagree.glasslayer.data.source.HookReportSource
import com.drdisagree.glasslayer.data.source.LocalPrefsSource
import com.drdisagree.glasslayer.data.source.ReportFileSource
import com.drdisagree.glasslayer.data.source.XposedServiceSource
import com.drdisagree.glasslayer.domain.repository.AppStateRepository
import com.drdisagree.glasslayer.domain.repository.LicenseRepository
import com.drdisagree.glasslayer.domain.repository.SettingsRepository
import com.drdisagree.glasslayer.domain.repository.StatusRepository
import com.drdisagree.glasslayer.domain.usecase.CheckRootUseCase
import com.drdisagree.glasslayer.domain.usecase.CompleteOnboardingUseCase
import com.drdisagree.glasslayer.domain.usecase.GetDefaultSettingsUseCase
import com.drdisagree.glasslayer.domain.usecase.GetInstallTimeUseCase
import com.drdisagree.glasslayer.domain.usecase.GetInstalledApkUseCase
import com.drdisagree.glasslayer.domain.usecase.GetLicensesUseCase
import com.drdisagree.glasslayer.domain.usecase.IsOnboardingDoneUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveFrameworkUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveHookReportUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveModuleStatusUseCase
import com.drdisagree.glasslayer.domain.usecase.ObserveSettingsUseCase
import com.drdisagree.glasslayer.domain.usecase.RequestHookReportUseCase
import com.drdisagree.glasslayer.domain.usecase.RestartSystemUiUseCase
import com.drdisagree.glasslayer.domain.usecase.SaveReportUseCase
import com.drdisagree.glasslayer.domain.usecase.SetBatterySaverActionUseCase
import com.drdisagree.glasslayer.domain.usecase.SetLevelUseCase
import com.drdisagree.glasslayer.domain.usecase.SetPerformanceModeUseCase
import com.drdisagree.glasslayer.domain.usecase.SetStyleUseCase
import com.drdisagree.glasslayer.domain.usecase.SetBatterySaverBackgroundUseCase
import com.drdisagree.glasslayer.domain.usecase.SetThermalActionUseCase
import com.drdisagree.glasslayer.domain.usecase.SetToggleUseCase

class AppContainer(context: Context) {

    private val app = context.applicationContext
    private val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(LocalPrefsSource(app), XposedServiceSource)
    private val statusRepository: StatusRepository =
        StatusRepositoryImpl(XposedServiceSource, HookReportSource(app), ReportFileSource(app))
    private val licenseRepository: LicenseRepository = LicenseRepositoryImpl(app)
    private val appStateRepository: AppStateRepository = AppStateRepositoryImpl(LocalPrefsSource(app))

    val observeSettings = ObserveSettingsUseCase(settingsRepository)
    val getDefaultSettings = GetDefaultSettingsUseCase(settingsRepository)
    val setToggle = SetToggleUseCase(settingsRepository)
    val setLevel = SetLevelUseCase(settingsRepository)
    val setStyle = SetStyleUseCase(settingsRepository)
    val setPerformanceMode = SetPerformanceModeUseCase(settingsRepository)
    val setBatterySaverAction = SetBatterySaverActionUseCase(settingsRepository)
    val setThermalAction = SetThermalActionUseCase(settingsRepository)
    val setBatterySaverBackground = SetBatterySaverBackgroundUseCase(settingsRepository)
    val observeModuleStatus = ObserveModuleStatusUseCase(statusRepository)
    val observeFramework = ObserveFrameworkUseCase(statusRepository)
    val observeHookReport = ObserveHookReportUseCase(statusRepository)
    val getInstallTime = GetInstallTimeUseCase(statusRepository)
    val requestHookReport = RequestHookReportUseCase(statusRepository)
    val restartSystemUi = RestartSystemUiUseCase(statusRepository)
    val getInstalledApk = GetInstalledApkUseCase(statusRepository)
    val saveReport = SaveReportUseCase(statusRepository)
    val getLicenses = GetLicensesUseCase(licenseRepository)
    val isOnboardingDone = IsOnboardingDoneUseCase(appStateRepository)
    val completeOnboarding = CompleteOnboardingUseCase(appStateRepository)
    val checkRoot = CheckRootUseCase(appStateRepository)
}
