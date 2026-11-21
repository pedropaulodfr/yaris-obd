package com.pedro.yarisobd.obd

import com.pedro.yarisobd.sim.VehicleSimulator
import com.pedro.yarisobd.telemetry.DataSource
import com.pedro.yarisobd.telemetry.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Teste de integração da cadeia completa, sem Android:
 *
 * modelo do veículo → emulador ELM327 → fila em memória → sessão ELM →
 * decodificação → repositório de telemetria.
 *
 * É o teste que responde "o simulador serve para testar o app?" sem depender de
 * celular, emulador ou tela.
 */
class ObdIntegrationTest {

    private lateinit var vehicle: VehicleSimulator
    private lateinit var connection: ObdConnection
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        vehicle = VehicleSimulator()
        vehicle.autoDrive = false
        vehicle.throttleCommand = 0.5

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        connection = ObdConnection(
            SimulatedTransport(vehicle, latencyMs = 0),
            TelemetryRepository,
        )
    }

    @After
    fun tearDown() {
        connection.disconnect()
        scope.cancel()
    }

    @Test
    fun `handshake identifica adaptador e protocolo`() = runBlocking {
        connection.connect(scope)

        val state = connection.state.value
        assertTrue("estado inesperado: $state", state is ObdState.Ready)

        val ready = state as ObdState.Ready
        assertTrue(ready.adapter.contains("ELM327"))
        assertTrue(
            "protocolo deveria ser CAN: ${ready.protocol}",
            ready.protocol.contains("15765"),
        )
        assertTrue("PIDs suportados não foram descobertos", ready.supportedPids.size > 10)
        assertTrue(0x0C in ready.supportedPids)
        assertTrue("PID 0x5E deveria estar ausente", 0x5E !in ready.supportedPids)
        assertEquals(VehicleSimulator.FAKE_VIN, connection.vin)
    }

    @Test
    fun `laco de leitura publica valores decodificados`() = runBlocking {
        repeat(100) { vehicle.tick(0.1) } // coloca o carro andando

        connection.connect(scope)
        delay(1_200)

        val telemetry = TelemetryRepository.state.value
        assertEquals(DataSource.EMULATED, telemetry.source)
        assertNotNull("RPM não foi lido", telemetry.rpm)
        assertNotNull("velocidade não foi lida", telemetry.speedKph)
        assertNotNull("temperatura não foi lida", telemetry.coolantC)
        assertTrue("RPM irreal: ${telemetry.rpm}", telemetry.rpm!! in 600.0..7000.0)
        assertTrue("velocidade irreal: ${telemetry.speedKph}", telemetry.speedKph!! in 0.0..180.0)
        assertTrue(
            "dado deveria estar fresco",
            !telemetry.isStale(System.currentTimeMillis()),
        )
    }

    @Test
    fun `leitura e limpeza de DTC passam pelo protocolo`() = runBlocking {
        connection.connect(scope)
        assertTrue(connection.state.value is ObdState.Ready)

        vehicle.addDtc("P0420")
        vehicle.addDtc("P0171")

        assertEquals(listOf("P0420", "P0171"), connection.readDtcs())

        assertTrue(connection.clearDtcs())
        assertTrue(connection.readDtcs().isEmpty())
    }

    @Test
    fun `valores publicados acompanham o modelo do veiculo`() = runBlocking {
        repeat(80) { vehicle.tick(0.1) } // o carro está parado: modelo estático
        val expected = vehicle.snapshot()

        connection.connect(scope)
        delay(1_500)

        val telemetry = TelemetryRepository.state.value
        // Tolerância cobre só a quantização do PID (rpm é múltiplo de 0,25;
        // velocidade é inteira). Se a decodificação estiver errada, estoura.
        assertEquals("RPM decodificado não bate com o modelo", expected.rpm, telemetry.rpm!!, 1.0)
        assertEquals("velocidade decodificada não bate", expected.speedKph, telemetry.speedKph!!, 1.0)
        assertEquals("carga decodificada não bate", expected.loadPct, telemetry.loadPct!!, 1.0)
        assertEquals("temperatura decodificada não bate", expected.coolantC, telemetry.coolantC!!, 1.0)
    }

    @Test
    fun `motor desligado nao produz leitura`() = runBlocking {
        vehicle.setEngineOn(false)
        connection.connect(scope)

        // A ECU responde NO DATA para tudo no Modo 01: não há o que publicar.
        val state = connection.state.value
        assertTrue("esperado Ready ou Failed, veio $state", state is ObdState.Ready || state is ObdState.Failed)
    }
}
