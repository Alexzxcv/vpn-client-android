package ru.sapn.vpn.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Контракт control-plane (база — BuildConfig.API_BASE_URL).
 * Авторизация — Bearer JWT, добавляется [AuthInterceptor] автоматически,
 * кроме login/refresh (помечены [Unauthenticated]-семантикой по пути).
 */
interface VpnApi {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokensResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokensResponse

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @GET("me")
    suspend fun me(): MeResponse

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateMeRequest): MeResponse

    @GET("subscription")
    suspend fun subscription(): SubscriptionResponse

    @GET("vpn/locations")
    suspend fun locations(): List<LocationResponse>

    // ---- Devices ----

    @GET("devices")
    suspend fun devices(): List<DeviceResponse>

    @POST("devices")
    suspend fun registerDevice(@Body body: DeviceRequest): DeviceResponse

    @DELETE("devices/{device_id}")
    suspend fun deleteDevice(@Path("device_id") deviceId: String)

    /**
     * Запрос конфига. Помимо JWT требует подпись устройства в заголовках:
     *  - X-Device-Id        — device_id, выданный при регистрации;
     *  - X-Device-Timestamp — unix-время (сек);
     *  - X-Device-Signature — base64(Ed25519_sign("<device_id>.<timestamp>")).
     */
    @POST("vpn/config")
    suspend fun config(
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Device-Timestamp") timestamp: String,
        @Header("X-Device-Signature") signature: String,
        @Body body: ConfigRequest,
    ): ConfigResponse
}
