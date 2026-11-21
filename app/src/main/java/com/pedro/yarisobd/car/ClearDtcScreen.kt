package com.pedro.yarisobd.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.Template
import com.pedro.yarisobd.R
import com.pedro.yarisobd.obd.ConnectionController
import com.pedro.yarisobd.telemetry.TelemetryRepository

/**
 * Confirmação de limpeza de códigos na central.
 *
 * Regras de segurança (iguais às da tela do celular):
 *  - o veículo precisa estar **parado**;
 *  - a ação é descrita antes de ser executada;
 *  - só depois do toque em "Apagar códigos" o comando vai para a ECU.
 */
class ClearDtcScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val speed = TelemetryRepository.state.value.speedKph

        if (speed != null && speed > STATIONARY_LIMIT) {
            return MessageTemplate.Builder(
                "O veículo está em movimento (${speed.toInt()} km/h).\n\n" +
                    "Limpar códigos só pode ser feito com o carro parado: a operação zera " +
                    "os monitores de prontidão e o histórico de diagnóstico.",
            )
                .setTitle(carContext.getString(R.string.clear_dtc_blocked))
                .setHeaderAction(Action.BACK)
                .build()
        }

        return LongMessageTemplate.Builder(
            """
            Esta é a única operação do app que escreve na ECU.

            Serão apagados:
            • códigos confirmados e pendentes;
            • o quadro congelado (freeze frame);
            • os monitores de prontidão (a luz de injeção apaga).

            O defeito não é consertado. Se a causa existir, o código volta.
            """.trimIndent(),
        )
            .setTitle(carContext.getString(R.string.clear_dtc_title))
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.action_cancel))
                            .setOnClickListener(OnClickListener { screenManager.pop() })
                            .build(),
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.action_erase_codes))
                            .setBackgroundColor(CarColor.RED)
                            .setOnClickListener(
                                OnClickListener {
                                    screenManager.pop()
                                    ConnectionController.clearDtcsAsync { }
                                },
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private companion object {
        const val STATIONARY_LIMIT = 1.0
    }
}
