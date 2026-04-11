package com.eduardo.nunes.drt.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AppMainViewModel(
    private val appSharedState: AppSharedState
) : ViewModel() {

    private val _effect = MutableSharedFlow<AppMainContract.Effect>()
    val effect: SharedFlow<AppMainContract.Effect> = _effect.asSharedFlow()

    fun handleIntent(intent: AppMainContract.Intent) {
        viewModelScope.launch {
            when (intent) {
                is AppMainContract.Intent.NavigateTo -> _effect.emit(AppMainContract.Effect.NavigateTo(intent.route))
                is AppMainContract.Intent.NavigateBack -> _effect.emit(AppMainContract.Effect.NavigateBack)
                is AppMainContract.Intent.ClearLogs -> appSharedState.clearLogs()
            }
        }
    }
}
