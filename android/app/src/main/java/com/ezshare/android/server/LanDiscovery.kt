package com.ezshare.android.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.InetAddress

/// 通过 mDNS 发布本机服务并发现局域网内其他 EZShare 实例。
class LanDiscovery(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val peers = mutableMapOf<String, Peer>()

    private var registration: NsdServiceInfo? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val multicastLock: WifiManager.MulticastLock? = try {
        (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("EZShareNSD")
            ?.apply { setReferenceCounted(true); acquire() }
    } catch (_: Exception) {
        null
    }

    var onPeers: ((List<Peer>) -> Unit)? = null

    fun register(port: Int, name: String, requireCode: Boolean = false) {
        unregister()
        val info = NsdServiceInfo().apply {
            // 访问码标记写进服务名：SDK 的 TXT 属性在不同版本上行为不一致
            serviceName = if (requireCode) "$name-lock" else name
            serviceType = SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        try {
            manager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            registration = info
            registrationListener = listener
        } catch (_: Exception) {}
    }

    fun unregister() {
        val info = registration ?: return
        val listener = registrationListener ?: return
        try { manager?.unregisterService(listener) } catch (_: Exception) {}
        registration = null
        registrationListener = null
    }

    /// 重新扫描一次。结果通过 onPeers 在主线程回调。
    fun refresh() {
        synchronized(peers) {
            peers.clear()
        }
        emit(emptyList())

        discoveryListener?.let { old ->
            try { manager?.stopServiceDiscovery(old) } catch (_: Exception) {}
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { manager?.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.startsWith(SERVICE_TYPE)) return
                manager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val attrs = resolved.attributes
                        val resolvedName = resolved.serviceName ?: "EZShare"
                        // 访问码标记：TXT 的 code 字段优先，服务名 -lock 兜底
                        val codeFlag = attrs["code"]?.let { String(it, Charsets.UTF_8) }
                        val requiresCode = codeFlag == "1" || resolvedName.endsWith("-lock")

                        // 地址选择：优先 TXT 里的 IPv4，其次过滤后的解析地址。
                        // NSD 常返回 fe80::...%wlan0 这类 link-local IPv6，直接拼进 URL 会请求失败。
                        val txtIpv4 = attrs["ipv4"]?.let { String(it, Charsets.UTF_8) }
                        val host = txtIpv4?.takeIf { it.isNotBlank() } ?: preferredHost(resolved.host)
                        if (host.isNullOrBlank()) return

                        val name = resolvedName.removeSuffix("-lock")
                        val peer = Peer(name, host, resolved.port, requiresCode)
                        synchronized(peers) { peers[peer.url] = peer }
                        emit(peers.values.sortedBy { it.name })
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(peers) {
                    peers.keys.removeAll { it.contains(service.serviceName ?: "\u0000") }
                }
                emit(peers.values.sortedBy { it.name })
            }
        }
        discoveryListener = listener
        try {
            manager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}
    }

    fun release() {
        unregister()
        discoveryListener?.let { try { manager?.stopServiceDiscovery(it) } catch (_: Exception) {} }
        discoveryListener = null
        try { multicastLock?.release() } catch (_: Exception) {}
    }

    private fun emit(list: List<Peer>) {
        main.post { onPeers?.invoke(list) }
    }

    /// 从解析结果里挑一个可直接放进 URL 的主机：优先 IPv4，跳过回环与 link-local。
    /// IPv6 字面量加方括号，否则 "http://fe80::1%wlan0:8080" 不是合法 URL。
    private fun preferredHost(address: InetAddress?): String? {
        if (address == null) return null
        if (address.isLoopbackAddress || address.isLinkLocalAddress) return null
        val raw = address.hostAddress ?: return null
        if (address is Inet4Address) return raw
        return if (raw.contains(":")) "[$raw]" else raw
    }

    companion object {
        const val SERVICE_TYPE = "_ezshare._tcp"
    }
}
