package com.pedro.yarisobd.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.pedro.yarisobd.R
import com.pedro.yarisobd.telemetry.DataSource
import com.pedro.yarisobd.telemetry.RideData
import com.pedro.yarisobd.telemetry.Telemetry
import kotlin.math.cos
import kotlin.math.sin

/**
 * Base comum dos 12 layouts do laboratório.
 *
 * Concentra o que **não pode variar** entre eles: paleta, tipografia, cabeçalho,
 * rodapé, avisos de dado simulado/velho e os primitivos de desenho (mostrador
 * com agulha, fita, anel, sparkline, cartão).
 *
 * As cores são `open` de propósito: o layout "Diurno" inverte a paleta sem
 * duplicar os outros componentes.
 */
abstract class StyleBase(protected val context: Context) : DashboardStyleRenderer {

    protected open val colorBg: Int = context.getColor(R.color.bg)
    protected open val colorSurface: Int = context.getColor(R.color.surface)
    protected open val colorSurface2: Int = context.getColor(R.color.surface2)
    protected open val colorLine: Int = context.getColor(R.color.line)
    protected open val colorText: Int = context.getColor(R.color.text_primary)
    protected open val colorDim: Int = context.getColor(R.color.text_secondary)
    protected open val colorDim2: Int = context.getColor(R.color.text_tertiary)
    protected open val colorAccent: Int = context.getColor(R.color.accent)
    protected open val colorWarn: Int = context.getColor(R.color.warn)
    protected open val colorDanger: Int = context.getColor(R.color.danger)
    protected open val colorOk: Int = context.getColor(R.color.ok)
    protected open val colorViolet: Int = context.getColor(R.color.violet)

