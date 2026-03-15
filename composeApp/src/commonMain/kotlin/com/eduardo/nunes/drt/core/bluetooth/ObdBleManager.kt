package com.eduardo.nunes.drt.core.bluetooth

import com.eduardo.nunes.drt.features.race.RaceTelemetryContract
import com.juul.kable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// OBD2 service and characteristic UUIDs (Uscan/ELM327 BLE standard)
private const val OBD_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
private const val OBD_RX_CHARACTERISTIC_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
private const val OBD_TX_CHARACTERISTIC_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

private const val PID_SPEED = "010D"
private const val PID_RPM = "010C"

class ObdBleManager(
    private val scope: CoroutineScope
) {
    private val _bluetoothStatus =
        MutableStateFlow<RaceTelemetryContract.BluetoothStatus>(RaceTelemetryContract.BluetoothStatus.Disconnected)
    val bluetoothStatus: StateFlow<RaceTelemetryContract.BluetoothStatus> = _bluetoothStatus

    private val _currentSpeed = MutableStateFlow(0)
    val currentSpeed: StateFlow<Int> = _currentSpeed

    private val _currentRpm = MutableStateFlow(0)
    val currentRpm: StateFlow<Int> = _currentRpm

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var peripheral: Peripheral? = null
    private var dataStreamJob: Job? = null
    private val scanner = Scanner()

    private fun logTerminal(message: String) {
        _logs.update { (it + message).takeLast(32) }
    }

    fun startScanning() {
        if (_bluetoothStatus.value is RaceTelemetryContract.BluetoothStatus.Scanning) return
        _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Scanning

        scope.launch {
            try {
                scanner.advertisements
                    .filter { adv ->
                        val name = adv.name ?: ""
                        listOf("OBD", "uScan", "Vgate", "ELM").any { name.contains(it, true) }
                    }
                    .onEach {
                        _bluetoothStatus.value =
                            RaceTelemetryContract.BluetoothStatus.DeviceFound(it)
                    }
                    .take(1)
                    .collect()
            } catch (e: Exception) {
                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
            }
        }
    }

    fun connectToDevice(advertisement: Advertisement) {
        scope.launch {
            runCatching {
                val p = scope.peripheral(advertisement)
                peripheral = p

                // Monitor de estado real da conexão
                launch {
                    p.state.collect { state ->
                        if (state is State.Disconnected) {
                            _bluetoothStatus.value =
                                RaceTelemetryContract.BluetoothStatus.Disconnected
                            dataStreamJob?.cancel()
                        }
                    }
                }

                p.connect()
                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Connected(
                    advertisement.name ?: "OBD Device"
                )
                logTerminal("Connected to hardware!")
                startDataStream(p)
            }.onFailure {
                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
            }
        }
    }

    private fun startDataStream(p: Peripheral) {
        dataStreamJob?.cancel()
        dataStreamJob = scope.launch {
            // 1. Observador de Respostas (RX)
            launch {
                p.observe(characteristicOf(OBD_SERVICE_UUID, OBD_RX_CHARACTERISTIC_UUID))
                    .collect { data ->
                        val response = data.decodeToString()
                        if (response.isNotBlank()) parseObdResponse(response)
                    }
            }

            // 2. Inicialização ELM327
            delay(500)
            val initCommands = listOf("ATZ", "ATL0", "ATE0", "ATSP0")
            initCommands.forEach { cmd ->
                sendPidRequest(p, cmd)
                delay(400)
            }

            // 3. Loop de Telemetria (Polling)
            while (isActive && _bluetoothStatus.value is RaceTelemetryContract.BluetoothStatus.Connected) {
                sendPidRequest(p, PID_SPEED)
                delay(100)
                sendPidRequest(p, PID_RPM)
                delay(100)
            }
        }
    }

    private suspend fun sendPidRequest(p: Peripheral, pid: String) {
        runCatching {
            val cmd = "$pid\r".encodeToByteArray()
            p.write(
                characteristicOf(OBD_SERVICE_UUID, OBD_TX_CHARACTERISTIC_UUID),
                cmd,
                WriteType.WithResponse
            )
        }
    }

    private fun parseObdResponse(response: String) {
        val clean = StringBuilder()
        response.forEach { if (it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f') clean.append(it) }
        val data = clean.toString()

        if (data.length < 4) return

        if (data.startsWith("410D") && data.length >= 6) {
            _currentSpeed.value = parseHexByte(data, 4)
        } else if (data.startsWith("410C") && data.length >= 8) {
            val a = parseHexByte(data, 4)
            val b = parseHexByte(data, 6)
            _currentRpm.value = ((a shl 8) or b) shr 2
        }
    }

    private fun parseHexByte(s: String, offset: Int): Int {
        val high = hexCharToInt(s[offset])
        val low = hexCharToInt(s[offset + 1])
        return (high shl 4) or low
    }

    private fun hexCharToInt(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> c - 'a' + 10
        else -> 0
    }

    fun disconnect() {
        dataStreamJob?.cancel()
        scope.launch {
            peripheral?.disconnect()
            peripheral = null
            _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
        }
    }
}