package ru.sapn.vpn.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sapn.vpn.domain.model.VlessConfig

/**
 * Тесты на сборку ссылки для «Поделиться». Обратный разбор (`parse`) здесь не
 * проверяем: он ходит в android.net.Uri, которого в JVM-тестах нет.
 */
class VlessLinkParserTest {

    private val config = VlessConfig(
        host = "node1.example.com",
        port = 443,
        uuid = "11111111-2222-3333-4444-555555555555",
        security = "reality",
        flow = "xtls-rprx-vision",
        publicKey = "BASE64PUBKEY",
        shortId = "0123abcd",
        sni = "www.microsoft.com",
        fingerprint = "chrome",
        expiresAt = null,
    )

    @Test
    fun `builds a full reality link`() {
        assertEquals(
            "vless://11111111-2222-3333-4444-555555555555@node1.example.com:443" +
                "?type=tcp&encryption=none&security=reality&sni=www.microsoft.com" +
                "&fp=chrome&pbk=BASE64PUBKEY&sid=0123abcd&flow=xtls-rprx-vision" +
                "#My%20node",
            VlessLinkParser.build("My node", config),
        )
    }

    @Test
    fun `omits empty optional params`() {
        val bare = config.copy(flow = "", publicKey = "", shortId = "", fingerprint = "")
        val link = VlessLinkParser.build("", bare)
        assertTrue(link, link.endsWith("?type=tcp&encryption=none&security=reality&sni=www.microsoft.com"))
    }

    @Test
    fun `wraps ipv6 host in brackets so the port survives`() {
        val v6 = config.copy(host = "2001:db8::1")
        assertTrue(VlessLinkParser.build("v6", v6).contains("@[2001:db8::1]:443?"))
    }

    @Test
    fun `percent-encodes the name instead of using plus`() {
        val link = VlessLinkParser.build("дом & дача", config)
        assertTrue(link, link.endsWith("#%D0%B4%D0%BE%D0%BC%20%26%20%D0%B4%D0%B0%D1%87%D0%B0"))
    }
}
