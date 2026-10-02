package com.example.domain.usecase

import com.example.data.model.ServerProfile
import com.example.data.preferences.PreferencesService
import com.example.data.utils.ParsedServer
import com.example.data.utils.ServerTextParser
import com.example.domain.repository.ServerRepository

/**
 * Caso de uso para importação em massa de servidores a partir de texto (formato WhatsApp ou URLs).
 */
class ImportServersFromTextUseCase(
    private val serverRepository: ServerRepository,
    private val preferencesService: PreferencesService
) {
    /**
     * Analisa o texto sem salvar, permitindo pré-visualização na tela de manutenção.
     */
    fun parsePreview(text: String): List<ParsedServer> {
        return ServerTextParser.parse(text)
    }

    /**
     * Salva os servidores extraídos no banco de dados.
     */
    suspend fun saveExtracted(
        servers: List<ParsedServer>,
        fallbackUsername: String? = null,
        fallbackPassword: String? = null
    ): Result<List<ServerProfile>> {
        if (servers.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nenhum servidor foi detectado para importar."))
        }

        return try {
            val user = fallbackUsername ?: preferencesService.username.ifEmpty { null }
            val pass = fallbackPassword ?: preferencesService.password.ifEmpty { null }

            val entities = ServerTextParser.toServerProfiles(servers, user, pass)
            serverRepository.insertServers(entities)
            Result.success(entities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executa parsing e persistência direta a partir do texto.
     */
    suspend operator fun invoke(text: String): Result<List<ServerProfile>> {
        val parsed = ServerTextParser.parse(text)
        if (parsed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nenhum link ou servidor válido encontrado no texto."))
        }
        return saveExtracted(parsed)
    }
}
