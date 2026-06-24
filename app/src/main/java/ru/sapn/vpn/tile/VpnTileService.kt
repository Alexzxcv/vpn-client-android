package ru.sapn.vpn.tile

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.sapn.vpn.MainActivity
import ru.sapn.vpn.domain.vpn.VpnState
import ru.sapn.vpn.vpn.VpnController

/**
 * Плитка быстрых настроек (шторка): одно нажатие — подключить/отключить VPN.
 *
 * Состояние берём из того же [VpnController.state], что наблюдает UI, поэтому
 * плитка и экран всегда синхронны. Сама плитка движок не реализует.
 *
 * Поведение по нажатию:
 *  - если туннель активен (CONNECTED/CONNECTING) — отключаем напрямую через
 *    [VpnController.stop];
 *  - если отключён — поднять туннель из плитки нельзя: нужен системный диалог
 *    VpnService.prepare() (его нельзя показать из TileService) и сетевой запрос
 *    свежего конфига. Поэтому открываем [MainActivity], где живёт полный путь
 *    connect (привязка устройства + получение конфига + согласие).
 */
class VpnTileService : TileService() {

    /** Скоуп жив между onStartListening и onStopListening. */
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        // Подписываемся на состояние и обновляем плитку при каждом изменении.
        VpnController.state
            .onEach { render(it) }
            .launchIn(s)
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (VpnController.state.value) {
            VpnState.CONNECTED, VpnState.CONNECTING -> {
                // Активный туннель гасим прямо из плитки.
                VpnController.stop(applicationContext)
            }
            VpnState.DISCONNECTED, VpnState.ERROR -> {
                // Согласие на VPN уже выдано и есть открытая сессия — но конфиг
                // всё равно тянется по сети из ViewModel, поэтому связывать tun
                // из плитки нельзя. Передаём подключение в MainActivity.
                openAppToConnect()
            }
        }
    }

    /** Открыть приложение для полного цикла подключения (через MainActivity). */
    private fun openAppToConnect() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Если экран заблокирован — сперва разблокировать, затем запустить.
        if (isLocked) {
            unlockAndRun { startActivityAndCollapse(intent) }
        } else {
            startActivityAndCollapse(intent)
        }
    }

    /** Привести плитку в соответствие состоянию туннеля. */
    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        // CONNECTING оставляем неактивной — активной плитка станет только при CONNECTED.
        tile.state = when (state) {
            VpnState.CONNECTED -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
