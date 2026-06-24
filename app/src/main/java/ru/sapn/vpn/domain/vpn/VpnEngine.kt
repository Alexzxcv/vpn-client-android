package ru.sapn.vpn.domain.vpn

import android.os.ParcelFileDescriptor
import ru.sapn.vpn.domain.model.VlessConfig

/** Состояние VPN-туннеля. */
enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

/**
 * Абстракция бинарного VPN-движка (Xray-core / sing-box).
 *
 * Реализация принимает уже открытый tun-дескриптор (его готовит [android.net.VpnService])
 * и [VlessConfig], собирает из них рабочий конфиг движка и поднимает прокси внутри процесса,
 * заворачивая трафик из tun в VLESS Reality.
 *
 * Реализация — [ru.sapn.vpn.vpn.XrayCoreVpnEngine] (sing-box/libbox). Без AAR она
 * работает как безопасная заглушка; подключение реального движка — см. её KDoc.
 */
interface VpnEngine {
    /**
     * Запустить движок.
     * @param tunFd дескриптор tun-интерфейса от VpnService.establish().
     * @param config параметры VLESS Reality ноды.
     */
    fun start(tunFd: ParcelFileDescriptor, config: VlessConfig)

    /** Остановить движок и освободить ресурсы. */
    fun stop()
}
