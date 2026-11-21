package com.pedro.yarisobd.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.pedro.yarisobd.R

/**
 * Tela de diagnóstico da integração: mostra o que o host do Android Auto
 * entregou (superfície, tamanho, taxa de quadros) e o estado da fonte de dados.
 *
 * Serve para provar/comprovar a exibição na central sem depender de logcat.
 */
class DiagnosticsScreen(
    carContext: CarContext,
    private val statusProvider: () -> ScreenStatus,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val status = statusProvider()

        val pane = Pane.Builder()
            .addRow(row("Superfície", status.surfaceState))
            .addRow(row("Resolução", status.surfaceSize))
            .addRow(row("Quadros desenhados", status.framesDrawn.toString()))
            .addRow(row("Taxa medida", "%.1f fps".format(status.measuredFps)))
            .addRow(row("Fonte de dados", status.dataSource))
            .addRow(row("Idade do dado", status.dataAge))
            .apply {
                status.lastError?.let { addRow(row("Último erro", it)) }
            }
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.screen_diagnostics))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun row(title: String, value: String): Row =
        Row.Builder()
            .setTitle(title)
            .addText(value)
            .build()
}
