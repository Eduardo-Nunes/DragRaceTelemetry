package com.eduardo.nunes.drt.core.state

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Object global para compartilhar estados comuns entre ViewModels independentes,
 * garantindo a Single Source of Truth em nível de aplicação.
 */
object AppSharedState {
    val showTerminalLogs = MutableStateFlow(false)
}
