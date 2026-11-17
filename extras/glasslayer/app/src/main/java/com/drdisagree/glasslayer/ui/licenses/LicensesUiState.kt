package com.drdisagree.glasslayer.ui.licenses

import com.drdisagree.glasslayer.domain.model.License

data class LicensesUiState(
    val licenses: List<License> = emptyList()
)
