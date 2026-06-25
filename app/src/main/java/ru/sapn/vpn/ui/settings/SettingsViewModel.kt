package ru.sapn.vpn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.sapn.vpn.data.local.SettingsStore
import ru.sapn.vpn.domain.model.VpnSettings

data class SettingsUiState(
    val russiaDirect: Boolean = false,
    val directList: String = "",
    val saved: Boolean = false,
    val loaded: Boolean = false,
)

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val s = store.get()
            _ui.value = SettingsUiState(
                russiaDirect = s.russiaDirect,
                directList = s.directList.joinToString("\n"),
                loaded = true,
            )
        }
    }

    fun setRussiaDirect(v: Boolean) {
        _ui.value = _ui.value.copy(russiaDirect = v, saved = false)
    }

    fun setDirectList(v: String) {
        _ui.value = _ui.value.copy(directList = v, saved = false)
    }

    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            store.save(
                VpnSettings(
                    russiaDirect = s.russiaDirect,
                    directList = s.directList.lines().map { it.trim() }.filter { it.isNotEmpty() },
                )
            )
            _ui.value = _ui.value.copy(saved = true)
        }
    }

    class Factory(private val store: SettingsStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(store) as T
    }
}
