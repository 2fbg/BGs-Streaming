package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.database.IptvDatabase
import com.example.data.model.ContentType
import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import com.example.data.model.ServerProfile
import com.example.data.parser.M3UParser
import com.example.data.service.PreferencesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        IptvDatabase::class.java,
        "mk21_iptv_db"
    ).fallbackToDestructiveMigration().build()

    private val playlistItemDao = db.playlistItemDao()
    private val manualPlaylistDao = db.manualPlaylistDao()
    
    val preferencesService = PreferencesService(application)
    
    val predefinedServers = listOf(
        ServerProfile("server_1", "VLOG", "http://vlogmk.de"),
        ServerProfile("server_2", "LUB TV", "http://redeinternadestiny.top"),
        ServerProfile("server_3", "CINELON21", "http://cinelontv.work"),
        ServerProfile("server_4", "TANNIX", "http://tannix.fun"),
        ServerProfile("server_5", "CB6000", "http://cb6.fun"),
        ServerProfile("server_6", "MK21 TV", "http://mk21.uk"),
        ServerProfile("server_7", "NOVA+", "http://novamk21.win")
    )

    private val predefinedNames = predefinedServers.map { it.name }.toSet()

    private val _username = MutableStateFlow(preferencesService.username)
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow(preferencesService.password)
    val password = _password.asStateFlow()

    private val _activePlaylistName = MutableStateFlow(preferencesService.activePlaylistName)
    val activePlaylistName = _activePlaylistName.asStateFlow()

    // Loading & Progress States
    private val _loadingProgress = MutableStateFlow<Int?>(null) // null means not loading
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _loginSuccess = MutableSharedFlow<Unit>(replay = 0)
    val loginSuccess = _loginSuccess.asSharedFlow()

    // Manual list records
    val manualPlaylists = manualPlaylistDao.getAllManualPlaylists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current playing channel/movie
    private val _currentPlayingItem = MutableStateFlow<PlaylistItem?>(null)
    val currentPlayingItem = _currentPlayingItem.asStateFlow()

    // Content types filtering
    private val _selectedContentType = MutableStateFlow(ContentType.LIVE)
    val selectedContentType = _selectedContentType.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // PIN Protection dialog triggers
    private val _adultPinGranted = MutableStateFlow(false)
    val adultPinGranted = _adultPinGranted.asStateFlow()

    private val _activePinPromptItem = MutableStateFlow<PlaylistItem?>(null)
    val activePinPromptItem = _activePinPromptItem.asStateFlow()

    // Live state bindings based on currently active list selection
    val activeItemsList = combine(
        _activePlaylistName,
        _selectedContentType,
        _selectedCategory,
        _searchQuery
    ) { playlistName, contentType, category, query ->
        Triple(playlistName, contentType, Pair(category, query))
    }.flatMapLatest { (playlist, type, catQuery) ->
        val (category, query) = catQuery
        if (query.isNotEmpty()) {
            playlistItemDao.searchItems(playlist, "%$query%")
        } else if (category == "Todas" || category == "Todos") {
            playlistItemDao.getItemsByType(playlist, type.name)
        } else {
            playlistItemDao.getItemsByCategoryAndType(playlist, category, type.name)
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dynamic categorizations loaded dynamically from the playlist items
    val activeCategories = combine(
        _activePlaylistName,
        _selectedContentType
    ) { playlistName, contentType ->
        Pair(playlistName, contentType)
    }.flatMapLatest { (playlist, type) ->
        playlistItemDao.getCategoriesByType(playlist, type.name).map { list ->
            listOf("Todas") + list
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Todas")
    )

    // Highlights selection (Randomly gets 5 items of ACTIVE playlist)
    val highlightsList = _activePlaylistName.flatMapLatest { playlist ->
        playlistItemDao.getRandomHighlights(playlist).map { items ->
            items.shuffled().take(6)
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        // Autoload current configurations or do silent caching check
        viewModelScope.launch {
            try {
                if (isCredentialsConfigured()) {
                    val lastUpdate = preferencesService.getLastPlaylistUpdateTimestamp(preferencesService.activePlaylistName)
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdate
                    
                    // If cache details do not exist, or are older than 1 day, fetch from internet
                    if (lastUpdate == 0L) {
                        refreshActivePlaylist()
                    } else if (timeSinceLastUpdate > oneDayMs) {
                        // Update in silent background threads to satisfy "atualizar de forma assíncrona depois"
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                downloadAndParsePlaylistSilently()
                            } catch (e: Throwable) {
                                Log.e("MK21_VM", "Error updating background playlist cache", e)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("MK21_VM", "Error during init loading configuration check", e)
            }
        }
    }

    fun isCredentialsConfigured(): Boolean {
        return preferencesService.isCredentialsConfigured(predefinedNames)
    }

    fun setCredentials(usernameInput: String, passwordInput: String) {
        _username.value = usernameInput
        _password.value = passwordInput
        preferencesService.username = usernameInput
        preferencesService.password = passwordInput
    }

    fun setAdultPin(newPin: String) {
        preferencesService.adultPin = newPin
    }

    fun selectPlaylist(playlistName: String) {
        _activePlaylistName.value = playlistName
        preferencesService.activePlaylistName = playlistName
        
        // Match base servers ID if predefined
        val matchedServer = predefinedServers.find { it.name == playlistName }
        if (matchedServer != null) {
            preferencesService.activeServerId = matchedServer.id
        }
        
        // Load items. If they do not exist in database cache, download them automatically.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isCredentialsConfigured()) {
                    val count = playlistItemDao.getItemsByType(playlistName, ContentType.LIVE.name).first().size
                    if (count == 0) {
                        refreshActivePlaylist()
                    }
                }
            } catch (e: Throwable) {
                Log.e("MK21_VM", "Error in selectPlaylist loading database check", e)
            }
        }
    }

    fun changeContentType(type: ContentType) {
        _selectedContentType.value = type
        _selectedCategory.value = "Todas"
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playContent(item: PlaylistItem) {
        if (item.isAdult && !_adultPinGranted.value) {
            _activePinPromptItem.value = item
        } else {
            _currentPlayingItem.value = item
            viewModelScope.launch(Dispatchers.IO) {
                playlistItemDao.updateLastWatched(item.id, System.currentTimeMillis())
            }
        }
    }

    fun closePlayback() {
        _currentPlayingItem.value = null
    }

    fun dismissPinPrompt() {
        _activePinPromptItem.value = null
    }

    fun checkPinAndPlay(pinAttempt: String): Boolean {
        if (pinAttempt == preferencesService.adultPin) {
            _adultPinGranted.value = true
            val item = _activePinPromptItem.value
            if (item != null) {
                _currentPlayingItem.value = item
                _activePinPromptItem.value = null
                viewModelScope.launch(Dispatchers.IO) {
                    playlistItemDao.updateLastWatched(item.id, System.currentTimeMillis())
                }
            }
            return true
        }
        return false
    }

    fun toggleFavorite(item: PlaylistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.setFavorite(item.id, !item.isFavorite)
        }
    }

    // Manual lists operations
    fun addManualPlaylist(name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            manualPlaylistDao.insertManualPlaylist(ManualPlaylist(name, url))
        }
    }

    fun updateManualPlaylist(oldName: String, newName: String, newUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (oldName != newName) {
                manualPlaylistDao.deleteManualPlaylist(oldName)
                playlistItemDao.clearPlaylistItems(oldName)
                manualPlaylistDao.insertManualPlaylist(ManualPlaylist(newName, newUrl))
                if (_activePlaylistName.value == oldName) {
                    selectPlaylist(newName)
                }
            } else {
                manualPlaylistDao.insertManualPlaylist(ManualPlaylist(newName, newUrl))
                playlistItemDao.clearPlaylistItems(newName)
                if (_activePlaylistName.value == newName) {
                    refreshActivePlaylist()
                }
            }
        }
    }

    fun deleteManualPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            manualPlaylistDao.deleteManualPlaylist(name)
            playlistItemDao.clearPlaylistItems(name)
            if (_activePlaylistName.value == name) {
                // Return to default Server 1
                selectPlaylist(predefinedServers[0].name)
            }
        }
    }

    fun refreshActivePlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = 0
            _errorMessage.value = null
            try {
                val targetPlaylist = _activePlaylistName.value
                val downloadUrl = getActivePlaylistDownloadUrl()
                
                if (downloadUrl.isEmpty()) {
                    _errorMessage.value = "Por favor, configure o usuário/senha ou insira uma lista manual."
                    _loadingProgress.value = null
                    return@launch
                }

                _loadingProgress.value = 10 // connected
                val request = Request.Builder().url(downloadUrl).build()
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("Falha de conexão com o servidor. Código: ${response.code}")
                }
                
                _loadingProgress.value = 35 // downloading
                val bytesStream = response.body?.byteStream()
                if (bytesStream == null) {
                    throw Exception("Lista M3U vazia ou incorreta.")
                }
                
                _loadingProgress.value = 50 // parsing
                val parsedItems = M3UParser.parse(bytesStream, targetPlaylist) { progress ->
                    // Offset parsing sequence to range of 50 to 95
                    val mappedProgress = 50 + (progress * 45 / 100)
                    _loadingProgress.value = mappedProgress
                }

                if (parsedItems.isEmpty()) {
                    throw Exception("Tópicos e canais não encontrados no arquivo M3U.")
                }

                _loadingProgress.value = 95 // saving cache
                playlistItemDao.clearAndInsertPlaylistItems(targetPlaylist, parsedItems)

                preferencesService.setLastPlaylistUpdateTimestamp(targetPlaylist, System.currentTimeMillis())
                _loadingProgress.value = 100
                _loginSuccess.emit(Unit)
                kotlinx.coroutines.delay(1200) // slight delay to show progress complete
                _loadingProgress.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao processar a lista: ${e.message}"
                _loadingProgress.value = null
                Log.e("MK21_VM", "Error refreshing IPTV list", e)
            }
        }
    }

    private fun downloadAndParsePlaylistSilently(targetPlaylist: String = _activePlaylistName.value) {
        try {
            val downloadUrl = getActivePlaylistDownloadUrl(targetPlaylist)
            if (downloadUrl.isEmpty()) return

            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.let { stream ->
                    val parsedItems = M3UParser.parse(stream, targetPlaylist) { _ -> }
                    if (parsedItems.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            playlistItemDao.clearAndInsertPlaylistItems(targetPlaylist, parsedItems)
                            preferencesService.setLastPlaylistUpdateTimestamp(targetPlaylist, System.currentTimeMillis())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MK21_VM", "Silent download/parse failed safely context: ${e.message}", e)
        }
    }

    private fun getActivePlaylistDownloadUrl(targetPlaylist: String = _activePlaylistName.value): String {
        val currentPlaylist = targetPlaylist
        
        // Is it a predefined server?
        val predefinedServer = predefinedServers.find { it.name == currentPlaylist }
        if (predefinedServer != null) {
            val user = _username.value.trim()
            val pass = _password.value.trim()
            if (user.isEmpty() || pass.isEmpty()) {
                return ""
            }
            // Construct HTTP url always, force http:// instead of https://
            var base = predefinedServer.baseUrl
            if (base.startsWith("https://", ignoreCase = true)) {
                base = "http://" + base.substring(8)
            } else if (!base.startsWith("http://", ignoreCase = true)) {
                base = "http://$base"
            }
            return "$base/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts"
        }
        
        // Is it a manual list?
        var manualUrl = ""
        val matched = manualPlaylists.value.find { it.name == currentPlaylist }
        if (matched != null) {
            manualUrl = matched.url
            // Force http:// instead of https:// for manual lists too if pasted as https
            if (manualUrl.startsWith("https://", ignoreCase = true)) {
                manualUrl = "http://" + manualUrl.substring(8)
            }
        }
        return manualUrl
    }
}
