package com.pedro.yarisobd.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.OnClickListener
import com.pedro.yarisobd.R

/**
 * Menu da tela do carro.
 *
 * A barra de ações do Android Auto comporta poucos botões e qualquer botão a
 * mais rouba espaço do painel. Por isso existe **um único ícone discreto** no
 * canto: ele concentra as telas secundárias, e o painel fica limpo para o que
 * importa — os instrumentos.
 */
class MenuScreen(
    carContext: CarContext,
    private val onShowDiagnostics: () -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_style))
                    .addText("Escolher entre os layouts do painel")
                    .setOnClickListener(OnClickListener { screenManager.push(StyleScreen(carContext)) })
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_dtc))
                    .addText("Códigos de falha lidos da ECU")
                    .setOnClickListener(OnClickListener { screenManager.push(DtcScreen(carContext)) })
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.screen_diagnostics))
                    .addText("Superfície, quadros por segundo e último erro")
                    .setOnClickListener(
                        OnClickListener {
                            screenManager.pop()
                            onShowDiagnostics()
                        },
                    )
                    .build(),
            )
            .build()

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.screen_menu))
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
