package ru.sapn.vpn.domain.model

/** Пользовательский (свой) VLESS-сервер, добавленный вручную по ссылке. */
data class CustomServer(
    val id: String,
    val name: String,
    val config: VlessConfig,
)
