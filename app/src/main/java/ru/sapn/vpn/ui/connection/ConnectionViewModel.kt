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
import ru.sapn.vpn.domain.model.Location
import ru.sapn.vpn.domain.model.Subscription
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.repository.VpnRepository
import ru.sapn.vpn.domain.update.AppUpdate
import ru.sapn.vpn.domain.update.UpdateRepository
import ru.sapn.vpn.domain.vpn.VpnState
import ru.sapn.vpn.vpn.VpnController
import java.time.Instant
import java.time.format.DateTimeParseException

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
    private val updateRepository: UpdateRepository,
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ConnectionUiState())
    val ui: StateFlow<ConnectionUiState> = _ui.asStateFlow()

    /** Доступное обновление (null — нечего показывать). */
    private val _update = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()

    val vpnState: StateFlow<VpnState> = VpnController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VpnState.DISCONNECTED)

    /** Корутина авто-рефреша конфига (перезапускается при каждом успешном fetch). */
    private var refreshJob: Job? = null

    /** Момент последней проверки обновлений (троттлинг, чтобы не дёргать GitHub). */
    private var lastUpdateCheckMs = 0L

    /**
     * Проверка обновлений: не чаще раза в [UPDATE_CHECK_INTERVAL_MS]. Любые ошибки
     * репозиторий проглатывает (success(null)), так что баннер просто не появится.
     */
    fun checkForUpdate() {
        val now = System.currentTimeMillis()
        if (_update.value == null && now - lastUpdateCheckMs < UPDATE_CHECK_INTERVAL_MS) return
        lastUpdateCheckMs = now
        viewModelScope.launch {
            updateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
                .onSuccess { _update.value = it }
        }
    }

    /** Скрыть баннер обновления (до следующего запуска/проверки). */
    fun dismissUpdate() {
        _update.value = null
    }

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
                    scheduleRefresh(config)
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
        refreshJob?.cancel()
        refreshJob = null
        VpnController.stop(getApplication())
    }

    /**
     * Планирует авто-рефреш конфига: спит до момента «expires_at − 12ч», затем
     * запрашивает новый конфиг и, если туннель ещё активен, перезапускает движок.
     * Если [expiresAt] отсутствует/некорректен — рефреш не планируется.
     */
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
                    // Бесшовно поднимаем туннель с новым конфигом.
                    VpnController.start(getApplication(), fresh)
                    scheduleRefresh(fresh)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        error = e.message ?: "Не удалось обновить конфиг",
                    )
                }
        }
    }

    private fun parseInstant(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    private companion object {
        /** Авто-рефреш, когда до истечения остаётся менее 12ч. */
        const val REFRESH_LEAD_SECONDS = 12L * 60L * 60L

        /** Минимальный интервал между проверками обновлений — 6 часов. */
        const val UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }

    class Factory(
        private val app: Application,
        private val vpnRepository: VpnRepository,
        private val updateRepository: UpdateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectionViewModel(app, vpnRepository, updateRepository) as T
    }
}
