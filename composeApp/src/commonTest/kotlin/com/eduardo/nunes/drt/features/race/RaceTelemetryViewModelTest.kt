package com.eduardo.nunes.drt.features.race

import app.cash.turbine.test
import com.eduardo.nunes.drt.core.bluetooth.BleDevice
import com.eduardo.nunes.drt.core.bluetooth.BluetoothStatus
import com.eduardo.nunes.drt.core.bluetooth.ObdBleManager
import com.eduardo.nunes.drt.core.location.GpsManager
import com.eduardo.nunes.drt.core.state.AppSharedState
import com.eduardo.nunes.drt.core.velocity.VelocityFusionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RaceTelemetryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeAppSharedState: AppSharedState
    private lateinit var fakeObdBleManager: FakeObdBleManager
    private lateinit var fakeGpsManager: FakeGpsManager
    private lateinit var fakeVelocityFusionManager: FakeVelocityFusionManager

    private lateinit var viewModel: RaceTelemetryViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fakeAppSharedState = AppSharedState()
        fakeObdBleManager = FakeObdBleManager()
        fakeGpsManager = FakeGpsManager()
        fakeVelocityFusionManager = FakeVelocityFusionManager()

        viewModel = RaceTelemetryViewModel(
            appSharedState = fakeAppSharedState,
            obdBleManager = fakeObdBleManager,
            gpsManager = fakeGpsManager,
            velocityFusionManager = fakeVelocityFusionManager,
            defaultDispatcher = testDispatcher
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `When init then expect GpsManager starts tracking`() = runTest(testDispatcher) {
        assertTrue(fakeGpsManager.isTrackingStarted)
    }

    @Test
    fun `When Bluetooth Connected event received then expect state updated to Connected`() = runTest(testDispatcher) {
        viewModel.state.test {
            // Initial state (Disconnected)
            assertEquals(BluetoothStatus.Disconnected, awaitItem().bluetoothStatus)
            
            fakeObdBleManager.emitStatus(BluetoothStatus.Connected("OBD2_TEST"))
            
            val state = expectMostRecentItem()
            assertTrue(state.bluetoothStatus is BluetoothStatus.Connected)
            assertEquals("OBD2_TEST", (state.bluetoothStatus as BluetoothStatus.Connected).deviceName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `When ConnectToDevice intent fired and status is DeviceFound then expect connectToDevice called`() = runTest(testDispatcher) {
        val device = FakeBleDevice("OBD2_TEST", "id1")
        fakeObdBleManager.emitStatus(BluetoothStatus.DeviceFound(device))
        
        viewModel.handleIntent(RaceTelemetryContract.Intent.ConnectToDevice)

        assertTrue(fakeObdBleManager.wasConnectCalled)
        assertEquals(device, fakeObdBleManager.lastConnectedDevice)
    }

    @Test
    fun `When StartRace intent fired and Bluetooth not connected then expect error effect and race not recording`() = runTest(testDispatcher) {
        viewModel.effect.test {
            viewModel.handleIntent(RaceTelemetryContract.Intent.StartRace)
            val effect = awaitItem()
            assertTrue(effect is RaceTelemetryContract.Effect.ShowError)
            assertEquals("Conecte o OBD2 primeiro!", (effect as RaceTelemetryContract.Effect.ShowError).message)
            assertFalse(viewModel.state.value.race.isRecording)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `When race is armed and speed increases then expect timer started and distance tracking`() = runTest(testDispatcher) {
        viewModel.state.test {
            // Initial state
            assertTrue(awaitItem().bluetoothStatus is BluetoothStatus.Disconnected)

            // 1. Connect device
            fakeObdBleManager.emitStatus(BluetoothStatus.Connected("OBD2_TEST"))
            assertTrue(expectMostRecentItem().bluetoothStatus is BluetoothStatus.Connected)

            // 2. Arm the race
            viewModel.handleIntent(RaceTelemetryContract.Intent.StartRace)
            assertTrue(expectMostRecentItem().race.isRecording)

            // 3. Car accelerates
            fakeVelocityFusionManager.emitFusedSpeed(20.0)
            
            val racingState = expectMostRecentItem()

            // Race should be recording and distance should be greater than 0
            assertTrue(racingState.race.isRecording)
            assertTrue(racingState.race.currentDistance > 0.0)
            assertEquals(20, racingState.telemetry.speed)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `When Bluetooth disconnected then expect GPS tracking stopped and race reset`() = runTest(testDispatcher) {
        viewModel.state.test {
            // Skip initial
            awaitItem()

            // Conecta primeiro e arma corrida
            fakeObdBleManager.emitStatus(BluetoothStatus.Connected("Device"))
            assertTrue(expectMostRecentItem().bluetoothStatus is BluetoothStatus.Connected)

            viewModel.handleIntent(RaceTelemetryContract.Intent.StartRace)
            assertTrue(expectMostRecentItem().race.isRecording)

            // Dispara desconexão
            fakeObdBleManager.emitStatus(BluetoothStatus.Disconnected)

            val state = expectMostRecentItem()
            assertEquals(BluetoothStatus.Disconnected, state.bluetoothStatus)
            assertFalse(state.race.isRecording)
            assertFalse(fakeGpsManager.isTrackingStarted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class FakeObdBleManager : ObdBleManager {
    private val _bluetoothStatus = MutableStateFlow<BluetoothStatus>(BluetoothStatus.Disconnected)
    override val bluetoothStatus = _bluetoothStatus.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0)
    override val currentSpeed = _currentSpeed.asStateFlow()

    private val _currentRpm = MutableStateFlow(0)
    override val currentRpm = _currentRpm.asStateFlow()

    var wasConnectCalled = false
    var wasStartScanningCalled = false
    var lastConnectedDevice: BleDevice? = null

    override fun startScanning() { wasStartScanningCalled = true }
    override fun connectToDevice(device: BleDevice) { 
        wasConnectCalled = true 
        lastConnectedDevice = device
    }
    override fun disconnect() {}

    fun emitStatus(status: BluetoothStatus) {
        _bluetoothStatus.value = status
    }
}

class FakeGpsManager : GpsManager {
    private val _currentSpeed = MutableStateFlow(0)
    override val currentSpeed = _currentSpeed.asStateFlow()
    var isTrackingStarted = false
    override fun startTracking() { isTrackingStarted = true }
    override fun stopTracking() { isTrackingStarted = false }
}

class FakeVelocityFusionManager : VelocityFusionManager {
    private val _fusedVelocity = MutableStateFlow(0.0)
    override val fusedVelocity = _fusedVelocity.asStateFlow()
    override fun updateObdSpeed(obdSpeed: Double) {}
    override fun updateGpsSpeed(gpsSpeed: Double) {}
    fun emitFusedSpeed(speed: Double) {
        _fusedVelocity.value = speed
    }
}

data class FakeBleDevice(override val name: String?, override val identifier: String) : BleDevice
