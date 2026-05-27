package com.example.ui

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.media3.common.util.UnstableApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentType
import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import com.example.ui.theme.DarkCard
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NetflixRed
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import android.media.AudioManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange

/**
 * TV Series Episodes Grouping Data Classes & Heuristics
 */
data class GroupedSeries(
    val title: String,
    val logoUrl: String?,
    val category: String,
    val episodes: List<PlaylistItem>
)

fun groupSeriesItems(items: List<PlaylistItem>): List<GroupedSeries> {
    val regex = Regex("(?i)\\b(s\\d{1,2}e\\d{1,2}|\\d{1,2}x\\d{1,2}|t\\d{1,2}e\\d{1,2}|season\\s*\\d+\\s*episode\\s*\\d+|temp\\.\\s*\\d+\\s*ep\\.\\s*\\d+|capitulo\\s*\\d+|capí?tulo\\s*\\d+)\\b")
    
    val groups = items.groupBy { item ->
        val matchResult = regex.find(item.name)
        if (matchResult != null) {
            val idx = matchResult.range.first
            var seriesName = item.name.substring(0, idx).trim()
            seriesName = seriesName.trimEnd('-', '_', ',', '/', ':', ' ')
            if (seriesName.isEmpty()) item.name else seriesName
        } else {
            // Check for EP.xx or Episode xx
            val epRegex = Regex("(?i)\\bep\\.?\\s*\\d+\\b|\\bepisod[io|io|o]\\s*\\d+\\b")
            val epMatch = epRegex.find(item.name)
            if (epMatch != null) {
                val idx = epMatch.range.first
                var seriesName = item.name.substring(0, idx).trim()
                seriesName = seriesName.trimEnd('-', '_', ',', '/', ':', ' ')
                if (seriesName.isEmpty()) item.name else seriesName
            } else {
                item.name
            }
        }
    }

    return groups.map { (seriesTitle, episodes) ->
        GroupedSeries(
            title = seriesTitle,
            logoUrl = episodes.firstOrNull { !it.logoUrl.isNullOrEmpty() }?.logoUrl ?: episodes.firstOrNull()?.logoUrl,
            category = episodes.firstOrNull()?.category ?: "Séries",
            episodes = episodes.sortedWith(compareBy<PlaylistItem> { it.name })
        )
    }.sortedBy { it.title }
}

/**
 * Navigation routes
 */
object Routes {
    const val STARTUP_GATE = "startup_gate"
    const val SERVER_CONFIG = "server_config"
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val isPremiumActive by viewModel.isPremiumActive.collectAsState()
    val trialDaysLeft by viewModel.trialDaysLeft.collectAsState()
    
    val isTrialExpired = trialDaysLeft <= 0 && !isPremiumActive
    
    if (isTrialExpired) {
        TrialExpiredScreen(viewModel = viewModel)
    } else {
        var currentScreen by remember { mutableStateOf(Routes.STARTUP_GATE) }
        
        // Check which screen to show on bootup
        LaunchedEffect(Unit) {
            if (viewModel.isCredentialsConfigured()) {
                currentScreen = Routes.HOME
            } else {
                currentScreen = Routes.SERVER_CONFIG
            }
        }

        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                Routes.STARTUP_GATE -> {
                    StartupGateScreen()
                }
                Routes.SERVER_CONFIG -> {
                    ServerConfigScreen(
                        viewModel = viewModel,
                        onNavigateToHome = { currentScreen = Routes.HOME }
                    )
                }
                Routes.HOME -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = { currentScreen = Routes.SETTINGS },
                        onNavigateToConfig = { currentScreen = Routes.SERVER_CONFIG }
                    )
                }
                Routes.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Routes.HOME }
                    )
                }
            }
        }
    }
}

@Composable
fun TrialExpiredScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val virtualMac = viewModel.virtualMacAddress
    
    var activationCodeInput by remember { mutableStateOf("") }
    var activationError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0A0A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF131111))
                .border(1.5.dp, GoldPremium.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock Icon with premium gold aura
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(GoldPremium.copy(alpha = 0.1f))
                    .border(1.dp, GoldPremium.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Licença Requerida",
                    tint = GoldPremium,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "Período de Testes Expirado",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation
            Text(
                text = "Seus 5 dias de avaliação gratuita terminaram. Para continuar a testar e utilizar todas as funções exclusivas do aplicativo, entre em contato com o desenvolvedor e envie a chave abaixo para ativação rápida do seu aparelho.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // MAC Key Display Card with copy button
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B1B)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CHAVE DO DISPOSITIVO (VIRTUAL MAC)",
                            color = GoldPremium,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = virtualMac,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(virtualMac))
                            android.widget.Toast.makeText(context, "Chave copiada para a área de transferência!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar chave",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (activationError != null) {
                Text(
                    text = activationError!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Code input field
            OutlinedTextField(
                value = activationCodeInput,
                onValueChange = { 
                    activationCodeInput = it
                    activationError = null
                },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                label = { Text("Código de Ativação / Licença", color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPremium,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = GoldPremium
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action button
            Button(
                onClick = {
                    if (activationCodeInput.isEmpty()) {
                        activationError = "Por favor, digite seu código de ativação"
                    } else {
                        val success = viewModel.activateLicense(activationCodeInput)
                        if (success) {
                            android.widget.Toast.makeText(context, "Dispositivo ativado com sucesso! Aproveite!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            activationError = "Código inválido para este dispositivo!"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "ATIVAR DISPOSITIVO",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MK21Logo(
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true,
    compact: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // "MK" in Silver/White Gradient
            Text(
                text = "MK",
                fontSize = if (compact) 18.sp else 38.sp,
                fontWeight = FontWeight.Black,
                style = androidx.compose.ui.text.TextStyle(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    letterSpacing = (-1).sp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFCCCCCC))
                    )
                )
            )
            // "21" in Red Gradient
            Text(
                text = "21",
                fontSize = if (compact) 20.sp else 42.sp,
                fontWeight = FontWeight.Black,
                style = androidx.compose.ui.text.TextStyle(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    letterSpacing = (-1).sp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF1E1E), Color(0xFFB30000))
                    )
                )
            )
        }
        
        if (showSubtitle) {
            Spacer(modifier = Modifier.height(2.dp))
            // Subtitle: MAIS QUE UM NÚMERO É RESULTADO
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Red horizontal line
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .width(10.dp)
                        .background(Color(0xFFFF1E1E))
                )
                Text(
                    text = " MAIS QUE UM NÚMERO É RESULTADO ",
                    fontSize = if (compact) 6.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    letterSpacing = 0.2.sp
                )
                // Red horizontal line
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .width(10.dp)
                        .background(Color(0xFFFF1E1E))
                )
            }
        }
    }
}

@Composable
fun SophisticatedBrandHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    MK21Logo(
        modifier = modifier,
        showSubtitle = !compact,
        compact = compact
    )
}

/**
 * Standard Boot Setup load screen
 */
@Composable
fun StartupGateScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.SophisticatedBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SophisticatedBrandHeader()
            Spacer(modifier = Modifier.height(36.dp))
            CircularProgressIndicator(color = com.example.ui.theme.SophisticatedRedStart, strokeWidth = 3.dp)
        }
    }
}

/**
 * LOGIN AND SERVER SETTINGS
 */
