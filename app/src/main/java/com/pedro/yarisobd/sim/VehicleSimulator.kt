package com.pedro.yarisobd.sim

import com.pedro.yarisobd.telemetry.DtcCodes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Modelo simplificado de um carro flex 1.5 (referência: família 2NR-FBE do
 * Yaris brasileiro) rodando em tempo real.
 *
 * Isto **não é** engenharia de motor nem reprodução fiel do veículo: é um
 * modelo de plausibilidade. O objetivo é que os valores se comportem como num
 * carro de verdade — marcha lenta estável, rotação acompanhando a velocidade,
 * aquecimento gradual, consumo coerente, resposta ao acelerador — para que o
 * app possa ser testado de ponta a ponta antes de existir um scanner físico.
 *
 * Nada aqui é apresentado ao usuário como dado do veículo: o painel mostra o
 * aviso de dados simulados enquanto a fonte for [com.pedro.yarisobd.telemetry.DataSource.EMULATED].
 */
class VehicleSimulator {

    // ---- entradas de controle (podem ser alteradas a qualquer momento) ----

    /** Acelerador manual, de 0.0 a 1.0. Ignorado quando [autoDrive] está ligado. */
    @Volatile
    var throttleCommand: Double = 0.0

    /** Quando ligado, executa um ciclo de condução automático. */
    @Volatile
    var autoDrive: Boolean = true

    /** Temperatura ambiente, °C. */
    @Volatile
    var ambientC: Double = 28.0

    // ---- estado do veículo ----

    private var engineOn = true
    private var speedKph = 0.0
    private var rpm = IDLE_RPM
    private var gear = 1
    private var coolantC = 28.0
    private var intakeC = 28.0
    private var fuelLevelPct = 62.5
    private var distanceKm = 0.0
    private var distanceSinceClearKm = 0.0
    private var runTimeSec = 0.0
    private var elapsedSec = 0.0
    private var ethanolPct = 27.0
    private var voltage = 12.4
    private val confirmedDtcs = mutableListOf<Int>()
    private val pendingDtcs = mutableListOf<Int>()

    private val random = Random(seed = 42)

    /** Avança o modelo. [dtSeconds] deve ser pequeno (0,05 s ou menos). */
    @Synchronized
    fun tick(dtSeconds: Double) {
        if (dtSeconds <= 0.0) return
        val dt = dtSeconds.coerceAtMost(0.5)

        elapsedSec += dt
        if (!engineOn) {
            speedKph = max(0.0, speedKph - 4.0 * dt)
            rpm = approach(rpm, 0.0, dt, 0.4)
            return
        }

        runTimeSec += dt

        val throttle = if (autoDrive) autoThrottle(elapsedSec) else throttleCommand
        updateSpeed(throttle, dt)
        updateGear(throttle, dt)
        updateRpm(dt)
        updateTemperatures(throttle, dt)
        updateFuel(dt)
        updateElectrical(dt)

        distanceSinceClearKm += speedKph * dt / 3600.0
        distanceKm += speedKph * dt / 3600.0
    }

    private fun updateSpeed(throttle: Double, dt: Double) {
        // Arrasto cresce com o quadrado da velocidade; freio motor acima do
        // ponto em que o pé sai do acelerador.
        val drag = 0.25 + 0.0009 * speedKph * speedKph
        val engineBrake = if (throttle < 0.02) 2.2 else 0.0
        val accel = throttle * 8.0 - drag - engineBrake
        speedKph = max(0.0, speedKph + accel * dt)
        if (throttle < 0.02 && speedKph < 1.0) speedKph = 0.0
    }

    private fun updateGear(throttle: Double, dt: Double) {
        // Escolha de marcha com histerese: evita trocar a cada quadro perto do
        // limiar, que faria a rotação "pular" no painel.
        val upshift = when (gear) {
            1 -> 15.0
            2 -> 28.0
            3 -> 47.0
            4 -> 68.0
            5 -> 95.0
            else -> Double.MAX_VALUE
        }
        val downshift = when (gear) {
            1 -> 0.0
            2 -> 9.0
            3 -> 22.0
            4 -> 40.0
            5 -> 62.0
            else -> 88.0
        }
        when {
            speedKph > upshift && throttle > 0.05 -> gear = min(6, gear + 1)
            speedKph < downshift -> gear = max(1, gear - 1)
        }
    }

