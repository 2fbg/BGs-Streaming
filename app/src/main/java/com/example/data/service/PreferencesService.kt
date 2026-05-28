package com.example.data.service

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
        get() {
            val raw = try { prefs.getString(KEY_USERNAME, "") ?: "" } catch (e: Throwable) { "" }
            if (raw.isEmpty()) {
                val defaultUser = "usuario_demo"
                val encrypted = KeystoreHelper.encrypt(defaultUser)
                try { prefs.edit().putString(KEY_USERNAME, encrypted).apply() } catch (e: Throwable) {}
                return defaultUser
            }
            if (!raw.startsWith("v1:") && !raw.startsWith("fallback:")) {
                // Migration: raw is old plaintext. Encrypt and save.
                val encrypted = KeystoreHelper.encrypt(raw)
                try { prefs.edit().putString(KEY_USERNAME, encrypted).apply() } catch (e: Throwable) {}
                return raw
            }
            return KeystoreHelper.decrypt(raw)
        }
        set(value) {
            val encrypted = KeystoreHelper.encrypt(value)
            try { prefs.edit().putString(KEY_USERNAME, encrypted).apply() } catch (e: Throwable) {}
        }

    var password: String
        get() {
            val raw = try { prefs.getString(KEY_PASSWORD, "") ?: "" } catch (e: Throwable) { "" }
            if (raw.isEmpty()) {
                val defaultPass = "senha_demo"
                val encrypted = KeystoreHelper.encrypt(defaultPass)
                try { prefs.edit().putString(KEY_PASSWORD, encrypted).apply() } catch (e: Throwable) {}
                return defaultPass
            }
            if (!raw.startsWith("v1:") && !raw.startsWith("fallback:")) {
                // Migration: raw is old plaintext. Encrypt and save.
                val encrypted = KeystoreHelper.encrypt(raw)
                try { prefs.edit().putString(KEY_PASSWORD, encrypted).apply() } catch (e: Throwable) {}
                return raw
            }
            return KeystoreHelper.decrypt(raw)
        }
        set(value) {
            val encrypted = KeystoreHelper.encrypt(value)
            try { prefs.edit().putString(KEY_PASSWORD, encrypted).apply() } catch (e: Throwable) {}
        }

    var activeServerId: String
        get() = try { prefs.getString(KEY_ACTIVE_SERVER_ID, "server_1") ?: "server_1" } catch (e: Throwable) { "server_1" }
        set(value) { try { prefs.edit().putString(KEY_ACTIVE_SERVER_ID, value).apply() } catch (e: Throwable) {} }

    var activePlaylistName: String
        get() = try { prefs.getString(KEY_ACTIVE_PLAYLIST_NAME, "VLOG") ?: "VLOG" } catch (e: Throwable) { "VLOG" }
        set(value) { try { prefs.edit().putString(KEY_ACTIVE_PLAYLIST_NAME, value).apply() } catch (e: Throwable) {} }

    var adultPin: String
        get() = try { prefs.getString(KEY_ADULT_PIN, "0000") ?: "0000" } catch (e: Throwable) { "0000" }
        set(value) { try { prefs.edit().putString(KEY_ADULT_PIN, value).apply() } catch (e: Throwable) {} }

    var useSameCredentialsForServers: Boolean
        get() = try { prefs.getBoolean(KEY_USE_SAME_CREDENTIALS, true) } catch (e: Throwable) { true }
        set(value) { try { prefs.edit().putBoolean(KEY_USE_SAME_CREDENTIALS, value).apply() } catch (e: Throwable) {} }

    // New configuration properties for fully functional settings screen
    var hideLiveCategories: Boolean
        get() = try { prefs.getBoolean("hide_live_categories", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("hide_live_categories", value).apply() } catch (e: Throwable) {} }

    var useExternalPlayer: Boolean
        get() = try { prefs.getBoolean("use_external_player", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("use_external_player", value).apply() } catch (e: Throwable) {} }

    var externalPlayerType: String
        get() = try { prefs.getString("external_player_type", "Qualquer Player") ?: "Qualquer Player" } catch (e: Throwable) { "Qualquer Player" }
        set(value) { try { prefs.edit().putString("external_player_type", value).apply() } catch (e: Throwable) {} }

    var deviceType: String
        get() = try { prefs.getString("device_type", "Celular / Tablet") ?: "Celular / Tablet" } catch (e: Throwable) { "Celular / Tablet" }
        set(value) { try { prefs.edit().putString("device_type", value).apply() } catch (e: Throwable) {} }

    var appLanguage: String
        get() = try { prefs.getString("app_language", "Português") ?: "Português" } catch (e: Throwable) { "Português" }
        set(value) { try { prefs.edit().putString("app_language", value).apply() } catch (e: Throwable) {} }

    var timeFormat: String
        get() = try { prefs.getString("time_format", "24 horas") ?: "24 horas" } catch (e: Throwable) { "24 horas" }
        set(value) { try { prefs.edit().putString("time_format", value).apply() } catch (e: Throwable) {} }

    var appLayout: String
        get() = try { prefs.getString("app_layout", "Grid Clássico") ?: "Grid Clássico" } catch (e: Throwable) { "Grid Clássico" }
        set(value) { try { prefs.edit().putString("app_layout", value).apply() } catch (e: Throwable) {} }

    var liveStreamFormat: String
        get() = try { prefs.getString("live_stream_format", "MPEG-TS (.ts)") ?: "MPEG-TS (.ts)" } catch (e: Throwable) { "MPEG-TS (.ts)" }
        set(value) { try { prefs.edit().putString("live_stream_format", value).apply() } catch (e: Throwable) {} }

    var subtitleConfig: String
        get() = try { prefs.getString("subtitle_config", "Média (Padrão)") ?: "Média (Padrão)" } catch (e: Throwable) { "Média (Padrão)" }
        set(value) { try { prefs.edit().putString("subtitle_config", value).apply() } catch (e: Throwable) {} }

    var menuSortOrder: String
        get() = try { prefs.getString("menu_sort_order", "Ordem por adição") ?: "Ordem por adição" } catch (e: Throwable) { "Ordem por adição" }
        set(value) { try { prefs.edit().putString("menu_sort_order", value).apply() } catch (e: Throwable) {} }

    // Offline Licensing & 5-Day Trial Control properties
    var trialStartDate: Long
        get() = try { prefs.getLong("trial_start_date", 0L) } catch (e: Throwable) { 0L }
        set(value) { try { prefs.edit().putLong("trial_start_date", value).apply() } catch (e: Throwable) {} }

    var activationKey: String
        get() = try { prefs.getString("activation_key", "") ?: "" } catch (e: Throwable) { "" }
        set(value) { try { prefs.edit().putString("activation_key", value).apply() } catch (e: Throwable) {} }

    val deviceId: String by lazy {
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "MK21DEVICEID"
            androidId
        } catch (e: Throwable) {
            "MK21DEVICEID"
        }
    }

    val virtualMac: String by lazy {
        val cleanId = deviceId.replace("[^A-Fa-f0-9]".toRegex(), "").padEnd(12, 'F').take(12).uppercase()
        cleanId.chunked(2).joinToString(":")
    }

    fun generateValidKeyForDevice(deviceMac: String): String {
        val salt = "MK21_GOLDEN_SALT_2026"
        val rawInput = deviceMac.uppercase().trim() + salt
        val md5 = java.security.MessageDigest.getInstance("MD5")
        val hashBytes = md5.digest(rawInput.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02X", b))
        }
        val fullHash = sb.toString()
        val p1 = fullHash.take(4)
        val p2 = fullHash.substring(4, 8)
        val p3 = fullHash.substring(8, 12)
        return "MK-$p1-$p2-$p3"
    }

    fun isLicenseValid(): Boolean {
        val key = activationKey
        if (key.isEmpty()) return false
        val expected = generateValidKeyForDevice(virtualMac)
        return key.uppercase().trim() == expected
    }

    fun getTrialDaysRemaining(): Int {
        if (isLicenseValid()) return 9999
        val start = trialStartDate
        if (start == 0L) return 5
        val now = System.currentTimeMillis()
        val elapsedMs = now - start
        val fiveDaysMs = 5 * 24 * 60 * 60 * 1000L
        val remainingMs = fiveDaysMs - elapsedMs
        if (remainingMs <= 0) return 0
        return (remainingMs / (24 * 60 * 60 * 1000L)).toInt().coerceIn(0, 5)
    }

    fun setLastPlaylistUpdateTimestamp(playlistName: String, timestamp: Long) {
        try { prefs.edit().putLong(KEY_LAST_UPDATE_PREFIX + playlistName, timestamp).apply() } catch (e: Throwable) {}
    }

    fun getLastPlaylistUpdateTimestamp(playlistName: String): Long {
        return try { prefs.getLong(KEY_LAST_UPDATE_PREFIX + playlistName, 0L) } catch (e: Throwable) { 0L }
    }

    var cachedServersJson: String
        get() = try { prefs.getString("cached_servers_json", "") ?: "" } catch (e: Throwable) { "" }
        set(value) { try { prefs.edit().putString("cached_servers_json", value).apply() } catch (e: Throwable) {} }

    var dynamicServersUrl: String
        get() = try { prefs.getString("dynamic_servers_url", "https://raw.githubusercontent.com/2fbg/BGs-Streaming/main/servers.json") ?: "https://raw.githubusercontent.com/2fbg/BGs-Streaming/main/servers.json" } catch (e: Throwable) { "https://raw.githubusercontent.com/2fbg/BGs-Streaming/main/servers.json" }
        set(value) { try { prefs.edit().putString("dynamic_servers_url", value).apply() } catch (e: Throwable) {} }

    var loadLiveInForeground: Boolean
        get() = try { prefs.getBoolean("load_live_foreground", true) } catch (e: Throwable) { true }
        set(value) { try { prefs.edit().putBoolean("load_live_foreground", value).apply() } catch (e: Throwable) {} }

    var loadMoviesInForeground: Boolean
        get() = try { prefs.getBoolean("load_movies_foreground", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("load_movies_foreground", value).apply() } catch (e: Throwable) {} }

    var loadSeriesInForeground: Boolean
        get() = try { prefs.getBoolean("load_series_foreground", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("load_series_foreground", value).apply() } catch (e: Throwable) {} }

    var syncIntervalFrequency: String
        get() = try { prefs.getString("sync_interval_frequency", "Uma vez ao dia") ?: "Uma vez ao dia" } catch (e: Throwable) { "Uma vez ao dia" }
        set(value) { try { prefs.edit().putString("sync_interval_frequency", value).apply() } catch (e: Throwable) {} }

    var syncAllListsBackground: Boolean
        get() = try { prefs.getBoolean("sync_all_lists_background", true) } catch (e: Throwable) { true }
        set(value) { try { prefs.edit().putBoolean("sync_all_lists_background", value).apply() } catch (e: Throwable) {} }

    var hideBackgroundProgress: Boolean
        get() = try { prefs.getBoolean("hide_background_progress", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("hide_background_progress", value).apply() } catch (e: Throwable) {} }

    fun isCredentialsConfigured(predefinedNames: Set<String>): Boolean {
        val isPredefined = predefinedNames.contains(activePlaylistName)
        return activePlaylistName.isNotEmpty() && (
            !isPredefined || (username.isNotEmpty() && password.isNotEmpty())
        )
    }
}

