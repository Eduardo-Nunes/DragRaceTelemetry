package com.eduardo.nunes.drt.core.velocity

import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.math.abs

data class FusionState(
    val currentCorrectionRatio: Double = 1.0,
    val startTimeMs: Long = Clock.System.now().toEpochMilliseconds(),
    val hasReceivedGps: Boolean = false
)

class VelocityFusionManager(
    private val appSharedState: AppSharedState,
    scope: CoroutineScope
) {

    // Otimização 1: MutableStateFlow é mais rápido para valores únicos de estado do que SharedFlow com replay
    private val _obdVelocity = MutableStateFlow(0.0)
    
    private val _gpsVelocity = MutableSharedFlow<Double>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _fusionState = MutableStateFlow(FusionState())

    private val _fusedVelocity = MutableStateFlow(0.0)
    val fusedVelocity: StateFlow<Double> = _fusedVelocity.asStateFlow()

    @Volatile
    private var newRatio = 1.0

    init {
        scope.launch {
            _gpsVelocity.collect { gpsSpeed ->
                val obdSpeed = _obdVelocity.value

                _fusionState.update { state ->
                    newRatio = state.currentCorrectionRatio

                    if (gpsSpeed > 20.0 && obdSpeed > 20.0) {
                        val difference = abs(obdSpeed - gpsSpeed) / gpsSpeed
                        
                        if (difference > 0.25) {
                            appSharedState.logTerminal("VelocityFusion: Wheel Spin detectado! Dif: ${(difference * 100).toInt()}%. Ratio mantido.")
                        } else {
                            newRatio = gpsSpeed / obdSpeed
                            
                            if (abs(state.currentCorrectionRatio - newRatio) > 0.01) {
                                appSharedState.logTerminal("VelocityFusion: Drift corrigido. Novo Ratio: $newRatio")
                            }
                        }
                    } else {
                        // Otimização 3: Só checa o relógio se ainda estiver no warm-up
                        val now = Clock.System.now().toEpochMilliseconds()
                        if (!state.hasReceivedGps && (now - state.startTimeMs) < 5000) {
                            newRatio = 1.0
                        }
                    }

                    // Otimização 2: Evita alocação na Heap recriando a data class se nada mudou
                    if (newRatio == state.currentCorrectionRatio && state.hasReceivedGps) {
                        return@update state
                    }

                    state.copy(
                        currentCorrectionRatio = newRatio,
                        hasReceivedGps = true
                    )
                }
            }
        }

        // O combine cuidará de sempre propagar a mudança pra UI quando OBD *ou* o Fator de Correção mudarem
        scope.launch {
            _obdVelocity.combine(_fusionState) { obdSpeed, state ->
                obdSpeed * state.currentCorrectionRatio
            }.collect { fusedSpeed ->
                _fusedVelocity.value = fusedSpeed
            }
        }
    }

    fun updateObdSpeed(speed: Double) {
        _obdVelocity.value = speed
    }

    fun updateGpsSpeed(speed: Double) {
        _gpsVelocity.tryEmit(speed)
    }
}
