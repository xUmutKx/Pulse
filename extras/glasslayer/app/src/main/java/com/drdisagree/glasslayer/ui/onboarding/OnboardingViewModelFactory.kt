package com.drdisagree.glasslayer.ui.onboarding

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drdisagree.glasslayer.di.AppContainer

fun onboardingViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        OnboardingViewModel(
            observeFramework = container.observeFramework,
            observeHookReport = container.observeHookReport,
            getInstalledApk = container.getInstalledApk,
            requestHookReport = container.requestHookReport,
            restartSystemUi = container.restartSystemUi,
            checkRoot = container.checkRoot,
            completeOnboarding = container.completeOnboarding
        )
    }
}
