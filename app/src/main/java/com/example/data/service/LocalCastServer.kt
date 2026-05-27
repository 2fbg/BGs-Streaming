package com.example.data.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.model.PlaylistItem
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.Executors

object LocalCastServer {
    private const val TAG = "LocalCastServer"
    private var server: HttpServer? = null
    var activeItem: PlaylistItem? = null
    var serverPort: Int = 8585
    var isRunning: Boolean = false
        private set

    fun startServer(context: Context, item: PlaylistItem): String? {
        activeItem = item
        if (server != null) {
            return getCastUrl(context)
        }
        try {
            server = HttpServer.create(InetSocketAddress(serverPort), 0)
            server?.createContext("/", CastHtmlHandler())
            server?.executor = Executors.newSingleThreadExecutor()
            server?.start()
            isRunning = true
            Log.d(TAG, "Server started on port $serverPort")
            return getCastUrl(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server: ", e)
            try {
                // Try alternate port if 8585 is bound
                serverPort = 9015
                server = HttpServer.create(InetSocketAddress(serverPort), 0)
                server?.createContext("/", CastHtmlHandler())
                server?.executor = Executors.newSingleThreadExecutor()
                server?.start()
                isRunning = true
                Log.d(TAG, "Server started on alternate port $serverPort")
                return getCastUrl(context)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed server creation fallback", ex)
            }
        }
        return null
    }

    fun stopServer() {
        try {
            server?.stop(0)
            server = null
            isRunning = false
            Log.d(TAG, "Server stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
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

    private class CastHtmlHandler : HttpHandler {
        override fun handle(t: HttpExchange) {
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
                </body>
                </html>
                """.trimIndent()
            }
            
            val responseBytes = html.toByteArray(Charsets.UTF_8)
            t.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            t.sendResponseHeaders(200, responseBytes.size.toLong())
            val os: OutputStream = t.responseBody
            try {
                os.write(responseBytes)
            } finally {
                os.close()
            }
        }
    }
}