    protected val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.LEFT
    }

    protected val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    protected val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    protected val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    protected val rect = RectF()
    protected val path = Path()

    final override fun draw(canvas: Canvas, width: Int, height: Int, data: Telemetry, nowMs: Long, fps: Float) {
        canvas.drawColor(colorBg)
        currentTelemetry = data
        val shortSide = minOf(width, height).toFloat()
        drawHeader(canvas, width, height, shortSide, fps)
        drawBody(canvas, width, height, shortSide, data)
        drawFooter(canvas, width, height, shortSide, data)
        drawStatusOverlay(canvas, width, height, shortSide, data, nowMs)
    }

    protected abstract fun drawBody(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry)

    // ---- cabeçalho / rodapé / aviso ----

    protected fun drawHeader(canvas: Canvas, width: Int, height: Int, shortSide: Float, fps: Float) {
        text(canvas, "YARIS OBD", width * 0.025f, height * 0.085f, shortSide * 0.034f, colorDim, Paint.Align.LEFT, bold = false)
        text(canvas, "%.0f fps".format(fps), width * 0.975f, height * 0.085f, shortSide * 0.028f, colorDim2, Paint.Align.RIGHT, bold = false)
    }

    protected open fun drawFooter(canvas: Canvas, width: Int, height: Int, shortSide: Float, data: Telemetry) {
        val y = height * 0.945f
        val size = shortSide * 0.030f
        val items = listOf(
            "MAP ${fmt(data.mapKpa)} kPa",
            "MAF ${fmt1(data.mafGps)} g/s",
            "AR ${fmt(data.intakeC)} °C",
            "BAT ${fmt1(data.moduleVoltage)} V",
            "COMB ${fmt(data.fuelLevelPct)} %",
        )
        val left = width * 0.025f
        val step = width * 0.95f / items.size
        items.forEachIndexed { index, item ->
            text(canvas, item, left + step * index, y, size, colorDim2, Paint.Align.LEFT, bold = false)
        }
    }

    protected fun drawStatusOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        shortSide: Float,
        data: Telemetry,
        nowMs: Long,
    ) {
        val stale = data.isStale(nowMs)
        val (message, color) = when {
            stale || data.source == DataSource.NONE -> "AGUARDANDO SCANNER OBD2" to colorDanger
            data.source == DataSource.EMULATED -> "ADAPTADOR VIRTUAL — DADOS SIMULADOS" to colorWarn
            data.source == DataSource.DEMO -> "SIMULAÇÃO — SEM DADOS DO VEÍCULO" to colorWarn
            data.source == DataSource.REAL -> "SCANNER CONECTADO" to colorOk
            else -> return
        }

        labelPaint.textSize = shortSide * 0.032f
        val textWidth = labelPaint.measureText(message)
        val padH = shortSide * 0.026f
        val boxHeight = shortSide * 0.058f
        val boxWidth = textWidth + padH * 2f
        val cx = width * 0.5f
        val top = height * 0.045f

        rect.set(cx - boxWidth / 2f, top, cx + boxWidth / 2f, top + boxHeight)
        fillPaint.color = color
        fillPaint.alpha = if (data.source == DataSource.REAL) 30 else 36
        canvas.drawRoundRect(rect, boxHeight / 2f, boxHeight / 2f, fillPaint)
        fillPaint.alpha = 255

        // Ponto à esquerda, como no laboratório.
        val dotY = top + boxHeight / 2f
        fillPaint.color = color
        canvas.drawCircle(cx - boxWidth / 2f + padH * 0.75f, dotY, boxHeight * 0.13f, fillPaint)

        text(
            canvas, message, cx + padH * 0.35f, top + boxHeight * 0.68f,
            shortSide * 0.032f, color, Paint.Align.CENTER, bold = false,
        )
    }

    // ---- primitivos ----

    protected fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        align: Paint.Align,
        bold: Boolean = true,
    ) {
        val paint = if (bold) numPaint else labelPaint
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        canvas.drawText(value, x, y, paint)
    }

    /** Cartão arredondado do laboratório. */
    protected fun panel(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, color: Int = colorSurface) {
        rect.set(left, top, left + width, top + height)
        fillPaint.color = color
        canvas.drawRoundRect(rect, height * 0.16f, height * 0.16f, fillPaint)
    }

    protected fun hBar(canvas: Canvas, left: Float, right: Float, centerY: Float, thickness: Float, fraction: Float, color: Int) {
        val radius = thickness / 2f
        rect.set(left, centerY - radius, right, centerY + radius)
        fillPaint.color = colorSurface2
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (fraction > 0.002f) {
            rect.set(left, centerY - radius, left + (right - left) * fraction.coerceIn(0f, 1f), centerY + radius)
            fillPaint.color = color
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    protected fun vBar(
        canvas: Canvas,
        x: Float,
        top: Float,
        bottom: Float,
        width: Float,
        fraction: Float,
        color: Int,
    ) {
        val radius = width / 2f
        rect.set(x - width / 2f, top, x + width / 2f, bottom)
        fillPaint.color = colorSurface2
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (fraction > 0.002f) {
            val fillTop = bottom - (bottom - top) * fraction.coerceIn(0f, 1f)
            rect.set(x - width / 2f, fillTop, x + width / 2f, bottom)
            fillPaint.color = color
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    /**
     * Mostrador com agulha, escala gravada e zona vermelha.
     *
     * [labels] são os valores impressos na escala; [sweepDegrees] menor que 360
     * deixa o arco aberto embaixo, como num painel convencional.
     */
    protected fun dial(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        fraction: Float,
        labels: List<String>,
        redFrom: Float?,
        needleColor: Int,
        startDegrees: Float = 144f,
        sweepDegrees: Float = 252f,
    ) {
        val outer = radius
        val inner = radius * 0.84

        // Zona vermelha (arco externo, traço fino).
        if (redFrom != null) {
            strokePaint.color = colorDanger
            strokePaint.alpha = 130
            strokePaint.strokeWidth = radius * 0.035f
            rect.set(cx - outer, cy - outer, cx + outer, cy + outer)
            canvas.drawArc(
                rect,
                startDegrees + sweepDegrees * redFrom,
                sweepDegrees * (1f - redFrom),
                false,
                strokePaint,
            )
            strokePaint.alpha = 255
        }

        // Escala: traços longos nos rótulos, curtos entre eles.
        val steps = (labels.size - 1) * 2
        for (step in 0..steps) {
            val fractionStep = step.toFloat() / steps
            val angle = Math.toRadians((startDegrees + sweepDegrees * fractionStep).toDouble())
            val major = step % 2 == 0
            val lineWidth = if (major) radius * 0.014f else radius * 0.008f
            val lineColor = if (major) colorText else colorDim2
            strokePaint.color = lineColor
            strokePaint.strokeWidth = lineWidth
            val r1 = inner * (if (major) 0.90f else 0.955f)
            val r2 = inner
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            canvas.drawLine(
                (cx + cosA * r1).toFloat(),
                (cy + sinA * r1).toFloat(),
                (cx + cosA * r2).toFloat(),
                (cy + sinA * r2).toFloat(),
                strokePaint,
            )
            if (major) {
                val labelR = inner * 0.74f
                text(
                    canvas,
                    labels[step / 2],
                    (cx + cosA * labelR).toFloat(),
                    (cy + sinA * labelR + radius * 0.045f).toFloat(),
                    radius * 0.11f,
                    colorDim,
                    Paint.Align.CENTER,
                    bold = false,
                )
            }
        }

        // Agulha.
        val needleAngle = Math.toRadians((startDegrees + sweepDegrees * fraction.coerceIn(0f, 1f)).toDouble())
        val needleLength = radius * 0.72f
        strokePaint.color = needleColor
        strokePaint.strokeWidth = radius * 0.028f
        canvas.drawLine(cx, cy, cx + cos(needleAngle).toFloat() * needleLength, cy + sin(needleAngle).toFloat() * needleLength, strokePaint)

        // Cubo central.
        fillPaint.color = colorSurface2
        canvas.drawCircle(cx, cy, radius * 0.075f, fillPaint)
        fillPaint.color = colorLine
        canvas.drawCircle(cx, cy, radius * 0.045f, fillPaint)
    }

    /** Fita graduada: janela com escala que rola conforme o valor. */
    protected fun tape(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        value: Double?,
        max: Double,
        majorStep: Double,
        color: Int,
    ) {
        rect.set(left, top, left + width, top + height)
        fillPaint.color = colorSurface
        canvas.drawRoundRect(rect, width * 0.12f, width * 0.12f, fillPaint)

        val pixelsPerUnit = height / (max * 0.35) // janela mostra ~35% da escala
        val current = value ?: 0.0
        val first = ((current - max * 0.20) / majorStep).toInt() * majorStep
        val last = current + max * 0.20

        canvas.save()
        rect.set(left, top, left + width, top + height)
        canvas.clipRect(rect)

        var mark = first
        while (mark <= last) {
            if (mark >= 0) {
                val y = (top + height / 2f) - ((mark - current) * pixelsPerUnit).toFloat()
                val isMajor = (mark % (majorStep * 2)) == 0.0
                strokePaint.color = if (isMajor) colorDim else colorDim2
                strokePaint.strokeWidth = if (isMajor) width * 0.028f else width * 0.014f
                val tickLeft = left + width * (if (isMajor) 0.10f else 0.16f)
                canvas.drawLine(tickLeft, y, left + width * 0.30f, y, strokePaint)
                if (isMajor) {
                    text(
                        canvas,
                        mark.toInt().toString(),
                        left + width * 0.38f,
                        y + height * 0.022f,
                        height * 0.075f,
                        colorDim,
                        Paint.Align.LEFT,
                        bold = false,
                    )
                }
            }
            mark += majorStep
        }
        canvas.restore()

        // Ponteiro central: linhas de referência.
        strokePaint.color = color
        strokePaint.strokeWidth = width * 0.022f
        canvas.drawLine(left + width * 0.06f, top + height / 2f, left + width * 0.30f, top + height / 2f, strokePaint)
        canvas.drawLine(left + width * 0.70f, top + height / 2f, left + width * 0.94f, top + height / 2f, strokePaint)
    }

    /**
     * Anel de progresso com texto no centro.
     * [thicknessFraction] é a espessura como fração do raio (0,10–0,20 fica
     * legível); passar 1,0 transformaria o anel num disco.
     */
    protected fun ring(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        fraction: Float,
        color: Int,
        thicknessFraction: Float,
    ) {
        val stroke = radius * thicknessFraction.coerceIn(0.05f, 0.35f)
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        strokePaint.strokeWidth = stroke
        strokePaint.color = colorSurface2
        canvas.drawArc(rect, 135f, 270f, false, strokePaint)

        if (fraction > 0.002f) {
            strokePaint.color = color
            canvas.drawArc(rect, 135f, 270f * fraction.coerceIn(0f, 1f), false, strokePaint)
        }
    }

    /** Sparkline com área preenchida (histórico de 30 s). */
    protected fun sparkline(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        values: FloatArray,
        maxValue: Float,
        color: Int,
    ) {
        if (values.size < 2) return
        val scale = if (maxValue > 0f) maxValue else 1f

        path.reset()
        values.forEachIndexed { index, value ->
            val x = left + width * (index.toFloat() / (values.size - 1).coerceAtLeast(1))
            val y = top + height - (value / scale).coerceIn(0f, 1f) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Área.
        val area = Path(path)
        area.lineTo(left + width, top + height)
        area.lineTo(left, top + height)
        area.close()
        fillPaint.color = color
        fillPaint.alpha = 40
        canvas.drawPath(area, fillPaint)
        fillPaint.alpha = 255

        strokePaint.color = color
        strokePaint.strokeWidth = height * 0.045f
        canvas.drawPath(path, strokePaint)
    }

    /** Cartão com rótulo em cima, número grande e unidade. */
    protected fun statCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        label: String,
        value: String,
        unit: String,
        valueColor: Int,
        fraction: Float? = null,
        barColor: Int = colorAccent,
    ) {
        panel(canvas, left, top, width, height)
        text(canvas, label.uppercase(), left + width * 0.07f, top + height * 0.28f, height * 0.13f, colorDim, Paint.Align.LEFT, bold = false)
        text(canvas, value, left + width * 0.07f, top + height * 0.70f, height * 0.34f, valueColor, Paint.Align.LEFT)
        if (unit.isNotEmpty()) {
            numPaint.textSize = height * 0.34f
            val valueWidth = numPaint.measureText(value)
            text(
                canvas,
                unit,
                left + width * 0.07f + valueWidth + height * 0.05f,
                top + height * 0.70f,
                height * 0.13f,
                colorDim,
                Paint.Align.LEFT,
                bold = false,
            )
        }
        if (fraction != null) {
            hBar(
                canvas,
                left + width * 0.07f,
                left + width * 0.93f,
                top + height * 0.86f,
                height * 0.05f,
                fraction,
                barColor,
            )
        }
    }

    // ---- formatação (indisponível nunca vira zero) ----

    protected fun fmt(value: Double?): String = value?.let { GaugeMath.formatInt(it) } ?: "—"
    protected fun fmt1(value: Double?): String = value?.let { GaugeMath.format1(it) } ?: "—"

    protected fun semantic(value: Double?, warnAt: Double, dangerAt: Double, base: Int): Int = when {
        value == null -> colorDim
        value >= dangerAt -> colorDanger
        value >= warnAt -> colorWarn
        else -> base
    }

    protected fun ride(): RideData.Snapshot = RideData.snapshot(currentTelemetry)

    /** Guardado só para [ride]; atualizado a cada quadro. */
    private var currentTelemetry: Telemetry = Telemetry.EMPTY

    protected fun rememberTelemetry(data: Telemetry) {
        currentTelemetry = data
    }

    protected fun gearLabel(): String = RideData.gearLabel ?: "N/D"
}

