package com.eduardo.nunes.drt.features.race.model

import com.eduardo.nunes.drt.core.bluetooth.BluetoothStatus

internal sealed interface HardwareEvent {
    data class BleStatus(val status: BluetoothStatus) : HardwareEvent
    data class FusedSpeed(val speed: Int) : HardwareEvent
    data class Rpm(val rpm: Int) : HardwareEvent
    data class Logs(val logs: List<String>) : HardwareEvent
    data class ShowLogs(val show: Boolean) : HardwareEvent
}
