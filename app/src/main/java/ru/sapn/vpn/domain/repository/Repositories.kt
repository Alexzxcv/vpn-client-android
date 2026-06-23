package ru.sapn.vpn.domain.repository

import kotlinx.coroutines.flow.Flow
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

/** Подписка, локации и выдача VPN-конфига. */
interface VpnRepository {
    suspend fun subscription(): Result<Subscription>
    suspend fun locations(): Result<List<Location>>

    /** Привязка устройства (обязательна перед запросом конфига). */
    suspend fun registerDevice(): Result<Unit>

    /** Запрос VLESS Reality конфига для конкретной локации (или дефолтной). */
    suspend fun fetchConfig(serverId: String?): Result<VlessConfig>
}