object KeystoreHelper {
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"

    // A secure static 128-bit key for local software encryption
    private val keyBytes = byteArrayOf(
        0x4D, 0x4B, 0x32, 0x31, 0x53, 0x65, 0x63, 0x75, // "MK21Secu"
        0x72, 0x65, 0x50, 0x61, 0x73, 0x73, 0x77, 0x64  // "rePasswd"
    )
    private val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, ALGORITHM)

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv ?: ByteArray(16)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            return "v1:$ivBase64.$encryptedBase64"
        } catch (e: Throwable) {
            android.util.Log.e("KeystoreHelper", "Encryption failed, falling back to base64 encoding", e)
        }
        
        // Fallback: Simple Base64 encoding prefixed with "fallback:" so we don't crash
        return try {
            val base64 = Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "fallback:$base64"
        } catch (e: Throwable) {
            ""
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        
        // Check if it's the premium V1 software key encryption format
        if (cipherText.startsWith("v1:")) {
            try {
                val securePart = cipherText.substring(3)
                if (securePart.contains(".")) {
                    val parts = securePart.split(".")
                    if (parts.size >= 2) {
                        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                        val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                        
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
                        val decryptedBytes = cipher.doFinal(encryptedBytes)
                        return String(decryptedBytes, Charsets.UTF_8)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("KeystoreHelper", "Decryption failed, seeking fallback", e)
            }
        }
        
        // Check if it is the fallback format
        if (cipherText.startsWith("fallback:")) {
            try {
                val base64Part = cipherText.substring(9)
                val decodedBytes = Base64.decode(base64Part, Base64.NO_WRAP)
                return String(decodedBytes, Charsets.UTF_8)
            } catch (e: Throwable) {
                android.util.Log.e("KeystoreHelper", "Fallback decoding failed", e)
            }
        }
        
        return cipherText
    }
}
