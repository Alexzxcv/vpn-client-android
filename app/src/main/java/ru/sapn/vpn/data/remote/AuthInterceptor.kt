package ru.sapn.vpn.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.sapn.vpn.data.local.TokenStore

/**
 * Подставляет `Authorization: Bearer <access>` ко всем запросам, кроме auth/*.
 * При 401 пытается один раз обновить токен через /auth/refresh и повторить запрос.
 * Если refresh не удался — чистит токены (пользователя выкинет на экран логина).
 *
 * Refresh выполняется отдельным "голым" OkHttp-вызовом, чтобы не уйти в рекурсию
 * интерсептора. URL refresh берётся относительно исходного запроса.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val json: Json,
) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // auth/login и auth/refresh идут без токена.
        if (isAuthPath(original)) {
            return chain.proceed(original)
        }

        val access = runBlocking { tokenStore.accessToken() }
        val response = chain.proceed(original.withBearer(access))

        if (response.code != 401) return response

        // 401 — пробуем обновить токен (синхронно, под локом).
        val newAccess = synchronized(refreshLock) {
            runBlocking { refreshTokens(chain, original) }
        } ?: run {
            runBlocking { tokenStore.clear() }
            return response
        }

        response.close()
        return chain.proceed(original.withBearer(newAccess))
    }

    private suspend fun refreshTokens(chain: Interceptor.Chain, original: Request): String? {
        val refresh = tokenStore.refreshToken() ?: return null

        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
            .toRequestBody("application/json".toMediaType())

        // refresh-эндпоинт относительно базы исходного запроса.
        val refreshUrl = original.url.newBuilder()
            .encodedPath(refreshPath(original))
            .build()

        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post(body)
            .build()

        chain.proceed(refreshRequest).use { resp ->
            if (!resp.isSuccessful) return null
            val raw = resp.body?.string() ?: return null
            val tokens = runCatching {
                json.decodeFromString(TokensResponse.serializer(), raw)
            }.getOrNull() ?: return null
            tokenStore.save(tokens.accessToken, tokens.refreshToken)
            return tokens.accessToken
        }
    }

    private fun Request.withBearer(token: String?): Request =
        if (token.isNullOrBlank()) this
        else newBuilder().header("Authorization", "Bearer $token").build()

    private fun isAuthPath(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/auth/login") || path.endsWith("/auth/refresh")
    }

    /** Строит путь к refresh, сохраняя возможный префикс базы (/api). */
    private fun refreshPath(original: Request): String {
        val segments = original.url.encodedPathSegments
        // Отрезаем последние сегменты текущего запроса до общего префикса базы
        // не получится надёжно — поэтому берём базовый префикс до первого
        // "api" сегмента, либо корень.
        val apiIndex = segments.indexOf("api")
        val prefix = if (apiIndex >= 0) segments.take(apiIndex + 1) else emptyList()
        return "/" + (prefix + listOf("auth", "refresh")).joinToString("/")
    }
}
