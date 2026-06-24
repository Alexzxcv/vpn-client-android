package ru.sapn.vpn.data.remote

import retrofit2.HttpException

/**
 * Доменно-нейтральная ошибка API с HTTP-кодом.
 * Позволяет UI различать состояния (401 — не авторизован, 409 — конфликт и т.п.)
 * без знания про Retrofit.
 */
class ApiException(
    val code: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Преобразует любую ошибку в [ApiException] (HTTP-код, если это [HttpException]). */
fun Throwable.toApiException(): ApiException = when (this) {
    is ApiException -> this
    is HttpException -> ApiException(code(), message ?: "HTTP ${code()}", this)
    else -> ApiException(0, message ?: "network error", this)
}
