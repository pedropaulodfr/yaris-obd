package com.pedro.yarisobd.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.OnClickListener
import com.pedro.yarisobd.AppPrefs
import com.pedro.yarisobd.R
import com.pedro.yarisobd.render.DashboardStyle

/**
 * Escolha do layout **pela tela da central**.
 *
 * A escolha é gravada na mesma preferência usada pelo celular, e o painel
 * relê a preferência uma vez por segundo — então o layout novo aparece assim
 * que a tela volta, sem recarregar o app nem tocar no telefone.
 */
class StyleScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val current = AppPrefs.dashboardStyle(carContext)

        val list = ItemList.Builder()
        DashboardStyle.entries.forEach { style: DashboardStyle ->
            val suffix = if (style == current) " · em uso" else ""
            list.addItem(
                Row.Builder()
                    .setTitle(style.label + suffix)
                    .addText(style.description)
                    .setOnClickListener(
                        OnClickListener {
                            AppPrefs.setDashboardStyle(carContext, style)
                            screenManager.pop()
                        },
                    )
                    .build(),
            )
        }

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.screen_style))
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
