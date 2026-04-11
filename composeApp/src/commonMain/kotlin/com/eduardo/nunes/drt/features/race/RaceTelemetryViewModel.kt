package com.eduardo.nunes.drt.features.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.core.location.GpsManager
import com.eduardo.nunes.drt.core.model.Telemetry
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class RaceTelemetryViewModel(
    private val appSharedState: AppSharedState,
    private val obdBleManager: ObdBleManager,
    private val gpsManager: GpsManager
) : ViewModel() {

    private val _state = MutableStateFlow(RaceTelemetryContract.State())
    val state: StateFlow<RaceTelemetryContract.State> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<RaceTelemetryContract.Effect>()
    val effect: SharedFlow<RaceTelemetryContract.Effect> = _effect.asSharedFlow()

    private var raceStartTime: TimeMark? = null
    private var timerJob: Job? = null

    init {
        // Observa o status do Bluetooth - SALVA AO DESCONECTAR
        viewModelScope.launch {
            obdBleManager.bluetoothStatus.collect { status ->
                _state.update { it.copy(bluetoothStatus = status) }

                when (status) {
                    is RaceTelemetryContract.BluetoothStatus.Disconnected -> {
                        if (_state.value.isRecording) saveCurrentRaceToHistory()
                        resetRace()
                        gpsManager.stopTracking()
                    }
                    is RaceTelemetryContract.BluetoothStatus.Connected -> {
                        gpsManager.startTracking()
                    }
                    else -> Unit
                }
            }
        }

        // Observa a velocidade combinada do OBD2 e GPS
        viewModelScope.launch {
//            combine(obdBleManager.currentSpeed, gpsManager.currentSpeed) { obdSpeed, gpsSpeed ->
                // Usa a velocidade do GPS se a do OBD for zero ou ausente, caso contrário prefere OBD,
                // ou simplesmente o maior valor para garantir captura rápida
//                maxOf(obdSpeed, gpsSpeed)
//            }
            obdBleManager.currentSpeed.collect { speed ->
                _state.update { 
                    it.copy(
                        currentSpeed = speed,
                        maxSpeed = if (speed > it.maxSpeed) speed else it.maxSpeed
                    )
                }
                handleSpeedUpdate(speed)
            }
        }

        // Observa RPM e rastreia RPM-MAX
        viewModelScope.launch {
            obdBleManager.currentRpm.collect { rpm ->
                _state.update { 
                    it.copy(
                        currentRpm = rpm,
                        maxRpm = if (rpm > it.maxRpm) rpm else it.maxRpm
                    )
                }
            }
        }

        // Logs e Configurações
        viewModelScope.launch {
            appSharedState.logs.collect { newLogs -> _state.update { it.copy(terminalLogs = newLogs) } }
        }
        viewModelScope.launch {
            appSharedState.showTerminalLogs.collect { show -> _state.update { it.copy(showTerminalLogs = show) } }
        }
    }

    private fun saveCurrentRaceToHistory() {
        val currentState = _state.value
        if (raceStartTime != null) {
            val now = Clock.System.now()
            val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val dateStr = "${local.day.toString().padStart(2, '0')}/${local.month.toString().padStart(2, '0')} ${local.hour}:${local.minute.toString().padStart(2, '0')}"

            val telemetry = Telemetry(
                id = now.toEpochMilliseconds().toString(),
                date = dateStr,
                timer0to100 = currentState.formattedTimer0to100,
                timer60ft = currentState.formatted60ft,
                timer100m = currentState.formatted100m,
                timer201m = currentState.formatted201m,
                maxSpeed = currentState.maxSpeed,
                maxRpm = currentState.maxRpm
            )
            appSharedState.addTelemetry(telemetry)
        }
    }

    fun handleIntent(intent: RaceTelemetryContract.Intent) {
        when (intent) {
            is RaceTelemetryContract.Intent.StartScanning -> obdBleManager.startScanning()
            is RaceTelemetryContract.Intent.DisconnectDevice -> obdBleManager.disconnect()
            is RaceTelemetryContract.Intent.StopRace -> stopRecording()
            is RaceTelemetryContract.Intent.StartRace -> startRecording()
            is RaceTelemetryContract.Intent.ConnectToDevice -> connectToDevice()
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
                    timer0to100 = 0L, formattedTimer0to100 = "0.000", is0to100Completed = false,
                    currentDistance = 0.0, currentTimer = 0L, formattedCurrentTimer = "0.000",
                    timer60ft = null, formatted60ft = "--.---",
                    timer100m = null, formatted100m = "--.---",
                    timer201m = null, formatted201m = "--.---",
                    maxSpeed = 0, maxRpm = 0
                )
            }
            raceStartTime = null
        } else {
            sendEffect(RaceTelemetryContract.Effect.ShowError("Conecte o OBD2 primeiro!"))
        }
    }

    private fun stopRecording() {
        if (_state.value.isRecording) saveCurrentRaceToHistory()
        timerJob?.cancel()
        _state.update { it.copy(isRecording = false) }
    }

    private fun resetRace() {
        _state.update {
            it.copy(
                isRecording = false,
                timer0to100 = null, formattedTimer0to100 = "0.000", is0to100Completed = false,
                currentDistance = 0.0, currentTimer = 0L, formattedCurrentTimer = "0.000",
                timer60ft = null, formatted60ft = "--.---",
                timer100m = null, formatted100m = "--.---",
                timer201m = null, formatted201m = "--.---"
            )
        }
        raceStartTime = null
    }

    private fun handleSpeedUpdate(speed: Int) {
        if (!_state.value.isRecording) return
        if (speed > 0 && raceStartTime == null) {
            raceStartTime = TimeSource.Monotonic.markNow()
            startUiTimer()
        }
    }

    private fun startUiTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            var currentDistanceMeters = 0.0
            var lastTick = TimeSource.Monotonic.markNow()

            while (isActive && _state.value.isRecording) {
                val tickStart = TimeSource.Monotonic.markNow()
                val dtMs = lastTick.elapsedNow().inWholeMilliseconds.coerceAtLeast(1)
                lastTick = tickStart

                val currentSpeedMs = _state.value.currentSpeed / 3.6
                currentDistanceMeters += currentSpeedMs * (dtMs / 1000.0)

                raceStartTime?.let { startTime ->
                    val elapsed = startTime.elapsedNow().inWholeMilliseconds
                    val currentState = _state.value
                    
                    val formatted = formatMillis(elapsed)
                    
                    var n0to100 = currentState.formattedTimer0to100
                    var n0to100Done = currentState.is0to100Completed
                    if (!n0to100Done) {
                        n0to100 = formatted
                        if (currentState.currentSpeed >= 100) n0to100Done = true
                    }

                    var n60 = currentState.formatted60ft
                    if (currentDistanceMeters >= 18.288 && n60 == "--.---") n60 = formatted
                    
                    var n100m = currentState.formatted100m
                    if (currentDistanceMeters >= 100.0 && n100m == "--.---") n100m = formatted

                    var n201m = currentState.formatted201m
                    if (currentDistanceMeters >= 201.0 && n201m == "--.---") {
                        n201m = formatted
                        _state.update { it.copy(formatted201m = n201m, isRecording = false) }
                        saveCurrentRaceToHistory()
                        sendEffect(RaceTelemetryContract.Effect.Race201mCompleted(elapsed, n201m))
                        timerJob?.cancel()
                    }

                    _state.update {
                        it.copy(
                            currentDistance = currentDistanceMeters,
                            currentTimer = elapsed,
                            formattedCurrentTimer = formatted,
                            formattedTimer0to100 = n0to100,
                            is0to100Completed = n0to100Done,
                            formatted60ft = n60,
                            formatted100m = n100m,
                            formatted201m = n201m
                        )
                    }
                }
                delay(16.milliseconds)
            }
        }
    }

    private fun formatMillis(millis: Long): String {
        val s = millis / 1000
        val ms = (millis % 1000).toString().padStart(3, '0')
        return "$s.$ms"
    }

    private fun sendEffect(effect: RaceTelemetryContract.Effect) {
        viewModelScope.launch { _effect.emit(effect) }
    }
}
