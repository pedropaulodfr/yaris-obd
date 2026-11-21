package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.GaugeMath
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.Telemetry
import kotlin.math.cos
import kotlin.math.sin

/** 07 · Radar — seis eixos normalizados, leitura de conjunto. */
class RadarStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.RADAR

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.18f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top

        val cx = width * 0.28f
        val cy = top + bodyHeight * 0.50f
        val radius = minOf(bodyHeight * 0.45f, width * 0.17f)

        val axes = listOf(
            Axis("Velocidade", GaugeMath.speedFraction(data.speedKph), fmt(data.speedKph), "km/h"),
            Axis("Rotação", GaugeMath.rpmFraction(data.rpm), fmt(data.rpm), "rpm"),
            Axis("Carga", GaugeMath.fraction(data.loadPct, 0.0, 100.0), fmt(data.loadPct), "%"),
            Axis("Acelerador", GaugeMath.fraction(data.throttlePct, 0.0, 100.0), fmt(data.throttlePct), "%"),
            Axis("Água", GaugeMath.coolantFraction(data.coolantC), fmt(data.coolantC), "°C"),
            Axis("Bateria", GaugeMath.fraction(data.moduleVoltage, 10.0, 15.0), fmt1(data.moduleVoltage), "V"),
        )

        // Anéis de referência.
        strokePaint.color = colorLine
        strokePaint.strokeWidth = bodyHeight * 0.004f
        for (step in 1..3) {
            val r = radius * step / 3f
            path.reset()
            axes.indices.forEach { index ->
                val angle = angleOf(index, axes.size)
                val x = cx + cos(angle).toFloat() * r
                val y = cy + sin(angle).toFloat() * r
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, strokePaint)
        }

        // Eixos e rótulos.
        axes.forEachIndexed { index, axis ->
            val angle = angleOf(index, axes.size)
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            canvas.drawLine(cx, cy, x, y, strokePaint)

            val labelRadius = radius * 1.16f
            text(
                canvas, axis.label.uppercase(),
                cx + cos(angle).toFloat() * labelRadius,
                cy + sin(angle).toFloat() * labelRadius,
                bodyHeight * 0.045f, colorDim2, Paint.Align.CENTER, bold = false,
            )
        }

        // Polígono de valores.
        path.reset()
        axes.forEachIndexed { index, axis ->
            val angle = angleOf(index, axes.size)
            val r = radius * axis.fraction.coerceIn(0.02f, 1f)
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        fillPaint.color = colorAccent
        fillPaint.alpha = 55
        canvas.drawPath(path, fillPaint)
        fillPaint.alpha = 255

        strokePaint.color = colorAccent
        strokePaint.strokeWidth = bodyHeight * 0.006f
        canvas.drawPath(path, strokePaint)

        // Pontos e valores nos vértices.
        axes.forEachIndexed { index, axis ->
            val angle = angleOf(index, axes.size)
            val r = radius * axis.fraction.coerceIn(0.02f, 1f)
            fillPaint.color = colorAccent
            canvas.drawCircle(cx + cos(angle).toFloat() * r, cy + sin(angle).toFloat() * r, bodyHeight * 0.008f, fillPaint)
        }

        // Lado direito: velocidade e legenda.
        text(canvas, "VELOCIDADE", width * 0.66f, top + bodyHeight * 0.14f, bodyHeight * 0.055f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, fmt(data.speedKph), width * 0.66f, top + bodyHeight * 0.32f, bodyHeight * 0.22f, colorText, Paint.Align.LEFT)
        text(
            canvas, "km/h",
            width * 0.66f + numPaint.measureText(fmt(data.speedKph)) + bodyHeight * 0.02f,
            top + bodyHeight * 0.32f, bodyHeight * 0.07f, colorDim, Paint.Align.LEFT, bold = false,
        )

        axes.forEachIndexed { index, axis ->
            val y = top + bodyHeight * (0.48f + index * 0.075f)
            fillPaint.color = colorAccent
            canvas.drawCircle(width * 0.67f, y - bodyHeight * 0.015f, bodyHeight * 0.007f, fillPaint)
            text(canvas, axis.label, width * 0.70f, y, bodyHeight * 0.05f, colorDim, Paint.Align.LEFT, bold = false)
            text(canvas, "${axis.value} ${axis.unit}", width * 0.975f, y, bodyHeight * 0.05f, colorText, Paint.Align.RIGHT)
        }
    }

    private fun angleOf(index: Int, total: Int): Double {
        // Começa em cima e segue no sentido horário.
        val step = (2 * Math.PI) / total
        return -Math.PI / 2 + step * index
    }

    private data class Axis(
        val label: String,
        val fraction: Float,
        val value: String,
        val unit: String,
    )
}
