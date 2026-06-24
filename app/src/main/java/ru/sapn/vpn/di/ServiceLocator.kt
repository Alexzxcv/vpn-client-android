package ru.sapn.vpn.di

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.sapn.vpn.BuildConfig
import ru.sapn.vpn.data.local.DeviceIdentity
import ru.sapn.vpn.data.local.TokenStore
import ru.sapn.vpn.data.remote.AuthInterceptor
import ru.sapn.vpn.data.remote.VpnApi
import ru.sapn.vpn.data.repository.AccountRepositoryImpl
import ru.sapn.vpn.data.repository.AuthRepositoryImpl
import ru.sapn.vpn.data.repository.UpdateRepositoryImpl
import ru.sapn.vpn.data.repository.VpnRepositoryImpl
import ru.sapn.vpn.domain.repository.AccountRepository
import ru.sapn.vpn.domain.repository.AuthRepository
import ru.sapn.vpn.domain.repository.VpnRepository
import ru.sapn.vpn.domain.update.UpdateRepository

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
    private val deviceIdentity = DeviceIdentity(appContext)

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

    // Отдельный клиент для GitHub: другой хост, без нашего Bearer-токена.
    private val plainHttp: OkHttpClient = OkHttpClient.Builder().build()

    val authRepository: AuthRepository = AuthRepositoryImpl(api, tokenStore)
    val accountRepository: AccountRepository = AccountRepositoryImpl(api)
    val vpnRepository: VpnRepository = VpnRepositoryImpl(api, deviceIdentity, tokenStore)
    val updateRepository: UpdateRepository = UpdateRepositoryImpl(plainHttp, json)
}
