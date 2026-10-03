package com.example.domain.repository

import com.example.data.model.ServerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Interface do repositório de servidores IPTV na camada de domínio.
 */
interface ServerRepository {

    fun getAllServers(): Flow<List<ServerProfile>>

    fun getActiveServers(): Flow<List<ServerProfile>>

    suspend fun getServerById(id: String): ServerProfile?

    suspend fun insertServers(servers: List<ServerProfile>)

    suspend fun insertServer(server: ServerProfile)

    suspend fun updateServer(server: ServerProfile)

    suspend fun deleteServers(serverIds: List<String>)

    suspend fun toggleServerActive(serverId: String, isActive: Boolean)

    suspend fun applyCredentialsToAllServers(username: String, password: String)

    suspend fun fetchDynamicServers(url: String): Result<List<ServerProfile>>

    suspend fun loadCachedOrPredefinedServers(): List<ServerProfile>
}
