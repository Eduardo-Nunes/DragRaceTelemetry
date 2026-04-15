package com.eduardo.nunes.drt.core.velocity

import app.cash.turbine.test
import com.eduardo.nunes.drt.core.state.AppSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class VelocityFusionManagerTest {

    private val fakeAppSharedState = AppSharedState()

    @Test
    fun `initial state should have 0 fused velocity and 1 correction ratio`() = runTest {
        // Arrange
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val manager = VelocityFusionManager(fakeAppSharedState, scope)

        // Act & Assert
        manager.fusedVelocity.test {
            // A velocidade fundida inicial deve ser 0.0
            assertEquals(0.0, awaitItem())
        }
    }

    @Test
    fun `calibration threshold should not alter ratio if speed is below 20kmh`() = runTest {
        // Arrange
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val manager = VelocityFusionManager(fakeAppSharedState, scope)

        // Usamos a Turbine para observar o fluxo ANTES de enviarmos as atualizações,
        // garantindo que capturamos as emissões na ordem correta.
        manager.fusedVelocity.test {
            // 1. Estado inicial
            assertEquals(0.0, awaitItem())

            // 2. Recebe velocidade do OBD
            manager.updateObdSpeed(15.0)
            assertEquals(15.0, awaitItem())

            // 3. Recebe GPS (abaixo de 20km/h)
            manager.updateGpsSpeed(10.0)

            // Como o ratio continua 1.0 e o OBD não mudou, o StateFlow manterá o valor 15.0,
            // não emitindo um novo estado por conta do distinctUntilChanged interno do StateFlow.
            expectNoEvents()
        }
    }

    @Test
    fun `successful calibration should calculate and apply ratio`() = runTest {
        // Arrange
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val manager = VelocityFusionManager(fakeAppSharedState, scope)

        manager.fusedVelocity.test {
            // 1. Estado inicial
            assertEquals(0.0, awaitItem())

            // 2. OBD está marcando 100km/h
            manager.updateObdSpeed(100.0)
            assertEquals(100.0, awaitItem()) // Primeiro recebe obd pura

            // 3. GPS real é 95km/h. (ratio de 0.95)
            manager.updateGpsSpeed(95.0)
            assertEquals(95.0, awaitItem())  // Ajusta para a obd com fator de correção do GPS

            // 4. OBD atualizou a velocidade, e deve ser calculada com o mais recente ratio (0.95)
            manager.updateObdSpeed(105.0)
            assertEquals(99.75, awaitItem())

        }
    }

    @Test
    fun `wheel spin protection should keep previous ratio on massive discrepancies`() = runTest {
        // Arrange
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val manager = VelocityFusionManager(fakeAppSharedState, scope)

        manager.fusedVelocity.test {
            // Pula o valor inicial
            assertEquals(0.0, awaitItem())

            // Passo 1: Estabelecer um cruzeiro (Cruising) sem roda patinando
            manager.updateObdSpeed(50.0)
            assertEquals(50.0, awaitItem())

            manager.updateGpsSpeed(50.0) // Ratio = 1.0. Nenhuma emissão extra esperada para 50.0

            // Passo 2: Arrancada repentina (Wheel Spin).
            // O OBD (roda) vai para 100km/h
            manager.updateObdSpeed(100.0)
            assertEquals(100.0, awaitItem()) // Ratio ainda 1.0, então velocidade = 100.0

            // GPS (carro real) a apenas 55km/h. A diferença é > 25%, bloqueando atualização do ratio.
            manager.updateGpsSpeed(55.0)
            
            // Se o ratio FOSSE atualizado para 0.55, a fusedVelocity emitiria 55.0.
            // Como NÃO DEVE ser atualizado, usa o ratio antigo (1.0) e NADA deve ser emitido.
            expectNoEvents()
        }
    }
}
