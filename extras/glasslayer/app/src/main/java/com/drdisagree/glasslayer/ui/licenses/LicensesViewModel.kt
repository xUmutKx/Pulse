package com.drdisagree.glasslayer.ui.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.glasslayer.domain.usecase.GetLicensesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LicensesViewModel(getLicenses: GetLicensesUseCase) : ViewModel() {

    private val state = MutableStateFlow(LicensesUiState())
    val uiState: StateFlow<LicensesUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = LicensesUiState(getLicenses()) }
    }
}
