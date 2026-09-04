package ru.sapn.vpn.vpn

import android.net.Network
import android.net.VpnService
import android.util.Log
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.model.VpnSettings
import ru.sapn.vpn.domain.vpn.VpnEngine
import ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    // Смену сети обрабатываем не в колбэке ConnectivityManager: resetNetwork() —
    // нативный вызов, задерживать им системный колбэк не стоит.
    private var networkExecutor: ExecutorService? = null

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

        val executor = Executors.newSingleThreadExecutor()
        networkExecutor = executor

        val platform = AndroidPlatformInterface(
            service = service,
            newBuilder = { service.Builder() },
            onDefaultNetworkChanged = { network ->
                runCatching { executor.execute { applyNetworkChange(network) } }
            },
        )
        // Libbox.newService(configContent, platformInterface): BoxService.
        val box = Libbox.newService(configJson, platform)
        box.start() // поднимает tun (через openTun) + sing-box, заворачивает трафик
        boxService = box
        platformIface = platform
        // Первый onAvailable мог прийти, пока boxService ещё не был присвоен, —
        // подложку выставляем явно уже после старта.
        runCatching { service.setUnderlyingNetworks(platform.currentNetwork()?.let { arrayOf(it) }) }
        Log.i(TAG, "engine started")
    }

    /**
     * Подложка сменилась на уже поднятом туннеле (mobile ↔ wi-fi).
     *
     * Одного updateDefaultInterface мало: соединения, висящие на умершем
     * интерфейсе, sing-box сам не закрывает — туннель формально остаётся
     * «подключён», а трафик стоит, пока VPN не переключишь вручную.
     * resetNetwork() рвёт эти соединения и сбрасывает DNS-кеш, приложения
     * переоткрывают их уже через новую сеть. setUnderlyingNetworks сообщает
     * системе, поверх чего теперь работает VPN.
     */
    private fun applyNetworkChange(network: Network?) {
        val box = boxService ?: return
        runCatching { service.setUnderlyingNetworks(network?.let { arrayOf(it) }) }
        runCatching { box.resetNetwork() }
            .onFailure { Log.w(TAG, "resetNetwork failed: ${it.message}") }
    }

    override fun stop() {
        Log.i(TAG, "engine stop")
        networkExecutor?.let { runCatching { it.shutdownNow() } }
        networkExecutor = null
        boxService?.let { box -> runCatching { box.close() } } // останавливает sing-box
        platformIface?.let { runCatching { it.close() } }      // закрывает tun FD
        boxService = null
        platformIface = null
    }

    private companion object {
        const val TAG = "XrayCoreVpnEngine"
    }
}
