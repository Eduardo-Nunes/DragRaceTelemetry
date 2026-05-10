package com.eduardo.nunes.drt.core.velocity

import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// 1. Uso do TimeMark (Monotônico) para precisão absoluta e imunidade a fuso-horário
data class FusionState(
    val currentCorrectionRatio: Double = 1.0,
    val startTime: TimeMark = TimeSource.Monotonic.markNow(),
    val hasReceivedGps: Boolean = false
)

interface VelocityFusionManager {
    val fusedVelocity: StateFlow<Double>
    fun updateObdSpeed(obdSpeed: Double)
    fun updateGpsSpeed(gpsSpeed: Double)
}

class VelocityFusionManagerImpl(
    private val appSharedState: AppSharedState
) : VelocityFusionManager {
    // 2. Estado exposto para a UI
    private val _fusedVelocity = MutableStateFlow(0.0)
    override val fusedVelocity: StateFlow<Double> = _fusedVelocity.asStateFlow()

    private val _fusionState = MutableStateFlow(FusionState())

    // Guarda a última velocidade crua do OBD de forma thread-safe e sem alocação
    @Volatile
    private var latestObdSpeed = 0.0

    // 3. Atualização do OBD é expressa: apenas multiplica e reflete na UI
    override fun updateObdSpeed(obdSpeed: Double) {
        latestObdSpeed = obdSpeed
        _fusedVelocity.value = obdSpeed * _fusionState.value.currentCorrectionRatio
    }

    // 4. GPS atualiza o fator de correção sem usar Flows extras
    override fun updateGpsSpeed(gpsSpeed: Double) {
        val obdSpeed = latestObdSpeed
        var logMessageToEmit: String? = null // Evita side-effects no bloco update

        _fusionState.update { state ->
            var newRatio = state.currentCorrectionRatio
            logMessageToEmit = null // Reseta caso o CAS repita o loop

            if (gpsSpeed > MIN_SPEED_FOR_CALC && obdSpeed > MIN_SPEED_FOR_CALC) {
                val difference = abs(obdSpeed - gpsSpeed) / gpsSpeed

                if (difference > MAX_WHEEL_SPIN_DIFF) {
                    logMessageToEmit = "VelocityFusion: Wheel Spin detectado! Dif: ${(difference * 100).toInt()}%. Ratio mantido."
                } else {
                    newRatio = gpsSpeed / obdSpeed

                    if (abs(state.currentCorrectionRatio - newRatio) > MIN_RATIO_CHANGE_LOG) {
                        logMessageToEmit = "VelocityFusion: Drift corrigido. Novo Ratio: $newRatio"
                    }
                }
            } else {
                if (!state.hasReceivedGps && state.startTime.elapsedNow() < WARM_UP_DURATION) {
                    newRatio = 1.0
                }
            }

            // Otimização: Retorna o mesmo objeto se nada mudou
            if (newRatio == state.currentCorrectionRatio && state.hasReceivedGps) {
                return@update state
            }

            state.copy(
                currentCorrectionRatio = newRatio,
                hasReceivedGps = true
            )
        }

        // Os side-effects (logs) ocorrem DEPOIS do update, de forma segura
        logMessageToEmit?.let { appSharedState.logTerminal(it) }

        // Como o Ratio acabou de mudar, forçamos um update imediato na UI
        _fusedVelocity.value = latestObdSpeed * _fusionState.value.currentCorrectionRatio
    }

    companion object {
        // Extração de Magic Numbers melhora a leitura e manutenção
        private const val MIN_SPEED_FOR_CALC = 20.0
        private const val MAX_WHEEL_SPIN_DIFF = 0.25
        private const val MIN_RATIO_CHANGE_LOG = 0.01
        private val WARM_UP_DURATION = 5.seconds
    }
}