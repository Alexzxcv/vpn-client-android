package ru.sapn.vpn.data.repository

import ru.sapn.vpn.data.remote.ChangePasswordRequest
import ru.sapn.vpn.data.remote.UpdateMeRequest
import ru.sapn.vpn.data.remote.VpnApi
import ru.sapn.vpn.data.remote.toApiException
import ru.sapn.vpn.domain.model.Device
import ru.sapn.vpn.domain.model.User
import ru.sapn.vpn.domain.repository.AccountRepository

class AccountRepositoryImpl(
    private val api: VpnApi,
) : AccountRepository {

    override suspend fun me(): Result<User> = runCatching {
        api.me().toDomain()
    }.recoverCatching { throw it.toApiException() }

    override suspend fun updateProfile(email: String?, username: String?): Result<User> =
        runCatching {
            api.updateMe(UpdateMeRequest(email = email, username = username)).toDomain()
        }.recoverCatching { throw it.toApiException() }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        api.changePassword(
            ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword)
        )
    }.recoverCatching { throw it.toApiException() }

    override suspend fun devices(): Result<List<Device>> = runCatching {
        api.devices().map { it.toDomain() }
    }.recoverCatching { throw it.toApiException() }

    override suspend fun revokeDevice(deviceId: String): Result<Unit> = runCatching {
        api.deleteDevice(deviceId)
    }.recoverCatching { throw it.toApiException() }
}
