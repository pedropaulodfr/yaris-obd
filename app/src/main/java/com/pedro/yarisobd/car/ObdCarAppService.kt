package com.pedro.yarisobd.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Ponto de entrada descoberto pelo host do Android Auto.
 *
 * O host encontra este serviço pela ação `androidx.car.app.CarAppService` no
 * manifesto e pelo `<uses name="template"/>` em `automotive_app_desc.xml`.
 */
class ObdCarAppService : CarAppService() {

    override fun onCreateSession(): Session = ObdSession()

    /**
     * Restringe quem pode se conectar a este serviço.
     *
     * Em build de debug aceita qualquer host, que é o que a própria
     * documentação da biblioteca recomenda para facilitar o teste com o DHU.
     * Em release só aceita os hosts da lista de assinaturas conhecidas do
     * Google — sem isso, qualquer app instalado no celular poderia se passar
     * por central e receber os templates (com a telemetria embutida).
     */
    override fun createHostValidator(): HostValidator {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (debuggable) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }
}
