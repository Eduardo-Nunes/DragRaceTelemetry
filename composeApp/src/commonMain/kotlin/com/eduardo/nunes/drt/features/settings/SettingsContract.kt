package com.eduardo.nunes.drt.features.settings

interface SettingsContract {
    data class State(
        val showTerminalLogs: Boolean = false
    )

    sealed class Intent {
        data class ToggleTerminalLogs(val show: Boolean) : Intent()
    }
}
