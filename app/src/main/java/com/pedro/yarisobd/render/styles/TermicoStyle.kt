package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/**
 * 05 · Térmico — temperatura do arrefecimento como informação principal.
 *
 * "Ventoinha" **não** é legível por OBD-II padrão: aparece como "—" em vez de um
 * estado inventado.
 */
class TermicoStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.TERMICO

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.18f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top

        // Herói: temperatura.
        text(canvas, "TEMPERATURA DA ÁGUA", width * 0.05f, top + bodyHeight * 0.10f, bodyHeight * 0.06f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, fmt(data.coolantC), width * 0.05f, top + bodyHeight * 0.42f, bodyHeight * 0.30f, colorText, Paint.Align.LEFT)
        text(
            canvas, "°C",
            width * 0.05f + numPaint.measureText(fmt(data.coolantC)) + bodyHeight * 0.02f,
            top + bodyHeight * 0.42f, bodyHeight * 0.09f, colorDim, Paint.Align.LEFT, bold = false,
        )

        val state = when {
            data.coolantC == null -> "sem leitura"
            data.coolantC!! >= 110.0 -> "Crítico"
            data.coolantC!! >= 100.0 -> "Atenção"
            data.coolantC!! < 70.0 -> "Aquecendo"
            else -> "Normal"
        }
        val stateColor = when (state) {
            "Crítico" -> colorDanger
            "Atenção" -> colorWarn
            "Aquecendo" -> colorAccent
            else -> colorOk
        }
        panel(canvas, width * 0.05f, top + bodyHeight * 0.52f, bodyHeight * 0.52f, bodyHeight * 0.12f, colorSurface)
        fillPaint.color = stateColor
        canvas.drawCircle(width * 0.05f + bodyHeight * 0.06f, top + bodyHeight * 0.58f, bodyHeight * 0.022f, fillPaint)
        text(canvas, state, width * 0.05f + bodyHeight * 0.12f, top + bodyHeight * 0.615f, bodyHeight * 0.065f, stateColor, Paint.Align.LEFT, bold = false)

        text(canvas, "Ar admitido ${fmt(data.intakeC)} °C", width * 0.05f, top + bodyHeight * 0.76f, bodyHeight * 0.06f, colorDim, Paint.Align.LEFT, bold = false)

        // Tubo vertical com faixas.
        val tubeX = width * 0.30f
        val tubeTop = top + bodyHeight * 0.06f
        val tubeBottom = top + bodyHeight * 0.94f
        val tubeWidth = width * 0.035f
        vBar(canvas, tubeX, tubeTop, tubeBottom, tubeWidth, GaugeMath.coolantFraction(data.coolantC), semantic(data.coolantC, 100.0, 110.0, colorAccent))

        // Faixas de referência (100 °C e 110 °C na escala 40..120).
        listOf(100.0 to "100", 110.0 to "110").forEach { (value, label) ->
            val fraction = GaugeMath.coolantFraction(value)
            val y = tubeBottom - (tubeBottom - tubeTop) * fraction
            strokePaint.color = if (value >= 110) colorDanger else colorWarn
            strokePaint.strokeWidth = bodyHeight * 0.004f
            canvas.drawLine(tubeX - tubeWidth * 1.1f, y, tubeX + tubeWidth * 1.1f, y, strokePaint)
            text(canvas, label, tubeX + tubeWidth * 1.6f, y + bodyHeight * 0.015f, bodyHeight * 0.045f, colorDim2, Paint.Align.LEFT, bold = false)
        }
        text(canvas, "120", tubeX - tubeWidth * 1.6f, tubeTop + bodyHeight * 0.02f, bodyHeight * 0.045f, colorDim2, Paint.Align.RIGHT, bold = false)
        text(canvas, "40", tubeX - tubeWidth * 1.6f, tubeBottom, bodyHeight * 0.045f, colorDim2, Paint.Align.RIGHT, bold = false)

        // Painéis à direita.
        val left = width * 0.44f
        val right = width * 0.975f
        val colWidth = right - left
        val cardHeight = bodyHeight * 0.17f
        val gap = bodyHeight * 0.035f

        val rows = listOf(
            Triple("Ventoinha", "—", "não é lida por OBD"),
            Triple("Avanço de ignição", "${fmt(data.timingAdvanceDeg)} °", ""),
            Triple("MAP", "${fmt(data.mapKpa)} kPa", ""),
            Triple("Carga", "${fmt(data.loadPct)} %", ""),
            Triple("Bateria", "${fmt1(data.moduleVoltage)} V", ""),
        )

        rows.forEachIndexed { index, (label, value, note) ->
            val y = top + (cardHeight + gap) * index
            panel(canvas, left, y, colWidth, cardHeight)
            text(canvas, label.uppercase(), left + colWidth * 0.05f, y + cardHeight * 0.34f, cardHeight * 0.16f, colorDim, Paint.Align.LEFT, bold = false)
            text(canvas, value, left + colWidth * 0.05f, y + cardHeight * 0.72f, cardHeight * 0.30f, colorText, Paint.Align.LEFT)
            if (note.isNotEmpty()) {
                text(canvas, note, left + colWidth * 0.05f, y + cardHeight * 0.88f, cardHeight * 0.12f, colorDim2, Paint.Align.LEFT, bold = false)
            }
        }
    }
}
