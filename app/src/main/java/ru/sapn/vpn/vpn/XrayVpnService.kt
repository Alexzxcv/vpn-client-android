package ru.sapn.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.sapn.vpn.R
import ru.sapn.vpn.SapnApp
import ru.sapn.vpn.data.local.LastConnectionStore
import ru.sapn.vpn.data.local.SettingsStore
import ru.sapn.vpn.domain.model.VlessConfig
import ru.sapn.vpn.domain.vpn.VpnEngine
import ru.sapn.vpn.domain.vpn.VpnState
import java.time.Instant

/**
 * VPN-сервис. Реальную маршрутизацию делает sing-box ([XrayCoreVpnEngine]).
 *
 * Жизненный цикл:
 *  1. UI вызывает VpnService.prepare() (диалог разрешения). После согласия —
 *     [VpnController.start] шлёт ACTION_CONNECT.
 *  2. Сервис стартует foreground и отдаёт конфиг движку, который сам строит tun
 *     (libbox.PlatformInterface.openTun) и заворачивает трафик.
 *  3. ACTION_DISCONNECT / onDestroy останавливают движок.
 *
 * ВАЖНО: onStartCommand приходит на MAIN-потоке, а старт/остановка sing-box и
 * чтение DataStore занимают секунды. Поэтому на main остаётся только
 * startForeground, всё остальное уходит в [serviceScope] под [tunnelMutex].
 * Раньше здесь был runBlocking прямо из onStartCommand — он намертво вешал UI
 * (ANR «приложение не отвечает») при переключении серверов.
 *
 * START_NOT_STICKY: если процесс умрёт, система НЕ перезапускает сервис с тем же
 * intent (иначе при сбое движка получался цикл рестартов).
 */
class XrayVpnService : VpnService() {

    private val engine: VpnEngine = XrayCoreVpnEngine(service = this)
    private val lastConnStore by lazy { LastConnectionStore(applicationContext) }

    // Рефреш credential ЖИВЁТ В СЕРВИСЕ (не в ViewModel): foreground-сервис держит
    // процесс, но Activity/ViewModel уничтожается в фоне — если рефреш там, при
    // долгом аптайне credential протухает, туннель (и DNS через него) умирает и
    // интернет пропадает. Scope сервиса не привязан к Activity.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    // Старт и остановка движка — строго по очереди: подряд идущие ACTION_CONNECT
    // (быстрое переключение нод) не должны запускать sing-box параллельно.
    private val tunnelMutex = Mutex()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnect()
            ACTION_CONNECT -> startTunnel(VpnController.consumePendingConfig())
            // Always-on VPN / системный старт без UI: поднимаем последний конфиг.
            // Читаем его уже в фоне — DataStore на main-потоке трогать нельзя.
            else -> startTunnel(config = null, fallBackToLastSaved = true)
        }
        return START_NOT_STICKY
    }

    /**
     * Поднимает туннель. На main-потоке — только startForeground (его система
     * требует сразу), вся тяжёлая работа уходит в [serviceScope].
     */
    private fun startTunnel(config: VlessConfig?, fallBackToLastSaved: Boolean = false) {
        VpnController.updateState(VpnState.CONNECTING)
        startForeground(NOTIF_ID, buildNotification())

        serviceScope.launch {
            tunnelMutex.withLock {
                val cfg = config
                    ?: if (fallBackToLastSaved) runCatching { lastConnStore.get() }.getOrNull() else null
                if (cfg == null) {
                    Log.w(TAG, "startTunnel: no config")
                    VpnController.fail(getString(R.string.vpn_error_no_config))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@withLock
                }
                try {
                    // Перед стартом гасим возможный предыдущий туннель — это делает
                    // повторный ACTION_CONNECT (смена ноды) бесшовным переподключением.
                    runCatching { engine.stop() }
                    val settings = SettingsStore(applicationContext).get()
                    engine.start(cfg, settings)
                    // Сохраняем конфиг для Always-on (поднять туннель без UI).
                    runCatching { lastConnStore.save(cfg) }
                    VpnController.updateState(VpnState.CONNECTED)
                    scheduleRefresh(cfg)
                } catch (t: Throwable) {
                    Log.e(TAG, "connect failed", t)
                    VpnController.fail(t.message ?: t.javaClass.simpleName)
                    runCatching { engine.stop() }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    /**
     * Планирует перевыпуск credential до истечения (за [REFRESH_LEAD_SECONDS]).
     * Свои серверы (expiresAt == null) не истекают — рефреш не нужен.
     */
    private fun scheduleRefresh(config: VlessConfig) {
        refreshJob?.cancel()
        val expiryStr = config.expiresAt ?: return
        val expiry = runCatching { Instant.parse(expiryStr) }.getOrNull() ?: return
        val refreshAt = expiry.minusSeconds(REFRESH_LEAD_SECONDS)
        val delayMs = (refreshAt.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)
        refreshJob = serviceScope.launch {
            delay(delayMs)
            refreshConfig()
        }
    }

    /**
     * Тянет свежий конфиг последней backend-ноды (по сохранённому server_id) и
     * перезапускает туннель обычным путём (ACTION_CONNECT → startTunnel на main-
     * потоке, который заново запланирует рефреш). При ошибке — повтор позже, чтобы
     * не остаться с протухшим credential.
     */
    private suspend fun refreshConfig() {
        val container = (application as? SapnApp)?.container ?: return
        val serverId = runCatching { lastConnStore.serverId() }.getOrNull()
        // Свой сервер не истекает — рефреш не нужен.
        if (serverId != null && serverId.startsWith("custom:")) return
        val target = serverId?.takeIf { it.isNotBlank() }
        container.vpnRepository.fetchConfig(target)
            .onSuccess { fresh ->
                Log.i(TAG, "credential refreshed; restarting tunnel")
                VpnController.start(applicationContext, fresh)
            }
            .onFailure {
                Log.w(TAG, "config refresh failed; retry later: ${it.message}")
                refreshJob = serviceScope.launch {
                    delay(REFRESH_RETRY_MS)
                    refreshConfig()
                }
            }
    }

    private fun disconnect() {
        refreshJob?.cancel()
        // Состояние и уведомление снимаем сразу: UI не должен ждать движок.
        VpnController.updateState(VpnState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.launch {
            tunnelMutex.withLock { runCatching { engine.stop() } }
            stopSelf()
        }
    }

    override fun onRevoke() {
        // Система отозвала разрешение VPN (например, другой VPN перехватил).
        disconnect()
        super.onRevoke()
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        runCatching { serviceScope.cancel() }
        runCatching { engine.stop() }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.vpn_notification_active))
            .setSmallIcon(R.drawable.ic_tile_vpn)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "ru.sapn.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.sapn.vpn.DISCONNECT"

        private const val TAG = "XrayVpnService"
        private const val CHANNEL_ID = "sapn_vpn"
        private const val NOTIF_ID = 1

        // За сколько до истечения перевыпускать credential и через сколько
        // повторять при неудаче.
        private const val REFRESH_LEAD_SECONDS = 12L * 60L * 60L
        private const val REFRESH_RETRY_MS = 30L * 60L * 1000L
    }
}
