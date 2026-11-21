package com.pedro.yarisobd.telemetry

/**
 * Descrições locais de códigos de falha.
 *
 * Regras desta base:
 * - só entram códigos **genéricos** da norma SAE J2012, cuja descrição é
 *   padronizada e verificável;
 * - códigos do fabricante (`P1xxx`, `P2xxx`, `P3xxx`, além de `C/B/U1xxx`)
 *   **não** são interpretados: a ECU muda o significado por montadora e inventar
 *   uma descrição aqui seria mentir sobre o defeito;
 * - a "gravidade" é orientação de leitura, não diagnóstico.
 *
 * O app deve sempre deixar claro que a interpretação é genérica e que a
 * confirmação do defeito é trabalho de um mecânico com o manual do veículo.
 */
enum class DtcSeverity {
    /** Pode esperar: não afeta a condução imediata. */
    ATENCAO,

    /** Afeta consumo, emissões ou desempenho; deve ser verificado. */
    MODERADA,

    /** Risco de dano ao motor/catalisador ou perda de função: tratar logo. */
    GRAVE,

    /** Sem interpretação local: código do fabricante ou não catalogado. */
    DESCONHECIDA,
}

data class DtcInfo(
    val code: String,
    val description: String,
    val severity: DtcSeverity,
)

object DtcCatalog {

    private val known: Map<String, DtcInfo> = listOf(
        DtcInfo("P0100", "Circuito do medidor de fluxo de ar (MAF)", DtcSeverity.MODERADA),
        DtcInfo("P0101", "Faixa/desempenho do MAF fora do esperado", DtcSeverity.MODERADA),
        DtcInfo("P0102", "Sinal do MAF abaixo do mínimo", DtcSeverity.MODERADA),
        DtcInfo("P0103", "Sinal do MAF acima do máximo", DtcSeverity.MODERADA),
        DtcInfo("P0112", "Sinal do sensor de temperatura do ar baixo", DtcSeverity.ATENCAO),
        DtcInfo("P0113", "Sinal do sensor de temperatura do ar alto", DtcSeverity.ATENCAO),
        DtcInfo("P0115", "Circuito do sensor de temperatura do motor", DtcSeverity.MODERADA),
        DtcInfo("P0117", "Sinal do sensor de temperatura do motor baixo", DtcSeverity.MODERADA),
        DtcInfo("P0118", "Sinal do sensor de temperatura do motor alto", DtcSeverity.MODERADA),
        DtcInfo("P0120", "Circuito do sensor de posição da borboleta", DtcSeverity.MODERADA),
        DtcInfo("P0121", "Faixa do sensor de borboleta fora do esperado", DtcSeverity.MODERADA),
        DtcInfo("P0122", "Sinal do sensor de borboleta baixo", DtcSeverity.MODERADA),
        DtcInfo("P0123", "Sinal do sensor de borboleta alto", DtcSeverity.MODERADA),
        DtcInfo("P0171", "Mistura pobre no banco 1 (ar demais ou combustível de menos)", DtcSeverity.MODERADA),
        DtcInfo("P0172", "Mistura rica no banco 1 (combustível demais)", DtcSeverity.MODERADA),
        DtcInfo("P0300", "Falhas de combustão aleatórias/múltiplos cilindros", DtcSeverity.GRAVE),
        DtcInfo("P0301", "Falha de combustão no cilindro 1", DtcSeverity.GRAVE),
        DtcInfo("P0302", "Falha de combustão no cilindro 2", DtcSeverity.GRAVE),
        DtcInfo("P0303", "Falha de combustão no cilindro 3", DtcSeverity.GRAVE),
        DtcInfo("P0304", "Falha de combustão no cilindro 4", DtcSeverity.GRAVE),
        DtcInfo("P0335", "Circuito do sensor de rotação do virabrequim", DtcSeverity.GRAVE),
        DtcInfo("P0340", "Circuito do sensor de fase do comando", DtcSeverity.MODERADA),
        DtcInfo("P0401", "Fluxo insuficiente na recirculação de gases (EGR)", DtcSeverity.ATENCAO),
        DtcInfo("P0420", "Eficiência do catalisador abaixo do limite (banco 1)", DtcSeverity.MODERADA),
        DtcInfo("P0430", "Eficiência do catalisador abaixo do limite (banco 2)", DtcSeverity.MODERADA),
        DtcInfo("P0442", "Pequeno vazamento no sistema de vapores (EVAP)", DtcSeverity.ATENCAO),
        DtcInfo("P0455", "Grande vazamento no sistema de vapores (EVAP)", DtcSeverity.ATENCAO),
        DtcInfo("P0456", "Vazamento muito pequeno no sistema de vapores (EVAP)", DtcSeverity.ATENCAO),
        DtcInfo("P0500", "Sensor de velocidade do veículo", DtcSeverity.MODERADA),
        DtcInfo("P0505", "Sistema de controle de marcha lenta", DtcSeverity.ATENCAO),
        DtcInfo("P0562", "Tensão do sistema baixa", DtcSeverity.MODERADA),
        DtcInfo("P0563", "Tensão do sistema alta", DtcSeverity.MODERADA),
        DtcInfo("P0700", "Falha no sistema de controle da transmissão", DtcSeverity.MODERADA),
    ).associateBy { it.code }

    fun describe(code: String): DtcInfo = known[code.uppercase()] ?: DtcInfo(
        code = code.uppercase(),
        description = "Código sem descrição genérica: pode ser específico do fabricante. " +
            "Consulte a documentação Toyota para este código.",
        severity = DtcSeverity.DESCONHECIDA,
    )

    fun severityLabel(severity: DtcSeverity): String = when (severity) {
        DtcSeverity.ATENCAO -> "Atenção"
        DtcSeverity.MODERADA -> "Verificar"
        DtcSeverity.GRAVE -> "Grave"
        DtcSeverity.DESCONHECIDA -> "Sem interpretação"
    }
}
