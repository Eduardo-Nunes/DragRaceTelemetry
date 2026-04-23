package com.eduardo.nunes.drt.features.race

import com.eduardo.nunes.drt.core.bluetooth.BluetoothStatus
import com.eduardo.nunes.drt.features.race.model.RaceSession
import com.eduardo.nunes.drt.features.race.model.TelemetryState
import com.eduardo.nunes.drt.features.race.model.TerminalState

internal interface RaceTelemetryContract {

    data class State(
        val bluetoothStatus: BluetoothStatus = BluetoothStatus.Disconnected,
        val telemetry: TelemetryState = TelemetryState(),
        val race: RaceSession = RaceSession(),
        val terminal: TerminalState = TerminalState()
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
        data class Race0to100Completed(val time: Long, val formattedTime: String) : Effect()
        data class Race201mCompleted(val time: Long, val formattedTime: String) : Effect()
    }
}
