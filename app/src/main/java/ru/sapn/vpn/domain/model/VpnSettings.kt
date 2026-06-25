package ru.sapn.vpn.domain.model

/**
 * Локальные настройки клиента.
 *
 * @param russiaDirect российские сайты (.ru/.рф/.su) идут напрямую, мимо туннеля.
 * @param directList   ручной split-tunnel: домены (".ru", "example.com") и/или
 *                     IP/CIDR ("10.0.0.0/8"), которые идут напрямую.
 *
 * Порт локального прокси (как в Windows-клиенте) на Android не применим: туннель
 * полностью на стороне sing-box (tun), отдельного SOCKS/HTTP-порта нет.
 */
data class VpnSettings(
    val russiaDirect: Boolean = false,
    val directList: List<String> = emptyList(),
)
