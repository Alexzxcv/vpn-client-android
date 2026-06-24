package ru.sapn.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import ru.sapn.vpn.R
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.vpn.VpnEngine
import ru.sapn.vpn.domain.vpn.VpnState

/**
 * Каркас VPN-сервиса.
 *
 * Жизненный цикл:
 *  1. UI вызывает VpnService.prepare() (диалог разрешения). После согласия —
 *     [VpnController.start], который шлёт ACTION_CONNECT.
 *  2. Сервис строит tun ([buildTun]) и отдаёт дескриптор движку [VpnEngine].
 *  3. ACTION_DISCONNECT / onDestroy останавливают движок и закрывают tun.
 *
 * Реальную маршрутизацию трафика делает бинарный движок [XrayCoreVpnEngine]
 * (sing-box/libbox). Без подключённого AAR движок — заглушка: tun поднимается,
 * но трафик не заворачивается.
 */
class XrayVpnService : VpnService() {

    // TODO(di): когда появится DI, инжектить реальный VpnEngine.
    // Движку передаём сам VpnService: libbox через него делает protect() и строит
    // свой tun (PlatformInterface). В fallback-режиме это не используется.
    private val engine: VpnEngine = XrayCoreVpnEngine(service = this)

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> {
                disconnect()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
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
            val fd = buildTun(config)
            tun = fd
            engine.start(fd, config)
            VpnController.updateState(VpnState.CONNECTED)
        } catch (t: Throwable) {
            Log.e(TAG, "connect failed", t)
            VpnController.updateState(VpnState.ERROR)
            cleanup()
            stopSelf()
        }
    }

    private fun disconnect() {
        cleanup()
        VpnController.updateState(VpnState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Создаёт tun-интерфейс. Адреса/маршруты ниже — базовые «все наружу».
     *
     * ВАЖНО про libbox: в реальном режиме (ENGINE_AAR_AVAILABLE=true) sing-box
     * строит СВОЙ tun сам через PlatformInterface.openTun, а этот предварительный
     * дескриптор движок закрывает. Здесь tun нужен прежде всего для fallback-режима
     * и для валидации разрешения VPN. Защита от петли handshake до ноды в реальном
     * режиме обеспечивается protect() сокетов sing-box (autoDetectInterfaceControl),
     * а не маршрутом-исключением.
     */
    private fun buildTun(config: VlessConfig): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("SAPN VPN")
            .setMtu(1500)
            .addAddress("10.66.66.2", 24)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)        // весь IPv4 в туннель
            .addRoute("::", 0)             // весь IPv6 в туннель

        // Не заворачиваем собственный трафик приложения, чтобы не зациклить
        // обращения к control-plane и не пускать их через незапущенный движок.
        runCatching { builder.addDisallowedApplication(packageName) }

        // TODO(vpn-engine): при необходимости добавить маршрут-исключение для
        //  адреса самой ноды (config.host), чтобы handshake движка шёл напрямую.

        return builder.establish()
            ?: error("VpnService.establish() returned null (нет разрешения VPN?)")
    }

    private fun cleanup() {
        runCatching { engine.stop() }
        runCatching { tun?.close() }
        tun = null
    }

    override fun onRevoke() {
        // Система отозвала разрешение VPN (например, другой VPN перехватил).
        disconnect()
        super.onRevoke()
    }

    override fun onDestroy() {
        cleanup()
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
