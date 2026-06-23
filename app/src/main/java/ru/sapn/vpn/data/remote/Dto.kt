package ru.sapn.vpn.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Auth ----

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class TokensResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

// ---- User / subscription ----

@Serializable
data class MeResponse(
    val id: String,
    val email: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

@Serializable
data class SubscriptionResponse(
    val plan: String,
    val active: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
)

// ---- Locations ----

@Serializable
data class LocationResponse(
    val id: String,
    val name: String,
    val location: String,
)

// ---- Devices ----

@Serializable
data class DeviceRequest(
    @SerialName("device_id") val deviceId: String,
    val name: String,
    val platform: String = "android",
)

// ---- VPN config ----

@Serializable
data class ConfigRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("server_id") val serverId: String? = null,
)

@Serializable
data class ConfigResponse(
    val server: String,
    val port: Int,
    val uuid: String,
    val security: String,
    val flow: String = "",
    @SerialName("public_key") val publicKey: String,
    @SerialName("short_id") val shortId: String,
    val sni: String,
    val fingerprint: String,
)
