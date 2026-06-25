package ru.sapn.vpn.domain.model

/** Профиль пользователя (GET /me). */
data class User(
    val id: String,
    val email: String,
    val username: String?,
    val isAdmin: Boolean,
)

/** Зарегистрированное устройство (GET /devices). */
data class Device(
    val deviceId: String,
    val name: String,
    val platform: String,
    val blocked: Boolean,
    val lastSeenAt: String?,
    val createdAt: String?,
)

/** Подписка пользователя (GET /subscription). */
data class Subscription(
    val plan: String,
    val active: Boolean,
    val deviceLimit: Int,
    val trafficLimitBytes: Long,
    val trafficUsedBytes: Long,
    val expiresAt: String?,
    /** Бесплатный суточный лимит (для free-юзеров): лимит и использовано сегодня. */
    val freeDailyLimitBytes: Long = 0,
    val freeDailyUsedBytes: Long = 0,
)

/** Локация/нода для подключения (GET /vpn/locations). */
data class Location(
    val id: String,
    val name: String,
    val location: String,
    /** Пинг ноды (мс), 0 — неизвестно. */
    val pingMs: Int = 0,
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
    /** ISO-8601 момент истечения выданного конфига (для авто-рефреша). */
    val expiresAt: String? = null,
)
