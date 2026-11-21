package com.pedro.yarisobd.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Adaptador OBD2 físico por Bluetooth.
 *
 * Dois caminhos, porque o mercado tem os dois e o usuário não deveria precisar
 * saber qual comprou:
 *  - **Clássico/SPP (RFCOMM)**: ELM327 "azul" comum, OBDLink MX, vLinker, Vgate BT.
 *  - **BLE (GATT)**: iCar Pro BLE, OBDLink CX, Veepeak BLE.
 *
 * A camada acima (`ElmSession`) só enxerga [ObdLink] com streams; é por isso que
 * os dois funcionam sem mudar o resto do código.
 */
class BluetoothTransport(
    private val context: Context,
    private val address: String,
    private val deviceName: String?,
    private val mode: Mode = Mode.AUTO,
) : ObdTransport {

    enum class Mode { AUTO, CLASSIC, BLE }

    override val description: String
        get() = (deviceName ?: address) + when (resolvedMode) {
            Mode.BLE -> " · BLE"
            Mode.CLASSIC -> " · Bluetooth clássico"
            Mode.AUTO -> ""
        }

    override val isVirtual: Boolean = false

    @Volatile
    private var resolvedMode: Mode = mode

    private var socket: BluetoothSocket? = null
    private var bleLink: BleLink? = null

    override suspend fun open(): ObdLink = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(BluetoothManager::class.java)
            ?: throw IllegalStateException("Este aparelho não tem Bluetooth")
        val adapter = manager.adapter
            ?: throw IllegalStateException("Este aparelho não tem Bluetooth")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Permissão de Bluetooth não concedida")
        }
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth está desligado")

        val device = adapter.getRemoteDevice(address)

        when (mode) {
            Mode.BLE -> return@withContext openBle(device)
            Mode.CLASSIC -> return@withContext openClassic(device)
            Mode.AUTO -> {
                // Clássico falha rápido em aparelho só-BLE; se falhar, tenta BLE.
                val classic = runCatching { openClassic(device) }.getOrNull()
                if (classic != null) return@withContext classic
                return@withContext openBle(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openClassic(device: BluetoothDevice): ObdLink {
        resolvedMode = Mode.CLASSIC
        val uuid = UUID.fromString(SPP_UUID)
        val created = device.createRfcommSocketToServiceRecord(uuid)
        socket = created
        created.connect()
        return ObdLink(created.inputStream, created.outputStream)
    }

    private suspend fun openBle(device: BluetoothDevice): ObdLink {
        resolvedMode = Mode.BLE
        val link = BleLink()
        val connected = withTimeoutOrNull(BLE_CONNECT_TIMEOUT_MS) {
            link.connect(context, device)
        } ?: false

        if (!connected) {
            link.close()
            throw IllegalStateException(
                "Não foi possível conectar por BLE. Confira se o adaptador está pareado e " +
                    "se não está conectado a outro aparelho.",
            )
        }

        bleLink = link
        return ObdLink(link.inputStream(), link.outputStream())
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        runCatching { bleLink?.close() }
        bleLink = null
    }

    companion object {
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val BLE_CONNECT_TIMEOUT_MS = 15_000L

        /** Serviços UART usados pelos adaptadores BLE mais comuns (FFE0/FFE5/18F0). */
        val BLE_SERVICE_UUIDS = listOf(
            UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000FFE5-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("000018F0-0000-1000-8000-00805F9B34FB"),
        )

        /** Characteristics de escrita correspondentes. */
        val BLE_WRITE_UUIDS = listOf(
            UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("00002AF8-0000-1000-8000-00805F9B34FB"),
        )

        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}

/**
 * Canal BLE: as notificações da characteristic viram um `InputStream`; as
 * escritas vão para o mesmo canal.
 *
 * Em BLE cada escrita é um pacote (20 bytes com MTU padrão). Comando do ELM327
 * cabe; resposta longa chega em pedaços — por isso a leitura usa as
 * notificações, e não um tamanho fixo.
 */
@SuppressLint("MissingPermission")
private class BleLink {

    private val incoming = InMemoryPipe()
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    suspend fun connect(context: Context, device: BluetoothDevice): Boolean {
        val ready = CompletableDeferred<Boolean>()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else {
                    if (!ready.isCompleted) ready.complete(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                gatt = g
                val service = pickService(g)
                val characteristic = pickCharacteristic(service)
                if (characteristic == null) {
                    if (!ready.isCompleted) ready.complete(false)
                    return
                }
                writeCharacteristic = characteristic
                g.setCharacteristicNotification(characteristic, true)

                characteristic.getDescriptor(BluetoothTransport.Companion.let { UUID.fromString("00002902-0000-1000-8000-00805F9B34FB") })
                    ?.let { descriptor ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(descriptor)
                        }
                    }

                if (!ready.isCompleted) ready.complete(true)
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                incoming.write(value)
            }

            @Deprecated("API < 33")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                incoming.write(value)
            }
        }

        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) ?: return false
        gatt = g
        return ready.await()
    }

    fun inputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            val read = read(one, 0, 1)
            return if (read <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = incoming.read(b, off, len, 200)

        override fun available(): Int = incoming.available()

        override fun close() = incoming.close()
    }

    fun outputStream(): OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            val g = gatt ?: return
            val characteristic = writeCharacteristic ?: return
            val chunk = bytes.copyOfRange(offset, offset + length)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = chunk
            g.writeCharacteristic(characteristic)
        }
    }

    fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        incoming.close()
    }

    private fun pickService(g: BluetoothGatt): BluetoothGattService? {
        BluetoothTransport.BLE_SERVICE_UUIDS.forEach { uuid ->
            g.getService(uuid)?.let { return it }
        }
        return g.services.firstOrNull { service ->
            service.characteristics.any {
                it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            }
        }
    }

    private fun pickCharacteristic(service: BluetoothGattService?): BluetoothGattCharacteristic? {
        if (service == null) return null
        BluetoothTransport.BLE_WRITE_UUIDS.forEach { uuid ->
            service.getCharacteristic(uuid)?.let { return it }
        }
        return service.characteristics.firstOrNull {
            it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        }
    }
}
