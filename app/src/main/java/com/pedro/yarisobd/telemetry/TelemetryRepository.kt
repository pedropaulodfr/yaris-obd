package com.pedro.yarisobd.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponto único de leitura da telemetria, compartilhado entre a tela do celular e
 * a tela projetada no carro.
 *
 * Fica em memória de propósito: o app precisa funcionar sem internet e sem
 * serviço em background. O Android Auto mantém o processo vivo enquanto a tela
 * do app está ativa; quando o host libera a tela, não deve existir nada
 * rodando consumindo bateria.
 */
object TelemetryRepository {

    private val _state = MutableStateFlow(Telemetry.EMPTY)
    val state: StateFlow<Telemetry> = _state.asStateFlow()

    @Volatile
    private var demoRunning = false

    fun update(telemetry: Telemetry) {
        _state.value = telemetry
        // Alimenta histórico, viagem e consumo estimado no mesmo ponto: qualquer
        // fonte (scanner real ou simulador) passa por aqui.
        RideData.onTelemetry(telemetry)
    }

    fun markDemoRunning(running: Boolean) {
        demoRunning = running
    }

    fun isDemoRunning(): Boolean = demoRunning

    /** Zera o estado quando nenhuma fonte (real ou simulada) está ativa. */
    fun clear() {
        _state.value = Telemetry.EMPTY.copy(updatedAtMs = 0L)
    }
}
