package com.eduardo.nunes.drt.features.history

import androidx.lifecycle.ViewModel
import com.eduardo.nunes.drt.core.model.Telemetry
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel(
    appSharedState: AppSharedState
) : ViewModel() {
    val telemetryHistory: StateFlow<List<Telemetry>> = appSharedState.telemetryHistory
}
