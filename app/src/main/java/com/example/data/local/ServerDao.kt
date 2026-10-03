package com.example.data.local

import androidx.room.*
import com.example.data.model.ServerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) do Room para gerenciamento de servidores IPTV.
 */
@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM servers")
    suspend fun getAllServersList(): List<ServerProfile>

    @Query("SELECT * FROM servers WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveServers(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): ServerProfile?

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun getServerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerProfile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerProfile)

    @Update
    suspend fun updateServer(server: ServerProfile)

    @Query("UPDATE servers SET isActive = :isActive WHERE id = :serverId")
    suspend fun toggleServerActive(serverId: String, isActive: Boolean)

    @Query("UPDATE servers SET username = :username, password = :password")
    suspend fun updateCredentialsForAll(username: String, password: String)

    @Query("DELETE FROM servers WHERE id IN (:serverIds)")
    suspend fun deleteServers(serverIds: List<String>)

    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun deleteServer(serverId: String)

    @Query("DELETE FROM servers")
    suspend fun deleteAllServers()
}
