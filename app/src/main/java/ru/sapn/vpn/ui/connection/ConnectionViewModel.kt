package ru.sapn.vpn.ui.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.sapn.vpn.BuildConfig
import ru.sapn.vpn.data.local.CustomServerStore
import ru.sapn.vpn.domain.model.CustomServer
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.repository.VpnRepository
import ru.sapn.vpn.domain.update.AppUpdate
import ru.sapn.vpn.domain.update.UpdateRepository
import ru.sapn.vpn.domain.vpn.VpnState
import ru.sapn.vpn.vpn.VlessLinkParser
import ru.sapn.vpn.vpn.VpnController
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

data class ConnectionUiState(
    val loading: Boolean = false,
    val subscription: Subscription? = null,
    val locations: List<Location> = emptyList(),
    val customServers: List<CustomServer> = emptyList(),
    val selectedLocationId: String? = null,
    val error: String? = null,
    /** Ошибка добавления своего конфига (для диалога). */
    val customError: String? = null,
    /** Просьба показать системный VPN-диалог (VpnService.prepare). */
    val needsVpnPermission: Boolean = false,
)

/**
 * Управляет данными экрана подключения и оркеструет connect/disconnect.
 *
 * Поддерживает два источника серверов: ноды подписки (через бэкенд) и свои
 * VLESS-конфиги (локально, без бэкенда; id вида "custom:<uuid>").
 */
class ConnectionViewModel(
    app: Application,
    private val vpnRepository: VpnRepository,
    private val updateRepository: UpdateRepository,
    private val customServerStore: CustomServerStore,
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ConnectionUiState())
    val ui: StateFlow<ConnectionUiState> = _ui.asStateFlow()

    private val _update = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()

    val vpnState: StateFlow<VpnState> = VpnController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VpnState.DISCONNECTED)

    val vpnError: StateFlow<String?> = VpnController.error

    private var refreshJob: Job? = null
    private var lastUpdateCheckMs = 0L

    fun checkForUpdate() {
        val now = System.currentTimeMillis()
        if (_update.value == null && now - lastUpdateCheckMs < UPDATE_CHECK_INTERVAL_MS) return
        lastUpdateCheckMs = now
        viewModelScope.launch {
            updateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
                .onSuccess { _update.value = it }
        }
    }

    fun dismissUpdate() {
        _update.value = null
    }

    fun load() {
        loadCustomServers()
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val sub = vpnRepository.subscription().getOrNull()
            vpnRepository.locations()
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

    private fun loadCustomServers() {
        viewModelScope.launch {
            val list = customServerStore.list()
            _ui.value = _ui.value.copy(
                customServers = list,
                selectedLocationId = _ui.value.selectedLocationId
                    ?: list.firstOrNull()?.let { CUSTOM_PREFIX + it.id },
            )
        }
    }

    /** Добавить свой конфиг по vless-ссылке. Ошибка парсинга — в [customError]. */
    fun addCustomServer(link: String) {
        VlessLinkParser.parse(link).fold(
            onSuccess = { (name, config) ->
                viewModelScope.launch {
                    customServerStore.add(CustomServer(UUID.randomUUID().toString(), name, config))
                    _ui.value = _ui.value.copy(customError = null)
                    loadCustomServers()
                }
            },
            onFailure = { e ->
                _ui.value = _ui.value.copy(customError = e.message ?: "Некорректная ссылка")
            },
        )
    }

    fun clearCustomError() {
        _ui.value = _ui.value.copy(customError = null)
    }

    fun removeCustomServer(id: String) {
        viewModelScope.launch {
            customServerStore.remove(id)
            if (_ui.value.selectedLocationId == CUSTOM_PREFIX + id) {
                _ui.value = _ui.value.copy(selectedLocationId = _ui.value.locations.firstOrNull()?.id)
            }
            loadCustomServers()
        }
    }

    fun selectLocation(id: String) {
        val prev = _ui.value.selectedLocationId
        _ui.value = _ui.value.copy(selectedLocationId = id)
        val st = vpnState.value
        if (id != prev && (st == VpnState.CONNECTED || st == VpnState.CONNECTING)) {
            reconnect(id)
        }
    }

    private fun reconnect(id: String) {
        customConfig(id)?.let { cfg ->
            // Свой конфиг — поднимаем напрямую (сервис погасит старый туннель).
            VpnController.start(getApplication(), cfg)
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            vpnRepository.fetchConfig(id)
                .onSuccess { config ->
                    _ui.value = _ui.value.copy(loading = false)
                    VpnController.start(getApplication(), config)
                    scheduleRefresh(config)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Не удалось переключить ноду")
                }
        }
    }

    /** Шаг 1: для нод подписки — привязать устройство; для своих — сразу к разрешению VPN. */
    fun connect() {
        val sel = _ui.value.selectedLocationId
        if (isCustom(sel)) {
            _ui.value = _ui.value.copy(error = null, needsVpnPermission = true)
            return
        }
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
        val sel = _ui.value.selectedLocationId
        // Свой конфиг — бэкенд не нужен.
        customConfig(sel)?.let { cfg ->
            VpnController.start(getApplication(), cfg)
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            vpnRepository.fetchConfig(sel)
                .onSuccess { config ->
                    _ui.value = _ui.value.copy(loading = false)
                    VpnController.start(getApplication(), config)
                    scheduleRefresh(config)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Не удалось получить конфиг")
                }
        }
    }

    fun disconnect() {
        refreshJob?.cancel()
        refreshJob = null
        VpnController.stop(getApplication())
    }

    private fun scheduleRefresh(config: VlessConfig) {
        refreshJob?.cancel()
        val expiry = config.expiresAt?.let { parseInstant(it) } ?: return
        val refreshAt = expiry.minusSeconds(REFRESH_LEAD_SECONDS)
        val delayMs = (refreshAt.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)

        refreshJob = viewModelScope.launch {
            delay(delayMs)
            if (vpnState.value != VpnState.CONNECTED && vpnState.value != VpnState.CONNECTING) {
                return@launch
            }
            vpnRepository.fetchConfig(_ui.value.selectedLocationId)
                .onSuccess { fresh ->
                    VpnController.start(getApplication(), fresh)
                    scheduleRefresh(fresh)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(error = e.message ?: "Не удалось обновить конфиг")
                }
        }
    }

    private fun isCustom(id: String?): Boolean = id != null && id.startsWith(CUSTOM_PREFIX)

    private fun customConfig(id: String?): VlessConfig? =
        if (isCustom(id)) _ui.value.customServers.find { CUSTOM_PREFIX + it.id == id }?.config else null

    private fun parseInstant(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    private companion object {
        const val CUSTOM_PREFIX = "custom:"
        const val REFRESH_LEAD_SECONDS = 12L * 60L * 60L
        const val UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }

    class Factory(
        private val app: Application,
        private val vpnRepository: VpnRepository,
        private val updateRepository: UpdateRepository,
        private val customServerStore: CustomServerStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectionViewModel(app, vpnRepository, updateRepository, customServerStore) as T
    }
}
