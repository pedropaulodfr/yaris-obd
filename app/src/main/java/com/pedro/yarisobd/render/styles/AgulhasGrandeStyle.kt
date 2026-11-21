package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/**
 * Agulhas Grande — mesma linguagem do layout "Agulhas", mas com os dois
 * mostradores ocupando quase toda a tela.
 *
 * Justificativa: a agulha é o elemento que se lê de relance a maior distância;
 * quanto maior o mostrador, menos tempo o olho gasta para achar a posição. Os
 * demais indicadores foram reduzidos a uma faixa fina embaixo, porque são
 * consultados, não vigiados.
 */
class AgulhasGrandeStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.AGULHAS_GRANDE

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.155f
        val bottom = height * 0.845f
        val bodyHeight = bottom - top

        // Raio limitado pela largura para os dois relógios não se tocarem.
        val radius = minOf(bodyHeight * 0.52f, width * 0.205f)
        val cy = top + bodyHeight * 0.47f

        dial(
            canvas, width * 0.285f, cy, radius,
            GaugeMath.speedFraction(data.speedKph),
            listOf("0", "20", "40", "60", "80", "100", "120", "140", "160", "180"),
            redFrom = null,
            needleColor = colorDanger,
        )
        dial(
            canvas, width * 0.715f, cy, radius,
            GaugeMath.rpmFraction(data.rpm),
            listOf("0", "1", "2", "3", "4", "5", "6", "7"),
            redFrom = GaugeMath.rpmFraction(6000.0),
            needleColor = colorDanger,
        )

        // Leitura digital grande dentro de cada relógio.
        text(canvas, fmt(data.speedKph), width * 0.285f, cy + radius * 0.52f, radius * 0.36f, colorText, Paint.Align.CENTER)
        text(canvas, "km/h", width * 0.285f, cy + radius * 0.70f, radius * 0.115f, colorDim, Paint.Align.CENTER, bold = false)
        text(canvas, fmt(data.rpm), width * 0.715f, cy + radius * 0.52f, radius * 0.36f, colorText, Paint.Align.CENTER)
        text(canvas, "rpm", width * 0.715f, cy + radius * 0.70f, radius * 0.115f, colorDim, Paint.Align.CENTER, bold = false)

        text(canvas, "VELOCIDADE", width * 0.285f, cy + radius * 0.93f, radius * 0.10f, colorDim2, Paint.Align.CENTER, bold = false)
        text(canvas, "ROTAÇÃO", width * 0.715f, cy + radius * 0.93f, radius * 0.10f, colorDim2, Paint.Align.CENTER, bold = false)
    }

    /** Faixa compacta no rodapé: indicadores secundários, bem menores. */
    override fun drawFooter(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val y = height * 0.915f
        val labelSize = shortSide * 0.020f
        val valueSize = shortSide * 0.034f

        val items = listOf(
            "ÁGUA" to "${fmt(data.coolantC)} °C",
            "CARGA" to "${fmt(data.loadPct)} %",
            "ACEL." to "${fmt(data.throttlePct)} %",
            "COMB" to "${fmt(data.fuelLevelPct)} %",
            "BAT" to "${fmt1(data.moduleVoltage)} V",
            "MAP" to "${fmt(data.mapKpa)} kPa",
            "MAF" to "${fmt1(data.mafGps)} g/s",
        )

        val left = width * 0.035f
        val step = width * 0.93f / items.size
        items.forEachIndexed { index, (label, value) ->
            val x = left + step * index
            text(canvas, label, x, y - valueSize * 0.85f, labelSize, colorDim2, Paint.Align.LEFT, bold = false)
            text(canvas, value, x, y, valueSize, colorText, Paint.Align.LEFT)
        }
    }
}
