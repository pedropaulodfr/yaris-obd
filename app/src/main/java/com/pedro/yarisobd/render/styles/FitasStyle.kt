package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/** 02 · Fitas — escalas graduadas que rolam, com cartões de apoio. */
class FitasStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.FITAS

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.18f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top

        // Fita principal (velocidade) e secundária (rotação).
        val mainLeft = width * 0.045f
        val mainWidth = width * 0.20f
        tape(canvas, mainLeft, top, mainWidth, bodyHeight, data.speedKph, 180.0, 10.0, colorAccent)

        text(
            canvas, fmt(data.speedKph),
            mainLeft + mainWidth + width * 0.02f, top + bodyHeight * 0.34f,
            bodyHeight * 0.30f, colorText, Paint.Align.LEFT,
        )
        text(
            canvas, "km/h",
            mainLeft + mainWidth + width * 0.02f, top + bodyHeight * 0.44f,
            bodyHeight * 0.075f, colorDim, Paint.Align.LEFT, bold = false,
        )

        val subLeft = width * 0.045f
        val subTop = top + bodyHeight * 0.60f
        val subHeight = bodyHeight * 0.38f
        tape(canvas, subLeft, subTop, mainWidth, subHeight, data.rpm, 7000.0, 500.0, colorViolet)

        text(
            canvas, fmt(data.rpm),
            subLeft + mainWidth + width * 0.02f, subTop + subHeight * 0.52f,
            bodyHeight * 0.16f, colorText, Paint.Align.LEFT,
        )
        text(
            canvas, "rpm",
            subLeft + mainWidth + width * 0.02f, subTop + subHeight * 0.70f,
            bodyHeight * 0.065f, colorDim, Paint.Align.LEFT, bold = false,
        )

        // Cartões à direita.
        val left = width * 0.44f
        val right = width * 0.975f
        val colWidth = right - left
        val cardHeight = bodyHeight * 0.215f
        val gap = bodyHeight * 0.03f

        statCard(
            canvas, left, top, colWidth, cardHeight, "Água",
            fmt(data.coolantC), "°C",
            semantic(data.coolantC, 100.0, 110.0, colorText),
            GaugeMath.coolantFraction(data.coolantC),
            semantic(data.coolantC, 100.0, 110.0, colorOk),
        )
        statCard(
            canvas, left, top + cardHeight + gap, colWidth, cardHeight, "Carga",
            fmt(data.loadPct), "%", colorText,
            GaugeMath.fraction(data.loadPct, 0.0, 100.0), colorWarn,
        )
        statCard(
            canvas, left, top + (cardHeight + gap) * 2f, colWidth, cardHeight, "Acelerador",
            fmt(data.throttlePct), "%", colorText,
            GaugeMath.fraction(data.throttlePct, 0.0, 100.0), colorOk,
        )
        statCard(
            canvas, left, top + (cardHeight + gap) * 3f, colWidth, cardHeight, "Bateria",
            fmt1(data.moduleVoltage), "V", colorText,
            GaugeMath.fraction(data.moduleVoltage, 10.0, 15.0), colorAccent,
        )
    }
}
