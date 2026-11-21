package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/**
 * 04 · Eco — eficiência e consumo.
 *
 * A eficiência é **estimada** (não é grandeza do carro): penaliza rotação alta e
 * carga alta. O rótulo diz "est." para não passar por medição.
 */
class EcoStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.ECO

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.18f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top
        val ride = ride()

        // Anel de eficiência.
        val cx = width * 0.17f
        val cy = top + bodyHeight * 0.50f
        val radius = minOf(bodyHeight * 0.34f, width * 0.09f)
        val score = efficiency(data)
        ring(canvas, cx, cy, radius, (score / 100.0).toFloat(), efficiencyColor(score), 0.16f)
        text(canvas, "${score.toInt()}", cx, cy + radius * 0.18f, radius * 0.62f, colorText, Paint.Align.CENTER)
        text(canvas, "eficiência est.", cx, cy + radius * 0.56f, radius * 0.16f, colorDim, Paint.Align.CENTER, bold = false)

        // Consumo instantâneo.
        val cons = ride.instantConsumptionL100
        text(canvas, "CONSUMO INSTANTÂNEO", width * 0.34f, top + bodyHeight * 0.14f, bodyHeight * 0.06f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, cons?.let { String.format("%.1f", it) } ?: "—", width * 0.34f, top + bodyHeight * 0.42f, bodyHeight * 0.26f, colorText, Paint.Align.LEFT)
        text(canvas, "L/100 km · estimado", width * 0.34f, top + bodyHeight * 0.52f, bodyHeight * 0.055f, colorDim, Paint.Align.LEFT, bold = false)

        // Velocidade logo abaixo.
        text(canvas, fmt(data.speedKph), width * 0.34f, top + bodyHeight * 0.80f, bodyHeight * 0.20f, colorText, Paint.Align.LEFT)
        text(
            canvas, "km/h",
            width * 0.34f + numPaint.measureText(fmt(data.speedKph)) + bodyHeight * 0.02f,
            top + bodyHeight * 0.80f, bodyHeight * 0.07f, colorDim, Paint.Align.LEFT, bold = false,
        )

        // Minis à direita.
        val left = width * 0.66f
        val right = width * 0.975f
        val colWidth = right - left
        val cardHeight = bodyHeight * 0.22f
        val gap = bodyHeight * 0.05f

        statCard(
            canvas, left, top, colWidth, cardHeight, "Autonomia",
            ride.rangeKm?.let { fmt(it.toDouble()) } ?: "—", "km", colorText,
        )
        statCard(
            canvas, left, top + cardHeight + gap, colWidth, cardHeight, "Consumo médio",
            ride.averageConsumptionL100?.let { String.format("%.1f", it) } ?: "—", "L/100", colorText,
        )
        statCard(
            canvas, left, top + (cardHeight + gap) * 2f, colWidth, cardHeight, "Tanque",
            fmt(data.fuelLevelPct), "%", colorText,
            GaugeMath.fraction(data.fuelLevelPct, 0.0, 100.0), colorAccent,
        )

        if (!ride.hasConsumption) {
            text(
                canvas, "Sem MAF: consumo não estimável", width * 0.66f, bottom - bodyHeight * 0.02f,
                bodyHeight * 0.05f, colorWarn, Paint.Align.LEFT, bold = false,
            )
        }
    }

    private fun efficiency(data: Telemetry): Double {
        val rpm = data.rpm ?: 0.0
        val load = data.loadPct ?: 0.0
        val penalty = (rpm / 7000.0) * 55.0 + (load / 100.0) * 35.0
        return (100.0 - penalty).coerceIn(0.0, 100.0)
    }

    private fun efficiencyColor(score: Double): Int = when {
        score >= 70 -> colorOk
        score >= 40 -> colorWarn
        else -> colorDanger
    }
}

