package ru.sapn.vpn.vpn.libbox

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterface as LibboxInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.NetworkInterface as JavaInterface

/**
 * Реализация io.nekohasekai.libbox.PlatformInterface поверх Android [VpnService]
 * (sing-box 1.11.x).
 *
 * Ключевое для работы трафика: [startDefaultInterfaceMonitor] СООБЩАЕТ sing-box о
 * текущей сети (ConnectivityManager). Без этого sing-box считает, что подложки
 * нет, и дропает весь исходящий трафик («подключено, но интернета нет»).
 * protect() ([autoDetectInterfaceControl]) выводит сокеты движка из tun.
 */
class AndroidPlatformInterface(
    private val service: VpnService,
    private val newBuilder: () -> VpnService.Builder,
) : PlatformInterface {

    @Volatile
    private var tunFd: ParcelFileDescriptor? = null

    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun openTun(options: TunOptions): Int {
        val builder = newBuilder()
            .setSession("SAPN VPN")
            .setMtu(options.getMTU())

        val inet4 = options.inet4Address
        while (inet4.hasNext()) {
            val p = inet4.next()
            builder.addAddress(p.address(), p.prefix())
        }
        val inet6 = options.inet6Address
        while (inet6.hasNext()) {
            val p = inet6.next()
            builder.addAddress(p.address(), p.prefix())
        }

        if (options.autoRoute) {
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

        runCatching {
            val dns = options.getDNSServerAddress()
            if (dns != null && dns.value.isNotBlank()) builder.addDnsServer(dns.value)
        }

        // Свой трафик приложения мимо tun (control-plane не через движок).
        runCatching { builder.addDisallowedApplication(service.packageName) }

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
        Log.i(TAG, "tun established, fd=${pfd.fd}")
        return pfd.fd
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    /** protect() сокета: трафик движка идёт мимо tun (иначе петля handshake). */
    override fun autoDetectInterfaceControl(fd: Int) {
        if (!service.protect(fd)) {
            throw IllegalStateException("VpnService.protect($fd) failed")
        }
    }

    // ── Мониторинг сети: сообщаем sing-box об активной подложке. ──
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        if (listener == null) return
        val cm = service.getSystemService(ConnectivityManager::class.java) ?: return
        connectivity = cm
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = pushDefault(cm, network, listener)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                pushDefault(cm, network, listener)
            override fun onLost(network: Network) {
                runCatching { listener.updateDefaultInterface("", -1, false, false) }
            }
        }
        networkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        // Сразу отдаём текущую активную сеть (колбэк может прийти не мгновенно).
        runCatching { cm.activeNetwork?.let { pushDefault(cm, it, listener) } }
    }

    private fun pushDefault(cm: ConnectivityManager, network: Network, listener: InterfaceUpdateListener) {
        runCatching {
            val name = cm.getLinkProperties(network)?.interfaceName ?: return
            val index = JavaInterface.getByName(name)?.index ?: 0
            val caps = cm.getNetworkCapabilities(network)
            val expensive = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            listener.updateDefaultInterface(name, index, expensive, false)
            Log.i(TAG, "default interface -> $name#$index expensive=$expensive")
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        networkCallback?.let { cb -> runCatching { connectivity?.unregisterNetworkCallback(cb) } }
        networkCallback = null
        connectivity = null
    }

    /** Перечисляем сетевые интерфейсы для sing-box (имя/индекс/адреса/флаги). */
    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = ArrayList<LibboxInterface>()
        runCatching {
            val ifaces = JavaInterface.getNetworkInterfaces() ?: return@runCatching
            for (nif in java.util.Collections.list(ifaces)) {
                val item = LibboxInterface()
                item.setName(nif.name)
                item.setIndex(runCatching { nif.index }.getOrDefault(0))
                item.setMTU(runCatching { nif.mtu }.getOrDefault(0))
                item.setType(Libbox.InterfaceTypeOther)
                val addrs = nif.interfaceAddresses.mapNotNull { ia ->
                    ia.address?.hostAddress?.let { "$it/${ia.networkPrefixLength}" }
                }
                item.setAddresses(StringList(addrs))
                var flags = 0
                runCatching {
                    if (nif.isUp) flags = flags or 0x1            // net.FlagUp
                    if (nif.supportsMulticast()) flags = flags or 0x10
                    if (nif.isLoopback) flags = flags or 0x4
                    if (nif.isPointToPoint) flags = flags or 0x8
                }
                item.setFlags(flags)
                result.add(item)
            }
        }
        return InterfaceList(result)
    }

    /** Логи sing-box → Logcat (tag "sing-box"). Это не конфиг и не ключи. */
    override fun writeLog(message: String?) {
        if (!message.isNullOrBlank()) Log.i("sing-box", message)
    }

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

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() {}
    override fun sendNotification(notification: Notification?) {}

    /** Закрываем удерживаемый FD при остановке движка. */
    fun close() {
        runCatching { tunFd?.close() }
        tunFd = null
    }

    private companion object {
        const val TAG = "SapnPlatform"
    }

    // ── Лёгкие итераторы под gomobile-интерфейсы. ──
    private class StringList(private val items: List<String>) : StringIterator {
        private var i = 0
        override fun hasNext(): Boolean = i < items.size
        override fun next(): String = items[i++]
        override fun len(): Int = items.size
    }

    private class InterfaceList(private val items: List<LibboxInterface>) : NetworkInterfaceIterator {
        private var i = 0
        override fun hasNext(): Boolean = i < items.size
        override fun next(): LibboxInterface = items[i++]
    }
}
