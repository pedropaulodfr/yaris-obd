package com.pedro.yarisobd.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do modelo do veículo. Verificam comportamento (acelera, freia, aquece,
 * fica em marcha lenta) em vez de valores exatos — o modelo é de plausibilidade,
 * não de engenharia.
 */
class VehicleSimulatorTest {

    @Test
    fun `acelerador abre a velocidade`() {
        val vehicle = VehicleSimulator()
        vehicle.autoDrive = false
        vehicle.throttleCommand = 1.0

        repeat(100) { vehicle.tick(0.1) } // 10 s

        val snapshot = vehicle.snapshot()
        assertTrue("o carro deveria estar andando", snapshot.speedKph > 20.0)
        assertTrue("a rotação deveria subir junto", snapshot.rpm > 1000.0)
        assertTrue(VehicleSimulator.isPlausible(snapshot))
    }

    @Test
    fun `sem acelerador o carro volta a marcha lenta`() {
        val vehicle = VehicleSimulator()
        vehicle.autoDrive = false
        vehicle.throttleCommand = 0.0

        repeat(200) { vehicle.tick(0.1) } // 20 s parado

        val snapshot = vehicle.snapshot()
        assertEquals(0.0, snapshot.speedKph, 0.5)
        assertTrue("rpm de marcha lenta fora da faixa: ${snapshot.rpm}", snapshot.rpm in 700.0..800.0)
    }

    @Test
    fun `temperatura sobe com o tempo de funcionamento`() {
        val vehicle = VehicleSimulator()
        val start = vehicle.snapshot().coolantC

        repeat(600) { vehicle.tick(0.1) } // 60 s

        val after = vehicle.snapshot().coolantC
        assertTrue("o motor deveria aquecer: $start -> $after", after > start + 10.0)
        assertTrue("temperatura irreal: $after", after < 105.0)
    }

    @Test
    fun `ciclo automatico percorre aceleracao e frenagem`() {
        val vehicle = VehicleSimulator()
        vehicle.autoDrive = true

        var maxSpeed = 0.0
        repeat(600) {
            vehicle.tick(0.1) // 60 s = um ciclo completo
            maxSpeed = maxOf(maxSpeed, vehicle.snapshot().speedKph)
        }

        assertTrue("o ciclo deveria atingir velocidade de cruzeiro: $maxSpeed", maxSpeed > 30.0)
        assertTrue(VehicleSimulator.isPlausible(vehicle.snapshot()))
    }

    @Test
    fun `marchas sobem com a velocidade e nao oscilam`() {
        val vehicle = VehicleSimulator()
        vehicle.autoDrive = false
        vehicle.throttleCommand = 1.0

        val gears = mutableListOf<Int>()
        repeat(600) {
            vehicle.tick(0.1)
            gears += vehicle.snapshot().gear
        }

        // As marchas nunca podem cair enquanto a velocidade sobe com o pé no fundo.
        var previous = 0
        gears.forEach { gear ->
            assertTrue("marcha caiu de $previous para $gear", gear >= previous)
            previous = gear
        }
        assertTrue("deveria ter subido de marcha", gears.last() > 1)
    }

    @Test
    fun `consumo faz o tanque baixar devagar`() {
        val vehicle = VehicleSimulator()
        vehicle.autoDrive = false
        vehicle.throttleCommand = 0.6

        val start = vehicle.snapshot().fuelLevelPct
        repeat(300) { vehicle.tick(0.1) } // 30 s

        val after = vehicle.snapshot().fuelLevelPct
        assertTrue("o nível deveria cair um pouco", after < start)
        assertTrue("o nível caiu rápido demais: $start -> $after", after > start - 1.0)
    }

    @Test
    fun `falhas controlam a luz de injecao`() {
        val vehicle = VehicleSimulator()
        assertFalse(vehicle.snapshot().milOn)

        assertTrue(vehicle.addDtc("P0420"))
        assertFalse("não deve aceitar a mesma falha duas vezes", vehicle.addDtc("P0420"))
        assertTrue(vehicle.snapshot().milOn)
        assertEquals(listOf("P0420"), vehicle.snapshot().confirmedDtcs)

        vehicle.clearDtcs()
        assertFalse(vehicle.snapshot().milOn)
        assertTrue(vehicle.snapshot().confirmedDtcs.isEmpty())
    }

    @Test
    fun `falha invalida e recusada`() {
        val vehicle = VehicleSimulator()
        assertFalse(vehicle.addDtc("X9999"))
        assertFalse(vehicle.addDtc("P42"))
    }

    @Test
    fun `VIN simulado e reconhecivel e nao e um VIN real`() {
        val vin = VehicleSimulator.FAKE_VIN
        assertEquals(17, vin.length)
        assertTrue("o VIN simulado deve ser obviamente fictício", vin.startsWith("SIMULATED"))
    }

    @Test
    fun `motor desligado congela a rotacao`() {
        val vehicle = VehicleSimulator()
        vehicle.setEngineOn(false)
        repeat(50) { vehicle.tick(0.1) }
        assertEquals(0.0, vehicle.snapshot().rpm, 1.0)
    }
}
