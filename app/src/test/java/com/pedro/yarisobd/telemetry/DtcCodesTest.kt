package com.pedro.yarisobd.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da codificação de códigos de falha. Um erro aqui mostra "P0000" no
 * lugar de uma falha real, ou inventa uma falha que não existe.
 */
class DtcCodesTest {

    @Test
    fun `converte texto para os dois bytes`() {
        assertEquals(0x0420, DtcCodes.parse("P0420"))
        assertEquals(0x0171, DtcCodes.parse("P0171"))
        assertEquals(0x0300, DtcCodes.parse("P0300"))
    }

    @Test
    fun `aceita minusculas e espacos`() {
        assertEquals(0x0420, DtcCodes.parse(" p0420 "))
        assertEquals(0x0420, DtcCodes.parse("P 0420"))
    }

    @Test
    fun `converte de volta para texto`() {
        assertEquals("P0420", DtcCodes.format(0x0420))
        assertEquals("P0171", DtcCodes.format(0x0171))
    }

    @Test
    fun `familias C, B e U usam os dois bits altos`() {
        assertEquals("C1234", DtcCodes.format(0x5234))
        assertEquals("B1234", DtcCodes.format(0x9234))
        assertEquals("U1234", DtcCodes.format(0xD234))
    }

    @Test
    fun `ida e volta preserva o codigo`() {
        listOf("P0420", "P0171", "P0301", "C0561", "B1000", "U0100").forEach { code ->
            val parsed = DtcCodes.parse(code)
            assertTrue("não converteu $code", parsed != null)
            assertEquals(code, DtcCodes.format(parsed!!))
        }
    }

    @Test
    fun `texto invalido retorna null`() {
        assertNull(DtcCodes.parse("X0420"))
        assertNull(DtcCodes.parse("P420"))
        assertNull(DtcCodes.parse("P04Z0"))
        assertNull(DtcCodes.parse(""))
    }

    @Test
    fun `lista descarta pares vazios`() {
        // 43 03 04 20 01 71 00 00 -> dois DTCs, o par 00 00 é descartado
        val payload = byteArrayOf(0x04, 0x20, 0x01, 0x71, 0x00, 0x00)
        assertEquals(listOf("P0420", "P0171"), DtcCodes.parseList(payload))
    }

    @Test
    fun `lista vazia nao inventa codigos`() {
        assertEquals(emptyList<String>(), DtcCodes.parseList(byteArrayOf()))
        assertEquals(emptyList<String>(), DtcCodes.parseList(byteArrayOf(0x00, 0x00)))
    }
}
