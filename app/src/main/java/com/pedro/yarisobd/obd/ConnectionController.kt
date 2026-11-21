package com.pedro.yarisobd.obd

import android.content.Context
import com.pedro.yarisobd.sim.VehicleSimulator
import com.pedro.yarisobd.sim.VehicleSnapshot
import com.pedro.yarisobd.telemetry.RideData
import com.pedro.yarisobd.telemetry.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controla a fonte de dados do app: **scanner físico** ou **adaptador virtual**.
 *
 * A diferença entre os dois é só o [ObdTransport]. Todo o resto — sessão ELM,
 * descoberta de PIDs, decodificação, diagnóstico — é exatamente o mesmo código,
 * e é por isso que o simulador serve de teste de verdade.
 */
object ConnectionController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var vehicle: VehicleSimulator? = null
    private var connection: ObdConnection? = null
    private var tickerJob: Job? = null
    private var autoStarted = false
    private var appContext: Context? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _vehicleState = MutableStateFlow<VehicleSnapshot?>(null)
    val vehicleState: StateFlow<VehicleSnapshot?> = _vehicleState.asStateFlow()

    private val _obdState = MutableStateFlow<ObdState>(ObdState.Disconnected)
    val obdState: StateFlow<ObdState> = _obdState.asStateFlow()

    private val _usingSimulator = MutableStateFlow(true)
    val usingSimulator: StateFlow<Boolean> = _usingSimulator.asStateFlow()

    val isRunning: Boolean get() = _running.value
    val isSimulator: Boolean get() = _usingSimulator.value

    /** Liga o carro virtual e conecta pelo adaptador emulado. */
    fun startSimulator(context: Context, auto: Boolean = false) {
        if (_running.value) disconnect()

        val simulator = VehicleSimulator()
        vehicle = simulator
        autoStarted = auto
        _usingSimulator.value = true
        appContext = context.applicationContext
        ObdForegroundService.start(context.applicationContext)

        tickerJob = scope.launch {
            var lastNanos = System.nanoTime()
            while (isActive) {
                val now = System.nanoTime()
                val dt = (now - lastNanos) / 1_000_000_000.0
                lastNanos = now
                simulator.tick(dt)
                val snapshot = simulator.snapshot()
                _vehicleState.value = snapshot
                // Marcha só existe no simulador; com scanner real fica "—".
                RideData.gearLabel = "D${snapshot.gear}"
                delay(TICK_INTERVAL_MS)
            }
        }

        connect(SimulatedTransport(simulator))
    }

    /** Conecta a um scanner físico já escolhido. */
    fun startScanner(
        context: Context,
        address: String,
        name: String?,
        mode: BluetoothTransport.Mode = BluetoothTransport.Mode.AUTO,
    ) {
        if (_running.value) disconnect()

        tickerJob?.cancel()
        tickerJob = null
        vehicle = null
        _vehicleState.value = null
        _usingSimulator.value = false
        RideData.gearLabel = null
        appContext = context.applicationContext
        ObdForegroundService.start(context.applicationContext)

        connect(BluetoothTransport(context.applicationContext, address, name, mode))
    }

    private fun connect(transport: ObdTransport) {
        val newConnection = ObdConnection(transport, TelemetryRepository)
        connection = newConnection
        _running.value = true

        scope.launch {
            newConnection.connect(scope)
            newConnection.state.collect { _obdState.value = it }
        }
    }

    fun disconnect() {
        appContext?.let { ObdForegroundService.stop(it) }
        tickerJob?.cancel()
        tickerJob = null
        connection?.disconnect()
        connection = null
        vehicle = null
        RideData.gearLabel = null
        autoStarted = false
        _vehicleState.value = null
        _obdState.value = ObdState.Disconnected
        _running.value = false
    }

    /** Sobe o simulador apenas se nada estiver conectado (usado pela tela do carro). */
    fun startIfIdle(context: Context, auto: Boolean): Boolean {
        if (_running.value) return false
        appContext = context.applicationContext
        startSimulator(context, auto)
        return true
    }

    fun stopIfAutoStarted() {
        if (autoStarted) disconnect()
    }

    // ---- controles do simulador ----

    fun setThrottle(fraction: Double) {
        vehicle?.throttleCommand = fraction.coerceIn(0.0, 1.0)
    }

    fun setAutoDrive(enabled: Boolean) {
        vehicle?.autoDrive = enabled
    }

    // ---- diagnóstico assíncrono (mesma interface para as duas fontes) ----

    fun readDtcsAsync(onResult: (List<String>) -> Unit) = withConnection(onResult) { it.readDtcs() }

    fun clearDtcsAsync(onResult: (Boolean) -> Unit) {
        val conn = connection
        if (conn == null) {
            onResult(false)
            return
        }
        scope.launch {
            val ok = runCatching { conn.clearDtcs() }.getOrDefault(false)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    private fun withConnection(onResult: (List<String>) -> Unit, block: suspend (ObdConnection) -> List<String>) {
        val conn = connection
        if (conn == null) {
            onResult(emptyList())
            return
        }
        scope.launch {
            val result = runCatching { block(conn) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    val vin: String? get() = connection?.vin

    private const val TICK_INTERVAL_MS = 50L
}


