package ru.sapn.vpn.vpn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import ru.sapn.vpn.domain.model.VlessConfig

/**
 * Сборка JSON-конфига sing-box из [VlessConfig].
 *
 * sing-box имеет НАТИВНЫЙ tun-inbound, поэтому tun2socks не нужен: дескриптор tun,
 * который создаёт [android.net.VpnService], libbox получает через PlatformInterface
 * (наш [ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface.openTun]).
 *
 * Структура зеркалит Windows-клиент (internal/singbox/config.go):
 *  - tun inbound (адрес /30, auto_route, stack=system, sniff DNS);
 *  - vless + reality outbound (tag "vless-out") + direct + block;
 *  - DNS через туннель (анти-leak), strategy ipv4_only;
 *  - route-правило «трафик до самой ноды — напрямую» (иначе петля handshake),
 *    hijack-dns, и блок «голого» IPv6.
 *
 * Целевая версия sing-box: 1.11.x (поле tun `address`, новый формат dns/route).
 *
 * ВАЖНО (безопасность): сериализованный конфиг содержит uuid и ключи Reality —
 * НИКОГДА не логируем результат [build]/[buildString].
 */
object SingBoxConfigBuilder {

    /** Имя и адресация tun. /30 в редко используемом диапазоне, чтобы не пересечься с LAN. */
    const val TUN_INTERFACE = "sapn-tun"
    const val TUN_ADDRESS = "172.18.0.1/30"
    const val TUN_MTU = 1500

    private val json = Json { prettyPrint = false }

    fun build(config: VlessConfig): JsonObject = buildJsonObject {
        put("log", buildJsonObject {
            put("level", "warn")
            put("timestamp", true)
        })

        // ---- DNS: дефолтный резолвер — через туннель (анти-leak). ----
        putJsonObject("dns") {
            putJsonArray("servers") {
                addJsonObject {
                    put("tag", "dns-remote")
                    put("address", "1.1.1.1")
                    put("detour", "vless-out")
                }
                addJsonObject {
                    put("tag", "dns-direct")
                    put("address", "1.1.1.1")
                    put("detour", "direct")
                }
            }
            put("final", "dns-remote")
            put("strategy", "ipv4_only") // не выдаём AAAA: защита от IPv6 DNS-leak
        }

        // ---- Inbounds: единственный tun. FD отдаёт PlatformInterface. ----
        putJsonArray("inbounds") {
            addJsonObject {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", TUN_INTERFACE)
                putJsonArray("address") { add(TUN_ADDRESS) } // IPv4-only на tun (анти-leak)
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "system")
                put("mtu", TUN_MTU)
                put("sniff", true) // перехват DNS для hijack-dns
            }
        }

        // ---- Outbounds: vless reality + direct + block. ----
        putJsonArray("outbounds") {
            add(buildVlessOutbound(config))
            addJsonObject {
                put("type", "direct")
                put("tag", "direct")
            }
            addJsonObject {
                put("type", "block")
                put("tag", "block")
            }
        }

        // ---- Route. ----
        putJsonObject("route") {
            putJsonArray("rules") {
                // DNS-запросы перехватываем, чтобы ОС не резолвила мимо туннеля.
                addJsonObject {
                    put("protocol", "dns")
                    put("action", "hijack-dns")
                }
                // Трафик до самой ноды — напрямую, иначе handshake уйдёт в петлю.
                addJsonObject {
                    putJsonArray("domain") { add(config.host) }
                    put("outbound", "direct")
                }
                // Анти-leak: «голый» IPv6 не выпускаем в обход туннеля.
                addJsonObject {
                    put("ip_version", 6)
                    put("outbound", "block")
                }
            }
            put("final", "vless-out")
            put("auto_detect_interface", true)
        }
    }

    /** То же, но в виде строки JSON (передаётся в libbox). НЕ логировать. */
    fun buildString(config: VlessConfig): String =
        json.encodeToString(JsonObject.serializer(), build(config))

    private fun buildVlessOutbound(config: VlessConfig): JsonObject = buildJsonObject {
        put("type", "vless")
        put("tag", "vless-out")
        put("server", config.host)
        put("server_port", config.port)
        put("uuid", config.uuid)
        if (config.flow.isNotBlank()) put("flow", config.flow)
        putJsonObject("tls") {
            put("enabled", true)
            put("server_name", config.sni)
            putJsonObject("utls") {
                put("enabled", true)
                put("fingerprint", config.fingerprint.ifBlank { "chrome" })
            }
            putJsonObject("reality") {
                put("enabled", true)
                put("public_key", config.publicKey)
                put("short_id", config.shortId)
            }
        }
    }
}
