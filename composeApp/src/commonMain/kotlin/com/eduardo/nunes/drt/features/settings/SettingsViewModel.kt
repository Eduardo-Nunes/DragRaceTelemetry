package com.eduardo.nunes.drt.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsContract.State())
    val state: StateFlow<SettingsContract.State> = _state.asStateFlow()

    init {
        // Observa a fonte da verdade global
        viewModelScope.launch {
            AppSharedState.showTerminalLogs.collect { show ->
                _state.update { it.copy(showTerminalLogs = show) }
            }
        }
    }

    fun handleIntent(intent: SettingsContract.Intent) {
        when (intent) {
            is SettingsContract.Intent.ToggleTerminalLogs -> {
                // Atualiza o objeto compartilhado. Todos os ViewModels que o observam reagirão.
                AppSharedState.showTerminalLogs.value = intent.show
            }
        }
    }
}
