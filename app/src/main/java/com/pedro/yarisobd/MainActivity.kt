package com.pedro.yarisobd

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.pedro.yarisobd.obd.ConnectionController
import com.pedro.yarisobd.obd.ConsoleLog
import com.pedro.yarisobd.obd.ObdConnection
import com.pedro.yarisobd.obd.ObdState
import com.pedro.yarisobd.obd.ScannerDevice
import com.pedro.yarisobd.obd.ScannerDiscovery
import com.pedro.yarisobd.render.DashboardPreview
import com.pedro.yarisobd.render.DashboardStyle
import com.pedro.yarisobd.telemetry.DataSource
import com.pedro.yarisobd.telemetry.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Painel de controle do app no celular.
 *
 * O produto é a tela do carro; aqui ficam a escolha da fonte de dados
 * (**scanner físico** ou **simulador**), os controles de simulação, o layout do
 * painel, o diagnóstico e o console do protocolo.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var updating = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var statusView: TextView
    private lateinit var liveView: TextView
    private lateinit var consoleView: TextView
    private lateinit var dtcResultView: TextView
    private lateinit var throttleLabel: TextView
    private lateinit var throttleBar: SeekBar
    private lateinit var autoSwitch: Switch
    private lateinit var connectButton: Button
    private lateinit var scannerButton: Button

    private var pendingScannerSelection = false

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        liveView = findViewById(R.id.live)
        consoleView = findViewById(R.id.console)
        dtcResultView = findViewById(R.id.dtc_result)
        throttleLabel = findViewById(R.id.throttle_label)
        throttleBar = findViewById(R.id.seek_throttle)
        autoSwitch = findViewById(R.id.switch_auto)
        connectButton = findViewById(R.id.btn_start)
        scannerButton = findViewById(R.id.btn_scanner)

        findViewById<TextView>(R.id.instructions).text = INSTRUCTIONS

        connectButton.setOnClickListener {
            if (ConnectionController.isRunning) {
                ConnectionController.disconnect()
            } else {
                ConnectionController.startSimulator(this)
            }
            refreshUi()
        }

        scannerButton.setOnClickListener { chooseScanner() }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            ConnectionController.disconnect()
            refreshUi()
        }

        autoSwitch.setOnCheckedChangeListener { _, checked -> ConnectionController.setAutoDrive(checked) }

        val autoStartSwitch = findViewById<Switch>(R.id.switch_autostart)
        autoStartSwitch.isChecked = AppPrefs.autoStartSim(this)
        autoStartSwitch.setOnCheckedChangeListener { _, checked -> AppPrefs.setAutoStartSim(this, checked) }

        setupStylePicker()

        throttleBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (autoSwitch.isChecked) autoSwitch.isChecked = false
                ConnectionController.setThrottle(progress / 100.0)
                throttleLabel.text = "Acelerador manual: $progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        findViewById<Button>(R.id.btn_read_dtc).setOnClickListener { startDtcRead() }
        findViewById<Button>(R.id.btn_clear_dtc).setOnClickListener { startDtcClear() }

        findViewById<Button>(R.id.open_android_auto).setOnClickListener { openAndroidAuto() }
        findViewById<Button>(R.id.export_preview).setOnClickListener { exportPreview() }

        if (intent?.getBooleanExtra(EXTRA_START_SIM, false) == true) {
            ConnectionController.startSimulator(this)
        }
        if (intent?.getBooleanExtra(EXTRA_EXPORT_PREVIEW, false) == true) {
            handler.postDelayed({ exportPreview() }, PREVIEW_DELAY_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!updating) {
            updating = true
            handler.post(refreshTask)
        }
    }

    override fun onPause() {
        super.onPause()
        updating = false
        handler.removeCallbacks(refreshTask)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        scope.launch { } // mantém o escopo; conexão é do objeto global
        super.onDestroy()
    }

    // ---- seleção de adaptador ----

    private fun chooseScanner() {
        if (!ScannerDiscovery.hasPermissions(this)) {
            pendingScannerSelection = true
            requestBluetoothPermissions()
            return
        }
        if (!ConnectionController.isRunning) {
            showScannerList(emptyList(), scanning = true)
        } else {
            showScannerList(emptyList(), scanning = false)
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        // O serviço em primeiro plano precisa de notificação no Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        requestPermissions(permissions.toTypedArray(), REQUEST_BT_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted && pendingScannerSelection) {
                pendingScannerSelection = false
                chooseScanner()
            } else if (!granted) {
                showInfo(
                    "Permissão necessária",
                    "Sem permissão de Bluetooth o app não consegue procurar nem conectar ao scanner OBD2.",
                )
            }
        }
    }

    private fun showScannerList(devices: List<ScannerDevice>, scanning: Boolean) {
        if (scanning) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Procurando adaptadores…")
                .setMessage("Listando aparelhos pareados e procurando anúncios BLE por 12 segundos.")
                .setNegativeButton("Cancelar", null)
                .create()
            dialog.show()

            scope.launch {
                val bonded = withContext(Dispatchers.IO) { ScannerDiscovery.bondedDevices(this@MainActivity) }
                val found = linkedMapOf<String, ScannerDevice>()
                bonded.forEach { found[it.address] = it }

                withContext(Dispatchers.IO) {
                    runCatching {
                        ScannerDiscovery.scanBle(this@MainActivity).collect { device ->
                            if (!found.containsKey(device.address)) found[device.address] = device
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    showScannerList(found.values.toList(), scanning = false)
                }
            }
        } else {
            if (devices.isEmpty()) {
                showInfo(
                    "Nenhum adaptador encontrado",
                    "Pareie o scanner em Configurações → Bluetooth e tente de novo. " +
                        "Alguns adaptadores BLE só aparecem ligados na tomada OBD2 do carro.",
                )
                return
            }

            val labels = devices.map { device ->
                val kind = if (device.bonded) "pareado" else "BLE"
                "${device.name ?: "(sem nome)"} · ${device.address} · $kind"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Escolher adaptador")
                .setItems(labels) { _, which ->
                    val device = devices[which]
                    if (ConnectionController.isRunning) ConnectionController.disconnect()
                    ConnectionController.startScanner(this, device.address, device.name)
                    Toast.makeText(this, "Conectando a ${device.name ?: device.address}…", Toast.LENGTH_SHORT).show()
                    refreshUi()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ---- diagnóstico com duas confirmações ----

    private fun startDtcRead() {
        if (!ConnectionController.isRunning) {
            showInfo("Sem conexão", NOT_CONNECTED_MESSAGE)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Ler códigos de falha (DTC)")
            .setMessage(READ_EXPLANATION)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ -> confirmDtcRead() }
            .show()
    }

    private fun confirmDtcRead() {
        AlertDialog.Builder(this)
            .setTitle("Confirmar leitura agora?")
            .setMessage(
                "Nada será alterado no veículo. A leitura leva cerca de um segundo " +
                    "e pode ser repetida quantas vezes quiser.\n\nExecutar a leitura agora?",
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Executar leitura") { _, _ ->
                dtcResultView.text = "Lendo códigos…"
                ConnectionController.readDtcsAsync { codes ->
                    dtcResultView.text = if (codes.isEmpty()) {
                        "Nenhum código de falha confirmado."
                    } else {
                        "Códigos confirmados: " + codes.joinToString(", ")
                    }
                    refreshUi()
                }
            }
            .show()
    }

    private fun startDtcClear() {
        if (!ConnectionController.isRunning) {
            showInfo("Sem conexão", NOT_CONNECTED_MESSAGE)
            return
        }

        val speed = TelemetryRepository.state.value.speedKph
        if (speed != null && speed > STATIONARY_SPEED_LIMIT) {
            showInfo(
                "Veículo em movimento",
                "A leitura de velocidade indica ${speed.toInt()} km/h.\n\n" +
                    "Apagar códigos de falha só pode ser feito com o veículo parado: a operação " +
                    "zera os monitores de prontidão e o histórico de diagnóstico.",
            )
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Limpar códigos de falha (DTC)")
            .setMessage(CLEAR_EXPLANATION)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ -> confirmDtcClear() }
            .show()
    }

    private fun confirmDtcClear() {
        AlertDialog.Builder(this)
            .setTitle("Tem certeza?")
            .setMessage(
                "Esta é a única operação do app que ESCREVE na ECU.\n\n" +
                    "Serão apagados: códigos confirmados, pendentes, freeze frame e monitores de " +
                    "prontidão. A luz de injeção apaga. O defeito não é consertado.\n\nApagar agora?",
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar códigos") { _, _ ->
                dtcResultView.text = "Limpando códigos…"
                ConnectionController.clearDtcsAsync { ok ->
                    dtcResultView.text = if (ok) {
                        "Códigos limpos. Monitores de prontidão zerados."
                    } else {
                        "Falha ao limpar: o módulo não confirmou o comando."
                    }
                    refreshUi()
                }
            }
            .show()
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Entendi", null).show()
    }

    // ---- layout ----

    private fun setupStylePicker() {
        val spinner = findViewById<Spinner>(R.id.spinner_style)
        val description = findViewById<TextView>(R.id.style_description)
        val preview = findViewById<ImageView>(R.id.style_preview)
        val styles = DashboardStyle.entries

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles.map { it.label })

        val current = AppPrefs.dashboardStyle(this)
        spinner.setSelection(styles.indexOf(current), false)
        description.text = current.description

        fun renderPreview(style: DashboardStyle) {
            Thread {
                val telemetry = TelemetryRepository.state.value
                    .takeIf { it.source != DataSource.NONE && !it.isStale(System.currentTimeMillis()) }
                val bitmap = runCatching {
                    DashboardPreview.renderBitmap(this, style, PREVIEW_WIDTH, PREVIEW_HEIGHT, telemetry)
                }.getOrNull() ?: return@Thread
                runOnUiThread { if (!isFinishing) preview.setImageBitmap(bitmap) }
            }.start()
        }

        renderPreview(current)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val style = styles[position]
                AppPrefs.setDashboardStyle(this@MainActivity, style)
                description.text = style.description
                renderPreview(style)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun openAndroidAuto() {
        val launchIntent = packageManager.getLaunchIntentForPackage(ANDROID_AUTO_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
            return
        }
        val detailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", ANDROID_AUTO_PACKAGE, null),
        )
        if (detailsIntent.resolveActivity(packageManager) != null) {
            Toast.makeText(this, "Abrindo as configurações do Android Auto", Toast.LENGTH_SHORT).show()
            startActivity(detailsIntent)
        } else {
            Toast.makeText(this, "Android Auto não encontrado neste aparelho", Toast.LENGTH_LONG).show()
        }
    }

    // ---- atualização de tela ----

    private fun refreshUi() {
        statusView.text = statusText()
        liveView.text = liveText()
        consoleView.text = ConsoleLog.lines.value.takeLast(CONSOLE_LINES).joinToString("\n")
            .ifBlank { "(sem tráfego — conecte o scanner ou ligue o simulador)" }
        connectButton.text = if (ConnectionController.isRunning) "DESCONECTAR" else "LIGAR SIMULADOR"
    }

    private fun statusText(): String = buildString {
        val obd = ConnectionController.obdState.value
        val simulator = ConnectionController.isSimulator

        appendLine("Fonte: " + if (!ConnectionController.isRunning) "desconectado" else if (simulator) "simulador" else "scanner físico")
        when (obd) {
            is ObdState.Ready -> {
                appendLine("Conexão: pronta")
                appendLine("Adaptador: ${obd.adapter}")
                appendLine("Protocolo: ${obd.protocol}")
                appendLine("PIDs suportados: ${obd.supportedPids.size}")
            }
            is ObdState.Failed -> appendLine("Falha: ${obd.message}")
            ObdState.Connecting -> appendLine("Conectando…")
            ObdState.Disconnected -> appendLine("Sem conexão")
        }
        ConnectionController.vin?.let { appendLine("VIN: $it") }
        appendLine()
        append(installSourceText())
    }

    private fun liveText(): String {
        val t = TelemetryRepository.state.value
        val vehicle = ConnectionController.vehicleState.value
        return buildString {
            appendLine("RPM ${ObdConnection.format(t.rpm)}    Velocidade ${ObdConnection.format(t.speedKph)} km/h")
            appendLine("Água ${ObdConnection.format(t.coolantC)} °C    Ar ${ObdConnection.format(t.intakeC)} °C")
            appendLine("Carga ${ObdConnection.format(t.loadPct)}%    Acelerador ${ObdConnection.format(t.throttlePct)}%")
            appendLine("MAP ${ObdConnection.format(t.mapKpa)} kPa    MAF ${ObdConnection.format(t.mafGps, 1)} g/s")
            appendLine("Etanol ${ObdConnection.format(t.ethanolPct)}%    Bateria ${ObdConnection.format(t.moduleVoltage, 1)} V")
            appendLine("Nível ${ObdConnection.format(t.fuelLevelPct)}%    Avanço ${ObdConnection.format(t.timingAdvanceDeg)}°")
            appendLine(
                "MIL " + (if (t.milOn) "ACESA" else "apagada") +
                    "    DTCs ${t.dtcCount}" +
                    (vehicle?.let { "    Marcha ${it.gear}" } ?: "    Marcha —"),
            )
            append("Fonte: ${t.source}")
        }
    }

    private fun installSourceText(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { packageManager.getInstallSourceInfo(packageName) }.getOrNull()
        } else {
            null
        }
        val initiating = info?.initiatingPackageName
        val installing = info?.installingPackageName
        val trusted = initiating == PACKAGE_INSTALLER_PACKAGE || installing == PLAY_STORE_PACKAGE

        return buildString {
            appendLine("Instalado por: ${initiating ?: "desconhecido"}")
            appendLine("Pacote instalador: ${installing ?: "nenhum"}")
            append(
                if (trusted) "Android Auto deve aceitar esta instalação."
                else "Android Auto vai RECUSAR: instale pela Play Store, pelo UnknownAuto (Shizuku) ou por Internal App Sharing.",
            )
        }
    }

    private fun exportPreview() {
        Thread {
            val current = TelemetryRepository.state.value
                .takeIf { it.source != DataSource.NONE && !it.isStale(System.currentTimeMillis()) }
            runCatching {
                DashboardPreview.export(this, current, AppPrefs.dashboardStyle(this))
            }.onSuccess { files ->
                runOnUiThread {
                    Toast.makeText(this, "Prévias salvas em ${files.first().parentFile?.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                runOnUiThread { Toast.makeText(this, "Falha ao gerar prévia: ${error.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    companion object {
        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
        private const val PACKAGE_INSTALLER_PACKAGE = "com.google.android.packageinstaller"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"

        const val EXTRA_EXPORT_PREVIEW = "export_preview"
        const val EXTRA_START_SIM = "start_sim"

        private const val PREVIEW_DELAY_MS = 9_000L
        private const val REFRESH_INTERVAL_MS = 400L
        private const val CONSOLE_LINES = 12
        private const val PREVIEW_WIDTH = 960
        private const val PREVIEW_HEIGHT = 360
        private const val STATIONARY_SPEED_LIMIT = 1.0
        private const val REQUEST_BT_PERMISSIONS = 1001

        private val NOT_CONNECTED_MESSAGE =
            "Nenhum adaptador conectado.\n\nLigue o simulador ou conecte o scanner antes de consultar o diagnóstico."

        private val READ_EXPLANATION =
            "O que faz:\n" +
                "• Envia à ECU o pedido de códigos confirmados (Modo 03) e pendentes (Modo 07).\n" +
                "• Mostra os códigos encontrados e o estado da luz de injeção.\n\n" +
                "É SOMENTE LEITURA: não altera injeção, não apaga memória.\n\n" +
                "Impactos: nenhum sobre o carro.\n\nDeseja prosseguir?"

        private val CLEAR_EXPLANATION =
            "O que faz:\n" +
                "• Envia o comando de limpar códigos (Modo 04).\n" +
                "• Apaga confirmados e pendentes, o freeze frame e os monitores de prontidão.\n\n" +
                "Impactos:\n" +
                "• É a única operação do app que ESCREVE na ECU.\n" +
                "• O defeito NÃO é consertado: se a causa existir, o código volta.\n" +
                "• Zera a prontidão (pode reprovar inspeção de emissões).\n" +
                "• Apaga o histórico de diagnóstico.\n" +
                "• Só deve ser feita com o veículo PARADO.\n\nDeseja prosseguir?"

        private val INSTRUCTIONS = """
            Como usar

            SCANNER FÍSICO
            1. Ligue o carro e plugue o scanner na tomada OBD2.
            2. Pareie o adaptador em Configurações → Bluetooth.
            3. Toque em "Escolher adaptador" e selecione o seu.
            4. O app inicializa, descobre os PIDs e começa a ler.

            SIMULADOR (sem scanner)
            Toque em "Ligar simulador" e use o ciclo automático ou o acelerador manual.

            No carro, a tela mostra o layout escolhido acima.
            As ações de diagnóstico pedem confirmação em duas etapas.
        """.trimIndent()
    }
}

