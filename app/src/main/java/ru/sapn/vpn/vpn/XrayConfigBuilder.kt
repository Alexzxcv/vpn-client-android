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
 * Сборка JSON-конфига Xray-core из [VlessConfig].
 *
 * ПРИМЕЧАНИЕ: активный движок — sing-box ([SingBoxConfigBuilder] + libbox), у него
 * нативный tun-inbound и tun2socks не нужен. Этот билдер оставлен как референс
 * формата Xray (SOCKS inbound + VLESS Reality outbound) на случай альтернативного
 * пути libXray + tun2socks. В текущей сборке [XrayCoreVpnEngine] его НЕ использует.
 *
 * Содержит:
 *  - VLESS Reality outbound (адрес/порт/uuid/flow + realitySettings);
 *  - SOCKS inbound на 127.0.0.1, через который tun2socks заворачивает трафик из tun;
 *  - DNS и базовый routing.
 *
 * ВАЖНО (безопасность): сериализованный конфиг содержит uuid и ключи Reality —
 * НИКОГДА не логируем результат [build]/[buildString].
 */
object XrayConfigBuilder {

    const val SOCKS_HOST = "127.0.0.1"
    const val SOCKS_PORT = 10808

    private val json = Json { prettyPrint = false }

    fun build(config: VlessConfig): JsonObject = buildJsonObject {
        put("log", buildJsonObject { put("loglevel", "warning") })

        putJsonArray("inbounds") {
            addJsonObject {
                put("tag", "socks-in")
                put("listen", SOCKS_HOST)
                put("port", SOCKS_PORT)
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("udp", true)
                    put("auth", "noauth")
                }
                putJsonObject("sniffing") {
                    put("enabled", true)
                    putJsonArray("destOverride") {
                        add("http")
                        add("tls")
                        add("quic")
                    }
                }
            }
        }

        putJsonArray("outbounds") {
            add(buildVlessOutbound(config))
            // Прямой выход (для split-tunnel/исключений по маршрутизации).
            addJsonObject {
                put("tag", "direct")
                put("protocol", "freedom")
            }
            // Блокирующий outbound.
            addJsonObject {
                put("tag", "block")
                put("protocol", "blackhole")
            }
        }

        putJsonObject("dns") {
            putJsonArray("servers") {
                add("1.1.1.1")
                add("8.8.8.8")
            }
        }
    }

    /** То же, но в виде строки JSON (передаётся в движок). НЕ логировать. */
    fun buildString(config: VlessConfig): String = json.encodeToString(JsonObject.serializer(), build(config))

    private fun buildVlessOutbound(config: VlessConfig): JsonObject = buildJsonObject {
        put("tag", "proxy")
        put("protocol", "vless")
        putJsonObject("settings") {
            putJsonArray("vnext") {
                addJsonObject {
                    put("address", config.host)
                    put("port", config.port)
                    putJsonArray("users") {
                        addJsonObject {
                            put("id", config.uuid)
                            put("encryption", "none")
                            if (config.flow.isNotBlank()) put("flow", config.flow)
                        }
                    }
                }
            }
        }
        putJsonObject("streamSettings") {
            put("network", "tcp")
            put("security", config.security.ifBlank { "reality" })
            putJsonObject("realitySettings") {
                put("publicKey", config.publicKey)
                put("shortId", config.shortId)
                put("serverName", config.sni)
                put("fingerprint", config.fingerprint.ifBlank { "chrome" })
                // SpiderX обычно пустой для клиента.
                put("spiderX", "")
            }
        }
    }
}
