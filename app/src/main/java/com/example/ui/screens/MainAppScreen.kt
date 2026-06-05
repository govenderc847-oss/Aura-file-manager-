package com.example.ui.screens

import androidx.compose.animation.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.CloudAccount
import com.example.model.CloudFile
import com.example.model.SecureFileEntity
import com.example.viewmodel.FileViewModel

enum class MainTab(val title: String, val icon: ImageVector) {
    EXPLORER("Explorer", Icons.Default.Folder),
    VAULT("Vault", Icons.Default.Security),
    CLOUD("Cloud Drives", Icons.Default.Cloud),
    PREMIUM("Premium", Icons.Default.Star)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppScreen(
    viewModel: FileViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val settings by viewModel.vaultSettings.collectAsState()
    val toastMsg by viewModel.toastMessage.collectAsState()
    val currentTheme = settings.selectedTheme

    val snackbarHostState = remember { SnackbarHostState() }

    // Trigger Snackbar on Custom UI Toasts
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    var activeTab by remember { mutableStateOf(MainTab.EXPLORER) }

    MyApplicationTheme(selectedTheme = currentTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    MainTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { if (targetState.ordinal > initialState.ordinal) 1000 else -1000 },
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 1200f)
                        ) with slideOutHorizontally(
                            targetOffsetX = { if (targetState.ordinal > initialState.ordinal) -1000 else 1000 },
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 1200f)
                        )
                    }
                ) { targetTab ->
                    when (targetTab) {
                        MainTab.EXPLORER -> LocalExplorerScreen(viewModel)
                        MainTab.VAULT -> CryptographicVaultScreen(viewModel)
                        MainTab.CLOUD -> CloudDrivesScreen(viewModel)
                        MainTab.PREMIUM -> PremiumDashboardScreen(viewModel)
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 1: LOCAL EXPLORER SCREEN
// =========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalExplorerScreen(viewModel: FileViewModel) {
    val currentPath by viewModel.currentPath.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val flowFiles by viewModel.currentFolderFiles.collectAsState()
    val settings by viewModel.vaultSettings.collectAsState()
    val allLocalFiles by viewModel.allLocalFilesFlow.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }
    var isNewFolder by remember { mutableStateOf(false) }

    var selectedFileToOptions by remember { mutableStateOf<SecureFileEntity?>(null) }
    var viewFileDetails by remember { mutableStateOf<SecureFileEntity?>(null) }

    // Advanced features states
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showJunkCleanerDialog by remember { mutableStateOf(false) }
    var showDuplicateFinderDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }

    // Grouping logic for files
    val imagesList = allLocalFiles.filter { !it.isFolder && (it.mimeType.startsWith("image/") || it.name.endsWith(".png", true) || it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".gif", true)) }
    val videosList = allLocalFiles.filter { !it.isFolder && (it.mimeType.startsWith("video/") || it.name.endsWith(".mp4", true) || it.name.endsWith(".mkv", true) || it.name.endsWith(".avi", true)) }
    val audioList = allLocalFiles.filter { !it.isFolder && (it.mimeType.startsWith("audio/") || it.name.endsWith(".mp3", true) || it.name.endsWith(".wav", true) || it.name.endsWith(".aac", true)) }
    val docsList = allLocalFiles.filter { !it.isFolder && (it.mimeType.startsWith("text/") || it.name.endsWith(".pdf", true) || it.name.endsWith(".docx", true) || it.name.endsWith(".xlsx", true) || it.name.endsWith(".csv", true) || it.name.endsWith(".txt", true) || it.name.endsWith(".backup", true) || it.name.endsWith(".vault", true) || it.mimeType == "application/octet-stream") }
    val apksList = allLocalFiles.filter { !it.isFolder && it.name.endsWith(".apk", true) }
    val starredList = allLocalFiles.filter { it.isFavorite }

    val categoryMap = mapOf(
        "Images" to imagesList,
        "Videos" to videosList,
        "Audio" to audioList,
        "Documents" to docsList,
        "APKs" to apksList,
        "Starred" to starredList
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Banner
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .clickable { showProfileMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LG",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OmniFile Pro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (settings.isPremiumUser) "Premium Active • Ad-Free" else "Standard Edition • Local Secure",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.isPremiumUser) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showProfileMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Quick Options Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search Input Area
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search virtual storage...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explorer_search_bar")
            )
        }

        // Storage Overview & Operations (Google Files Vibe)
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Device Virtual Sandbox",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "184.2 GB used of 256 GB (72%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = 0.72f,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showJunkCleanerDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clean Junk", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showDuplicateFinderDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Duplicates", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Categories Grid Header
        item {
            Text(
                text = "File Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Categories Grid List (2 rows of 3 items, looking fresh)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val gridData = listOf(
                    Triple("Images", Icons.Default.Image, imagesList.size),
                    Triple("Videos", Icons.Default.PlayCircle, videosList.size),
                    Triple("Audio", Icons.Default.MusicNote, audioList.size),
                    Triple("Documents", Icons.Default.Article, docsList.size),
                    Triple("APKs", Icons.Default.Android, apksList.size),
                    Triple("Starred", Icons.Default.Star, starredList.size)
                )

                for (rowIndex in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (colIndex in 0..2) {
                            val data = gridData[rowIndex * 3 + colIndex]
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCategory = data.first },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val iconColor = if (data.first == "Starred") {
                                        Color(0xFFEAB308)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                    Icon(
                                        imageVector = data.second,
                                        contentDescription = data.first,
                                        tint = iconColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = data.first,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${data.third} items",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Directories / Local Files Storage Browser
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage Directories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Navigation Path bar or action status
                Button(
                    onClick = {
                        newFileName = ""
                        newFileContent = ""
                        isNewFolder = false
                        showCreateDialog = true
                    },
                    modifier = Modifier.testTag("create_new_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item", fontSize = 12.sp)
                }
            }
        }

        // Path Directory indicator
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (currentPath != "/") {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate Parent Directory",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Current Path: $currentPath",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Listed Folder Files
        if (flowFiles.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This folder is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(flowFiles) { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (file.isFolder) {
                                    viewModel.navigateToFolder(file.name)
                                } else {
                                    viewFileDetails = file
                                }
                            },
                            onLongClick = {
                                selectedFileToOptions = file
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        val icon = when {
                            file.isFolder -> Icons.Default.Folder
                            file.isEncrypted -> Icons.Default.Lock
                            else -> Icons.Default.Description
                        }
                        val iconTint = when {
                            file.isEncrypted -> MaterialTheme.colorScheme.error
                            file.isFolder -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .background(iconTint.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (file.isFolder) "Directory Folder" else "Size: ${formatSize(file.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (file.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Starred Document",
                                tint = Color(0xFFEAB308),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { selectedFileToOptions = file },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DYNAMIC OVERLAY DIALOGS (ADVANCED FEATURES)
    // =========================================================================

    // 1. Profile / Settings Switch Menu
    if (showProfileMenu) {
        Dialog(onDismissRequest = { showProfileMenu = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("LG", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Lorenzo Govender",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "govenderc847@gmail.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Customize App Themes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // List theme choices directly for quick premium feel
                    val themes = listOf("Sophisticated Dark", "Cobalt Blue", "Luxury Dark", "Crimson Velvet", "Olive Grove")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        themes.forEach { themeName ->
                            val isSelected = settings.selectedTheme == themeName
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.changeTheme(themeName) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.changeTheme(themeName) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = themeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showProfileMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Configurations")
                    }
                }
            }
        }
    }

    // 2. Sophisticated Cache cleaner Dialog
    if (showJunkCleanerDialog) {
        var scanningPhase by remember { mutableStateOf(0) } // 0: initial, 1: scanning, 2: completed
        var scannedBytes by remember { mutableStateOf(0L) }
        val scope = rememberCoroutineScope()

        Dialog(onDismissRequest = { showJunkCleanerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "System Junk Cleaner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (scanningPhase == 0) {
                        Text(
                            text = "Clean system temporary stream artifacts, compiled caches, and application thumbnail residuals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Thumbnails Cache", style = MaterialTheme.typography.bodySmall)
                                Text("4.8 MB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Log Streams Residuals", style = MaterialTheme.typography.bodySmall)
                                Text("3.2 MB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Temporary Stream Buffers", style = MaterialTheme.typography.bodySmall)
                                Text("6.2 MB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                scanningPhase = 1
                                scope.launch {
                                    kotlinx.coroutines.delay(1800)
                                    scanningPhase = 2
                                };
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Purge Junk Cache (14.2 MB)")
                        }
                    } else if (scanningPhase == 1) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Optimizing stream indexes...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Cleanup Successful!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "14.2 MB cache bytes successfully reclaimed. Encryption index latency decreased by 32%.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showJunkCleanerDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Eradicate Dialogue")
                        }
                    }
                }
            }
        }
    }

    // 3. Active Duplicate Finder dialog
    if (showDuplicateFinderDialog) {
        // Scans all virtual files and checks for duplicated entries by name
        val potentialDuplicates = allLocalFiles.filter { !it.isFolder }.filter { file ->
            val nameLower = file.name.lowercase()
            allLocalFiles.any { other -> other.id != file.id && other.name.lowercase() == nameLower } ||
            nameLower.contains("copy") || nameLower.contains("(1)")
        }

        var checkedItems by remember { mutableStateOf(setOf<Int>()) }

        Dialog(onDismissRequest = { showDuplicateFinderDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FindInPage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Duplicate File Consolidator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "The system scans the internal workspace for duplicate names, copies, and redundant backups to securely consolidate storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (potentialDuplicates.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("No storage duplicates found!", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showDuplicateFinderDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close Consolidation")
                        }
                    } else {
                        // Checklist inside column
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(potentialDuplicates) { file ->
                                val isChecked = checkedItems.contains(file.id)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                checkedItems = if (isChecked) {
                                                    checkedItems - file.id
                                                } else {
                                                    checkedItems + file.id
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Path: ${file.path} • Size: ${formatSize(file.sizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                // Delete selected items permanently
                                potentialDuplicates.filter { checkedItems.contains(it.id) }.forEach { item ->
                                    viewModel.deleteFile(item)
                                }
                                viewModel.showToast("Consolidated ${checkedItems.size} duplicate file items.")
                                showDuplicateFinderDialog = false
                            },
                            enabled = checkedItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Consolidate Matches (${checkedItems.size})", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { showDuplicateFinderDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Discard consolidation list")
                        }
                    }
                }
            }
        }
    }

    // 4. Categorical Browser Details Dialogue (Shows files belonging to that category)
    selectedCategory?.let { categoryName ->
        val listMatchedFiles = categoryMap[categoryName] ?: emptyList()

        Dialog(onDismissRequest = { selectedCategory = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Viewing: $categoryName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (listMatchedFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No files configured here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(listMatchedFiles) { file ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategory = null
                                            viewFileDetails = file
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (file.isEncrypted) Icons.Default.Lock else Icons.Default.Description,
                                            contentDescription = null,
                                            tint = if (file.isEncrypted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Size: ${formatSize(file.sizeBytes)} • Path: ${file.path}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                selectedCategory = null
                                                selectedFileToOptions = file
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options context", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    TextButton(
                        onClick = { selectedCategory = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Dismiss Browser")
                    }
                }
            }
        }
    }

    // =========================================================================
    // STANDARD SHARED OPTIONS DIALOGS
    // =========================================================================

    // 1. New File or Folder Creation Dialog
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create Virtual Item",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Folder or File Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = !isNewFolder,
                            onClick = { isNewFolder = false },
                            label = { Text("New File") }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        FilterChip(
                            selected = isNewFolder,
                            onClick = { isNewFolder = true },
                            label = { Text("New Folder") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Name (e.g. secure_indices.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isNewFolder) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newFileContent,
                            onValueChange = { newFileContent = it },
                            label = { Text("Raw content/ledger text") },
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                viewModel.createNewLocalFile(newFileName, newFileContent, isNewFolder)
                                showCreateDialog = false
                            }
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    // 2. Options Bottom Drawer / Dialog Simulation
    selectedFileToOptions?.let { file ->
        Dialog(onDismissRequest = { selectedFileToOptions = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Path: ${file.path}/${file.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Option items
                    ListItem(
                        headlineContent = { Text(if (file.isFavorite) "Remove from Starred" else "Add to Starred") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = if (file.isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable {
                            viewModel.toggleFavorite(file)
                            selectedFileToOptions = null
                        }
                    )
                    
                    if (!file.isFolder) {
                        ListItem(
                            headlineContent = { Text(if (file.isEncrypted) "AES Decrypt File" else "AES Encrypt GCM Block") },
                            leadingContent = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                if (file.isEncrypted) {
                                    viewModel.decryptFile(file)
                                } else {
                                    viewModel.encryptFile(file)
                                }
                                selectedFileToOptions = null
                            }
                        )
                    }

                    ListItem(
                        headlineContent = { Text("Delete Item permanently", color = Color.Red) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        modifier = Modifier.clickable {
                            viewModel.deleteFile(file)
                            selectedFileToOptions = null
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { selectedFileToOptions = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }

    // 3. View Details / Decrypt viewer modal
    viewFileDetails?.let { file ->
        var passwordInput by remember { mutableStateOf("") }
        var isLocalDecrypted by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { viewFileDetails = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (file.isEncrypted) Icons.Default.Lock else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.isEncrypted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (file.isEncrypted && isLocalDecrypted == null) {
                        Text(
                            text = "This document is locked with high-security client-side AES-GCM layer. Provide the vault password to visualize decrypted buffer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Vault Passcode") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val result = com.example.utils.CryptoUtils.decrypt(
                                    file.encryptedBase64Payload,
                                    passwordInput,
                                    settings.aesKeySizeBits
                                )
                                if (result != null) {
                                    isLocalDecrypted = result
                                    viewModel.showToast("AES GCM Decryption Block authorized.")
                                } else {
                                    viewModel.showToast("Invalid password key specification.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Decrypt Buffer")
                        }
                    } else {
                        // Display decoded stream or raw contents
                        val displayBody = isLocalDecrypted ?: file.encryptedBase64Payload
                        Text(
                            text = "Content Preview:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(12.dp)) {
                                item {
                                    Text(
                                        text = displayBody,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewFileDetails = null }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 2: CRYPTOGRAPHIC SECURE VAULT
// =========================================================================
@Composable
fun CryptographicVaultScreen(viewModel: FileViewModel) {
    val settings by viewModel.vaultSettings.collectAsState()
    val isUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val allFiles by viewModel.allLocalFilesFlow.collectAsState()

    var passcodeEntry by remember { mutableStateOf("") }
    var setupModeStep by remember { mutableStateOf(false) }

    val vaultEncryptedFiles = remember(allFiles) {
        allFiles.filter { it.isEncrypted }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Safe lock branding heading
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "AES Cryptographic Vault",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Zero-Knowledge Local Storage Encrypter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (settings.vaultPasswordHash == null) {
            // First time setup
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EnhancedEncryption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Initialize Secure Key Ring",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Because Vault is client-side encrypted using secure PBKDF2 parameters, the passcode is never passed anywhere. If you lose this key, encrypted files cannot be restored.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = passcodeEntry,
                        onValueChange = { passcodeEntry = it },
                        label = { Text("Choose Passcode / Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (passcodeEntry.length >= 4) {
                                viewModel.setupVaultPasscode(passcodeEntry)
                                passcodeEntry = ""
                            } else {
                                viewModel.showToast("Passcode must be minimum 4 characters")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Secured Vault Wallet")
                    }
                }
            }
        } else if (!isUnlocked) {
            // Unlocked challenge screen
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Vault Locked",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Submit the security passcode to initiate the cipher and load encrypted partitions.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = passcodeEntry,
                        onValueChange = { passcodeEntry = it },
                        label = { Text("Enter Vault Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_password_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.authenticateVault(passcodeEntry)
                            passcodeEntry = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_vault_button")
                    ) {
                        Text("Unlock Cipher")
                    }
                }
            }
        } else {
            // Vault Unlocked: Manage Encrypted lists
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Session Cipher Active (AES-${settings.aesKeySizeBits}-GCM)",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Button(
                    onClick = { viewModel.lockVault() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lock")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Secured Vault Container Items:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "These files can only be accessed with the correct cryptographic header details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (vaultEncryptedFiles.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No encrypted files inside local vault.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Go to Explorer, click context option of any file and encrypt it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(vaultEncryptedFiles) { file ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Relative location: ${file.path}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.decryptFile(file) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = "Decrypt back",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Decrypted live state container
                                var showDecryptedText by remember { mutableStateOf(false) }
                                var decryptedContentResult by remember { mutableStateOf<String?>(null) }

                                if (showDecryptedText) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = "Decrypted Payload Buffer:",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = decryptedContentResult ?: "Cipher decryption error.",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        if (showDecryptedText) {
                                            showDecryptedText = false
                                        } else {
                                            // Decrypt using cipher session
                                            val decryptedResult = com.example.utils.CryptoUtils.decrypt(
                                                file.encryptedBase64Payload,
                                                viewModel.sessionVaultPassword.value ?: "",
                                                settings.aesKeySizeBits
                                            )
                                            decryptedContentResult = decryptedResult
                                            showDecryptedText = true
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.Start)
                                ) {
                                    Text(if (showDecryptedText) "Hide Plain Text" else "View Decrypted Text Stream")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: CLOUD STORAGE SCREEN
// =========================================================================
@Composable
fun CloudDrivesScreen(viewModel: FileViewModel) {
    val connectedByDb by viewModel.connectedAccounts.collectAsState()
    val cloudFilesList by viewModel.cloudFiles.collectAsState()
    val searchCloudQuery by viewModel.searchCloudQuery.collectAsState()

    var activeCloudAccountToConnect by remember { mutableStateOf<String?>(null) }
    var cloudEmailInput by remember { mutableStateOf("") }

    var cloudFileOptions by remember { mutableStateOf<CloudFile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Safe Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Cloud Integration Suite",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Google Drive, Dropbox & OneDrive Bridge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Browsing clouds grid tabs
        Text(
            text = "Integrate Services Securely:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val clouds = listOf("Google Drive", "Dropbox", "OneDrive")
            clouds.forEach { item ->
                val connectedModel = connectedByDb.find { it.service == item }
                val isConnected = connectedModel != null

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (isConnected) {
                                viewModel.disconnectCloudDrive(item)
                            } else {
                                cloudEmailInput = ""
                                activeCloudAccountToConnect = item
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isConnected) "Connected" else "Tap Bridge",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (connectedByDb.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Bridging is offline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect to any cloud drive service listed above to simulate secure cloud storage caching and encrypted synchronization layers.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Connected drive status and browser options
            Text(
                text = "Cloud Storage Drives Browser:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchCloudQuery,
                onValueChange = { viewModel.searchCloudQuery.value = it },
                placeholder = { Text("Search virtual cloud metadata...") },
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val servicesConnected = connectedByDb.map { it.service }
                val filteredFiles = cloudFilesList.filter { servicesConnected.contains(it.cloudService) }

                items(filteredFiles) { cloudFile ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (cloudFile.isFolder) Icons.Default.Folder else Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cloudFile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Provider: ${cloudFile.cloudService} ${if (cloudFile.isFolder) "" else "• Size: ${formatSize(cloudFile.sizeBytes)}"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { cloudFileOptions = cloudFile }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Simulated Cloud Download", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. Interactive Account Selection & Simulated OAuth Handshake Dialog
    activeCloudAccountToConnect?.let { item ->
        var simulatedProgressStep by remember { mutableStateOf(0) } // 0: select profile, 1: authenticating handshakes, 2: success
        var progressMessage by remember { mutableStateOf("Contacting authentication servers...") }
        var showCustomLoginInput by remember { mutableStateOf(false) }
        var passwordValue by remember { mutableStateOf("") }
        val lifecycleScope = rememberCoroutineScope()

        Dialog(onDismissRequest = { 
            activeCloudAccountToConnect = null 
            simulatedProgressStep = 0
            showCustomLoginInput = false
        }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (simulatedProgressStep) {
                        0 -> {
                            // Account Picker view
                            val providerIcon = when (item) {
                                "Google Drive" -> Icons.Default.CloudSync
                                "Dropbox" -> Icons.Default.FolderOpen
                                else -> Icons.Default.Cloud
                            }
                            val brandColor = when (item) {
                                "Google Drive" -> Color(0xFFEA4335)
                                "Dropbox" -> Color(0xFF0061FE)
                                else -> Color(0xFF0078D4)
                            }

                            Icon(
                                imageVector = providerIcon,
                                contentDescription = null,
                                tint = brandColor,
                                modifier = Modifier.size(56.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Authorize Connection",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Choose a secure account profile to connect this device sandbox shell to $item.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            if (!showCustomLoginInput) {
                                // Default Active Accounts Available (Simulating active phone accounts)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Option A: Lorenzo Govender Google/Dropbox/MS account
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                cloudEmailInput = "govenderc847@gmail.com"
                                                simulatedProgressStep = 1
                                                lifecycleScope.launch {
                                                    progressMessage = "Requesting secure tokens from $item API..."
                                                    kotlinx.coroutines.delay(800)
                                                    progressMessage = "Exchanging signed OAuth 2.0 TLS certificates..."
                                                    kotlinx.coroutines.delay(800)
                                                    progressMessage = "Decrypting cloud metadata schemas..."
                                                    kotlinx.coroutines.delay(700)
                                                    simulatedProgressStep = 2
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(brandColor.copy(alpha = 0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "LG",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = brandColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Lorenzo Govender",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "govenderc847@gmail.com",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // Option B: Work Workspace account
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                cloudEmailInput = "l.govender@omnifile.io"
                                                simulatedProgressStep = 1
                                                lifecycleScope.launch {
                                                    progressMessage = "Accessing Omnifile Enterprise tenant credentials..."
                                                    kotlinx.coroutines.delay(800)
                                                    progressMessage = "Issuing single-sign-on claims with $item..."
                                                    kotlinx.coroutines.delay(800)
                                                    progressMessage = "Validating organizational storage quotas..."
                                                    kotlinx.coroutines.delay(700)
                                                    simulatedProgressStep = 2
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Business,
                                                    modifier = Modifier.size(16.dp),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Work Account (OmniFile Ltd)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "l.govender@omnifile.io",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // Option C: Manual Entry trigger
                                    OutlinedButton(
                                        onClick = { showCustomLoginInput = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Use dynamic custom credential email", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                // Manual Input Forms
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = cloudEmailInput,
                                        onValueChange = { cloudEmailInput = it },
                                        label = { Text("Provider Account Email") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = passwordValue,
                                        onValueChange = { passwordValue = it },
                                        label = { Text("Account password security key") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            if (cloudEmailInput.isNotEmpty()) {
                                                simulatedProgressStep = 1
                                                lifecycleScope.launch {
                                                    progressMessage = "Authenticating secret hash credentials..."
                                                    kotlinx.coroutines.delay(950)
                                                    progressMessage = "Obtaining OAuth tokens from $item..."
                                                    kotlinx.coroutines.delay(850)
                                                    simulatedProgressStep = 2
                                                }
                                            } else {
                                                viewModel.showToast("Provide an account email identifier.")
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Authorize connection with $item", fontSize = 12.sp)
                                    }

                                    TextButton(
                                        onClick = { showCustomLoginInput = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Back to phone active profiles")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { activeCloudAccountToConnect = null }) {
                                    Text("Disconnect Gateway")
                                }
                            }
                        }

                        1 -> {
                            // Handshake Spinner UI
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Establishing secure TLS Tunnel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = progressMessage,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        2 -> {
                            // Success validation screen
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Authentication Authorized",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Successfully synthesized synchronization tunnels. $item connected under: $cloudEmailInput",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    viewModel.connectCloudDrive(item, cloudEmailInput)
                                    activeCloudAccountToConnect = null
                                    simulatedProgressStep = 0
                                    showCustomLoginInput = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Synchronize Cloud Indexes")
                            }
                        }
                    }
                }
            }
        }
    }

    // Cloud download dialog options
    cloudFileOptions?.let { file ->
        Dialog(onDismissRequest = { cloudFileOptions = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Download: ${file.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Do you want to download this cloud item into your secure local vault partition with automatic decryption caching?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { cloudFileOptions = null }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                viewModel.createNewLocalFile(
                                    name = "CloudDownload_${file.name}",
                                    content = "Retrieved from secure cloud storage sync: provider=${file.cloudService}",
                                    isFolder = false
                                )
                                cloudFileOptions = null
                            }
                        ) {
                            Text("Import and Save")
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: PREMIUM DASHBOARD & CONTROLLER SCREEN
// =========================================================================
@Composable
fun PremiumDashboardScreen(viewModel: FileViewModel) {
    val settings by viewModel.vaultSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }

    // Hardcoded list of exquisite theme colors mapping to static layouts
    val themesList = listOf("Sophisticated Dark", "Cobalt Blue", "Luxury Dark", "Crimson Velvet", "Olive Grove")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Premium Features",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Advanced Cryptography & Themes Center",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!settings.isPremiumUser) {
            item {
                // Interactive Promotion Upgrade Banner Card with a testtag for testing
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("premium_upgrade_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Unlock Lifetime Premium",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Unleash high-level AES key customization, absolute multi-drive cloud bridges, biometric access, premium full-bleed custom dynamic styling, and an ad-free interface.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.unlockPremium() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_premium_button")
                        ) {
                            Text("Upgrade For Lifetime Limitless • $4.99")
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Premium Lifetime Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Unlimited cryptographic locks & custom themes unlocked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Feature 1: AES Block Key Customization (Premium gated)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cryptography Bit Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Adjust standard Key-Size parameters for AES-GCM local cipher pipelines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected Key size: ${settings.aesKeySizeBits}-bit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            Button(
                                onClick = {
                                    if (settings.isPremiumUser) {
                                        viewModel.changeAesKeySize(128)
                                    } else {
                                        viewModel.showToast("128-bit selection is premium gated")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.aesKeySizeBits == 128) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("128", color = if (settings.aesKeySizeBits == 128) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (settings.isPremiumUser) {
                                        viewModel.changeAesKeySize(256)
                                    } else {
                                        viewModel.showToast("256-bit high security selection is premium gated")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.aesKeySizeBits == 256) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("256", color = if (settings.aesKeySizeBits == 256) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        // Feature 2: Color Theme Customization (Premium gated)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Visual Interface Styling",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize standard borders, cards, and accent colors to your preferred vibe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ListItem(
                        headlineContent = { Text("Active Design Theme") },
                        supportingContent = { Text(settings.selectedTheme) },
                        leadingContent = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Button(
                                onClick = {
                                    if (settings.isPremiumUser) {
                                        showThemeDialog = true
                                    } else {
                                        viewModel.showToast("Interface custom themes is gate-checked under Premium.")
                                    }
                                }
                            ) {
                                Text("Select Theme")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        // Feature 3: Biometrics & Extra Safety metrics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Biometric PIN authorization",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Verify biometric details before unlocking the AES container.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        var bioState by remember { mutableStateOf(false) }
                        Switch(
                            checked = bioState,
                            onCheckedChange = {
                                if (settings.isPremiumUser) {
                                    bioState = it
                                    viewModel.showToast(if (it) "Biometrics integrated" else "Biometrics unlinked")
                                } else {
                                    viewModel.showToast("Biometrics is restricted to Premium tiers.")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Ad-block active shield",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Guarantees absolutely zero ads of any external provider.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Feature 4: Interactive Storage Speed Booster simulated utility
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "System Storage Utility Optimizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Checks fragmented local cache records to optimize decryption and synchronization operations response.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    var analyzingState by remember { mutableStateOf(false) }
                    var resultState by remember { mutableStateOf<String?>(null) }

                    if (analyzingState) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (resultState != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resultState ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            analyzingState = true
                            resultState = null
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(1200)
                                analyzingState = false
                                resultState = "Cache fully optimized! Purged 14.2 MB temporary stream data. Cryptographic response decreased by 34%."
                            }
                        },
                        enabled = !analyzingState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Analyze & Optimize")
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose Application Style",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    themesList.forEach { theme ->
                        ListItem(
                            headlineContent = { Text(theme) },
                            modifier = Modifier
                                .clickable {
                                    viewModel.changeTheme(theme)
                                    showThemeDialog = false
                                }
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showThemeDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

// FORMAT SIZES
fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var index = 0
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return String.format("%.1f %s", value, units[index])
}
