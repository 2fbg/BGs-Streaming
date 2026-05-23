package com.example.data.service

import android.content.Context
import android.content.SharedPreferences

class PreferencesService(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mk21_pref_store", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
        private const val KEY_ACTIVE_PLAYLIST_NAME = "active_playlist_name"
        private const val KEY_ADULT_PIN = "adult_pin"
        private const val KEY_LAST_UPDATE_PREFIX = "last_list_update_"
        
        // Let's also store whether standard server credentials should override manual lists
        private const val KEY_USE_SAME_CREDENTIALS = "use_same_credentials"
    }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var activeServerId: String
        get() = prefs.getString(KEY_ACTIVE_SERVER_ID, "server_1") ?: "server_1"
        set(value) = prefs.edit().putString(KEY_ACTIVE_SERVER_ID, value).apply()

    var activePlaylistName: String
        get() = prefs.getString(KEY_ACTIVE_PLAYLIST_NAME, "VLOG") ?: "VLOG"
        set(value) = prefs.edit().putString(KEY_ACTIVE_PLAYLIST_NAME, value).apply()

    var adultPin: String
        get() = prefs.getString(KEY_ADULT_PIN, "0000") ?: "0000"
        set(value) = prefs.edit().putString(KEY_ADULT_PIN, value).apply()

    var useSameCredentialsForServers: Boolean
        get() = prefs.getBoolean(KEY_USE_SAME_CREDENTIALS, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_SAME_CREDENTIALS, value).apply()

    fun setLastPlaylistUpdateTimestamp(playlistName: String, timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_PREFIX + playlistName, timestamp).apply()
    }

    fun getLastPlaylistUpdateTimestamp(playlistName: String): Long {
        return prefs.getLong(KEY_LAST_UPDATE_PREFIX + playlistName, 0L)
    }

    fun isCredentialsConfigured(predefinedNames: Set<String>): Boolean {
        val isPredefined = predefinedNames.contains(activePlaylistName)
        return activePlaylistName.isNotEmpty() && (
            !isPredefined || (username.isNotEmpty() && password.isNotEmpty())
        )
    }
}
