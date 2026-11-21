package com.pedro.yarisobd.car

/**
 * Diagnóstico em tempo de execução da integração com o Android Auto.
 * Existe para a fase de prova de conceito: quando algo não aparece na central,
 * este é o primeiro lugar a olhar.
 */
data class ScreenStatus(
    val surfaceState: String,
    val surfaceSize: String,
    val framesDrawn: Long,
    val measuredFps: Float,
    val dataSource: String,
    val dataAge: String,
    val lastError: String?,
)
