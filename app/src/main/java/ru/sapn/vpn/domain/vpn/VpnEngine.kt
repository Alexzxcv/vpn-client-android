package ru.sapn.vpn.domain.vpn

import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.model.VpnSettings

/** Состояние VPN-туннеля. */
enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

/**
 * Абстракция VPN-движка (sing-box / libbox).
 *
 * Движок сам строит tun через VpnService (libbox.PlatformInterface.openTun) из
 * [VlessConfig] и заворачивает трафик в VLESS Reality. tun снаружи НЕ передаётся —
 * иначе на Android получаются два конкурирующих establish() (см. историю #2).
 *
 * Реализация — [ru.sapn.vpn.vpn.XrayCoreVpnEngine].
 */
interface VpnEngine {
    /** Запустить движок: поднять tun + sing-box по [config] и [settings]. Бросает при ошибке. */
    fun start(config: VlessConfig, settings: VpnSettings)

    /** Остановить движок и освободить ресурсы. */
    fun stop()
}
