package com.eduardo.nunes.drt.features.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class RaceTelemetryViewModel(
    private val obdBleManager: ObdBleManager
) : ViewModel() {

    private val _state = MutableStateFlow(RaceTelemetryContract.State())
    val state: StateFlow<RaceTelemetryContract.State> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<RaceTelemetryContract.Effect>()
    val effect: SharedFlow<RaceTelemetryContract.Effect> = _effect.asSharedFlow()

    private var raceStartTime: TimeMark? = null

    private var timerJob: Job? = null

    init {
        // Observa o status do Bluetooth
        viewModelScope.launch {
            obdBleManager.bluetoothStatus.collect { status ->
                _state.update { it.copy(bluetoothStatus = status) }
                if (status is RaceTelemetryContract.BluetoothStatus.Disconnected) {
                    resetRace()
                }
            }
        }

        // Observa a velocidade do carro via OBD2
        viewModelScope.launch {
            obdBleManager.currentSpeed.collect { speed ->
                _state.update { it.copy(currentSpeed = speed) }
                handleSpeedUpdate(speed)
            }
        }

        // Observa as Rotações por Minuto (RPM)
        viewModelScope.launch {
            obdBleManager.currentRpm.collect { rpm ->
                _state.update { it.copy(currentRpm = rpm) }
            }
        }

        // Observa os logs do terminal Bluetooth
        viewModelScope.launch {
            obdBleManager.logs.collect { newLogs ->
                _state.update { it.copy(terminalLogs = newLogs) }
            }
        }

        // Observa o AppSharedState para as configurações globais
        viewModelScope.launch {
            AppSharedState.showTerminalLogs.collect { show ->
                _state.update { it.copy(showTerminalLogs = show) }
            }
        }
    }

    fun handleIntent(intent: RaceTelemetryContract.Intent) {
        when (intent) {
            is RaceTelemetryContract.Intent.StartScanning -> obdBleManager.startScanning()
            is RaceTelemetryContract.Intent.ConnectToDevice -> {
                val currentStatus = _state.value.bluetoothStatus
                if (currentStatus is RaceTelemetryContract.BluetoothStatus.DeviceFound) {
                    obdBleManager.connectToDevice(currentStatus.device)
                }
            }

            is RaceTelemetryContract.Intent.DisconnectDevice -> obdBleManager.disconnect()
            is RaceTelemetryContract.Intent.StopRace -> stopRecording()
            is RaceTelemetryContract.Intent.StartRace -> startRecording()
        }
    }

    private fun startRecording() {
        if (_state.value.bluetoothStatus is RaceTelemetryContract.BluetoothStatus.Connected) {
            _state.update {
                it.copy(
                    isRecording = true,
                    timer0to100 = 0L,
                    formattedTimer0to100 = "0.000"
                )
            }
            raceStartTime = null
            // O timerJob NÃO começa aqui. Ele começa no primeiro movimento (speed > 0).
        } else {
            sendEffect(RaceTelemetryContract.Effect.ShowError("Conecte o OBD2 primeiro!"))
        }
    }

    private fun stopRecording() {
        timerJob?.cancel()
        _state.update { it.copy(isRecording = false) }
    }

    private fun resetRace() {
        _state.update {
            it.copy(
                isRecording = false,
                timer0to100 = null,
                formattedTimer0to100 = "0.000"
            )
        }
        raceStartTime = null
    }

    /**
     * Lógica Crítica (Testes de Mesa)
     * - A latência do adaptador OBD2 é considerada (geralmente atualiza 2 a 5x por segundo no máximo).
     * - O cronômetro precisa disparar no momento que o speed sai de 0 para >0.
     * - Quando chegar em >=100km/h ele para o cronômetro.
     */
    private fun handleSpeedUpdate(speed: Int) {
        if (!_state.value.isRecording) return

        // Dispara o cronômetro no primeiro sinal de movimento
        if (speed > 0 && raceStartTime == null) {
            raceStartTime = TimeSource.Monotonic.markNow()
            startUiTimer() // <--- LANÇA O LOOP DE UI INDEPENDENTE
        }

        // Para o cronômetro ao atingir 100km/h
        if (speed >= 100 && _state.value.isRecording) {
            stopRecording()
            val finalTime = raceStartTime?.elapsedNow()?.inWholeMilliseconds ?: 0L
            _state.update {
                it.copy(
                    timer0to100 = finalTime,
                    formattedTimer0to100 = formatMillis(finalTime)
                )
            }
            sendEffect(RaceTelemetryContract.Effect.RaceCompleted(finalTime, formatMillis(finalTime)))
        }
    }

    private fun startUiTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _state.value.isRecording) {
                raceStartTime?.let { startTime ->
                    val elapsed = startTime.elapsedNow().inWholeMilliseconds

                    // Atualizamos o estado na Main Thread para a UI
                    _state.update {
                        it.copy(
                            timer0to100 = elapsed,
                            formattedTimer0to100 = formatMillis(elapsed)
                        )
                    }
                }
                // Delay de 16ms aprox. 60fps para o cronômetro ser fluído
                delay(16)
            }
        }
    }

    private fun formatMillis(millis: Long): String {
        val seconds = millis / 1000
        val ms = millis % 1000
        return "${seconds}.${ms.toString().padStart(3, '0')}"
    }

    private fun sendEffect(effect: RaceTelemetryContract.Effect) {
        viewModelScope.launch {
            _effect.emit(effect)
        }
    }

    override fun onCleared() {
        super.onCleared()
        obdBleManager.disconnect() // Garante a desconexão quando a feature/app é fechada
    }
}
