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
            is RaceTelemetryContract.Intent.DisconnectDevice -> obdBleManager.disconnect()
            is RaceTelemetryContract.Intent.StopRace -> stopRecording()
            is RaceTelemetryContract.Intent.StartRace -> startRecording()
            is RaceTelemetryContract.Intent.ConnectToDevice -> connectToDevice()
            is RaceTelemetryContract.Intent.ClearLogs -> obdBleManager.clearLogs()
        }
    }

    private fun connectToDevice() {
        val currentStatus = _state.value.bluetoothStatus
        if (currentStatus is RaceTelemetryContract.BluetoothStatus.DeviceFound) {
            obdBleManager.connectToDevice(currentStatus.device)
        }
    }

    private fun startRecording() {
        if (_state.value.bluetoothStatus is RaceTelemetryContract.BluetoothStatus.Connected) {
            _state.update {
                it.copy(
                    isRecording = true,
                    // Reseta ambos os cronômetros
                    timer0to100 = 0L,
                    formattedTimer0to100 = "0.000",
                    is0to100Completed = false,
                    currentDistance = 0.0,
                    currentTimer = 0L,
                    formattedCurrentTimer = "0.000",
                    timer60ft = null, formatted60ft = "--.---",
                    timer100m = null, formatted100m = "--.---",
                    timer201m = null, formatted201m = "--.---"
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
        // Não reseta os valores para que o usuário possa ver o resultado final.
        // O reset é feito no `startRecording` ou `resetRace`.
    }

    private fun resetRace() {
        _state.update {
            it.copy(
                isRecording = false,
                timer0to100 = null,
                formattedTimer0to100 = "0.000",
                is0to100Completed = false,
                currentDistance = 0.0,
                currentTimer = 0L,
                formattedCurrentTimer = "0.000",
                timer60ft = null, formatted60ft = "--.---",
                timer100m = null, formatted100m = "--.---",
                timer201m = null, formatted201m = "--.---"
            )
        }
        raceStartTime = null
    }

    /**
     * Lógica Crítica (Testes de Mesa)
     * - A latência do adaptador OBD2 é considerada (geralmente atualiza 2 a 5x por segundo no máximo).
     * - O cronômetro precisa disparar no momento que o speed sai de 0 para >0.
     * - A distância é integrada no loop da UI.
     */
    private fun handleSpeedUpdate(speed: Int) {
        if (!_state.value.isRecording) return

        // Dispara o cronômetro no primeiro sinal de movimento
        if (speed > 0 && raceStartTime == null) {
            raceStartTime = TimeSource.Monotonic.markNow()
            startUiTimer() // <--- LANÇA O LOOP DE UI INDEPENDENTE
        }
    }

    private fun startUiTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            var currentDistanceMeters = 0.0
            var lastTick = TimeSource.Monotonic.markNow()

            // CACHE: Evita alocar milhares de Strings por segundo para etapas já concluídas
            var cachedFormatted0to100 = "0.000"
            var cachedFormatted60ft = "--.---"
            var cachedFormatted100m = "--.---"
            var cachedFormatted201m = "--.---"
            var cachedFormattedCurrentTimer = "0.000"

            while (isActive && _state.value.isRecording) {
                val tickStart = TimeSource.Monotonic.markNow()
                val dtMs = lastTick.elapsedNow().inWholeMilliseconds.coerceAtLeast(1) // Evita divisão por zero
                lastTick = tickStart

                // --- Cálculos de Física ---
                val currentSpeedMs = _state.value.currentSpeed / 3.6
                val deltaDistance = currentSpeedMs * (dtMs / 1000.0)
                currentDistanceMeters += deltaDistance

                raceStartTime?.let { startTime ->
                    val elapsed = startTime.elapsedNow().inWholeMilliseconds

                    val currentState = _state.value
                    var newTimer60ft = currentState.timer60ft
                    var newTimer100m = currentState.timer100m
                    var newTimer201m = currentState.timer201m

                    // --- Lógica para 0-100 km/h ---
                    var newTimer0to100 = currentState.timer0to100
                    var newIs0to100Completed = currentState.is0to100Completed
                    if (!newIs0to100Completed) {
                        if (_state.value.currentSpeed >= 100) {
                            newTimer0to100 = elapsed
                            cachedFormatted0to100 = formatMillis(elapsed) // Formata apenas na conclusão
                            newIs0to100Completed = true // Marca como completo para não recalcular
                            sendEffect(RaceTelemetryContract.Effect.Race0to100Completed(elapsed, formatMillis(elapsed)))
                        } else {
                            // Atualiza o timer enquanto a corrida está em andamento
                            newTimer0to100 = elapsed
                            cachedFormatted0to100 = formatMillis(elapsed)
                        }
                    }

                    // --- Lógica para Distância (Splits) ---
                    if (currentDistanceMeters >= 18.288 && newTimer60ft == null) {
                        newTimer60ft = elapsed
                        cachedFormatted60ft = formatMillis(elapsed) // Formata e guarda em cache
                    }
                    if (currentDistanceMeters >= 100.0 && newTimer100m == null) {
                        newTimer100m = elapsed
                        cachedFormatted100m = formatMillis(elapsed)
                    }
                    if (currentDistanceMeters >= 201.0 && newTimer201m == null) {
                        newTimer201m = elapsed
                        cachedFormatted201m = formatMillis(elapsed)
                        // A corrida de 201m é a condição final, então paramos tudo.
                        stopRecording() // Para o loop e atualiza isRecording
                        sendEffect(RaceTelemetryContract.Effect.Race201mCompleted(elapsed, formatMillis(elapsed)))
                    }

                    cachedFormattedCurrentTimer = formatMillis(elapsed)

                    // Atualiza o estado com todos os novos valores numa única operação
                    _state.update {
                        it.copy(
                            currentDistance = currentDistanceMeters,
                            currentTimer = elapsed,
                            formattedCurrentTimer = cachedFormattedCurrentTimer,
                            // 0-100
                            timer0to100 = newTimer0to100,
                            formattedTimer0to100 = cachedFormatted0to100,
                            is0to100Completed = newIs0to100Completed,
                            // Splits
                            timer60ft = newTimer60ft,
                            formatted60ft = cachedFormatted60ft,
                            timer100m = newTimer100m,
                            formatted100m = cachedFormatted100m,
                            timer201m = newTimer201m,
                            formatted201m = cachedFormatted201m
                        )
                    }
                }
                // Garante que o loop rode a ~60fps de forma mais precisa
                delay((16 - tickStart.elapsedNow().inWholeMilliseconds).coerceAtLeast(1))
            }
        }
    }

    private fun formatMillis(millis: Long): String {
        val seconds = millis / 1000
        val ms = millis % 1000
        
        // Otimização: Evita a criação de strings desnecessárias pelo padStart num loop de 60fps
        val msString = when {
            ms < 10 -> "00$ms"
            ms < 100 -> "0$ms"
            else -> ms.toString()
        }
        return "$seconds.$msString"
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
