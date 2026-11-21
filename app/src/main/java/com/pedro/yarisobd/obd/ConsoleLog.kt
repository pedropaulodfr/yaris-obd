package com.pedro.yarisobd.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Console de comandos e respostas cruas, igual ao de um app OBD de verdade.
 *
 * Existe por dois motivos: depurar o adaptador quando algo não responde, e
 * permitir ver o protocolo acontecendo (é o que dá confiança de que o app está
 * conversando de verdade, e não só exibindo números).
 */
object ConsoleLog {

    private const val MAX_LINES = 400

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun add(direction: Char, text: String) {
        val prefix = if (direction == '>') ">>" else "<<"
        val clean = text.replace("\r", " · ").replace("\n", " ")
        val updated = (_lines.value + "$prefix $clean").takeLast(MAX_LINES)
        _lines.value = updated
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
