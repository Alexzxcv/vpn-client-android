package ru.sapn.vpn.data.repository

import ru.sapn.vpn.data.remote.ConfigResponse
import ru.sapn.vpn.data.remote.DeviceResponse
import ru.sapn.vpn.data.remote.LocationResponse
import ru.sapn.vpn.data.remote.MeResponse
import ru.sapn.vpn.data.remote.SubscriptionResponse
import ru.sapn.vpn.domain.model.Device
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.model.User
import ru.sapn.vpn.domain.model.VlessConfig

fun MeResponse.toDomain() =
    User(id = id, email = email, username = username, isAdmin = isAdmin)

fun DeviceResponse.toDomain() = Device(
    deviceId = deviceId,
    name = name,
    platform = platform,
    blocked = blocked,
    lastSeenAt = lastSeenAt,
    createdAt = createdAt,
)

fun SubscriptionResponse.toDomain() =
    Subscription(plan = plan, active = active, expiresAt = expiresAt)

fun LocationResponse.toDomain() =
    Location(id = id, name = name, location = location)

fun ConfigResponse.toDomain() = VlessConfig(
    host = server,
    port = port,
    uuid = uuid,
    security = security,
    flow = flow,
    publicKey = publicKey,
    shortId = shortId,
    sni = sni,
    fingerprint = fingerprint,
    expiresAt = expiresAt,
)
