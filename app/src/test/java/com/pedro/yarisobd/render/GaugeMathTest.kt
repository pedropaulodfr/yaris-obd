package com.pedro.yarisobd.render

import com.pedro.yarisobd.telemetry.DataSource
import com.pedro.yarisobd.telemetry.DemoTelemetrySource
import com.pedro.yarisobd.telemetry.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da lógica do painel que podem rodar em JVM, sem carro nem dispositivo.
 * Cobrem exatamente as regras que, se quebrarem, fazem o painel mentir:
 * valor indisponível, escala saturada e detecção de dado velho.
 */
class GaugeMathTest {

    @Test
    fun `valor nulo vira fracao zero e nunca NaN`() {
        assertEquals(0f, GaugeMath.rpmFraction(null), 0.0001f)
        assertEquals(0f, GaugeMath.speedFraction(null), 0.0001f)
        assertEquals(0f, GaugeMath.coolantFraction(null), 0.0001f)
    }

    @Test
    fun `fracao satura nos limites da escala`() {
        assertEquals(0f, GaugeMath.rpmFraction(-100.0), 0.0001f)
        assertEquals(1f, GaugeMath.rpmFraction(99_999.0), 0.0001f)
        assertEquals(0.5f, GaugeMath.rpmFraction(3500.0), 0.0001f)
    }

    @Test
    fun `angulo acompanha o arco do conta-giros`() {
        assertEquals(150f, GaugeMath.angleFor(0f), 0.0001f)
        assertEquals(390f, GaugeMath.angleFor(1f), 0.0001f)
        assertEquals(270f, GaugeMath.angleFor(0.5f), 0.0001f)
    }

    @Test
    fun `zona vermelha comeca em 6000 rpm`() {
        assertFalse(GaugeMath.isRedline(5999.0))
        assertFalse(GaugeMath.isRedline(null))
        assertTrue(GaugeMath.isRedline(6000.0))
    }

    @Test
    fun `indisponivel e exibido como travessao e nao como zero`() {
        assertEquals("—", GaugeMath.formatInt(null))
        assertEquals("—", GaugeMath.format1(null))
        assertEquals("0", GaugeMath.formatInt(0.0))
        assertEquals("90.5", GaugeMath.format1(90.45))
    }

    @Test
    fun `dado sem leitura e considerado velho`() {
        val never = Telemetry()
        assertTrue(never.isStale(nowMs = 1_000_000L))
    }

    @Test
    fun `leitura recente nao e considerada velha`() {
        val recent = Telemetry(updatedAtMs = 1_000_000L)
        assertFalse(recent.isStale(nowMs = 1_000_500L))
        assertTrue(recent.isStale(nowMs = 1_000_000L + Telemetry.STALE_TIMEOUT_MS + 1))
    }

    @Test
    fun `simulacao e sempre marcada como demo`() {
        val sample = DemoTelemetrySource.sample(12.0)
        assertEquals(DataSource.DEMO, sample.source)
        assertTrue("valor simulado precisa ser sinalizado", sample.isSimulated)
    }

    @Test
    fun `simulacao produz valores dentro de faixas fisicamente plausiveis`() {
        // Varre um ciclo completo e checa plausibilidade em vez de valores exatos.
        var t = 0.0
        while (t < 40.0) {
            val s = DemoTelemetrySource.sample(t)
            assertTrue("velocidade fora de faixa em t=$t", s.speedKph!! in 0.0..180.0)
            assertTrue("rpm fora de faixa em t=$t", s.rpm!! in 600.0..7000.0)
            assertTrue("acelerador fora de faixa em t=$t", s.throttlePct!! in 0.0..100.0)
            assertTrue("carga fora de faixa em t=$t", s.loadPct!! in 0.0..100.0)
            t += 0.5
        }
    }
}