    private fun updateRpm(dt: Double) {
        // Relação aproximada rotação/velocidade por marcha (rpm por km/h).
        val rpmPerKph = when (gear) {
            1 -> 118.0
            2 -> 74.0
            3 -> 50.0
            4 -> 36.0
            5 -> 27.0
            else -> 21.5
        }
        val target = if (speedKph < 1.0) IDLE_RPM else max(IDLE_RPM, speedKph * rpmPerKph)
        rpm = approach(rpm, target, dt, 0.30)
        if (speedKph < 1.0) {
            // Marcha lenta com pequena oscilação, como num motor real.
            rpm += (random.nextDouble(-6.0, 6.0)) * dt
            rpm = rpm.coerceIn(IDLE_RPM - 25.0, IDLE_RPM + 35.0)
        }
    }

    private fun updateTemperatures(throttle: Double, dt: Double) {
        val load = currentLoad()
        val coolantTarget = 88.0 + 5.0 * load
        coolantC = approach(coolantC, coolantTarget, dt, 40.0)
        coolantC = max(ambientC, coolantC)

        val intakeTarget = ambientC + 4.0 + 7.0 * throttle
        intakeC = approach(intakeC, intakeTarget, dt, 8.0)
    }

    private fun updateFuel(dt: Double) {
        // Consumo pelo MAF, dividido por um AFR fixo apenas para o tanque
        // baixar de forma realista. Não é usada como estimativa de consumo.
        val fuelGramsPerSecond = currentMaf() / 14.0
        val litersPerHour = fuelGramsPerSecond * 3600.0 / 745.0
        val tankLiters = 45.0
        fuelLevelPct -= (litersPerHour / 3600.0 / tankLiters * 100.0) * dt
        fuelLevelPct = fuelLevelPct.coerceIn(3.0, 100.0)

        // O sensor de etanol só atualiza de vez em quando, como no carro real.
        if (random.nextDouble() < 0.001 * dt * 60.0) {
            ethanolPct = (ethanolPct + random.nextDouble(-0.4, 0.4)).coerceIn(20.0, 100.0)
        }
    }

    private fun updateElectrical(dt: Double) {
        val target = if (rpm > 500) 14.15 - 0.15 * (rpm / 7000.0) else 12.3
        voltage = approach(voltage, target, dt, 1.2)
        voltage += random.nextDouble(-0.02, 0.02)
    }

    // ---- grandezas derivadas do estado atual ----

    private fun currentThrottle(): Double =
        if (autoDrive) autoThrottle(elapsedSec) else throttleCommand

    private fun currentLoad(): Double = (currentMap() / 101.0 * 100.0).coerceIn(12.0, 100.0) / 100.0

    private fun currentMap(): Double {
        val throttle = currentThrottle()
        return 30.0 + throttle * 68.0 + (rpm / 7000.0) * 8.0
    }

    private fun currentMaf(): Double {
        val map = currentMap()
        return 2.4 + (rpm / 1000.0) * (map / 100.0) * 3.0
    }

    /**
     * Ciclo de condução automático de 60 s:
     * aceleração suave → cruzeiro → frenagem → marcha lenta → acelerada forte.
     */
    private fun autoThrottle(t: Double): Double {
        val cycle = t % 60.0
        return when {
            cycle < 12.0 -> 0.30 + (cycle / 12.0) * 0.45
            cycle < 30.0 -> 0.26 + 0.04 * kotlin.math.sin(cycle)
            cycle < 38.0 -> 0.0
            cycle < 48.0 -> 0.0
            cycle < 54.0 -> 0.55 + (cycle - 48.0) / 6.0 * 0.35
            else -> 0.30
        }
    }

    // ---- diagnóstico ----

    @Synchronized
    fun addDtc(code: String): Boolean {
        val parsed = DtcCodes.parse(code) ?: return false
        if (confirmedDtcs.contains(parsed) || confirmedDtcs.size >= 8) return false
        confirmedDtcs += parsed
        return true
    }

