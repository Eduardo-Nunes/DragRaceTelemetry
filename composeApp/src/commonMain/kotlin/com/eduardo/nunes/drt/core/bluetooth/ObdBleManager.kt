package com.eduardo.nunes.drt.core.bluetooth

import com.eduardo.nunes.drt.features.race.RaceTelemetryContract
import com.juul.kable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// OBD2 service and characteristic UUIDs (common for many ELM327-based adapters)
private const val OBD_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
private const val OBD_RX_CHARACTERISTIC_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
private const val OBD_TX_CHARACTERISTIC_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

// PIDs for Speed and RPM
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
    private var connectionJob: Job? = null
    private var dataStreamJob: Job? = null

    // Initialize Kable Scanner
    private val scanner = Scanner()

    private fun logTerminal(message: String) {
        println(message)
        _logs.update { current ->
            (current + message).takeLast(32) // Mantém as últimas 32 linhas para o mini-terminal
        }
    }

    fun startScanning() {
        if (_bluetoothStatus.value is RaceTelemetryContract.BluetoothStatus.Scanning) return

        _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Scanning
        logTerminal("Scanning for OBD2 devices...")
        scope.launch {
            try {
                scanner.advertisements
                    .filter { advertisement ->
                        val name = advertisement.name ?: ""
                        name.contains("OBD", ignoreCase = true) ||
                                name.contains("uScan", ignoreCase = true) ||
                                name.contains("Vgate", ignoreCase = true) ||
                                name.contains("ELM", ignoreCase = true)
                    }
                    .onEach { advertisement ->
                        _bluetoothStatus.value =
                            RaceTelemetryContract.BluetoothStatus.DeviceFound(advertisement)
                        logTerminal("Device Found: ${advertisement.name}")
                    }
                    .take(1) // Take the first relevant device found
                    .collect()
            } catch (e: Exception) {
                logTerminal("Scan Error: ${e.message}")
                _bluetoothStatus.value =
                    RaceTelemetryContract.BluetoothStatus.ConnectionFailed("Error scanning: ${e.message}")
                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
            }
        }
    }

    fun connectToDevice(advertisement: Advertisement) {
        if (_bluetoothStatus.value is RaceTelemetryContract.BluetoothStatus.Connected) return
        if (connectionJob?.isActive == true) return

        logTerminal("Connecting to ${advertisement.name}...")

        connectionJob = scope.launch {
            runCatching {
                peripheral = scope.peripheral(advertisement)
                peripheral?.connect()
                if (peripheral?.state?.value is State.Connected) {
                    _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Connected(
                        advertisement.name ?: "OBD Device"
                    )
                    logTerminal("Connected Successfully!")
                    delay(300)
                    startDataStream()
                } else throw Exception("Connection Failed")

            }.onFailure { e ->
                logTerminal("Connection Failed: ${e.message}")
                _bluetoothStatus.value =
                    RaceTelemetryContract.BluetoothStatus.ConnectionFailed("Failed to connect: ${e.message}")
                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
            }
        }
    }

    fun disconnect() {
        dataStreamJob?.cancel()
        connectionJob?.cancel()
        scope.launch {
            peripheral?.disconnect()
            peripheral = null
            _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
            logTerminal("Disconnected.")
        }
    }

    private suspend fun initializeObdAdapter() {
        val initCommands = listOf(
            "ATZ",    // Reset o adaptador
            "ATL0",   // Desliga linefeeds
            "ATE0",   // Echo off (não repete o que você envia)
            "ATSP0"   // Protocolo automático
        )

        for (command in initCommands) {
            sendPidRequest(command)
            delay(500) // Dá tempo para o adaptador processar
        }
    }

    private fun startDataStream() {
        dataStreamJob?.cancel()
        dataStreamJob = scope.launch {
            val p = peripheral ?: return@launch
            
            // 1. Lança o observador em uma corrotina separada para não bloquear o loop de TX
            launch {
                runCatching {
                    logTerminal("Observing RX (fff1)...")
                    p.observe(characteristicOf(OBD_SERVICE_UUID, OBD_RX_CHARACTERISTIC_UUID))
                        .collect { data ->
                            val response = data.decodeToString()
                            logTerminal("RX: $response")
                            parseObdResponse(response)
                        }
                }.onFailure { e ->
                    logTerminal("RX Error: ${e.message}")
                    disconnect()
                }
            }

            // 2. Inicializa o protocolo ELM327 via TX (fff2)
            delay(500) // Estabilização
            logTerminal("Initializing ELM327 via TX (fff2)...")
            initializeObdAdapter()

            // 3. Loop de Polling de PIDs
            logTerminal("Starting PID Polling...")
            while (isActive && _bluetoothStatus.value is RaceTelemetryContract.BluetoothStatus.Connected) {
                try {
                    sendPidRequest(PID_SPEED)
                    delay(150)
                    sendPidRequest(PID_RPM)
                    delay(150)
                } catch (e: Exception) {
                    logTerminal("Polling Error: ${e.message}")
                    disconnect()
                    break
                }
            }
        }
    }

    private suspend fun sendPidRequest(pid: String) {
        peripheral?.let { p ->
            runCatching {
                val command = "$pid\r".encodeToByteArray()
                p.write(characteristicOf(OBD_SERVICE_UUID, OBD_TX_CHARACTERISTIC_UUID), command, WriteType.WithResponse)
                logTerminal("TX: $pid")
            }.onFailure { e ->
                logTerminal("TX Failed ($pid): ${e.message}")
                throw e
            }
        }
    }

    private fun parseObdResponse(response: String) {
        // Otimização Bitwise para extração de dados
        val clean = StringBuilder(response.length)
        for (i in response.indices) {
            val c = response[i]
            if (c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f') {
                clean.append(c)
            }
        }
        val data = clean.toString()

        if (data.length < 4) return

        runCatching {
            if (data.startsWith("410D") && data.length >= 6) {
                _currentSpeed.value = parseHexByte(data, 4)
            } else if (data.startsWith("410C") && data.length >= 8) {
                val a = parseHexByte(data, 4)
                val b = parseHexByte(data, 6)
                // RPM Bitwise: ((A << 8) | B) >> 2
                _currentRpm.value = ((a shl 8) or b) shr 2
            }
        }.onFailure {
            logTerminal("Parse Error: $response")
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
}
