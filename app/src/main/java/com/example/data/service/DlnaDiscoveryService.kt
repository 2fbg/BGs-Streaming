package com.example.data.service

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors

class DlnaDiscoveryService {
    companion object {
        private const val TAG = "DlnaDiscoveryService"
    }

    /**
     * Scan the local network for DLNA renderers using SSDP M-SEARCH protocol
     */
    fun discoverDlnaDevices(onDeviceDiscovered: (DLNADevice) -> Unit) {
        Thread {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 2500
                val target = InetAddress.getByName("239.255.255.250")

                // M-SEARCH query for MediaRenderer:1 (standard for televisions and set-top boxes)
                val query = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

                val bytes = query.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, target, 1900)
                socket.send(packet)

                val buffer = ByteArray(4096)
                val responsePacket = DatagramPacket(buffer, buffer.size)

                val discoveredLocations = mutableSetOf<String>()
                val startTime = System.currentTimeMillis()

                // Listen for unicast replies for 2.5 seconds
                while (System.currentTimeMillis() - startTime < 2500) {
                    try {
                        socket.receive(responsePacket)
                        val response = String(responsePacket.data, 0, responsePacket.length)
                        val locationLine = response.lines().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        if (locationLine != null) {
                            val locationUrl = locationLine.substringAfter(":").trim()
                            if (discoveredLocations.add(locationUrl)) {
                                fetchDeviceDescription(locationUrl, responsePacket.address.hostAddress ?: "") { device ->
                                    if (device != null) {
                                        onDeviceDiscovered(device)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // socket timeout or done
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSDP Discovery exception", e)
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // Ignore close
                }
            }
        }.start()
    }

    /**
     * Fetch XML device description to find FriendlyName and AVTransport ControlURL
     */
    fun fetchDeviceDescription(descUrl: String, ip: String, callback: (DLNADevice?) -> Unit) {
        Thread {
            try {
                val url = URL(descUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                if (conn.responseCode == 200) {
                    val xml = conn.inputStream.bufferedReader().use { it.readText() }
                    var friendlyName = xml.substringAfter("<friendlyName>", "").substringBefore("</friendlyName>").trim()
                    if (friendlyName.isEmpty()) {
                        friendlyName = "Smart TV ($ip)"
                    }

                    // Look for AVTransport service block
                    val avTransportIndex = xml.indexOf("urn:schemas-upnp-org:service:AVTransport")
                    var controlUrl = "/AVTransport/control"
                    if (avTransportIndex != -1) {
                        val serviceBlock = xml.substring(avTransportIndex, xml.indexOf("</service>", avTransportIndex).coerceAtLeast(avTransportIndex))
                        val extracted = serviceBlock.substringAfter("<controlURL>", "").substringBefore("</controlURL>").trim()
                        if (extracted.isNotEmpty()) {
                            controlUrl = extracted
                        }
                    }

                    // Resolve relative URLs against baseUrl
                    val baseUrl = "${url.protocol}://${url.host}:${url.port}"
                    val resolvedControlUrl = if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) {
                        controlUrl
                    } else {
                        baseUrl + (if (controlUrl.startsWith("/")) "" else "/") + controlUrl
                    }

                    val device = DLNADevice(
                        friendlyName = friendlyName,
                        controlUrl = resolvedControlUrl,
                        baseUrl = "$baseUrl/",
                        ipAddress = ip
                    )
                    callback(device)
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                callback(null)
            }
        }.start()
    }

    /**
     * Background direct IP sweep for Smart TV description files (Parallelized for maximum speed)
     */
    fun probeManualDevice(ip: String, onResult: (Boolean, DLNADevice?) -> Unit) {
        val commonPorts = listOf(49152, 1800, 50244, 49153, 8012, 8008, 55000, 8200)
        val executor = Executors.newFixedThreadPool(16)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val activeTasks = java.util.concurrent.atomic.AtomicInteger(0)

        for (port in commonPorts) {
            val urls = listOf(
                "http://$ip:$port/dlna/description.xml",
                "http://$ip:$port/description.xml",
                "http://$ip:$port/xml/device_description.xml",
                "http://$ip:$port/upnp/desc.xml",
                "http://$ip:$port/dd.xml"
            )
            for (u in urls) {
                activeTasks.incrementAndGet()
                executor.submit {
                    try {
                        if (!finished.get()) {
                            val url = URL(u)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 600
                            conn.readTimeout = 600
                            if (conn.responseCode == 200) {
                                val xmlText = conn.inputStream.bufferedReader().use { it.readText() }
                                if (xmlText.contains("MediaRenderer") || xmlText.contains("AVTransport") || xmlText.contains("avtransport")) {
                                    if (finished.compareAndSet(false, true)) {
                                        var friendlyName = xmlText.substringAfter("<friendlyName>", "").substringBefore("</friendlyName>").trim()
                                        if (friendlyName.isEmpty()) friendlyName = "Smart TV ($ip)"

                                        val avTransportIndex = xmlText.indexOf("urn:schemas-upnp-org:service:AVTransport")
                                        var controlUrl = "/AVTransport/control"
                                        if (avTransportIndex != -1) {
                                            val serviceBlock = xmlText.substring(avTransportIndex, xmlText.indexOf("</service>", avTransportIndex).coerceAtLeast(avTransportIndex))
                                            val extracted = serviceBlock.substringAfter("<controlURL>", "").substringBefore("</controlURL>").trim()
                                            if (extracted.isNotEmpty()) {
                                                controlUrl = extracted
                                            }
                                        }

                                        val resolved = if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) {
                                            controlUrl
                                        } else {
                                            "http://$ip:$port" + (if (controlUrl.startsWith("/")) "" else "/") + controlUrl
                                        }

                                        val foundDevice = DLNADevice(
                                            friendlyName = friendlyName,
                                            controlUrl = resolved,
                                            baseUrl = "http://$ip:$port/",
                                            ipAddress = ip
                                        )
                                        executor.shutdownNow()
                                        onResult(true, foundDevice)
                                        return@submit
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    } finally {
                        if (activeTasks.decrementAndGet() == 0 && !finished.get()) {
                            if (finished.compareAndSet(false, true)) {
                                executor.shutdown()
                                // Fallback standard TV endpoints if none successfully replied
                                val fallbackPort = 49152
                                val fallbackControlUrl = "http://$ip:$fallbackPort/upnp/control/AVTransport"
                                val fallbackDevice = DLNADevice(
                                    friendlyName = "Smart TV ($ip)",
                                    controlUrl = fallbackControlUrl,
                                    baseUrl = "http://$ip:$fallbackPort/",
                                    ipAddress = ip
                                )
                                onResult(true, fallbackDevice)
                            }
                        }
                    }
                }
            }
        }
    }
}
