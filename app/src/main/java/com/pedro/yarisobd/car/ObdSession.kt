package com.pedro.yarisobd.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Sessão do app no carro. Uma sessão por conexão com a central.
 */
class ObdSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen = DashboardScreen(carContext)
}
