package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry

/** 11 · HUD — só o essencial, em tamanho enorme. */
class HudStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.HUD

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.16f
        val bottom = height * 0.88f
        val bodyHeight = bottom - top

        val speedSize = minOf(bodyHeight * 0.62f, width * 0.30f)
        text(canvas, fmt(data.speedKph), width * 0.5f, top + bodyHeight * 0.52f, speedSize, colorText, Paint.Align.CENTER)
        text(canvas, "km/h", width * 0.5f, top + bodyHeight * 0.63f, bodyHeight * 0.07f, colorDim, Paint.Align.CENTER, bold = false)

        // Barra de rotação logo abaixo.
        val barLeft = width * 0.16f
        val barRight = width * 0.84f
        val barY = top + bodyHeight * 0.76f
        hBar(
            canvas, barLeft, barRight, barY, bodyHeight * 0.05f,
            GaugeMath.rpmFraction(data.rpm),
            if (GaugeMath.isRedline(data.rpm)) colorDanger else colorAccent,
        )

        val rowY = top + bodyHeight * 0.90f
        val items = listOf(
            "MARCHA" to gearLabel(),
            "ÁGUA" to "${fmt(data.coolantC)} °C",
            "COMB" to "${fmt(data.fuelLevelPct)} %",
            "BAT" to "${fmt1(data.moduleVoltage)} V",
        )
        items.forEachIndexed { index, (label, value) ->
            val x = width * (0.16f + index * 0.23f)
            text(canvas, "$label ", x, rowY, bodyHeight * 0.055f, colorDim2, Paint.Align.LEFT, bold = false)
            labelPaint.textSize = bodyHeight * 0.055f
            text(canvas, value, x + labelPaint.measureText("$label "), rowY, bodyHeight * 0.055f, colorText, Paint.Align.LEFT)
        }

        if (GaugeMath.isRedline(data.rpm)) {
            text(
                canvas, "SHIFT", width * 0.5f, top + bodyHeight * 0.08f,
                bodyHeight * 0.09f, colorDanger, Paint.Align.CENTER,
            )
        }
    }
}
