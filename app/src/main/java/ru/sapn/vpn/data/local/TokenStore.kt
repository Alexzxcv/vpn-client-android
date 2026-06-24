package ru.sapn.vpn.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sapn_tokens")

/**
 * Хранение JWT-токенов в DataStore.
 *
 * NB: DataStore Preferences не шифрует данные. Для продакшена токены стоит
 * дополнительно шифровать (Jetpack Security / EncryptedFile или хранить refresh
 * в Keystore). См. TODO ниже.
 */
class TokenStore(private val context: Context) {

    private companion object {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    /** device_id, выданный сервером при регистрации устройства (POST /devices). */
    suspend fun deviceId(): String? = context.dataStore.data.first()[DEVICE_ID]

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { it[DEVICE_ID] = deviceId }
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[ACCESS] }

    val isLoggedIn: Flow<Boolean> =
        context.dataStore.data.map { !it[ACCESS].isNullOrBlank() }

    suspend fun accessToken(): String? = context.dataStore.data.first()[ACCESS]

    suspend fun refreshToken(): String? = context.dataStore.data.first()[REFRESH]

    suspend fun save(access: String, refresh: String) {
        // TODO(security): зашифровать перед записью (Keystore / EncryptedFile).
        context.dataStore.edit {
            it[ACCESS] = access
            it[REFRESH] = refresh
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
