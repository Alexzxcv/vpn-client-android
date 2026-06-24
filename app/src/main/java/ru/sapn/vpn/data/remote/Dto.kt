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
    val username: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

/** PATCH /me — частичное обновление профиля (только переданные поля). */
@Serializable
data class UpdateMeRequest(
    val email: String? = null,
    val username: String? = null,
)

/** POST /auth/change-password. */
@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
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

/**
 * POST /devices — регистрация устройства по публичному ключу Ed25519.
 * Идемпотентно по public_key. Возвращает [DeviceResponse] с device_id.
 */
@Serializable
data class DeviceRequest(
    @SerialName("public_key") val publicKey: String, // base64 std, 32 байта
    val name: String,
    val platform: String = "android",
    val mac: String? = null,
)

/** Элемент GET /devices и ответ POST /devices. */
@Serializable
data class DeviceResponse(
    @SerialName("device_id") val deviceId: String,
    val name: String = "",
    val platform: String = "",
    val blocked: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---- VPN config ----

@Serializable
data class ConfigRequest(
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
    @SerialName("expires_at") val expiresAt: String? = null,
)
