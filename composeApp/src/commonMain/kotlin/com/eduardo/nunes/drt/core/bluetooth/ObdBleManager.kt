package com.eduardo.nunes.drt.core.bluetooth

import com.eduardo.nunes.drt.core.state.AppSharedState
import com.juul.kable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

class ObdBleManager(
    private val sharedState: AppSharedState,
    private val scope: CoroutineScope
) {
    private val _bluetoothStatus =
        MutableStateFlow<BluetoothStatus>(BluetoothStatus.Disconnected)
    val bluetoothStatus: StateFlow<BluetoothStatus> = _bluetoothStatus

    private val _currentSpeed = MutableStateFlow(0)
    val currentSpeed: StateFlow<Int> = _currentSpeed

    private val _currentRpm = MutableStateFlow(0)
    val currentRpm: StateFlow<Int> = _currentRpm
    private var peripheral: Peripheral? = null
    private var dataStreamJob: Job? = null
    private var stateMonitorJob: Job? = null
    private val scanner = Scanner()
    private val parser = ObdParser(
        listener = { pid, value ->
            // Um único ponto de distribuição (Dispatcher) de estado
            when (pid) {
                ObdPid.SPEED -> _currentSpeed.value = value
                ObdPid.RPM -> _currentRpm.value = value
            }
        },
        log = { msg ->
            sharedState.logTerminal(msg)
        }
    )

    fun startScanning() {
        // Guarda de estado
        if (_bluetoothStatus.value is BluetoothStatus.Scanning) return

        _bluetoothStatus.value = BluetoothStatus.Scanning
        sharedState.logTerminal("Scanning for OBD2 devices...")

        scope.launch {
            try {
                val device = scanner.advertisements.first { adv ->
                    val name = adv.name?.uppercase() ?: return@first false
                    TARGET_OBD_NAMES.any { target -> name.contains(target) }
                }

                sharedState.logTerminal("Device Found: ${device.name}")
                _bluetoothStatus.value = BluetoothStatus.DeviceFound(device)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sharedState.logTerminal("Scan Error: ${e.message}")
                _bluetoothStatus.value = BluetoothStatus.Disconnected
            }
        }
    }

    fun connectToDevice(advertisement: Advertisement) {
        if (_bluetoothStatus.value is BluetoothStatus.Connecting) return
        // Evita múltiplas tentativas de conexão simultâneas
        _bluetoothStatus.value = BluetoothStatus.Connecting
        sharedState.logTerminal("Connecting to ${advertisement.name}...")

        scope.launch {
            try {
                val p = scope.peripheral(advertisement)
                peripheral = p

                // Cancela qualquer monitoramento antigo antes de iniciar um novo (evita leaks)
                stateMonitorJob?.cancel()

                // Inicia o monitoramento de forma isolada
                stateMonitorJob = launch { monitorPeripheralState(p) }

                // O metodo connect() da biblioteca Kable geralmente é suspensivo e lança exceção em caso de falha.
                // Não precisamos checar "is State.Connected" logo em seguida. Se passou da linha abaixo, conectou.
                p.connect()

                _bluetoothStatus.value = BluetoothStatus.Connected(advertisement.name ?: "OBD Device")
                sharedState.logTerminal("Connected Successfully!")

                if (sharedState.showTerminalLogs.value) {
                    logPeripheralServices(p)
                }

                startDataStream(p)
            } catch (e: CancellationException) {
                // Se a coroutine for cancelada intencionalmente, desconecta limpo.
                peripheral?.disconnect()
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Erro desconhecido ao conectar"
                sharedState.logTerminal("Conn Failed: $errorMessage")

                _bluetoothStatus.value = BluetoothStatus.ConnectionFailed(errorMessage)

                // Limpa o peripheral em caso de falha para não tentar reconectar num objeto quebrado
                peripheral = null
            }
        }
    }

    private suspend fun monitorPeripheralState(p: Peripheral) {
        p.state.collect { state ->
            sharedState.logTerminal("GATT State: $state")
            if (state is State.Disconnected) {
                _bluetoothStatus.value = BluetoothStatus.Disconnected
                dataStreamJob?.cancel()
                // Se desconectou, a própria coroutine de monitoramento não precisa mais rodar
                currentCoroutineContext().cancel()
            }
        }
    }

    private suspend fun logPeripheralServices(p: Peripheral) {
        // O delay foi movido para cá para não atrasar o início do DataStream
        delay(120.milliseconds)
        p.services?.forEach { service ->
            sharedState.logTerminal("Service Found: ${service.serviceUuid}")
            service.characteristics.forEach { char ->
                sharedState.logTerminal(" -> Char: ${char.characteristicUuid}")
            }
        }
    }

    private fun startDataStream(p: Peripheral) {
        dataStreamJob?.cancel()
        dataStreamJob = scope.launch(Dispatchers.Default) { // Usando Default para processamento bitwise
            try {
                // 1. Inicia o observador de forma isolada e segura
                launch { observeIncomingData(p) }

                // 2. Aguarda a inicialização do chip ELM327
                initializeElm327(p)

                // 3. Inicia o loop infinito
                startTelemetryPolling(p)

            } catch (e: CancellationException) {
                sharedState.logTerminal("Data Stream Stopped (Cancelled).")
                throw e
            } catch (e: Exception) {
                // Se der erro de escrita ou inicialização, cai aqui
                sharedState.logTerminal("Stream Error: ${e.message}")
                _bluetoothStatus.value = BluetoothStatus.Disconnected
            }
        }
    }

    private suspend fun observeIncomingData(p: Peripheral) {
        val rxChar = characteristicOf(OBD_SERVICE_UUID, OBD_RX_CHARACTERISTIC_UUID)

        p.observe(rxChar)
            // O operador 'catch' impede que erros de leitura quebrem a coroutine pai
            .catch { e -> sharedState.logTerminal("RX Error: ${e.message}") }
            .collect { data ->
                val response = data.decodeToString().trim()
                if (response.isNotEmpty()) {
                    parser.processResponse(response) // Usando nosso parser otimizado
                }
            }
    }

    private suspend fun initializeElm327(p: Peripheral) {
        delay(500.milliseconds)

        // ATZ: Reset, ATE0: Echo Off, ATL0: Linefeeds Off, ATSP0: Auto Protocol
        val initCommands = listOf("ATZ", "ATE0", "ATL0", "ATSP0")

        for (cmd in initCommands) {
            sharedState.logTerminal("TX Init: $cmd")
            sendPidRequest(p, cmd)
            delay(300.milliseconds)
        }
    }

    private suspend fun startTelemetryPolling(p: Peripheral) {
        sharedState.logTerminal("Starting Telemetry Loop...")

        // 'currentCoroutineContext().isActive' é a forma mais segura de manter o loop.
        // Se o Bluetooth desconectar (tratado na função connectToDevice), o Job
        // é cancelado e esse loop para instantaneamente na próxima suspensão (delay).
        while (currentCoroutineContext().isActive) {
            sendPidRequest(p, PID_SPEED)
            delay(120.milliseconds)

            sendPidRequest(p, PID_RPM)
            delay(120.milliseconds)
        }
    }

    private suspend fun sendPidRequest(p: Peripheral, pid: String) {
        try {
            val txChar = characteristicOf(OBD_SERVICE_UUID, OBD_TX_CHARACTERISTIC_UUID)
            val cmd = "$pid\r".encodeToByteArray()
            p.write(txChar, cmd, WriteType.WithResponse)
        } catch (e: CancellationException) {
            throw e // Deixa o cancelamento fluir naturalmente
        } catch (e: Exception) {
            sharedState.logTerminal("TX Error ($pid): ${e.message}")
        }
    }

    fun disconnect() {
        dataStreamJob?.cancel()
        stateMonitorJob?.cancel() // Garante que paramos de escutar o GATT

        // Atualiza a UI instantaneamente (não precisa de coroutine para isso)
        _bluetoothStatus.value = BluetoothStatus.Disconnected

        scope.launch {
            sharedState.logTerminal("Disconnecting hardware...")
            try {
                peripheral?.disconnect()
            } catch (_: Exception) {
                // Ignora erros ao tentar desconectar, o que importa é limpar a referência
            } finally {
                peripheral = null
            }
        }
    }

    companion object {
        // OBD2 service and characteristic UUIDs (Padrão para a maioria dos ELM327 fff0)
        private const val OBD_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
        private const val OBD_RX_CHARACTERISTIC_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        private const val OBD_TX_CHARACTERISTIC_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
        private val TARGET_OBD_NAMES = listOf("OBD", "USCAN", "VGATE", "ELM")
        private const val PID_SPEED = "010D"
        private const val PID_RPM = "010C"
    }

}
