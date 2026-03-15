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
        val timer0to100: Long? = null,
        val formattedTimer0to100: String = "0.000",
        val isRecording: Boolean = false,
        val showTerminalLogs: Boolean = false,
        val terminalLogs: List<String> = emptyList()
    )

    sealed class Intent {
        object StartScanning : Intent()
        object ConnectToDevice : Intent()
        object DisconnectDevice : Intent()
        object StartRace : Intent()
        object StopRace : Intent()
    }

    sealed class Effect {
        data class ShowError(val message: String) : Effect()
        data class RaceCompleted(val time: Long, val formattedTime: String) : Effect()
    }
}
