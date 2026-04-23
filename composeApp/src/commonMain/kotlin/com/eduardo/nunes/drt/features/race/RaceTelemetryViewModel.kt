package com.eduardo.nunes.drt.features.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardo.nunes.drt.core.bluetooth.BluetoothStatus
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.core.location.GpsManager
import com.eduardo.nunes.drt.core.model.Telemetry
import com.eduardo.nunes.drt.core.state.AppSharedState
import com.eduardo.nunes.drt.core.velocity.VelocityFusionManager
import com.eduardo.nunes.drt.features.race.model.Checkpoint
import com.eduardo.nunes.drt.features.race.model.HardwareEvent
import com.eduardo.nunes.drt.features.race.model.RaceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.time.TimeMark
class RaceTelemetryViewModel(
    private val appSharedState: AppSharedState,
    private val obdBleManager: ObdBleManager,
    private val gpsManager: GpsManager,
    private val velocityFusionManager: VelocityFusionManager
) : ViewModel() {
    private val _state = MutableStateFlow(RaceTelemetryContract.State())
    internal val state: StateFlow<RaceTelemetryContract.State> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<RaceTelemetryContract.Effect>()
    internal val effect: SharedFlow<RaceTelemetryContract.Effect> = _effect.asSharedFlow()

    private var timerJob: Job? = null
    private var raceStartTimeMark: TimeMark? = null

    init {
        gpsManager.startTracking()
        setupDataPipelines()
        observeHardwareEvents()
    }
    private fun setupDataPipelines() {
        // 1. Pipelines puros: Apenas movem dados de um lugar para outro sem alterar UI
        // Usar 'onEach().launchIn()' é mais idiomático e limpo que múltiplos 'launch { collect {} }'
        obdBleManager.currentSpeed
            .onEach { velocityFusionManager.updateObdSpeed(it.toDouble()) }
            .launchIn(viewModelScope)

        gpsManager.currentSpeed
            .onEach { velocityFusionManager.updateGpsSpeed(it.toDouble()) }
            .launchIn(viewModelScope)
    }

    private fun observeHardwareEvents() {
        // 2. Unificação de Streams (Event Stream Unification)
        // Isso cria um funil único. Zero concorrências de escrita no _state, zero race conditions.
        val hardwareStream = merge(
            obdBleManager.bluetoothStatus.map { HardwareEvent.BleStatus(it) },
            velocityFusionManager.fusedVelocity.map { HardwareEvent.FusedSpeed(it.toInt()) },
            obdBleManager.currentRpm.map { HardwareEvent.Rpm(it) },
            appSharedState.logs.map { HardwareEvent.Logs(it) },
            appSharedState.showTerminalLogs.map { HardwareEvent.ShowLogs(it) }
        )

        hardwareStream
            .onEach { event -> processHardwareEvent(event) }
            .launchIn(viewModelScope)
    }

    private fun processHardwareEvent(event: HardwareEvent) {
        when (event) {
            is HardwareEvent.BleStatus -> handleBluetoothStatus(event.status)
            is HardwareEvent.FusedSpeed -> handleSpeed(event.speed)
            is HardwareEvent.Rpm -> handleRpm(event)
            is HardwareEvent.Logs -> _state.update { it.copy(terminal = it.terminal.copy(logs = event.logs)) }
            is HardwareEvent.ShowLogs -> _state.update { it.copy(terminal = it.terminal.copy(isVisible = event.show)) }
        }
    }

    private fun handleBluetoothStatus(status: BluetoothStatus) {
        _state.update { it.copy(bluetoothStatus = status) }

        when (status) {
            is BluetoothStatus.Disconnected -> {
                if (_state.value.race.isRecording) saveCurrentRaceToHistory()
                resetRace()
                gpsManager.stopTracking()
            }
            is BluetoothStatus.Connected -> gpsManager.startTracking()
            else -> Unit
        }
    }

    private fun handleSpeed(speed: Int) {
        _state.update {
            it.copy(
                telemetry = it.telemetry.copy(
                    speed = speed,
                    maxSpeed = maxOf(speed, it.telemetry.maxSpeed)
                )
            )
        }

        val raceState = _state.value.race
        if (raceState.isRecording && speed > 0 && raceStartTimeMark == null) {
            raceStartTimeMark = TimeSource.Monotonic.markNow()
            startUiTimer()
        }
    }

    private fun handleRpm(event: HardwareEvent.Rpm) {
        _state.update {
            it.copy(
                telemetry = it.telemetry.copy(
                    rpm = event.rpm,
                    maxRpm = maxOf(event.rpm, it.telemetry.maxRpm)
                )
            )
        }
    }

    internal fun handleIntent(intent: RaceTelemetryContract.Intent) {
        when (intent) {
            is RaceTelemetryContract.Intent.StartScanning -> obdBleManager.startScanning()
            is RaceTelemetryContract.Intent.DisconnectDevice -> obdBleManager.disconnect()
            is RaceTelemetryContract.Intent.StopRace -> stopRecording()
            is RaceTelemetryContract.Intent.StartRace -> startRecording()
            is RaceTelemetryContract.Intent.ConnectToDevice -> {
                val currentStatus = _state.value.bluetoothStatus
                if (currentStatus is BluetoothStatus.DeviceFound) {
                    obdBleManager.connectToDevice(currentStatus.device)
                }
            }
        }
    }

    private fun startRecording() {
        if (_state.value.bluetoothStatus !is BluetoothStatus.Connected) {
            sendEffect(RaceTelemetryContract.Effect.ShowError("Conecte o OBD2 primeiro!"))
            return
        }

        raceStartTimeMark = null
        _state.update { it.copy(race = RaceSession(isRecording = true)) }
    }

    private fun stopRecording() {
        if (_state.value.race.isRecording) saveCurrentRaceToHistory()
        timerJob?.cancel()
        _state.update { it.copy(race = it.race.copy(isRecording = false)) }
    }

    private fun resetRace() {
        raceStartTimeMark = null
        _state.update { it.copy(race = RaceSession()) }
    }

    private fun startUiTimer() {
        timerJob?.cancel()

        timerJob = viewModelScope.launch(Dispatchers.Default) {
            var currentDistance = 0.0
            var lastTick = TimeSource.Monotonic.markNow()

            while (isActive && _state.value.race.isRecording) {
                // Conta os milissegundos desde o último frame de forma hiper precisa
                val dtMs = lastTick.elapsedNow().inWholeMilliseconds
                lastTick = TimeSource.Monotonic.markNow()

                val speedMs = _state.value.telemetry.speed / 3.6
                currentDistance += speedMs * (dtMs / 1000.0)

                // Tempo total desde o início da corrida (seguro contra mudança de fuso ou fuso-horário)
                val elapsed = raceStartTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 0L

                _state.update { currentState ->
                    val oldRace = currentState.race
                    var newRace = oldRace.copy(
                        currentDistance = currentDistance,
                        currentTimerMs = elapsed
                    )

                    // 0-100 km/h
                    if (!oldRace.run0to100.isCompleted) {
                        if (currentState.telemetry.speed >= 100) {
                            newRace = newRace.copy(run0to100 = Checkpoint(elapsed))
                        }
                    }

                    // 60ft
                    if (!oldRace.run60ft.isCompleted && currentDistance >= 18.288) {
                        newRace = newRace.copy(run60ft = Checkpoint(elapsed))
                    }

                    // 100m
                    if (!oldRace.run100m.isCompleted && currentDistance >= 100.0) {
                        newRace = newRace.copy(run100m = Checkpoint(elapsed))
                    }

                    // 201m (Finaliza a corrida)
                    if (!oldRace.run201m.isCompleted && currentDistance >= 201.168) {
                        newRace = newRace.copy(
                            run201m = Checkpoint(elapsed),
                            isRecording = false
                        )
                        sendEffect(RaceTelemetryContract.Effect.Race201mCompleted(elapsed, newRace.run201m.formattedTime))
                        saveCurrentRaceToHistory()
                        timerJob?.cancel()
                    }

                    currentState.copy(race = newRace)
                }

                // 16ms loop. Como agora só trafegamos primitivos e Value Objects, o impacto no GC é quase zero.
                delay(16.milliseconds)
            }
        }
    }

    private fun saveCurrentRaceToHistory() {
        val currentState = _state.value
        val race = currentState.race
        val telemetryState = currentState.telemetry

        if (raceStartTimeMark != null) {
            val now = Clock.System.now()
            val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val dateStr = "${local.day.toString().padStart(2, '0')}/${local.month.toString().padStart(2, '0')} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"

            val telemetry = Telemetry(
                id = now.toEpochMilliseconds().toString(),
                date = dateStr,
                timer0to100 = race.run0to100.formattedTime, // A string só é gerada aqui no fim da corrida!
                timer60ft = race.run60ft.formattedTime,
                timer100m = race.run100m.formattedTime,
                timer201m = race.run201m.formattedTime,
                maxSpeed = telemetryState.maxSpeed,
                maxRpm = telemetryState.maxRpm
            )
            appSharedState.addTelemetry(telemetry)
        }
    }

    private fun sendEffect(effect: RaceTelemetryContract.Effect) {
        viewModelScope.launch { _effect.emit(effect) }
    }
}