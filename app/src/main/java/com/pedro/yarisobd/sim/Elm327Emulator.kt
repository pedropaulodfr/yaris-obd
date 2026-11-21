package com.pedro.yarisobd.sim

import com.pedro.yarisobd.telemetry.DtcCodes
import kotlin.math.roundToInt

/**
 * Emulador de um adaptador ELM327 conversando com o modelo de veículo.
 *
 * Por que emular o adaptador em vez de injetar valores direto no app: assim o
 * caminho de produção inteiro é exercitado — envio do comando, temporização,
 * eco, prompt `>`, resposta em hexadecimal, `NO DATA`, máscara de PIDs
 * suportados, códigos de falha. O que for testado aqui é o mesmo código que vai
 * ler o scanner físico.
 *
 * O que **não** é fiel e deve ser tratado como aproximação:
 * - o formato textual de respostas multi-frame (VIN) não foi validado contra um
 *   ELM327 real;
 * - não há arbitragem de barramento, colisão nem erro de ISO-TP;
 * - a latência é simulada no transporte, não aqui (esta classe é pura).
 *
 * Referência do protocolo: comandos AT públicos do ELM327 e SAE J1979.
 */
class Elm327Emulator(private val vehicle: VehicleSimulator) {

    // ---- estado configurável pelos comandos AT ----

    private var echo = true
    private var linefeeds = false
    private var spaces = true
    private var headers = false
    private var canAutoFormat = true
    private var protocolNumber = "6"

    /** Após reset/protocolo automático, o primeiro pedido imprime "SEARCHING...". */
    private var searchingPending = true

    val description: String = "ELM327 v1.5 (simulado)"

    /**
     * Processa uma linha de comando (sem o `\r`) e devolve exatamente o texto
     * que o adaptador enviaria de volta, incluindo eco e prompt `>`.
     */
    fun processCommand(line: String): String {
        val command = line.trim()
        val out = StringBuilder()

        if (echo) out.append(command).append('\r')

        val upper = command.uppercase()
        val responseLines: List<String> = when {
            upper.isEmpty() -> listOf("")
            upper.startsWith("AT") -> handleAt(upper)
            else -> handleObd(upper)
        }

        for (responseLine in responseLines) {
            if (responseLine.isEmpty()) continue
            out.append(responseLine).append('\r')
            if (linefeeds) out.append('\n')
        }
        out.append('\r').append('>')
        return out.toString()
    }

    // ---- comandos AT ----

    private fun handleAt(command: String): List<String> {
        val compact = command.replace(" ", "")

        return when {
            compact == "ATZ" -> {
                echo = true
                linefeeds = false
                spaces = true
                headers = false
                canAutoFormat = true
                protocolNumber = "6"
                searchingPending = true
                listOf("", description)
            }

            compact == "ATI" -> listOf(description)
            compact == "AT@1" -> listOf("OBDII to RS232 Interpreter")
            compact == "AT@2" -> listOf("?")
            compact == "ATE0" -> { echo = false; ok() }
            compact == "ATE1" -> { echo = true; ok() }
            compact == "ATL0" -> { linefeeds = false; ok() }
            compact == "ATL1" -> { linefeeds = true; ok() }
            compact == "ATS0" -> { spaces = false; ok() }
            compact == "ATS1" -> { spaces = true; ok() }
            compact == "ATH0" -> { headers = false; ok() }
            compact == "ATH1" -> { headers = true; ok() }
            compact == "ATCAF0" -> { canAutoFormat = false; ok() }
            compact == "ATCAF1" -> { canAutoFormat = true; ok() }
            compact.startsWith("ATSP") -> {
                val requested = compact.removePrefix("ATSP")
                protocolNumber = if (requested == "0") "6" else requested
                searchingPending = true
                ok()
            }
            compact == "ATDPN" -> listOf(if (protocolNumber == "6") "A6" else protocolNumber)
            compact == "ATDP" -> listOf("AUTO, ISO 15765-4 (CAN 11/500)")
            compact == "ATRV" -> listOf(String.format("%.1fV", vehicle.snapshot().voltage))
            compact == "ATIGN" -> listOf("ON")
            compact == "ATPC" -> ok()
            compact.startsWith("ATST") -> ok()
            compact.startsWith("ATAL") -> ok()
            compact.startsWith("ATIB") -> ok()
            compact.startsWith("ATCFC") -> ok()
            compact.startsWith("ATSH") -> ok()
            compact.startsWith("ATTA") -> ok()
            compact == "AT" -> ok()
            else -> listOf("?")
        }
    }

