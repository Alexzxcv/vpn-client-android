package ru.sapn.vpn.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.sapn.vpn.MainActivity
import ru.sapn.vpn.SapnApp
import ru.sapn.vpn.data.local.LastConnectionStore
import ru.sapn.vpn.domain.vpn.VpnState
import ru.sapn.vpn.vpn.VpnController

/**
 * Плитка быстрых настроек (шторка): одно нажатие — подключить/отключить VPN.
 *
 * Состояние берём из того же [VpnController.state], что наблюдает UI.
 *  - туннель активен → отключаем напрямую через [VpnController.stop];
 *  - отключён → поднять из плитки нельзя (нужен VpnService.prepare() + сетевой
 *    запрос конфига) → открываем [MainActivity].
 *
 * На Android 14+ startActivityAndCollapse(Intent) бросает исключение — нужен
 * PendingIntent. Всё в onClick обёрнуто в runCatching, чтобы плитка никогда не
 * валила процесс.
 */
class VpnTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        VpnController.state
            .onEach { runCatching { render(it) } }
            .launchIn(s)
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        runCatching {
            when (VpnController.state.value) {
                VpnState.CONNECTED, VpnState.CONNECTING -> VpnController.stop(applicationContext)
                VpnState.DISCONNECTED, VpnState.ERROR -> {
                    // Если согласие VPN уже выдано — подключаемся прямо из плитки.
                    // Иначе нужен системный диалог prepare() → открываем приложение.
                    if (VpnService.prepare(this) == null) connectDirectly() else openAppToConnect()
                }
            }
        }.onFailure { Log.w(TAG, "tile click failed: ${it.message}") }
    }

    /**
     * Полный цикл подключения из плитки: восстанавливаем ПОСЛЕДНЕЕ подключение и
     * поднимаем туннель.
     *
     * Восстанавливаем ПОСЛЕДНЮЮ выбранную локацию по сохранённому server_id:
     *  - свой сервер ("custom:…") — поднимаем сохранённый конфиг напрямую (backend
     *    не нужен, не истекает);
     *  - конкретная backend-нода — тянем СВЕЖИЙ конфиг именно этой ноды (по её id);
     *  - ничего не сохранено — лучшая нода (fetchConfig(null)).
     * Раньше для backend всегда бралась первая онлайн-нода, забывая выбор.
     */
    private fun connectDirectly() {
        val container = (application as? SapnApp)?.container ?: return openAppToConnect()
        val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        VpnController.updateState(VpnState.CONNECTING)
        s.launch {
            val store = LastConnectionStore(applicationContext)
            val lastId = store.serverId()

            // Свой сервер — поднимаем сохранённый конфиг напрямую.
            if (lastId != null && lastId.startsWith("custom:")) {
                val last = store.get()
                if (last != null) {
                    VpnController.start(applicationContext, last)
                    return@launch
                }
            }

            // Backend: свежий конфиг последней выбранной ноды (lastId), иначе лучшей.
            val serverId = lastId?.takeIf { it.isNotBlank() && !it.startsWith("custom:") }
            container.vpnRepository.fetchConfig(serverId)
                .onSuccess { config -> VpnController.start(applicationContext, config) }
                .onFailure {
                    VpnController.updateState(VpnState.ERROR)
                    Log.w(TAG, "tile connect failed: ${it.message}")
                }
        }
    }

    private fun openAppToConnect() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launch: () -> Unit = {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { runCatching { launch() } } else runCatching { launch() }
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            VpnState.CONNECTED -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private companion object {
        const val TAG = "VpnTileService"
    }
}
