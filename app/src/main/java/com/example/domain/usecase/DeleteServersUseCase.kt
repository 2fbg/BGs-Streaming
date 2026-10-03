package com.example.domain.usecase

import com.example.domain.repository.ServerRepository

/**
 * Caso de uso para exclusão de servidores individualmente ou em massa com seleção múltipla.
 */
class DeleteServersUseCase(
    private val serverRepository: ServerRepository
) {
    suspend operator fun invoke(serverIds: List<String>): Result<Unit> {
        if (serverIds.isEmpty()) {
            return Result.success(Unit)
        }

        return try {
            serverRepository.deleteServers(serverIds)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
