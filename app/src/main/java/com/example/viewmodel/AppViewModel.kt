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
    
    private val staticDefaultServers = listOf(
        ServerProfile("server_1", "VLOG", "http://vlogmk.de"),
        ServerProfile("server_2", "LUB TV", "http://triimundial.shop"),
        ServerProfile("server_3", "CINELON21", "http://infinixparcerias.site"),
        ServerProfile("server_4", "TANNIX", "http://unituf.online"),
        ServerProfile("server_5", "CB6000", "http://cb6.fun"),
        ServerProfile("server_6", "MK21 TV", "http://appsmk.org"),
        ServerProfile("server_7", "NOVA+", "http://novamk21.win")
    )

    private val _predefinedServersState = MutableStateFlow<List<ServerProfile>>(staticDefaultServers)
    
    val predefinedServers: List<ServerProfile>
        get() = _predefinedServersState.value

    private val predefinedNames: Set<String>
        get() = _predefinedServersState.value.map { it.name }.toSet()

    // Real-time Licencing State Flows
    private val _isPremiumActive = MutableStateFlow(preferencesService.isLicenseValid())
    val isPremiumActive = _isPremiumActive.asStateFlow()

    private val _trialDaysLeft = MutableStateFlow(preferencesService.getTrialDaysRemaining())
    val trialDaysLeft = _trialDaysLeft.asStateFlow()

    val virtualMacAddress: String
        get() = preferencesService.virtualMac

    fun activateLicense(key: String): Boolean {
        preferencesService.activationKey = key
        val isValid = preferencesService.isLicenseValid()
        _isPremiumActive.value = isValid
        _trialDaysLeft.value = preferencesService.getTrialDaysRemaining()
        return isValid
    }

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
    enum class SortOrder {
        DEFAULT,                // Ordem por número (original order)
        ALPHABETICAL,           // Ordem por A-Z
        ALPHABETICAL_DESC,      // Ordem por Z-A
        BY_ADDITION,            // Ordem por adição (newest / NOVO first)
        BY_RATING               // Ordem por qualificação (4K -> HD -> others)
    }

    private val _sortOrder = MutableStateFlow(
        when (preferencesService.menuSortOrder) {
            "Ordem por A-Z" -> SortOrder.ALPHABETICAL
            "Ordem por Z-A" -> SortOrder.ALPHABETICAL_DESC
            "Ordem por adição" -> SortOrder.BY_ADDITION
            "Ordem por qualificação" -> SortOrder.BY_RATING
            else -> SortOrder.DEFAULT
        }
    )
    val sortOrder = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun updateMenuSortOrder(stringOrder: String) {
        preferencesService.menuSortOrder = stringOrder
        _sortOrder.value = when (stringOrder) {
            "Ordem por A-Z" -> SortOrder.ALPHABETICAL
            "Ordem por Z-A" -> SortOrder.ALPHABETICAL_DESC
            "Ordem por adição" -> SortOrder.BY_ADDITION
            "Ordem por qualificação" -> SortOrder.BY_RATING
            else -> SortOrder.DEFAULT
        }
    }

    private val _adultPinGranted = MutableStateFlow(false)
    val adultPinGranted = _adultPinGranted.asStateFlow()

    private val _activePinPromptItem = MutableStateFlow<PlaylistItem?>(null)
    val activePinPromptItem = _activePinPromptItem.asStateFlow()

    private val _activePinPromptCategory = MutableStateFlow<String?>(null)
    val activePinPromptCategory = _activePinPromptCategory.asStateFlow()

    // Live state bindings based on currently active list selection
    val activeItemsList = combine(
        combine(_activePlaylistName, _selectedContentType, _selectedCategory) { playlist, type, category ->
            Triple(playlist, type, category)
        },
        combine(_searchQuery, _adultPinGranted, _sortOrder) { query, adultGranted, sort ->
            Triple(query, adultGranted, sort)
        }
    ) { p1, p2 ->
        Pair(p1, p2)
    }.flatMapLatest { (p1, p2) ->
        val (playlist, type, category) = p1
        val (query, adultGranted, sortOrder) = p2
        
        val rawFlow = if (query.isNotEmpty()) {
            playlistItemDao.searchItems(playlist, "%$query%")
        } else if (category == "★ Favoritos") {
            playlistItemDao.getFavorites(playlist).map { list ->
                list.filter { it.contentType == type.name }
            }
        } else if (category == "Todas" || category == "Todos") {
            playlistItemDao.getItemsByType(playlist, type.name)
        } else {
            playlistItemDao.getItemsByCategoryAndType(playlist, category, type.name)
        }
        
        rawFlow.map { list ->
            // 1. Filter out adult items if PIN has not been entered
            val filtered = if (adultGranted) {
                list
            } else {
                list.filter { !it.isAdult && !isAdultCategory(it.category) }
            }
            
            // 2. Sort the final filtered list
            val sorted = when (sortOrder) {
                SortOrder.ALPHABETICAL -> filtered.sortedBy { it.name }
                SortOrder.ALPHABETICAL_DESC -> filtered.sortedByDescending { it.name }
                SortOrder.BY_ADDITION -> {
                    // "Ordem por adição" puts NOVO items (stable remainder of id.hashCode() % 3 is 2) first
                    val isNovo = { item: PlaylistItem ->
                        val r = item.id.hashCode() % 3
                        val positiveR = if (r < 0) r + 3 else r
                        positiveR == 2
                    }
                    filtered.sortedWith(
                        compareBy<PlaylistItem> { !isNovo(it) } // Put true (isNovo) before false
                            .thenByDescending { it.id }
                    )
                }
                SortOrder.BY_RATING -> {
                    // "Ordem por qualificação" sorts 4K (remainder 0) first, HD (remainder 1) second, others/NOVO (remainder 2) third
                    val ratePriority = { item: PlaylistItem ->
                        val r = item.id.hashCode() % 3
                        val positiveR = if (r < 0) r + 3 else r
                        when (positiveR) {
                            0 -> 0 // 4K first
                            1 -> 1 // HD second
                            else -> 2 // others/NOVO third
                        }
                    }
                    filtered.sortedWith(
                        compareBy<PlaylistItem> { ratePriority(it) }
                            .thenBy { it.name }
                    )
                }
                SortOrder.DEFAULT -> filtered.sortedBy { it.id } // "Ordem por número" sorts by raw insertion number (ID ascending)
            }
            sorted.take(2000)
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
            listOf("Todas", "★ Favoritos") + list
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Todas")
    )

    // Continue Watching Section Flow based on last watched items
    val continueWatchingList = combine(
        _activePlaylistName,
        _selectedContentType
    ) { playlistName, contentType ->
        Pair(playlistName, contentType)
    }.flatMapLatest { (playlist, type) ->
        playlistItemDao.getContinueWatching(playlist, type.name)
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
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

    private fun loadCachedServers() {
        val cachedJson = preferencesService.cachedServersJson
        if (cachedJson.isNotEmpty()) {
            val parsed = parseServersJson(cachedJson)
            if (parsed.isNotEmpty()) {
                _predefinedServersState.value = parsed
            }
        }
    }

    private fun parseServersJson(jsonStr: String): List<ServerProfile> {
        val list = mutableListOf<ServerProfile>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val baseUrl = obj.optString("baseUrl", "")
                if (id.isNotEmpty() && name.isNotEmpty() && baseUrl.isNotEmpty()) {
                    list.add(ServerProfile(id, name, baseUrl))
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("MK21_VM", "Error parsing servers JSON: ", e)
        }
        return list
    }

    private fun fetchDynamicServers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/2fbg/BGs-Streaming/main/servers.json")
                    .build()
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val parsed = parseServersJson(bodyString)
                            if (parsed.isNotEmpty()) {
                                preferencesService.cachedServersJson = bodyString
                                _predefinedServersState.value = parsed
                                Log.d("MK21_VM", "Successfully loaded ${parsed.size} dynamic servers from GitHub raw JSON!")
                            }
                        }
                    } else {
                        Log.w("MK21_VM", "Failed to load dynamic servers from GitHub: ${response.code}")
                    }
                }
            } catch (e: Throwable) {
                Log.e("MK21_VM", "Error fetching dynamic servers from GitHub", e)
            }
        }
    }

    init {
        // Load dynamically cached servers immediately, then fetch latest in background
        loadCachedServers()
        fetchDynamicServers()

        // Initialize trial period if first bootup
        if (preferencesService.trialStartDate == 0L) {
            preferencesService.trialStartDate = System.currentTimeMillis()
        }
        
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

    fun isAdultCategory(category: String): Boolean {
        val uppercase = category.uppercase()
        val pattern = listOf("18+", "ADULTO", "ADULT", "XXX", "SEXY", "PLAYBOY", "PENTHOUSE", "VENUS", "HOT ", "HUSTLER", "FORBIDDEN", "FORA DA LEI", "S0X")
        return pattern.any { uppercase.contains(it) }
    }

    fun selectCategory(category: String) {
        if (isAdultCategory(category) && !_adultPinGranted.value) {
            _activePinPromptCategory.value = category
        } else {
            _selectedCategory.value = category
        }
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

    fun clearMoviesAndSeriesHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.clearMoviesAndSeriesHistory(_activePlaylistName.value)
        }
    }

    fun clearLiveHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.clearLiveHistory(_activePlaylistName.value)
        }
    }

    fun closePlayback() {
        _currentPlayingItem.value = null
    }

    fun dismissPinPrompt() {
        _activePinPromptItem.value = null
    }

    fun dismissCategoryPinPrompt() {
        _activePinPromptCategory.value = null
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
            val cat = _activePinPromptCategory.value
            if (cat != null) {
                _selectedCategory.value = cat
                _activePinPromptCategory.value = null
            }
            return true
        }
        return false
    }

    fun playNext() {
        val current = _currentPlayingItem.value ?: return
        val list = activeItemsList.value
        val index = list.indexOfFirst { it.id == current.id }
        if (index != -1 && index < list.size - 1) {
            playContent(list[index + 1])
        }
    }

    fun playPrevious() {
        val current = _currentPlayingItem.value ?: return
        val list = activeItemsList.value
        val index = list.indexOfFirst { it.id == current.id }
        if (index > 0) {
            playContent(list[index - 1])
        }
    }

    fun toggleFavorite(item: PlaylistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.setFavorite(item.id, !item.isFavorite)
        }
    }

    fun loadDemoData() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = 10
            _errorMessage.value = null
            try {
                val demoPlaylist = "DEMO MK21"
                _activePlaylistName.value = demoPlaylist
                preferencesService.activePlaylistName = demoPlaylist
                preferencesService.username = "demo"
                preferencesService.password = "demo"
                _username.value = "demo"
                _password.value = "demo"

                _loadingProgress.value = 40
                
                val items = listOf(
                    // LIVE CHANNELS
                    PlaylistItem(
                        name = "Globo RJ HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Record TV HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "SBT HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Band HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "ESPN 1 Brasil HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                        logoUrl = null,
                        category = "Esportes",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "SporTV HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                        logoUrl = null,
                        category = "Esportes",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "HBO Premium FHD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
                        logoUrl = null,
                        category = "Filmes & Séries",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Telecine Action HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                        logoUrl = null,
                        category = "Filmes & Séries",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Playboy TV (Adulto 18+)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                        logoUrl = null,
                        category = "Canais Adultos 18+",
                        contentType = "LIVE",
                        isAdult = true,
                        playlistSource = demoPlaylist
                    ),
                    
                    // MOVIES
                    PlaylistItem(
                        name = "Batman - O Cavaleiro das Trevas",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        logoUrl = null,
                        category = "Ação / Blockbusters",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Duna Parte 2 (2024)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        logoUrl = null,
                        category = "Ficção Científica",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Oppenheimer (2023)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        logoUrl = null,
                        category = "Drama / Biográfico",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Matrix Resurrections",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                        logoUrl = null,
                        category = "Ação / Blockbusters",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Coringa (2019)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                        logoUrl = null,
                        category = "Drama / Biográfico",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Segredos do Passado (Adulto 18+)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                        logoUrl = null,
                        category = "Conteúdo Adulto 18+",
                        contentType = "MOVIE",
                        isAdult = true,
                        playlistSource = demoPlaylist
                    ),

                    // SERIES
                    PlaylistItem(
                        name = "House of the Dragon - Temporada 2 Ep 01",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        logoUrl = null,
                        category = "Fantasias / Drama",
                        contentType = "SERIES",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Breaking Bad - S01E01 Pilot",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                        logoUrl = null,
                        category = "Drama / Policial",
                        contentType = "SERIES",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Stranger Things - Temporada 4 Ep 01",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                        logoUrl = null,
                        category = "Mistério / Suspense",
                        contentType = "SERIES",
                        playlistSource = demoPlaylist
                    )
                )

                _loadingProgress.value = 75
                playlistItemDao.clearAndInsertPlaylistItems(demoPlaylist, items)
                
                preferencesService.setLastPlaylistUpdateTimestamp(demoPlaylist, System.currentTimeMillis())
                _loadingProgress.value = 100
                _loginSuccess.emit(Unit)
                kotlinx.coroutines.delay(800)
                _loadingProgress.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Erro no demo: ${e.message}"
                _loadingProgress.value = null
            }
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
