package com.example.data.repository

import com.example.data.database.ManualPlaylistDao
import com.example.data.database.PlaylistItemDao
import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

interface IptvRepository {
    fun getAllItemsByPlaylist(source: String): Flow<List<PlaylistItem>>
    fun getItemsByType(source: String, type: String): Flow<List<PlaylistItem>>
    fun getCategoriesByType(source: String, type: String): Flow<List<String>>
    fun getItemsByCategoryAndType(source: String, category: String, type: String): Flow<List<PlaylistItem>>
    fun searchItems(source: String, query: String): Flow<List<PlaylistItem>>
    fun getFavorites(source: String): Flow<List<PlaylistItem>>
    fun getContinueWatching(source: String, type: String): Flow<List<PlaylistItem>>
    fun getRandomHighlights(source: String): Flow<List<PlaylistItem>>
    
    suspend fun savePlaylistItems(source: String, items: List<PlaylistItem>)
    suspend fun clearPlaylist(source: String)
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
    suspend fun updateLastWatched(id: Long, timestamp: Long)
    suspend fun clearLiveHistory(source: String)

    fun getAllManualPlaylists(): Flow<List<ManualPlaylist>>
    suspend fun insertManualPlaylist(playlist: ManualPlaylist)
    suspend fun deleteManualPlaylist(name: String)
}

class IptvRepositoryImpl(
    private val playlistItemDao: PlaylistItemDao,
    private val manualPlaylistDao: ManualPlaylistDao
) : IptvRepository {

    override fun getAllItemsByPlaylist(source: String): Flow<List<PlaylistItem>> =
        playlistItemDao.getAllItemsByPlaylist(source)

    override fun getItemsByType(source: String, type: String): Flow<List<PlaylistItem>> =
        playlistItemDao.getItemsByType(source, type)

    override fun getCategoriesByType(source: String, type: String): Flow<List<String>> =
        playlistItemDao.getCategoriesByType(source, type)

    override fun getItemsByCategoryAndType(source: String, category: String, type: String): Flow<List<PlaylistItem>> =
        playlistItemDao.getItemsByCategoryAndType(source, category, type)

    override fun searchItems(source: String, query: String): Flow<List<PlaylistItem>> =
        playlistItemDao.searchItems(source, query)

    override fun getFavorites(source: String): Flow<List<PlaylistItem>> =
        playlistItemDao.getFavorites(source)

    override fun getContinueWatching(source: String, type: String): Flow<List<PlaylistItem>> =
        playlistItemDao.getContinueWatching(source, type)

    override fun getRandomHighlights(source: String): Flow<List<PlaylistItem>> =
        playlistItemDao.getRandomHighlights(source)

    override suspend fun savePlaylistItems(source: String, items: List<PlaylistItem>) =
        playlistItemDao.clearAndInsertPlaylistItems(source, items)

    override suspend fun clearPlaylist(source: String) =
        playlistItemDao.clearPlaylistItems(source)

    override suspend fun updateFavorite(id: Long, isFavorite: Boolean) =
        playlistItemDao.setFavorite(id, isFavorite)

    override suspend fun updateLastWatched(id: Long, timestamp: Long) =
        playlistItemDao.updateLastWatched(id, timestamp)

    override suspend fun clearLiveHistory(source: String) =
        playlistItemDao.clearLiveHistory(source)

    override fun getAllManualPlaylists(): Flow<List<ManualPlaylist>> =
        manualPlaylistDao.getAllManualPlaylists()

    override suspend fun insertManualPlaylist(playlist: ManualPlaylist) =
        manualPlaylistDao.insertManualPlaylist(playlist)

    override suspend fun deleteManualPlaylist(name: String) =
        manualPlaylistDao.deleteManualPlaylist(name)
}
