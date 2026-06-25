package ru.sapn.vpn.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.sapn.vpn.domain.model.CustomServer
import ru.sapn.vpn.domain.model.VlessConfig

private val Context.customServersDataStore by preferencesDataStore(name = "sapn_custom_servers")

/** Сериализуемое представление [CustomServer] для хранения в DataStore (JSON). */
@Serializable
private data class CustomServerEntity(
    val id: String,
    val name: String,
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

private fun CustomServerEntity.toDomain() = CustomServer(
    id = id,
    name = name,
    config = VlessConfig(host, port, uuid, security, flow, publicKey, shortId, sni, fingerprint),
)

private fun CustomServer.toEntity() = CustomServerEntity(
    id = id, name = name,
    host = config.host, port = config.port, uuid = config.uuid, security = config.security,
    flow = config.flow, publicKey = config.publicKey, shortId = config.shortId,
    sni = config.sni, fingerprint = config.fingerprint,
)

/** Хранение пользовательских VLESS-серверов (свои конфиги) в DataStore. */
class CustomServerStore(private val context: Context) {

    private companion object {
        val KEY = stringPreferencesKey("servers_json")
        val json = Json { ignoreUnknownKeys = true }
    }

    val flow: Flow<List<CustomServer>> = context.customServersDataStore.data.map { decode(it[KEY]) }

    suspend fun list(): List<CustomServer> = flow.first()

    /** Добавляет сервер с указанным id (UUID генерируется вызывающим). */
    suspend fun add(server: CustomServer) {
        val current = list().toMutableList()
        current.add(server)
        persist(current)
    }

    suspend fun remove(id: String) {
        persist(list().filterNot { it.id == id })
    }

    private suspend fun persist(servers: List<CustomServer>) {
        val entities: List<CustomServerEntity> = servers.map { it.toEntity() }
        val raw = json.encodeToString(entities)
        context.customServersDataStore.edit { it[KEY] = raw }
    }

    private fun decode(raw: String?): List<CustomServer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CustomServerEntity>>(raw).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }
}
