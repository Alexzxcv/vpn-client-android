package ru.sapn.vpn.domain.model

/** Профиль пользователя (GET /me). */
data class User(
    val id: String,
    val email: String,
    val isAdmin: Boolean,
)

/** Подписка пользователя (GET /subscription). */
data class Subscription(
    val plan: String,
    val active: Boolean,
    val expiresAt: String?,
)

/** Локация/нода для подключения (GET /vpn/locations). */
data class Location(
    val id: String,
    val name: String,
    val location: String,
)

/**
 * Параметры VLESS Reality для поднятия туннеля (POST /vpn/config).
 * Это то, что VpnEngine превращает в конфиг Xray/sing-box.
 * Чувствительные поля (uuid, publicKey, shortId) НЕ логируем.
 */
data class VlessConfig(
    val host: String,
    val port: Int,
    val uuid: String,
    val security: String,   // "reality"
    val flow: String,
    val publicKey: String,
    val shortId: String,
    val sni: String,
    val fingerprint: String,
)
