package ru.sapn.vpn.vpn

import android.net.Uri
import ru.sapn.vpn.domain.model.VlessConfig

/**
 * Парсер VLESS Reality-ссылок вида:
 *   vless://<uuid>@<host>:<port>?security=reality&pbk=<pubkey>&sid=<shortid>
 *           &sni=<sni>&fp=<fingerprint>&flow=<flow>&type=tcp#<name>
 *
 * Возвращает имя (из #fragment) и [VlessConfig]. Бросает с понятным сообщением,
 * если ссылка некорректна.
 */
object VlessLinkParser {

    fun parse(raw: String): Result<Pair<String, VlessConfig>> = runCatching {
        val s = raw.trim()
        require(s.startsWith("vless://")) { "Ссылка должна начинаться с vless://" }

        val uri = Uri.parse(s)
        val uuid = uri.userInfo?.takeIf { it.isNotBlank() }
            ?: error("В ссылке нет UUID (vless://UUID@host:port)")
        val host = uri.host?.takeIf { it.isNotBlank() } ?: error("В ссылке нет хоста")
        val port = uri.port.takeIf { it > 0 } ?: error("В ссылке нет порта")

        fun q(key: String): String? = uri.getQueryParameter(key)?.takeIf { it.isNotBlank() }

        val name = uri.fragment?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() } ?: host

        val config = VlessConfig(
            host = host,
            port = port,
            uuid = uuid,
            security = q("security") ?: "reality",
            flow = q("flow") ?: "",
            publicKey = q("pbk") ?: "",
            shortId = q("sid") ?: "",
            sni = q("sni") ?: q("peer") ?: host,
            fingerprint = q("fp") ?: "chrome",
        )
        name to config
    }
}
