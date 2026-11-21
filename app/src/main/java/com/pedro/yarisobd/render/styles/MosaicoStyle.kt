package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/** 12 · Mosaico — cartões de tamanhos diferentes, velocidade herói. */
class MosaicoStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.MOSAICO

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.17f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top
        val gap = width * 0.010f
        val left = width * 0.03f
        val right = width * 0.975f

        // Coluna herói (velocidade) ocupa a esquerda.
        val heroWidth = (right - left) * 0.40f
        val heroHeight = bodyHeight
        panel(canvas, left, top, heroWidth, heroHeight)
        text(canvas, "VELOCIDADE", left + heroWidth * 0.06f, top + heroHeight * 0.13f, heroHeight * 0.045f, colorDim, Paint.Align.LEFT, bold = false)
        text(
            canvas, fmt(data.speedKph), left + heroWidth * 0.06f, top + heroHeight * 0.52f,
            heroHeight * 0.34f, colorText, Paint.Align.LEFT,
        )
        text(
            canvas, "km/h",
            left + heroWidth * 0.06f + numPaint.measureText(fmt(data.speedKph)) + heroHeight * 0.02f,
            top + heroHeight * 0.52f, heroHeight * 0.06f, colorDim, Paint.Align.LEFT, bold = false,
        )

        val ride = ride()
        text(canvas, "MARCHA", left + heroWidth * 0.06f, top + heroHeight * 0.72f, heroHeight * 0.04f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, gearLabel(), left + heroWidth * 0.06f, top + heroHeight * 0.82f, heroHeight * 0.08f, colorAccent, Paint.Align.LEFT)
        text(
            canvas, "VIAGEM ${String.format("%.1f", ride.tripKm)} km",
            left + heroWidth * 0.06f, top + heroHeight * 0.92f, heroHeight * 0.035f, colorDim, Paint.Align.LEFT, bold = false,
        )

        // Para o resto: grade 2 colunas.
        val restLeft = left + heroWidth + gap
        val restWidth = right - restLeft
        val colWidth = (restWidth - gap) / 2f
        val ringHeight = bodyHeight * 0.44f
        val smallHeight = bodyHeight * 0.27f

        // Dois anéis.
        ringCard(canvas, restLeft, top, colWidth, ringHeight, "Rotação", GaugeMath.rpmFraction(data.rpm), fmt(data.rpm), colorViolet)
        ringCard(canvas, restLeft + colWidth + gap, top, colWidth, ringHeight, "Água", GaugeMath.coolantFraction(data.coolantC), "${fmt(data.coolantC)}°", semantic(data.coolantC, 100.0, 110.0, colorAccent))

        // Quatro cartões pequenos.
        val smallTop = top + ringHeight + gap
        val items = listOf(
            Triple("Carga", "${fmt(data.loadPct)} %", GaugeMath.fraction(data.loadPct, 0.0, 100.0)),
            Triple("Acelerador", "${fmt(data.throttlePct)} %", GaugeMath.fraction(data.throttlePct, 0.0, 100.0)),
            Triple("Bateria", "${fmt1(data.moduleVoltage)} V", GaugeMath.fraction(data.moduleVoltage, 10.0, 15.0)),
            Triple("Combustível", "${fmt(data.fuelLevelPct)} %", GaugeMath.fraction(data.fuelLevelPct, 0.0, 100.0)),
        )
        items.forEachIndexed { index, (label, value, fraction) ->
            val column = index % 2
            val row = index / 2
            val cardLeft = restLeft + column * (colWidth + gap)
            val cardTop = smallTop + row * (smallHeight + gap)
            statCard(canvas, cardLeft, cardTop, colWidth, smallHeight, label, value.substringBefore(" "), value.substringAfter(" ", ""), colorText, fraction, colorAccent)
        }
    }

    private fun ringCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        label: String,
        fraction: Float,
        value: String,
        color: Int,
    ) {
        panel(canvas, left, top, width, height)
        val radius = minOf(width, height) * 0.34f
        val cx = left + width * 0.5f
        val cy = top + height * 0.54f
        ring(canvas, cx, cy, radius, fraction, color, 0.16f)
        text(canvas, value, cx, cy + radius * 0.20f, radius * 0.52f, colorText, Paint.Align.CENTER)
        text(canvas, label.uppercase(), cx, top + height * 0.90f, height * 0.10f, colorDim2, Paint.Align.CENTER, bold = false)
    }
}