    private fun ok(): List<String> = listOf("OK")

    // ---- requisições OBD ----

    private fun handleObd(command: String): List<String> {
        val hex = command.replace(" ", "").replace("\r", "").replace("\n", "")
        if (hex.isEmpty() || hex.length % 2 != 0 || hex.any { it !in "0123456789ABCDEFabcdef" }) {
            return listOf("?")
        }

        val bytes = hex.chunked(2).map { it.toInt(16) }.toIntArray()
        if (bytes.isEmpty()) return listOf("?")

        val snapshot = vehicle.snapshot()

        if (!snapshot.engineOn && bytes[0] == 0x01) {
            return listOf("NO DATA")
        }

        val prefix = if (searchingPending && bytes[0] == 0x01) {
            searchingPending = false
            listOf("SEARCHING...")
        } else {
            emptyList()
        }

        val results = when (bytes[0]) {
            0x01 -> {
                val pids = if (bytes.size > 1) bytes.drop(1) else listOf(0x00)
                pids.map { pid -> mode01(pid, snapshot) ?: "NO DATA" }
            }
            0x03 -> listOf(mode03(snapshot))
            0x04 -> {
                vehicle.clearDtcs()
                listOf(frame(intArrayOf(0x44)))
            }
            0x07 -> listOf(modeDtcList(0x47, snapshot.pendingDtcs))
            0x09 -> listOf(if (bytes.getOrElse(1) { 0 } == 0x02) mode09Vin(snapshot) else mode09Generic(bytes.getOrElse(1) { 0 }))
            0x0A -> listOf(modeDtcList(0x4A, emptyList()))
            else -> listOf("NO DATA")
        }

        return prefix + results
    }

    /** Resposta do Modo 01 para um PID, ou null quando não suportado. */
    private fun mode01(pid: Int, s: VehicleSnapshot): String? = when (pid) {
        0x00 -> frame(mask(0x00))
        0x20 -> frame(mask(0x20))
        0x40 -> frame(mask(0x40))
        0x60 -> frame(mask(0x60))

        0x01 -> frame(
            intArrayOf(
                0x41, 0x01,
                if (s.milOn) 0x80 or (s.dtcCount and 0x7F) else s.dtcCount and 0x7F,
                0x07, 0x65, 0x04,
            ),
        )

        0x04 -> frame(intArrayOf(0x41, 0x04, percentByte(s.loadPct)))
        0x05 -> frame(intArrayOf(0x41, 0x05, (s.coolantC + 40).roundToInt().coerceIn(0, 255)))
        0x0B -> frame(intArrayOf(0x41, 0x0B, s.mapKpa.roundToInt().coerceIn(0, 255)))
        0x0C -> {
            val raw = (s.rpm * 4).roundToInt().coerceIn(0, 65535)
            frame(intArrayOf(0x41, 0x0C, (raw shr 8) and 0xFF, raw and 0xFF))
        }
        0x0D -> frame(intArrayOf(0x41, 0x0D, s.speedKph.roundToInt().coerceIn(0, 255)))
        0x0E -> frame(intArrayOf(0x41, 0x0E, ((s.timingAdvanceDeg + 64) * 2).roundToInt().coerceIn(0, 255)))
        0x0F -> frame(intArrayOf(0x41, 0x0F, (s.intakeC + 40).roundToInt().coerceIn(0, 255)))
        0x10 -> {
            val raw = (s.mafGps * 100).roundToInt().coerceIn(0, 65535)
            frame(intArrayOf(0x41, 0x10, (raw shr 8) and 0xFF, raw and 0xFF))
        }
        0x11 -> frame(intArrayOf(0x41, 0x11, percentByte(s.throttlePct)))
        0x1C -> frame(intArrayOf(0x41, 0x1C, 0x01))
        0x1F -> {
            val seconds = s.runTimeSec.toInt().coerceIn(0, 65535)
            frame(intArrayOf(0x41, 0x1F, (seconds shr 8) and 0xFF, seconds and 0xFF))
        }
        0x21 -> {
            val km = if (s.milOn) s.distanceTotalKm.toInt().coerceIn(0, 65535) else 0
            frame(intArrayOf(0x41, 0x21, (km shr 8) and 0xFF, km and 0xFF))
        }
        0x2F -> frame(intArrayOf(0x41, 0x2F, percentByte(s.fuelLevelPct)))
        0x31 -> {
            val km = s.distanceSinceClearKm.toInt().coerceIn(0, 65535)
            frame(intArrayOf(0x41, 0x31, (km shr 8) and 0xFF, km and 0xFF))
        }
        0x33 -> frame(intArrayOf(0x41, 0x33, s.baroKpa.roundToInt().coerceIn(0, 255)))
        0x42 -> {
            val raw = (s.voltage * 1000).roundToInt().coerceIn(0, 65535)
            frame(intArrayOf(0x41, 0x42, (raw shr 8) and 0xFF, raw and 0xFF))
        }
        0x52 -> frame(intArrayOf(0x41, 0x52, percentByte(s.ethanolPct)))

        else -> null
    }

