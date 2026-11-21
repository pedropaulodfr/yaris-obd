package com.pedro.yarisobd.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.OnClickListener
import com.pedro.yarisobd.R
import com.pedro.yarisobd.telemetry.DtcCatalog
import com.pedro.yarisobd.telemetry.DtcRepository

/**
 * Tela de diagnóstico na central: mostra os códigos de falha lidos da ECU.
 *
 * A limpeza exige uma tela de confirmação própria ([ClearDtcScreen]) — é a única
 * operação do app que escreve na ECU.
 */
class DtcScreen(
    carContext: CarContext,
    private val onRequestRefresh: () -> Unit = {},
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val snapshot = DtcRepository.state.value
        onRequestRefresh()

        if (snapshot.confirmed.isEmpty() && snapshot.pending.isEmpty()) {
            return MessageTemplate.Builder(
                buildString {
                    append("Nenhum código de falha confirmado.")
                    append("\n\nLuz de injeção: ")
                    append(if (snapshot.milOn) "ACESA" else "apagada")
                    if (!snapshot.readable) {
                        append("\n\nSem leitura da ECU no momento.")
                    }
                },
            )
                .setTitle(carContext.getString(R.string.screen_dtc))
                .setHeaderAction(Action.BACK)
                .build()
        }

        val list = ItemList.Builder()
        snapshot.confirmed.forEach { code ->
            val info = DtcCatalog.describe(code)
            list.addItem(
                Row.Builder()
                    .setTitle("$code · ${DtcCatalog.severityLabel(info.severity)}")
                    .addText(info.description)
                    .build(),
            )
        }
        if (snapshot.pending.isNotEmpty()) {
            list.addItem(
                Row.Builder()
                    .setTitle("Pendentes")
                    .addText(snapshot.pending.joinToString(", "))
                    .build(),
            )
        }

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.screen_dtc))
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.action_clear_dtc))
                            .setIcon(CarIcon.ALERT)
                            .setBackgroundColor(CarColor.RED)
                            .setOnClickListener(OnClickListener { screenManager.push(ClearDtcScreen(carContext)) })
                            .build(),
                    )
                    .build(),
            )
            .build()
    }
}
