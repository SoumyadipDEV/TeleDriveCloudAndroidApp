package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.TelegramService
import com.example.api.UploadResult
import com.example.data.FileRepository
import com.example.data.VFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

data class UploadTask(
    val id: String,
    val name: String,
    val size: Long,
    val progress: Int = 0,
    val speed: String = "0 KB/s",
    val eta: String = "Calculating...",
    val status: String = "Uploading" // "Uploading", "Success", "Failed"
)

data class AuthState(
    val mode: String = "BotMode", // "BotMode" or "UserClient"
    val botToken: String = "",
    val chatId: String = "",
    val phoneNumber: String = "",
    val apiId: String = "",
    val apiHash: String = "",
    val isOtpSent: Boolean = false,
    val otpCode: String = "",
    val isLoggedIn: Boolean = false
)

data class VirtualDrive(
    val id: Long,
    val name: String,
    val description: String,
    val iconType: String // "all", "video", "document", "music"
)

class MainViewModel(private val repository: FileRepository) : ViewModel() {

    private val telegramService = TelegramService()

    // Screen navigation state: "Home", "MyDrive", "Shared", "Recent", "Starred", "Trash"
    private val _currentScreen = MutableStateFlow("Home")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Active Virtual Drive Channel ID
    private val _activeChannelId = MutableStateFlow(0L) // Default 0L is "Saved Messages"
    val activeChannelId: StateFlow<Long> = _activeChannelId.asStateFlow()

    // Virtual Drives / Channels list
    private val _virtualDrives = MutableStateFlow<List<VirtualDrive>>(
        listOf(
            VirtualDrive(0L, "Saved Messages", "Default Private Storage", "all"),
            VirtualDrive(101L, "Movies & Videos", "Media Virtual Drive", "video"),
            VirtualDrive(102L, "Work & Study", "Document Virtual Drive", "document"),
            VirtualDrive(103L, "Music Locker", "Audio Virtual Drive", "music")
        )
    )
    val virtualDrives: StateFlow<List<VirtualDrive>> = _virtualDrives.asStateFlow()

    // Local Syncing indicator
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Dual Theme Support state
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Sort order: "Name", "Date Modified", "File Size", "File Type"
    private val _sortBy = MutableStateFlow("Name")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    // Multi-Account Switcher states
    private val _accounts = MutableStateFlow<List<String>>(listOf("+1 (555) 019-2834"))
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _activeAccountIndex = MutableStateFlow(0)
    val activeAccountIndex: StateFlow<Int> = _activeAccountIndex.asStateFlow()

    // Folder navigation state (0L is root folder)
    private val _currentFolderId = MutableStateFlow(0L)
    val currentFolderId: StateFlow<Long> = _currentFolderId.asStateFlow()

    // Path folder history for back navigation
    private val folderBackstack = mutableListOf<Long>()

    // View layout state: Grid vs List
    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active upload tasks for sticky panel
    private val _uploadTasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val uploadTasks: StateFlow<List<UploadTask>> = _uploadTasks.asStateFlow()

    // Authentication State
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Active folder path (breadcrumbs)
    private val _breadcrumbs = MutableStateFlow<List<VFile>>(emptyList())
    val breadcrumbs: StateFlow<List<VFile>> = _breadcrumbs.asStateFlow()

    // Selected file filter (e.g., "all", "image", "video", "application/pdf")
    private val _fileFilter = MutableStateFlow("all")
    val fileFilter: StateFlow<String> = _fileFilter.asStateFlow()

    init {
        // Init flow
    }

