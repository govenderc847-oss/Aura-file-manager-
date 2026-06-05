package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.model.CloudAccount
import com.example.model.CloudFile
import com.example.model.LocalFile
import com.example.model.SecureFileEntity
import com.example.model.VaultSetting
import com.example.utils.CryptoUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.spec.KeySpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class FileViewModel(application: Application) : AndroidViewModel(application) {

    // Initialize Room Database and Repo directly here for robust self-containment
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
    .fallbackToDestructiveMigration()
    .build()

    private val dao = db.filesDao()

    // Screen States
    val currentPath = MutableStateFlow("/")
    val searchQuery = MutableStateFlow("")
    val searchCloudQuery = MutableStateFlow("")

    val toastMessage = MutableStateFlow<String?>(null)

    // Vault Authentication Session States
    val isVaultUnlocked = MutableStateFlow(false)
    val sessionVaultPassword = MutableStateFlow<String?>(null) // Session decrypted key holder

    // Premium status and Theme state
    val vaultSettings = dao.getVaultSettingsFlow()
        .map { it ?: VaultSetting(id = 1) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VaultSetting(id = 1)
        )

    // Current files & folders in local sandbox
    val allLocalFilesFlow = dao.getAllFiles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current cloud connection states
    val connectedAccounts = dao.getAllCloudAccounts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Mock Cloud Files (simulated cloud directories)
    private val _rawCloudFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    val cloudFiles = combine(_rawCloudFiles, searchCloudQuery) { files, query ->
        if (query.isEmpty()) {
            files
        } else {
            files.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filter files based on path & search query
    val currentFolderFiles = combine(allLocalFilesFlow, currentPath, searchQuery) { files, path, query ->
        files.filter { file ->
            // Filter by hierarchical folder path
            val normalizedFilePath = if (file.path.endsWith("/")) file.path else "${file.path}/"
            val normalizedCurrentPath = if (path.endsWith("/")) path else "$path/"
            
            val matchesFolder = if (file.isFolder) {
                // To list folders accurately, folder's container path should match current path
                file.path == path
            } else {
                file.path == path
            }

            val matchesQuery = if (query.isEmpty()) true else file.name.contains(query, ignoreCase = true)
            matchesFolder && matchesQuery
        }
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    init {
        // Seed files and default vault settings on first launcher run if DB is empty
        viewModelScope.launch {
            // Check settings setup
            val currentSettings = dao.getVaultSettingsDirect()
            if (currentSettings == null) {
                dao.insertOrUpdateVaultSetting(
                    VaultSetting(
                        id = 1,
                        vaultPasswordHash = null,
                        isPremiumUser = false,
                        selectedTheme = "Sophisticated Dark",
                        aesKeySizeBits = 256
                    )
                )
            }

            // Seed Local Files if table empty
            dao.getAllFiles().first().let { currentFiles ->
                if (currentFiles.isEmpty()) {
                    seedDefaultVirtualFiles()
                }
            }

            // Seed default Cloud items
            seedDefaultCloudDriveItems()
        }
    }

    private suspend fun seedDefaultVirtualFiles() {
        val defaultFiles = listOf(
            SecureFileEntity(
                name = "Documents",
                path = "/",
                isFolder = true,
                isEncrypted = false,
                encryptedBase64Payload = "",
                mimeType = "folder",
                sizeBytes = 0L
            ),
            SecureFileEntity(
                name = "Finance Ledger 2026.csv",
                path = "/",
                isFolder = false,
                isEncrypted = false,
                encryptedBase64Payload = "Month,Revenue,Expenses,Profit\nJan,45000,32000,13000\nFeb,48000,31000,17000\nMar,52000,33500,18500",
                mimeType = "text/csv",
                sizeBytes = 1845L,
                isFavorite = true
            ),
            // Seed an encrypted file with initial password payload "safe" passcode placeholder
            // A secure placeholder payload so we show locks out-of-the-box
            SecureFileEntity(
                name = "cryptocurrency_indices.backup",
                path = "/Documents",
                isFolder = false,
                isEncrypted = true,
                // Simple representation of an encrypted block
                encryptedBase64Payload = "U2VjdXJlIEFlcyBHQ00gZW5jcnlwdGVkIHBheWxvYWQgLSBVbmxvY2sgdG8gdmlldyB0aGUgYmFja3VwIGtleXM=",
                mimeType = "application/octet-stream",
                sizeBytes = 4120L
            ),
            SecureFileEntity(
                name = "personal_id_scan.png",
                path = "/Documents",
                isFolder = false,
                isEncrypted = false,
                encryptedBase64Payload = "[Binary Image Data Representation]",
                mimeType = "image/png",
                sizeBytes = 852000L
            )
        )
        for (file in defaultFiles) {
            dao.insertFile(file)
        }
    }

    private fun seedDefaultCloudDriveItems() {
        _rawCloudFiles.value = listOf(
            CloudFile("g1", "Income Tax declaration 2025.pdf", false, 1420000L, "application/pdf", "Google Drive"),
            CloudFile("g2", "Backup photos", true, 0L, "folder", "Google Drive"),
            CloudFile("g3", "vacation_vlog.mp4", false, 48200000L, "video/mp4", "Google Drive", "g2"),
            CloudFile("d1", "Customer database info.xlsx", false, 984000L, "application/excel", "Dropbox"),
            CloudFile("d2", "Contracts", true, 0L, "folder", "Dropbox"),
            CloudFile("o1", "Teams Meeting record.mp3", false, 12400000L, "audio/mp3", "OneDrive")
        )
    }

    // Toast Utility
    fun showToast(msg: String) {
        toastMessage.value = msg
    }

    fun clearToast() {
        toastMessage.value = null
    }

    // NAVIGATION
    fun navigateToFolder(folderName: String) {
        viewModelScope.launch {
            val base = currentPath.value
            val next = if (base == "/") "/$folderName" else "$base/$folderName"
            currentPath.value = next
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            val base = currentPath.value
            if (base == "/") return@launch
            val lastSlash = base.lastIndexOf('/')
            val parent = if (lastSlash == 0) "/" else base.substring(0, lastSlash)
            currentPath.value = parent
        }
    }

    // LOCAL INTEGRATIONS & FILE PERSISTENCE
    fun createNewLocalFile(name: String, content: String, isFolder: Boolean) {
        viewModelScope.launch {
            if (name.isBlank()) {
                showToast("Name cannot be empty")
                return@launch
            }
            val mime = if (isFolder) "folder" else {
                when {
                    name.endsWith(".txt") -> "text/plain"
                    name.endsWith(".csv") -> "text/csv"
                    name.endsWith(".xml") -> "text/xml"
                    else -> "text/plain"
                }
            }
            val size = if (isFolder) 0L else content.toByteArray(Charsets.UTF_8).size.toLong()
            val newFile = SecureFileEntity(
                name = name,
                path = currentPath.value,
                isFolder = isFolder,
                isEncrypted = false,
                encryptedBase64Payload = content,
                mimeType = mime,
                sizeBytes = size
            )
            dao.insertFile(newFile)
            showToast("'${name}' created successfully")
        }
    }

    fun deleteFile(fileEntity: SecureFileEntity) {
        viewModelScope.launch {
            dao.deleteFile(fileEntity)
            showToast("'${fileEntity.name}' deleted")
        }
    }

    fun toggleFavorite(fileEntity: SecureFileEntity) {
        viewModelScope.launch {
            dao.updateFavorite(fileEntity.id, !fileEntity.isFavorite)
        }
    }

    // VAULT ENGINE & CRYPTOGRAPHY
    fun setupVaultPasscode(password: String) {
        viewModelScope.launch {
            val saltBytes = CryptoUtils.generateSalt()
            val saltString = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP)
            
            // Derive hash to prevent saving plain passcode
            val key = CryptoUtils.deriveKey(password, saltBytes, 128)
            val hash = android.util.Base64.encodeToString(key.encoded, android.util.Base64.NO_WRAP)

            val currentSettings = dao.getVaultSettingsDirect() ?: VaultSetting(id = 1)
            dao.insertOrUpdateVaultSetting(
                currentSettings.copy(
                    vaultPasswordHash = hash,
                    vaultSalt = saltString
                )
            )
            isVaultUnlocked.value = true
            sessionVaultPassword.value = password
            showToast("Secure Vault created with strong GCM lock")
        }
    }

    fun authenticateVault(password: String): Boolean {
        val settings = vaultSettings.value
        if (settings.vaultPasswordHash == null || settings.vaultSalt == null) {
            return false
        }
        return try {
            val saltBytes = android.util.Base64.decode(settings.vaultSalt, android.util.Base64.NO_WRAP)
            val correctKeyBytes = android.util.Base64.decode(settings.vaultPasswordHash, android.util.Base64.NO_WRAP)
            
            val calculatedKey = CryptoUtils.deriveKey(password, saltBytes, 128)
            val isMatch = calculatedKey.encoded.contentEquals(correctKeyBytes)
            if (isMatch) {
                isVaultUnlocked.value = true
                sessionVaultPassword.value = password
                showToast("Vault Unlocked: GCM Key Active")
            } else {
                showToast("Incorrect passcode")
            }
            isMatch
        } catch (e: Exception) {
            false
        }
    }

    fun lockVault() {
        isVaultUnlocked.value = false
        sessionVaultPassword.value = null
        showToast("Vault Locked. Secure environment cleared.")
    }

    /**
     * Encrypts an existing plain-text file in the current directory
     */
    fun encryptFile(file: SecureFileEntity) {
        val password = sessionVaultPassword.value
        if (password == null) {
            showToast("Please unlock or configure the Secure Vault first")
            return
        }
        viewModelScope.launch {
            try {
                val salt = CryptoUtils.generateSalt()
                val keySpec = CryptoUtils.deriveKey(password, salt, vaultSettings.value.aesKeySizeBits)
                val encryptedText = CryptoUtils.encrypt(file.encryptedBase64Payload, keySpec, salt)
                
                val updated = file.copy(
                    isEncrypted = true,
                    encryptedBase64Payload = encryptedText
                )
                dao.updateFile(updated)
                showToast("File '${file.name}' fully encrypted using AES-${vaultSettings.value.aesKeySizeBits}")
            } catch (e: Exception) {
                showToast("Encryption failed: ${e.message}")
            }
        }
    }

    /**
     * Decrypts an encrypted file
     */
    fun decryptFile(file: SecureFileEntity, alternatePassword: String? = null) {
        val password = alternatePassword ?: sessionVaultPassword.value
        if (password == null) {
            showToast("Unlock Vault to perform quick decryption")
            return
        }
        viewModelScope.launch {
            try {
                val decrypted = CryptoUtils.decrypt(
                    file.encryptedBase64Payload,
                    password,
                    vaultSettings.value.aesKeySizeBits
                )
                
                if (decrypted != null) {
                    val updated = file.copy(
                        isEncrypted = false,
                        encryptedBase64Payload = decrypted
                    )
                    dao.updateFile(updated)
                    showToast("File '${file.name}' decrypted back to plain context")
                } else {
                    showToast("Decryption failed. Incorrect password or modified payload.")
                }
            } catch (e: Exception) {
                showToast("Decryption error: ${e.message}")
            }
        }
    }

    // CLOUD INTEGRATION SIMULATIONS
    fun connectCloudDrive(serviceName: String, accountEmail: String) {
        viewModelScope.launch {
            if (accountEmail.isBlank() || !accountEmail.contains("@")) {
                showToast("Please enter a valid credential email.")
                return@launch
            }
            val totalBytes = 15L * 1024 * 1024 * 1024 // 15 GB
            val usedBytes = (3.5 * 1024 * 1024 * 1024).toLong() // 3.5 GB
            val newAcc = CloudAccount(
                service = serviceName,
                email = accountEmail,
                accessToken = "simulated_oauth2_sh_token_672b1a",
                isConnected = true,
                storageUsedBytes = usedBytes,
                storageTotalBytes = totalBytes
            )
            dao.insertCloudAccount(newAcc)
            showToast("Securely integrated $serviceName ($accountEmail)")
        }
    }

    fun disconnectCloudDrive(serviceName: String) {
        viewModelScope.launch {
            dao.deleteCloudAccountByService(serviceName)
            showToast("Disconnected $serviceName links securely")
        }
    }

    fun triggerCloudSync(serviceName: String) {
        viewModelScope.launch {
            showToast("Checking $serviceName state...")
            kotlinx.coroutines.delay(1000)
            showToast("$serviceName synced. 0 discrepancies found.")
        }
    }

    // PREMIUM TIERS
    fun unlockPremium() {
        viewModelScope.launch {
            val currentSettings = dao.getVaultSettingsDirect() ?: VaultSetting(id = 1)
            dao.insertOrUpdateVaultSetting(
                currentSettings.copy(
                    isPremiumUser = true,
                    premiumUnlockedDate = System.currentTimeMillis()
                )
            )
            showToast("Premium Active! Lifetime features unlocked.")
        }
    }

    fun changeTheme(themeName: String) {
        viewModelScope.launch {
            val currentSettings = dao.getVaultSettingsDirect() ?: VaultSetting(id = 1)
            dao.insertOrUpdateVaultSetting(
                currentSettings.copy(selectedTheme = themeName)
            )
            showToast("Theme updated to $themeName")
        }
    }

    fun changeAesKeySize(bits: Int) {
        viewModelScope.launch {
            val currentSettings = dao.getVaultSettingsDirect() ?: VaultSetting(id = 1)
            dao.insertOrUpdateVaultSetting(
                currentSettings.copy(aesKeySizeBits = bits)
            )
            showToast("AES Encrypter Key Size set to $bits-bit")
        }
    }
}
