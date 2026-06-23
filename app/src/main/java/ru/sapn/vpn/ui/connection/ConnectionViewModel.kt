package ru.sapn.vpn.ui.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.repository.VpnRepository
import ru.sapn.vpn.domain.vpn.VpnState
import ru.sapn.vpn.vpn.VpnController

data class ConnectionUiState(
    val loading: Boolean = false,
    val subscription: Subscription? = null,
    val locations: List<Location> = emptyList(),
    val selectedLocationId: String? = null,
    val error: String? = null,
    /** Просьба показать системный VPN-диалог (VpnService.prepare). */
    val needsVpnPermission: Boolean = false,
)

/**
 * Управляет данными экрана подключения и оркеструет connect/disconnect.
 *
 * connect() сам по себе НЕ поднимает туннель: он запрашивает конфиг и, если
 * нужно разрешение VPN, выставляет [ConnectionUiState.needsVpnPermission].
 * UI показывает системный диалог и вызывает [onVpnPermissionResult].
 */
class ConnectionViewModel(
    app: Application,
    private val vpnRepository: VpnRepository,
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ConnectionUiState())
    val ui: StateFlow<ConnectionUiState> = _ui.asStateFlow()

    val vpnState: StateFlow<VpnState> = VpnController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VpnState.DISCONNECTED)

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val sub = vpnRepository.subscription().getOrNull()
            val locsResult = vpnRepository.locations()
            locsResult
                .onSuccess { locs ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        subscription = sub,
                        locations = locs,
                        selectedLocationId = _ui.value.selectedLocationId ?: locs.firstOrNull()?.id,
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        subscription = sub,
                        error = e.message ?: "Не удалось загрузить локации",
                    )
                }
        }
    }

    fun selectLocation(id: String) {
        _ui.value = _ui.value.copy(selectedLocationId = id)
    }

    /** Шаг 1: привязать устройство, проверить разрешение VPN. */
    fun connect() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val bind = vpnRepository.registerDevice()
            if (bind.isFailure) {
                _ui.value = _ui.value.copy(
                    loading = false,
                    error = bind.exceptionOrNull()?.message ?: "Не удалось привязать устройство",
                )
                return@launch
            }
            // Передаём управление UI — нужен системный VpnService.prepare().
            _ui.value = _ui.value.copy(loading = false, needsVpnPermission = true)
        }
    }

    /** Шаг 2: пользователь ответил на системный диалог разрешения VPN. */
    fun onVpnPermissionResult(granted: Boolean) {
        _ui.value = _ui.value.copy(needsVpnPermission = false)
        if (!granted) {
            _ui.value = _ui.value.copy(error = "Нет разрешения на VPN")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            vpnRepository.fetchConfig(_ui.value.selectedLocationId)
                .onSuccess { config ->
                    _ui.value = _ui.value.copy(loading = false)
                    VpnController.start(getApplication(), config)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        error = e.message ?: "Не удалось получить конфиг",
                    )
                }
        }
    }

    fun disconnect() {
        VpnController.stop(getApplication())
    }

    class Factory(
        private val app: Application,
        private val vpnRepository: VpnRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectionViewModel(app, vpnRepository) as T
    }
}
