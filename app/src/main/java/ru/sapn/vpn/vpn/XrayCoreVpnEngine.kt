package ru.sapn.vpn.vpn

import android.net.VpnService
import android.util.Log
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.model.VpnSettings
import ru.sapn.vpn.domain.vpn.VpnEngine
import ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface

/**
 * VPN-движок на базе sing-box (libbox AAR, sing-box 1.11.x), VLESS Reality.
 *
 * libbox строит tun сам через [AndroidPlatformInterface.openTun] (получая наш
 * [VpnService.Builder]) и владеет его FD — внешний tun не передаём. protect()
 * исходящих сокетов движка идёт через autoDetectInterfaceControl (анти-петля
 * handshake до ноды).
 *
 * Безопасность: configJson содержит uuid/ключи Reality — НИКОГДА его не логируем.
 */
class XrayCoreVpnEngine(
    private val service: VpnService,
) : VpnEngine {

    // boxService ← io.nekohasekai.libbox.BoxService
    // platformIface ← AndroidPlatformInterface (владеет tun FD)
    private var boxService: BoxService? = null
    private var platformIface: AndroidPlatformInterface? = null

    override fun start(config: VlessConfig, settings: VpnSettings) {
        // Собираем реальный конфиг sing-box. Сам объект конфига НЕ логируем.
        val configJson = SingBoxConfigBuilder.buildString(config, settings)
        Log.i(TAG, "engine start -> ${config.host}:${config.port} (${config.security}), config ${configJson.length}B")

        // Базовая инициализация libbox (однократно за процесс — безопасно повторять).
        Libbox.setup(
            SetupOptions().apply {
                basePath = service.filesDir.absolutePath
                workingPath = service.filesDir.absolutePath
                tempPath = service.cacheDir.absolutePath
            }
        )

        val platform = AndroidPlatformInterface(
            service = service,
            newBuilder = { service.Builder() },
        )
        // Libbox.newService(configContent, platformInterface): BoxService.
        val box = Libbox.newService(configJson, platform)
        box.start() // поднимает tun (через openTun) + sing-box, заворачивает трафик
        boxService = box
        platformIface = platform
        Log.i(TAG, "engine started")
    }

    override fun stop() {
        Log.i(TAG, "engine stop")
        boxService?.let { box -> runCatching { box.close() } } // останавливает sing-box
        platformIface?.let { runCatching { it.close() } }      // закрывает tun FD
        boxService = null
        platformIface = null
    }

    private companion object {
        const val TAG = "XrayCoreVpnEngine"
    }
}
