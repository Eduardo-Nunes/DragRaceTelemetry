package com.eduardo.nunes.drt.core.state

import com.eduardo.nunes.drt.core.model.Telemetry
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Object global para compartilhar estados comuns entre ViewModels independentes,
 * garantindo a Single Source of Truth em nível de aplicação.
 */
class AppSharedState {
    val showTerminalLogs = MutableStateFlow(false)

    private val _telemetryHistory = MutableStateFlow<List<Telemetry>>(emptyList())
    val telemetryHistory = _telemetryHistory.asStateFlow()

    private val _logs = MutableStateFlow<PersistentList<String>>(persistentListOf())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun addTelemetry(telemetry: Telemetry) {
        _telemetryHistory.update { it + telemetry }
    }

    fun logTerminal(message: String) {
        val timestamp = " > "
        val formattedMessage = "$timestamp$message"

        _logs.update { currentLogs ->
            var newLogs = currentLogs.add(formattedMessage)

            if (newLogs.size > 256) {
                newLogs = newLogs.removeAt(0)
            }

            newLogs
        }
    }

    fun clearLogs() {
        _logs.value = persistentListOf()
    }
}
