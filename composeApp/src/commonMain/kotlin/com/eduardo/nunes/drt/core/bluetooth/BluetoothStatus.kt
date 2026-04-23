package com.eduardo.nunes.drt.core.bluetooth

import com.juul.kable.Advertisement

sealed class BluetoothStatus {
    object Disconnected : BluetoothStatus()
    object Scanning : BluetoothStatus()
    object Connecting : BluetoothStatus()
    data class Connected(val deviceName: String) : BluetoothStatus()
    data class DeviceFound(val device: Advertisement) : BluetoothStatus()
    data class ConnectionFailed(val message: String) : BluetoothStatus()
}
