package com.pedro.yarisobd.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull

/** Um adaptador encontrado (pareado ou anunciado por BLE). */
data class ScannerDevice(
    val name: String?,
    val address: String,
    val bonded: Boolean,
    val supportsBle: Boolean,
)

/**
 * Descobre adaptadores OBD2: lista os aparelhos já pareados e, se permitido,
 * varre BLE por anúncios.
 *
 * Pré-requisito: permissões de Bluetooth. Sem `BLUETOOTH_SCAN` no Android 12+
 * nem faz sentido tentar — a varredura simplesmente não devolve nada.
 */
object ScannerDiscovery {

    fun hasPermissions(context: Context): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Aparelhos pareados no sistema. É onde o ELM327 clássico sempre aparece. */
    @SuppressLint("MissingPermission")
    fun bondedDevices(context: Context): List<ScannerDevice> {
        if (!hasPermissions(context)) return emptyList()
        val adapter: BluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return adapter.bondedDevices.orEmpty().map { device ->
            ScannerDevice(
                name = runCatching { device.name }.getOrNull(),
                address = device.address,
                bonded = true,
                supportsBle = device.type == BluetoothDevice.DEVICE_TYPE_LE ||
                    device.type == BluetoothDevice.DEVICE_TYPE_DUAL,
            )
        }.sortedBy { it.name ?: it.address }
    }

    /**
     * Varredura BLE por [durationMs]. Devolve o que aparecer no período — não
     * existe "fim" de varredura, então o tempo é a única forma de parar.
     */
    @SuppressLint("MissingPermission")
    fun scanBle(context: Context, durationMs: Long = 12_000): Flow<ScannerDevice> = callbackFlow {
        if (!hasPermissions(context)) {
            close()
            return@callbackFlow
        }

        val scanner = context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val seen = mutableSetOf<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (seen.add(device.address)) {
                    trySend(
                        ScannerDevice(
                            name = runCatching { result.scanRecord?.deviceName ?: device.name }.getOrNull(),
                            address = device.address,
                            bonded = device.bondState == BluetoothDevice.BOND_BONDED,
                            supportsBle = true,
                        ),
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, callback)
        val finished = withTimeoutOrNull(durationMs) { awaitClose { scanner.stopScan(callback) } }
        if (finished == null) {
            runCatching { scanner.stopScan(callback) }
            close()
        }
    }
}
