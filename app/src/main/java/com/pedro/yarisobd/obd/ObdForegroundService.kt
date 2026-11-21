package com.pedro.yarisobd.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.yarisobd.MainActivity
import com.pedro.yarisobd.R

/**
 * Serviço em primeiro plano que mantém a leitura OBD viva enquanto o app está
 * projetado no carro.
 *
 * Por que existe: sem serviço em primeiro plano, o Android trata o app como
 * candidato a congelamento quando você abre outro aplicativo no celular (câmera,
 * WhatsApp). O sintoma que isso causava era exatamente o relatado em campo: a
 * tela da central continuava lá, mas os valores paravam de atualizar.
 *
 * O serviço não faz a leitura em si — ele mantém o **processo** ativo; o laço de
 * leitura continua no [ConnectionController].
 */
class ObdForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Nunca deixar o serviço derrubar o app: se o sistema não permitir o
        // primeiro plano agora (ex.: sem permissão de Bluetooth ou app em
        // segundo plano), o app continua funcionando — só perde a garantia de
        // ficar vivo com outro aplicativo aberto.
        return try {
            startForeground(NOTIFICATION_ID, buildNotification())
            START_STICKY
        } catch (error: Exception) {
            android.util.Log.w("YarisOBD", "Serviço em primeiro plano não permitido: ${error.message}")
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Leitura OBD ativa — mantendo a conexão com o carro")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Conexão com o scanner",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mantém a leitura OBD ativa enquanto o app está no carro"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "obd_connection"
        private const val NOTIFICATION_ID = 1001

        /** Liga o serviço sem quebrar em versões antigas de Android. */
        fun start(context: Context) {
            val intent = Intent(context, ObdForegroundService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ObdForegroundService::class.java)) }
        }
    }
}
