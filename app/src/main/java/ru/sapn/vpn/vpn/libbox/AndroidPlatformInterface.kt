package ru.sapn.vpn.vpn.libbox

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * РЕАЛЬНАЯ ИНТЕГРАЦИЯ libbox (sing-box mobile).
 *
 * Этот файл ИМПОРТИРУЕТ типы из AAR `io.nekohasekai.libbox.*`. Пока AAR не лежит
 * в `app/libs/libbox.aar` (см. app/build.gradle.kts), файл НЕ компилируется, поэтому
 * по умолчанию он закомментирован целиком. Чтобы включить реальный движок:
 *
 *   1. Положи libbox.aar в app/libs/ (как собрать — см. KDoc XrayCoreVpnEngine).
 *   2. В app/build.gradle.kts раскомментируй `implementation(files("libs/libbox.aar"))`.
 *   3. Раскомментируй тело этого файла (убери блок /* … */ ниже).
 *   4. В XrayCoreVpnEngine поставь ENGINE_AAR_AVAILABLE = true.
 *
 * Класс реализует io.nekohasekai.libbox.PlatformInterface — мост между sing-box и
 * Android VpnService:
 *  - openTun(): отдаёт движку FD tun, который мы строим из TunOptions через
 *    VpnService.Builder (адреса/маршруты/MTU/DNS берём из options);
 *  - autoDetectInterfaceControl(fd): protect() исходящих сокетов sing-box, чтобы
 *    handshake до ноды не зацикливался в tun;
 *  - остальные методы — безопасные дефолты (no-op / разумные значения).
 *
 * Сигнатуры сверены по публичному API libbox (pkg.go.dev .../experimental/libbox),
 * ветка sing-box 1.11.x. gomobile отображает Go-методы GetX()/OpenTun() в Kotlin как
 * getX()/openTun(); методы с (…, error) бросают исключение. Если апстрим поменяет
 * интерфейс — сверь по experimental/libbox/platform_interface.go.
 * ─────────────────────────────────────────────────────────────────────────────
 */

/*
import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

/**
 * Реализация libbox.PlatformInterface поверх Android [VpnService].
 *
 * @param service активный VpnService (для protect() и построения tun).
 * @param newBuilder фабрика свежего VpnService.Builder под каждый openTun().
 */
class AndroidPlatformInterface(
    private val service: VpnService,
    private val newBuilder: () -> VpnService.Builder,
) : PlatformInterface {

    @Volatile
    private var tunFd: ParcelFileDescriptor? = null

    /**
     * Строим tun из опций, которые sing-box сформировал из нашего конфига
     * (адрес 172.18.0.1/30, MTU 1500, auto_route → маршруты), и отдаём FD.
     * Владение FD остаётся за нами — закрываем в [close].
     */
    override fun openTun(options: TunOptions): Int {
        val builder = newBuilder()
            .setSession("SAPN VPN")
            .setMtu(options.mtu)

        // IPv4-адреса tun.
        val inet4 = options.inet4Address
        while (inet4.hasNext()) {
            val p = inet4.next()
            builder.addAddress(p.address(), p.prefix())
        }
        // IPv6 (если sing-box их добавил; в нашем конфиге — только v4).
        val inet6 = options.inet6Address
        while (inet6.hasNext()) {
            val p = inet6.next()
            builder.addAddress(p.address(), p.prefix())
        }

        if (options.autoRoute) {
            // Конкретные маршруты, которые посчитал sing-box; если их нет —
            // заворачиваем весь IPv4 (v6 не маршрутизируем: анти-leak).
            val r4 = options.inet4RouteAddress
            if (r4.hasNext()) {
                while (r4.hasNext()) {
                    val p = r4.next()
                    builder.addRoute(p.address(), p.prefix())
                }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }
            val r6 = options.inet6RouteAddress
            while (r6.hasNext()) {
                val p = r6.next()
                builder.addRoute(p.address(), p.prefix())
            }
        }

        // DNS из опций (если задан).
        runCatching {
            val dns = options.dnsServerAddress
            if (dns != null && dns.value.isNotBlank()) builder.addDnsServer(dns.value)
        }

        // Свой трафик приложения мимо tun: control-plane не должен идти через движок.
        runCatching { builder.addDisallowedApplication(service.packageName) }

        // Per-app: пакеты, которые sing-box просит включить/исключить.
        val include = options.includePackage
        while (include.hasNext()) {
            runCatching { builder.addAllowedApplication(include.next()) }
        }
        val exclude = options.excludePackage
        while (exclude.hasNext()) {
            runCatching { builder.addDisallowedApplication(exclude.next()) }
        }

        val pfd = builder.establish()
            ?: throw IllegalStateException("VpnService.establish() returned null (нет разрешения VPN?)")
        tunFd = pfd
        return pfd.fd
    }

    /** sing-box сам делает авто-детект интерфейса через protect(). */
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    /** protect() сокета: трафик движка идёт мимо tun (иначе петля handshake). */
    override fun autoDetectInterfaceControl(fd: Int) {
        if (!service.protect(fd)) {
            throw IllegalStateException("VpnService.protect($fd) failed")
        }
    }

    /** Свой DNS-транспорт не подменяем — используем заданный в конфиге. */
    override fun localDNSTransport(): LocalDNSTransport? = null

    /** Логи sing-box не пробрасываем (анти-leak: конфиг/ключи не должны утечь в Logcat). */
    override fun writeLog(message: String?) {}

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int,
    ): Int = throw UnsupportedOperationException("findConnectionOwner not supported")

    override fun packageNameByUid(uid: Int): String =
        throw UnsupportedOperationException("packageNameByUid not supported")

    override fun uidByPackageName(packageName: String?): Int =
        throw UnsupportedOperationException("uidByPackageName not supported")

    // ── Мониторинг интерфейсов/Wi-Fi не используем: безопасные дефолты. ──
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}
    override fun getInterfaces(): NetworkInterfaceIterator =
        throw UnsupportedOperationException("getInterfaces not supported")

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): WIFIState? = null
    override fun systemCertificates(): StringIterator? = null
    override fun clearDNSCache() {}
    override fun sendNotification(notification: Notification?) {}

    /** Закрываем удерживаемый FD при остановке движка. */
    fun close() {
        runCatching { tunFd?.close() }
        tunFd = null
    }
}
*/
