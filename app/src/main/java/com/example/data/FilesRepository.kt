package com.example.data

import com.example.model.CloudAccount
import com.example.model.SecureFileEntity
import com.example.model.VaultSetting
import kotlinx.coroutines.flow.Flow

class FilesRepository(private val filesDao: FilesDao) {

    // Virtual Drive Access
    val allFiles: Flow<List<SecureFileEntity>> = filesDao.getAllFiles()
    
    fun getFilesByFolder(path: String): Flow<List<SecureFileEntity>> = 
        filesDao.getFilesByPath(path)

    suspend fun insertFile(file: SecureFileEntity): Long = 
        filesDao.insertFile(file)

    suspend fun updateFile(file: SecureFileEntity) = 
        filesDao.updateFile(file)

    suspend fun deleteFileById(id: Int) = 
        filesDao.deleteFileById(id)

    suspend fun getFileById(id: Int): SecureFileEntity? = 
        filesDao.getFileById(id)

    suspend fun toggleFavorite(id: Int, isFavorite: Boolean) = 
        filesDao.updateFavorite(id, isFavorite)

    // Cloud Connectivity
    val cloudAccounts: Flow<List<CloudAccount>> = filesDao.getAllCloudAccounts()

    suspend fun saveCloudAccount(account: CloudAccount) = 
        filesDao.insertCloudAccount(account)

    suspend fun disconnectCloudAccount(service: String) = 
        filesDao.deleteCloudAccountByService(service)

    // Secure Vault and Theme Preferences
    val vaultSettings: Flow<VaultSetting?> = filesDao.getVaultSettingsFlow()

    suspend fun getVaultSettingsDirect(): VaultSetting? = 
        filesDao.getVaultSettingsDirect()

    suspend fun saveVaultSettings(settings: VaultSetting) = 
        filesDao.insertOrUpdateVaultSetting(settings)
}
