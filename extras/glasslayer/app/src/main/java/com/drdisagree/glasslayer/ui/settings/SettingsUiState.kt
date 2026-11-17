package com.drdisagree.glasslayer.ui.settings

import com.drdisagree.glasslayer.domain.model.GlassSettings
import com.drdisagree.glasslayer.domain.model.ModuleStatus

data class SettingsUiState(
    val settings: GlassSettings = GlassSettings(),
    val defaults: GlassSettings = GlassSettings(),
    val status: ModuleStatus = ModuleStatus(),
    val restart: RestartState = RestartState.IDLE,
    val loaded: Boolean = false
)
