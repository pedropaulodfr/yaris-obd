package com.pedro.yarisobd.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do emulador ELM327. Cobrem o que, se estiver errado, faz o app
 * "funcionar" contra um adaptador que na verdade não conversa: eco, prompt,
 * formatação de espaços, máscara de PIDs suportados, `NO DATA` e diagnóstico.
 */
class Elm327EmulatorTest {

    private fun newEmulator(): Pair<VehicleSimulator, Elm327Emulator> {
        val vehicle = VehicleSimulator()
        return vehicle to Elm327Emulator(vehicle)
    }

    @Test
    fun `ATI identifica o adaptador`() {
        val (_, emulator) = newEmulator()
        val response = emulator.processCommand("ATI")
        assertTrue(response, response.contains("ELM327"))
    }

    @Test
    fun `ATE0 desliga o eco do comando`() {
        val (_, emulator) = newEmulator()
        val before = emulator.processCommand("ATI")
        assertTrue("com eco ligado o comando deve reaparecer", before.startsWith("ATI"))

        emulator.processCommand("ATE0")
        val after = emulator.processCommand("ATI")
        assertFalse("com eco desligado o comando não deve reaparecer", after.startsWith("ATI"))
        assertTrue(after, after.contains("ELM327"))
    }

    @Test
    fun `toda resposta termina com o prompt maior que`() {
        val (_, emulator) = newEmulator()
        listOf("ATI", "ATE0", "0100", "010C", "015E").forEach { command ->
            val response = emulator.processCommand(command)
            assertTrue("comando $command sem prompt: $response", response.trimEnd().endsWith(">"))
        }
    }

    @Test
    fun `ATS0 remove os espacos da resposta`() {
        val (_, emulator) = newEmulator()
        emulator.processCommand("ATE0")

        emulator.processCommand("ATS1")
        assertTrue(emulator.processCommand("010D").contains("41 0D"))

        emulator.processCommand("ATS0")
        val compact = emulator.processCommand("010D")
        assertTrue(compact, compact.contains("410D"))
        assertFalse(compact, compact.contains("41 0D"))
    }

    @Test
    fun `RPM responde com a formula correta`() {
        val (_, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("ATS1")
        emulator.processCommand("0100") // consome o SEARCHING...

        // Marcha lenta do modelo: 750 rpm -> 750*4 = 3000 = 0x0BB8.
        val response = emulator.processCommand("010C")
        assertTrue(response, response.contains("41 0C 0B B8"))
    }

    @Test
    fun `PID nao suportado responde NO DATA`() {
        val (_, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("0100")

        // 0x5E (vazão de combustível) foi deliberadamente deixado de fora:
        // é provavelmente ausente no Yaris flex.
        val response = emulator.processCommand("015E")
        assertTrue(response, response.contains("NO DATA"))
    }

    @Test
    fun `mascara de PIDs suportados bate com a tabela`() {
        val (_, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("ATS1")
        emulator.processCommand("0100")

        // 0x01, 0x04, 0x05 | 0x0B..0x10 | 0x11 | 0x1C, 0x1F
        assertTrue(emulator.processCommand("0100").contains("41 00 98 3F 80 12"))
        // 0x21 | 0x2F | 0x31, 0x33
        assertTrue(emulator.processCommand("0120").contains("41 20 80 02 A0 00"))
        // 0x42 | 0x52
        assertTrue(emulator.processCommand("0140").contains("41 40 40 00 40 00"))
        assertTrue(emulator.processCommand("0160").contains("41 60 00 00 00 00"))
    }

    @Test
    fun `comando desconhecido responde interrogacao`() {
        val (_, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        assertTrue(emulator.processCommand("ATXPT0").contains("?"))
    }

    @Test
    fun `falha injetada aparece no modo 03 e acende a MIL`() {
        val (vehicle, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("ATS1")

        vehicle.addDtc("P0420")

        val dtcs = emulator.processCommand("03")
        assertTrue(dtcs, dtcs.contains("43 01 04 20"))

        val mil = emulator.processCommand("0101")
        // Byte A = 0x81 -> MIL acesa + 1 DTC.
        assertTrue(mil, mil.contains("41 01 81"))
    }

    @Test
    fun `modo 04 limpa as falhas`() {
        val (vehicle, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("ATS1")
        vehicle.addDtc("P0171")

        val cleared = emulator.processCommand("04")
        assertTrue(cleared, cleared.contains("44"))
        assertTrue(emulator.processCommand("03").contains("43 00"))
        assertTrue(emulator.processCommand("0101").contains("41 01 00"))
    }

    @Test
    fun `VIN volta em multi-frame`() {
        val (_, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("ATS1")

        val vin = emulator.processCommand("0902")
        assertTrue(vin, vin.contains("0: 49 02 01 53 49 4D 55"))
    }

    @Test
    fun `motor desligado nao entrega dados do modo 01`() {
        val (vehicle, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("0100")

        vehicle.setEngineOn(false)
        assertTrue(emulator.processCommand("010C").contains("NO DATA"))
    }

    @Test
    fun `respostas do modelo ficam dentro das faixas fisicas`() {
        val (vehicle, emulator) = newEmulator()
        emulator.processCommand("ATE0")
        emulator.processCommand("ATS1")

        repeat(200) { vehicle.tick(0.1) }
        assertTrue(VehicleSimulator.isPlausible(vehicle.snapshot()))

        // Velocidade do PID 0x0D não pode passar de 255 km/h (limite do byte).
        val speed = emulator.processCommand("010D")
        assertEquals(0, Regex("41 0D FF").findAll(speed).count())
    }
}
