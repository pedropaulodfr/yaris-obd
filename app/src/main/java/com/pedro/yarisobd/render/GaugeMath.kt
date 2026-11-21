package com.pedro.yarisobd.render

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Matemática pura do painel: conversão de valor em fração de escala, ângulo de
 * arco e formatação. Fica separada do desenho para poder ser testada em JVM
 * (os testes rodam sem emulador nem dispositivo).
 */
object GaugeMath {

    /** Faixa de escala do conta-giros. O 2NR-FBE (1.5) corta perto de 6.200 rpm. */
    const val RPM_MIN = 0.0
    const val RPM_MAX = 7000.0

    const val SPEED_MIN = 0.0
    const val SPEED_MAX = 180.0

    const val COOLANT_MIN = 40.0
    const val COOLANT_MAX = 120.0

    /** Início e abertura do arco do conta-giros (graus, sentido horário). */
    const val ARC_START_ANGLE = 150f
    const val ARC_SWEEP_ANGLE = 240f

    /** Limite de valor `null` → fração 0, nunca `NaN`. */
    fun fraction(value: Double?, min: Double, max: Double): Float {
        if (value == null || max <= min) return 0f
        val raw = ((value - min) / (max - min)).toFloat()
        return raw.coerceIn(0f, 1f)
    }

    fun rpmFraction(rpm: Double?): Float = fraction(rpm, RPM_MIN, RPM_MAX)

    fun speedFraction(speedKph: Double?): Float = fraction(speedKph, SPEED_MIN, SPEED_MAX)

    fun coolantFraction(coolantC: Double?): Float = fraction(coolantC, COOLANT_MIN, COOLANT_MAX)

    /** Ângulo do arco correspondente à fração, em graus. */
    fun angleFor(fraction: Float, startAngle: Float = ARC_START_ANGLE, sweepAngle: Float = ARC_SWEEP_ANGLE): Float {
        val f = fraction.coerceIn(0f, 1f)
        return startAngle + sweepAngle * f
    }

    /** Zona vermelha do conta-giros (a partir de 6.000 rpm). */
    fun isRedline(rpm: Double?): Boolean = rpm != null && rpm >= 6000.0

    /** Valor inteiro para exibição; `null` vira travessão, nunca zero. */
    fun formatInt(value: Double?, unavailable: String = "—"): String =
        value?.let { it.roundToInt().toString() } ?: unavailable

    fun format1(value: Double?, unavailable: String = "—"): String =
        value?.let { String.format(Locale.US, "%.1f", it) } ?: unavailable

    fun format2(value: Double?, unavailable: String = "—"): String =
        value?.let { String.format(Locale.US, "%.2f", it) } ?: unavailable
}
