package ru.sapn.vpn.data.repository

import kotlinx.coroutines.flow.Flow
import ru.sapn.vpn.data.local.TokenStore
import ru.sapn.vpn.data.remote.LoginRequest
import ru.sapn.vpn.data.remote.VpnApi
import ru.sapn.vpn.domain.model.User
import ru.sapn.vpn.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val api: VpnApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    override suspend fun login(login: String, password: String): Result<Unit> = runCatching {
        val tokens = api.login(LoginRequest(login = login, password = password))
        tokenStore.save(tokens.accessToken, tokens.refreshToken)
    }

    override suspend fun logout() {
        tokenStore.clear()
    }

    override suspend fun currentUser(): Result<User> = runCatching {
        api.me().toDomain()
    }
}
