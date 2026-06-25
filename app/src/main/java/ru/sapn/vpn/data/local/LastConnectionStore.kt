package ru.sapn.vpn.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.sapn.vpn.domain.model.VlessConfig

private val Context.lastConnDataStore by preferencesDataStore(name = "sapn_last_conn")

@Serializable
private data class LastConfigEntity(
    val host: String,
    val port: Int,
    val uuid: String,
    val security: String,
    val flow: String,
    val publicKey: String,
    val shortId: String,
    val sni: String,
    val fingerprint: String,
)

/**
 * Хранит последний успешно использованный [VlessConfig], чтобы системный
 * Always-on VPN мог поднять туннель без UI (приложение может быть выгружено).
 */
class LastConnectionStore(private val context: Context) {

    private companion object {
        val KEY = stringPreferencesKey("config_json")
        val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun save(config: VlessConfig) {
        val e = LastConfigEntity(
            config.host, config.port, config.uuid, config.security, config.flow,
            config.publicKey, config.shortId, config.sni, config.fingerprint,
        )
        context.lastConnDataStore.edit { it[KEY] = json.encodeToString(e) }
    }

    suspend fun get(): VlessConfig? {
        val raw = context.lastConnDataStore.data.first()[KEY] ?: return null
        return runCatching {
            val e = json.decodeFromString<LastConfigEntity>(raw)
            VlessConfig(e.host, e.port, e.uuid, e.security, e.flow, e.publicKey, e.shortId, e.sni, e.fingerprint)
        }.getOrNull()
    }
}
