package ru.sapn.vpn.vpn

import android.net.Uri
import ru.sapn.vpn.domain.model.VlessConfig
import java.net.URLEncoder

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

    /**
     * Обратная операция к [parse]: собирает vless://-ссылку своего сервера для
     * кнопки «Поделиться». Порядок и набор параметров — как у v2rayNG/Nekoray,
     * чтобы ссылку принял любой клиент, а [parse] разобрал её обратно один в один.
     *
     * Uri.Builder намеренно не используем: так функция тестируется без Android.
     */
    fun build(name: String, config: VlessConfig): String {
        val params = buildList {
            add("type" to "tcp")
            add("encryption" to "none")
            add("security" to config.security.ifBlank { "reality" })
            if (config.sni.isNotBlank()) add("sni" to config.sni)
            if (config.fingerprint.isNotBlank()) add("fp" to config.fingerprint)
            if (config.publicKey.isNotBlank()) add("pbk" to config.publicKey)
            if (config.shortId.isNotBlank()) add("sid" to config.shortId)
            if (config.flow.isNotBlank()) add("flow" to config.flow)
        }.joinToString("&") { (key, value) -> "$key=${encode(value)}" }

        // IPv6-литерал в URI обязан быть в скобках, иначе двоеточия съедят порт.
        val host = if (config.host.contains(':')) "[${config.host}]" else config.host
        val fragment = name.takeIf { it.isNotBlank() }?.let { "#${encode(it)}" }.orEmpty()
        return "vless://${config.uuid}@$host:${config.port}?$params$fragment"
    }

    /** URLEncoder кодирует пробел как "+", в URI это верно только для query. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
