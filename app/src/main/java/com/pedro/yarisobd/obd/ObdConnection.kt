package com.pedro.yarisobd.obd

import com.pedro.yarisobd.telemetry.DataSource
import com.pedro.yarisobd.telemetry.DtcCodes
import com.pedro.yarisobd.telemetry.DtcRepository
import com.pedro.yarisobd.telemetry.Telemetry
import com.pedro.yarisobd.telemetry.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class ObdState {
    data object Disconnected : ObdState()

    data object Connecting : ObdState()

    data class Ready(
        val adapter: String,
        val protocol: String,
        val supportedPids: Set<Int>,
    ) : ObdState()

    data class Failed(val message: String) : ObdState()
}

/**
 * Conduz a conversa com o adaptador: inicialização, descoberta de PIDs
 * suportados, laço de leitura e comandos de diagnóstico.
 *
 * O laço lê poucos PIDs por ciclo de propósito: o gargalo real de um ELM327 é o
 * número de comandos por segundo (na casa de 10-20 num clone), não a banda. Ler
 * "tudo" a cada ciclo produz painel bonito com dado velho.
 */
class ObdConnection(
    private val transport: ObdTransport,
    private val repository: TelemetryRepository,
) {

    private val _state = MutableStateFlow<ObdState>(ObdState.Disconnected)
    val state: StateFlow<ObdState> = _state.asStateFlow()

    private var session: ElmSession? = null
    private var pollJob: Job? = null
    private var supported: Set<Int> = emptySet()
    private var latest = Telemetry()
    private var milOn = false
    private var dtcCount = 0

    private val source: DataSource =
        if (transport.isVirtual) DataSource.EMULATED else DataSource.REAL

    /** VIN lido da ECU, quando disponível. */
    @Volatile
    var vin: String? = null
        private set

    val isVirtual: Boolean get() = transport.isVirtual

    suspend fun connect(scope: CoroutineScope) {
        if (_state.value is ObdState.Ready) return
        _state.value = ObdState.Connecting

        try {
            val link = transport.open()
            val newSession = ElmSession(link)
            session = newSession

            // Sequência de inicialização: reset, sem eco, sem linefeed, sem
            // espaços, sem cabeçalho, protocolo automático.
            val reset = newSession.execute("ATZ", timeoutMs = 6_000)
            val adapter = reset.lines.lastOrNull { it.startsWith("ELM", ignoreCase = true) }
                ?: "adaptador desconhecido"

            listOf("ATE0", "ATL0", "ATS0", "ATH0").forEach { newSession.execute(it, 1_000) }

            newSession.execute("ATSP0", 1_000)
            var probe = newSession.execute("0100", 3_000)
            if (probe.isNoData || probe.isError) {
                // Alguns adaptadores não negociam automático com Toyota.
                newSession.execute("ATSP6", 1_000)
                probe = newSession.execute("0100", 3_000)
            }

            if (probe.isNoData || probe.isError) {
                _state.value = ObdState.Failed(
                    "Adaptador conectou, mas a ECU não respondeu. " +
                        "Isso é típico de clone ELM327 v2.1 incompatível.",
                )
                return
            }

            supported = readSupportedPids(newSession)

            val dpn = newSession.execute("ATDPN", 1_000)
            val protocol = describeProtocol(dpn.lines.firstOrNull())

            vin = readVin(newSession)

            _state.value = ObdState.Ready(adapter, protocol, supported)
            startPolling(scope, newSession)
        } catch (error: Exception) {
            _state.value = ObdState.Failed(error.message ?: "falha ao conectar")
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        session?.close()
        session = null
        transport.close()
        _state.value = ObdState.Disconnected
        repository.clear()
    }

    private fun startPolling(scope: CoroutineScope, session: ElmSession) {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            // PIDs rápidos: são os que precisam de atualização contínua.
            val fast = listOf(0x0C, 0x0D).filter { supports(it) }
            // PIDs lentos: um por ciclo, em rodízio.
            val slow = listOf(0x05, 0x0F, 0x04, 0x11, 0x0B, 0x10, 0x2F, 0x33, 0x42, 0x52, 0x0E)
                .filter { supports(it) }

            var slowIndex = 0
            var cycle = 0

            while (isActive) {
                val startedAt = System.currentTimeMillis()

                fast.forEach { pid -> readInto(pid, session) }
                if (slow.isNotEmpty()) {
                    readInto(slow[slowIndex % slow.size], session)
                    slowIndex++
                }

                // Estado da luz de injeção e contagem de DTCs: muda pouco,
                // não precisa estar em todo ciclo.
                if (cycle % 10 == 0) readMil(session)

                // Diagnóstico completo em rodízio lento: ler códigos custa
                // comandos e não muda a cada segundo.
                if (cycle % DIAGNOSTIC_EVERY_CYCLES == 0) refreshDiagnostics(session)

                cycle++
                latest = latest.copy(
                    source = source,
                    milOn = milOn,
                    dtcCount = dtcCount,
                    updatedAtMs = System.currentTimeMillis(),
                )
                repository.update(latest)

                val elapsed = System.currentTimeMillis() - startedAt
                delay((POLL_INTERVAL_MS - elapsed).coerceAtLeast(MIN_POLL_GAP_MS))
            }
        }
    }

    private fun supports(pid: Int): Boolean = supported.isEmpty() || pid in supported

    private suspend fun readInto(pid: Int, session: ElmSession) {
        val value = readPid(pid, session) ?: return
        latest = when (pid) {
            0x0C -> latest.copy(rpm = value)
            0x0D -> latest.copy(speedKph = value)
            0x05 -> latest.copy(coolantC = value)
            0x0F -> latest.copy(intakeC = value)
            0x04 -> latest.copy(loadPct = value)
            0x11 -> latest.copy(throttlePct = value)
            0x0B -> latest.copy(mapKpa = value)
            0x10 -> latest.copy(mafGps = value)
            0x2F -> latest.copy(fuelLevelPct = value)
            0x33 -> latest.copy(baroKpa = value)
            0x42 -> latest.copy(moduleVoltage = value)
            0x52 -> latest.copy(ethanolPct = value)
            0x0E -> latest.copy(timingAdvanceDeg = value)
            else -> latest
        }
    }

    private suspend fun readPid(pid: Int, session: ElmSession): Double? {
        val response = session.execute(String.format("01%02X", pid), 2_000)
        if (response.isNoData || response.isError) return null

        val data = response.hexBytes()
        if (data.size < 3) return null
        if (data[0].toInt() and 0xFF != 0x41) return null
        if (data[1].toInt() and 0xFF != pid) return null

        val spec = Pids.byId(pid) ?: return null
        val payload = data.copyOfRange(2, data.size)
        if (payload.size < spec.requiredBytes) return null
        return spec.decode(payload)
    }

    private suspend fun readMil(session: ElmSession) {
        if (!supports(0x01)) return
        val response = session.execute("0101", 2_000)
        if (response.isNoData || response.isError) return
        val data = response.hexBytes()
        if (data.size < 3) return
        if (data[0].toInt() and 0xFF != 0x41) return

        val a = data[2].toInt() and 0xFF
        milOn = (a and 0x80) != 0
        dtcCount = a and 0x7F
    }

    /** Descobre os PIDs suportados a partir das máscaras 0100/0120/0140/0160. */
    private suspend fun readSupportedPids(session: ElmSession): Set<Int> {
        val result = mutableSetOf<Int>()
        for (base in listOf(0x00, 0x20, 0x40, 0x60)) {
            val response = session.execute(String.format("01%02X", base), 2_000)
            if (response.isNoData || response.isError) continue

            val data = response.hexBytes()
            if (data.size < 6) continue
            if (data[0].toInt() and 0xFF != 0x41) continue

            for (index in 0 until 32) {
                val byteIndex = 2 + index / 8
                if (byteIndex >= data.size) break
                val bit = 7 - (index % 8)
                val isSet = ((data[byteIndex].toInt() and 0xFF) shr bit) and 1 == 1
                if (isSet) result += base + 1 + index
            }
        }
        return result
    }

    private suspend fun readVin(session: ElmSession): String? {
        val response = session.execute("0902", 4_000)
        if (response.isNoData || response.isError) return null
        return response.multiFrameAscii()?.takeIf { it.length >= 11 }
    }

    /** Lê DTCs confirmados e pendentes e publica no repositório compartilhado. */
    private suspend fun refreshDiagnostics(session: ElmSession) {
        val confirmed = readDtcs(session)
        val pending = readPendingDtcs(session)
        DtcRepository.update(confirmed, pending, milOn || confirmed.isNotEmpty())
    }

    /** DTCs confirmados (Modo 03). */
    suspend fun readDtcs(): List<String> {
        val session = session ?: return emptyList()
        return readDtcs(session)
    }

    private suspend fun readDtcs(session: ElmSession): List<String> {
        val response = session.execute("03", 3_000)
        if (response.isNoData || response.isError) return emptyList()

        val data = response.hexBytes()
        if (data.size < 2) return emptyList()
        if (data[0].toInt() and 0xFF != 0x43) return emptyList()
        return DtcCodes.parseList(data.copyOfRange(2, data.size))
    }

    /** DTCs pendentes (Modo 07). */
    suspend fun readPendingDtcs(): List<String> {
        val session = session ?: return emptyList()
        return readPendingDtcs(session)
    }

    private suspend fun readPendingDtcs(session: ElmSession): List<String> {
        val response = session.execute("07", 3_000)
        if (response.isNoData || response.isError) return emptyList()

        val data = response.hexBytes()
        if (data.size < 2) return emptyList()
        if (data[0].toInt() and 0xFF != 0x47) return emptyList()
        return DtcCodes.parseList(data.copyOfRange(2, data.size))
    }

    /**
     * Limpa DTCs (Modo 04). Só deve ser chamado com o veículo parado e após
     * confirmação explícita do usuário: apaga também freeze frame, readiness e
     * adaptativos aprendidos.
     */
    suspend fun clearDtcs(): Boolean {
        val session = session ?: return false
        val response = session.execute("04", 3_000)
        if (response.isNoData || response.isError) return false
        val data = response.hexBytes()
        val ok = data.isNotEmpty() && (data[0].toInt() and 0xFF == 0x44)
        if (ok) {
            dtcCount = 0
            milOn = false
            latest = latest.copy(dtcCount = 0, milOn = false)
            repository.update(latest)
            DtcRepository.clear()
        }
        return ok
    }

    /** Texto amigável do protocolo a partir do retorno de `ATDPN`. */
    private fun describeProtocol(raw: String?): String = when (raw?.trim()?.uppercase()) {
        "A6", "6" -> "ISO 15765-4 CAN (11 bits, 500 kbps)"
        "A7", "7" -> "ISO 15765-4 CAN (29 bits, 500 kbps)"
        "A8", "8" -> "ISO 15765-4 CAN (11 bits, 250 kbps)"
        "A9", "9" -> "ISO 15765-4 CAN (29 bits, 250 kbps)"
        "A3", "3" -> "ISO 9141-2"
        "A4", "4" -> "ISO 14230-4 KWP (5 baud)"
        "A5", "5" -> "ISO 14230-4 KWP (fast)"
        "A1", "1" -> "SAE J1850 PWM"
        "A2", "2" -> "SAE J1850 VPW"
        null, "" -> "não informado"
        else -> raw?.trim() ?: "não informado"
    }

    companion object {
        /** Período alvo de um ciclo completo de leitura. */
        const val POLL_INTERVAL_MS = 150L

        /** A cada quantos ciclos o diagnóstico completo é relido (~15 s). */
        const val DIAGNOSTIC_EVERY_CYCLES = 100

        private const val MIN_POLL_GAP_MS = 20L

        /** Formata um valor para a interface, com "—" quando indisponível. */
        fun format(value: Double?, decimals: Int = 0): String {
            if (value == null) return "—"
            return if (decimals == 0) {
                value.roundToInt().toString()
            } else {
                String.format("%.${decimals}f", value)
            }
        }
    }
}