@Composable
fun ServerConfigScreen(viewModel: AppViewModel, onNavigateToHome: () -> Unit) {
    val context = LocalContext.current
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val activePlaylist by viewModel.activePlaylistName.collectAsState()
    val manualLists by viewModel.manualPlaylists.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()

    var showManualDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<com.example.data.model.ManualPlaylist?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Predef, 1: Manual List

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(com.example.ui.theme.SophisticatedBg, Color(0xFF0F0F12))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Integrated Sophisticated Brand Logo Header
            SophisticatedBrandHeader()

            Spacer(modifier = Modifier.height(36.dp))

            // Tab Bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = com.example.ui.theme.SophisticatedRedStart,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = com.example.ui.theme.SophisticatedRedStart
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Predefinidos", fontWeight = FontWeight.Bold, color = if (selectedTab == 0) Color.White else Color.Gray, fontSize = 14.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Listas Manuais", fontWeight = FontWeight.Bold, color = if (selectedTab == 1) Color.White else Color.Gray, fontSize = 14.sp) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                // Predefined Server setupcard
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Acesso Oficial MK21",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // USERNAME & PASSWORD IN A SINGLE CONTIGUOUS MODERN ROW WITH WARN ON OVERFLOW
                        val isUsernameExceeded = username.length > 15
                        val isPasswordExceeded = password.length > 15

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { viewModel.setCredentials(it, password) },
                                label = { Text("Usuário", fontSize = 11.sp, color = Color.Gray) },
                                maxLines = 1,
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                isError = isUsernameExceeded,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("username_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isUsernameExceeded) com.example.ui.theme.NetflixRed else com.example.ui.theme.SophisticatedRedStart,
                                    unfocusedBorderColor = if (isUsernameExceeded) com.example.ui.theme.NetflixRed.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = if (isUsernameExceeded) com.example.ui.theme.NetflixRed else com.example.ui.theme.SophisticatedRedStart,
                                    focusedContainerColor = Color(0xFF09090C),
                                    unfocusedContainerColor = Color(0xFF09090C),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { viewModel.setCredentials(username, it) },
                                label = { Text("Senha", fontSize = 11.sp, color = Color.Gray) },
                                maxLines = 1,
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                isError = isPasswordExceeded,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("password_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isPasswordExceeded) com.example.ui.theme.NetflixRed else com.example.ui.theme.SophisticatedRedStart,
                                    unfocusedBorderColor = if (isPasswordExceeded) com.example.ui.theme.NetflixRed.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = if (isPasswordExceeded) com.example.ui.theme.NetflixRed else com.example.ui.theme.SophisticatedRedStart,
                                    focusedContainerColor = Color(0xFF09090C),
                                    unfocusedContainerColor = Color(0xFF09090C),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        if (isUsernameExceeded || isPasswordExceeded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = com.example.ui.theme.NetflixRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Aviso: O limite recomendado é 15 caracteres!",
                                    color = com.example.ui.theme.NetflixRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Selecione o Servidor",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        var serverExpanded by remember { mutableStateOf(false) }
                        val selectedServer = viewModel.predefinedServers.find { it.name == activePlaylist } ?: viewModel.predefinedServers.first()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_combo_box")
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { serverExpanded = !serverExpanded },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = "Server icon",
                                        tint = com.example.ui.theme.SophisticatedRedStart,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "CONEXÃO SELECIONADA",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = com.example.ui.theme.GoldPremium,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = selectedServer.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown icon",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = serverExpanded,
                                onDismissRequest = { serverExpanded = false },
                                modifier = Modifier
                                    .width(260.dp)
                                    .background(Color(0xFF0C0C0F))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(10.dp))
                            ) {
                                viewModel.predefinedServers.forEach { server ->
                                    val isSelected = activePlaylist == server.name
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Dns,
                                                    contentDescription = null,
                                                    tint = if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = server.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectPlaylist(server.name)
                                            serverExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Multiple Manual Playlist lists configuration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Minhas Listas M3U",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            IconButton(onClick = { showManualDialog = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add List", tint = GoldPremium)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        if (manualLists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhuma lista salva.\nToque no '+' para adicionar.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            manualLists.forEach { manual ->
                                val isSelected = activePlaylist == manual.name
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.selectPlaylist(manual.name) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF1F0B0E) else Color(0xFF0F0F12)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.selectPlaylist(manual.name) },
                                            colors = RadioButtonDefaults.colors(selectedColor = com.example.ui.theme.SophisticatedRedStart)
                                        )
                                        
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = manual.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = manual.url,
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { editingPlaylist = manual },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit List",
                                                    tint = com.example.ui.theme.GoldPremium.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteManualPlaylist(manual.name) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete List",
                                                    tint = Color.Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error display
            if (errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1215)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg ?: "",
                        color = Color.Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Main Load / Enter Button
            Button(
                onClick = {
                    if (selectedTab == 0 && (username.isEmpty() || password.isEmpty())) {
                        viewModel.selectPlaylist(activePlaylist) // will trigger validate
                    } else {
                        viewModel.refreshActivePlaylist()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_button"),
                enabled = loadingProgress == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (loadingProgress == null) {
                                Brush.horizontalGradient(
                                    colors = listOf(com.example.ui.theme.SophisticatedRedStart, com.example.ui.theme.SophisticatedRedEnd)
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.5f), com.example.ui.theme.SophisticatedRedEnd.copy(alpha = 0.5f))
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingProgress != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Sincronizando...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Enter icon", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ENTRAR E CARREGAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Enter Home instantly if items already cached in Room!
            LaunchedEffect(Unit) {
                viewModel.loginSuccess.collect {
                    onNavigateToHome()
                }
            }

            LaunchedEffect(loadingProgress) {
                if (loadingProgress == 100) {
                    delay(400)
                    onNavigateToHome()
                }
            }

            // If we already have items in active lists, let the user enter directly without refreshing
            Spacer(modifier = Modifier.height(12.dp))
            val itemsCount = viewModel.activeItemsList.collectAsState().value.size
            if (itemsCount > 0 && loadingProgress == null) {
                TextButton(onClick = onNavigateToHome) {
                    Text("Prosseguir para Home (Modo Offline / Cache)", color = GoldPremium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Percentage-based Load HUD Overlay
        if (loadingProgress != null && loadingProgress!! < 100) {
            Dialog(onDismissRequest = {}) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (loadingProgress ?: 0).toFloat() / 100f },
                            modifier = Modifier.size(90.dp),
                            color = NetflixRed,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "$loadingProgress%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Buscando Playlist",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Por favor, aguarde...",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Unified Add/Edit Manual Playlist Dialog Form
        if (showManualDialog || editingPlaylist != null) {
            val isEditing = editingPlaylist != null
            val initialName = editingPlaylist?.name ?: ""
            val initialUrl = editingPlaylist?.url ?: ""
            
            var inputListName by remember(editingPlaylist) { mutableStateOf(initialName) }
            var inputListUrl by remember(editingPlaylist) { mutableStateOf(initialUrl) }
            
            Dialog(onDismissRequest = { 
                showManualDialog = false 
                editingPlaylist = null
            }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isEditing) "Editar Lista Manual" else "Adicionar Lista Manual",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = inputListName,
                            onValueChange = { inputListName = it },
                            label = { Text("Nome da Lista", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color(0xFF09090C),
                                unfocusedContainerColor = Color(0xFF09090C),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = inputListUrl,
                            onValueChange = { inputListUrl = it.trim() },
                            label = { Text("URL M3U", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color(0xFF09090C),
                                unfocusedContainerColor = Color(0xFF09090C),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { 
                                showManualDialog = false 
                                editingPlaylist = null
                            }) {
                                Text("Cancelar", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (inputListName.isNotEmpty() && inputListUrl.isNotEmpty()) {
                                        if (isEditing) {
                                            viewModel.updateManualPlaylist(editingPlaylist!!.name, inputListName, inputListUrl)
                                        } else {
                                            viewModel.addManualPlaylist(inputListName, inputListUrl)
                                        }
                                        showManualDialog = false
                                        editingPlaylist = null
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SophisticatedRedStart)
                            ) {
                                Text("Salvar", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * HOME STREAMING DASHBOARD SCREEN
 */
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    val context = LocalContext.current
    val activePlaylist by viewModel.activePlaylistName.collectAsState()
    val manualLists by viewModel.manualPlaylists.collectAsState()
    val contentType by viewModel.selectedContentType.collectAsState()
    val activeCategory by viewModel.selectedCategory.collectAsState()
    val categories by viewModel.activeCategories.collectAsState()
    val itemsList by viewModel.activeItemsList.collectAsState()
    val searchResultQuery by viewModel.searchQuery.collectAsState()
    val highlights by viewModel.highlightsList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPlayingItem by viewModel.currentPlayingItem.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val continueWatching by viewModel.continueWatchingList.collectAsState()
    val backgroundLoadingState by viewModel.backgroundLoadingState.collectAsState()

    var showPlaylistMenu by remember { mutableStateOf(false) }
    var selectedSeriesForDetail by remember { mutableStateOf<GroupedSeries?>(null) }
    var showSortOrderDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val gridColumnsCount = remember(contentType, isLandscape) {
        if (contentType == ContentType.LIVE) {
            if (isLandscape) 3 else 2
        } else {
            if (isLandscape) 4 else 3
        }
    }

    val processedSeriesChunks = remember(itemsList, contentType, isLandscape, gridColumnsCount) {
        if (contentType == ContentType.SERIES) {
            val grouped = groupSeriesItems(itemsList)
            grouped.chunked(gridColumnsCount)
        } else {
            emptyList()
        }
    }

    val processedNormalChunks = remember(itemsList, contentType, isLandscape, gridColumnsCount) {
        if (contentType != ContentType.SERIES) {
            itemsList.chunked(gridColumnsCount)
        } else {
            emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.SophisticatedBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            if (isLandscape) {
                // Squeezed Premium Landscape Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SophisticatedBrandHeader(
                        compact = true,
                        modifier = Modifier.clickable { onNavigateToConfig() }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Connected Dropdown combo
                    Box {
                        Button(
                            onClick = { showPlaylistMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111115)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = "Playlist selector", tint = GoldPremium, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activePlaylist.take(12) + if (activePlaylist.length > 12) ".." else "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown indicators", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = showPlaylistMenu,
                            onDismissRequest = { showPlaylistMenu = false },
                            modifier = Modifier.background(Color(0xFF0C0C0F))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Predefinidos Oficial", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            viewModel.predefinedServers.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(server.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Listas Customizadas", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            manualLists.forEach { manual ->
                                DropdownMenuItem(
                                    text = { Text(manual.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(manual.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // ContentType pill tabs
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            ContentType.LIVE to "Ao Vivo",
                            ContentType.MOVIE to "Filmes",
                            ContentType.SERIES to "Séries"
                        ).forEach { (type, label) ->
                            val isSelected = contentType == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color(0xFF111115))
                                    .clickable { viewModel.changeContentType(type) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Consolidated Search box (BasicTextField - centers content and never crops vertically!)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF09090C), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            singleLine = true,
                            maxLines = 1,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(Color.White),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Buscar...",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.setSearchQuery("") },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Direct Quick Sort Button on Home Screen
                    IconButton(onClick = { showSortOrderDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Sort, contentDescription = "Ordenação", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = { viewModel.refreshActivePlaylist() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh list", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                // Portrait Original Column
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SophisticatedBrandHeader(
                        compact = true,
                        modifier = Modifier.clickable { onNavigateToConfig() }
                    )
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Box {
                        Button(
                            onClick = { showPlaylistMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111115)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = "Playlist selector", tint = GoldPremium, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activePlaylist.take(12) + if (activePlaylist.length > 12) ".." else "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown indicators", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = showPlaylistMenu,
                            onDismissRequest = { showPlaylistMenu = false },
                            modifier = Modifier.background(Color(0xFF0C0C0F))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Predefinidos Oficial", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            viewModel.predefinedServers.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(server.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Listas Customizadas", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            manualLists.forEach { manual ->
                                DropdownMenuItem(
                                    text = { Text(manual.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(manual.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { viewModel.refreshActivePlaylist() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh content list",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open preferences settings page",
                            tint = Color.White
                        )
                    }
                }

                // Categories Selector Row and Search Bar on the SAME line!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: ContentType tabs
                    Row(
                        modifier = Modifier.weight(1.4f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            ContentType.LIVE to "Ao Vivo",
                            ContentType.MOVIE to "Filmes",
                            ContentType.SERIES to "Séries"
                        ).forEach { (type, label) ->
                            val isSelected = contentType == type
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.changeContentType(type) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .height(3.dp)
                                        .width(24.dp)
                                        .background(if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color.Transparent)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right: Compact legible Search Bar (Using BasicTextField to prevent vertical cut-off completely!)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(36.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF09090C), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            singleLine = true,
                            maxLines = 1,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(Color.White),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Buscar...",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.setSearchQuery("") },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Direct Quick Sort Button on Home Screen (Portrait)
                    IconButton(
                        onClick = { showSortOrderDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Classificar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Category submenu selection Row (using REAL data)
            if (searchQuery.isEmpty() && categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = activeCategory == cat
                        Card(
                            onClick = { viewModel.selectCategory(cat) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color(0xFF111115)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = cat,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (backgroundLoadingState != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161111)),
                    border = BorderStroke(1.dp, com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = com.example.ui.theme.SophisticatedRedStart,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = backgroundLoadingState ?: "",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Highlights Slider Carousel Banner
                if (searchQuery.isEmpty() && highlights.isNotEmpty()) {
                    item {
                        HighlightBanner(highlights = highlights, onPlayItem = { viewModel.playContent(it) })
                    }
                }

                // Continue Watching Section
                if (searchQuery.isEmpty() && continueWatching.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Continuar Assistindo",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = GoldPremium,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                letterSpacing = 0.5.sp
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(continueWatching) { item ->
                                    Card(
                                        onClick = { viewModel.playContent(item) },
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(96.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(enhancedLogoFallback(item.logoUrl, item.name))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = item.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            // Play icon center overlay
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                    .padding(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Continuar assistindo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            // Title at the bottom
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomStart)
                                                    .background(Color.Black.copy(alpha = 0.82f))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Grid Items List
                item {
                    val count = itemsList.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Resultados da Busca" else activeCategory,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "$count itens found",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                if (itemsList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.SentimentDissatisfied, contentDescription = "None found", tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Nenhum canal ou vídeo carregado.\nTente clicar em atualizar ou conferir credenciais.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                if (errorMsg != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Detalhes: $errorMsg",
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = Color.Red.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (contentType == ContentType.SERIES) {
                    // Smart Netflix-style Series Grouping layout using optimized remembered chunks!
                    items(processedSeriesChunks) { rowSeries ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (series in rowSeries) {
                                Box(modifier = Modifier.weight(1f)) {
                                    GroupedSeriesCard(
                                        series = series,
                                        onClick = { selectedSeriesForDetail = series }
                                    )
                                }
                            }
                            if (rowSeries.size < gridColumnsCount) {
                                for (i in 0 until (gridColumnsCount - rowSeries.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // Optimized Grid Layout using custom grouping chunk loops and remembered list processing!
                    items(processedNormalChunks) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (item in rowItems) {
                                Box(modifier = Modifier.weight(1f)) {
                                    GridItemCard(
                                        item = item,
                                        isGridCompact = contentType != ContentType.LIVE,
                                        onClick = { viewModel.playContent(item) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item) }
                                    )
                                }
                            }
                            // Filler boxes to balance columns nicely
                            if (rowItems.size < gridColumnsCount) {
                                for (i in 0 until (gridColumnsCount - rowItems.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Percentage-based Load HUD Overlay with progressive state labels
        if (loadingProgress != null && loadingProgress!! < 100) {
            val statusText = when {
                loadingProgress!! <= 15 -> "Conectando ao servidor..."
                loadingProgress!! <= 45 -> "Baixando canais e mídias..."
                loadingProgress!! <= 80 -> "Processando mídias recebidas..."
                loadingProgress!! <= 95 -> "Salvando dados no banco..."
                else -> "Finalizando sincronização..."
            }

            Dialog(onDismissRequest = {}) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (loadingProgress ?: 0).toFloat() / 100f },
                            modifier = Modifier.size(90.dp),
                            color = NetflixRed,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "$loadingProgress%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Buscando Playlist",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Series Detail and Episode picker dialog
        if (selectedSeriesForDetail != null) {
            val series = selectedSeriesForDetail!!
            val groupedSeasons = remember(series.episodes) {
                groupEpisodesBySeason(series.episodes)
            }
            val seasonList = remember(groupedSeasons) { groupedSeasons.keys.toList() }
            var selectedSeason by remember(seasonList) {
                mutableStateOf(seasonList.firstOrNull() ?: "")
            }
            val episodesInSeason = remember(selectedSeason, groupedSeasons) {
                groupedSeasons[selectedSeason] ?: emptyList()
            }
            Dialog(onDismissRequest = { selectedSeriesForDetail = null }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0E)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(enhancedLogoFallback(series.logoUrl, series.title))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = series.title,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = series.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${series.episodes.size} episódios • ${series.category}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { selectedSeriesForDetail = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close episodes list", tint = Color.White)
                            }
                        }
                        
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Seasons Horizontal Chips Selector if more than 1 season exists
                        if (seasonList.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                items(seasonList) { season ->
                                    val isCurrent = season == selectedSeason
                                    Card(
                                        onClick = { selectedSeason = season },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrent) com.example.ui.theme.SophisticatedRedStart else Color(0xFF1B1A1E)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isCurrent) Color.Transparent else Color.White.copy(alpha = 0.08f)
                                        )
                                    ) {
                                        Text(
                                            text = season,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = if (selectedSeason.isNotEmpty()) "$selectedSeason - Episódios Disponíveis" else "Episódios Disponíveis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPremium,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(episodesInSeason) { episode ->
                                Card(
                                    onClick = {
                                        viewModel.playContent(episode)
                                        selectedSeriesForDetail = null
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play episode",
                                            tint = NetflixRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = episode.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PIN protection input Dialog prompt
        val activePinItem by viewModel.activePinPromptItem.collectAsState()
        if (activePinItem != null) {
            AdultPinDialog(
                onDismiss = { viewModel.dismissPinPrompt() },
                onPinVerify = { pin ->
                    viewModel.checkPinAndPlay(pin)
                }
            )
        }

        val activePinCategory by viewModel.activePinPromptCategory.collectAsState()
        if (activePinCategory != null) {
            AdultPinDialog(
                onDismiss = { viewModel.dismissCategoryPinPrompt() },
                onPinVerify = { pin ->
                    viewModel.checkPinAndPlay(pin)
                }
            )
        }

        // Complete fullscreen video player overlay
        if (currentPlayingItem != null) {
            VideoPlayerUI(
                playlistItem = currentPlayingItem!!,
                onClosePlayback = { viewModel.closePlayback() },
                onPlayPrevious = { viewModel.playPrevious() },
                onPlayNext = { viewModel.playNext() }
            )
        }

        if (showSortOrderDialog) {
            SettingsSelectionDialog(
                title = "Ordenação dos Menus/Canais",
                options = listOf(
                    "Ordem por número",
                    "Ordem por adição",
                    "Ordem por qualificação",
                    "Ordem por A-Z",
                    "Ordem por Z-A"
                ),
                currentValue = viewModel.preferencesService.menuSortOrder,
                onDismiss = { showSortOrderDialog = false },
                onOptionSelected = {
                    viewModel.updateMenuSortOrder(it)
                    showSortOrderDialog = false
                }
            )
        }
    }
}

/**
 * FEATURE HIGH INTERACTIVE HIGHLIGHT BANNER
 */
@Composable
fun HighlightBanner(highlights: List<PlaylistItem>, onPlayItem: (PlaylistItem) -> Unit) {
    var activeIndex by remember { mutableIntStateOf(0) }
    
    // Auto slider looping every 3 seconds
    LaunchedEffect(highlights) {
        activeIndex = 0 // Reset sliding index when highlights list changes (like changing playlist)
        while (true) {
            delay(3000)
            if (highlights.isNotEmpty()) {
                activeIndex = (activeIndex + 1) % highlights.size
            }
        }
    }

    if (highlights.isEmpty()) return
    val safeActiveIndex = if (activeIndex >= 0 && activeIndex < highlights.size) {
        activeIndex
    } else {
        0
    }
    val highlightedItem = highlights[safeActiveIndex]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(22.dp))
            .clickable { onPlayItem(highlightedItem) }
    ) {
        // Logo image / Banner image background with fallback
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(enhancedLogoFallback(highlightedItem.logoUrl, highlightedItem.name))
                .crossfade(true)
                .build(),
            contentDescription = "Highlight banner logo background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.85f
        )

        // Bottom gradient darkening
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent, 
                            Color.Black.copy(alpha = 0.3f), 
                            com.example.ui.theme.SophisticatedBg.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Top info badge "DESTAQUE DA SEMANA"
        Row(
            modifier = Modifier
                .padding(14.dp)
                .align(Alignment.TopStart)
        ) {
            Badge(
                containerColor = com.example.ui.theme.SophisticatedRedStart, 
                contentColor = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "DESTAQUE DA SEMANA", 
                    fontWeight = FontWeight.Black, 
                    fontSize = 8.sp, 
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Info contents
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = highlightedItem.category.uppercase(),
                fontSize = 9.sp,
                color = com.example.ui.theme.GoldPremium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = highlightedItem.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "IPTV Especial • 2026 • 4K Ultra HD • Estéreo",
                fontSize = 9.5.sp,
                color = com.example.ui.theme.SlateTextMuted,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            
            // Buttons Row matching HTML design
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: "Assistir Agora" (Primary white action button)
                Button(
                    onClick = { onPlayItem(highlightedItem) },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play icon",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Assistir Agora", 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Button 2: Glassmorphic Add/Action icon button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onPlayItem(highlightedItem) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add playlist item icon",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * GROUPED SERIES GRID CARD COMPOSABLE
 */
@Composable
fun GroupedSeriesCard(series: GroupedSeries, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(enhancedLogoFallback(series.logoUrl, series.title))
                        .crossfade(true)
                        .build(),
                    contentDescription = series.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Episode Count Badge
                Box(modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${series.episodes.size} Eps", 
                        fontSize = 8.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = GoldPremium
                    )
                }
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF09090C))
                .padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = series.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%01d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun getCurrentEpgProgram(channelName: String): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val name = channelName.uppercase()
    return when {
        name.contains("GLOBO") -> {
            when {
                hour in 6..8 -> "Bom Dia Brasil"
                hour in 9..11 -> "Mais Você"
                hour in 12..13 -> "Praça TV - 1ª Edição"
                hour in 14..16 -> "Sessão da Tarde"
                hour in 17..19 -> "Vale a Pena Ver de Novo"
                hour in 20..21 -> "Jornal Nacional"
                hour in 22..23 -> "Novela das Nove"
                else -> "Cinema Espetacular"
            }
        }
        name.contains("RECORD") -> {
            when (hour) {
                in 6..8 -> "Balanço Geral Manhã"
                in 12..14 -> "Balanço Geral"
                in 15..17 -> "Cidade Alerta"
                in 20..21 -> "Jornal da Record"
                else -> "Super Tela"
            }
        }
        name.contains("SBT") -> {
            when (hour) {
                in 6..8 -> "Primeiro Impacto"
                in 13..14 -> "Chaves"
                in 20..21 -> "Romeu e Julieta"
                in 22..23 -> "Programa do Ratinho"
                else -> "Cine Espetacular"
            }
        }
        name.contains("BAND") -> {
            when (hour) {
                in 12..13 -> "Jogo Aberto"
                in 14..15 -> "Os Donos da Bola"
                in 16..18 -> "Brasil Urgente"
                in 19..20 -> "Jornal da Band"
                else -> "Esporte Total"
            }
        }
        name.contains("SPORT") || name.contains("ESPN") || name.contains("PREMIERE") -> {
            when (hour) {
                in 10..12 -> "Mesa Redonda ao Vivo"
                in 13..15 -> "Futebol Ao Vivo / Gols da Rodada"
                in 16..18 -> "SportsCenter"
                in 19..22 -> "Brasileirão Campeonato Ao Vivo"
                else -> "Linha de Passe"
            }
        }
        name.contains("TELE") || name.contains("HBO") || name.contains("CINEMA") || name.contains("WARNER") || name.contains("UNIVERSAL") -> {
            when (hour) {
                in 13..15 -> "Filme Extra: Batman - O Cavaleiro das Trevas"
                in 16..18 -> "Filme Blockbuster: Duna Parte 2"
                in 19..21 -> "Série do Ano: House of the Dragon"
                in 22..23 -> "Filme Lançamento: Oppenheimer"
                else -> "Sessão Cult de Cinema"
            }
        }
        else -> {
            when (hour) {
                in 6..11 -> "Programação Matinal"
                in 12..17 -> "Tarde de Entretenimento"
                in 18..22 -> "Jornalismo & Show do Intervalo"
                else -> "Sessão Corujão / Madrugada de Filmes"
            }
        }
    }
}

/**
 * INDIVIDUAL GRID ROW ITEM DISPLAY CARD
 */
@Composable
fun GridItemCard(item: PlaylistItem, isGridCompact: Boolean, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isGridCompact) 146.dp else 96.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isGridCompact) {
                // Cinematic Style Portrait Card for Movies/Series
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(enhancedLogoFallback(item.logoUrl, item.name))
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Upper Right Badge (HD, 4K, NOVO, 18+) matching HTML
                        val movieBadge = remember(item.id) {
                            val code = item.id.hashCode() % 3
                            when (code) {
                                0 -> "4K"
                                1 -> "HD"
                                else -> "NOVO"
                            }
                        }
                        Box(modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (item.isAdult) "18+" else movieBadge, 
                                fontSize = 8.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (item.isAdult) Color.Red else Color.White
                            )
                        }

                        // Upper Left Favorite button overlay
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) Color.Red else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090C))
                        .padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = item.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                // Wide Landscape Card for TV Channels
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo box with Slate background matching HTML
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(Color.White.copy(alpha = 0.03f))
                            .padding(12.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(enhancedLogoFallback(item.logoUrl, item.name))
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = item.category.uppercase(),
                            fontSize = 8.sp,
                            color = com.example.ui.theme.GoldPremium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "NO AR: " + getCurrentEpgProgram(item.name),
                                fontSize = 9.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    if (item.isAdult) {
                        Box(modifier = Modifier
                            .padding(end = 4.dp)
                            .background(Color.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("18+", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Favorite Button for TV Channels
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) Color.Red else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Streaming progress indicator at the very bottom of live channels card matching HTML
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.05f))
                        .align(Alignment.BottomStart)
                ) {
                    val streamProgress = remember(item.id) {
                        val code = item.id.hashCode()
                        val progressBase = (code % 40) + 40 // Stable deterministic mock between 40% and 80%
                        progressBase / 100f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(streamProgress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(com.example.ui.theme.SophisticatedRedStart, Color(0xFFC01E1E))
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * ADULT SECURITY PASSCODE PROMPT
 */
@Composable
fun AdultPinDialog(onDismiss: () -> Unit, onPinVerify: (String) -> Boolean) {
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Adult locked content",
                    tint = com.example.ui.theme.SophisticatedRedStart,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Conteúdo Adulto Protegido",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "Insira o PIN de acesso de 4 dígitos",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pinValue,
                    onValueChange = {
                        if (it.length <= 4) {
                            pinValue = it
                            pinError = false
                        }
                    },
                    modifier = Modifier.width(140.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 10.sp,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedLabelColor = com.example.ui.theme.SophisticatedRedStart
                    )
                )

                if (pinError) {
                    Text(
                        text = "PIN incorreto. Tente novamente.",
                        color = Color.Red,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Voltar", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (pinValue.isNotEmpty()) {
                                val success = onPinVerify(pinValue)
                                if (!success) {
                                    pinError = true
                                }
                            } else {
                                pinError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SophisticatedRedStart)
                    ) {
                        Text("Confirmar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * COMPREHENSIVE EXO PLAYER COMPOSABLE CONTROL OVERLAY
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerUI(
    playlistItem: PlaylistItem,
    onClosePlayback: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    var isPlaying by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    
    // Gestures and control states
    var brightnessValue by remember { mutableFloatStateOf(1.0f) }
    var volumeValue by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var lastVolumeBeforeMute by remember { mutableFloatStateOf(0.5f) }
    
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    
    var controlsVisible by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showAspectOverlay by remember { mutableStateOf(false) }
    var aspectOverlayText by remember { mutableStateOf("Ajustar (Original)") }
    var aspectOverlayJob by remember { mutableStateOf<Job?>(null) }
    
    var overlayDismissJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val toggleMute: () -> Unit = {
        if (isMuted) {
            isMuted = false
            volumeValue = lastVolumeBeforeMute.coerceAtLeast(0.1f)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (volumeValue * maxVol).toInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        } else {
            isMuted = true
            lastVolumeBeforeMute = volumeValue
            volumeValue = 0f
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }
    }

    // Edge-to-edge / Immersive Fullscreen Mode management
    DisposableEffect(Unit) {
        val window = activity?.window
        var originalCutoutMode: Int? = null
        if (window != null && activity?.isFinishing == false && activity?.isDestroyed == false) {
            try {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                // Draw under camera cutout and notches for seamless fullscreen across all brands (Xiaomi, Samsung, etc.)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    originalCutoutMode = lp.layoutInDisplayCutoutMode
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = lp
                }

                // Auto initialize system brightness matching
                val lp = window.attributes
                if (lp.screenBrightness > 0f) {
                    brightnessValue = lp.screenBrightness
                }
            } catch (e: Exception) {
                // Prevent crash if layout is in transition
            }
        }
        
        // Auto initialize volume percentage matching
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                volumeValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol.toFloat()
            }
        } catch (e: Exception) {
            // Audio manager state error safety
        }

        onDispose {
            if (window != null && activity?.isFinishing == false && activity?.isDestroyed == false) {
                try {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                        val lp = window.attributes
                        lp.layoutInDisplayCutoutMode = originalCutoutMode
                        window.attributes = lp
                    }
                } catch (e: Exception) {
                    // Prevent any detached window / decorView crashes during cleanup
                }
            }
        }
    }

    // Auto-hide playback controls after 5 seconds of inactivity
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
            showSpeedMenu = false
        }
    }

    // Standard media3 ExoPlayer controller setup with proper scopes
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Remembered PlayerView reference to guarantee order of synchronization upon disposal without leaking context
    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(playlistItem) {
        errorMessage = null
        val url = playlistItem.url.trim()
        if (url.isEmpty()) {
            errorMessage = "A URL de transmissão está vazia."
            isBuffering = false
            return@LaunchedEffect
        }
        try {
            isBuffering = true
            // Stop and clear previous playback state first to prevent native decoding deadlocks/crashes!
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (e: Exception) {
                // Ignore player transient reset errors
            }
            val item = MediaItem.fromUri(url)
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            errorMessage = "Erro ao carregar mídia: ${e.localizedMessage ?: "Causa desconhecida"}"
            isBuffering = false
        }
    }

    DisposableEffect(exoPlayer) {
        // Apply Wakelock equivalent flag on creation to keep screen active
        val window = activity?.window
        if (activity?.isFinishing == false && activity?.isDestroyed == false) {
            try {
                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (e: Exception) {
                // Safety catch
            }
        }
        
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Impossível reproduzir canal/mídia. Conexão terminada pelo link."
                isBuffering = false
                try {
                    exoPlayer.stop() // Immediately free hardware decoder and avoid ANR/Main Thread starvation!
                } catch (e: Exception) {
                    // safety clean
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            try {
                playerViewInstance?.player = null // DETACH FIRST to prevent native surface / crash thread race condition
                playerViewInstance = null
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                // Ignore player cleanup errors
            }
            if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                try {
                    window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } catch (e: Exception) {
                    // Prevent crash during window detachment
                }
            }
        }
    }

    // Capture hardware backpress to exit playback instead of exiting app
    BackHandler {
        onClosePlayback()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragSide = if (down.position.x < size.width / 2f) 1 else 2 // 1: Left (Brightness), 2: Right (Volume)
                    var totalDragY = 0f
                    var isDrag = false
                    
                    drag(down.id) { change ->
                        val dragAmount = change.positionChange()
                        totalDragY += Math.abs(dragAmount.y)
                        
                        // Drag threshold
                        if (totalDragY > 15f) {
                            isDrag = true
                        }
                        
                        if (isDrag) {
                            change.consume()
                            val delta = -dragAmount.y / size.height.toFloat() * 1.5f
                            
                            if (dragSide == 1) {
                                // Left-side Swipe: Screen Brightness
                                brightnessValue = (brightnessValue + delta).coerceIn(0.01f, 1.0f)
                                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                                    activity.runOnUiThread {
                                        try {
                                            val lp = activity.window.attributes
                                            lp.screenBrightness = brightnessValue
                                            activity.window.attributes = lp
                                        } catch (e: Exception) {
                                            // safety block
                                        }
                                    }
                                }
                                showBrightnessOverlay = true
                                showVolumeOverlay = false
                            } else {
                                // Right-side Swipe: Audio volume
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                var volPercent = volumeValue
                                if (isMuted && delta > 0) {
                                    isMuted = false
                                    volPercent = lastVolumeBeforeMute
                                }
                                volPercent = (volPercent + delta).coerceIn(0f, 1.0f)
                                if (volPercent > 0.01f) {
                                    isMuted = false
                                }
                                
                                val targetVol = (volPercent * maxVol).toInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                volumeValue = volPercent
                                
                                showVolumeOverlay = true
                                showBrightnessOverlay = false
                            }
                            
                            // Visual overlay auto-dismiss timer
                            overlayDismissJob?.cancel()
                            overlayDismissJob = coroutineScope.launch {
                                delay(1500)
                                showBrightnessOverlay = false
                                showVolumeOverlay = false
                            }
                        }
                    }
                    
                    // Tap toggle controls if it was a tap (not drag)
                    if (!isDrag) {
                        controlsVisible = !controlsVisible
                        if (!controlsVisible) {
                            showSpeedMenu = false
                        }
                    }
                }
            }
    ) {
        // Player Surface Component using AndroidView binding
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // Use custom beautiful overlay controls instead of native slop!
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewInstance = this
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
                view.resizeMode = resizeModeState
            },
            onRelease = { view ->
                view.player = null
                if (playerViewInstance == view) {
                    playerViewInstance = null
                }
            }
        )

        // Custom Visual Sidebar Overlays (Swipe Brightness & Volume)
        // Brightness sidebar (Left)
        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn() + slideInHorizontally { -it },
            exit = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Brightness5,
                    contentDescription = "Brightness Indicator",
                    tint = GoldPremium,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(brightnessValue)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(3.dp))
                            .background(GoldPremium)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${(brightnessValue * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Volume sidebar (Right)
        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(vertical = 16.dp)
            ) {
                IconButton(
                    onClick = { toggleMute() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute or Unmute Indicator",
                        tint = NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(volumeValue)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(3.dp))
                            .background(NetflixRed)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${(volumeValue * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Transient Aspect Ratio Overlay (Animated Center Badge)
        AnimatedVisibility(
            visible = showAspectOverlay,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, GoldPremium.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = when (resizeModeState) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.AspectRatio
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.Fullscreen
                        else -> Icons.Default.FullscreenExit
                    },
                    contentDescription = null,
                    tint = GoldPremium,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = aspectOverlayText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Cinematic Dark transparent gradient vignette overlay behind controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )
        }

        // Animated Interactive Control Center HUD
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClosePlayback,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close player screen", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = playlistItem.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Categoria: ${playlistItem.category}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Loading state indicator
                if (isBuffering && errorMessage == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NetflixRed, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Carregando stream...", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }

                // Error and retry indicator
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xCC3E1215)),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        isBuffering = true
                                        val url = playlistItem.url.trim()
                                        try {
                                            exoPlayer.stop()
                                            exoPlayer.clearMediaItems()
                                            val item = MediaItem.fromUri(url)
                                            exoPlayer.setMediaItem(item)
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        } catch (e: Exception) {
                                            errorMessage = "Erro ao carregar mídia: ${e.localizedMessage ?: "Causa desconhecida"}"
                                            isBuffering = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                                ) {
                                    Text("Tentar Novamente")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Playback speed selection bar popup
                AnimatedVisibility(
                    visible = showSpeedMenu,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { speed ->
                            val isSelected = currentSpeed == speed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NetflixRed else Color.Transparent)
                                    .clickable {
                                        currentSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${speed}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Progress Seek bar for Movies and Series (Track time progression)
                if (playlistItem.contentType == ContentType.MOVIE.name || playlistItem.contentType == ContentType.SERIES.name) {
                    var currentPos by remember { mutableLongStateOf(0L) }
                    var duration by remember { mutableLongStateOf(0L) }

                    LaunchedEffect(exoPlayer) {
                        while (isActive) {
                            try {
                                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                                    currentPos = exoPlayer.currentPosition
                                    duration = exoPlayer.duration.coerceAtLeast(0L)
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                // Ignore if player is released or in error state
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                // Severe native / low level crashes bypass or release safety
                            }
                            delay(500)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formatTime(currentPos),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = currentPos.toFloat(),
                            onValueChange = {
                                try {
                                    exoPlayer.seekTo(it.toLong())
                                    currentPos = it.toLong()
                                } catch (e: Exception) {
                                    // Ignore if player is stopped or released
                                }
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = NetflixRed,
                                activeTrackColor = NetflixRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom Player bar controllers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Playback Speed & Aspect Ratio Controller Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            TextButton(
                                onClick = { showSpeedMenu = !showSpeedMenu },
                                colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                if (isLandscape) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${currentSpeed}x",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                resizeModeState = when (resizeModeState) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                                        aspectOverlayText = "Esticar (Preencher)"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    }
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                                        aspectOverlayText = "Zoom (Cortar)"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    }
                                    else -> {
                                        aspectOverlayText = "Ajustar (Original)"
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                                showAspectOverlay = true
                                aspectOverlayJob?.cancel()
                                aspectOverlayJob = coroutineScope.launch {
                                    delay(1500)
                                    showAspectOverlay = false
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = when (resizeModeState) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.AspectRatio
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.Fullscreen
                                    else -> Icons.Default.FullscreenExit
                                },
                                contentDescription = "Alternar Modo de Proporção",
                                tint = GoldPremium,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { toggleMute() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Toggle Mute volume",
                                tint = GoldPremium,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Combined Play, Pause, Previous, Next controls centered
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = onPlayPrevious,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Canal Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                isPlaying = !isPlaying
                                exoPlayer.playWhenReady = isPlaying
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play button indicator",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onPlayNext,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Próximo Canal",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Audio track switcher dialog overlay button trigger
                    var showAudioSubDialog by remember { mutableStateOf(false) }
                    if (isLandscape) {
                        TextButton(
                            onClick = { showAudioSubDialog = true },
                            colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Legendas e Áudio",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Áudio e Legenda",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { showAudioSubDialog = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Legendas e Áudio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (showAudioSubDialog) {
                        var selectedAudio by remember { mutableStateOf("") }
                        var selectedSub by remember { mutableStateOf("des") }

                        val currentTracks = exoPlayer.currentTracks
                        val audioTracks = remember(currentTracks) {
                            val list = mutableListOf<Pair<String, String>>()
                            for (group in currentTracks.groups) {
                                if (group.type == 1) { // 1 = C.TRACK_TYPE_AUDIO
                                    for (i in 0 until group.length) {
                                        if (group.isTrackSupported(i)) {
                                            val format = group.getTrackFormat(i)
                                            val lang = format.language ?: ""
                                            val label = format.label ?: ""
                                            val friendlyName = when (lang.lowercase()) {
                                                "por", "pt", "pt-br" -> "Português"
                                                "eng", "en" -> "Inglês"
                                                "spa", "es" -> "Espanhol"
                                                "fra", "fr" -> "Francês"
                                                "ita", "it" -> "Italiano"
                                                "deu", "de" -> "Alemão"
                                                else -> label.ifEmpty { lang.uppercase().ifEmpty { "Áudio ${i + 1}" } }
                                            }
                                            if (list.none { it.first == lang }) {
                                                list.add(lang to friendlyName)
                                            }
                                        }
                                    }
                                }
                            }
                            list
                        }

                        val subtitleTracks = remember(currentTracks) {
                            val list = mutableListOf<Pair<String, String>>()
                            for (group in currentTracks.groups) {
                                if (group.type == 3) { // 3 = C.TRACK_TYPE_TEXT
                                    for (i in 0 until group.length) {
                                        if (group.isTrackSupported(i)) {
                                            val format = group.getTrackFormat(i)
                                            val lang = format.language ?: ""
                                            val label = format.label ?: ""
                                            val friendlyName = when (lang.lowercase()) {
                                                "por", "pt", "pt-br" -> "Português"
                                                "eng", "en" -> "Inglês"
                                                "spa", "es" -> "Espanhol"
                                                "fra", "fr" -> "Francês"
                                                "ita", "it" -> "Italiano"
                                                "deu", "de" -> "Alemão"
                                                else -> label.ifEmpty { lang.uppercase().ifEmpty { "Legenda ${i + 1}" } }
                                            }
                                            if (list.none { it.first == lang }) {
                                                list.add(lang to friendlyName)
                                            }
                                        }
                                    }
                                }
                            }
                            list
                        }

                        LaunchedEffect(currentTracks) {
                            selectedAudio = exoPlayer.trackSelectionParameters.preferredAudioLanguages.firstOrNull() ?: ""
                            selectedSub = exoPlayer.trackSelectionParameters.preferredTextLanguages.firstOrNull() ?: "des"
                        }

                        Dialog(onDismissRequest = { showAudioSubDialog = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Áudio & Legendas",
                                        color = GoldPremium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        // Audio Column
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("ÁUDIO", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            // Se não houver mais de uma opção para trocar o áudio, aparece vazio
                                            if (audioTracks.size > 1) {
                                                audioTracks.forEach { (code, label) ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                selectedAudio = code
                                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                                    .buildUpon()
                                                                    .setPreferredAudioLanguage(code)
                                                                    .build()
                                                            }
                                                            .padding(vertical = 6.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = selectedAudio == code,
                                                            onClick = {
                                                                selectedAudio = code
                                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                                    .buildUpon()
                                                                    .setPreferredAudioLanguage(code)
                                                                    .build()
                                                            },
                                                            colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(label, color = Color.White, fontSize = 12.sp)
                                                    }
                                                }
                                            } else {
                                                // Aparece vazio conforme solicitado pelo usuário
                                                Spacer(modifier = Modifier.height(80.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Subtitle Column
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("LEGENDAS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            // Se não houver opções de legenda para trocar, aparece vazio
                                            if (subtitleTracks.isNotEmpty()) {
                                                val subsWithDisable = listOf("des" to "Desativado") + subtitleTracks
                                                subsWithDisable.forEach { (code, label) ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                selectedSub = code
                                                                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                                                if (code == "des") {
                                                                    builder.setPreferredTextLanguage(null)
                                                                } else {
                                                                    builder.setPreferredTextLanguage(code)
                                                                }
                                                                exoPlayer.trackSelectionParameters = builder.build()
                                                            }
                                                            .padding(vertical = 6.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = selectedSub == code,
                                                            onClick = {
                                                                selectedSub = code
                                                                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                                                if (code == "des") {
                                                                    builder.setPreferredTextLanguage(null)
                                                                } else {
                                                                    builder.setPreferredTextLanguage(code)
                                                                }
                                                                exoPlayer.trackSelectionParameters = builder.build()
                                                            },
                                                            colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(label, color = Color.White, fontSize = 12.sp)
                                                    }
                                                }
                                            } else {
                                                // Aparece vazio conforme solicitado pelo usuário
                                                Spacer(modifier = Modifier.height(80.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = { showAudioSubDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("CONCLUIR", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SETTINGS CONTROL CONFIGURATIONS SCREEN
 */
@Composable
fun SettingsScreen(viewModel: AppViewModel, onNavigateBack: () -> Unit) {
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val activePlaylist by viewModel.activePlaylistName.collectAsState()
    val isPremiumActive by viewModel.isPremiumActive.collectAsState()
    val trialDaysLeft by viewModel.trialDaysLeft.collectAsState()
    val adultPin = viewModel.preferencesService.adultPin
    
    var snackbarVisible by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("Configurações atualizadas com sucesso!") }
    
    var showParentalControlDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTimeFormatDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showLiveStreamFormatDialog by remember { mutableStateOf(false) }
    var showSubtitleSizeDialog by remember { mutableStateOf(false) }
    var showDeviceTypeDialog by remember { mutableStateOf(false) }
    var showHideLiveCategoriesDialog by remember { mutableStateOf(false) }
    var showExternalPlayerDialog by remember { mutableStateOf(false) }
    var showClearMoviesHistoryDialog by remember { mutableStateOf(false) }
    var showClearLiveHistoryDialog by remember { mutableStateOf(false) }
    var showPlaylistsDialog by remember { mutableStateOf(false) }
    var showUpdateNowDialog by remember { mutableStateOf(false) }
    var showSortOrderDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showStagedLoadingDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0E0E))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Ajustes / Configurações",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Server connection card (compact info)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Servidor: $activePlaylist",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Usuário Logado: $username",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PREFERÊNCIAS SISTEMA (COMPACTO)",
                color = GoldPremium,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Dynamic grid list of 9 clean config options utilizing basic guaranteed compiling icons
            val configList = listOf(
                "Controle dos pais" to Icons.Default.Lock,
                "Limpar histórico de filmes" to Icons.Default.Delete,
                "Leitor externo" to Icons.Default.PlayArrow,
                "Atualizar agora" to Icons.Default.Refresh,
                "Limpar canais de histórico" to Icons.Default.ClearAll,
                "Configurações de legenda" to Icons.Default.Info,
                "Ordenação do menu" to Icons.Default.Sort,
                "Carregamento em etapas" to Icons.Default.List,
                "Licença e Ativação" to Icons.Default.VpnKey
            )
 
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                configList.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { (title, icon) ->
                            Card(
                                onClick = {
                                    when (title) {
                                        "Controle dos pais" -> showParentalControlDialog = true
                                        "Limpar histórico de filmes" -> showClearMoviesHistoryDialog = true
                                        "Leitor externo" -> showExternalPlayerDialog = true
                                        "Atualizar agora" -> showUpdateNowDialog = true
                                        "Limpar canais de histórico" -> showClearLiveHistoryDialog = true
                                        "Configurações de legenda" -> showSubtitleSizeDialog = true
                                        "Ordenação do menu" -> showSortOrderDialog = true
                                        "Carregamento em etapas" -> showStagedLoadingDialog = true
                                        "Licença e Ativação" -> showLicenseDialog = true
                                    }
                                },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = title,
                                        tint = GoldPremium,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        // Dynamic interactive value descriptor display below title
                                        val subtext = when (title) {
                                            "Controle dos pais" -> "Segurança active"
                                            "Limpar histórico de filmes" -> "Apagar Continue Assistindo"
                                            "Leitor externo" -> if (viewModel.preferencesService.useExternalPlayer) "Player Externo" else "Player Interno"
                                            "Atualizar agora" -> "Sincronizar m3u"
                                            "Limpar canais de histórico" -> "Apagar histórico canais"
                                            "Configurações de legenda" -> viewModel.preferencesService.subtitleConfig
                                            "Ordenação do menu" -> viewModel.preferencesService.menuSortOrder
                                            "Carregamento em etapas" -> {
                                                val list = mutableListOf<String>()
                                                if (viewModel.preferencesService.loadLiveInForeground) list.add("Canais")
                                                if (viewModel.preferencesService.loadMoviesInForeground) list.add("Filmes")
                                                if (viewModel.preferencesService.loadSeriesInForeground) list.add("Séries")
                                                if (list.isEmpty()) "Segundo Plano" else "1º Plano: " + list.joinToString(", ")
                                            }
                                            "Licença e Ativação" -> if (isPremiumActive) "Premium Ativo" else "$trialDaysLeft dias"
                                            else -> ""
                                        }
                                        
                                        if (subtext.isNotEmpty()) {
                                            Text(
                                                text = subtext,
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (pair.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer version
            Text(
                text = "MK21 MultiServidor v1.21.PRO - Android Engine",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // CONTROL DOS PAIS DIALOG POPUP (Parental PIN password changer matching exact requested attachment fields)
        if (showParentalControlDialog) {
            var currentInput by remember { mutableStateOf("") }
            var newInput by remember { mutableStateOf("") }
            var confirmInput by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            Dialog(onDismissRequest = { showParentalControlDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00004C)), // Beautiful deep blue/indigo background mimicking the attachment
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, Color.White),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Controle dos pais",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        OutlinedTextField(
                            value = currentInput,
                            onValueChange = { currentInput = it },
                            label = { Text("Senha", color = Color.White.copy(alpha = 0.7f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        OutlinedTextField(
                            value = newInput,
                            onValueChange = { newInput = it },
                            label = { Text("Nova Senha", color = Color.White.copy(alpha = 0.7f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        OutlinedTextField(
                            value = confirmInput,
                            onValueChange = { confirmInput = it },
                            label = { Text("Confirme sua senha:", color = Color.White.copy(alpha = 0.7f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showParentalControlDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (currentInput != adultPin) {
                                        errorMessage = "Senha atual incorreta!"
                                    } else if (newInput.isEmpty()) {
                                        errorMessage = "A nova senha não pode ser vazia!"
                                    } else if (newInput != confirmInput) {
                                        errorMessage = "As novas senhas não coincidem!"
                                    } else {
                                        viewModel.setAdultPin(newInput)
                                        showParentalControlDialog = false
                                        snackbarMessage = "Senha parental atualizada com sucesso!"
                                        snackbarVisible = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("OK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Language dialogue
        if (showLanguageDialog) {
            SettingsSelectionDialog(
                title = "Mudar Idioma",
                options = listOf("Português", "English", "Español"),
                currentValue = viewModel.preferencesService.appLanguage,
                onDismiss = { showLanguageDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.appLanguage = it
                    snackbarMessage = "Idioma alterado para $it!"
                    snackbarVisible = true
                }
            )
        }

        // Time format dialogue
        if (showTimeFormatDialog) {
            SettingsSelectionDialog(
                title = "Formato de Hora",
                options = listOf("12 horas (AM/PM)", "24 horas"),
                currentValue = viewModel.preferencesService.timeFormat,
                onDismiss = { showTimeFormatDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.timeFormat = it
                    snackbarMessage = "Formato de hora alterado para $it!"
                    snackbarVisible = true
                }
            )
        }

        // Layout choice dialogue
        if (showLayoutDialog) {
            SettingsSelectionDialog(
                title = "Layout do Aplicativo",
                options = listOf("Grid Clássico", "Lista Moderna", "Grade Compacta"),
                currentValue = viewModel.preferencesService.appLayout,
                onDismiss = { showLayoutDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.appLayout = it
                    snackbarMessage = "Layout do aplicativo definido como: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Live stream format dialogue
        if (showLiveStreamFormatDialog) {
            SettingsSelectionDialog(
                title = "Formato da Transmissão (MPEG/HLS)",
                options = listOf("MPEG-TS (.ts)", "HLS (.m3u8)"),
                currentValue = viewModel.preferencesService.liveStreamFormat,
                onDismiss = { showLiveStreamFormatDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.liveStreamFormat = it
                    snackbarMessage = "Formato de transmissão definido para $it!"
                    snackbarVisible = true
                }
            )
        }

        // Subtitles configuration dialogue
        if (showSubtitleSizeDialog) {
            SettingsSelectionDialog(
                title = "Tamanho das Legendas",
                options = listOf("Pequena", "Média (Padrão)", "Grande"),
                currentValue = viewModel.preferencesService.subtitleConfig,
                onDismiss = { showSubtitleSizeDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.subtitleConfig = it
                    snackbarMessage = "Legendas definidas para: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Menu/Channel Sorting configuration dialogue
        if (showSortOrderDialog) {
            SettingsSelectionDialog(
                title = "Ordenação dos Menus/Canais",
                options = listOf(
                    "Ordem por número",
                    "Ordem por adição",
                    "Ordem por qualificação",
                    "Ordem por A-Z",
                    "Ordem por Z-A"
                ),
                currentValue = viewModel.preferencesService.menuSortOrder,
                onDismiss = { showSortOrderDialog = false },
                onOptionSelected = {
                    viewModel.updateMenuSortOrder(it)
                    showSortOrderDialog = false
                    snackbarMessage = "Ordenação definida para: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Device Type dialogue
        if (showDeviceTypeDialog) {
            SettingsSelectionDialog(
                title = "Tipo de Dispositivo",
                options = listOf("Celular / Tablet", "TV Box / Android TV"),
                currentValue = viewModel.preferencesService.deviceType,
                onDismiss = { showDeviceTypeDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.deviceType = it
                    snackbarMessage = "Modo de dispositivo definido como: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Hide Live TV categories dialogue
        if (showHideLiveCategoriesDialog) {
            SettingsSelectionDialog(
                title = "Exibir Categorias Ao Vivo",
                options = listOf("Mostrar Categorias Ao Vivo", "Ocultar Categorias Ao Vivo"),
                currentValue = if (viewModel.preferencesService.hideLiveCategories) "Ocultar Categorias Ao Vivo" else "Mostrar Categorias Ao Vivo",
                onDismiss = { showHideLiveCategoriesDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.hideLiveCategories = (it == "Ocultar Categorias Ao Vivo")
                    snackbarMessage = "Categorias Ao Vivo foram configuradas como: $it"
                    snackbarVisible = true
                }
            )
        }

        // Video Player options dialogue
        if (showExternalPlayerDialog) {
            SettingsSelectionDialog(
                title = "Escolher Leitor de Vídeo",
                options = listOf("Usar Player Interno (Padrão)", "Usar Player Externo"),
                currentValue = if (viewModel.preferencesService.useExternalPlayer) "Usar Player Externo" else "Usar Player Interno (Padrão)",
                onDismiss = { showExternalPlayerDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.useExternalPlayer = (it == "Usar Player Externo")
                    snackbarMessage = "Configuração salva: $it"
                    snackbarVisible = true
                }
            )
        }

        // Dialog for Playlists Info
        if (showPlaylistsDialog) {
            Dialog(onDismissRequest = { showPlaylistsDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Listas de Canais",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Playlist Ativa:\n$activePlaylist",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Usuário conectado: $username\nServidores conectados ao portal de multisservidor MK21.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Button(
                            onClick = { showPlaylistsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ENTENDIDO", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showStagedLoadingDialog) {
            Dialog(onDismissRequest = { showStagedLoadingDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Carregamento em Etapas",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Selecione quais conteúdos carregar em Primeiro Plano. Os demais serão carregados em Segundo Plano de forma silenciosa e incremental para não travar o aplicativo.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        var liveChecked by remember { mutableStateOf(viewModel.preferencesService.loadLiveInForeground) }
                        var moviesChecked by remember { mutableStateOf(viewModel.preferencesService.loadMoviesInForeground) }
                        var seriesChecked by remember { mutableStateOf(viewModel.preferencesService.loadSeriesInForeground) }

                        // Custom Switch / Checkbox row
                        @Composable
                        fun StagedOptionRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCheckedChange(!checked) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = description, color = Color.Gray, fontSize = 9.sp)
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = onCheckedChange,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NetflixRed,
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color.White
                                    )
                                )
                            }
                        }

                        StagedOptionRow(
                            title = "Canais (Ao Vivo)",
                            description = "Carga instantânea de canais de TV",
                            checked = liveChecked,
                            onCheckedChange = { 
                                liveChecked = it
                                // Prevent checking nothing
                                if (!it && !moviesChecked && !seriesChecked) {
                                    liveChecked = true
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.05f)))

                        StagedOptionRow(
                            title = "Filmes (VOD)",
                            description = "Carregar catálogo de filmes na inicialização",
                            checked = moviesChecked,
                            onCheckedChange = { 
                                moviesChecked = it
                                if (!liveChecked && !it && !seriesChecked) {
                                    liveChecked = true
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.05f)))

                        StagedOptionRow(
                            title = "Séries (VOD)",
                            description = "Carregar catálogo de séries na inicialização",
                            checked = seriesChecked,
                            onCheckedChange = { 
                                seriesChecked = it
                                if (!liveChecked && !moviesChecked && !it) {
                                    liveChecked = true
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showStagedLoadingDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.preferencesService.loadLiveInForeground = liveChecked
                                    viewModel.preferencesService.loadMoviesInForeground = moviesChecked
                                    viewModel.preferencesService.loadSeriesInForeground = seriesChecked
                                    snackbarMessage = "Configurações de carga salvas com sucesso!"
                                    snackbarVisible = true
                                    showStagedLoadingDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("SALVAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for License & Activation
        if (showLicenseDialog) {
            var localKeyInput by remember { mutableStateOf("") }
            var licenseStatusMsg by remember { mutableStateOf<String?>(null) }
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val context = LocalContext.current
            val virtualMac = viewModel.virtualMacAddress

            Dialog(onDismissRequest = { showLicenseDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131111)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, GoldPremium.copy(alpha = 0.3f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Licença & Ativação",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPremiumActive) Color(0xFF1B5E20) else Color(0xFFE65100))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPremiumActive) "PREMIUM ATIVO" else "MODO AVALIAÇÃO: $trialDaysLeft DIAS",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Device Key with Copy Button
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B1B)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "CHAVE DO DISPOSITIVO (VIRTUAL MAC):",
                                    color = Color.Gray,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = virtualMac,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(virtualMac))
                                            android.widget.Toast.makeText(context, "Chave copiada!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar",
                                            tint = GoldPremium,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (licenseStatusMsg != null) {
                            Text(
                                text = licenseStatusMsg!!,
                                color = if (isPremiumActive) Color.Green else Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Input field to enter license
                        OutlinedTextField(
                            value = localKeyInput,
                            onValueChange = {
                                localKeyInput = it
                                licenseStatusMsg = null
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            label = { Text("Código de Ativação", color = Color.Gray, fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPremium,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = GoldPremium
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showLicenseDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (localKeyInput.isEmpty()) {
                                        licenseStatusMsg = "Insira um código válido"
                                    } else {
                                        val success = viewModel.activateLicense(localKeyInput)
                                        if (success) {
                                            licenseStatusMsg = "Premium Ativado!"
                                            android.widget.Toast.makeText(context, "Chave ativada com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            licenseStatusMsg = "Código inválido para este ID"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ATIVAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Clear Movie History
        if (showClearMoviesHistoryDialog) {
            Dialog(onDismissRequest = { showClearMoviesHistoryDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Limpar Histórico",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Deseja realmente apagar o seu histórico de filmes e séries assistidos (Continue Assistindo)?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showClearMoviesHistoryDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("NÃO", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.clearMoviesAndSeriesHistory()
                                    showClearMoviesHistoryDialog = false
                                    snackbarMessage = "Histórico de filmes e séries apagado!"
                                    snackbarVisible = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("SIM", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Clear Live TV History
        if (showClearLiveHistoryDialog) {
            Dialog(onDismissRequest = { showClearLiveHistoryDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Limpar Canais Assistidos",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Deseja realmente apagar o histórico dos canais ao vivo assistidos ultimamente?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showClearLiveHistoryDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("NÃO", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.clearLiveHistory()
                                    showClearLiveHistoryDialog = false
                                    snackbarMessage = "Histórico de canais ao vivo apagado!"
                                    snackbarVisible = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("SIM", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Update Playlist from Server
        if (showUpdateNowDialog) {
            Dialog(onDismissRequest = {}) { // non-dismissable during update
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    var isUpdating by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        viewModel.refreshActivePlaylist()
                        delay(1200)
                        isUpdating = false
                    }

                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sincronização",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (isUpdating) {
                            CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Buscando e indexando dados de $activePlaylist...",
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Playlist atualizada com sucesso no banco local!",
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { showUpdateNowDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Animated Confirmation toast saving preferences
        AnimatedVisibility(
            visible = snackbarVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = Color.Green, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snackbarMessage,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = { snackbarVisible = false },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("FECHAR", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Universal selection Dialog helper for premium responsive settings
 */
@Composable
fun SettingsSelectionDialog(
    title: String,
    options: List<String>,
    currentValue: String,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = GoldPremium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(option)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = option == currentValue,
                                onClick = {
                                    onOptionSelected(option)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Dynamic fallback vector placeholder images when channels/content doesn't have custom tvg-logo
 */
fun enhancedLogoFallback(logoUrl: String?, name: String): Any {
    if (!logoUrl.isNullOrEmpty() && logoUrl.startsWith("http")) {
        return logoUrl
    }
    
    // Choose appropriate fallback category vectors from placeholder service
    val identifier = name.hashCode().coerceAtLeast(0) % 6
    val placeholderColorHex = when (identifier) {
        0 -> "b1060f" // dark dark Red
        1 -> "1c1d1d" // Slate Blue
        2 -> "4a148c" // Purple glow
        3 -> "1a237e" // Navy depth
        4 -> "004d40" // Teal hue
        else -> "e50914" // standard red
    }
    // Return high quality visual label generator URL for flawless display integration!
    return "https://dummyimage.com/300x200/$placeholderColorHex/ffffff.png&text=${name.take(12)}"
}

fun groupEpisodesBySeason(episodes: List<com.example.data.model.PlaylistItem>): Map<String, List<com.example.data.model.PlaylistItem>> {
    val regexes = listOf(
        Regex("(?i)S(\\d+)"),
        Regex("(?i)T(\\d+)"),
        Regex("(?i)Temporada\\s*(\\d+)"),
        Regex("(?i)Temp\\.?\\s*(\\d+)")
    )
    val grouped = mutableMapOf<String, MutableList<com.example.data.model.PlaylistItem>>()
    for (episode in episodes) {
        var foundSeason = "Temporada 1"
        for (regex in regexes) {
            val match = regex.find(episode.name)
            if (match != null) {
                val numStr = match.groupValues[1]
                val num = numStr.toIntOrNull() ?: 1
                foundSeason = "Temporada $num"
                break
            }
        }
        grouped.getOrPut(foundSeason) { mutableListOf() }.add(episode)
    }
    return grouped.toSortedMap(compareBy { key ->
        val num = Regex("\\d+").find(key)?.value?.toIntOrNull() ?: 0
        num
    })
}
