package ru.sapn.vpn.data.repository

import android.content.Context
import ru.sapn.vpn.data.local.DeviceIdProvider
import ru.sapn.vpn.data.remote.ConfigRequest
import ru.sapn.vpn.data.remote.DeviceRequest
import ru.sapn.vpn.data.remote.VpnApi
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.repository.VpnRepository

class VpnRepositoryImpl(
    private val api: VpnApi,
    private val appContext: Context,
) : VpnRepository {

    private val deviceId: String get() = DeviceIdProvider.deviceId(appContext)

    override suspend fun subscription(): Result<Subscription> = runCatching {
        api.subscription().toDomain()
    }

    override suspend fun locations(): Result<List<Location>> = runCatching {
        api.locations().map { it.toDomain() }
    }

    override suspend fun registerDevice(): Result<Unit> = runCatching {
        api.registerDevice(
            DeviceRequest(
                deviceId = deviceId,
                name = DeviceIdProvider.deviceName(),
                platform = "android",
            )
        )
    }

    override suspend fun fetchConfig(serverId: String?): Result<VlessConfig> = runCatching {
        api.config(ConfigRequest(deviceId = deviceId, serverId = serverId)).toDomain()
    }
}
