package ru.sapn.vpn.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.vpn.VpnEngine

/**
 * ЗАГЛУШКА VPN-движка.
 *
 * Реального туннеля НЕ поднимает: только держит tun-дескриптор и логирует факт
 * старта/стопа (без чувствительных данных). Нужна, чтобы остальной код (сервис,
 * ViewModel, UI, состояние подключения) был полностью рабочим и тестируемым
 * без бинарной зависимости.
 *
 * ──────────────────────────────────────────────────────────────────────────
 * КАК ПОДКЛЮЧИТЬ РЕАЛЬНЫЙ ДВИЖОК (libXray / sing-box):
 *
 * 1. Положи AAR в app/libs/ и подключи в app/build.gradle.kts
 *    (раскомментируй implementation(files("libs/...")) ),
 *    либо подключи Maven-артефакт движка.
 *
 * 2. Собери из [config] JSON-конфиг outbound'а VLESS Reality, например для Xray:
 *      {
 *        "outbounds": [{
 *          "protocol": "vless",
 *          "settings": { "vnext": [{
 *            "address": config.host, "port": config.port,
 *            "users": [{ "id": config.uuid, "flow": config.flow, "encryption": "none" }]
 *          }]},
 *          "streamSettings": {
 *            "network": "tcp", "security": "reality",
 *            "realitySettings": {
 *              "publicKey": config.publicKey, "shortId": config.shortId,
 *              "serverName": config.sni, "fingerprint": config.fingerprint
 *            }
 *          }
 *        }],
 *        "inbounds": [{ "protocol": "dokodemo-door" / tun2socks ... }]
 *      }
 *    (для sing-box — соответствующий формат с inbound типа "tun").
 *
 * 3. Передай в движок файловый дескриптор tun: tunFd.detachFd() / tunFd.fd,
 *    запусти движок с собранным конфигом (напр. LibXray.runXray(...) /
 *    Libbox.newService(...).start()).
 *
 * 4. В [stop] корректно останови движок и закрой ресурсы.
 *
 * 5. DNS/маршруты/исключения приложений настраиваются на стороне VpnService
 *    (см. XrayVpnService.buildTun) и/или в конфиге движка.
 * ──────────────────────────────────────────────────────────────────────────
 */
class StubVpnEngine : VpnEngine {

    private var tun: ParcelFileDescriptor? = null

    override fun start(tunFd: ParcelFileDescriptor, config: VlessConfig) {
        tun = tunFd
        // Логируем только нечувствительное: хост и порт. uuid/ключи НЕ логируем.
        Log.i(TAG, "STUB engine start -> ${config.host}:${config.port} (${config.security})")
        // TODO(vpn-engine): здесь собрать конфиг и запустить бинарный движок.
    }

    override fun stop() {
        Log.i(TAG, "STUB engine stop")
        // TODO(vpn-engine): остановить бинарный движок.
        runCatching { tun?.close() }
        tun = null
    }

    private companion object {
        const val TAG = "StubVpnEngine"
    }
}