    private fun mode03(s: VehicleSnapshot): String {
        val codes = s.confirmedDtcs.mapNotNull { DtcCodes.parse(it) }
        val payload = mutableListOf(0x43, codes.size)
        codes.forEach { code ->
            payload += (code shr 8) and 0xFF
            payload += code and 0xFF
        }
        return frame(payload.toIntArray())
    }

    private fun modeDtcList(service: Int, codes: List<String>): String {
        val parsed = codes.mapNotNull { DtcCodes.parse(it) }
        val payload = mutableListOf(service, parsed.size)
        parsed.forEach { code ->
            payload += (code shr 8) and 0xFF
            payload += code and 0xFF
        }
        return frame(payload.toIntArray())
    }

    /** VIN em multi-frame, no formato textual do ELM327. */
    private fun mode09Vin(s: VehicleSnapshot): String {
        val vinBytes = s.vid.toByteArray(Charsets.US_ASCII).map { it.toInt() and 0xFF }
        val payload = mutableListOf(0x49, 0x02, 0x01)
        payload += vinBytes

        val padded = payload.toMutableList()
        while (padded.size % 7 != 0) padded += 0x00

        val header = String.format("%03X", payload.size)
        val sb = StringBuilder(header)
        var frameIndex = 0
        var offset = 0
        while (offset < padded.size) {
            val chunk = padded.subList(offset, minOf(offset + 7, padded.size))
            sb.append('\r').append(frameIndex).append(": ")
            if (spaces) {
                sb.append(chunk.joinToString(" ") { String.format("%02X", it) })
            } else {
                sb.append(chunk.joinToString("") { String.format("%02X", it) })
            }
            frameIndex++
            offset += 7
        }
        return sb.toString()
    }

    private fun mode09Generic(pid: Int): String = when (pid) {
        0x00 -> frame(intArrayOf(0x49, 0x00, 0x55, 0x40, 0x00, 0x00))
        0x0A -> frame(intArrayOf(0x49, 0x0A, 0x01, 0x45, 0x43, 0x4D, 0x2D, 0x53, 0x49, 0x4D))
        else -> "NO DATA"
    }

    // ---- montagem das respostas ----

    /**
     * Aplica as preferências de formatação (espaços e cabeçalho) a um quadro.
     * Com cabeçalho ligado o formato é `7E8 <tamanho> <bytes...>`.
     */
    private fun frame(data: IntArray): String {
        val body = if (headers) intArrayOf(0x07, 0xE8, data.size) + data else data
        return if (spaces) {
            body.joinToString(" ") { String.format("%02X", it) }
        } else {
            body.joinToString("") { String.format("%02X", it) }
        }
    }

    /** Máscara de PIDs suportados de 0x00 a 0x60 (4 bytes cada). */
    private fun mask(base: Int): IntArray {
        val bytes = IntArray(4)
        for (index in 0 until 32) {
            val pid = base + 1 + index
            if (pid in SUPPORTED_PIDS) {
                val byteIndex = index / 8
                val bit = 7 - (index % 8)
                bytes[byteIndex] = bytes[byteIndex] or (1 shl bit)
            }
        }
        return intArrayOf(0x41, base) + bytes
    }

    private fun percentByte(percent: Double): Int =
        (percent / 100.0 * 255.0).roundToInt().coerceIn(0, 255)

    companion object {
        /**
         * PIDs que este "carro" declara suportar. Escolhidos para se parecerem
         * com uma ECU de Yaris flex, incluindo o 0x52 (teor de etanol) que é a
         * hipótese mais útil do projeto — e deixando de fora 0x5C (temperatura
         * de óleo) e 0x5E (vazão de combustível), provavelmente ausentes neste
         * carro.
         */
        val SUPPORTED_PIDS: Set<Int> = setOf(
            0x01, 0x04, 0x05, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
            0x10, 0x11, 0x1C, 0x1F, 0x21, 0x2F, 0x31, 0x33,
            0x42, 0x52,
        )
    }
}
