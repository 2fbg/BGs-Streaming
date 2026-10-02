package com.example.domain.usecase

import com.example.data.preferences.PreferencesService
import com.example.domain.repository.ServerRepository

/**
 * Caso de uso para login único no MK21 Player.
 * Salva credenciais com segurança e aplica automaticamente a todos os servidores cadastrados.
 */
class LoginUseCase(
    private val serverRepository: ServerRepository,
    private val preferencesService: PreferencesService
) {
    suspend operator fun invoke(username: String, password: String): Result<Unit> {
        val cleanUser = username.trim()
        val cleanPass = password.trim()

        if (cleanUser.isEmpty()) {
            return Result.failure(IllegalArgumentException("O usuário não pode estar em branco."))
        }
        if (cleanPass.isEmpty()) {
            return Result.failure(IllegalArgumentException("A senha não pode estar em branco."))
        }

        return try {
            // Salva no armazenamento seguro (EncryptedSharedPreferences)
            preferencesService.username = cleanUser
            preferencesService.password = cleanPass

            // Aplica credenciais a todos os servidores no banco Room
            serverRepository.applyCredentialsToAllServers(cleanUser, cleanPass)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
