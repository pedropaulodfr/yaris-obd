package com.pedro.yarisobd.render.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.render.StyleBase
import com.pedro.yarisobd.telemetry.DtcCatalog
import com.pedro.yarisobd.telemetry.DtcRepository
import com.pedro.yarisobd.telemetry.DtcSeverity
import com.pedro.yarisobd.telemetry.Telemetry

/** 08 · Diagnóstico — estado do sistema, códigos e saúde do enlace. */
class DiagStyle(context: Context) : StyleBase(context) {

    override val style = DashboardStyle.DIAGNOSTICO

    override fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val top = height * 0.16f
        val bottom = height * 0.87f
        val bodyHeight = bottom - top

        val snapshot = DtcRepository.state.value
        val hasFaults = snapshot.confirmed.isNotEmpty()
        val statusColor = if (hasFaults) colorDanger else colorOk

        // Cartão de estado.
        val statusWidth = width * 0.28f
        panel(canvas, width * 0.03f, top, statusWidth, bodyHeight)
        text(
            canvas, if (hasFaults) "!" else "OK",
            width * 0.03f + statusWidth * 0.5f, top + bodyHeight * 0.22f,
            bodyHeight * 0.10f, statusColor, Paint.Align.CENTER,
        )
        text(
            canvas, if (hasFaults) "Falhas registradas" else "Sistema OK",
            width * 0.03f + statusWidth * 0.5f, top + bodyHeight * 0.34f,
            bodyHeight * 0.062f, statusColor, Paint.Align.CENTER,
        )
        text(
            canvas, if (hasFaults) "${snapshot.confirmed.size} código(s)" else "Nenhum código confirmado",
            width * 0.03f + statusWidth * 0.5f, top + bodyHeight * 0.43f,
            bodyHeight * 0.045f, colorDim, Paint.Align.CENTER, bold = false,
        )

        text(
            canvas, "${snapshot.confirmed.size}",
            width * 0.03f + statusWidth * 0.5f, top + bodyHeight * 0.65f,
            bodyHeight * 0.18f, colorText, Paint.Align.CENTER,
        )
        text(
            canvas, "CÓDIGOS ATIVOS",
            width * 0.03f + statusWidth * 0.5f, top + bodyHeight * 0.73f,
            bodyHeight * 0.038f, colorDim2, Paint.Align.CENTER, bold = false,
        )

        // Luz de injeção.
        val milColor = if (data.milOn) colorWarn else colorDim2
        panel(
            canvas, width * 0.03f + statusWidth * 0.2f, top + bodyHeight * 0.80f,
            statusWidth * 0.6f, bodyHeight * 0.10f, colorSurface2,
        )
        fillPaint.color = milColor
        canvas.drawCircle(
            width * 0.03f + statusWidth * 0.32f, top + bodyHeight * 0.85f,
            bodyHeight * 0.016f, fillPaint,
        )
        text(
            canvas, if (data.milOn) "MIL acesa" else "MIL apagada",
            width * 0.03f + statusWidth * 0.38f, top + bodyHeight * 0.862f,
            bodyHeight * 0.045f, milColor, Paint.Align.LEFT, bold = false,
        )

        // Lista de códigos.
        val listLeft = width * 0.34f
        val listRight = width * 0.76f
        val listWidth = listRight - listLeft

        text(canvas, "CÓDIGO", listLeft, top + bodyHeight * 0.06f, bodyHeight * 0.035f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, "DESCRIÇÃO", listLeft + listWidth * 0.22f, top + bodyHeight * 0.06f, bodyHeight * 0.035f, colorDim2, Paint.Align.LEFT, bold = false)
        text(canvas, "GRAVIDADE", listRight, top + bodyHeight * 0.06f, bodyHeight * 0.035f, colorDim2, Paint.Align.RIGHT, bold = false)

        val rows = snapshot.confirmed.take(6)
        if (rows.isEmpty()) {
            text(
                canvas, "Sem falhas registradas · leitura concluída",
                listLeft + listWidth * 0.5f, top + bodyHeight * 0.40f,
                bodyHeight * 0.05f, colorDim, Paint.Align.CENTER, bold = false,
            )
        } else {
            rows.forEachIndexed { index, code ->
                val info = DtcCatalog.describe(code)
                val y = top + bodyHeight * (0.14f + index * 0.115f)
                panel(
                    canvas, listLeft, y, listWidth, bodyHeight * 0.10f,
                    if (index % 2 == 0) colorSurface else colorSurface2,
                )
                text(canvas, code, listLeft + listWidth * 0.04f, y + bodyHeight * 0.065f, bodyHeight * 0.05f, colorText, Paint.Align.LEFT)
                text(
                    canvas, info.description.take(46),
                    listLeft + listWidth * 0.22f, y + bodyHeight * 0.062f,
                    bodyHeight * 0.038f, colorDim, Paint.Align.LEFT, bold = false,
                )
                val severityColor = when (info.severity) {
                    DtcSeverity.GRAVE -> colorDanger
                    DtcSeverity.MODERADA -> colorWarn
                    DtcSeverity.ATENCAO -> colorAccent
                    DtcSeverity.DESCONHECIDA -> colorDim2
                }
                text(
                    canvas, DtcCatalog.severityLabel(info.severity).uppercase(),
                    listRight - listWidth * 0.02f, y + bodyHeight * 0.065f,
                    bodyHeight * 0.032f, severityColor, Paint.Align.RIGHT, bold = false,
                )
            }
        }

        // Saúde do enlace.
        val healthLeft = width * 0.79f
        val healthWidth = width * 0.18f
        panel(canvas, healthLeft, top, healthWidth, bodyHeight)
        text(canvas, "ENLACE", healthLeft + healthWidth * 0.1f, top + bodyHeight * 0.10f, bodyHeight * 0.038f, colorDim2, Paint.Align.LEFT, bold = false)

        val ageMs = if (data.updatedAtMs == 0L) -1 else System.currentTimeMillis() - data.updatedAtMs
        val health = listOf(
            "Dado" to if (ageMs < 0) "—" else "$ageMs ms",
            "Fonte" to when (data.source) {
                com.pedro.yarisobd.telemetry.DataSource.REAL -> "scanner"
                com.pedro.yarisobd.telemetry.DataSource.EMULATED -> "virtual"
                com.pedro.yarisobd.telemetry.DataSource.DEMO -> "demo"
                com.pedro.yarisobd.telemetry.DataSource.NONE -> "—"
            },
            "PIDs" to (if (data.source == com.pedro.yarisobd.telemetry.DataSource.NONE) "—" else "ativos"),
        )
        health.forEachIndexed { index, (label, value) ->
            val y = top + bodyHeight * (0.24f + index * 0.22f)
            text(canvas, label.uppercase(), healthLeft + healthWidth * 0.1f, y, bodyHeight * 0.032f, colorDim2, Paint.Align.LEFT, bold = false)
            text(canvas, value, healthLeft + healthWidth * 0.1f, y + bodyHeight * 0.07f, bodyHeight * 0.05f, colorText, Paint.Align.LEFT)
        }
    }
}
