package com.pedro.yarisobd.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Fonte de dados de **simulação**, usada apenas enquanto o scanner OBD2 físico
 * não está conectado.
 *
 * Contrato de honestidade do projeto: todo valor produzido aqui sai com
 * [Telemetry.source] = `DataSource.DEMO`, e a interface obrigatoriamente mostra
 * o aviso permanente. Nada gerado por esta classe pode ser apresentado ao
 * usuário como leitura do carro.
 *
 * A simulação existe por um motivo prático: sem ela não é possível validar
 * layout, taxa de quadros e o caminho de desenho na superfície do Android Auto
 * antes de o adaptador chegar.
 */
class DemoTelemetrySource(private val scope: CoroutineScope) {

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        TelemetryRepository.markDemoRunning(true)
        job = scope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive) {
                val elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000.0
                TelemetryRepository.update(sample(elapsedSeconds))
                // 10 Hz é mais que suficiente para um painel automotivo e
                // mantém o custo de CPU/bateria baixo.
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        TelemetryRepository.markDemoRunning(false)
        TelemetryRepository.clear()
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 100L

        /**
         * Ciclo de condução determinístico de 40 s:
         * aceleração (0-8 s) → cruzeiro 60 km/h (8-20 s) → frenagem (20-28 s)
         * → marcha lenta (28-40 s).
         */
        fun sample(seconds: Double): Telemetry {
            val cycle = ((seconds % 40.0) + 40.0) % 40.0

            val speed = when {
                cycle < 8.0 -> cycle / 8.0 * CRUISE_SPEED
                cycle < 20.0 -> CRUISE_SPEED
                cycle < 28.0 -> CRUISE_SPEED - (cycle - 20.0) / 8.0 * CRUISE_SPEED
                else -> 0.0
            }

            val accelerating = cycle < 8.0
            val braking = cycle >= 20.0 && cycle < 28.0

            val throttle = when {
                accelerating -> 20.0 + speed / CRUISE_SPEED * 25.0
                braking -> 0.0
                cycle < 20.0 -> 17.0
                else -> 0.0
            }

            val rpm = if (speed < 1.0 && !accelerating) {
                IDLE_RPM
            } else {
                IDLE_RPM + speed * 33.0 + (throttle - 17.0) * 12.0
            }

            val load = (18.0 + throttle * 0.85).coerceIn(10.0, 92.0)
            // Aquece de 28 °C até ~90 °C nos primeiros 90 s de simulação.
            val coolant = 28.0 + (90.0 - 28.0) * (1.0 - Math.exp(-seconds / 45.0)) +
                1.5 * sin(seconds / 17.0)
            val intake = 30.0 + 6.0 * sin(seconds / 23.0)
            val map = if (throttle < 1.0) 32.0 else 30.0 + throttle * 1.25
            val maf = if (throttle < 1.0) 2.6 else 2.6 + rpm / 7000.0 * 9.0 + throttle * 0.05
            val voltage = 14.2 - 0.15 * sin(seconds / 9.0)

            return Telemetry(
                speedKph = speed.roundTo(1),
                rpm = rpm.roundTo(0),
                coolantC = coolant.roundTo(1),
                intakeC = intake.roundTo(1),
                loadPct = load.roundTo(1),
                throttlePct = throttle.roundTo(1),
                mapKpa = map.roundTo(1),
                mafGps = maf.roundTo(2),
                fuelLevelPct = 62.5,
                baroKpa = 101.0,
                moduleVoltage = voltage.roundTo(2),
                source = DataSource.DEMO,
                updatedAtMs = System.currentTimeMillis(),
            )
        }

        private const val CRUISE_SPEED = 60.0
        private const val IDLE_RPM = 750.0

        private fun Double.roundTo(decimals: Int): Double {
            if (decimals <= 0) return roundToInt().toDouble()
            var factor = 1.0
            repeat(decimals) { factor *= 10.0 }
            return (this * factor).roundToInt() / factor
        }
    }
}
