package com.example.ui

import android.app.Activity
import android.view.ViewGroup
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
import androidx.compose.foundation.text.KeyboardOptions
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

@Composable
fun SophisticatedBrandHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCirc),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        // Logo Box with Red Gradient
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFD72323), Color(0xFF8B0000))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MK",
                fontSize = if (compact) 16.sp else 21.sp,
                fontWeight = FontWeight.Black,
                style = androidx.compose.ui.text.TextStyle(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    letterSpacing = (-1.5).sp
                ),
                color = Color.White
            )
        }

        // Title and Status
        Column {
            Text(
                text = "MULTISERVIDOR",
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = if (compact) 2.sp else 4.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 1.dp)
            ) {
                // Pulse Indicator
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(com.example.ui.theme.ActiveGreen.copy(alpha = alpha))
                )
                Text(
                    text = "SERVER-02 ATIVO",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.ActiveGreen,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
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

                        // USERNAME & PASSWORD IN A SINGLE CONTIGUOUS MODERN ROW WITH 15-CHAR LIMIT
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { if (it.length <= 15) viewModel.setCredentials(it, password) },
                                label = { Text("Usuário (máx 15)", fontSize = 10.sp, color = Color.Gray) },
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("username_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                    focusedContainerColor = Color(0xFF09090C),
                                    unfocusedContainerColor = Color(0xFF09090C),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { if (it.length <= 15) viewModel.setCredentials(username, it) },
                                label = { Text("Senha (máx 15)", fontSize = 10.sp, color = Color.Gray) },
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
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("password_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                    focusedContainerColor = Color(0xFF09090C),
                                    unfocusedContainerColor = Color(0xFF09090C),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = { (loadingProgress ?: 0).toFloat() / 100f },
                            modifier = Modifier.size(72.dp),
                            color = NetflixRed,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Buscando Playlist",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Por favor, aguarde...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "$loadingProgress%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NetflixRed
                        )
                    }
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
    val highlights by viewModel.highlightsList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPlayingItem by viewModel.currentPlayingItem.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()

    var showPlaylistMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.SophisticatedBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sophisticated Brand Logo
                SophisticatedBrandHeader(
                    compact = true,
                    modifier = Modifier.clickable { onNavigateToConfig() }
                )
                
                Spacer(modifier = Modifier.width(14.dp))
                
                // Integrated Server selector Dropdown Combo
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

                // Action controls: Refresh & Settings
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

            // Categories Selector Navigation TabRow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .height(3.dp)
                                .width(28.dp)
                                .background(if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color.Transparent)
                        )
                    }
                }
            }

            // Search Bar Input
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Buscar canais, filmes ou séries...", fontSize = 13.sp, color = Color.Gray) },
                maxLines = 1,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.Gray)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color(0xFF09090C),
                    unfocusedContainerColor = Color(0xFF09090C)
                )
            )

            // Category submenu selection Row (using REAL data)
            Spacer(modifier = Modifier.height(10.dp))
            if (searchQuery.isEmpty() && categories.isNotEmpty()) {
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
                } else {
                    // Optimized Grid Layout using custom grouping chunk loops inside LazyColumn
                    val gridColumnsCount = if (contentType == ContentType.LIVE) 2 else 3
                    val chunkedList = itemsList.chunked(gridColumnsCount)
                    
                    items(chunkedList) { rowItems ->
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
                                        onClick = { viewModel.playContent(item) }
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

        // Percentage-based Load HUD Overlay
        if (loadingProgress != null && loadingProgress!! < 100) {
            Dialog(onDismissRequest = {}) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = { (loadingProgress ?: 0).toFloat() / 100f },
                            modifier = Modifier.size(72.dp),
                            color = NetflixRed,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Atualizando Lista",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "$loadingProgress%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NetflixRed
                        )
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
                    val success = viewModel.checkPinAndPlay(pin)
                    if (!success) {
                        // feedback or toast
                    }
                }
            )
        }

        // Complete fullscreen video player overlay
        if (currentPlayingItem != null) {
            VideoPlayerUI(
                playlistItem = currentPlayingItem!!,
                onClosePlayback = { viewModel.closePlayback() }
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
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "IPTV Especial • 2026 • 4K Ultra HD • Estéreo",
                fontSize = 11.sp,
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
 * INDIVIDUAL GRID ROW ITEM DISPLAY CARD
 */
@Composable
fun GridItemCard(item: PlaylistItem, isGridCompact: Boolean, onClick: () -> Unit) {
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
                    }
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090C))
                        .padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = item.name,
                            fontSize = 11.sp,
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
                            fontSize = 9.sp,
                            color = com.example.ui.theme.GoldPremium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    if (item.isAdult) {
                        Box(modifier = Modifier
                            .padding(end = 12.dp)
                            .background(Color.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("18+", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
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
fun AdultPinDialog(onDismiss: () -> Unit, onPinVerify: (String) -> Unit) {
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
                            if (pinValue == "0000" || pinValue.isNotEmpty()) { // standard pass block verify
                                onPinVerify(pinValue)
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
fun VideoPlayerUI(playlistItem: PlaylistItem, onClosePlayback: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    
    // Gestures states
    var brightnessValue by remember { mutableFloatStateOf(1f) }
    var volumeValue by remember { mutableFloatStateOf(1f) }

    // Standard media3 ExoPlayer controller setup with proper scopes
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

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
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Impossível reproduzir canal/mídia. Conexão terminada pelo link."
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                detectTransformGestures { _, pan, zoom, _ ->
                    // Gesture support modifying brightness/volume on drags optionally
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
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            onRelease = { view ->
                view.player = null
            }
        )

        // Bottom cinematic bar overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // Custom Overlay Panels and Info text
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
                        fontSize = 16.sp,
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
                                    exoPlayer.prepare()
                                    exoPlayer.play()
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

            // Bottom Player bar controllers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
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
    val adultPin = viewModel.preferencesService.adultPin
    
    var tempPin by remember { mutableStateOf(adultPin) }
    var snackbarVisible by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.height(24.dp))

            // Config card controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Preferências Gerais",
                        color = GoldPremium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Servidor / Lista Ativa:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = activePlaylist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Credenciais MK21 Atuais:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Usuário: $username [${password.length} chars pass]",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Segurança & Controle PIN Adulto:",
                        color = GoldPremium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tempPin,
                        onValueChange = { if (it.length <= 4) tempPin = it },
                        label = { Text("Alterar PIN Adulto") },
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NetflixRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = NetflixRed
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.setAdultPin(tempPin)
                            snackbarVisible = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SALVAR PREFERÊNCIAS", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Back option info
            Text(
                text = "MK21 MultiServidor v1.21.PRO - Android Engine",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = Color.Green, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Configurações salvas com sucesso!", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = { snackbarVisible = false }) {
                        Text("FECHAR", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
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
