package com.eduardo.nunes.drt.core.state

import com.eduardo.nunes.drt.core.model.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Object global para compartilhar estados comuns entre ViewModels independentes,
 * garantindo a Single Source of Truth em nível de aplicação.
 */
object AppSharedState {
    val showTerminalLogs = MutableStateFlow(false)

    private val _telemetryHistory = MutableStateFlow<List<Telemetry>>(emptyList())
    val telemetryHistory = _telemetryHistory.asStateFlow()

    fun addTelemetry(telemetry: Telemetry) {
        _telemetryHistory.update { it + telemetry }
    }
}
