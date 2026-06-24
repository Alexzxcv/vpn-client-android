package ru.sapn.vpn.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.vpn.VpnEngine

/**
 * VPN-движок на базе Xray-core (VLESS Reality).
 *
 * Текущее состояние: движок СОБИРАЕТ реальный рабочий конфиг Xray из [VlessConfig]
 * ([XrayConfigBuilder]) и держит tun-дескриптор, но фактический запуск бинарного
 * Xray-core + tun2socks выполняется только при подключённом AAR (см. ниже).
 * Без AAR ведёт себя как заглушка (туннель не поднимается), чтобы сборка и весь
 * остальной код оставались рабочими.
 *
 * ──────────────────────────────────────────────────────────────────────────
 * ЧТО ОСТАЛОСЬ ДЛЯ ПОЛНОЙ ИНТЕГРАЦИИ (один контролируемый шаг):
 *
 * Архитектура: Xray-core НЕ имеет нативного tun-inbound, поэтому связка такая:
 *     tun (VpnService) ──tun2socks──> SOCKS5 127.0.0.1:10808 (Xray inbound)
 *                                       └─ VLESS Reality outbound ─> нода
 *
 * 1. AAR:
 *    Вариант A (рекомендуется) — libXray:
 *      - Источник: github.com/xtls/libXray (Go, собирается через gomobile bind).
 *      - Сборка:  gomobile bind -target=android -androidapi 26 -o libxray.aar ./...
 *      - Кладём app/libs/libxray.aar, в app/build.gradle.kts:
 *            implementation(files("libs/libxray.aar"))
 *      - Дополнительно нужен tun2socks AAR (напр. github.com/heiher/hev-socks5-tunnel
 *        или badvpn-tun2socks, собранный под Android) — для моста tun→SOCKS.
 *
 *    Вариант B — sing-box (libbox), если переходим с Xray на sing-box:
 *      - github.com/SagerNet/sing-box, gomobile bind → libbox.aar.
 *      - У sing-box есть нативный tun-inbound: тогда tun2socks НЕ нужен, FD от
 *        VpnService отдаётся напрямую через PlatformInterface, а конфиг строится
 *        в формате sing-box (отдельный билдер). Точка интеграции — та же ([start]).
 *
 * 2. Запуск (точка интеграции — ровно здесь, в [start]):
 *      val configJson = XrayConfigBuilder.buildString(config)   // уже готов
 *      // a) стартуем Xray-core с configJson:
 *      //      LibXray.runXrayFromJSON(datadir, configJson)      // имя метода — по версии libXray
 *      // b) стартуем tun2socks, отдав ему tunFd.detachFd() и адрес SOCKS:
 *      //      Tun2socks.start(tunFd.detachFd(),
 *      //                      XrayConfigBuilder.SOCKS_HOST, XrayConfigBuilder.SOCKS_PORT, mtu=1500)
 *
 * 3. Маршрут до самой ноды (config.host) должен идти НАПРЯМУЮ, иначе handshake
 *    Xray зациклится через tun. Это настраивается в [XrayVpnService.buildTun]
 *    (addRoute-исключение / addDisallowedApplication уже частично закрывает вопрос,
 *    т.к. процесс приложения исключён из tun).
 *
 * 4. В [stop] — корректно остановить Xray и tun2socks и закрыть ресурсы.
 *
 * Безопасность: configJson содержит uuid/ключи Reality — НИКОГДА не логируем его.
 * ──────────────────────────────────────────────────────────────────────────
 */
class XrayCoreVpnEngine : VpnEngine {

    private var tun: ParcelFileDescriptor? = null

    override fun start(tunFd: ParcelFileDescriptor, config: VlessConfig) {
        tun = tunFd

        // Собираем реальный конфиг Xray. Сам объект конфига НЕ логируем.
        val configJson = XrayConfigBuilder.buildString(config)
        // Лог только нечувствительного: факт старта, хост:порт, размер конфига.
        Log.i(TAG, "engine start -> ${config.host}:${config.port} (${config.security}), config ${configJson.length}B")

        // TODO(vpn-engine): здесь запустить Xray-core (configJson) и tun2socks (tunFd).
        // См. подробную инструкцию в KDoc этого класса. Пока AAR не подключён —
        // туннель не поднимается (трафик идёт мимо), остальное приложение работает.
        if (!ENGINE_AAR_AVAILABLE) {
            Log.w(TAG, "Xray AAR не подключён — туннель не активен (см. KDoc XrayCoreVpnEngine).")
        }
    }

    override fun stop() {
        Log.i(TAG, "engine stop")
        // TODO(vpn-engine): остановить Xray-core и tun2socks.
        runCatching { tun?.close() }
        tun = null
    }

    private companion object {
        const val TAG = "XrayCoreVpnEngine"

        /**
         * Флаг наличия бинарного движка. Ставится в true после подключения AAR и
         * раскомментирования реального запуска в [start]/[stop].
         */
        const val ENGINE_AAR_AVAILABLE = false
    }
}
