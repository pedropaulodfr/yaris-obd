package com.pedro.yarisobd.render

/**
 * Os 12 layouts aprovados no laboratório (`docs/mockups`), na ordem em que
 * aparecem lá.
 *
 * Cada um responde a um uso diferente: dirigir, diagnosticar, economizar
 * combustível, olhar temperatura. A escolha é feita no celular e aplica na
 * central em até 1 segundo.
 */
enum class DashboardStyle(
    val id: String,
    val label: String,
    val description: String,
) {
    AGULHAS(
        "agulhas",
        "Agulhas",
        "Dois relógios com agulha e escala gravada. Familiar para quem já dirigiu qualquer carro.",
    ),
    AGULHAS_GRANDE(
        "agulhas-grande",
        "Agulhas Grande",
        "Dois mostradores enormes dominando a tela, com os demais indicadores numa faixa fina embaixo.",
    ),
    FITAS(
        "fitas",
        "Fitas",
        "Velocidade e rotação em fitas graduadas que rolam, como instrumentação de precisão.",
    ),
    COCKPIT(
        "cockpit",
        "Cockpit",
        "Rotação no topo com luzes de troca, velocidade dominante e marcha em destaque.",
    ),
    ECO(
        "eco",
        "Eco",
        "Anel de eficiência e consumo instantâneo. Feito para dirigir econômico.",
    ),
    TERMICO(
        "termico",
        "Térmico",
        "Temperatura da água como informação principal, com faixas de operação.",
    ),
    TENDENCIA(
        "tendencia",
        "Tendência",
        "Gráficos dos últimos 30 segundos: mostra para onde o motor está indo.",
    ),
    RADAR(
        "radar",
        "Radar",
        "Seis eixos normalizados numa teia: leitura de conjunto, não de número.",
    ),
    DIAGNOSTICO(
        "diagnostico",
        "Diagnóstico",
        "Estado do sistema, códigos de falha e saúde do enlace.",
    ),
    VIAGEM(
        "viagem",
        "Viagem",
        "Computador de bordo: distância, tempo, médias e autonomia.",
    ),
    DIURNO(
        "diurno",
        "Diurno",
        "Tema claro, para uso com sol forte. Mesma hierarquia, contraste invertido.",
    ),
    HUD(
        "hud",
        "HUD",
        "Só o essencial em tamanho enorme: velocidade, rotação e quatro números.",
    ),
    MOSAICO(
        "mosaico",
        "Mosaico",
        "Cartões de tamanhos diferentes: velocidade herói, anéis e medidores menores.",
    ),
    ;

    companion object {
        fun fromId(id: String?): DashboardStyle =
            entries.firstOrNull { it.id == id } ?: AGULHAS
    }
}

/** Contrato de desenho de um layout. */
interface DashboardStyleRenderer {
    val style: DashboardStyle

    fun draw(
        canvas: android.graphics.Canvas,
        width: Int,
        height: Int,
        data: com.pedro.yarisobd.telemetry.Telemetry,
        nowMs: Long,
        fps: Float,
    )
}
