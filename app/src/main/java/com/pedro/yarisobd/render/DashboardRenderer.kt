package com.pedro.yarisobd.render

import android.content.Context
import android.graphics.Canvas
import com.pedro.yarisobd.render.styles.AgulhasStyle
import com.pedro.yarisobd.render.styles.AgulhasGrandeStyle
import com.pedro.yarisobd.render.styles.CockpitStyle
import com.pedro.yarisobd.render.styles.DiagStyle
import com.pedro.yarisobd.render.styles.DiurnoStyle
import com.pedro.yarisobd.render.styles.EcoStyle
import com.pedro.yarisobd.render.styles.FitasStyle
import com.pedro.yarisobd.render.styles.HudStyle
import com.pedro.yarisobd.render.styles.MosaicoStyle
import com.pedro.yarisobd.render.styles.RadarStyle
import com.pedro.yarisobd.render.styles.TendenciaStyle
import com.pedro.yarisobd.render.styles.TermicoStyle
import com.pedro.yarisobd.render.styles.ViagemStyle
import com.pedro.yarisobd.telemetry.Telemetry

/**
 * Desenha o painel no `Canvas` da superfície do Android Auto, delegando para o
 * layout escolhido (os 12 aprovados no laboratório de `docs/mockups`).
 *
 * O layout é lido de preferência e pode mudar com a tela do carro já aberta.
 */
class DashboardRenderer(
    context: Context,
    initialStyle: DashboardStyle = DashboardStyle.AGULHAS,
) {

    private val appContext = context.applicationContext
    private val implementations = mutableMapOf<DashboardStyle, DashboardStyleRenderer>()

    var style: DashboardStyle = initialStyle

    fun draw(canvas: Canvas, width: Int, height: Int, data: Telemetry, nowMs: Long, fps: Float) {
        val renderer = implementations[style] ?: create(style).also { implementations[style] = it }
        renderer.draw(canvas, width, height, data, nowMs, fps)
    }

    private fun create(style: DashboardStyle): DashboardStyleRenderer = when (style) {
        DashboardStyle.AGULHAS -> AgulhasStyle(appContext)
        DashboardStyle.AGULHAS_GRANDE -> AgulhasGrandeStyle(appContext)
        DashboardStyle.FITAS -> FitasStyle(appContext)
        DashboardStyle.COCKPIT -> CockpitStyle(appContext)
        DashboardStyle.ECO -> EcoStyle(appContext)
        DashboardStyle.TERMICO -> TermicoStyle(appContext)
        DashboardStyle.TENDENCIA -> TendenciaStyle(appContext)
        DashboardStyle.RADAR -> RadarStyle(appContext)
        DashboardStyle.DIAGNOSTICO -> DiagStyle(appContext)
        DashboardStyle.VIAGEM -> ViagemStyle(appContext)
        DashboardStyle.DIURNO -> DiurnoStyle(appContext)
        DashboardStyle.HUD -> HudStyle(appContext)
        DashboardStyle.MOSAICO -> MosaicoStyle(appContext)
    }
}
