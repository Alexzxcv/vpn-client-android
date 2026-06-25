package ru.sapn.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import kotlinx.coroutines.runBlocking
import ru.sapn.vpn.R
import ru.sapn.vpn.data.local.SettingsStore
import ru.sapn.vpn.domain.vpn.VpnEngine
import ru.sapn.vpn.domain.vpn.VpnState

/**
 * VPN-сервис. Реальную маршрутизацию делает sing-box ([XrayCoreVpnEngine]).
 *
 * Жизненный цикл:
 *  1. UI вызывает VpnService.prepare() (диалог разрешения). После согласия —
 *     [VpnController.start] шлёт ACTION_CONNECT.
 *  2. Сервис стартует foreground и отдаёт конфиг движку, который сам строит tun
 *     (libbox.PlatformInterface.openTun) и заворачивает трафик.
 *  3. ACTION_DISCONNECT / onDestroy останавливают движок.
 *
 * START_NOT_STICKY: если процесс умрёт, система НЕ перезапускает сервис с тем же
 * intent (иначе при сбое движка получался цикл рестартов).
 */
class XrayVpnService : VpnService() {

    private val engine: VpnEngine = XrayCoreVpnEngine(service = this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun connect() {
        val config = VpnController.consumePendingConfig()
        if (config == null) {
            Log.w(TAG, "connect: pending config is null")
            VpnController.updateState(VpnState.ERROR)
            stopSelf()
            return
        }

        VpnController.updateState(VpnState.CONNECTING)
        startForeground(NOTIF_ID, buildNotification())

        try {
            val settings = runBlocking { SettingsStore(applicationContext).get() }
            engine.start(config, settings)
            VpnController.updateState(VpnState.CONNECTED)
        } catch (t: Throwable) {
            Log.e(TAG, "connect failed: ${t.message}")
            VpnController.updateState(VpnState.ERROR)
            runCatching { engine.stop() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun disconnect() {
        runCatching { engine.stop() }
        VpnController.updateState(VpnState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // Система отозвала разрешение VPN (например, другой VPN перехватил).
        disconnect()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { engine.stop() }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("VPN активен")
            .setSmallIcon(R.drawable.ic_tile_vpn)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "ru.sapn.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.sapn.vpn.DISCONNECT"

        private const val TAG = "XrayVpnService"
        private const val CHANNEL_ID = "sapn_vpn"
        private const val NOTIF_ID = 1
    }
}
