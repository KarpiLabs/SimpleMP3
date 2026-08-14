package io.karpilabs.simplemp3.data.jellyfin

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers Jellyfin servers on the local network via the official UDP protocol
 * (port 7359, payload "Who is JellyfinServer?").
 */
@Singleton
class JellyfinDiscovery
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val moshi: Moshi,
    ) {
        private val responseAdapter by lazy { moshi.adapter(DiscoveredServerResponse::class.java) }

        suspend fun discover(timeoutMs: Long = 3_500L): List<DiscoveredJellyfinServer> =
            withContext(Dispatchers.IO) {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                @Suppress("DEPRECATION")
                val lock =
                    wifi.createMulticastLock("simplemp3-jellyfin-discovery").apply {
                        setReferenceCounted(true)
                        acquire()
                    }
                try {
                    coroutineScope {
                        val jobs =
                            listOf(
                                async { broadcastDiscover(timeoutMs) },
                                async { probeCommonLocalPorts() },
                            )
                        jobs
                            .awaitAll()
                            .flatten()
                            .distinctBy { normalizeKey(it.address) }
                            .sortedBy { it.name.lowercase() }
                    }
                } finally {
                    if (lock.isHeld) lock.release()
                }
            }

        private fun broadcastDiscover(timeoutMs: Long): List<DiscoveredJellyfinServer> {
            val found = linkedMapOf<String, DiscoveredJellyfinServer>()
            val payload = DISCOVERY_MESSAGE.toByteArray(StandardCharsets.UTF_8)
            val socket =
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 400
                    reuseAddress = true
                }

            try {
                val targets = broadcastTargets()
                for (target in targets) {
                    try {
                        val packet =
                            DatagramPacket(
                                payload,
                                payload.size,
                                InetSocketAddress(target, DISCOVERY_PORT),
                            )
                        socket.send(packet)
                    } catch (_: Exception) {
                        // try next broadcast address
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)
                        val text =
                            String(
                                response.data,
                                response.offset,
                                response.length,
                                StandardCharsets.UTF_8,
                            ).trim()
                        parseResponse(text)?.let { server ->
                            found[normalizeKey(server.address)] = server
                        }
                    } catch (_: SocketTimeoutException) {
                        // keep listening until deadline
                    } catch (_: Exception) {
                        break
                    }
                }
            } finally {
                socket.close()
            }
            return found.values.toList()
        }

        /**
         * Fallback: hit common Jellyfin HTTP ports on the phone's local subnet gateway + this host.
         * Helps when UDP discovery is blocked by AP isolation.
         */

        /**
         * Light fallback only — keep cold discovery snappy.
         * Prefer UDP; probe gateway (.1) and this device's own subnet host .1/.2 on 8096.
         */
        private suspend fun probeCommonLocalPorts(): List<DiscoveredJellyfinServer> {
            val candidates = mutableListOf<String>()
            localIpv4Prefixes().take(2).forEach { prefix ->
                listOf(1, 2).forEach { host ->
                    candidates += "http://$prefix.$host:8096"
                }
            }

            return coroutineScope {
                candidates
                    .distinct()
                    .map { base ->
                        async {
                            withTimeoutOrNull(350) {
                                if (isJellyfinServer(base)) {
                                    DiscoveredJellyfinServer(
                                        address = base,
                                        name = "Jellyfin ($base)",
                                        id = base,
                                        endpointAddress = base,
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }
        }

        private fun isJellyfinServer(baseUrl: String): Boolean =
            try {
                val url = java.net.URL("$baseUrl/System/Info/Public")
                val conn =
                    (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 500
                        readTimeout = 500
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                    }
                conn.inputStream.use { stream ->
                    val body = stream.readBytes().toString(StandardCharsets.UTF_8)
                    conn.disconnect()
                    body.contains("ServerName", ignoreCase = true) ||
                        body.contains("Id", ignoreCase = true) ||
                        body.contains("Version", ignoreCase = true)
                }
            } catch (_: Exception) {
                false
            }

        private fun parseResponse(json: String): DiscoveredJellyfinServer? {
            return try {
                // Some servers wrap differently; try direct parse first
                val parsed = responseAdapter.fromJson(json) ?: return null
                val address = parsed.address?.takeIf { it.isNotBlank() } ?: return null
                DiscoveredJellyfinServer(
                    address = normalizeUrl(address),
                    name = parsed.name?.takeIf { it.isNotBlank() } ?: "Jellyfin Server",
                    id = parsed.id.orEmpty(),
                    endpointAddress = parsed.endpointAddress,
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun broadcastTargets(): List<InetAddress> {
            val targets = linkedSetOf<InetAddress>()
            try {
                targets += InetAddress.getByName("255.255.255.255")
            } catch (_: Exception) {
            }

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                for (ni in interfaces) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (addr in ni.interfaceAddresses) {
                        val broadcast = addr.broadcast ?: continue
                        targets += broadcast
                    }
                }
            } catch (_: Exception) {
            }

            // Also try subnet broadcast from active network link
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                ) {
                    val lp: LinkProperties? = cm.getLinkProperties(network)
                    lp?.linkAddresses?.forEach { la ->
                        val host = la.address.hostAddress ?: return@forEach
                        if (host.contains(':')) return@forEach // skip IPv6 here
                        val prefix = la.prefixLength
                        if (prefix in 1..30) {
                            subnetBroadcast(host, prefix)?.let { targets += it }
                        }
                    }
                }
            } catch (_: Exception) {
            }

            return targets.toList()
        }

        private fun localIpv4Prefixes(): List<String> {
            val prefixes = linkedSetOf<String>()
            try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { ni ->
                    if (!ni.isUp || ni.isLoopback) return@forEach
                    ni.inetAddresses.toList().forEach { addr ->
                        val host = addr.hostAddress ?: return@forEach
                        if (host.contains(':')) return@forEach
                        val parts = host.split('.')
                        if (parts.size == 4) {
                            prefixes += "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return prefixes.toList()
        }

        private fun subnetBroadcast(
            ip: String,
            prefixLength: Int,
        ): InetAddress? {
            return try {
                val parts = ip.split('.').map { it.toInt() }
                if (parts.size != 4) return null
                var addr = 0
                for (p in parts) addr = (addr shl 8) or (p and 0xff)
                val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
                val bcast = addr or mask.inv()
                val bytes =
                    byteArrayOf(
                        ((bcast ushr 24) and 0xff).toByte(),
                        ((bcast ushr 16) and 0xff).toByte(),
                        ((bcast ushr 8) and 0xff).toByte(),
                        (bcast and 0xff).toByte(),
                    )
                InetAddress.getByAddress(bytes)
            } catch (_: Exception) {
                null
            }
        }

        private fun normalizeUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            return url
        }

        private fun normalizeKey(address: String): String = normalizeUrl(address).lowercase().removeSuffix("/")

        companion object {
            const val DISCOVERY_PORT = 7359
            const val DISCOVERY_MESSAGE = "Who is JellyfinServer?"
        }
    }

@JsonClass(generateAdapter = true)
data class DiscoveredServerResponse(
    @Json(name = "Address") val address: String? = null,
    @Json(name = "Id") val id: String? = null,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "EndpointAddress") val endpointAddress: String? = null,
)

data class DiscoveredJellyfinServer(
    val address: String,
    val name: String,
    val id: String,
    val endpointAddress: String? = null,
)
