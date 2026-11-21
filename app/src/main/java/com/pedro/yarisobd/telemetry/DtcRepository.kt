package com.pedro.yarisobd.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado de diagnóstico compartilhado entre a tela do celular e a do carro. */
data class DtcSnapshot(
    val confirmed: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val milOn: Boolean = false,
    val readable: Boolean = false,
    val lastReadAtMs: Long = 0L,
) {
    val isEmpty: Boolean get() = confirmed.isEmpty() && pending.isEmpty()
}

/**
 * Guarda o último diagnóstico lido da ECU.
 *
 * É um objeto simples de propósito: o Android Auto mantém o processo vivo
 * enquanto a tela está aberta, não há necessidade de persistir em disco.
 */
object DtcRepository {

    private val _state = MutableStateFlow(DtcSnapshot())
    val state: StateFlow<DtcSnapshot> = _state.asStateFlow()

    fun update(confirmed: List<String>, pending: List<String>, milOn: Boolean) {
        _state.value = DtcSnapshot(
            confirmed = confirmed.distinct().sorted(),
            pending = pending.distinct().sorted(),
            milOn = milOn,
            readable = true,
            lastReadAtMs = System.currentTimeMillis(),
        )
    }

    fun markUnreadable() {
        _state.value = _state.value.copy(readable = false)
    }

    fun clear() {
        _state.value = DtcSnapshot(
            confirmed = emptyList(),
            pending = emptyList(),
            milOn = false,
            readable = true,
            lastReadAtMs = System.currentTimeMillis(),
        )
    }
}
