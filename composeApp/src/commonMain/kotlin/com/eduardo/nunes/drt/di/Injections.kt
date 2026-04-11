package com.eduardo.nunes.drt.di

import com.eduardo.nunes.drt.app.AppMainViewModel
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.core.location.GpsManager
import com.eduardo.nunes.drt.core.state.AppSharedState
import com.eduardo.nunes.drt.core.velocity.VelocityFusionManager
import com.eduardo.nunes.drt.features.history.HistoryViewModel
import com.eduardo.nunes.drt.features.race.RaceTelemetryViewModel
import com.eduardo.nunes.drt.features.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AppSharedState() }

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    factory { ObdBleManager(get(), get()) }
    factory { VelocityFusionManager(get(), get()) }
    factory { GpsManager() }

    // ViewModels
    viewModel { AppMainViewModel(get()) }
    viewModel { RaceTelemetryViewModel(get(), get(), get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
}
