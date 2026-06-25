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
import ru.sapn.vpn.domain.model.VpnSettings

/**
 * Сборка JSON-конфига sing-box из [VlessConfig] (+ [VpnSettings]).
 *
 * sing-box имеет НАТИВНЫЙ tun-inbound: дескриптор tun libbox получает через
 * PlatformInterface ([ru.sapn.vpn.vpn.libbox.AndroidPlatformInterface.openTun]).
 * Стек — **gvisor** (надёжный userspace-netstack на Android; system-стек требует
 * привилегий и на Android не заводится → «нет трафика»).
 *
 * Структура зеркалит Windows-клиент:
 *  - tun inbound (адрес /30, auto_route, stack=gvisor, sniff DNS);
 *  - vless + reality outbound + direct + block;
 *  - DNS через туннель (анти-leak), strategy ipv4_only;
 *  - route: hijack-dns, «нода — напрямую» (ip_cidr, иначе петля handshake),
 *    RU-direct/direct-list (по настройкам), блок «голого» IPv6.
 *
 * ВАЖНО (безопасность): конфиг содержит uuid и ключи Reality — НИКОГДА не логируем.
 */
object SingBoxConfigBuilder {

    const val TUN_INTERFACE = "sapn-tun"
    const val TUN_ADDRESS = "172.18.0.1/30"
    const val TUN_MTU = 1500

    private val json = Json { prettyPrint = false }
    private val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}(/\d{1,2})?$""")

    fun build(config: VlessConfig, settings: VpnSettings = VpnSettings()): JsonObject = buildJsonObject {
        put("log", buildJsonObject {
            put("level", "info")
            put("timestamp", true)
        })

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
            put("strategy", "ipv4_only")
        }

        putJsonArray("inbounds") {
            addJsonObject {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", TUN_INTERFACE)
                putJsonArray("address") { add(TUN_ADDRESS) }
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "gvisor")
                put("mtu", TUN_MTU)
                put("sniff", true)
            }
        }

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

        putJsonObject("route") {
            putJsonArray("rules") {
                // DNS-запросы перехватываем.
                addJsonObject {
                    put("protocol", "dns")
                    put("action", "hijack-dns")
                }
                // Трафик до самой ноды — напрямую (нода обычно IP → ip_cidr).
                addJsonObject {
                    if (ipv4Regex.matches(config.host)) {
                        putJsonArray("ip_cidr") { add(if (config.host.contains("/")) config.host else "${config.host}/32") }
                    } else {
                        putJsonArray("domain") { add(config.host) }
                    }
                    put("outbound", "direct")
                }
                // RU-direct: .ru/.су/.рф напрямую.
                if (settings.russiaDirect) {
                    addJsonObject {
                        putJsonArray("domain_suffix") {
                            add(".ru"); add(".su"); add(".xn--p1ai")
                        }
                        put("outbound", "direct")
                    }
                }
                // Ручной direct-list: домены и IP/CIDR.
                val directDomains = settings.directList.filter { !ipv4Regex.matches(it) }
                val directIps = settings.directList.filter { ipv4Regex.matches(it) }
                if (directDomains.isNotEmpty()) {
                    addJsonObject {
                        putJsonArray("domain_suffix") { directDomains.forEach { add(it) } }
                        put("outbound", "direct")
                    }
                }
                if (directIps.isNotEmpty()) {
                    addJsonObject {
                        putJsonArray("ip_cidr") {
                            directIps.forEach { add(if (it.contains("/")) it else "$it/32") }
                        }
                        put("outbound", "direct")
                    }
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

    /** То же, но строкой JSON (передаётся в libbox). НЕ логировать. */
    fun buildString(config: VlessConfig, settings: VpnSettings = VpnSettings()): String =
        json.encodeToString(JsonObject.serializer(), build(config, settings))

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
