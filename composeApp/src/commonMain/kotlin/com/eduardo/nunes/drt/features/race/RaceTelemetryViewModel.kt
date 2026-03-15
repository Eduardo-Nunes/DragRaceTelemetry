package com.eduardo.nunes.drt.features.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.flow.*
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
                    timer0to100 = null,
                    formattedTimer0to100 = "0.000"
                ) 
            }
            raceStartTime = null
        } else {
            sendEffect(RaceTelemetryContract.Effect.ShowError("Dispositivo OBD2 não conectado!"))
        }
    }

    private fun stopRecording() {
        _state.update { it.copy(isRecording = false) }
        raceStartTime = null
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

        if (speed > 0 && raceStartTime == null) {
            // O carro começou a se mover
            raceStartTime = TimeSource.Monotonic.markNow()
        }

        raceStartTime?.let { startTime ->
            val elapsed = startTime.elapsedNow().inWholeMilliseconds
            val formatted = formatMillis(elapsed)
            
            _state.update { 
                it.copy(
                    timer0to100 = elapsed,
                    formattedTimer0to100 = formatted
                ) 
            }

            if (speed >= 100) {
                // Chegou a 100 km/h, finaliza a puxada 0-100
                _state.update {
                    it.copy(
                        isRecording = false, 
                        timer0to100 = elapsed,
                        formattedTimer0to100 = formatted
                    ) 
                }
                sendEffect(RaceTelemetryContract.Effect.RaceCompleted(elapsed, formatted))
                raceStartTime = null
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
