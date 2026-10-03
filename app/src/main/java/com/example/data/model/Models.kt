package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tipo de conteúdo da playlist: LIVE (Ao Vivo), MOVIE (Filmes), SERIES (Séries)
 */
enum class ContentType {
    LIVE, MOVIE, SERIES
}

/**
 * Representa uma playlist manual cadastrada pelo usuário.
 */
@Immutable
@Entity(tableName = "manual_playlists")
data class ManualPlaylist(
    @PrimaryKey val name: String,
    val url: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
