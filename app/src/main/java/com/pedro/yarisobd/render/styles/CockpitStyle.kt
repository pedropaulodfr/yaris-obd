package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/** 03 · Cockpit — rotação com luzes de troca e velocidade dominante. */
class CockpitStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.COCKPIT

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.17f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top

        // Rotação em cima, centralizada.
        text(
            canvas, fmt(data.rpm), width * 0.30f, top + bodyHeight * 0.14f,
            bodyHeight * 0.16f, colorText, Paint.Align.RIGHT,
        )
        text(
            canvas, "rpm", width * 0.31f, top + bodyHeight * 0.13f,
            bodyHeight * 0.07f, colorDim, Paint.Align.LEFT, bold = false,
        )

        // Luzes de troca: acendem proporcionalmente, com a última faixa em vermelho.
        val ledCount = 10
        val ledRadius = bodyHeight * 0.016f
        val ledStartX = width * 0.38f
        val ledGap = width * 0.022f
        val ledY = top + bodyHeight * 0.10f
        val rpmFraction = GaugeMath.rpmFraction(data.rpm)
        for (index in 0 until ledCount) {
            val threshold = (index + 1).toFloat() / ledCount
            val lit = rpmFraction >= threshold - 0.02f
            fillPaint.color = when {
                !lit -> colorSurface2
                index >= ledCount - 3 -> colorDanger
                index >= ledCount - 5 -> colorWarn
                else -> colorOk
            }
            canvas.drawCircle(ledStartX + ledGap * index, ledY, ledRadius, fillPaint)
        }

        // SHIFT quando passa de 6.000 rpm.
        if (GaugeMath.isRedline(data.rpm)) {
            text(
                canvas, "SHIFT", width * 0.72f, top + bodyHeight * 0.12f,
                bodyHeight * 0.075f, colorDanger, Paint.Align.LEFT,
            )
        }

        // Velocidade herói.
        val speedSize = bodyHeight * 0.46f
        text(canvas, fmt(data.speedKph), width * 0.06f, top + bodyHeight * 0.68f, speedSize, colorText, Paint.Align.LEFT)
        text(
            canvas, "km/h",
            width * 0.06f + numPaint.measureText(fmt(data.speedKph)) + bodyHeight * 0.02f,
            top + bodyHeight * 0.68f, speedSize * 0.22f, colorDim, Paint.Align.LEFT, bold = false,
        )

        // Marcha.
        val gearLeft = width * 0.60f
        panel(canvas, gearLeft, top + bodyHeight * 0.30f, width * 0.12f, bodyHeight * 0.30f)
        text(
            canvas, "MARCHA", gearLeft + width * 0.06f, top + bodyHeight * 0.40f,
            bodyHeight * 0.06f, colorDim, Paint.Align.CENTER, bold = false,
        )
        text(
            canvas, gearLabel(), gearLeft + width * 0.06f, top + bodyHeight * 0.55f,
            bodyHeight * 0.14f, colorAccent, Paint.Align.CENTER,
        )

        // Auxiliares.
        val auxLeft = width * 0.78f
        val rows = listOf(
            "Água" to "${fmt(data.coolantC)} °C",
            "Bateria" to "${fmt1(data.moduleVoltage)} V",
            "Combustível" to "${fmt(data.fuelLevelPct)} %",
        )
        rows.forEachIndexed { index, (label, value) ->
            val y = top + bodyHeight * (0.32f + index * 0.16f)
            text(canvas, label.uppercase(), auxLeft, y, bodyHeight * 0.06f, colorDim2, Paint.Align.LEFT, bold = false)
            text(canvas, value, auxLeft, y + bodyHeight * 0.08f, bodyHeight * 0.10f, colorText, Paint.Align.LEFT)
        }

        // Barras de acelerador e carga.
        val barLeft = width * 0.06f
        val barRight = width * 0.52f
        val bar1Y = top + bodyHeight * 0.82f
        val bar2Y = top + bodyHeight * 0.93f

        text(canvas, "ACELERADOR", barLeft, bar1Y - bodyHeight * 0.03f, bodyHeight * 0.055f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, "${fmt(data.throttlePct)}%", barRight, bar1Y - bodyHeight * 0.03f, bodyHeight * 0.06f, colorText, Paint.Align.RIGHT)
        hBar(canvas, barLeft, barRight, bar1Y, bodyHeight * 0.045f, GaugeMath.fraction(data.throttlePct, 0.0, 100.0), colorOk)

        text(canvas, "CARGA", barLeft, bar2Y - bodyHeight * 0.03f, bodyHeight * 0.055f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, "${fmt(data.loadPct)}%", barRight, bar2Y - bodyHeight * 0.03f, bodyHeight * 0.06f, colorText, Paint.Align.RIGHT)
        hBar(canvas, barLeft, barRight, bar2Y, bodyHeight * 0.045f, GaugeMath.fraction(data.loadPct, 0.0, 100.0), colorWarn)
    }
}
