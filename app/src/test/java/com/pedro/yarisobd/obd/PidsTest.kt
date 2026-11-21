package com.pedro.yarisobd.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes das fórmulas de decodificação (SAE J1979).
 *
 * Uma fórmula errada aqui não quebra nada: produz um número plausível e falso.
 * É o tipo de defeito mais perigoso neste projeto, por isso cada PID tem caso.
 */
class PidsTest {

    @Test
    fun `RPM divide por quatro`() {
        // 0x1AF8 = 6904 -> 1726 rpm
        assertEquals(1726.0, Pids.RPM.decode(byteArrayOf(0x1A, 0xF8.toByte()))!!, 0.001)
        assertEquals(0.0, Pids.RPM.decode(byteArrayOf(0x00, 0x00))!!, 0.001)
    }

    @Test
    fun `velocidade e o primeiro byte em km por hora`() {
        assertEquals(60.0, Pids.SPEED.decode(byteArrayOf(60))!!, 0.001)
        assertEquals(0.0, Pids.SPEED.decode(byteArrayOf(0))!!, 0.001)
    }

    @Test
    fun `temperaturas usam offset de menos quarenta`() {
        assertEquals(88.0, Pids.COOLANT.decode(byteArrayOf(128.toByte()))!!, 0.001)
        assertEquals(-40.0, Pids.COOLANT.decode(byteArrayOf(0))!!, 0.001)
        assertEquals(32.0, Pids.INTAKE.decode(byteArrayOf(72))!!, 0.001)
    }

    @Test
    fun `percentuais usam escala de duzentos e cinquenta e cinco`() {
        assertEquals(100.0, Pids.LOAD.decode(byteArrayOf(0xFF.toByte()))!!, 0.001)
        assertEquals(0.0, Pids.THROTTLE.decode(byteArrayOf(0))!!, 0.001)
        assertEquals(50.0, Pids.FUEL_LEVEL.decode(byteArrayOf(128.toByte()))!!, 0.6)
        assertEquals(27.0, Pids.ETHANOL.decode(byteArrayOf(69))!!, 0.6)
    }

    @Test
    fun `MAF divide por cem`() {
        // 0x028A = 650 -> 6.50 g/s
        assertEquals(6.5, Pids.MAF.decode(byteArrayOf(0x02, 0x8A.toByte()))!!, 0.001)
    }

    @Test
    fun `tensao do modulo divide por mil`() {
        // 0x36B0 = 14000 -> 14.0 V
        assertEquals(14.0, Pids.VOLTAGE.decode(byteArrayOf(0x36, 0xB0.toByte()))!!, 0.001)
    }

    @Test
    fun `avanco de ignicao usa meio grau e offset`() {
        assertEquals(0.0, Pids.TIMING.decode(byteArrayOf(128.toByte()))!!, 0.001)
        assertEquals(22.0, Pids.TIMING.decode(byteArrayOf(172.toByte()))!!, 0.001)
    }

    @Test
    fun `map e baro sao o proprio byte em kPa`() {
        assertEquals(101.0, Pids.BARO.decode(byteArrayOf(101))!!, 0.001)
        assertEquals(35.0, Pids.MAP.decode(byteArrayOf(35))!!, 0.001)
    }

    @Test
    fun `todos os PIDs tem bytes minimos declarados coerentes`() {
        Pids.ALL.forEach { spec ->
            assertEquals(
                "PID ${spec.name} declara ${spec.requiredBytes} bytes",
                spec.requiredBytes,
                if (spec.pid == 0x0C || spec.pid == 0x10 || spec.pid == 0x42) 2 else 1,
            )
        }
    }

    @Test
    fun `PID inexistente retorna null em vez de inventar`() {
        assertNull(Pids.byId(0x5E))
        assertNull(Pids.byId(0x78))
    }
}