    // Reactive flow mapping to load files based on active directory and active channel
    val files: StateFlow<List<VFile>> = kotlinx.coroutines.flow.combine(
        _currentFolderId, _activeChannelId
    ) { folderId, channelId ->
        Pair(folderId, channelId)
    }.flatMapLatest { (folderId, channelId) ->
        repository.getFilesByParent(folderId, channelId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoriteFiles: StateFlow<List<VFile>> = _activeChannelId
        .flatMapLatest { channelId ->
            repository.getFavoriteFiles(channelId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentFiles: StateFlow<List<VFile>> = _activeChannelId
        .flatMapLatest { channelId ->
            repository.getRecentFiles(channelId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val trashFiles: StateFlow<List<VFile>> = _activeChannelId
        .flatMapLatest { channelId ->
            repository.getTrashFiles(channelId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalStorageUsed: StateFlow<Long> = _activeChannelId
        .flatMapLatest { channelId ->
            repository.getTotalStorageUsed(channelId)
        }
        .flatMapLatest { used ->
            MutableStateFlow(used ?: 0L)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    // Load persisted auth state, theme, drives, accounts
    fun loadSession(context: Context) {
        val prefs = context.getSharedPreferences("teledrive_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("mode", "BotMode") ?: "BotMode"
        val botToken = prefs.getString("bot_token", "") ?: ""
        val chatId = prefs.getString("chat_id", "") ?: ""
        val phoneNumber = prefs.getString("phone_number", "") ?: ""
        val apiId = prefs.getString("api_id", "") ?: ""
        val apiHash = prefs.getString("api_hash", "") ?: ""
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        _authState.value = AuthState(
            mode = mode,
            botToken = botToken,
            chatId = chatId,
            phoneNumber = phoneNumber,
            apiId = apiId,
            apiHash = apiHash,
            isLoggedIn = isLoggedIn
        )

        _isDarkMode.value = prefs.getBoolean("is_dark_mode", false)
        _activeChannelId.value = prefs.getLong("active_channel_id", 0L)

        // Load Accounts
        val accs = prefs.getString("telegram_accounts", "") ?: ""
        if (accs.isNotEmpty()) {
            _accounts.value = accs.split(",")
        } else {
            _accounts.value = listOf("+1 (555) 019-2834")
        }
        _activeAccountIndex.value = prefs.getInt("active_account_index", 0).coerceIn(0, _accounts.value.size - 1)

        // Load Custom Channels / Virtual Drives
        val customDrivesString = prefs.getString("custom_drives", "") ?: ""
        val drives = mutableListOf(
            VirtualDrive(0L, "Saved Messages", "Default Private Storage", "all"),
            VirtualDrive(101L, "Movies & Videos", "Media Virtual Drive", "video"),
            VirtualDrive(102L, "Work & Study", "Document Virtual Drive", "document"),
            VirtualDrive(103L, "Music Locker", "Audio Virtual Drive", "music")
        )
        if (customDrivesString.isNotEmpty()) {
            customDrivesString.split(";").forEach { part ->
                val tokens = part.split(",")
                if (tokens.size >= 4) {
                    val id = tokens[0].toLongOrNull() ?: return@forEach
                    val name = tokens[1]
                    val desc = tokens[2]
                    val iconType = tokens[3]
                    if (drives.none { it.id == id }) {
                        drives.add(VirtualDrive(id, name, desc, iconType))
                    }
                }
            }
        }
        _virtualDrives.value = drives
    }

    // Save auth session
    private fun saveSession(context: Context, state: AuthState) {
        val prefs = context.getSharedPreferences("teledrive_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("mode", state.mode)
            putString("bot_token", state.botToken)
            putString("chat_id", state.chatId)
            putString("phone_number", state.phoneNumber)
            putString("api_id", state.apiId)
            putString("api_hash", state.apiHash)
            putBoolean("is_logged_in", state.isLoggedIn)
            apply()
        }
    }

    fun logout(context: Context) {
        _authState.value = AuthState()
        saveSession(context, _authState.value)
    }

    fun setAuthMode(mode: String) {
        _authState.value = _authState.value.copy(mode = mode)
    }

    fun updateBotCredentials(context: Context, token: String, chatId: String, chatTitle: String = "") {
        val updated = _authState.value.copy(
            botToken = token,
            chatId = chatId,
            isLoggedIn = token.isNotBlank() && chatId.isNotBlank()
        )
        _authState.value = updated
        saveSession(context, updated)

        // Update the Saved Messages drive description and name if actual credentials are used
        val displayTitle = if (chatTitle.isNotBlank()) chatTitle else "Telegram Drive"
        val displayDesc = "Active Connected Storage"
        
        _virtualDrives.value = _virtualDrives.value.map {
            if (it.id == 0L) {
                it.copy(name = displayTitle, description = displayDesc)
            } else it
        }

        // Seed some starter files/folders in DB if logged in for the first time
        viewModelScope.launch {
            if (updated.isLoggedIn) {
                seedStarterItems()
            }
        }
    }

    // Validates bot credentials and then logs in
    fun loginWithBot(
        context: Context,
        token: String,
        chatId: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val (isValid, chatTitle) = telegramService.validateCredentials(token, chatId)
            if (isValid) {
                updateBotCredentials(context, token, chatId, chatTitle)
                onResult(true, "Successfully connected to $chatTitle!")
            } else {
                onResult(false, chatTitle)
            }
        }
    }

    fun sendUserOtp(context: Context, phone: String, apiId: String, apiHash: String) {
        viewModelScope.launch {
            delay(1500)
            _authState.value = _authState.value.copy(
                phoneNumber = phone,
                apiId = apiId,
                apiHash = apiHash,
                isOtpSent = true
            )
        }
    }

    fun verifyUserOtp(context: Context, code: String) {
        viewModelScope.launch {
            delay(1200)
            if (code.length == 5) {
                val updated = _authState.value.copy(
                    otpCode = code,
                    isLoggedIn = true
                )
                _authState.value = updated
                saveSession(context, updated)
                seedStarterItems()
            }
        }
    }

    private suspend fun seedStarterItems() {
        val existing = repository.getFilesByParent(0L, 0L).first()
        if (existing.isEmpty()) {
            withContext(Dispatchers.IO) {
                val welcomeFolderId = repository.insertFile(
                    VFile(
                        name = "Welcome to TeleDrive",
                        isFolder = true,
                        parentId = 0L,
                        channelId = 0L
                    )
                )
                repository.insertFile(
                    VFile(
                        name = "Quick Guide.pdf",
                        isFolder = false,
                        parentId = welcomeFolderId,
                        size = 1450000,
                        mimeType = "application/pdf",
                        channelId = 0L
                    )
                )
                repository.insertFile(
                    VFile(
                        name = "Demo Photo.jpg",
                        isFolder = false,
                        parentId = 0L,
                        size = 450000,
                        mimeType = "image/jpeg",
                        channelId = 0L
                    )
                )
                repository.insertFile(
                    VFile(
                        name = "Unlimited Movies & Media",
                        isFolder = true,
                        parentId = 0L,
                        channelId = 0L
                    )
                )
            }
        }
    }

    // VFS Operations
    fun navigateToFolder(folderId: Long) {
        viewModelScope.launch {
            if (folderId == 0L) {
                folderBackstack.clear()
                _breadcrumbs.value = emptyList()
            } else {
                val folder = repository.getFileById(folderId)
                if (folder != null && folder.isFolder) {
                    if (_currentFolderId.value != folderId) {
                        folderBackstack.add(_currentFolderId.value)
                    }
                    // Rebuild breadcrumbs
                    val path = mutableListOf<VFile>()
                    var current: VFile? = folder
                    while (current != null) {
                        path.add(0, current)
                        current = if (current.parentId != 0L) repository.getFileById(current.parentId) else null
                    }
                    _breadcrumbs.value = path
                }
            }
            _currentFolderId.value = folderId
        }
    }

    fun navigateBack(): Boolean {
        if (folderBackstack.isNotEmpty()) {
            val previousId = folderBackstack.removeAt(folderBackstack.size - 1)
            _currentFolderId.value = previousId
            viewModelScope.launch {
                if (previousId == 0L) {
                    _breadcrumbs.value = emptyList()
                } else {
                    val folder = repository.getFileById(previousId)
                    if (folder != null) {
                        val path = mutableListOf<VFile>()
                        var current: VFile? = folder
                        while (current != null) {
                            path.add(0, current)
                            current = if (current.parentId != 0L) repository.getFileById(current.parentId) else null
                        }
                        _breadcrumbs.value = path
                    }
                }
            }
            return true
        }
        return false
    }

    fun setScreen(screen: String) {
        _currentScreen.value = screen
        _currentFolderId.value = 0L
        _breadcrumbs.value = emptyList()
        folderBackstack.clear()
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFileFilter(filter: String) {
        _fileFilter.value = filter
    }

    // Dynamic virtual drives (channels) switcher
    fun switchVirtualDrive(context: Context, channelId: Long) {
        _activeChannelId.value = channelId
        _currentFolderId.value = 0L
        _breadcrumbs.value = emptyList()
        folderBackstack.clear()
        
        val prefs = context.getSharedPreferences("teledrive_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("active_channel_id", channelId).apply()
        
        Toast.makeText(context, "Switched to Virtual Drive", Toast.LENGTH_SHORT).show()
    }

    fun addVirtualDrive(context: Context, name: String, description: String, iconType: String) {
        val id = System.currentTimeMillis()
        val newDrive = VirtualDrive(id, name, description, iconType)
        _virtualDrives.value = _virtualDrives.value + newDrive

        val prefs = context.getSharedPreferences("teledrive_prefs", Context.MODE_PRIVATE)
        val customDrivesString = _virtualDrives.value.filter { it.id > 103L }
            .joinToString(";") { "${it.id},${it.name},${it.description},${it.iconType}" }
        prefs.edit().putString("custom_drives", customDrivesString).apply()

        // Automatically switch to it
        switchVirtualDrive(context, id)
    }

    // Local Sync Engine
    fun syncActiveChannel(context: Context) {
        viewModelScope.launch {
            _isSyncing.value = true
            delay(1500) // Simulating network delays, parsing TG channels messages

            val channelId = _activeChannelId.value
            val currentDrive = _virtualDrives.value.find { it.id == channelId }
            val type = currentDrive?.iconType ?: "all"

            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                when (type) {
                    "video" -> {
                        repository.insertFile(VFile(
                            parentId = _currentFolderId.value,
                            name = "Cinematic Drone Clip_${timestamp % 1000}.mp4",
                            isFolder = false,
                            size = 48 * 1024 * 1024,
                            mimeType = "video/mp4",
                            telegramFileId = "synced_video_${timestamp}",
                            channelId = channelId
                        ))
                        repository.insertFile(VFile(
                            parentId = _currentFolderId.value,
                            name = "Tutorial Walkthrough_${timestamp % 1000}.mkv",
                            isFolder = false,
                            size = 115 * 1024 * 1024,
                            mimeType = "video/x-matroska",
                            telegramFileId = "synced_video_2_${timestamp}",
                            channelId = channelId
                        ))
                    }
                    "document" -> {
                        repository.insertFile(VFile(
                            parentId = _currentFolderId.value,
                            name = "Project Roadmap_${timestamp % 1000}.xlsx",
                            isFolder = false,
                            size = 280 * 1024,
                            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            telegramFileId = "synced_doc_${timestamp}",
                            channelId = channelId
                        ))
                        repository.insertFile(VFile(
                            parentId = _currentFolderId.value,
                            name = "API Technical Specifications_${timestamp % 1000}.pdf",
                            isFolder = false,
                            size = 5 * 1024 * 1024,
                            mimeType = "application/pdf",
                            telegramFileId = "synced_doc_2_${timestamp}",
                            channelId = channelId
                        ))
                    }
                    "music" -> {
                        repository.insertFile(VFile(
                            parentId = _currentFolderId.value,
                            name = "Synthwave Neon Chill_${timestamp % 1000}.mp3",
                            isFolder = false,
                            size = 7 * 1024 * 1024,
                            mimeType = "audio/mpeg",
                            telegramFileId = "synced_audio_${timestamp}",
                            channelId = channelId
                        ))
                    }
                    else -> {
                        repository.insertFile(VFile(
                            parentId = _currentFolderId.value,
                            name = "Telegram Transferred Note_${timestamp % 1000}.txt",
                            isFolder = false,
                            size = 15 * 1024,
                            mimeType = "text/plain",
                            telegramFileId = "synced_txt_${timestamp}",
                            channelId = channelId
                        ))
                        repository.insertFile(VFile(
                            parentId = _currentFolderId.value,
                            name = "Shared Landscape_${timestamp % 1000}.png",
                            isFolder = false,
                            size = 1400 * 1024,
                            mimeType = "image/png",
                            telegramFileId = "synced_img_${timestamp}",
                            channelId = channelId
                        ))
                    }
                }
            }
            _isSyncing.value = false
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Channel metadata synchronized successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Dual Theme support
    fun toggleTheme(context: Context) {
        val nextMode = !_isDarkMode.value
        _isDarkMode.value = nextMode
        val prefs = context.getSharedPreferences("teledrive_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_dark_mode", nextMode).apply()
    }

    // Multi-Account Switcher
    fun addAccount(context: Context, phoneNumber: String) {
        if (phoneNumber.isNotBlank() && !_accounts.value.contains(phoneNumber)) {
            val newList = _accounts.value + phoneNumber
            _accounts.value = newList
            val prefs = context.getSharedPreferences("teledrive_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("telegram_accounts", newList.joinToString(",")).apply()
            switchAccount(context, newList.size - 1)
        }
    }

    fun switchAccount(context: Context, index: Int) {
        if (index in _accounts.value.indices) {
            _activeAccountIndex.value = index
            val prefs = context.getSharedPreferences("teledrive_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("active_account_index", index).apply()

            val activeNumber = _accounts.value[index]
            _authState.value = _authState.value.copy(
                phoneNumber = activeNumber,
                isLoggedIn = true,
                mode = "UserClient"
            )
            saveSession(context, _authState.value)
            Toast.makeText(context, "Switched to account: $activeNumber", Toast.LENGTH_SHORT).show()
        }
    }

    // Sorting state update
    fun updateSortBy(sort: String) {
        _sortBy.value = sort
    }

    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = VFile(
                name = name,
                isFolder = true,
                parentId = _currentFolderId.value,
                channelId = _activeChannelId.value
            )
            repository.insertFile(folder)
        }
    }

    fun renameFile(fileId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileById(fileId)
            if (file != null) {
                val updated = file.copy(
                    name = newName,
                    modifiedAt = System.currentTimeMillis()
                )
                repository.insertFile(updated)
            }
        }
    }

    fun toggleFavorite(fileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileById(fileId)
            if (file != null) {
                val updated = file.copy(
                    isFavorite = !file.isFavorite,
                    modifiedAt = System.currentTimeMillis()
                )
                repository.insertFile(updated)
            }
        }
    }

    fun toggleShared(fileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileById(fileId)
            if (file != null) {
                val updated = file.copy(
                    isShared = !file.isShared,
                    modifiedAt = System.currentTimeMillis()
                )
                repository.insertFile(updated)
            }
        }
    }

    // Trash bin logic
    fun moveToTrash(fileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileById(fileId)
            if (file != null) {
                val updated = file.copy(
                    isInTrash = true,
                    deletedAt = System.currentTimeMillis()
                )
                repository.insertFile(updated)
            }
        }
    }

    fun restoreFromTrash(fileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileById(fileId)
            if (file != null) {
                val updated = file.copy(
                    isInTrash = false,
                    deletedAt = null
                )
                repository.insertFile(updated)
            }
        }
    }

    fun deleteFilePermanently(fileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFileById(fileId)
        }
    }

    fun emptyTrash(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.emptyTrashByChannel(_activeChannelId.value)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Trash emptied permanently!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handles the actual uploading process from Android's file Uri
    fun uploadFileFromUri(context: Context, uri: Uri) {
        val taskId = System.currentTimeMillis().toString()
        val resolver = context.contentResolver

        // Retrieve file metadata
        var fileName = "unnamed_file"
        var fileSize = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val mimeType = resolver.getType(uri)

        // Add task to upload queue
        val initialTask = UploadTask(
            id = taskId,
            name = fileName,
            size = fileSize,
            status = "Uploading"
        )
        _uploadTasks.value = _uploadTasks.value + initialTask

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Copy stream to temporary file in Cache to upload securely
                val cacheFile = File(context.cacheDir, fileName)
                resolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val currentAuth = _authState.value
                val token = currentAuth.botToken.ifBlank { "MOCK_TOKEN" }
                val chatId = currentAuth.chatId.ifBlank { "MOCK_CHAT_ID" }

                var startTime = System.currentTimeMillis()

                val result = telegramService.uploadFile(
                    context = context,
                    file = cacheFile,
                    mimeType = mimeType,
                    token = token,
                    chatId = chatId
                ) { uploaded, total, speed, percent ->
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    val speedBps = if (elapsedSeconds > 0) (uploaded / elapsedSeconds).toLong() else 0L
                    val formattedSpeed = formatSpeed(speedBps)

                    val remainingBytes = total - uploaded
                    val etaSeconds = if (speedBps > 0) remainingBytes / speedBps else 0L
                    val etaString = if (etaSeconds > 0) "${etaSeconds}s remaining" else "Finishing..."

                    updateTaskProgress(taskId, percent, formattedSpeed, etaString)
                }

                when (result) {
                    is UploadResult.Success -> {
                        // Create metadata record in virtual file system
                        val vFile = VFile(
                            parentId = _currentFolderId.value,
                            name = fileName,
                            isFolder = false,
                            size = result.size,
                            mimeType = mimeType,
                            telegramFileId = result.fileId,
                            isRecent = true,
                            channelId = _activeChannelId.value
                        )
                        repository.insertFile(vFile)

                        // Update queue
                        setTaskStatus(taskId, "Success")
                        // Clean up cache
                        cacheFile.delete()
                    }
                    is UploadResult.Error -> {
                        setTaskStatus(taskId, "Failed")
                        cacheFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error copying file", e)
                setTaskStatus(taskId, "Failed")
            }
        }
    }

    private fun updateTaskProgress(taskId: String, progress: Int, speed: String, eta: String) {
        _uploadTasks.value = _uploadTasks.value.map {
            if (it.id == taskId) {
                it.copy(progress = progress, speed = speed, eta = eta)
            } else it
        }
    }

    private fun setTaskStatus(taskId: String, status: String) {
        _uploadTasks.value = _uploadTasks.value.map {
            if (it.id == taskId) {
                it.copy(status = status, progress = if (status == "Success") 100 else it.progress)
            } else it
        }
    }

    fun clearFinishedTasks() {
        _uploadTasks.value = _uploadTasks.value.filter { it.status == "Uploading" }
    }

    // Resolves file streaming URL
    suspend fun getStreamingUrl(fileId: String): String? {
        val token = _authState.value.botToken
        if (token.isBlank() || fileId.isBlank()) {
            return null
        }
        return telegramService.getStreamUrl(token, fileId)
    }

    // Downloads the file from Telegram and saves it to local Android Downloads
    fun downloadFile(context: Context, file: VFile, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = _authState.value.botToken
                val fileId = file.telegramFileId
                if (token.isBlank() || fileId.isNullOrBlank()) {
                    onComplete(false, "Login with Telegram required to download.")
                    return@launch
                }

                // Check environment
                val destinationDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val destinationFile = File(destinationDir, file.name)

                val success = telegramService.downloadFile(
                    token = token,
                    fileId = file.telegramFileId ?: "",
                    destination = destinationFile,
                    onProgress = { _, _ -> }
                )

                if (success) {
                    // Try to also copy it to the public downloads folder or notify
                    onComplete(true, "Successfully downloaded ${file.name} to External storage!")
                } else {
                    onComplete(false, "Download failed from Telegram servers.")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error in downloadFile", e)
                onComplete(false, "Error: ${e.localizedMessage ?: "Unknown download error"}")
            }
        }
    }

    private fun formatSpeed(bps: Long): String {
        if (bps <= 0) return "0 KB/s"
        val kbps = bps / 1024.0
        val mbps = kbps / 1024.0
        val df = DecimalFormat("#.##")
        return if (mbps > 1.0) {
            "${df.format(mbps)} MB/s"
        } else {
            "${df.format(kbps)} KB/s"
        }
    }
}

class MainViewModelFactory(private val repository: FileRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
