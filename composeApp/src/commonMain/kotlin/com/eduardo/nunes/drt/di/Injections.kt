package com.eduardo.nunes.drt.di

import com.eduardo.nunes.drt.app.AppMainViewModel
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.features.race.RaceTelemetryViewModel
import com.eduardo.nunes.drt.features.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Módulo do Koin para Injeção de Dependência
val appModule = module {
    // Singleton para o ObdBleManager (mantém a conexão viva em todo o app)
    single { ObdBleManager(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }

    // ViewModels: um por tela, e um principal para navegação
    viewModel { AppMainViewModel() }
    viewModel { RaceTelemetryViewModel(get()) }
    viewModel { SettingsViewModel() }
}
