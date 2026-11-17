package com.drdisagree.glasslayer.domain.repository

interface AppStateRepository {
    fun isOnboardingDone(): Boolean
    fun completeOnboarding()
    suspend fun hasRoot(): Boolean
}
