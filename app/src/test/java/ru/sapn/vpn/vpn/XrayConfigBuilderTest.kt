package ru.sapn.vpn.vpn

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sapn.vpn.domain.model.VlessConfig

class XrayConfigBuilderTest {

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
    fun `builds vless reality outbound with expected fields`() {
        val root = XrayConfigBuilder.build(config)

        val outbound = root["outbounds"]!!.jsonArray
            .first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }
            .jsonObject

        assertEquals("vless", outbound["protocol"]!!.jsonPrimitive.content)

        val vnext = outbound["settings"]!!.jsonObject["vnext"]!!.jsonArray.first().jsonObject
        assertEquals("node1.example.com", vnext["address"]!!.jsonPrimitive.content)
        assertEquals(443, vnext["port"]!!.jsonPrimitive.content.toInt())

        val user = vnext["users"]!!.jsonArray.first().jsonObject
        assertEquals(config.uuid, user["id"]!!.jsonPrimitive.content)
        assertEquals("xtls-rprx-vision", user["flow"]!!.jsonPrimitive.content)

        val reality = outbound["streamSettings"]!!.jsonObject["realitySettings"]!!.jsonObject
        assertEquals("BASE64PUBKEY", reality["publicKey"]!!.jsonPrimitive.content)
        assertEquals("0123abcd", reality["shortId"]!!.jsonPrimitive.content)
        assertEquals("www.microsoft.com", reality["serverName"]!!.jsonPrimitive.content)
        assertEquals("chrome", reality["fingerprint"]!!.jsonPrimitive.content)
    }

    @Test
    fun `builds socks inbound for tun2socks bridge`() {
        val inbound = XrayConfigBuilder.build(config)["inbounds"]!!.jsonArray.first().jsonObject
        assertEquals("socks", inbound["protocol"]!!.jsonPrimitive.content)
        assertEquals(XrayConfigBuilder.SOCKS_HOST, inbound["listen"]!!.jsonPrimitive.content)
        assertEquals(XrayConfigBuilder.SOCKS_PORT, inbound["port"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `omits flow when blank`() {
        val noFlow = config.copy(flow = "")
        val user = XrayConfigBuilder.build(noFlow)["outbounds"]!!.jsonArray
            .first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }
            .jsonObject["settings"]!!.jsonObject["vnext"]!!.jsonArray.first()
            .jsonObject["users"]!!.jsonArray.first().jsonObject
        assertTrue(!user.containsKey("flow"))
    }
}
