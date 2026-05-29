package com.example.data.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.model.PlaylistItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.NetworkInterface
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class DLNADevice(
    val friendlyName: String,
    val controlUrl: String,
    val baseUrl: String,
    val ipAddress: String,
)

object LocalCastServer {
    private const val TAG = "LocalCastServer"
    private var serverSocket: ServerSocket? = null
    private var executorService: ExecutorService? = null
    
    var activeItem: PlaylistItem? = null
    var serverPort: Int = 8585
    var isRunning: Boolean = false
        private set

    // Remote sync control states
    var remoteIsPlaying: Boolean = true
    var remoteSeekRequest: Long = -1L // in milliseconds
    var remoteVolume: Float = 0.8f
    var tvCurrentTimeSeconds: Float = 0.0f

    // DLNA Active Connection States
    var dlnaControlUrl: String? = null
    var dlnaDeviceIp: String? = null

    @Synchronized
    fun startServer(context: Context, item: PlaylistItem): String? {
        activeItem = item
        if (isRunning && serverSocket != null) {
            return getCastUrl(context)
        }
        
        try {
            serverSocket = ServerSocket(serverPort)
            startAcceptLoop()
            return getCastUrl(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server on primary port $serverPort, trying fallback: ", e)
            try {
                serverPort = 9015
                serverSocket = ServerSocket(serverPort)
                startAcceptLoop()
                return getCastUrl(context)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed server fallback setup", ex)
            }
        }
        return null
    }

    private fun startAcceptLoop() {
        isRunning = true
        executorService = Executors.newFixedThreadPool(4)
        
        // Start main acceptance thread
        Thread {
            while (isRunning) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    executorService?.execute {
                        handleConnection(socket)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error accepting socket connection", e)
                    }
                    break
                }
            }
        }.start()
        Log.d(TAG, "Custom ServerSocket HTTP server started on port $serverPort")
    }

    private fun handleConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: ""
            // Read headers (we can ignore content but we must read until blank line)
            var line: String?
            while (true) {
                line = reader.readLine()
                if (line.isNullOrEmpty()) {
                    break
                }
            }

            val path = if (firstLine.startsWith("GET ")) {
                firstLine.substringAfter("GET ").substringBefore(" HTTP/")
            } else {
                "/"
            }

            // Intercept real-time REST endpoint for TV polling commands
            if (path.contains("api/status")) {
                val timeQuery = path.substringAfter("time=", "")
                if (timeQuery.isNotEmpty()) {
                    val actualTime = timeQuery.substringBefore("&").toFloatOrNull()
                    if (actualTime != null) {
                        tvCurrentTimeSeconds = actualTime
                    }
                }
                
                val item = activeItem
                val json = """
                    {
                        "playing": $remoteIsPlaying,
                        "seekTo": $remoteSeekRequest,
                        "volume": $remoteVolume,
                        "url": "${item?.url ?: ""}",
                        "name": "${item?.name ?: ""}"
                    }
                """.trimIndent()
                
                // Reset seek request immediately after serving it
                if (remoteSeekRequest >= 0) {
                    remoteSeekRequest = -1L
                }
                
                val jsonBytes = json.toByteArray(Charsets.UTF_8)
                val responseHeader = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${jsonBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                
                val outputStream = socket.getOutputStream()
                outputStream.write(responseHeader.toByteArray(Charsets.UTF_8))
                outputStream.write(jsonBytes)
                outputStream.flush()
                return
            }

            val item = activeItem
            val html = if (item == null) {
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>MK21 Web Receiver</title>
                    <meta charset="utf-8">
                    <style>
                        body { margin:0; background:#0e0e11; color:#fff; font-family:sans-serif; display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; }
                        h1 { color:#ffd700; font-size: 24px; }
                        p { color:#8e8e93; }
                    </style>
                </head>
                <body>
                    <h1>Nenhuma transmissão ativa</h1>
                    <p>Por favor, selecione um canal, filme ou série no aplicativo MK21 para espelhar.</p>
                </body>
                </html>
                """.trimIndent()
            } else {
                val cleanUrl = item.url.trim()
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>MK21 IPTV Cast: ${item.name}</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link href="https://vjs.zencdn.net/8.10.0/video-js.css" rel="stylesheet" />
                    <style>
                        body { 
                            margin: 0; 
                            background-color: #0c0c0e; 
                            color: #ffffff; 
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; 
                            display: flex; 
                            flex-direction: column; 
                            align-items: center; 
                            justify-content: center; 
                            min-height: 100vh; 
                            overflow-x: hidden;
                        }
                        .container {
                            width: 100%;
                            max-width: 900px;
                            padding: 20px;
                            box-sizing: border-box;
                            text-align: center;
                        }
                        h1 { 
                            font-size: 24px; 
                            font-weight: 800; 
                            color: #ffbc0d; 
                            margin-bottom: 5px; 
                            text-shadow: 0 2px 4px rgba(0,0,0,0.5);
                        }
                        .subtitle { 
                            color: #a1a1aa; 
                            font-size: 14px; 
                            margin-bottom: 25px; 
                        }
                        .video-wrapper {
                            position: relative;
                            width: 100%;
                            background: #000;
                            border-radius: 12px;
                            overflow: hidden;
                            box-shadow: 0 15px 40px rgba(0,0,0,0.8);
                            border: 1px solid rgba(255,188,13,0.15);
                        }
                        .video-js {
                            width: 100% !important;
                            height: 506px !important;
                        }
                        @media(max-width: 768px) {
                            .video-js {
                                height: 360px !important;
                            }
                        }
                        @media(max-width: 480px) {
                            .video-js {
                                height: 240px !important;
                            }
                        }
                        .info-footer {
                            margin-top: 25px;
                            font-size: 13px;
                            color: #71717a;
                        }
                        .brand {
                            font-weight: bold;
                            color: #ffbc0d;
                        }
                        .live-tag {
                            display: inline-block;
                            background-color: #ef4444;
                            color: white;
                            padding: 2px 8px;
                            border-radius: 4px;
                            font-size: 11px;
                            font-weight: bold;
                            text-transform: uppercase;
                            vertical-align: middle;
                            margin-right: 8px;
                            animation: pulse 1.5s infinite;
                        }
                        @keyframes pulse {
                            0% { opacity: 0.6; }
                            50% { opacity: 1; }
                            100% { opacity: 0.6; }
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>
                            ${if (item.contentType == "LIVE") "<span class='live-tag'>Ao Vivo</span>" else ""}
                            ${item.name}
                        </h1>
                        <div class="subtitle">Categoria: ${item.category} • MK21 Web Receiver</div>
                        
                        <div class="video-wrapper">
                            <video 
                                id="my-video" 
                                class="video-js vjs-default-skin vjs-big-play-centered" 
                                controls 
                                autoplay 
                                preload="auto" 
                                data-setup='{}'>
                                <source src="${cleanUrl}" type="application/x-mpegURL">
                                <source src="${cleanUrl}" type="video/mp4">
                                <source src="${cleanUrl}" type="video/webm">
                                Seu navegador não suporta reprodução direta de redes de transmissão.
                            </video>
                        </div>
                        
                        <div class="info-footer">
                            Sincronizado via Smart URL • Desenvolvido por <span class="brand">MK21 IPTV Cast</span>
                        </div>
                    </div>
                    
                    <script src="https://vjs.zencdn.net/8.10.0/video.min.js"></script>
                    <script>
                        document.addEventListener("DOMContentLoaded", function() {
                            var player = videojs('my-video');
                            var currentVideoUrl = "${cleanUrl}";
                            
                            function startPolling() {
                                setInterval(function() {
                                    var currentTime = player.currentTime() || 0;
                                    fetch('/api/status?time=' + currentTime)
                                        .then(response => response.json())
                                        .then(data => {
                                            // 1. Channel / video stream synchronization
                                            if (data.url && data.url !== currentVideoUrl) {
                                                console.log("Stream changed! Reloading video...");
                                                location.reload();
                                            }
                                            
                                            // 2. Play/Pause command sync
                                            if (data.playing === false && !player.paused()) {
                                                player.pause();
                                            } else if (data.playing === true && player.paused()) {
                                                player.play().catch(function(e) {
                                                    console.log("Autoplay block:", e);
                                                });
                                            }
                                            
                                            // 3. Time seek command sync
                                            if (data.seekTo >= 0) {
                                                var seekSecs = data.seekTo / 1000;
                                                player.currentTime(seekSecs);
                                            }
                                            
                                            // 4. Volume command sync
                                            if (typeof data.volume === 'number') {
                                                player.volume(data.volume);
                                            }
                                        })
                                        .catch(err => console.error("Error polling commands:", err));
                                }, 1000);
                            }
                            
                            player.ready(function() {
                                startPolling();
                            });
                        });
                    </script>
                </body>
                </html>
                """.trimIndent()
            }

            val htmlBytes = html.toByteArray(Charsets.UTF_8)
            val outputStream = socket.getOutputStream()
            
            val responseHeader = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${htmlBytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"

            outputStream.write(responseHeader.toByteArray(Charsets.UTF_8))
            outputStream.write(htmlBytes)
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client session", e)
        } finally {
            try {
                socket.close()
            } catch (ex: Exception) {
                // Ignore close error
            }
        }
    }

    @Synchronized
    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ServerSocket", e)
        }
        try {
            executorService?.shutdownNow()
            executorService = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing executors", e)
        }
        Log.d(TAG, "Server stopped successfully")
    }

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val ipAddress = wifiManager.connectionInfo.ipAddress
                if (ipAddress != 0) {
                    return String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wifi IP fetching error", e)
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network adapter IP fetching error", e)
        }
        return "192.168.1.100"
    }

    fun getCastUrl(context: Context): String {
        return "http://${getLocalIpAddress(context)}:$serverPort/"
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
                
                // Secondary fallback search for AVTransport services
                val queryAv = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"
                val bytesAv = queryAv.toByteArray()
                val packetAv = DatagramPacket(bytesAv, bytesAv.size, target, 1900)
                socket.send(packetAv)
                
                val receiveBuffer = ByteArray(8192)
                val startTime = System.currentTimeMillis()
                val discoveredLocations = mutableSetOf<String>()
                
                while (System.currentTimeMillis() - startTime < 3000) {
                    try {
                        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        
                        // Look for LOCATION: http://...
                        val locationLine = response.lines().firstOrNull { it.uppercase(Locale.US).startsWith("LOCATION:") }
                        if (locationLine != null) {
                            val locationUrl = locationLine.substringAfter(":").trim()
                            if (locationUrl.isNotEmpty() && discoveredLocations.add(locationUrl)) {
                                fetchDeviceDescription(locationUrl, onDeviceDiscovered)
                            }
                        }
                    } catch (e: java.io.InterruptedIOException) {
                        break // Timeout
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing SSDP raw packet", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSDP search crashed", e)
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {}
            }
        }.start()
    }

    private fun fetchDeviceDescription(locationUrl: String, onDeviceDiscovered: (DLNADevice) -> Unit) {
        try {
            val url = URL(locationUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            
            if (conn.responseCode == 200) {
                val xmlText = conn.inputStream.bufferedReader().use { it.readText() }
                
                var friendlyName = xmlText.substringAfter("<friendlyName>", "").substringBefore("</friendlyName>").trim()
                if (friendlyName.isEmpty()) {
                    friendlyName = "Smart TV/DLNA Player"
                }
                
                // Locate AVTransport control path
                val avTransportIndex = xmlText.indexOf("urn:schemas-upnp-org:service:AVTransport")
                var controlUrlSub = "/AVTransport/control" // standard default fallback path
                if (avTransportIndex != -1) {
                    val serviceBlock = xmlText.substring(avTransportIndex, xmlText.indexOf("</service>", avTransportIndex).coerceAtLeast(avTransportIndex))
                    val extractedCtrl = serviceBlock.substringAfter("<controlURL>", "").substringBefore("</controlURL>").trim()
                    if (extractedCtrl.isNotEmpty()) {
                        controlUrlSub = extractedCtrl
                    }
                }
                
                val uri = url.toURI()
                val resolvedControlUrl = if (controlUrlSub.startsWith("http://") || controlUrlSub.startsWith("https://")) {
                    controlUrlSub
                } else {
                    val base = "${uri.scheme}://${uri.host}:${uri.port}"
                    if (controlUrlSub.startsWith("/")) {
                        "$base$controlUrlSub"
                    } else {
                        "$base/$controlUrlSub"
                    }
                }
                
                val baseUrl = "${uri.scheme}://${uri.host}:${uri.port}/"
                onDeviceDiscovered(
                    DLNADevice(
                        friendlyName = friendlyName,
                        controlUrl = resolvedControlUrl,
                        baseUrl = baseUrl,
                        ipAddress = uri.host
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading device XML at $locationUrl", e)
        }
    }

    /**
     * Background direct IP sweep for Smart TV description files
     */
    fun probeManualDevice(ip: String, onResult: (Boolean, DLNADevice?) -> Unit) {
        Thread {
            val commonPorts = listOf(49152, 1800, 50244, 49153, 8012, 8008, 55000, 8200)
            var success = false
            var foundDevice: DLNADevice? = null
            
            for (port in commonPorts) {
                val urls = listOf(
                    "http://$ip:$port/dlna/description.xml",
                    "http://$ip:$port/description.xml",
                    "http://$ip:$port/xml/device_description.xml",
                    "http://$ip:$port/upnp/desc.xml",
                    "http://$ip:$port/dd.xml"
                )
                for (u in urls) {
                    try {
                        val url = URL(u)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 400
                        conn.readTimeout = 400
                        if (conn.responseCode == 200) {
                            val xmlText = conn.inputStream.bufferedReader().use { it.readText() }
                            if (xmlText.contains("MediaRenderer") || xmlText.contains("AVTransport") || xmlText.contains("avtransport")) {
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
                                
                                foundDevice = DLNADevice(
                                    friendlyName = friendlyName,
                                    controlUrl = resolved,
                                    baseUrl = "http://$ip:$port/",
                                    ipAddress = ip
                                )
                                success = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and try next
                    }
                }
                if (success) break
            }
            
            if (!success) {
                // If standard SSDP is blocked or port scanning didn't identify XML, pre-build standard TV endpoints
                val fallbackPort = 49152
                val fallbackControlUrl = "http://$ip:$fallbackPort/upnp/control/AVTransport"
                foundDevice = DLNADevice(
                    friendlyName = "Smart TV ($ip)",
                    controlUrl = fallbackControlUrl,
                    baseUrl = "http://$ip:$fallbackPort/",
                    ipAddress = ip
                )
                onResult(true, foundDevice)
            } else {
                onResult(true, foundDevice)
            }
        }.start()
    }

    /**
     * Send direct media URL block to TV via UPnP DLNA SOAP Request
     */
    fun castUrlToDlna(controlUrl: String, streamUrl: String, title: String) {
        Thread {
            try {
                // SOAP Envelope format for SetAVTransportURI
                val soapSetUri = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                        <s:Body>
                            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                                <InstanceID>0</InstanceID>
                                <CurrentURI>$streamUrl</CurrentURI>
                                <CurrentURIMetaData><![CDATA[<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="false"><dc:title>$title</dc:title><upnp:class>object.item.videoItem.movie</upnp:class><res protocolInfo="http-get:*:video/mp4:*">$streamUrl</res></item></DIDL-Lite>]]></CurrentURIMetaData>
                            </u:SetAVTransportURI>
                        </s:Body>
                    </s:Envelope>
                """.trimIndent()
                
                sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", soapSetUri)
                
                // Brief pause to allow the renderer buffers to register the new media endpoint
                Thread.sleep(800)
                
                // Play Action to start streaming the newly set media URI
                val soapPlay = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                        <s:Body>
                            <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                                <InstanceID>0</InstanceID>
                                <Speed>1</Speed>
                            </u:Play>
                        </s:Body>
                    </s:Envelope>
                """.trimIndent()
                
                sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play", soapPlay)
                Log.d(TAG, "Cast commands fired successfully to DLNA target: $controlUrl")
            } catch (e: Exception) {
                Log.e(TAG, "DLNA Cast sequence failed", e)
            }
        }.start()
    }

    /**
     * Pause DLNA Media Playback
     */
    fun pauseDlna(controlUrl: String) {
        Thread {
            val soapPause = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                        </u:Pause>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Pause", soapPause)
        }.start()
    }

    /**
     * Resume DLNA Media Playback
     */
    fun resumeDlna(controlUrl: String) {
        Thread {
            val soapPlay = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                            <Speed>1</Speed>
                        </u:Play>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play", soapPlay)
        }.start()
    }

    /**
     * Seek to a specific timestamp in seconds
     */
    fun seekDlna(controlUrl: String, positionSeconds: Long) {
        Thread {
            val hh = positionSeconds / 3600
            val mm = (positionSeconds % 3600) / 60
            val ss = positionSeconds % 60
            val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss)
            
            val soapSeek = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                            <Unit>REL_TIME</Unit>
                            <Target>$timeStr</Target>
                        </u:Seek>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Seek", soapSeek)
        }.start()
    }

    /**
     * Stop DLNA Media Stream completely
     */
    fun stopDlna(controlUrl: String) {
        Thread {
            val soapStop = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                        </u:Stop>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Stop", soapStop)
        }.start()
    }

    private fun sendSoapAction(controlUrl: String, soapAction: String, xmlPayload: String) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(controlUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2500
            conn.readTimeout = 3000
            conn.doOutput = true
            
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPACTION", "\"$soapAction\"")
            
            val outputBytes = xmlPayload.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", outputBytes.size.toString())
            
            conn.outputStream.use { os ->
                os.write(outputBytes)
                os.flush()
            }
            
            val code = conn.responseCode
            if (code >= 200 && code < 300) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "UPnP SOAP success code $code Action $soapAction")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "UPnP SOAP failure $code: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOAP HTTP POST request failed to $controlUrl: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }
}

