package ru.sapn.vpn.di

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.sapn.vpn.BuildConfig
import ru.sapn.vpn.data.local.TokenStore
import ru.sapn.vpn.data.remote.AuthInterceptor
import ru.sapn.vpn.data.remote.VpnApi
import ru.sapn.vpn.data.repository.AuthRepositoryImpl
import ru.sapn.vpn.data.repository.VpnRepositoryImpl
import ru.sapn.vpn.domain.repository.AuthRepository
import ru.sapn.vpn.domain.repository.VpnRepository

/**
 * Простой Service Locator вместо тяжёлого DI-фреймворка — достаточно для скелета.
 * Позже легко заменить на Hilt/Koin: интерфейсы репозиториев уже выделены.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val tokenStore = TokenStore(appContext)

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore, json))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        // BODY может содержать токены — в DEBUG ограничиваемся HEADERS.
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .build()

    private val api: VpnApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(VpnApi::class.java)

    val authRepository: AuthRepository = AuthRepositoryImpl(api, tokenStore)
    val vpnRepository: VpnRepository = VpnRepositoryImpl(api, appContext)
}
