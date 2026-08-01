package io.karpilabs.simplemp3.data.drive

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkWifi {
    fun isUnmeteredOrWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        // Prefer true Wi‑Fi / Ethernet; also allow "not metered" for some VPNs/hotspots.
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
