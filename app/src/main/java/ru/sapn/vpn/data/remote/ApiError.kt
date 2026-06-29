package ru.sapn.vpn.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Доменно-нейтральная ошибка API с HTTP-кодом и (опционально) машинным кодом из
 * тела ответа (`code`, напр. "device_limit"). Позволяет UI различать состояния
 * (401 — не авторизован, 403 device_limit — лимит устройств и т.п.) без знания
 * про Retrofit.
 */
class ApiException(
    val code: Int,                   // HTTP-статус
    message: String,
    cause: Throwable? = null,
    val backendCode: String? = null, // поле "code" из тела ошибки бэкенда
) : Exception(message, cause)

@Serializable
private data class ApiErrorBody(val error: String? = null, val code: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true }

/** Преобразует любую ошибку в [ApiException] (HTTP-код + код тела, если это [HttpException]). */
fun Throwable.toApiException(): ApiException = when (this) {
    is ApiException -> this
    is HttpException -> {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val parsed = raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { errorJson.decodeFromString<ApiErrorBody>(it) }.getOrNull() }
        ApiException(
            code = code(),
            message = parsed?.error ?: message ?: "HTTP ${code()}",
            cause = this,
            backendCode = parsed?.code,
        )
    }
    else -> ApiException(0, message ?: "network error", this)
}
