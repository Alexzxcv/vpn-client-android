package ru.sapn.vpn.vpn

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.vpn.VpnState

/**
 * Мост между ViewModel и [XrayVpnService].
 *
 * Состояние держим в синглтоне-процесса: сервис обновляет его, UI наблюдает.
 * Конфиг для запуска передаём через [pendingConfig] (а не через Intent), чтобы
 * не сериализовать чувствительные параметры VLESS в Intent extras.
 */
object VpnController {

    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    /** Текст последней ошибки подключения (для показа в UI). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Конфиг, который сервис заберёт при старте. */
    @Volatile
    var pendingConfig: VlessConfig? = null
        private set

    fun updateState(state: VpnState) {
        if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) _error.value = null
        _state.value = state
    }

    /** Перевести в ошибку с человекочитаемым сообщением (показывается в UI). */
    fun fail(message: String?) {
        _error.value = message
        _state.value = VpnState.ERROR
    }

    /** Запрос на старт туннеля. Должен вызываться после VpnService.prepare(). */
    fun start(context: Context, config: VlessConfig) {
        pendingConfig = config
        val intent = Intent(context, XrayVpnService::class.java)
            .setAction(XrayVpnService.ACTION_CONNECT)
        context.startService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, XrayVpnService::class.java)
            .setAction(XrayVpnService.ACTION_DISCONNECT)
        context.startService(intent)
    }

    fun consumePendingConfig(): VlessConfig? {
        val c = pendingConfig
        pendingConfig = null
        return c
    }
}
