package com.pedro.yarisobd.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.pedro.yarisobd.telemetry.DemoTelemetrySource
import com.pedro.yarisobd.telemetry.Telemetry
import java.io.File

/**
 * Ferramenta de desenvolvimento: renderiza o painel em bitmap usando o **mesmo**
 * [DashboardRenderer] da tela do carro.
 *
 * Serve para duas coisas: comparar os estilos lado a lado no celular (a tela de
 * escolha mostra a prévia do estilo selecionado) e gravar PNGs para revisão.
 */
object DashboardPreview {

    private val SIZES = listOf(1920 to 720, 1280 to 720, 800 to 480)

    /** Um bitmap do estilo pedido, para exibir na seleção. */
    fun renderBitmap(
        context: Context,
        style: DashboardStyle,
        width: Int,
        height: Int,
        telemetry: Telemetry? = null,
    ): Bitmap {
        val renderer = DashboardRenderer(context, style)
        val data = telemetry ?: DemoTelemetrySource.sample(14.0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.draw(
            canvas = Canvas(bitmap),
            width = width,
            height = height,
            data = data,
            nowMs = data.updatedAtMs,
            fps = 12f,
        )
        return bitmap
    }

    /**
     * Grava um PNG por estilo, na resolução de central, mais as resoluções
     * alternativas para o estilo em uso.
     */
    fun export(context: Context, telemetry: Telemetry? = null, currentStyle: DashboardStyle? = null): List<File> {
        val data = telemetry ?: DemoTelemetrySource.sample(14.0)
        val outDir = File(context.filesDir, "previews").apply { mkdirs() }
        val files = mutableListOf<File>()

        DashboardStyle.entries.forEach { style ->
            val (width, height) = SIZES.first()
            val bitmap = renderBitmap(context, style, width, height, data)
            val file = File(outDir, "style-${style.id}-${width}x$height.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            files += file
        }

        currentStyle?.let { style ->
            SIZES.drop(1).forEach { (width, height) ->
                val bitmap = renderBitmap(context, style, width, height, data)
                val file = File(outDir, "style-${style.id}-${width}x$height.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                files += file
            }
        }

        return files
    }
}
