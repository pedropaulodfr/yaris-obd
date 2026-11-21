package com.pedro.yarisobd.telemetry

/**
 * De onde veio a leitura atual. A interface é obrigada a deixar visível quando
 * o dado **não** vem do veículo.
 */
enum class DataSource {
    /** Sem leitura nenhuma. */
    NONE,

    /** Valores gerados direto no app, sem passar pelo protocolo OBD. */
    DEMO,

    /**
     * Valores que passaram de verdade pelo caminho OBD (comandos, respostas
     * hex, decodificação), mas produzidos por um **adaptador virtual**
     * alimentado por um modelo de veículo simulado.
     */
    EMULATED,

    /** Adaptador físico lendo a ECU do veículo. */
    REAL,
}

/**
 * Uma leitura de telemetria do veículo.
 *
 * Regra central do projeto: `null` significa **indisponível** (PID não suportado
 * pela ECU, ou ainda não lido). Nunca preencher com `0.0` para representar
 * "não disponível" — zero é um valor de motor legítimo e confundir os dois
 * produz um painel que mente.
 */
data class Telemetry(
    /** PID 0x0D — velocidade do veículo, km/h. */
    val speedKph: Double? = null,
    /** PID 0x0C — rotação do motor, rpm. */
    val rpm: Double? = null,
    /** PID 0x05 — temperatura do líquido de arrefecimento, °C. */
    val coolantC: Double? = null,
    /** PID 0x0F — temperatura do ar de admissão, °C. */
    val intakeC: Double? = null,
    /** PID 0x04 — carga calculada do motor, %. */
    val loadPct: Double? = null,
    /** PID 0x11 — posição do acelerador, %. */
    val throttlePct: Double? = null,
    /** PID 0x0B — pressão do coletor de admissão, kPa. */
    val mapKpa: Double? = null,
    /** PID 0x10 — fluxo de ar pelo MAF, g/s. */
    val mafGps: Double? = null,
    /** PID 0x2F — nível de combustível, %. */
    val fuelLevelPct: Double? = null,
    /** PID 0x33 — pressão barométrica, kPa. */
    val baroKpa: Double? = null,
    /** PID 0x42 — tensão do módulo de controle, V. */
    val moduleVoltage: Double? = null,
    /** PID 0x52 — porcentagem de etanol no combustível, %. */
    val ethanolPct: Double? = null,
    /** PID 0x0E — avanço de ignição, graus antes do PMS. */
    val timingAdvanceDeg: Double? = null,
    /** PID 0x01 — luz de injeção acesa. */
    val milOn: Boolean = false,
    /** PID 0x01 — quantidade de DTCs confirmados. */
    val dtcCount: Int = 0,

    val source: DataSource = DataSource.NONE,

    /** Momento da última atualização (epoch ms). 0 = nunca atualizado. */
    val updatedAtMs: Long = 0L,
) {
    /** Dado velho: conexão viva mas sem leitura nova dentro do limite. */
    fun isStale(nowMs: Long, timeoutMs: Long = STALE_TIMEOUT_MS): Boolean =
        updatedAtMs == 0L || nowMs - updatedAtMs > timeoutMs

    /** Dado que não veio do veículo, por qualquer motivo. */
    val isSimulated: Boolean
        get() = source == DataSource.DEMO || source == DataSource.EMULATED

    companion object {
        /**
         * 3 s sem atualização = dado velho. Um ciclo de leitura de ~10 PIDs em
         * ELM327 fica na casa de 0,3-1,0 s, então 3 s indica problema real
         * (adaptador travado, contato ruim) e não apenas lentidão normal.
         */
        const val STALE_TIMEOUT_MS = 3_000L

        val EMPTY = Telemetry()
    }
}
