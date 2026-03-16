package com.eduardo.nunes.drt.features.race

import com.juul.kable.Advertisement

interface RaceTelemetryContract {
    sealed class BluetoothStatus {
        object Disconnected : BluetoothStatus()
        object Scanning : BluetoothStatus()
        data class Connected(val deviceName: String) : BluetoothStatus()
        data class DeviceFound(val device: Advertisement) : BluetoothStatus()
        data class ConnectionFailed(val message: String) : BluetoothStatus()
    }

    data class State(
        val bluetoothStatus: BluetoothStatus = BluetoothStatus.Disconnected,
        val currentSpeed: Int = 0,
        val currentRpm: Int = 0,
        val maxSpeed: Int = 0,
        val maxRpm: Int = 0,
        val isRecording: Boolean = false,
        val showTerminalLogs: Boolean = false,
        val terminalLogs: List<String> = emptyList(),

        // Cronômetro 0-100 km/h
        val timer0to100: Long? = null,
        val formattedTimer0to100: String = "0.000",
        val is0to100Completed: Boolean = false,

        // Cronômetro de Distância (Arrancada)
        val currentDistance: Double = 0.0,
        val currentTimer: Long = 0L,
        val formattedCurrentTimer: String = "0.000",
        val timer60ft: Long? = null,
        val formatted60ft: String = "--.---",
        val timer100m: Long? = null,
        val formatted100m: String = "--.---",
        val timer201m: Long? = null,
        val formatted201m: String = "--.---"
    )

    sealed class Intent {
        object StartScanning : Intent()
        object ConnectToDevice : Intent()
        object DisconnectDevice : Intent()
        object StartRace : Intent()
        object StopRace : Intent()
        object ClearLogs: Intent()
    }

    sealed class Effect {
        data class ShowError(val message: String) : Effect()
        data class Race0to100Completed(val time: Long, val formattedTime: String) : Effect()
        data class Race201mCompleted(val time: Long, val formattedTime: String) : Effect()
    }
}
