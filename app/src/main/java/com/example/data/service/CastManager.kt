package com.example.data.service

import android.content.Context
import com.example.data.model.PlaylistItem

class CastManager(private val context: Context) {
    val httpServer = LocalHttpServer()
    val discovery = DlnaDiscoveryService()
    val soapClient = DlnaSoapClient()

    fun startServer(item: PlaylistItem): String? {
        return httpServer.startServer(context, item)
    }

    fun stopServer() {
        httpServer.stopServer()
    }

    fun getCastUrl(): String {
        return httpServer.getCastUrl(context)
    }

    fun discoverDlnaDevices(onDeviceDiscovered: (DLNADevice) -> Unit) {
        discovery.discoverDlnaDevices(onDeviceDiscovered)
    }

    fun probeManualDevice(ip: String, onResult: (Boolean, DLNADevice?) -> Unit) {
        discovery.probeManualDevice(ip, onResult)
    }

    fun castUrlToDlna(controlUrl: String, streamUrl: String, title: String) {
        soapClient.castUrlToDlna(controlUrl, streamUrl, title)
    }

    fun pauseDlna(controlUrl: String) {
        soapClient.pauseDlna(controlUrl)
    }

    fun resumeDlna(controlUrl: String) {
        soapClient.resumeDlna(controlUrl)
    }

    fun seekDlna(controlUrl: String, positionSeconds: Long) {
        soapClient.seekDlna(controlUrl, positionSeconds)
    }

    fun stopDlna(controlUrl: String) {
        soapClient.stopDlna(controlUrl)
    }
}
