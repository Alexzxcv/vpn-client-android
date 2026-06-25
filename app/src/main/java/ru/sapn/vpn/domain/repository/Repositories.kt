package ru.sapn.vpn.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.sapn.vpn.domain.model.Device
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.model.User
import ru.sapn.vpn.domain.model.VlessConfig

/** Аутентификация и сессия. */
interface AuthRepository {
    /** Поток: залогинен ли пользователь (есть ли валидный access-токен в хранилище). */
    val isLoggedIn: Flow<Boolean>

    suspend fun login(login: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun currentUser(): Result<User>
}

/** Профиль и устройства (экран «Аккаунт»). */
interface AccountRepository {
    suspend fun me(): Result<User>

    /** PATCH /me — обновление email и/или username (null = не менять). */
    suspend fun updateProfile(email: String?, username: String?): Result<User>

    /** POST /auth/change-password. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /** GET /devices — список устройств пользователя. */
    suspend fun devices(): Result<List<Device>>

    /** DELETE /devices/{device_id} — отзыв устройства. */
    suspend fun revokeDevice(deviceId: String): Result<Unit>
}

/** Подписка, локации и выдача VPN-конфига. */
interface VpnRepository {
    suspend fun subscription(): Result<Subscription>
    suspend fun locations(): Result<List<Location>>

    /** Кол-во привязанных устройств (для индикатора «использовано/лимит»). */
    suspend fun devicesUsed(): Result<Int>

    /**
     * Регистрация устройства по публичному ключу (идемпотентно по public_key).
     * Сохраняет выданный device_id локально. Обязательна перед запросом конфига.
     */
    suspend fun registerDevice(): Result<Unit>

    /**
     * Запрос VLESS Reality конфига для конкретной локации (или дефолтной),
     * с подписью устройства (X-Device-* заголовки).
     */
    suspend fun fetchConfig(serverId: String?): Result<VlessConfig>
}
