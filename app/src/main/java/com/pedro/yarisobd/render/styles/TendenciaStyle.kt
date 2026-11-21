package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.RideData
import com.pedro.yarisobd.telemetry.Telemetry

/** 06 · Tendência — o que o motor fez nos últimos 30 segundos. */
class TendenciaStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.TENDENCIA

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.16f
        val bottom = height * 0.88f
        val bodyHeight = bottom - top
        val ride = ride()

        val rows = listOf(
            TrendRow("Velocidade", data.speedKph, "km/h", RideData.Channel.SPEED, ride.maxSpeed, 1.1, colorAccent),
            TrendRow("Rotação", data.rpm, "rpm", RideData.Channel.RPM, ride.maxRpm, 1.1, colorViolet),
            TrendRow("Carga", data.loadPct, "%", RideData.Channel.LOAD, ride.maxLoad, 100.0, colorWarn),
            TrendRow("Acelerador", data.throttlePct, "%", RideData.Channel.THROTTLE, ride.maxThrottle, 100.0, colorOk),
        )

        val rowHeight = bodyHeight / rows.size
        rows.forEachIndexed { index, row ->
            val rowTop = top + rowHeight * index
            val metaWidth = width * 0.20f

            text(canvas, row.label.uppercase(), width * 0.04f, rowTop + rowHeight * 0.26f, rowHeight * 0.15f, colorDim2, Paint.Align.LEFT, bold = false)
            text(canvas, row.valueText(), width * 0.04f, rowTop + rowHeight * 0.58f, rowHeight * 0.34f, colorText, Paint.Align.LEFT)
            text(
                canvas, row.unit,
                width * 0.04f + numPaint.measureText(row.valueText()) + rowHeight * 0.08f,
                rowTop + rowHeight * 0.58f, rowHeight * 0.14f, colorDim, Paint.Align.LEFT, bold = false,
            )
            text(canvas, "máx ${row.maxText()}", width * 0.04f, rowTop + rowHeight * 0.78f, rowHeight * 0.12f, colorDim2, Paint.Align.LEFT, bold = false)

            val chartLeft = width * 0.24f
            val chartRight = width * 0.975f
            sparkline(
                canvas, chartLeft, rowTop + rowHeight * 0.12f,
                chartRight - chartLeft, rowHeight * 0.66f,
                RideData.seriesOf(row.channel), row.scale.toFloat(), row.color,
            )
            hBar(canvas, chartLeft, chartRight, rowTop + rowHeight * 0.92f, rowHeight * 0.012f, 0f, colorLine)
        }

        // Eixo de tempo.
        val axisY = height * 0.925f
        listOf("-30 s", "-20 s", "-10 s", "agora").forEachIndexed { index, label ->
            val x = width * (0.24f + 0.245f * index)
            text(canvas, label, x, axisY, height * 0.022f, colorDim2, Paint.Align.LEFT, bold = false)
        }
    }

    private data class TrendRow(
        val label: String,
        val value: Double?,
        val unit: String,
        val channel: RideData.Channel,
        val maxValue: Double,
        val scale: Double,
        val color: Int,
    ) {
        fun valueText(): String = value?.let { GaugeMathFormat.int(it) } ?: "—"
        fun maxText(): String = if (maxValue > 0) GaugeMathFormat.int(maxValue) else "—"
    }

    private object GaugeMathFormat {
        fun int(value: Double): String = com.pedro.yarisobd.render.GaugeMath.formatInt(value)
    }
}
