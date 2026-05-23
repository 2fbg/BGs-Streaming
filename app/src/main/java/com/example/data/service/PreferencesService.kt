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
            val raw = prefs.getString(KEY_USERNAME, "") ?: ""
            if (raw.isEmpty()) return ""
            if (!raw.startsWith("v1:") && !raw.startsWith("fallback:") && !raw.contains(".")) {
                // Migration: raw is old plaintext. Encrypt and save.
                val encrypted = KeystoreHelper.encrypt(raw)
                prefs.edit().putString(KEY_USERNAME, encrypted).apply()
                return raw
            }
            return KeystoreHelper.decrypt(raw)
        }
        set(value) {
            val encrypted = KeystoreHelper.encrypt(value)
            prefs.edit().putString(KEY_USERNAME, encrypted).apply()
        }

    var password: String
        get() {
            val raw = prefs.getString(KEY_PASSWORD, "") ?: ""
            if (raw.isEmpty()) return ""
            if (!raw.startsWith("v1:") && !raw.startsWith("fallback:") && !raw.contains(".")) {
                // Migration: raw is old plaintext. Encrypt and save.
                val encrypted = KeystoreHelper.encrypt(raw)
                prefs.edit().putString(KEY_PASSWORD, encrypted).apply()
                return raw
            }
            return KeystoreHelper.decrypt(raw)
        }
        set(value) {
            val encrypted = KeystoreHelper.encrypt(value)
            prefs.edit().putString(KEY_PASSWORD, encrypted).apply()
        }

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

object KeystoreHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "MK21SecureKeyAlias"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            android.util.Log.e("KeystoreHelper", "Failed to initialize Keystore", e)
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            android.util.Log.e("KeystoreHelper", "getSecretKey failed", e)
            null
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val secretKey = getSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val iv = cipher.iv
                val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                return "v1:$ivBase64.$encryptedBase64"
            }
        } catch (e: Exception) {
            android.util.Log.e("KeystoreHelper", "Encryption failed, falling back to base64 encoding", e)
        }
        
        // Fallback: Simple Base64 encoding prefixed with "fallback:" so we don't crash
        return try {
            val base64 = Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "fallback:$base64"
        } catch (e: Exception) {
            ""
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        
        // Check if it's the premium V1 Keystore encryption format
        if (cipherText.startsWith("v1:")) {
            try {
                val securePart = cipherText.substring(3)
                if (securePart.contains(".")) {
                    val parts = securePart.split(".")
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val secretKey = getSecretKey()
                    if (secretKey != null) {
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        val spec = GCMParameterSpec(128, iv)
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                        val decryptedBytes = cipher.doFinal(encryptedBytes)
                        return String(decryptedBytes, Charsets.UTF_8)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KeystoreHelper", "Decryption failed, seeking fallback", e)
            }
        }
        
        // Check if it is the fallback format
        if (cipherText.startsWith("fallback:")) {
            try {
                val base64Part = cipherText.substring(9)
                val decodedBytes = Base64.decode(base64Part, Base64.NO_WRAP)
                return String(decodedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                android.util.Log.e("KeystoreHelper", "Fallback decoding failed", e)
            }
        }
        
        // If it was the old raw encrypted block (pre-prefix change)
        if (cipherText.contains(".") && !cipherText.startsWith("v1:") && !cipherText.startsWith("fallback:")) {
            try {
                val parts = cipherText.split(".")
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                val secretKey = getSecretKey()
                if (secretKey != null) {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)
                    return String(decryptedBytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                android.util.Log.e("KeystoreHelper", "Raw decrypt failed", e)
            }
        }
        
        return cipherText
    }
}
