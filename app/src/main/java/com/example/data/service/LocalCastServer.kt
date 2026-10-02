package com.example.data.service

import android.content.Context
import com.example.data.model.PlaylistItem

/**
 * Backward compatibility facade delegating to modularized Cast components.
 */
object LocalCastServer {
    val httpServer = LocalHttpServer()
    val discovery = DlnaDiscoveryService()
    val soapClient = DlnaSoapClient()

    var activeItem: PlaylistItem?
        get() = httpServer.activeItem
        set(value) { httpServer.activeItem = value }

    var serverPort: Int
        get() = httpServer.serverPort
        set(value) { httpServer.serverPort = value }

    val isRunning: Boolean
        get() = httpServer.isRunning

    var remoteIsPlaying: Boolean
        get() = httpServer.remoteIsPlaying
        set(value) { httpServer.remoteIsPlaying = value }

    var remoteSeekRequest: Long
        get() = httpServer.remoteSeekRequest
        set(value) { httpServer.remoteSeekRequest = value }

    var remoteVolume: Float
        get() = httpServer.remoteVolume
        set(value) { httpServer.remoteVolume = value }

    var tvCurrentTimeSeconds: Float
        get() = httpServer.tvCurrentTimeSeconds
        set(value) { httpServer.tvCurrentTimeSeconds = value }

    var dlnaControlUrl: String? = null
    var dlnaDeviceIp: String? = null

    fun startServer(context: Context, item: PlaylistItem): String? =
        httpServer.startServer(context, item)

    fun stopServer() =
        httpServer.stopServer()

    fun getLocalIpAddress(context: Context): String =
        httpServer.getLocalIpAddress(context)

    fun getCastUrl(context: Context): String =
        httpServer.getCastUrl(context)

    fun discoverDlnaDevices(onDeviceDiscovered: (DLNADevice) -> Unit) =
        discovery.discoverDlnaDevices(onDeviceDiscovered)

    fun probeManualDevice(ip: String, onResult: (Boolean, DLNADevice?) -> Unit) =
        discovery.probeManualDevice(ip, onResult)

    fun castUrlToDlna(controlUrl: String, streamUrl: String, title: String) =
        soapClient.castUrlToDlna(controlUrl, streamUrl, title)

    fun pauseDlna(controlUrl: String) =
        soapClient.pauseDlna(controlUrl)

    fun resumeDlna(controlUrl: String) =
        soapClient.resumeDlna(controlUrl)

    fun seekDlna(controlUrl: String, positionSeconds: Long) =
        soapClient.seekDlna(controlUrl, positionSeconds)

    fun stopDlna(controlUrl: String) =
        soapClient.stopDlna(controlUrl)
}
