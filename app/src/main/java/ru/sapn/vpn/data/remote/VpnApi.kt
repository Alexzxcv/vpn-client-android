package ru.sapn.vpn.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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

    @GET("me")
    suspend fun me(): MeResponse

    @GET("subscription")
    suspend fun subscription(): SubscriptionResponse

    @GET("vpn/locations")
    suspend fun locations(): List<LocationResponse>

    @POST("devices")
    suspend fun registerDevice(@Body body: DeviceRequest)

    @POST("vpn/config")
    suspend fun config(@Body body: ConfigRequest): ConfigResponse
}
