package ru.sapn.vpn.vpn

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sapn.vpn.domain.model.VlessConfig

class SingBoxConfigBuilderTest {

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
        val out = SingBoxConfigBuilder.build(config)["outbounds"]!!.jsonArray
            .first { it.jsonObject["tag"]?.jsonPrimitive?.content == "vless-out" }
            .jsonObject

        assertEquals("vless", out["type"]!!.jsonPrimitive.content)
        assertEquals("node1.example.com", out["server"]!!.jsonPrimitive.content)
        assertEquals(443, out["server_port"]!!.jsonPrimitive.content.toInt())
        assertEquals(config.uuid, out["uuid"]!!.jsonPrimitive.content)
        assertEquals("xtls-rprx-vision", out["flow"]!!.jsonPrimitive.content)

        val tls = out["tls"]!!.jsonObject
        assertEquals("www.microsoft.com", tls["server_name"]!!.jsonPrimitive.content)
        assertEquals("chrome", tls["utls"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content)

        val reality = tls["reality"]!!.jsonObject
        assertEquals("BASE64PUBKEY", reality["public_key"]!!.jsonPrimitive.content)
        assertEquals("0123abcd", reality["short_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `builds native tun inbound`() {
        val inbound = SingBoxConfigBuilder.build(config)["inbounds"]!!.jsonArray.first().jsonObject
        assertEquals("tun", inbound["type"]!!.jsonPrimitive.content)
        assertEquals(SingBoxConfigBuilder.TUN_INTERFACE, inbound["interface_name"]!!.jsonPrimitive.content)
        assertEquals(SingBoxConfigBuilder.TUN_MTU, inbound["mtu"]!!.jsonPrimitive.content.toInt())
        assertEquals(SingBoxConfigBuilder.TUN_ADDRESS, inbound["address"]!!.jsonArray.first().jsonPrimitive.content)
        assertTrue(inbound["auto_route"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `routes node host directly to avoid handshake loop`() {
        val rules = SingBoxConfigBuilder.build(config)["route"]!!.jsonObject["rules"]!!.jsonArray
        val directRule = rules.map { it.jsonObject }
            .first { r -> r["domain"]?.jsonArray?.any { it.jsonPrimitive.content == config.host } == true }
        assertEquals("direct", directRule["outbound"]!!.jsonPrimitive.content)
    }

    @Test
    fun `omits flow when blank`() {
        val out = SingBoxConfigBuilder.build(config.copy(flow = ""))["outbounds"]!!.jsonArray
            .first { it.jsonObject["tag"]?.jsonPrimitive?.content == "vless-out" }
            .jsonObject
        assertTrue(!out.containsKey("flow"))
    }
}
