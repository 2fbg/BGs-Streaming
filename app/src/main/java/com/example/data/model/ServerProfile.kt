package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa o perfil de um servidor IPTV Xtream Codes / M3U.
 * Permite controle de credenciais individuais ou globais e status ativo/inativo.
 */
@Immutable
@Entity(tableName = "servers")
data class ServerProfile(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val isActive: Boolean = true
)
