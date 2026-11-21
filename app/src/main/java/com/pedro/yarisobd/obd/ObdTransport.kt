package com.pedro.yarisobd.obd

import com.pedro.yarisobd.sim.Elm327Emulator
import com.pedro.yarisobd.sim.VehicleSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import java.io.OutputStream

/**
 * Um adaptador OBD2 conectado: um canal de entrada e um de saída.
 *
 * A camada acima (sessão ELM) só conhece estes dois streams. Isso é o que
 * permite trocar adaptador virtual por Bluetooth sem mexer no resto do código.
 */
class ObdLink(
    val input: InputStream,
    val output: OutputStream,
)

/** Origem física do adaptador. */
interface ObdTransport {
    /** Texto para exibir na interface. */
    val description: String

    /** `true` quando os dados são produzidos localmente, não pelo carro. */
    val isVirtual: Boolean

    suspend fun open(): ObdLink

    fun close()
}

/**
 * Fila de bytes em memória com leitura com timeout. Usada para ligar o app ao
 * emulador com a mesma semântica de um stream de Bluetooth (que bloqueia e
 * entrega bytes aos poucos).
 */
class InMemoryPipe {

    private val lock = Object()
    private val queue = ArrayDeque<Byte>()
    private var closed = false

    fun write(bytes: ByteArray) {
        synchronized(lock) {
            if (closed) return
            bytes.forEach { queue.addLast(it) }
            lock.notifyAll()
        }
    }

    fun read(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Long): Int {
        synchronized(lock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (queue.isEmpty() && !closed) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return 0
                try {
                    lock.wait(minOf(remaining, 25))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return -1
                }
            }
            if (queue.isEmpty()) return if (closed) -1 else 0

            var count = 0
            while (count < length && queue.isNotEmpty()) {
                buffer[offset + count] = queue.removeFirst()
                count++
            }
            return count
        }
    }

    fun available(): Int = synchronized(lock) { queue.size }

    fun close() {
        synchronized(lock) {
            closed = true
            lock.notifyAll()
        }
    }
}

private class PipeInputStream(private val pipe: InMemoryPipe) : InputStream() {
    override fun read(): Int {
        val one = ByteArray(1)
        val read = read(one, 0, 1)
        return if (read <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int = pipe.read(b, off, len, 200)

    override fun available(): Int = pipe.available()

    override fun close() = pipe.close()
}

private class PipeOutputStream(private val pipe: InMemoryPipe) : OutputStream() {
    override fun write(b: Int) = pipe.write(byteArrayOf(b.toByte()))

    override fun write(b: ByteArray, off: Int, len: Int) = pipe.write(b.copyOfRange(off, off + len))

    override fun close() = pipe.close()
}

/**
 * Adaptador ELM327 **virtual**: liga o app ao [Elm327Emulator], que conversa
 * com o [VehicleSimulator].
 *
 * A latência por comando é simulada de propósito. Um clone ELM327 real leva
 * dezenas de milissegundos por comando; sem essa espera o app pareceria muito
 * mais rápido do que será no carro e o orçamento de leitura ficaria irreal.
 */
class SimulatedTransport(
    private val vehicle: VehicleSimulator,
    private val latencyMs: Long = DEFAULT_LATENCY_MS,
) : ObdTransport {

    override val description: String = "Adaptador virtual (ELM327 emulado)"

    override val isVirtual: Boolean = true

    private var deviceThread: Thread? = null

    override suspend fun open(): ObdLink = suspendCancellableCoroutine { continuation ->
        val toDevice = InMemoryPipe()
        val fromDevice = InMemoryPipe()
        val emulator = Elm327Emulator(vehicle)

        val worker = Thread({
            val lineBuffer = StringBuilder()
            val readBuffer = ByteArray(256)
            while (!Thread.currentThread().isInterrupted) {
                val read = toDevice.read(readBuffer, 0, readBuffer.size, 100)
                if (read < 0) break
                if (read == 0) continue

                for (index in 0 until read) {
                    val char = readBuffer[index].toInt().toChar()
                    when (char) {
                        '\r' -> {
                            val line = lineBuffer.toString()
                            lineBuffer.setLength(0)
                            if (line.isBlank()) continue
                            Thread.sleep(latencyMs)
                            val response = emulator.processCommand(line)
                            fromDevice.write(response.toByteArray(Charsets.US_ASCII))
                        }
                        '\n' -> Unit
                        else -> lineBuffer.append(char)
                    }
                }
            }
        }, "elm327-virtual-device").apply {
            isDaemon = true
            start()
        }

        deviceThread = worker
        continuation.resumeWith(Result.success(ObdLink(PipeInputStream(fromDevice), PipeOutputStream(toDevice))))
    }

    override fun close() {
        deviceThread?.interrupt()
        deviceThread = null
    }

    companion object {
        const val DEFAULT_LATENCY_MS = 12L
    }
}
