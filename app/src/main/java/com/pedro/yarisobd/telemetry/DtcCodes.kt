package com.pedro.yarisobd.telemetry

/**
 * Códigos de falha (DTC) no formato de dois bytes do padrão SAE J2012/J1979.
 *
 * O primeiro byte define a família pela combinação dos dois bits mais altos:
 * `00` = P (powertrain), `01` = C (chassi), `10` = B (carroceria), `11` = U (rede).
 */
object DtcCodes {

    private val LETTERS = charArrayOf('P', 'C', 'B', 'U')

    /** "P0420" -> 0x0420. Retorna null se o texto não for um DTC válido. */
    fun parse(text: String): Int? {
        val clean = text.trim().uppercase().replace(" ", "")
        if (clean.length != 5) return null

        val family = LETTERS.indexOf(clean[0])
        if (family < 0) return null

        val digits = clean.substring(1)
        if (digits.any { it !in '0'..'9' }) return null

        return (family shl 14) or digits.toInt(16)
    }

    /** 0x0420 -> "P0420". */
    fun format(value: Int): String {
        val family = (value shr 14) and 0b11
        val rest = value and 0x3FFF
        return LETTERS[family] + String.format("%04X", rest)
    }

    /** Lista de DTCs a partir do payload do Modo 03/07/0A (sem o byte de serviço). */
    fun parseList(payload: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index + 1 < payload.size) {
            val hi = payload[index].toInt() and 0xFF
            val lo = payload[index + 1].toInt() and 0xFF
            index += 2
            if (hi == 0 && lo == 0) continue
            result += format((hi shl 8) or lo)
        }
        return result
    }
}
