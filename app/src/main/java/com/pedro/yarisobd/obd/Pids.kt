package com.pedro.yarisobd.obd

/**
 * Definição de um PID do Modo 01, com a fórmula de decodificação do padrão
 * SAE J1979.
 *
 * As fórmulas seguem a especificação pública (e a implementação de referência
 * do python-OBD). Se alguma estiver errada, o valor exibido mente — por isso
 * cada uma tem teste unitário.
 */
data class PidSpec(
    val pid: Int,
    val name: String,
    val unit: String,
    val requiredBytes: Int,
    val decode: (ByteArray) -> Double?,
)

private fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF

object Pids {

    /** PID 0x0C — rpm = ((A * 256) + B) / 4. */
    val RPM = PidSpec(0x0C, "RPM", "rpm", 2) { b ->
        ((b.u(0) * 256) + b.u(1)) / 4.0
    }

    /** PID 0x0D — velocidade em km/h = A. */
    val SPEED = PidSpec(0x0D, "Velocidade", "km/h", 1) { b -> b.u(0).toDouble() }

    /** PID 0x05 — temperatura do arrefecimento = A - 40. */
    val COOLANT = PidSpec(0x05, "Temperatura do líquido", "°C", 1) { b -> b.u(0) - 40.0 }

    /** PID 0x0F — temperatura do ar admitido = A - 40. */
    val INTAKE = PidSpec(0x0F, "Temperatura do ar", "°C", 1) { b -> b.u(0) - 40.0 }

    /** PID 0x04 — carga calculada = A * 100 / 255. */
    val LOAD = PidSpec(0x04, "Carga do motor", "%", 1) { b -> b.u(0) * 100.0 / 255.0 }

    /** PID 0x11 — posição do acelerador = A * 100 / 255. */
    val THROTTLE = PidSpec(0x11, "Acelerador", "%", 1) { b -> b.u(0) * 100.0 / 255.0 }

    /** PID 0x0B — pressão do coletor = A (kPa). */
    val MAP = PidSpec(0x0B, "Pressão do coletor", "kPa", 1) { b -> b.u(0).toDouble() }

    /** PID 0x10 — fluxo de ar = ((A * 256) + B) / 100 (g/s). */
    val MAF = PidSpec(0x10, "Fluxo de ar (MAF)", "g/s", 2) { b ->
        ((b.u(0) * 256) + b.u(1)) / 100.0
    }

    /** PID 0x2F — nível de combustível = A * 100 / 255. */
    val FUEL_LEVEL = PidSpec(0x2F, "Nível de combustível", "%", 1) { b -> b.u(0) * 100.0 / 255.0 }

    /** PID 0x33 — pressão barométrica = A (kPa). */
    val BARO = PidSpec(0x33, "Pressão barométrica", "kPa", 1) { b -> b.u(0).toDouble() }

    /** PID 0x42 — tensão do módulo = ((A * 256) + B) / 1000 (V). */
    val VOLTAGE = PidSpec(0x42, "Tensão do módulo", "V", 2) { b ->
        ((b.u(0) * 256) + b.u(1)) / 1000.0
    }

    /** PID 0x52 — teor de etanol = A * 100 / 255. */
    val ETHANOL = PidSpec(0x52, "Teor de etanol", "%", 1) { b -> b.u(0) * 100.0 / 255.0 }

    /** PID 0x0E — avanço de ignição = A / 2 - 64 (graus). */
    val TIMING = PidSpec(0x0E, "Avanço de ignição", "°", 1) { b -> b.u(0) / 2.0 - 64.0 }

    val ALL: List<PidSpec> = listOf(
        RPM, SPEED, COOLANT, INTAKE, LOAD, THROTTLE, MAP, MAF,
        FUEL_LEVEL, BARO, VOLTAGE, ETHANOL, TIMING,
    )

    fun byId(pid: Int): PidSpec? = ALL.firstOrNull { it.pid == pid }
}
