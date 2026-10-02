package com.example.data.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Utilitário de gerenciamento de players externos de vídeo via Intent.ACTION_VIEW.
 */
object ExternalPlayerHelper {

    const val PLAYER_ANY = "Qualquer Player (Recomendado)"
    const val PLAYER_VLC = "VLC for Android"
    const val PLAYER_MX = "MX Player"
    const val PLAYER_MX_PRO = "MX Player Pro"
    const val PLAYER_JUST_PLAYER = "Just Player"
    const val PLAYER_KODI = "Kodi"

    val AVAILABLE_PLAYERS = listOf(
        PLAYER_ANY,
        PLAYER_VLC,
        PLAYER_MX,
        PLAYER_MX_PRO,
        PLAYER_JUST_PLAYER,
        PLAYER_KODI
    )

    private val PACKAGE_MAP = mapOf(
        PLAYER_VLC to "org.videolan.vlc",
        PLAYER_MX to "com.mxtech.videoplayer.ad",
        PLAYER_MX_PRO to "com.mxtech.videoplayer.pro",
        PLAYER_JUST_PLAYER to "com.brouken.player",
        PLAYER_KODI to "org.xbmc.kodi"
    )

    fun getPackageName(playerName: String): String? = PACKAGE_MAP[playerName]

    /**
     * Verifica se o aplicativo especificado está instalado no dispositivo.
     */
    fun isPlayerInstalled(context: Context, playerName: String): Boolean {
        val packageName = getPackageName(playerName) ?: return true
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Abre a URL de reprodução usando o player externo configurado.
     * Caso o player específico não esteja instalado ou seja "Qualquer Player",
     * apresenta o seletor nativo do Android.
     */
    fun launchPlayer(
        context: Context,
        streamUrl: String,
        title: String,
        preferredPlayer: String
    ): Boolean {
        if (streamUrl.isBlank()) {
            Toast.makeText(context, "URL de stream inválida", Toast.LENGTH_SHORT).show()
            return false
        }

        val uri = Uri.parse(streamUrl)
        val mimeType = if (streamUrl.contains(".m3u8", ignoreCase = true)) {
            "application/x-mpegURL"
        } else {
            "video/*"
        }

        val targetPackage = getPackageName(preferredPlayer)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            putExtra("title", title)
            putExtra("android.intent.extra.TITLE", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Se o player específico estiver instalado, usa diretamente
        if (targetPackage != null && isPlayerInstalled(context, preferredPlayer)) {
            intent.setPackage(targetPackage)
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w("ExternalPlayerHelper", "Falha ao abrir $preferredPlayer diretamente: ${e.message}")
            }
        }

        // Fallback: seletor nativo (Chooser)
        val chooserIntent = Intent.createChooser(intent, "Abrir $title com").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            Log.e("ExternalPlayerHelper", "Nenhum player compatível encontrado: ${e.message}")
            Toast.makeText(
                context,
                "Nenhum player de vídeo externo encontrado no aparelho.",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }
}
