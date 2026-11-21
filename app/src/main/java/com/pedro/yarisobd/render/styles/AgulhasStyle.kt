package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/** 01 · Agulhas — dois relógios com agulha e coluna de apoio. */
class AgulhasStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.AGULHAS

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.17f
        val bottom = height * 0.88f
        val bodyHeight = bottom - top
        val radius = minOf(bodyHeight * 0.40f, width * 0.135f)
        val cy = top + bodyHeight * 0.46f

        dial(
            canvas, width * 0.155f, cy, radius,
            GaugeMath.speedFraction(data.speedKph),
            listOf("0", "20", "40", "60", "80", "100", "120", "140", "160", "180"),
            redFrom = null,
            needleColor = colorDanger,
        )
        dial(
            canvas, width * 0.405f, cy, radius,
            GaugeMath.rpmFraction(data.rpm),
            listOf("0", "1", "2", "3", "4", "5", "6", "7"),
            redFrom = GaugeMath.rpmFraction(6000.0),
            needleColor = colorDanger,
        )

        // Leitura digital sob cada relógio.
        text(canvas, fmt(data.speedKph), width * 0.155f, cy + radius * 0.52f, radius * 0.34f, colorText, Paint.Align.CENTER)
        text(canvas, "km/h", width * 0.155f, cy + radius * 0.70f, radius * 0.12f, colorDim, Paint.Align.CENTER, bold = false)
        text(canvas, fmt(data.rpm), width * 0.405f, cy + radius * 0.52f, radius * 0.34f, colorText, Paint.Align.CENTER)
        text(canvas, "rpm", width * 0.405f, cy + radius * 0.70f, radius * 0.12f, colorDim, Paint.Align.CENTER, bold = false)

        text(canvas, "VELOCIDADE", width * 0.155f, cy + radius * 0.95f, radius * 0.11f, colorDim2, Paint.Align.CENTER, bold = false)
        text(canvas, "ROTAÇÃO", width * 0.405f, cy + radius * 0.95f, radius * 0.11f, colorDim2, Paint.Align.CENTER, bold = false)

        // Coluna direita.
        val left = width * 0.60f
        val right = width * 0.975f
        val colWidth = right - left

        val tempHeight = bodyHeight * 0.30f
        panel(canvas, left, top + bodyHeight * 0.02f, colWidth, tempHeight)
        text(
            canvas, "TEMPERATURA DA ÁGUA",
            left + colWidth * 0.05f, top + bodyHeight * 0.02f + tempHeight * 0.28f,
            tempHeight * 0.13f, colorDim, Paint.Align.LEFT, bold = false,
        )
        text(
            canvas, fmt(data.coolantC),
            left + colWidth * 0.05f, top + bodyHeight * 0.02f + tempHeight * 0.72f,
            tempHeight * 0.40f, colorText, Paint.Align.LEFT,
        )
        text(
            canvas, "°C",
            left + colWidth * 0.05f + numPaint.measureText(fmt(data.coolantC)) * 1.02f,
            top + bodyHeight * 0.02f + tempHeight * 0.72f,
            tempHeight * 0.15f, colorDim, Paint.Align.LEFT, bold = false,
        )
        val barY = top + bodyHeight * 0.02f + tempHeight * 0.86f
        hBar(
            canvas, left + colWidth * 0.05f, right - colWidth * 0.05f, barY, tempHeight * 0.045f,
            GaugeMath.coolantFraction(data.coolantC),
            semantic(data.coolantC, 100.0, 110.0, colorOk),
        )
        text(canvas, "40", left + colWidth * 0.05f, barY + tempHeight * 0.13f, tempHeight * 0.09f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, "120", right - colWidth * 0.05f, barY + tempHeight * 0.13f, tempHeight * 0.09f, colorDim2, Paint.Align.RIGHT, bold = false)

        val cardTop = top + bodyHeight * 0.40f
        val cardHeight = bodyHeight * 0.30f
        val cardWidth = colWidth * 0.31f
        val gap = colWidth * 0.035f

        statCard(
            canvas, left, cardTop, cardWidth, cardHeight, "Combustível",
            fmt(data.fuelLevelPct), "%", colorText,
            GaugeMath.fraction(data.fuelLevelPct, 0.0, 100.0), colorAccent,
        )
        statCard(
            canvas, left + cardWidth + gap, cardTop, cardWidth, cardHeight, "Bateria",
            fmt1(data.moduleVoltage), "V", colorText,
            GaugeMath.fraction(data.moduleVoltage, 10.0, 15.0),
            semantic(data.moduleVoltage, 13.0, 15.0, colorAccent),
        )
        statCard(
            canvas, left + (cardWidth + gap) * 2f, cardTop, cardWidth, cardHeight, "Marcha",
            gearLabel(), "", colorAccent,
        )
    }
}
