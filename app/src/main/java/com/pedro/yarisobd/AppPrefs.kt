package com.pedro.yarisobd

import android.content.Context

/** Preferências simples do app. */
object AppPrefs {

    private const val FILE = "yaris_obd"
    private const val KEY_AUTO_START_SIM = "auto_start_sim"
    private const val KEY_DASHBOARD_STYLE = "dashboard_style"

    /**
     * Quando ligado, o adaptador virtual sobe sozinho ao abrir o app no carro.
     * Fica ligado por padrão nesta fase de testes; deve ser desligado quando
     * houver um scanner físico, para não mascarar falta de conexão.
     */
    fun autoStartSim(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_START_SIM, true)

    fun setAutoStartSim(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_START_SIM, value).apply()
    }

    /** Estilo visual do painel, compartilhado entre a tela do celular e a do carro. */
    fun dashboardStyle(context: Context): com.pedro.yarisobd.render.DashboardStyle =
        com.pedro.yarisobd.render.DashboardStyle.fromId(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_DASHBOARD_STYLE, null),
        )

    fun setDashboardStyle(context: Context, style: com.pedro.yarisobd.render.DashboardStyle) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_DASHBOARD_STYLE, style.id).apply()
    }
}
