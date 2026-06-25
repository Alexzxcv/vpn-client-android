package ru.sapn.vpn.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.sapn.vpn.domain.model.VpnSettings

private val Context.settingsDataStore by preferencesDataStore(name = "sapn_settings")

/** Хранение локальных настроек ([VpnSettings]) в DataStore. */
class SettingsStore(private val context: Context) {

    private companion object {
        val RUSSIA_DIRECT = booleanPreferencesKey("russia_direct")
        val DIRECT_LIST = stringPreferencesKey("direct_list") // строки через \n
    }

    val flow = context.settingsDataStore.data.map { p ->
        VpnSettings(
            russiaDirect = p[RUSSIA_DIRECT] ?: false,
            directList = (p[DIRECT_LIST] ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    suspend fun get(): VpnSettings = flow.first()

    suspend fun save(settings: VpnSettings) {
        context.settingsDataStore.edit { p ->
            p[RUSSIA_DIRECT] = settings.russiaDirect
            p[DIRECT_LIST] = settings.directList.joinToString("\n")
        }
    }
}
