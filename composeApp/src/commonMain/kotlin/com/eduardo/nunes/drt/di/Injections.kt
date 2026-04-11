package com.eduardo.nunes.drt.di

import com.eduardo.nunes.drt.app.AppMainViewModel
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.core.location.GpsManager
import com.eduardo.nunes.drt.core.state.AppSharedState
import com.eduardo.nunes.drt.core.velocity.VelocityFusionManager
import com.eduardo.nunes.drt.features.race.RaceTelemetryViewModel
import com.eduardo.nunes.drt.features.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    factory { ObdBleManager(get()) }
    factory { VelocityFusionManager(get()) }
    factory { GpsManager() }

    // Shared State Single Source of Truth
    single { AppSharedState }
    
    // ViewModels
    viewModel { AppMainViewModel(get()) }
    viewModel { RaceTelemetryViewModel(get(), get()) }
    viewModel { SettingsViewModel() }
}
