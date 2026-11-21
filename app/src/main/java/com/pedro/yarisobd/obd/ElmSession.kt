package com.pedro.yarisobd.obd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sessão de conversa com um adaptador ELM327.
 *
 * Regra crítica do ELM327: **um comando por vez**. Ele não tem fila; mandar dois
 * comandos sem esperar a resposta produz `STOPPED`, respostas trocadas ou lixo
 * no buffer. Por isso toda escrita passa por um `Mutex`.
 */
class ElmSession(private val link: ObdLink) {

    private val mutex = Mutex()

    suspend fun execute(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ElmResponse =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val response = StringBuilder()
                try {
                    link.output.write((command + "\r").toByteArray(Charsets.US_ASCII))
                    link.output.flush()
                } catch (error: Exception) {
                    return@withLock ElmResponse(command, "", error.message)
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(512)
                var sawPrompt = false

                while (System.currentTimeMillis() < deadline) {
                    val available = try {
                        link.input.available()
                    } catch (error: Exception) {
                        return@withLock ElmResponse(command, response.toString(), error.message)
                    }

                    if (available > 0) {
                        val read = try {
                            link.input.read(buffer, 0, minOf(available, buffer.size))
                        } catch (error: Exception) {
                            -1
                        }
                        if (read > 0) {
                            response.append(String(buffer, 0, read, Charsets.US_ASCII))
                            if (response.contains('>')) {
                                sawPrompt = true
                                break
                            }
                        }
                    } else {
                        Thread.sleep(2)
                    }
                }

                val text = response.toString()
                val error = when {
                    !sawPrompt -> "timeout aguardando '>'"
                    else -> null
                }

                ConsoleLog.add('>', command)
                ConsoleLog.add('<', text.ifBlank { "(sem resposta)" })

                ElmResponse(command, text, error)
            }
        }

    fun close() {
        runCatching { link.input.close() }
        runCatching { link.output.close() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3_000L
    }
}

/**
 * Resposta crua de um comando. A interpretação (hex, `NO DATA`, `OK`) fica aqui
 * para o resto do código não ter que lidar com texto do adaptador.
 */
class ElmResponse(
    val command: String,
    val rawText: String,
    val transportError: String? = null,
) {
    val lines: List<String> = rawText
        .split('\r', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != ">" }
        .filter { !it.equals(command.trim(), ignoreCase = true) }

    val isOk: Boolean get() = lines.any { it.equals("OK", true) }
    val isNoData: Boolean get() = lines.any { it.contains("NO DATA", true) }
    val isSearching: Boolean get() = lines.any { it.contains("SEARCHING", true) }
    val isError: Boolean
        get() = lines.any {
            it == "?" || it.startsWith("ERROR") || it.contains("UNABLE", true) || it == "STOPPED"
        }

    /**
     * Bytes de dados da resposta, ignorando avisos (`SEARCHING...`), `OK` e
     * cabeçalho de 3 dígitos quando `ATH1` está ligado.
     */
    fun hexBytes(): ByteArray {
        val result = mutableListOf<Byte>()
        for (line in lines) {
            if (line.contains("SEARCHING", true) || line.contains("BUS INIT", true)) continue
            if (line.equals("OK", true) || line.equals("STOPPED", true)) continue

            val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue

            val hasHeader = tokens.first().length == 3 && tokens.first().all { it.isHexDigit() }
            val dataTokens = if (hasHeader) tokens.drop(1) else tokens
            val hex = dataTokens.joinToString("")
            if (hex.isEmpty() || hex.any { !it.isHexDigit() } || hex.length % 2 != 0) continue

            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }
            // Com cabeçalho vem também o byte de tamanho (PCI); descarta.
            val from = if (hasHeader && bytes.isNotEmpty()) 1 else 0
            result += bytes.drop(from)
        }
        return result.toByteArray()
    }

    /**
     * Extrai o VIN de uma resposta multi-frame do ELM327, no formato
     * `014` / `0: 49 02 01 ...` / `1: ...`.
     */
    fun multiFrameAscii(): String? {
        val frames = lines.filter { it.matches(Regex("^\\d+:\\s*([0-9A-Fa-f]{2}\\s*)+")) }
        if (frames.isEmpty()) return null

        val bytes = mutableListOf<Byte>()
        for (frame in frames) {
            val payload = frame.substringAfter(':').trim()
            val hex = payload.replace(" ", "")
            if (hex.isEmpty() || hex.any { !it.isHexDigit() } || hex.length % 2 != 0) continue
            bytes += hex.chunked(2).map { it.toInt(16).toByte() }
        }
        if (bytes.size < 4) return null

        // 49 02 01 seguidos dos bytes ASCII do VIN.
        val start = 3
        return bytes.drop(start)
            .takeWhile { it != 0.toByte() }
            .map { (it.toInt() and 0xFF).toChar() }
            .joinToString("")
            .trim()
            .ifEmpty { null }
    }

    override fun toString(): String = rawText.trim()
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
