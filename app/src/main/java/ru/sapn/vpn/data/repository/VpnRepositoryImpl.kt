package ru.sapn.vpn.data.repository

import ru.sapn.vpn.data.local.DeviceIdentity
import ru.sapn.vpn.data.local.DeviceIdProvider
import ru.sapn.vpn.data.local.TokenStore
import ru.sapn.vpn.data.remote.ConfigRequest
import ru.sapn.vpn.data.remote.DeviceRequest
import ru.sapn.vpn.data.remote.VpnApi
import ru.sapn.vpn.data.remote.toApiException
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.repository.VpnRepository

class VpnRepositoryImpl(
    private val api: VpnApi,
    private val identity: DeviceIdentity,
    private val tokenStore: TokenStore,
) : VpnRepository {

    override suspend fun subscription(): Result<Subscription> = runCatching {
        api.subscription().toDomain()
    }

    override suspend fun locations(): Result<List<Location>> = runCatching {
        api.locations().map { it.toDomain() }
    }

    override suspend fun devicesUsed(): Result<Int> = runCatching {
        api.devices().size
    }

    override suspend fun registerDevice(): Result<Unit> = runCatching {
        identity.ensureKeyPair()
        val resp = api.registerDevice(
            DeviceRequest(
                publicKey = identity.publicKeyBase64(),
                name = DeviceIdProvider.deviceName(),
                platform = "android",
            )
        )
        tokenStore.saveDeviceId(resp.deviceId)
    }.recoverCatching { throw it.toApiException() }

    override suspend fun fetchConfig(serverId: String?): Result<VlessConfig> = runCatching {
        // Гарантируем, что device_id есть (регистрируемся при необходимости).
        val deviceId = tokenStore.deviceId() ?: run {
            registerDevice().getOrThrow()
            tokenStore.deviceId() ?: error("device_id is missing after registration")
        }

        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val signature = identity.sign("$deviceId.$timestamp")

        api.config(
            deviceId = deviceId,
            timestamp = timestamp,
            signature = signature,
            body = ConfigRequest(serverId = serverId),
        ).toDomain()
    }.recoverCatching { throw it.toApiException() }
}