    @Synchronized
    fun addPendingDtc(code: String): Boolean {
        val parsed = DtcCodes.parse(code) ?: return false
        if (pendingDtcs.contains(parsed) || pendingDtcs.size >= 4) return false
        pendingDtcs += parsed
        return true
    }

    @Synchronized
    fun clearDtcs() {
        confirmedDtcs.clear()
        pendingDtcs.clear()
        distanceSinceClearKm = 0.0
    }

    @Synchronized
    fun setEngineOn(on: Boolean) {
        engineOn = on
        if (!on) rpm = 0.0
    }

    @Synchronized
    fun snapshot(): VehicleSnapshot = VehicleSnapshot(
        engineOn = engineOn,
        speedKph = speedKph,
        rpm = rpm,
        gear = gear,
        coolantC = coolantC,
        intakeC = intakeC,
        loadPct = (currentMap() / 101.0 * 100.0).coerceIn(0.0, 100.0),
        throttlePct = currentThrottle() * 100.0,
        mapKpa = currentMap(),
        mafGps = currentMaf(),
        fuelLevelPct = fuelLevelPct,
        baroKpa = 101.0,
        voltage = voltage,
        ethanolPct = ethanolPct,
        timingAdvanceDeg = 8.0 + currentThrottle() * 18.0 - (rpm / 7000.0) * 4.0,
        milOn = confirmedDtcs.isNotEmpty(),
        dtcCount = confirmedDtcs.size,
        confirmedDtcs = confirmedDtcs.map { DtcCodes.format(it) },
        pendingDtcs = pendingDtcs.map { DtcCodes.format(it) },
        runTimeSec = runTimeSec,
        distanceSinceClearKm = distanceSinceClearKm,
        distanceTotalKm = distanceKm,
        vid = FAKE_VIN,
        fuelRateLph = currentMaf() / 14.0 * 3600.0 / 745.0,
    )

    private fun approach(current: Double, target: Double, dt: Double, tauSeconds: Double): Double {
        if (tauSeconds <= 0.0) return target
        val factor = min(1.0, dt / tauSeconds)
        return current + (target - current) * factor
    }

    companion object {
        const val IDLE_RPM = 750.0

        /**
         * VIN **fictício** e reconhecível como tal. Nunca usar um VIN real em
         * simulação: confundir os dois seria mentir sobre a identificação do
         * veículo.
         */
        const val FAKE_VIN = "SIMULATEDVIN00001"

        /** Amplitude usada em testes: verifica se o modelo é plausível. */
        fun isPlausible(snapshot: VehicleSnapshot): Boolean =
            snapshot.rpm in 0.0..7000.0 &&
                snapshot.speedKph in 0.0..220.0 &&
                snapshot.coolantC in -20.0..130.0 &&
                snapshot.intakeC in -20.0..90.0 &&
                snapshot.loadPct in 0.0..100.0 &&
                snapshot.throttlePct in 0.0..100.0 &&
                snapshot.mapKpa in 10.0..110.0 &&
                snapshot.mafGps in 0.0..70.0 &&
                snapshot.fuelLevelPct in 0.0..100.0 &&
                snapshot.voltage in 9.0..16.0 &&
                snapshot.ethanolPct in 0.0..100.0 &&
                abs(snapshot.timingAdvanceDeg) < 60.0
    }
}

/** Fotografia imutável do estado do veículo simulado. */
data class VehicleSnapshot(
    val engineOn: Boolean,
    val speedKph: Double,
    val rpm: Double,
    val gear: Int,
    val coolantC: Double,
    val intakeC: Double,
    val loadPct: Double,
    val throttlePct: Double,
    val mapKpa: Double,
    val mafGps: Double,
    val fuelLevelPct: Double,
    val baroKpa: Double,
    val voltage: Double,
    val ethanolPct: Double,
    val timingAdvanceDeg: Double,
    val milOn: Boolean,
    val dtcCount: Int,
    val confirmedDtcs: List<String>,
    val pendingDtcs: List<String>,
    val runTimeSec: Double,
    val distanceSinceClearKm: Double,
    val distanceTotalKm: Double,
    val vid: String,
    val fuelRateLph: Double,
)
