package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Type of playlist content: LIVE (Ao Vivo), MOVIE (Filmes), SERIES (Séries)
 */
enum class ContentType {
    LIVE, MOVIE, SERIES
}

/**
 * Represents a predefined Server configuration entry.
 */
data class ServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String
)

/**
 * Represents a parsed playlist channel, movie, or series.
 */
@Entity(tableName = "playlist_items")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val logoUrl: String?,
    val category: String, // group-title
    val contentType: String, // LIVE, MOVIE, SERIES
    val isAdult: Boolean = false,
    val playlistSource: String, // Name of the playlist / server this came from
    val lastWatchedTime: Long = 0,
    val isFavorite: Boolean = false
)

/**
 * Represents a custom manual M3U playlist added by the user.
 */
@Entity(tableName = "manual_playlists")
data class ManualPlaylist(
    @PrimaryKey val name: String,
    val url: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
