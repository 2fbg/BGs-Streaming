package com.example.data.repository

import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IptvRepositoryTest {

    // Clean in-memory fake repository without external mock dependencies
    private class FakeIptvRepository : IptvRepository {
        private val items = mutableListOf<PlaylistItem>()

        override fun getAllItemsByPlaylist(source: String): Flow<List<PlaylistItem>> =
            flowOf(items.filter { it.playlistSource == source })

        override fun getItemsByType(source: String, type: String): Flow<List<PlaylistItem>> =
            flowOf(items.filter { it.playlistSource == source && it.contentType == type })

        override fun getCategoriesByType(source: String, type: String): Flow<List<String>> =
            flowOf(items.filter { it.playlistSource == source && it.contentType == type }.map { it.category }.distinct())

        override fun getItemsByCategoryAndType(source: String, category: String, type: String): Flow<List<PlaylistItem>> =
            flowOf(items.filter { it.playlistSource == source && it.category == category && it.contentType == type })

        override fun searchItems(source: String, query: String): Flow<List<PlaylistItem>> =
            flowOf(items.filter { it.playlistSource == source && it.name.contains(query, ignoreCase = true) })

        override fun getFavorites(source: String): Flow<List<PlaylistItem>> =
            flowOf(items.filter { it.playlistSource == source && it.isFavorite })

        override fun getContinueWatching(source: String, type: String): Flow<List<PlaylistItem>> =
            flowOf(items.filter { it.playlistSource == source && it.contentType == type && it.lastWatchedTime > 0 })

        override fun getRandomHighlights(source: String): Flow<List<PlaylistItem>> =
            flowOf(items.filter { it.playlistSource == source }.take(5))

        override suspend fun savePlaylistItems(source: String, newItems: List<PlaylistItem>) {
            items.removeAll { it.playlistSource == source }
            items.addAll(newItems)
        }

        override suspend fun clearPlaylist(source: String) {
            items.removeAll { it.playlistSource == source }
        }

        override suspend fun updateFavorite(id: Long, isFavorite: Boolean) {}
        override suspend fun updateLastWatched(id: Long, timestamp: Long) {}
        override suspend fun clearLiveHistory(source: String) {}

        override fun getAllManualPlaylists(): Flow<List<ManualPlaylist>> = flowOf(emptyList())
        override suspend fun insertManualPlaylist(playlist: ManualPlaylist) {}
        override suspend fun deleteManualPlaylist(name: String) {}
    }

    @Test
    fun repository_savesAndReturnsItemsCorrectly() = runBlocking {
        val repo = FakeIptvRepository()
        val sampleItem = PlaylistItem(
            id = 1,
            name = "Test News HD",
            url = "http://test.stream/live.m3u8",
            logoUrl = "http://test.stream/logo.png",
            category = "News",
            contentType = "LIVE",
            playlistSource = "VLOG"
        )
        repo.savePlaylistItems("VLOG", listOf(sampleItem))

        val flow = repo.getItemsByType("VLOG", "LIVE")
        flow.collect { items ->
            assertEquals(1, items.size)
            assertEquals("Test News HD", items[0].name)
            assertEquals("LIVE", items[0].contentType)
        }
    }

    @Test
    fun repository_urlSanitizer_blocksDangerousSchemes() {
        val safeHttp = com.example.data.parser.M3UParser.sanitizeStreamUrl("http://example.com/stream.ts")
        val safeHttps = com.example.data.parser.M3UParser.sanitizeStreamUrl("https://example.com/stream.m3u8")
        val badFile = com.example.data.parser.M3UParser.sanitizeStreamUrl("file:///data/data/com.example/databases/mk21_iptv_db")
        val badContent = com.example.data.parser.M3UParser.sanitizeStreamUrl("content://media/external/images/media")
        val badJs = com.example.data.parser.M3UParser.sanitizeStreamUrl("javascript:alert(1)")

        assertEquals("http://example.com/stream.ts", safeHttp)
        assertEquals("https://example.com/stream.m3u8", safeHttps)
        assertNull(badFile)
        assertNull(badContent)
        assertNull(badJs)
    }
}
