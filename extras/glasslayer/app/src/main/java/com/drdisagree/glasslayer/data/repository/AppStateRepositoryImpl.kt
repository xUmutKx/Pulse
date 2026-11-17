package com.drdisagree.glasslayer.data.repository

import com.drdisagree.glasslayer.data.source.LocalPrefsSource
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.data.source.RootShell
import com.drdisagree.glasslayer.domain.repository.AppStateRepository

class AppStateRepositoryImpl(private val local: LocalPrefsSource) : AppStateRepository {

    override fun isOnboardingDone(): Boolean = local.boolean(PrefKeys.ONBOARDING_DONE, false)

    override fun completeOnboarding() {
        local.putBoolean(PrefKeys.ONBOARDING_DONE, true)
    }

    override suspend fun hasRoot(): Boolean = RootShell.run(ROOT_PROBE)
}

private const val ROOT_PROBE = "true"
