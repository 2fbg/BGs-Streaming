package com.example.presentation.navigation

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.utils.ExternalPlayerHelper
import com.example.presentation.ui.home.HomeScreen
import com.example.presentation.ui.login.LoginScreen
import com.example.presentation.ui.player.InternalPlayerScreen
import com.example.presentation.ui.settings.BulkServerManagementScreen
import com.example.presentation.ui.settings.SettingsScreen
import com.example.presentation.viewmodel.AppViewModel
import com.example.presentation.viewmodel.UiEvent

object AppDestinations {
    const val LOGIN = "login"
    const val HOME = "home"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val BULK_SERVER_MANAGEMENT = "bulk_server_management"
}

/**
 * Host de navegação do MK21 Player com animações suaves e tratamento de eventos de UI.
 */
@Composable
fun AppNavigation(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()

    // Se já houver credenciais salvas, inicia na Home; caso contrário, vai para o Login Único
    val startDestination = if (username.isNotBlank() && password.isNotBlank()) {
        AppDestinations.HOME
    } else {
        AppDestinations.LOGIN
    }

    // Observa eventos one-shot (abrir player externo, toast, login)
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.OpenExternalPlayer -> {
                    ExternalPlayerHelper.launchPlayer(
                        context = context,
                        streamUrl = event.url,
                        title = event.title,
                        preferredPlayer = event.preferredPlayer
                    )
                }
                is UiEvent.LoginSuccess -> {
                    navController.navigate(AppDestinations.HOME) {
                        popUpTo(AppDestinations.LOGIN) { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        // Tela de Login Único
        composable(AppDestinations.LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(AppDestinations.HOME) {
                        popUpTo(AppDestinations.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(AppDestinations.SETTINGS)
                }
            )
        }

        // Tela Inicial (Canais & Servidores)
        composable(AppDestinations.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToSettings = {
                    navController.navigate(AppDestinations.SETTINGS)
                },
                onNavigateToPlayer = { item ->
                    navController.navigate(AppDestinations.PLAYER)
                }
            )
        }

        // Tela de Player Interno (Media3 ExoPlayer)
        composable(AppDestinations.PLAYER) {
            val currentItem by viewModel.currentPlayingItem.collectAsState()
            if (currentItem != null) {
                InternalPlayerScreen(
                    item = currentItem!!,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        // Tela de Configurações
        composable(AppDestinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBulkManagement = {
                    navController.navigate(AppDestinations.BULK_SERVER_MANAGEMENT)
                }
            )
        }

        // Tela de Manutenção em Massa de Servidores (Importação WhatsApp / Regex / Exclusão Múltipla)
        composable(AppDestinations.BULK_SERVER_MANAGEMENT) {
            BulkServerManagementScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
