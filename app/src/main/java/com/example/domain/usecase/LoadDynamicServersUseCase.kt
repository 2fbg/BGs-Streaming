package com.example.domain.usecase

import com.example.data.model.ServerProfile
import com.example.data.preferences.PreferencesService
import com.example.domain.repository.ServerRepository

/**
 * Caso de uso para carregar a lista dinâmica de servidores a partir de uma URL configurável.
 */
class LoadDynamicServersUseCase(
    private val serverRepository: ServerRepository,
    private val preferencesService: PreferencesService
) {
    suspend operator fun invoke(customUrl: String? = null): Result<List<ServerProfile>> {
        val targetUrl = customUrl ?: preferencesService.dynamicServersUrl
        if (targetUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("A URL para busca de servidores dinâmicos não foi configurada."))
        }

        return serverRepository.fetchDynamicServers(targetUrl)
    }
}
