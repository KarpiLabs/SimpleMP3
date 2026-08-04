package io.karpilabs.simplemp3.data.quickconnect

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddresses {
    /**
     * Best-effort LAN IPv4 (prefers Wi‑Fi / non-cellular private ranges).
     */
    fun localIpv4(): String? {
        val candidates = mutableListOf<Pair<Int, String>>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                val name = ni.name.lowercase()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("169.254.")) continue // link-local
                    val score = when {
                        name.startsWith("wlan") || name.startsWith("wifi") -> 0
                        name.startsWith("eth") || name.startsWith("en") -> 1
                        name.startsWith("ap") || name.contains("swlan") -> 2
                        host.startsWith("192.168.") || host.startsWith("10.") -> 3
                        host.startsWith("172.") -> 4
                        else -> 10
                    }
                    candidates += score to host
                }
            }
        }
        return candidates.minByOrNull { it.first }?.second
    }
}
