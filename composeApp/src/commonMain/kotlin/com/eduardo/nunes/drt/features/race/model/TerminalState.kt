package com.eduardo.nunes.drt.features.race.model

internal data class TerminalState(
    val isVisible: Boolean = false,
    val logs: List<String> = emptyList()
)