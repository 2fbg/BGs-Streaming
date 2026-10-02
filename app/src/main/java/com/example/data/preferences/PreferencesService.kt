package com.example.data.preferences

import android.content.Context
import com.example.data.service.PreferencesService as BasePreferencesService

/**
 * Serviço de preferências seguras usando EncryptedSharedPreferences e cache local.
 * Atende à camada data/preferences da arquitetura limpa.
 */
class PreferencesService(context: Context) {
    private val delegate = BasePreferencesService(context)

    var username: String
        get() = delegate.username
        set(value) { delegate.username = value }

    var password: String
        get() = delegate.password
        set(value) { delegate.password = value }

    var dynamicServersUrl: String
        get() = delegate.dynamicServersUrl
        set(value) { delegate.dynamicServersUrl = value }

    var cachedServersJson: String
        get() = delegate.cachedServersJson
        set(value) { delegate.cachedServersJson = value }

    var useExternalPlayer: Boolean
        get() = delegate.useExternalPlayer
        set(value) { delegate.useExternalPlayer = value }

    var externalPlayerType: String
        get() = delegate.externalPlayerType
        set(value) { delegate.externalPlayerType = value }

    var activePlaylistName: String
        get() = delegate.activePlaylistName
        set(value) { delegate.activePlaylistName = value }

    var useAmoledMode: Boolean
        get() = delegate.useAmoledMode
        set(value) { delegate.useAmoledMode = value }

    var menuSortOrder: String
        get() = delegate.menuSortOrder
        set(value) { delegate.menuSortOrder = value }

    fun isLicenseValid(): Boolean = delegate.isLicenseValid()
    fun getTrialDaysRemaining(): Int = delegate.getTrialDaysRemaining()
    val virtualMac: String get() = delegate.virtualMac
}
