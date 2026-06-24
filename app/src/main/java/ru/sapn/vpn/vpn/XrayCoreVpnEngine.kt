package ru.sapn.vpn.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.vpn.VpnEngine

/**
 * VPN-движок на базе sing-box (libbox AAR), VLESS Reality.
 *
 * Почему sing-box, а не Xray+tun2socks:
 *  - у sing-box НАТИВНЫЙ tun-inbound: отдельный tun2socks-мост не нужен (одна
 *    зависимость вместо двух, меньше точек отказа);
 *  - libbox строит tun сам через PlatformInterface.openTun, отдавая нам
 *    VpnService.Builder — protect()/маршруты/DNS централизованы в движке;
 *  - формат конфига совпадает с Windows-клиентом (internal/singbox), поэтому
 *    [SingBoxConfigBuilder] зеркалит уже проверенную в бою логику.
 *
 * Текущее состояние: движок СОБИРАЕТ реальный рабочий конфиг sing-box из
 * [VlessConfig] ([SingBoxConfigBuilder]), но фактический запуск libbox.BoxService
 * выполняется только при подключённом AAR (флаг [ENGINE_AAR_AVAILABLE]). Без AAR
 * ведёт себя как безопасная заглушка (туннель не поднимается), чтобы сборка и весь
 * остальной код оставались рабочими.
 *
 * ──────────────────────────────────────────────────────────────────────────
 * КАК ВКЛЮЧИТЬ РЕАЛЬНЫЙ ДВИЖОК (один контролируемый шаг):
 *
 * 1. Собрать libbox.aar (sing-box mobile) на машине с Go+gomobile+Android NDK:
 *      git clone https://github.com/SagerNet/sing-box
 *      cd sing-box
 *      # вариант через Makefile апстрима:
 *      make lib_install      # ставит gomobile
 *      make lib_android      # → libbox.aar (с тегами with_gvisor,with_quic,…)
 *      # или вручную:
 *      go run ./cmd/internal/build_libbox -target android
 *    Скопировать полученный libbox.aar в app/libs/.
 *
 * 2. В app/build.gradle.kts раскомментировать:
 *      implementation(files("libs/libbox.aar"))
 *
 * 3. Раскомментировать тело
 *    app/src/main/java/ru/sapn/vpn/vpn/libbox/AndroidPlatformInterface.kt
 *    и реальный блок запуска ниже в [start] (помечен «РЕАЛЬНЫЙ ПУТЬ (libbox)»).
 *
 * 4. Поставить [ENGINE_AAR_AVAILABLE] = true.
 *
 * Безопасность: configJson содержит uuid/ключи Reality — НИКОГДА его не логируем.
 * ──────────────────────────────────────────────────────────────────────────
 */
class XrayCoreVpnEngine(
    /**
     * Активный VpnService. Нужен libbox-у для protect() сокетов и построения tun
     * через PlatformInterface. В fallback-режиме не используется.
     */
    private val service: VpnService? = null,
) : VpnEngine {

    /**
     * tun, который мог быть передан снаружи (fallback-режим). В реальном режиме
     * libbox строит tun сам через PlatformInterface и владеет его FD.
     */
    private var tun: ParcelFileDescriptor? = null

    // Держатели реального движка. Тип — Any?, чтобы файл компилировался без AAR.
    // boxService    ← io.nekohasekai.libbox.BoxService
    // platformIface ← ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface (владеет tun FD)
    private var boxService: Any? = null
    private var platformIface: Any? = null

    override fun start(tunFd: ParcelFileDescriptor, config: VlessConfig) {
        tun = tunFd

        // Собираем реальный конфиг sing-box. Сам объект конфига НЕ логируем.
        val configJson = SingBoxConfigBuilder.buildString(config)
        // Лог только нечувствительного: факт старта, хост:порт, размер конфига.
        Log.i(
            TAG,
            "engine start -> ${config.host}:${config.port} (${config.security}), config ${configJson.length}B",
        )

        if (!ENGINE_AAR_AVAILABLE) {
            Log.w(TAG, "libbox AAR не подключён — туннель не активен (см. KDoc XrayCoreVpnEngine).")
            return
        }

        /*
         * ── РЕАЛЬНЫЙ ПУТЬ (libbox). Раскомментировать вместе с AAR (см. KDoc). ──
         *
         * import io.nekohasekai.libbox.BoxService
         * import io.nekohasekai.libbox.Libbox
         * import io.nekohasekai.libbox.SetupOptions
         * import ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface
         *
         * val svc = service ?: error("libbox requires an active VpnService")
         *
         * // tun, переданный снаружи, libbox-у не нужен: он строит свой через
         * // PlatformInterface.openTun. Закрываем «лишний» дескриптор, чтобы не течь.
         * runCatching { tunFd.close() }
         * tun = null
         *
         * // Базовая инициализация libbox (однократно за процесс — безопасно повторять).
         * // Сигнатура: Libbox.setup(SetupOptions) — поля basePath/workingPath/tempPath.
         * Libbox.setup(
         *     SetupOptions().apply {
         *         basePath = svc.filesDir.absolutePath
         *         workingPath = svc.filesDir.absolutePath
         *         tempPath = svc.cacheDir.absolutePath
         *     }
         * )
         *
         * val platform = AndroidPlatformInterface(
         *     service = svc,
         *     newBuilder = { svc.Builder() },
         * )
         * // Сигнатура: Libbox.newService(configContent, platformInterface): BoxService.
         * val box = Libbox.newService(configJson, platform)
         * box.start()           // поднимает tun + sing-box, заворачивает трафик
         * boxService = box
         * platformIface = platform
         */
    }

    override fun stop() {
        Log.i(TAG, "engine stop")

        /*
         * ── РЕАЛЬНЫЙ ПУТЬ (libbox). Раскомментировать вместе с AAR. ──
         *
         * (boxService as? io.nekohasekai.libbox.BoxService)?.let { box ->
         *     runCatching { box.close() } // останавливает sing-box
         * }
         * (platformIface as? ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface)?.let {
         *     runCatching { it.close() }  // закрывает tun FD, которым владеет PlatformInterface
         * }
         */
        boxService = null
        platformIface = null

        runCatching { tun?.close() }
        tun = null
    }

    private companion object {
        const val TAG = "XrayCoreVpnEngine"

        /**
         * Флаг наличия бинарного движка. Ставится в true после подключения
         * libbox.aar и раскомментирования реального пути в [start]/[stop] и в
         * [ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface].
         */
        const val ENGINE_AAR_AVAILABLE = false
    }
}
