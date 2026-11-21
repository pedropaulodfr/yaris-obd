package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.R
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/**
 * 10 · Diurno — mesma hierarquia do HUD, com paleta clara para sol forte.
 *
 * Inverte só as cores; a estrutura e os avisos continuam herdados da base.
 */
class DiurnoStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.DIURNO

    override val colorBg: Int = context.getColor(R.color.day_bg)
    override val colorSurface: Int = context.getColor(R.color.day_surface)
    override val colorSurface2: Int = context.getColor(R.color.day_line)
    override val colorLine: Int = context.getColor(R.color.day_line)
    override val colorText: Int = context.getColor(R.color.day_text)
    override val colorDim: Int = context.getColor(R.color.day_dim)
    override val colorDim2: Int = context.getColor(R.color.day_dim)

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.17f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top

        text(canvas, "VELOCIDADE", width * 0.05f, top + bodyHeight * 0.10f, bodyHeight * 0.055f, colorDim, Paint.Align.LEFT, bold = false)
        text(canvas, fmt(data.speedKph), width * 0.05f, top + bodyHeight * 0.48f, bodyHeight * 0.36f, colorText, Paint.Align.LEFT)
        text(
            canvas, "km/h",
            width * 0.05f + numPaint.measureText(fmt(data.speedKph)) + bodyHeight * 0.02f,
            top + bodyHeight * 0.48f, bodyHeight * 0.09f, colorDim, Paint.Align.LEFT, bold = false,
        )

        // Rotação como barra.
        val barLeft = width * 0.05f
        val barRight = width * 0.52f
        val barY = top + bodyHeight * 0.66f
        text(canvas, "ROTAÇÃO", barLeft, barY - bodyHeight * 0.06f, bodyHeight * 0.045f, colorDim, Paint.Align.LEFT, bold = false)
        text(canvas, "${fmt(data.rpm)} rpm", barRight, barY - bodyHeight * 0.06f, bodyHeight * 0.05f, colorText, Paint.Align.RIGHT)
        hBar(canvas, barLeft, barRight, barY, bodyHeight * 0.05f, GaugeMath.rpmFraction(data.rpm), colorAccent)

        // Seis cartões à direita.
        val left = width * 0.58f
        val right = width * 0.975f
        val gap = width * 0.012f
        val cardWidth = (right - left - gap) / 2f
        val cardHeight = (bodyHeight - gap * 2f) / 3f

        val cards = listOf(
            Triple("Água", "${fmt(data.coolantC)} °C", colorText),
            Triple("Ar admitido", "${fmt(data.intakeC)} °C", colorText),
            Triple("Carga", "${fmt(data.loadPct)} %", colorText),
            Triple("Acelerador", "${fmt(data.throttlePct)} %", colorText),
            Triple("Bateria", "${fmt1(data.moduleVoltage)} V", colorText),
            Triple("Combustível", "${fmt(data.fuelLevelPct)} %", colorText),
        )

        cards.forEachIndexed { index, (label, value, color) ->
            val column = index % 2
            val row = index / 2
            val cardLeft = left + column * (cardWidth + gap)
            val cardTop = top + row * (cardHeight + gap)

            panel(canvas, cardLeft, cardTop, cardWidth, cardHeight)
            text(canvas, label.uppercase(), cardLeft + cardWidth * 0.08f, cardTop + cardHeight * 0.34f, cardHeight * 0.16f, colorDim, Paint.Align.LEFT, bold = false)
            text(canvas, value, cardLeft + cardWidth * 0.08f, cardTop + cardHeight * 0.76f, cardHeight * 0.32f, color, Paint.Align.LEFT)
        }

        text(canvas, "TEMA CLARO · PARA USO DIURNO", width * 0.975f, height * 0.925f, bodyHeight * 0.04f, colorDim, Paint.Align.RIGHT, bold = false)
    }
}
