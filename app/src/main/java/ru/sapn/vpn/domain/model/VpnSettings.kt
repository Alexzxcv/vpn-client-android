package ru.sapn.vpn.domain.model

/** Режим per-app маршрутизации (split tunneling по приложениям). */
enum class AppRoutingMode {
    /** Все приложения через VPN. */
    OFF,

    /** Только выбранные приложения через VPN (allowlist). */
    INCLUDE,

    /** Все, КРОМЕ выбранных, через VPN (denylist). */
    EXCLUDE,
}

/**
 * Локальные настройки клиента.
 *
 * @param russiaDirect российские сайты (.ru/.рф/.su) идут напрямую, мимо туннеля.
 * @param directList   ручной split-tunnel: домены (".ru", "example.com") и/или
 *                     IP/CIDR ("10.0.0.0/8"), которые идут напрямую.
 * @param appMode      режим per-app маршрутизации.
 * @param appPackages  пакеты приложений для [appMode] (INCLUDE/EXCLUDE).
 *
 * Порт локального прокси (как в Windows-клиенте) на Android не применим: туннель
 * полностью на стороне sing-box (tun), отдельного SOCKS/HTTP-порта нет.
 */
data class VpnSettings(
    val russiaDirect: Boolean = false,
    val directList: List<String> = emptyList(),
    val appMode: AppRoutingMode = AppRoutingMode.OFF,
    val appPackages: List<String> = emptyList(),
)
