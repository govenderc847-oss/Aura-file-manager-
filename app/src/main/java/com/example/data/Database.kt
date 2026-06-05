package com.example.data

import androidx.room.*
import com.example.model.CloudAccount
import com.example.model.SecureFileEntity
import com.example.model.VaultSetting
import kotlinx.coroutines.flow.Flow

@Dao
interface FilesDao {
    // -------------------------------------------------------------
    // Secure Files (Virtual Drive)
    // -------------------------------------------------------------
    @Query("SELECT * FROM secure_files ORDER BY isFolder DESC, name ASC")
    fun getAllFiles(): Flow<List<SecureFileEntity>>

    @Query("SELECT * FROM secure_files WHERE path = :path ORDER BY isFolder DESC, name ASC")
    fun getFilesByPath(path: String): Flow<List<SecureFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: SecureFileEntity): Long

    @Update
    suspend fun updateFile(file: SecureFileEntity)

    @Delete
    suspend fun deleteFile(file: SecureFileEntity)

    @Query("DELETE FROM secure_files WHERE id = :id")
    suspend fun deleteFileById(id: Int)

    @Query("SELECT * FROM secure_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: Int): SecureFileEntity?

    @Query("UPDATE secure_files SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFav: Boolean)

    // -------------------------------------------------------------
    // Cloud Accounts Links
    // -------------------------------------------------------------
    @Query("SELECT * FROM cloud_accounts")
    fun getAllCloudAccounts(): Flow<List<CloudAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudAccount(account: CloudAccount): Long

    @Update
    suspend fun updateCloudAccount(account: CloudAccount)

    @Delete
    suspend fun deleteCloudAccount(account: CloudAccount)

    @Query("DELETE FROM cloud_accounts WHERE service = :service")
    suspend fun deleteCloudAccountByService(service: String)

    // -------------------------------------------------------------
    // Vault & System Settings (Single Row Config)
    // -------------------------------------------------------------
    @Query("SELECT * FROM vault_settings WHERE id = 1 LIMIT 1")
    fun getVaultSettingsFlow(): Flow<VaultSetting?>

    @Query("SELECT * FROM vault_settings WHERE id = 1 LIMIT 1")
    suspend fun getVaultSettingsDirect(): VaultSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateVaultSetting(setting: VaultSetting)
}

@Database(
    entities = [SecureFileEntity::class, CloudAccount::class, VaultSetting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filesDao(): FilesDao

    companion object {
        const val DATABASE_NAME = "secure_manager_db"
    }
}
