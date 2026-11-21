package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/** 09 · Viagem — computador de bordo. */
class ViagemStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.VIAGEM

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.17f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top
        val ride = ride()

        // Herói: distância e tempo.
        text(canvas, "VIAGEM ATUAL", width * 0.05f, top + bodyHeight * 0.10f, bodyHeight * 0.05f, colorDim2, Paint.Align.LEFT, bold = false)
        text(
            canvas, String.format("%.1f", ride.tripKm), width * 0.05f, top + bodyHeight * 0.40f,
            bodyHeight * 0.26f, colorText, Paint.Align.LEFT,
        )
        text(
            canvas, "km",
            width * 0.05f + numPaint.measureText(String.format("%.1f", ride.tripKm)) * 1.02f,
            top + bodyHeight * 0.40f, bodyHeight * 0.08f, colorDim, Paint.Align.LEFT, bold = false,
        )

        val seconds = ride.tripSeconds
        val timeText = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        text(canvas, timeText, width * 0.05f, top + bodyHeight * 0.56f, bodyHeight * 0.09f, colorDim, Paint.Align.LEFT)
        text(canvas, "tempo ligado", width * 0.05f, top + bodyHeight * 0.63f, bodyHeight * 0.04f, colorDim2, Paint.Align.LEFT, bold = false)

        // Velocidade média e consumo.
        text(canvas, "VELOCIDADE MÉDIA", width * 0.05f, top + bodyHeight * 0.76f, bodyHeight * 0.04f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, String.format("%.0f", ride.avgSpeedKph), width * 0.05f, top + bodyHeight * 0.87f, bodyHeight * 0.10f, colorText, Paint.Align.LEFT)
        text(
            canvas, "km/h",
            width * 0.05f + numPaint.measureText(String.format("%.0f", ride.avgSpeedKph)) + bodyHeight * 0.02f,
            top + bodyHeight * 0.87f, bodyHeight * 0.04f, colorDim, Paint.Align.LEFT, bold = false,
        )

        // Cartões.
        val left = width * 0.34f
        val right = width * 0.975f
        val gap = width * 0.012f
        val cardWidth = (right - left - gap * 2f) / 3f
        val cardHeight = (bodyHeight - gap) / 2f

        statCard(canvas, left, top, cardWidth, cardHeight, "Consumo médio", ride.averageConsumptionL100?.let { String.format("%.1f", it) } ?: "—", "L/100", colorText)
        statCard(canvas, left + cardWidth + gap, top, cardWidth, cardHeight, "Autonomia", ride.rangeKm?.let { fmt(it) } ?: "—", "km", colorText)
        statCard(canvas, left + (cardWidth + gap) * 2f, top, cardWidth, cardHeight, "Combustível usado", String.format("%.2f", ride.fuelUsedLiters), "L", colorText)

        val secondTop = top + cardHeight + gap
        statCard(canvas, left, secondTop, cardWidth, cardHeight, "Consumo agora", ride.instantConsumptionL100?.let { String.format("%.1f", it) } ?: "—", "L/100 est.", colorText)
        statCard(
            canvas, left + cardWidth + gap, secondTop, cardWidth, cardHeight, "Nível do tanque",
            fmt(data.fuelLevelPct), "%", colorText,
            GaugeMath.fraction(data.fuelLevelPct, 0.0, 100.0), colorAccent,
        )
        statCard(
            canvas, left + (cardWidth + gap) * 2f, secondTop, cardWidth, cardHeight, "Distância (ECU)",
            "—", "km", colorDim,
        )
    }
}
