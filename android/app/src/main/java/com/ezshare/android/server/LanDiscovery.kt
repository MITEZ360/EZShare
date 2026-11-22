package com.ezshare.android.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

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

    fun register(port: Int, name: String) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = name
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
                        val address = resolved.host?.hostAddress ?: return
                        val peer = Peer(resolved.serviceName ?: "EZShare", address, resolved.port)
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

    companion object {
        const val SERVICE_TYPE = "_ezshare._tcp"
    }
}
