package com.eduardo.nunes.drt.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardo.nunes.drt.app.AppMainContract.Effect.*
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppMainViewModel(
    private val appSharedState: AppSharedState
) : ViewModel() {

    private val _state = MutableStateFlow(AppMainContract.State())
    val state: StateFlow<AppMainContract.State> = _state.asStateFlow()
    private val _effect = MutableSharedFlow<AppMainContract.Effect>()
    val effect: SharedFlow<AppMainContract.Effect> = _effect.asSharedFlow()

    fun handleIntent(intent: AppMainContract.Intent) {
        viewModelScope.launch {
            when (intent) {
                is AppMainContract.Intent.NavigateTo -> {
                    _effect.emit(NavigateTo(intent.route))
                    changeNavRailVisibility()
                }
                is AppMainContract.Intent.NavigateBack -> _effect.emit(NavigateBack)
                is AppMainContract.Intent.ClearLogs -> appSharedState.clearLogs()
                AppMainContract.Intent.OpenCloseNavRail -> changeNavRailVisibility()
            }
        }
    }

    private fun changeNavRailVisibility() {
        _state.update { value ->
            value.copy(showNavigationRail = !value.showNavigationRail)
        }
    }
}
