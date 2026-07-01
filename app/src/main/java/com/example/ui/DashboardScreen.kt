package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VFile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentScreen by viewModel.currentScreen.collectAsState()
    val activeChannelId by viewModel.activeChannelId.collectAsState()
    val virtualDrives by viewModel.virtualDrives.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val activeAccountIndex by viewModel.activeAccountIndex.collectAsState()

    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val fileFilter by viewModel.fileFilter.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val uploadTasks by viewModel.uploadTasks.collectAsState()

    // Database states
    val allFiles by viewModel.files.collectAsState()
    val favoriteFiles by viewModel.favoriteFiles.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val trashFiles by viewModel.trashFiles.collectAsState()
    val totalStorageUsed by viewModel.totalStorageUsed.collectAsState()

    // Dialog & Controllers
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<VFile?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var showAddChannelDialog by remember { mutableStateOf(false) }
    var channelNameInput by remember { mutableStateOf("") }
    var channelDescInput by remember { mutableStateOf("") }
    var channelTypeInput by remember { mutableStateOf("all") } // "all", "video", "document", "music"

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountPhoneInput by remember { mutableStateOf("") }

    var showActionSheetForFile by remember { mutableStateOf<VFile?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var activePreviewFile by remember { mutableStateOf<VFile?>(null) }
    var activePreviewUrl by remember { mutableStateOf<String?>(null) }

    // Dropdowns
    var showAccountDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var showNewMenuDropdown by remember { mutableStateOf(false) }

    val totalStorageLimit = 100L * 1024 * 1024 * 1024 * 1024 // 100 TB default limit

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadFileFromUri(context, it)
        }
    }

    // Filter active items based on screen, search queries, and category buttons
    val filteredFiles = remember(
        allFiles, favoriteFiles, recentFiles, trashFiles,
        currentScreen, searchQuery, fileFilter
    ) {
        val baseList = when (currentScreen) {
            "Home" -> recentFiles.take(12)
            "MyDrive" -> allFiles
            "Shared" -> allFiles.filter { it.isShared }
            "Recent" -> recentFiles
            "Starred" -> favoriteFiles
            "Trash" -> trashFiles
            else -> allFiles
        }

        baseList.filter { file ->
            val matchesSearch = file.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (fileFilter) {
                "images" -> file.mimeType?.startsWith("image") == true || file.name.lowercase().endsWith(".jpg") || file.name.lowercase().endsWith(".png") || file.name.lowercase().endsWith(".jpeg")
                "videos" -> file.mimeType?.startsWith("video") == true || file.name.lowercase().endsWith(".mp4") || file.name.lowercase().endsWith(".mkv")
                "audio" -> file.mimeType?.startsWith("audio") == true || file.name.lowercase().endsWith(".mp3")
                "pdfs" -> file.mimeType == "application/pdf" || file.name.lowercase().endsWith(".pdf")
                "documents" -> file.mimeType?.startsWith("application/") == true || file.name.lowercase().endsWith(".pdf") || file.name.lowercase().endsWith(".xlsx") || file.name.lowercase().endsWith(".txt")
                else -> true
            }
            matchesSearch && (file.isFolder || matchesFilter)
        }
    }

    // Apply Sorting to files
    val sortedFiles = remember(filteredFiles, sortBy) {
        val (foldersPart, filesPart) = filteredFiles.partition { it.isFolder }
        val sortedFolders = when (sortBy) {
            "Name" -> foldersPart.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            "Date Modified" -> foldersPart.sortedByDescending { it.modifiedAt }
            else -> foldersPart.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        val sortedFilesPart = when (sortBy) {
            "Name" -> filesPart.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            "Date Modified" -> filesPart.sortedByDescending { it.modifiedAt }
            "File Size" -> filesPart.sortedByDescending { it.size }
            "File Type" -> filesPart.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.mimeType ?: "" })
            else -> filesPart.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        sortedFolders + sortedFilesPart
    }

    val folders = remember(sortedFiles) { sortedFiles.filter { it.isFolder } }
    val files = remember(sortedFiles) { sortedFiles.filter { !it.isFolder } }

    // Modal Drawer wrapper
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(310.dp)
            ) {
                SidebarContent(
                    currentScreen = currentScreen,
                    totalStorageUsed = totalStorageUsed,
                    totalStorageLimit = totalStorageLimit,
                    virtualDrives = virtualDrives,
                    activeChannelId = activeChannelId,
                    onNavigate = { screen ->
                        viewModel.setScreen(screen)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onSelectChannel = { channelId ->
                        viewModel.switchVirtualDrive(context, channelId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onAddChannelClick = {
                        showAddChannelDialog = true
                    },
                    onLogout = {
                        onLogout()
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                // Wide Material 3 Google Drive Search bar
                Surface(
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(bottom = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 14.dp)
                        ) {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search in TeleDrive...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 16.sp
                                    )
                                }
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    textStyle = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }

                            // Sync / Refresh button with spinning animation
                            val transition = rememberInfiniteTransition(label = "SyncRotation")
                            val angle by transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "RotationAngle"
                            )

                            IconButton(
                                onClick = { viewModel.syncActiveChannel(context) },
                                enabled = !isSyncing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync Telegram Channel",
                                    tint = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.rotate(if (isSyncing) angle else 0f)
                                )
                            }

                            // Theme Toggle Button
                            IconButton(onClick = { viewModel.toggleTheme(context) }) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme"
                                )
                            }

                            // Notification Bell Button
                            val notificationsList by viewModel.notifications.collectAsState()
                            val unreadCount = remember(notificationsList) { notificationsList.count { !it.isRead } }

                            IconButton(onClick = { showNotificationsSheet = true }) {
                                BadgedBox(
                                    badge = {
                                        if (unreadCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ) {
                                                Text(unreadCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Profile bubble Multi-Account switcher trigger
                            Box {
                                val activePhone = accounts.getOrNull(activeAccountIndex) ?: "+1"
                                val initials = if (activePhone.length > 3) activePhone.takeLast(2) else "TG"
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { showAccountDropdown = true }
                                ) {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                // Dropdown containing multi-accounts list
                                DropdownMenu(
                                    expanded = showAccountDropdown,
                                    onDismissRequest = { showAccountDropdown = false }
                                ) {
                                    Text(
                                        text = "Telegram Accounts",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    accounts.forEachIndexed { index, phone ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(phone)
                                                    if (index == activeAccountIndex) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Active",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showAccountDropdown = false
                                                viewModel.switchAccount(context, index)
                                            }
                                        )
                                    }
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text("+ Add Telegram Phone") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        onClick = {
                                            showAccountDropdown = false
                                            showAddAccountDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        // Wide filter chips slider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filters = listOf(
                                "all" to "All Files",
                                "images" to "Images",
                                "videos" to "Videos",
                                "documents" to "Docs",
                                "audio" to "Music",
                                "pdfs" to "PDFs"
                            )
                            filters.forEach { (key, label) ->
                                FilterChip(
                                    selected = fileFilter == key,
                                    onClick = { viewModel.setFileFilter(key) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                // Floating Action Button with Google-style "+ New" dropdown options
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { showNewMenuDropdown = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Add options") },
                        text = { Text("New") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    )

                    DropdownMenu(
                        expanded = showNewMenuDropdown,
                        onDismissRequest = { showNewMenuDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Folder") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showNewMenuDropdown = false
                                showNewFolderDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Upload File") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                            onClick = {
                                showNewMenuDropdown = false
                                filePickerLauncher.launch("*/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Virtual Drive Channel") },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                            onClick = {
                                showNewMenuDropdown = false
                                showAddChannelDialog = true
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Sorting selector & Header Details
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title/Breadcrumbs Area
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (currentScreen == "MyDrive" && currentFolderId != 0L) {
                                IconButton(
                                    onClick = { viewModel.navigateBack() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            val activeDrive = virtualDrives.find { it.id == activeChannelId }
                            val screenLabel = when (currentScreen) {
                                "Home" -> "Home"
                                "MyDrive" -> {
                                    if (breadcrumbs.isNotEmpty()) {
                                        breadcrumbs.last().name
                                    } else {
                                        activeDrive?.name ?: "My Drive"
                                    }
                                }
                                "Shared" -> "Shared Files"
                                "Recent" -> "Recent Activity"
                                "Starred" -> "Starred Files"
                                "Trash" -> "Trash Bin"
                                else -> "Storage"
                            }

                            Text(
                                text = screenLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Sorting Dropdown Trigger & View Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Empty Trash action if on Trash Screen
                            if (currentScreen == "Trash" && trashFiles.isNotEmpty()) {
                                TextButton(onClick = { viewModel.emptyTrash(context) }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Empty Trash", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }

                            Box {
                                TextButton(onClick = { showSortDropdown = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(sortBy, fontSize = 12.sp)
                                }

                                DropdownMenu(
                                    expanded = showSortDropdown,
                                    onDismissRequest = { showSortDropdown = false }
                                ) {
                                    val sortOptions = listOf("Name", "Date Modified", "File Size", "File Type")
                                    sortOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                showSortDropdown = false
                                                viewModel.updateSortBy(option)
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                                    contentDescription = "Toggle View"
                                )
                            }
                        }
                    }

                    // Main Empty State Screen
                    if (sortedFiles.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (currentScreen == "Trash") Icons.Default.DeleteOutline else Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(90.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (currentScreen == "Trash") "Trash is empty" else "No items found here",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentScreen == "Trash") "Files and folders moved to trash will appear here. After 30 days, they are permanently destroyed."
                                       else "Use the menu below to add folders or upload media from your device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                            if (currentScreen != "Trash") {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload to Cloud")
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Storage Overview Card
                            if (currentScreen == "Home" || currentScreen == "MyDrive") {
                                StorageOverviewCard(totalStorageUsed = totalStorageUsed, totalStorageLimit = totalStorageLimit)
                            }

                            // Files Grid vs List Layout
                            if (isGridView) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (folders.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Text(
                                                text = "FOLDERS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                                            )
                                        }
                                        items(folders, key = { it.id }) { item ->
                                            FileGridCard(
                                                file = item,
                                                currentScreen = currentScreen,
                                                onFolderClick = { viewModel.navigateToFolder(item.id) },
                                                onLongClick = { showActionSheetForFile = item }
                                            )
                                        }
                                    }
                                    if (files.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Text(
                                                text = "FILES",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                            )
                                        }
                                        items(files, key = { it.id }) { item ->
                                            FileGridCard(
                                                file = item,
                                                currentScreen = currentScreen,
                                                onFolderClick = { viewModel.navigateToFolder(item.id) },
                                                onLongClick = { showActionSheetForFile = item }
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (folders.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "FOLDERS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                                            )
                                        }
                                        items(folders, key = { it.id }) { item ->
                                            FileListRow(
                                                file = item,
                                                currentScreen = currentScreen,
                                                onFolderClick = { viewModel.navigateToFolder(item.id) },
                                                onLongClick = { showActionSheetForFile = item }
                                            )
                                        }
                                    }
                                    if (files.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "FILES",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                            )
                                        }
                                        items(files, key = { it.id }) { item ->
                                            FileListRow(
                                                file = item,
                                                currentScreen = currentScreen,
                                                onFolderClick = { viewModel.navigateToFolder(item.id) },
                                                onLongClick = { showActionSheetForFile = item }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sticky upload progress tray
                AnimatedVisibility(
                    visible = uploadTasks.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    UploadProgressTray(
                        tasks = uploadTasks,
                        onClear = { viewModel.clearFinishedTasks() }
                    )
                }
            }
        }
    }

    // Context / Long-press Action Sheet (Modal Bottom Sheet)
    if (showActionSheetForFile != null) {
        val activeFile = showActionSheetForFile!!
        ModalBottomSheet(
            onDismissRequest = { showActionSheetForFile = null },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Header details
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = getFileIcon(activeFile),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Column {
                        Text(
                            text = activeFile.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activeFile.isFolder) "Folder" else formatSize(activeFile.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Divider()

                // Conditional items depending on trash state
                if (activeFile.isInTrash) {
                    DropdownMenuItem(
                        text = { Text("Restore File") },
                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF2E7D32)) },
                        onClick = {
                            viewModel.restoreFromTrash(activeFile.id)
                            showActionSheetForFile = null
                            Toast.makeText(context, "${activeFile.name} restored", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            viewModel.deleteFilePermanently(activeFile.id)
                            showActionSheetForFile = null
                            Toast.makeText(context, "${activeFile.name} permanently deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    if (!activeFile.isFolder) {
                        DropdownMenuItem(
                            text = { Text("Preview / Stream Online") },
                            leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                            onClick = {
                                showActionSheetForFile = null
                                coroutineScope.launch {
                                    val url = viewModel.getStreamingUrl(activeFile.telegramFileId ?: "")
                                    activePreviewUrl = url
                                    activePreviewFile = activeFile
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Download to Local Storage") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showActionSheetForFile = null
                                Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
                                viewModel.downloadFile(context, activeFile) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(if (activeFile.isFavorite) "Remove Bookmark" else "Bookmark File") },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = if (activeFile.isFavorite) Color(0xFFFFB300) else Color.Gray) },
                        onClick = {
                            viewModel.toggleFavorite(activeFile.id)
                            showActionSheetForFile = null
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(if (activeFile.isShared) "Disable Sharing" else "Enable Shared Access") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            viewModel.toggleShared(activeFile.id)
                            showActionSheetForFile = null
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Rename Item") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            renameTargetFile = activeFile
                            renameInput = activeFile.name
                            showRenameDialog = true
                            showActionSheetForFile = null
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            viewModel.moveToTrash(activeFile.id)
                            showActionSheetForFile = null
                            Toast.makeText(context, "${activeFile.name} moved to Trash", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Media Preview Dialog
    if (activePreviewFile != null) {
        FilePreviewDialog(
            file = activePreviewFile!!,
            streamingUrl = activePreviewUrl,
            onDismiss = {
                activePreviewFile = null
                activePreviewUrl = null
            }
        )
    }

    // Create Channel (Virtual Drive) Dialog
    if (showAddChannelDialog) {
        AlertDialog(
            onDismissRequest = { showAddChannelDialog = false },
            title = { Text("Add Virtual Channel Drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = channelNameInput,
                        onValueChange = { channelNameInput = it },
                        label = { Text("Channel / Drive Name") },
                        placeholder = { Text("e.g. Work Folders") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = channelDescInput,
                        onValueChange = { channelDescInput = it },
                        label = { Text("Description") },
                        placeholder = { Text("Acts as Document virtual storage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Primary Drive Content Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val types = listOf(
                            "all" to "Generic",
                            "video" to "Videos",
                            "document" to "Docs",
                            "music" to "Audio"
                        )
                        types.forEach { (typeKey, typeLabel) ->
                            FilterChip(
                                selected = channelTypeInput == typeKey,
                                onClick = { channelTypeInput = typeKey },
                                label = { Text(typeLabel, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (channelNameInput.isNotBlank()) {
                            viewModel.addVirtualDrive(context, channelNameInput, channelDescInput, channelTypeInput)
                            channelNameInput = ""
                            channelDescInput = ""
                            channelTypeInput = "all"
                            showAddChannelDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChannelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Add Telegram Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter another phone number to link a separate cloud instance:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = accountPhoneInput,
                        onValueChange = { accountPhoneInput = it },
                        placeholder = { Text("+1 (555) 012-3456") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (accountPhoneInput.isNotBlank()) {
                            viewModel.addAccount(context, accountPhoneInput)
                            accountPhoneInput = ""
                            showAddAccountDialog = false
                        }
                    }
                ) {
                    Text("Link Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    placeholder = { Text("Untitled Folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            viewModel.createFolder(folderNameInput)
                            folderNameInput = ""
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && renameTargetFile != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameFile(renameTargetFile!!.id, renameInput)
                            showRenameDialog = false
                            renameTargetFile = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Notifications Bottom Sheet
    if (showNotificationsSheet) {
        val notifications by viewModel.notifications.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { 
                showNotificationsSheet = false 
                viewModel.markAllNotificationsAsRead()
            },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activity Log & Alerts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearNotifications() }) {
                            Text("Clear All")
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                if (notifications.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "All quiet for now",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Uploads, downloads, and other transfer events will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            val colorScheme = MaterialTheme.colorScheme
                            val (icon, color, bg) = when (notification.type) {
                                NotificationType.SUCCESS -> Triple(
                                    Icons.Default.CheckCircle,
                                    Color(0xFF2E7D32),
                                    Color(0xFFE8F5E9)
                                )
                                NotificationType.ERROR -> Triple(
                                    Icons.Default.Error,
                                    colorScheme.error,
                                    colorScheme.errorContainer.copy(alpha = 0.2f)
                                )
                                NotificationType.INFO -> Triple(
                                    Icons.Default.Info,
                                    colorScheme.primary,
                                    colorScheme.primaryContainer.copy(alpha = 0.2f)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (notification.isRead) colorScheme.surfaceVariant.copy(alpha = 0.3f) else colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .clickable { viewModel.markNotificationAsRead(notification.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Type Icon with background circle
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(bg)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Text Content
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = notification.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                                            color = colorScheme.onSurface
                                        )
                                        if (!notification.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(colorScheme.primary)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notification.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                                    val timeStr = remember(notification.timestamp) { sdf.format(Date(notification.timestamp)) }
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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

@Composable
fun SidebarContent(
    currentScreen: String,
    totalStorageUsed: Long,
    totalStorageLimit: Long,
    virtualDrives: List<VirtualDrive>,
    activeChannelId: Long,
    onNavigate: (String) -> Unit,
    onSelectChannel: (Long) -> Unit,
    onAddChannelClick: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .navigationBarsPadding()
    ) {
        // Upper logo and title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White)
            }
            Text(
                text = "TeleDrive",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Divider(modifier = Modifier.padding(bottom = 12.dp))

        // Navigation Items
        SidebarItem(
            title = "Home",
            icon = Icons.Default.Home,
            isSelected = currentScreen == "Home",
            onClick = { onNavigate("Home") }
        )
        SidebarItem(
            title = "My Drive",
            icon = Icons.Default.FolderOpen,
            isSelected = currentScreen == "MyDrive",
            onClick = { onNavigate("MyDrive") }
        )
        SidebarItem(
            title = "Starred",
            icon = Icons.Default.StarBorder,
            isSelected = currentScreen == "Starred",
            onClick = { onNavigate("Starred") }
        )
        SidebarItem(
            title = "Shared",
            icon = Icons.Default.PeopleOutline,
            isSelected = currentScreen == "Shared",
            onClick = { onNavigate("Shared") }
        )
        SidebarItem(
            title = "Recent",
            icon = Icons.Default.AccessTime,
            isSelected = currentScreen == "Recent",
            onClick = { onNavigate("Recent") }
        )
        SidebarItem(
            title = "Trash",
            icon = Icons.Default.DeleteOutline,
            isSelected = currentScreen == "Trash",
            onClick = { onNavigate("Trash") }
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        // Virtual Drives List section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VIRTUAL DRIVES (CHANNELS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            IconButton(
                onClick = onAddChannelClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Add Channel Drive",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(virtualDrives) { drive ->
                val isSelected = drive.id == activeChannelId
                val driveIcon = when (drive.iconType) {
                    "video" -> Icons.Default.VideoFile
                    "document" -> Icons.Default.DocumentScanner
                    "music" -> Icons.Default.MusicNote
                    else -> Icons.Default.FolderSpecial
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                        .clickable { onSelectChannel(drive.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = driveIcon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = drive.name,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = drive.description,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Space Storage Indicator card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFC2E7FF))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = Color(0xFF001D35),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Storage used",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF001D35)
                )
            }

            val storagePct = (totalStorageUsed.toFloat() / totalStorageLimit).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF001D35).copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(storagePct)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color(0xFF0B57D0))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${formatSize(totalStorageUsed)} of ${formatSize(totalStorageLimit)} used",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF001D35),
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logout button
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign out / Disconnect")
        }
    }
}

@Composable
fun SidebarItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
fun StorageOverviewCard(
    totalStorageUsed: Long,
    totalStorageLimit: Long,
    modifier: Modifier = Modifier
) {
    val storagePct = (totalStorageUsed.toFloat() / totalStorageLimit).coerceIn(0f, 1f)
    val percentageText = "${(storagePct * 100).toInt()}%"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFC2E7FF)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage used",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF001D35)
                )
                Text(
                    text = percentageText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF001D35).copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF001D35).copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(storagePct)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color(0xFF0B57D0))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${formatSize(totalStorageUsed)} of ${formatSize(totalStorageLimit)} utilized",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF001D35)
            )
        }
    }
}

data class DensityColors(val container: Color, val content: Color)

fun getDensityColors(file: VFile): DensityColors {
    if (file.isFolder) {
        return DensityColors(container = Color(0xFFEFF6FF), content = Color(0xFF1A73E8))
    }
    val nameLower = file.name.lowercase()
    val mimeLower = file.mimeType?.lowercase() ?: ""
    return when {
        nameLower.endsWith(".pdf") || mimeLower == "application/pdf" -> 
            DensityColors(container = Color(0xFFFEF2F2), content = Color(0xFFEF4444))
        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || mimeLower.startsWith("image/") -> 
            DensityColors(container = Color(0xFFECFDF5), content = Color(0xFF10B981))
        nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || mimeLower.startsWith("audio/") -> 
            DensityColors(container = Color(0xFFF5F3FF), content = Color(0xFF8B5CF6))
        nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || mimeLower.startsWith("video/") -> 
            DensityColors(container = Color(0xFFFFFBEB), content = Color(0xFFF59E0B))
        nameLower.endsWith(".zip") || nameLower.endsWith(".rar") || nameLower.endsWith(".tar") || nameLower.endsWith(".gz") -> 
            DensityColors(container = Color(0xFFEEF2F6), content = Color(0xFF475569))
        else -> 
            DensityColors(container = Color(0xFFEFF6FF), content = Color(0xFF3B82F6))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    file: VFile,
    currentScreen: String,
    onFolderClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dColors = getDensityColors(file)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { if (file.isFolder && !file.isInTrash) onFolderClick() else onLongClick() },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(dColors.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileIcon(file),
                    contentDescription = null,
                    tint = dColors.content,
                    modifier = Modifier.size(36.dp)
                )
                if (file.isFavorite && !file.isInTrash) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = file.name,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Trash countdown description or standard file meta
            if (file.isInTrash) {
                val elapsedMs = System.currentTimeMillis() - (file.deletedAt ?: System.currentTimeMillis())
                val elapsedDays = (elapsedMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                val daysLeft = (30 - elapsedDays).coerceAtLeast(1)
                Text(
                    text = "Deletes in $daysLeft days",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (file.isFolder) "Folder" else formatSize(file.size),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    IconButton(
                        onClick = onLongClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions", modifier = Modifier.size(14.dp), tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    file: VFile,
    currentScreen: String,
    onFolderClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dColors = getDensityColors(file)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { if (file.isFolder && !file.isInTrash) onFolderClick() else onLongClick() },
                onLongClick = onLongClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(dColors.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getFileIcon(file),
                contentDescription = null,
                tint = dColors.content,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (file.isFavorite && !file.isInTrash) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(13.dp))
                }
            }
            if (file.isInTrash) {
                val elapsedMs = System.currentTimeMillis() - (file.deletedAt ?: System.currentTimeMillis())
                val elapsedDays = (elapsedMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                val daysLeft = (30 - elapsedDays).coerceAtLeast(1)
                Text(
                    text = "Deletes in $daysLeft days • Trash Bin",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = if (file.isFolder) "Folder" else formatSize(file.size),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        IconButton(
            onClick = onLongClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun UploadProgressTray(
    tasks: List<UploadTask>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ongoingCount = tasks.count { it.status == "Uploading" }
                    if (ongoingCount > 0) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Uploading $ongoingCount files...",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Text(
                            text = "All uploads completed",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear tray")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable list of transfers
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks) { task ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.5f)
                            )
                            Text(
                                text = if (task.status == "Uploading") "${task.progress}% • ${task.speed}" else task.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (task.status == "Success") Color(0xFF2E7D32) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        if (task.status == "Uploading") {
                            LinearProgressIndicator(
                                progress = { task.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = task.eta,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
