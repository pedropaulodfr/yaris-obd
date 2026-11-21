package com.pedro.yarisobd.telemetry

import kotlin.math.abs

/**
 * Dados que dependem de tempo e que o protocolo OBD não entrega prontos:
 * histórico recente (para gráficos de tendência), viagem acumulada e estimativa
 * de consumo.
 *
 * **Honestidade:** consumo é **estimativa** calculada a partir do MAF, nunca
 * medição da ECU. O estequiométrico muda com o teor de etanol (E27 ≈ 13,3;
 * E100 ≈ 9,0), por isso usamos o PID 0x52 quando disponível. Sem ele, o erro
 * pode passar de 30% — nesse caso os layouts mostram "—" em vez de um número
 * bonito e errado.
 */
object RideData {

    private const val CAPACITY = 150 // 30 s a ~5 Hz
    private const val TANK_LITERS = 45.0
    private const val GASOLINE_DENSITY_G_PER_L = 745.0

    private val speed = FloatArray(CAPACITY)
    private val rpm = FloatArray(CAPACITY)
    private val load = FloatArray(CAPACITY)
    private val throttle = FloatArray(CAPACITY)
    private var count = 0
    private var head = 0

    private var distanceKm = 0.0
    private var elapsedSeconds = 0.0
    private var fuelUsedLiters = 0.0
    private var lastTimestampMs = 0L

    private var maxSpeed = 0.0
    private var maxRpm = 0.0
    private var maxLoad = 0.0
    private var maxThrottle = 0.0

    /**
     * Marcha atual. **Só o simulador fornece** — o OBD-II padrão não expõe
     * marcha, então com scanner real fica `null` e os layouts mostram "—".
     */
    @Volatile
    var gearLabel: String? = null

    @Synchronized
    fun onTelemetry(t: Telemetry) {
        val now = if (t.updatedAtMs != 0L) t.updatedAtMs else System.currentTimeMillis()
        val dt = if (lastTimestampMs == 0L) 0.0 else ((now - lastTimestampMs) / 1000.0).coerceIn(0.0, 2.0)
        lastTimestampMs = now

        val currentSpeed = t.speedKph ?: 0.0
        speed[head] = currentSpeed.toFloat()
        rpm[head] = (t.rpm ?: 0.0).toFloat()
        load[head] = (t.loadPct ?: 0.0).toFloat()
        throttle[head] = (t.throttlePct ?: 0.0).toFloat()
        head = (head + 1) % CAPACITY
        count = minOf(count + 1, CAPACITY)

        if (dt > 0.0) {
            distanceKm += currentSpeed * dt / 3600.0
            elapsedSeconds += dt

            fuelRateLitersPerHour(t)?.let { rate ->
                fuelUsedLiters += rate * dt / 3600.0
            }
        }

        maxSpeed = maxOf(maxSpeed, currentSpeed)
        maxRpm = maxOf(maxRpm, t.rpm ?: 0.0)
        maxLoad = maxOf(maxLoad, t.loadPct ?: 0.0)
        maxThrottle = maxOf(maxThrottle, t.throttlePct ?: 0.0)
    }

    fun reset() {
        synchronized(this) {
            count = 0
            head = 0
            distanceKm = 0.0
            elapsedSeconds = 0.0
            fuelUsedLiters = 0.0
            maxSpeed = 0.0
            maxRpm = 0.0
            maxLoad = 0.0
            maxThrottle = 0.0
        }
    }

    /** Série recente (mais antiga → mais recente). */
    fun seriesOf(channel: Channel): FloatArray = synchronized(this) {
        val out = FloatArray(count)
        for (i in 0 until count) {
            val index = if (count < CAPACITY) i else (head + i) % CAPACITY
            out[i] = when (channel) {
                Channel.SPEED -> speed[index]
                Channel.RPM -> rpm[index]
                Channel.LOAD -> load[index]
                Channel.THROTTLE -> throttle[index]
            }
        }
        out
    }

    enum class Channel { SPEED, RPM, LOAD, THROTTLE }

    data class Snapshot(
        val tripKm: Double,
        val tripSeconds: Long,
        val avgSpeedKph: Double,
        val fuelUsedLiters: Double,
        val instantConsumptionL100: Double?,
        val averageConsumptionL100: Double?,
        val rangeKm: Double?,
        val maxSpeed: Double,
        val maxRpm: Double,
        val maxLoad: Double,
        val maxThrottle: Double,
        val hasConsumption: Boolean,
    )

    fun snapshot(t: Telemetry): Snapshot = synchronized(this) {
        val rate = fuelRateLitersPerHour(t)
        val currentSpeed = t.speedKph ?: 0.0

        val instant = if (rate != null && currentSpeed > 5.0) rate / currentSpeed * 100.0 else null
        val average = if (fuelUsedLiters > 0.01 && distanceKm > 0.05) {
            fuelUsedLiters / distanceKm * 100.0
        } else {
            null
        }
        val range = if (average != null && average > 0.1 && t.fuelLevelPct != null) {
            TANK_LITERS * (t.fuelLevelPct!! / 100.0) / average * 100.0
        } else {
            null
        }

        Snapshot(
            tripKm = distanceKm,
            tripSeconds = elapsedSeconds.toLong(),
            avgSpeedKph = if (elapsedSeconds > 1) distanceKm / elapsedSeconds * 3600.0 else 0.0,
            fuelUsedLiters = fuelUsedLiters,
            instantConsumptionL100 = instant,
            averageConsumptionL100 = average,
            rangeKm = range,
            maxSpeed = maxSpeed,
            maxRpm = maxRpm,
            maxLoad = maxLoad,
            maxThrottle = maxThrottle,
            hasConsumption = rate != null,
        )
    }

    /**
     * Vazão de combustível em L/h a partir do MAF.
     * AFR estequiométrico varia com o etanol: E0 ≈ 14,7 · E100 ≈ 9,0.
     * Retorna null quando não há MAF (não dá para estimar sem inventar).
     */
    private fun fuelRateLitersPerHour(t: Telemetry): Double? {
        val maf = t.mafGps ?: return null
        if (maf <= 0.0) return null

        val ethanol = t.ethanolPct ?: 0.0
        val afr = 14.7 - (ethanol / 100.0) * (14.7 - 9.0)
        if (afr <= 1.0) return null

        val gramsPerSecond = maf / afr
        return gramsPerSecond * 3600.0 / GASOLINE_DENSITY_G_PER_L
    }

    /** Usado pelos testes: garante que as séries nunca devolvem lixo. */
    fun isSeriesSane(values: FloatArray): Boolean = values.all { !it.isNaN() && abs(it) < 100_000f }
}
