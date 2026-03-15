package com.eduardo.nunes.drt.core.bluetooth

import com.eduardo.nunes.drt.features.race.RaceTelemetryContract
import com.juul.kable.*
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// OBD2 service and characteristic UUIDs (Padrão para a maioria dos ELM327 fff0)
private const val OBD_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
private const val OBD_RX_CHARACTERISTIC_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
private const val OBD_TX_CHARACTERISTIC_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

private const val PID_SPEED = "010D"
private const val PID_RPM = "010C"

class ObdBleManager(
    private val scope: CoroutineScope
) {
    private val _bluetoothStatus = MutableStateFlow<RaceTelemetryContract.BluetoothStatus>(RaceTelemetryContract.BluetoothStatus.Disconnected)
    val bluetoothStatus: StateFlow<RaceTelemetryContract.BluetoothStatus> = _bluetoothStatus

    private val _currentSpeed = MutableStateFlow(0)
    val currentSpeed: StateFlow<Int> = _currentSpeed

    private val _currentRpm = MutableStateFlow(0)
    val currentRpm: StateFlow<Int> = _currentRpm

    private val _logs = MutableStateFlow<PersistentList<String>>(persistentListOf())

    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var peripheral: Peripheral? = null
    private var dataStreamJob: Job? = null
    private val scanner = Scanner()

    private fun logTerminal(message: String) {
        val timestamp = " > "
        val formattedMessage = "$timestamp$message"

        _logs.update { currentLogs ->
            var newLogs = currentLogs.add(formattedMessage)

            if (newLogs.size > 256) {
                newLogs = newLogs.removeAt(0)
            }

            newLogs
        }
    }

    fun clearLogs(){
        _logs.value = persistentListOf()
    }

    fun startScanning() {
        if (_bluetoothStatus.value is RaceTelemetryContract.BluetoothStatus.Scanning) return
        _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Scanning
        logTerminal("Scanning for OBD2 devices...")

        scope.launch {
            try {
                scanner.advertisements
                    .filter { adv ->
                        val name = adv.name ?: ""
                        listOf("OBD", "uScan", "Vgate", "ELM").any { name.contains(it, true) }
                    }
                    .onEach {
                        logTerminal("Device Found: ${it.name}")
                        _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.DeviceFound(it)
                    }
                    .take(1)
                    .collect()
            } catch (e: Exception) {
                logTerminal("Scan Error: ${e.message}")
                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
            }
        }
    }

    fun connectToDevice(advertisement: Advertisement) {
        scope.launch {
            runCatching {
                logTerminal("Connecting to ${advertisement.name}...")
                val p = scope.peripheral(advertisement)
                peripheral = p

                launch {
                    p.state.collect { state ->
                        logTerminal("GATT State: $state")
                        if (state is State.Disconnected) {
                            _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
                            dataStreamJob?.cancel()
                        }
                    }
                }

                p.connect()
                if (peripheral?.state?.value is State.Connected) {
                    _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Connected(
                        advertisement.name ?: "OBD Device"
                    )
                    logTerminal("Connected Successfully!")
                    delay(120)
                    peripheral?.services?.forEach { service ->
                        logTerminal("Service Found: ${service.serviceUuid}")
                        service.characteristics.forEach { char ->
                            logTerminal(" -> Char: ${char.characteristicUuid}")
                        }
                    }
                    startDataStream(p)
                } else throw Exception("Connection Failed")

//                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Connected(advertisement.name ?: "OBD Device")
//                logTerminal("SUCCESS: Connected to hardware!")
//                startDataStream(p)
            }.onFailure { e ->
                logTerminal("Conn Failed: ${e.message}")
                _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
            }
        }
    }

    private fun startDataStream(p: Peripheral) {
        dataStreamJob?.cancel()
        dataStreamJob = scope.launch(Dispatchers.Default) { // Usando Default para processamento bitwise

            // 1. Observador de Respostas (RX)
            launch {
                val rxChar = characteristicOf(OBD_SERVICE_UUID, OBD_RX_CHARACTERISTIC_UUID)
                p.observe(rxChar).collect { data ->
                    val response = data.decodeToString().trim()
                    if (response.isNotEmpty()) {
                        logTerminal("RX Raw: $response")
                        parseObdResponse(response)
                    }
                }
            }

            // 2. Inicialização ELM327
            delay(500)
            val initCommands = listOf("ATZ", "ATE0", "ATL0", "ATSP0")
            initCommands.forEach { cmd ->
                logTerminal("TX Init: $cmd")
                sendPidRequest(p, cmd)
                delay(300)
            }

            // 3. Loop de Telemetria (Polling de alta frequência)
            logTerminal("Starting Telemetry Loop...")
            while (isActive && _bluetoothStatus.value is RaceTelemetryContract.BluetoothStatus.Connected) {
                sendPidRequest(p, PID_SPEED)
                delay(120) // Ajustado para evitar buffer overflow em clones ELM
                sendPidRequest(p, PID_RPM)
                delay(120)
            }
        }
    }

    private suspend fun sendPidRequest(p: Peripheral, pid: String) {
        try {
            val txChar = characteristicOf(OBD_SERVICE_UUID, OBD_TX_CHARACTERISTIC_UUID)
            val cmd = "$pid\r".encodeToByteArray()
            p.write(txChar, cmd, WriteType.WithResponse)
        } catch (e: Exception) {
            logTerminal("TX Error ($pid): ${e.message}")
        }
    }

    private fun parseObdResponse(response: String) {
        // Remove espaços e lixo (como '>' do prompt do ELM)
        val data = response.replace(" ", "").replace(">", "").uppercase()

        try {
            // Velocidade: Esperado "410DXX" (XX = hex da velocidade)
            if (data.contains("410D")) {
                val index = data.indexOf("410D") + 4
                if (data.length >= index + 2) {
                    val hexValue = data.substring(index, index + 2)
                    val speed = hexValue.toInt(16)
                    // Filtro de sanidade: OBD2 speed é 0-255.
                    // Se o carro está parado e deu 255, pode ser lixo de memória do buffer.
                    if (speed < 255) {
                        _currentSpeed.value = speed
                        logTerminal("Parsed Speed: $speed km/h")
                    } else if (speed == 255) {
                        logTerminal("Speed Warning: 255 received (Ignored)")
                    }
                }
            }

            // RPM: Esperado "410CXXYY" (RPM = ((XX*256)+YY)/4)
            else if (data.contains("410C")) {
                val index = data.indexOf("410C") + 4
                if (data.length >= index + 4) {
                    val a = data.substring(index, index + 2).toInt(16)
                    val b = data.substring(index + 2, index + 4).toInt(16)

                    // Cálculo bitwise robusto: (A * 256 + B) / 4
                    val rpm = ((a shl 8) or b) / 4
                    _currentRpm.value = rpm
                    logTerminal("Parsed RPM: $rpm")
                }
            }
        } catch (e: Exception) {
            logTerminal("Parser Error: ${e.message} in '$data'")
        }
    }

    fun disconnect() {
        dataStreamJob?.cancel()
        scope.launch {
            logTerminal("Disconnecting...")
            peripheral?.disconnect()
            peripheral = null
            _bluetoothStatus.value = RaceTelemetryContract.BluetoothStatus.Disconnected
        }
    }
}